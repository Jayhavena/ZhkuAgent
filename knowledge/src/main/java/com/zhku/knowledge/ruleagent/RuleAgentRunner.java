package com.zhku.knowledge.ruleagent;
import com.zhku.agent.agent.runtime.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.contract.AgentToolCall;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 领域循环只消费下一动作，运行生命周期仍交给共享runtime。 */
public final class RuleAgentRunner {
    public record Question(String id,String text){}
    public record Input(Owner owner,UUID taskId,long version,String question,List<Question> questions,Map<String,String> userConditions){
        public Input {
            Objects.requireNonNull(owner);Objects.requireNonNull(taskId);new CreateTask(question,UUID.randomUUID());if(version<1||version>MAX_SAFE_VERSION)throw new IllegalArgumentException("任务版本无效");
            questions=List.copyOf(questions);userConditions=Map.copyOf(userConditions);
            if(questions.isEmpty()||questions.size()>4||questions.stream().map(Question::id).distinct().count()!=questions.size()||userConditions.size()>16)throw new IllegalArgumentException("输入超出任务边界");
            // 子问题只能保留原句，不能由模型悄悄缩小请求；语义拆分质量仍需T05/T08验收。
            int cursor=0;for(var q:questions){new Search(q.id(),q.text());int start=question.indexOf(q.text(),cursor);if(start<0||!onlySeparators(question.substring(cursor,start)))throw new IllegalArgumentException("子问题遗漏原请求");cursor=start+q.text().length();}
            if(!onlySeparators(question.substring(cursor)))throw new IllegalArgumentException("子问题遗漏原请求");
        }
        public static Input single(Owner owner,UUID task,String text){return new Input(owner,task,1,text,List.of(new Question("q1",text)),Map.of());}
    }
    public record RuleContext(String ruleId,String questionScope,List<com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.Condition> conditions,List<String> evidenceGaps){}
    public record View(String question,List<Question> questions,Map<String,String> userConditions,List<ToolObservation> observations,List<RuleAgentEvidenceTools.VerifiedFact> facts,List<RuleContext> ruleContexts,List<Event> events,int attemptsRemaining,int stepsRemaining){}
    public record Reply(String actionJson,boolean providerResponseReceived){}
    public interface DecisionPort extends AutoCloseable {Reply decide(View view,Duration timeout);default void close(){}}
    public record Event(int attempt,String action,ToolObservation observation,ReasonCode reason){}
    public record Outcome(TaskSnapshot snapshot,List<Event> events){public Outcome{events=List.copyOf(events);}}
    public static final class Rejected extends RuntimeException {final ReasonCode reason;public Rejected(ReasonCode reason){this.reason=reason;}}
    private final DecisionPort decision;private final RuleActionBroker broker;private final Clock clock;
    public RuleAgentRunner(DecisionPort decision,RuleActionBroker broker,Clock clock){this.decision=decision;this.broker=broker;this.clock=clock;}
    private static boolean onlySeparators(String value){return value.matches("[\\s，,；;。！？!?]*");}
    public Outcome run(AgentRunContext context,Input input){
        Objects.requireNonNull(context);Objects.requireNonNull(input);if(context.state()!=AgentRunState.CREATED&&context.state()!=AgentRunState.STOP_REQUESTED)throw new IllegalArgumentException("运行上下文不能复用");
        int attempts=0,steps=0;boolean received=false;var events=new ArrayList<Event>();String previous=null;
        Instant deadline=clock.instant().plusMillis(DEFAULT_BUDGET.maxRunMillis());RuleResultPublisher.Published published=null;
        try{
            if(!context.start()&&!context.isStopRequested())throw new IllegalStateException("运行未启动");
            while(published==null){
                if(context.isStopRequested()){published=new RuleResultPublisher(broker,input,context.runId()).failed(ReasonCode.CANCELLED);break;}
                if(attempts>=DEFAULT_BUDGET.maxModelAttemptsPerRun()||steps>=DEFAULT_BUDGET.maxToolStepsPerRun()||!clock.instant().isBefore(deadline)){published=new RuleResultPublisher(broker,input,context.runId()).failed(ReasonCode.BUDGET_EXCEEDED);break;}
                var contexts=broker.facts().stream().map(f->broker.tools.reviewedEntry(f.ruleId()).orElseThrow(()->new Rejected(ReasonCode.EVIDENCE_CHANGED))).distinct().map(e->new RuleContext(e.template().ruleId(),e.questionScope(),e.template().conditions(),e.template().evidenceGaps())).toList();
                var view=new View(input.question(),input.questions(),input.userConditions(),broker.observations(),broker.facts(),contexts,List.copyOf(events),DEFAULT_BUDGET.maxModelAttemptsPerRun()-attempts,DEFAULT_BUDGET.maxToolStepsPerRun()-steps);
                if(json(view).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>DEFAULT_BUDGET.maxRequestBytes())throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                attempts++;context.advanceStep();Instant called=clock.instant();
                Reply reply=decision.decide(view,Duration.ofMillis(Math.min(DEFAULT_BUDGET.maxModelMillis(),Math.max(1,Duration.between(called,deadline).toMillis()))));
                // 不能在取消或超时之后发布迟到结果；阻塞调用的强制退出由T05适配器负责。
                if(context.isStopRequested()){published=new RuleResultPublisher(broker,input,context.runId()).failed(ReasonCode.CANCELLED);break;}
                if(Duration.between(called,clock.instant()).toMillis()>DEFAULT_BUDGET.maxModelMillis())throw new Rejected(ReasonCode.MODEL_TIMEOUT);
                if(!clock.instant().isBefore(deadline))throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                if(reply==null)throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);received|=reply.providerResponseReceived();
                String name="INVALID";
                try{
                    var action=RuleAgentJson.readAction(reply.actionJson());name=name(action);String fingerprint=json(action);
                    if(fingerprint.equals(previous)){published=new RuleResultPublisher(broker,input,context.runId()).failed(ReasonCode.NO_PROGRESS);events.add(new Event(attempts,name,null,ReasonCode.NO_PROGRESS));break;}previous=fingerprint;
                    String q=action instanceof Search s?s.subquestionId():action instanceof Read r?r.subquestionId():action instanceof Ask a?a.subquestionId():null;
                    if(q!=null&&input.questions().stream().noneMatch(x->x.id().equals(q)))throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                    steps++;var call=context.proposeToolCall(null,name,fingerprint);ToolObservation observation=null;ReasonCode reason=null;
                    try{
                        if(action instanceof Search||action instanceof Read)observation=broker.execute(action,input,context.runId(),steps);
                        else if(action instanceof Ask a)published=new RuleResultPublisher(broker,input,context.runId()).ask(a);
                        else if(action instanceof Finish f)published=new RuleResultPublisher(broker,input,context.runId()).finish(f);
                        var toolStatus=observation==null||observation.status()==ObservationStatus.SUCCEEDED||observation.status()==ObservationStatus.NO_MATCH?AgentToolCall.Status.SUCCEEDED:observation.status()==ObservationStatus.UNAVAILABLE?AgentToolCall.Status.FAILED:AgentToolCall.Status.REJECTED;
                        context.recordToolResult(call,new AgentToolCall.Result(toolStatus,json(observation==null?published.result():observation),AgentToolCall.PayloadFormat.JSON,toolStatus==AgentToolCall.Status.SUCCEEDED?null:com.zhku.agent.agent.runtime.contract.AgentRuntimeError.of(com.zhku.agent.agent.runtime.contract.AgentRuntimeError.Category.TOOL_FAILURE)));
                    }catch(Rejected invalid){reason=invalid.reason;context.recordToolResult(call,new AgentToolCall.Result(AgentToolCall.Status.REJECTED,null,null,com.zhku.agent.agent.runtime.contract.AgentRuntimeError.of(com.zhku.agent.agent.runtime.contract.AgentRuntimeError.Category.DOMAIN_FAILURE)));}
                    events.add(new Event(attempts,name,observation,reason));
                }catch(IllegalArgumentException malformed){
                    ReasonCode reason=ReasonCode.INVALID_MODEL_RESPONSE;
                    try{String type=new ObjectMapper().readTree(reply.actionJson()).path("type").asText();if(!Set.of("SEARCH_REVIEWED_RULES","READ_VERIFIED_RULE","ASK_NECESSARY_CONDITION","FINISH_CHECK").contains(type))reason=ReasonCode.UNKNOWN_TOOL;}catch(Exception ignored){}
                    events.add(new Event(attempts,name,null,reason));
                }catch(Rejected invalid){events.add(new Event(attempts,name,null,invalid.reason));}
            }
        }catch(RuleAgentProvider.Failure failure){received|=failure.received;published=new RuleResultPublisher(broker,input,context.runId()).failed(failure.reason);}
        catch(Rejected failure){published=new RuleResultPublisher(broker,input,context.runId()).failed(failure.reason);}
        catch(RuntimeException unavailable){published=new RuleResultPublisher(broker,input,context.runId()).failed(ReasonCode.INVALID_MODEL_RESPONSE);}
        finally{
            // 等待学生不占用工作线程；端口必须是本轮独占资源，不关闭宿主共享线程池。
            if(context.isStopRequested())published=new RuleResultPublisher(broker,input,context.runId()).failed(ReasonCode.CANCELLED);
            // 清理失败须在终态CAS前确定，避免runtime成功但结果失败的不可发布快照。
            try{context.cleanupOnce(decision::close);}catch(RuntimeException cleanupFailure){/* runtime已记录清理异常，仍需完成失败状态与终止收尾。 */}
            if(context.hasCleanupFailure())published=new RuleResultPublisher(broker,input,context.runId()).failed(ReasonCode.TASK_EXECUTION_UNAVAILABLE);
            var target=published==null?TaskStatus.FAILED:published.status();var state=target==TaskStatus.STOPPED?(context.isStopRequested()?AgentRunState.CANCELLED:AgentRunState.INCOMPLETE):target==TaskStatus.FAILED?AgentRunState.FAILED:target==TaskStatus.WAITING_USER||published.result().coverage()!=Coverage.COMPLETE?AgentRunState.INCOMPLETE:AgentRunState.SUCCEEDED;
            if(!context.finish(state)&&context.isStopRequested()){published=new RuleResultPublisher(broker,input,context.runId()).failed(ReasonCode.CANCELLED);context.finish(AgentRunState.CANCELLED);}
            context.signalTermination();context.publishTermination();
        }
        var execution=new Execution(Mode.ONLINE,attempts,steps,received,published.result().evidence().isEmpty()?AnswerSource.NONE:received?AnswerSource.LIVE_AGENT:AnswerSource.VERIFIED_RULE_ONLY,ReplayCoverage.NOT_APPLICABLE);
        var snapshot=new TaskSnapshot(SCHEMA_VERSION,input.taskId(),UUID.randomUUID(),context.runId(),input.version(),published.status(),context.state(),true,published.result(),published.continuation(),execution);
        return new Outcome(snapshot,events);
    }
    private static String json(Object value){try{return new ObjectMapper().writeValueAsString(value);}catch(Exception invalid){throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);}}
    private static String name(NextAction action){return action instanceof Search?"SEARCH_REVIEWED_RULES":action instanceof Read?"READ_VERIFIED_RULE":action instanceof Ask?"ASK_NECESSARY_CONDITION":"FINISH_CHECK";}
}
