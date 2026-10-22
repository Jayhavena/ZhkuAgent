package com.zhku.knowledge.ruleagent;
import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 单机短期任务存储，主体与Session共同隔离；不承诺跨重启恢复。 */
public final class RuleAgentTaskStore implements AutoCloseable {
    public enum Code { NOT_AVAILABLE,CONFLICT,EXPIRED,CAPACITY,NEEDS_CONFIRMATION,NEW_TASK_REQUIRED,CLOSED }
    public static final class Failure extends RuntimeException {public final Code code;public Failure(Code code){super(code.name(),null,false,false);this.code=code;}}
    public record Limits(int maxTasks,int maxTasksPerActor,int maxRequestKeys){public Limits{if(maxTasks<1||maxTasks>2048||maxTasksPerActor<1||maxTasksPerActor>maxTasks||maxRequestKeys<1||maxRequestKeys>8192)throw new IllegalArgumentException("任务容量无效");}}
    public static final Limits DEFAULT_LIMITS=new Limits(128,8,512);
    /** 每任务只留最近4次已接受运行，防止澄清链和异常重试造成无界内存增长。 */
    static final int MAX_TRACES_PER_TASK=4;
    public record Accepted(UUID taskId,UUID turnId,UUID runId,long version,boolean duplicate){}
    public record Turn(UUID turnId,UUID runId,String message,Map<String,String> conditions,TaskStatus status,int attempts){public Turn{conditions=Map.copyOf(conditions);}}
    public interface Work {RuleAgentRunner.Outcome run(AgentRunContext context,RuleAgentRunner.Input input);boolean revalidate(ResultEnvelope previous);}
    public interface Factory {Work open(Owner owner,UUID task);}
    private final Object lock=new Object();private final Executor executor;private final Factory factory;private final Clock clock;private final Limits limits;
    private final Consumer<AgentRunEvent> observer;private final AgentRunObservationDispatcher dispatcher;
    private final Map<UUID,Slot> tasks=new LinkedHashMap<>();private final Map<RequestKey,StoredRequest> requests=new LinkedHashMap<>();private final AtomicInteger jobs=new AtomicInteger();private boolean closed;
    private record StoredRequest(String payload,Accepted accepted,Instant expires){}
    private static final class Slot {
        final UUID id;final Owner owner;final String question;final List<RuleAgentRunner.Question> questions;final Instant expires;
        long version=1;int runs=1,clarifications,attempts;UUID turn=UUID.randomUUID();AgentRunContext context;TaskSnapshot published;ResultEnvelope previous;Instant waitingExpires;Work work;
        Map<String,String> conditions=Map.of();final List<Turn> history=new ArrayList<>();List<RuleAgentRunTrace> traces=List.of();
        Slot(Owner owner,String question,Instant expires,AgentRunContext context){this.id=UUID.randomUUID();this.owner=owner;this.question=question;this.expires=expires;this.questions=split(question);this.context=context;history.add(new Turn(turn,context.runId(),question,conditions,TaskStatus.ACTIVE,0));}
    }
    private record Ticket(Slot slot,long version,AgentRunContext context,UUID turn,Map<String,String> conditions){}
    public RuleAgentTaskStore(Executor executor,Factory factory,Clock clock,Limits limits){this.executor=Objects.requireNonNull(executor);this.factory=Objects.requireNonNull(factory);this.clock=Objects.requireNonNull(clock);this.limits=Objects.requireNonNull(limits);this.observer=null;this.dispatcher=null;}
    /**
     * 可选旁路入口：仅在OBS集成获批后，由T08宿主注入隐私安全的消费者。
     * dispatcher由宿主管理；TaskStore只管理RunContext，绝不关闭借用资源（包括processDefault）。
     * 默认HTTP Bean仍使用四参构造器；此接口不授权真实/rules接线。
     */
    public RuleAgentTaskStore(Executor executor,Factory factory,Clock clock,Limits limits,Consumer<AgentRunEvent> observer,AgentRunObservationDispatcher dispatcher){this.executor=Objects.requireNonNull(executor);this.factory=Objects.requireNonNull(factory);this.clock=Objects.requireNonNull(clock);this.limits=Objects.requireNonNull(limits);this.observer=Objects.requireNonNull(observer);this.dispatcher=Objects.requireNonNull(dispatcher);}
    // subscribe只建立订阅，不调用消费者；避免在任务锁内执行用户代码。事件仍由Runtime异步有界投递。
    private AgentRunContext newContext(String question){return observer==null?AgentRunContext.create(question):AgentRunContext.create(UUID.randomUUID(),question,observer,dispatcher);}
    public Accepted create(Owner owner,CreateTask request){
        Objects.requireNonNull(owner);Objects.requireNonNull(request);RequestKey key=new RequestKey(owner,RequestAction.CREATE,request.clientRequestId());String payload=payload(request);Ticket ticket;Accepted accepted;
        synchronized(lock){admit();var old=duplicate(key,payload);if(old!=null)return old;
            checkNewRequestCapacity();
            if(tasks.size()>=limits.maxTasks()||tasks.values().stream().filter(s->s.owner.actorId().equals(owner.actorId())).count()>=limits.maxTasksPerActor())throw new Failure(Code.CAPACITY);
            var slot=new Slot(owner,request.question(),clock.instant().plusMillis(DEFAULT_BUDGET.taskTtlMillis()),newContext(request.question()));tasks.put(slot.id,slot);accepted=accepted(slot,false);reserve(key,payload,accepted,slot.expires);ticket=ticket(slot);
        }
        schedule(ticket);return accepted;
    }
    public Accepted message(Owner owner,UUID task,Message request){
        RequestKey key=new RequestKey(owner,RequestAction.MESSAGE,request.clientRequestId());String payload=payload(List.of(task,request));Slot slot;long version;ResultEnvelope previous;Clarification condition;Work work;
        synchronized(lock){admit();var old=duplicate(key,payload);if(old!=null)return old;slot=owned(owner,task);checkWaiting(slot,request);version=slot.version;previous=slot.published.result();condition=previous.items().stream().flatMap(q->q.clarification().stream()).findFirst().orElseThrow();work=slot.work;}
        String value=resolve(request.message(),condition);
        // 真实证据重验在锁外；回来后再次比较版本/nonce，避免两个窗口拼接同一轮条件。
        boolean valid;try{valid=work!=null&&work.revalidate(previous);}catch(RuntimeException unavailable){throw new Failure(Code.NEEDS_CONFIRMATION);}
        if(!valid)throw new Failure(Code.EXPIRED);
        Ticket ticket;Accepted accepted;
        synchronized(lock){admit();var old=duplicate(key,payload);if(old!=null)return old;if(owned(owner,task)!=slot||slot.version!=version)throw new Failure(Code.CONFLICT);checkWaiting(slot,request);
            checkNewRequestCapacity();
            if(slot.runs>=DEFAULT_BUDGET.maxTaskRuns()||slot.clarifications>DEFAULT_BUDGET.maxClarificationTurns()||slot.attempts+DEFAULT_BUDGET.maxModelAttemptsPerRun()>DEFAULT_BUDGET.maxModelAttemptsPerTask()||slot.version+2>MAX_SAFE_VERSION)throw new Failure(Code.CAPACITY);
            var conditions=new LinkedHashMap<>(slot.conditions);conditions.put(condition.conditionId(),value);slot.conditions=Map.copyOf(conditions);
            slot.previous=previous;slot.published=null;slot.version++;slot.runs++;slot.turn=UUID.randomUUID();slot.context=newContext(slot.question);slot.waitingExpires=null;
            slot.history.add(new Turn(slot.turn,slot.context.runId(),request.message(),slot.conditions,TaskStatus.ACTIVE,0));accepted=accepted(slot,false);reserve(key,payload,accepted,slot.expires);ticket=ticket(slot);
        }
        schedule(ticket);return accepted;
    }
    public Accepted cancel(Owner owner,UUID task,Cancel request){
        RequestKey key=new RequestKey(owner,RequestAction.CANCEL,request.clientRequestId());String payload=payload(List.of(task,request));
        synchronized(lock){admit();var old=duplicate(key,payload);if(old!=null)return old;var slot=owned(owner,task);
            if(slot.published!=null||slot.version!=request.expectedVersion()||!slot.context.runId().equals(request.runId()))throw new Failure(Code.CONFLICT);
            checkNewRequestCapacity();
            // runtime终止但尚未保存的窗口不能虚称取消已受理；让调用方查询最终快照。
            if(!slot.context.requestStop(AgentStopReason.USER_REQUEST))throw new Failure(Code.CONFLICT);
            var accepted=accepted(slot,false);reserve(key,payload,accepted,slot.expires);return accepted;
        }
    }
    public TaskSnapshot snapshot(Owner owner,UUID task){synchronized(lock){ensureOpen();var slot=owned(owner,task);return slot.published!=null?slot.published:new TaskSnapshot(SCHEMA_VERSION,slot.id,slot.turn,slot.context.runId(),slot.version,TaskStatus.ACTIVE,slot.context.state(),false,slot.previous,null,new Execution(Mode.ONLINE,0,0,false,AnswerSource.NONE,ReplayCoverage.NOT_APPLICABLE));}}
    public Accepted lookup(Owner owner,RequestAction action,UUID key){synchronized(lock){ensureOpen();var entry=requests.get(new RequestKey(owner,action,key));if(entry==null)throw new Failure(Code.NOT_AVAILABLE);if(!clock.instant().isBefore(entry.expires()))throw new Failure(Code.EXPIRED);return entry.accepted();}}
    public List<Turn> history(Owner owner,UUID task){synchronized(lock){ensureOpen();return List.copyOf(owned(owner,task).history);}}
    /** 评测侧只读入口：Owner必须完全匹配；返回副本，元素为不可变投影，跨Owner读取直接失败。 */
    public List<RuleAgentRunTrace> traceHistory(Owner owner,UUID task){synchronized(lock){ensureOpen();return List.copyOf(owned(owner,task).traces);}}
    /** 轨迹随任务Slot删除而释放；只保留最近MAX_TRACES_PER_TASK条，超限淘汰最旧。 */
    static List<RuleAgentRunTrace> retainTrace(List<RuleAgentRunTrace> existing,RuleAgentRunTrace trace){
        var kept=new ArrayList<RuleAgentRunTrace>(existing);kept.add(Objects.requireNonNull(trace));
        while(kept.size()>MAX_TRACES_PER_TASK)kept.removeFirst();
        return List.copyOf(kept);
    }
    public int activeJobs(){return jobs.get();}
    private void schedule(Ticket ticket){
        var task=AgentRunTask.create(ticket.context(),()->execute(ticket));
        try{executor.execute(task);}catch(RejectedExecutionException rejected){publishFailure(ticket,ReasonCode.TASK_EXECUTION_UNAVAILABLE);}
    }
    private void execute(Ticket ticket){
        jobs.incrementAndGet();RuleAgentRunner.Outcome outcome=null;
        try{
            if(ticket.context().isStopRequested()){publishFailure(ticket,ReasonCode.CANCELLED);return;}
            Work work=ticket.slot().work;
            if(work==null){work=factory.open(ticket.slot().owner,ticket.slot().id);synchronized(lock){ticket.slot().work=Objects.requireNonNull(work);}}
            var input=new RuleAgentRunner.Input(ticket.slot().owner,ticket.slot().id,ticket.version()+1,ticket.slot().question,ticket.slot().questions,ticket.conditions());outcome=work.run(ticket.context(),input);
        }catch(RuntimeException failure){publishFailure(ticket,ReasonCode.TASK_EXECUTION_UNAVAILABLE);}
        finally{jobs.decrementAndGet();}
        if(outcome!=null)publish(ticket,outcome.snapshot(),outcome.events(),false);
    }
    private void publish(Ticket t,TaskSnapshot result,List<RuleAgentRunner.Event> events,boolean exceptionalFailure){
        synchronized(lock){var slot=t.slot();if(tasks.get(slot.id)!=slot||slot.context!=t.context()||slot.version!=t.version()||slot.published!=null)return;
            if(!result.taskId().equals(slot.id)||!result.runId().equals(t.context().runId())||!result.terminated()||!t.context().termination().isDone()||result.version()!=t.version()+1)throw new Failure(Code.CONFLICT);
            // 取消赢得runtime状态后，迟到的成功产物不能覆盖任务。停止标记不等于工作线程已退出。
            if(t.context().state()==AgentRunState.CANCELLED&&result.runState()!=AgentRunState.CANCELLED)return;
            // 第三轮可以完成答案，但不能发出已没有续答额度的第三次追问。
            if(result.taskStatus()==TaskStatus.WAITING_USER&&slot.clarifications>=DEFAULT_BUDGET.maxClarificationTurns()){
                var issue=new Issue("clarification-budget",IssueKind.PROCESSING_FAILURE,ReasonCode.BUDGET_EXCEEDED,"本任务已达到必要澄清次数上限，尚未完成的部分不作结论。","请整理已知条件后重新发起查证。");
                var limited=new ResultEnvelope(result.version(),result.runId(),result.result().catalogueVersion(),result.result().referenceRevision(),Coverage.NONE,slot.questions.stream().map(q->new Subquestion(q.id(),Coverage.NONE,List.of(),List.of(issue),List.of())).toList(),List.of(),List.of());
                var execution=result.execution();
                result=new TaskSnapshot(SCHEMA_VERSION,slot.id,t.turn(),result.runId(),result.version(),TaskStatus.STOPPED,result.runState(),true,limited,null,new Execution(execution.mode(),execution.modelAttemptCount(),execution.toolStepCount(),execution.providerResponseReceived(),AnswerSource.NONE,execution.replayCoverage()));
            }
            // 轨迹必须与已接受快照在同一锁内落账：过期ticket已在入口返回，迟到的成功或取消都不能追加。
            var retained=retainTrace(slot.traces,RuleAgentRunTrace.project(result,t.turn(),events,exceptionalFailure));
            slot.version=result.version();slot.attempts+=result.execution().modelAttemptCount();slot.published=new TaskSnapshot(SCHEMA_VERSION,slot.id,t.turn(),result.runId(),slot.version,result.taskStatus(),result.runState(),true,result.result(),result.continuationToken(),result.execution());slot.previous=null;slot.traces=retained;
            if(result.taskStatus()==TaskStatus.WAITING_USER){slot.clarifications++;slot.waitingExpires=clock.instant().plusMillis(DEFAULT_BUDGET.waitingTtlMillis());}
            int last=slot.history.size()-1;var old=slot.history.get(last);slot.history.set(last,new Turn(old.turnId(),old.runId(),old.message(),old.conditions(),result.taskStatus(),result.execution().modelAttemptCount()));
            if(closed)tasks.remove(slot.id);
        }
    }
    private void publishFailure(Ticket ticket,ReasonCode reason){
        var context=ticket.context();if(context.isStopRequested())reason=ReasonCode.CANCELLED;boolean cancelled=reason==ReasonCode.CANCELLED;
        context.finish(cancelled?AgentRunState.CANCELLED:AgentRunState.FAILED);context.cleanupOnce(()->{});context.signalTermination();context.publishTermination();
        var issue=new Issue("failure",reason.kind,reason,cancelled?"本轮已停止，未完成的部分不作结论。":"本轮任务执行失败，不代表资料没有规定。","请查询任务状态后重新查证，不自动重复提交。");
        var result=new ResultEnvelope(ticket.version()+1,context.runId(),"unavailable","2025.9.2",Coverage.NONE,ticket.slot().questions.stream().map(q->new Subquestion(q.id(),Coverage.NONE,List.of(),List.of(issue),List.of())).toList(),List.of(),List.of());
        publish(ticket,new TaskSnapshot(SCHEMA_VERSION,ticket.slot().id,ticket.turn(),context.runId(),ticket.version()+1,cancelled?TaskStatus.STOPPED:TaskStatus.FAILED,context.state(),true,result,null,new Execution(Mode.ONLINE,Math.min(context.currentStep(),DEFAULT_BUDGET.maxModelAttemptsPerRun()),0,false,AnswerSource.NONE,ReplayCoverage.NOT_APPLICABLE)),List.of(),true);
    }
    private void checkWaiting(Slot slot,Message request){if(slot.published==null||slot.published.taskStatus()!=TaskStatus.WAITING_USER||slot.version!=request.expectedVersion()||!Objects.equals(slot.published.continuationToken(),request.continuationToken()))throw new Failure(Code.CONFLICT);if(slot.waitingExpires==null||!clock.instant().isBefore(slot.waitingExpires))throw new Failure(Code.EXPIRED);}
    private Slot owned(Owner owner,UUID task){var slot=tasks.get(task);if(slot==null||!slot.owner.equals(owner))throw new Failure(Code.NOT_AVAILABLE);if(!clock.instant().isBefore(slot.expires))throw new Failure(Code.EXPIRED);return slot;}
    private void admit(){ensureOpen();Instant now=clock.instant();requests.entrySet().removeIf(e->!now.isBefore(e.getValue().expires()));tasks.entrySet().removeIf(e->e.getValue().published!=null&&!now.isBefore(e.getValue().expires));}
    // 满容量仍须返回已有受理记录；新请求必须在改变任务或消费续接令牌前检查容量。
    private void checkNewRequestCapacity(){if(requests.size()>=limits.maxRequestKeys())throw new Failure(Code.CAPACITY);}
    private void ensureOpen(){if(closed)throw new Failure(Code.CLOSED);}
    private Accepted duplicate(RequestKey key,String payload){var old=requests.get(key);if(old==null)return null;if(!old.payload().equals(payload))throw new Failure(Code.CONFLICT);var a=old.accepted();return new Accepted(a.taskId(),a.turnId(),a.runId(),a.version(),true);}
    private void reserve(RequestKey key,String payload,Accepted accepted,Instant expires){requests.put(key,new StoredRequest(payload,accepted,expires));}
    private static Accepted accepted(Slot slot,boolean duplicate){return new Accepted(slot.id,slot.turn,slot.context.runId(),slot.version,duplicate);}
    private static Ticket ticket(Slot slot){return new Ticket(slot,slot.version,slot.context,slot.turn,slot.conditions);}
    private static String payload(Object value){try{return new ObjectMapper().writeValueAsString(value);}catch(Exception invalid){throw new Failure(Code.CONFLICT);}}
    private static List<RuleAgentRunner.Question> split(String question){var result=new ArrayList<RuleAgentRunner.Question>();for(String text:question.split("[；;]")){if(!text.isBlank())result.add(new RuleAgentRunner.Question("q"+(result.size()+1),text.trim()));}if(result.isEmpty()||result.size()>4)throw new Failure(Code.NEEDS_CONFIRMATION);return List.copyOf(result);}
    private static String resolve(String message,Clarification condition){
        String text=message.trim();if(condition.allowedValues().contains(text))return text;
        if(text.matches("^(?:改问|换个问题|另外问|我想问|我要问).+"))throw new Failure(Code.NEW_TASK_REQUIRED);
        // 只接受明确的完整补充，不从否定或混合请求中摘一个公选关键词认定条件。
        if(condition.conditionId().equals("courseType")){
            if(text.matches("^(?:我这门课是|这门课是|课程属于|是)?(?:公共选修课|公共选修|公选课|公选)[。！!]?$"))return "PUBLIC";
            if(text.matches("^(?:我这门课是|这门课是|课程属于|是)?(?:专业选修课|专业选修|专选课|专选)[。！!]?$"))return "PROFESSIONAL";
        }
        throw new Failure(Code.NEEDS_CONFIRMATION);
    }
    @Override public void close(){List<AgentRunContext> contexts;synchronized(lock){if(closed)return;closed=true;contexts=tasks.values().stream().filter(s->s.published==null).map(s->s.context).toList();tasks.entrySet().removeIf(e->e.getValue().published!=null);requests.clear();}contexts.forEach(c->c.requestStop(AgentStopReason.USER_REQUEST));}
}
