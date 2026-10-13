package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.zhku.agent.agent.runtime.AgentRunState;
import java.util.*;

/** v3查证合同只验证结构、关联与状态；审核资格和事实语义仍由领域守卫验证。 */
public final class RuleAgentContract {
    private RuleAgentContract() {}
    public static final int SCHEMA_VERSION=3;
    public static final long MAX_SAFE_VERSION=9007199254740991L;
    public static final String BUDGET_REVISION="T05_READ_BUDGET_02";
    public static final int MIN_READ_HANDLES=1;
    // 两次真实读取决策触及旧5秒上限；仅放宽时间作有界验证，次数/权限不变，不是SLA或外发许可。
    public static final BudgetPolicy DEFAULT_BUDGET=new BudgetPolicy(4,6,10000,45000,3,12,2,3,8,32768,65536,1024,300000,900000);
    public record BudgetPolicy(int maxModelAttemptsPerRun,int maxToolStepsPerRun,int maxModelMillis,int maxRunMillis,
            int maxTaskRuns,int maxModelAttemptsPerTask,int maxClarificationTurns,int maxReadHandles,
            int maxEvidenceFragments,int maxEvidenceBytes,int maxRequestBytes,int maxOutputTokens,
            int waitingTtlMillis,int taskTtlMillis) {}
    public enum TaskStatus { ACTIVE,WAITING_USER,RESOLVED,STOPPED,FAILED }
    public enum Coverage { COMPLETE,PARTIAL,NONE }
    public enum Mode { ONLINE,ENGINEERING_REPLAY }
    public enum AnswerSource { NONE,LIVE_AGENT,SEALED_REPLAY,VERIFIED_RULE_ONLY }
    public enum ReplayCoverage { NOT_APPLICABLE,HIT,MISS }
    public enum ConditionSource { USER_DECLARED }
    public enum IssueKind { DATA_GAP,CLARIFICATION,PROCESSING_FAILURE,SCOPE_LIMIT,CONFLICT,REPLAY_MISS }
    public enum EvidenceRole { CLAIM,CLARIFICATION,CONFLICT,SCOPE }
    public enum ReasonCode {
        RETRIEVAL_NO_MATCH(IssueKind.DATA_GAP),DETAIL_NOT_IN_REVIEWED_TEXT(IssueKind.DATA_GAP),
        CURRENT_NOTICE_MISSING(IssueKind.DATA_GAP),PERSONAL_RECORD_NOT_AVAILABLE(IssueKind.DATA_GAP),
        REVIEWED_FACT_COVERAGE_GAP(IssueKind.DATA_GAP),MATERIAL_SCOPE_MISMATCH(IssueKind.SCOPE_LIMIT),
        CURRENT_APPLICABILITY_UNKNOWN(IssueKind.SCOPE_LIMIT),MISSING_CONDITION(IssueKind.CLARIFICATION),
        UNRESOLVED_CONFLICT(IssueKind.CONFLICT),REPLAY_NOT_COVERED(IssueKind.REPLAY_MISS),
        RETRIEVAL_UNAVAILABLE,MODEL_TIMEOUT,MODEL_RATE_LIMIT,INVALID_MODEL_RESPONSE,UNKNOWN_TOOL,
        EVIDENCE_CHANGED,AUDIT_UNAVAILABLE,BUDGET_EXCEEDED,CLARIFICATION_EXPIRED,CANCELLED,NO_PROGRESS,TASK_EXECUTION_UNAVAILABLE,
        MODEL_AUTH_FAILURE,MODEL_UNAVAILABLE,MODEL_CONNECTION_FAILURE;
        final IssueKind kind;
        ReasonCode(){this(IssueKind.PROCESSING_FAILURE);}
        ReasonCode(IssueKind kind){this.kind=kind;}
    }
    public record Execution(Mode mode,int modelAttemptCount,int toolStepCount,boolean providerResponseReceived,
            AnswerSource answerSource,ReplayCoverage replayCoverage) {
        public Execution {
            required(mode);required(answerSource);required(replayCoverage);
            require(modelAttemptCount>=0&&modelAttemptCount<=DEFAULT_BUDGET.maxModelAttemptsPerRun());
            require(toolStepCount>=0&&toolStepCount<=DEFAULT_BUDGET.maxToolStepsPerRun());
            require(!providerResponseReceived||modelAttemptCount>0);
            if(mode==Mode.ONLINE)require(replayCoverage==ReplayCoverage.NOT_APPLICABLE&&answerSource!=AnswerSource.SEALED_REPLAY);
            else require(modelAttemptCount==0&&!providerResponseReceived&&replayCoverage!=ReplayCoverage.NOT_APPLICABLE&&answerSource!=AnswerSource.LIVE_AGENT);
            if(answerSource==AnswerSource.LIVE_AGENT)require(providerResponseReceived&&modelAttemptCount>0);
            if(answerSource==AnswerSource.SEALED_REPLAY)require(replayCoverage==ReplayCoverage.HIT);
        }
    }
    public record Claim(String id,String factId,String text,Map<String,String> when,ConditionSource conditionSource) {
        public Claim {
            identifier(id);identifier(factId);RuleAgentContract.text(text,8192);required(conditionSource);required(when);
            require(when.size()<=16);when.forEach((key,value)->{identifier(key);RuleAgentContract.text(value,128);});when=Map.copyOf(when);
        }
    }
    public record Issue(String id,IssueKind kind,ReasonCode reasonCode,String message,String nextStep) {
        public Issue {identifier(id);required(kind);required(reasonCode);require(reasonCode.kind==kind);RuleAgentContract.text(message,2048);RuleAgentContract.text(nextStep,2048);}
    }
    public record Clarification(String conditionId,String question,List<String> allowedValues) {
        public Clarification {identifier(conditionId);RuleAgentContract.text(question,2048);allowedValues=RuleAgentContract.items(allowedValues,2,32);allowedValues.forEach(v->RuleAgentContract.text(v,128));unique(allowedValues);}
    }
    public record Subquestion(String id,Coverage coverage,List<Claim> claims,List<Issue> issues,List<Clarification> clarification) {
        public Subquestion {
            identifier(id);required(coverage);claims=RuleAgentContract.items(claims,0,16);issues=RuleAgentContract.items(issues,0,8);clarification=RuleAgentContract.items(clarification,0,1);
            require(clarification.isEmpty()==issues.stream().noneMatch(i->i.kind()==IssueKind.CLARIFICATION));
            Coverage expected=claims.isEmpty()?Coverage.NONE:issues.isEmpty()&&clarification.isEmpty()?Coverage.COMPLETE:Coverage.PARTIAL;
            require(coverage==expected);
        }
    }
    public record Evidence(String id,UUID sourceId,UUID chunkId,String revision,String checksum,
            long startInclusive,long endExclusive,String quote,UUID originalToken) {
        public Evidence {
            identifier(id);required(sourceId);required(chunkId);required(originalToken);RuleAgentContract.text(revision,128);RuleAgentContract.text(quote,4096);
            require(checksum!=null&&checksum.matches("[a-f0-9]{64}"));
            require(startInclusive>=0&&startInclusive<=MAX_SAFE_VERSION&&endExclusive>startInclusive
                    &&endExclusive<=MAX_SAFE_VERSION&&endExclusive-startInclusive==quote.length());
        }
    }
    public record EvidenceRelation(String evidenceId,String subquestionId,EvidenceRole role,String targetId) {
        public EvidenceRelation {identifier(evidenceId);identifier(subquestionId);identifier(targetId);required(role);}
    }
    private record Target(EvidenceRole role) {}
    public record ResultEnvelope(long publishedVersion,UUID originatingRunId,String catalogueVersion,String referenceRevision,
            Coverage coverage,List<Subquestion> items,List<Evidence> evidence,List<EvidenceRelation> evidenceRelations) {
        public ResultEnvelope {
            RuleAgentContract.version(publishedVersion);required(originatingRunId);RuleAgentContract.text(catalogueVersion,128);RuleAgentContract.text(referenceRevision,128);required(coverage);
            items=RuleAgentContract.items(items,1,4);evidence=RuleAgentContract.items(evidence,0,8);evidenceRelations=RuleAgentContract.items(evidenceRelations,0,64);
            unique(items.stream().map(Subquestion::id).toList());unique(evidence.stream().map(Evidence::id).toList());unique(evidenceRelations);
            require(coverage==RuleAgentContract.coverage(items));require(items.stream().mapToInt(q->q.clarification().size()).sum()<=1);
            var targets=new HashMap<String,Target>();
            for(var q:items){
                for(var c:q.claims())target(targets,q.id(),c.id(),EvidenceRole.CLAIM);
                for(var i:q.issues())target(targets,q.id(),i.id(),i.kind()==IssueKind.CONFLICT?EvidenceRole.CONFLICT:EvidenceRole.SCOPE);
                for(var c:q.clarification())target(targets,q.id(),c.conditionId(),EvidenceRole.CLARIFICATION);
            }
            var ids=new HashSet<String>();var used=new HashSet<String>();var counts=new HashMap<String,Integer>();
            int evidenceBytes=0;
            for(var e:evidence){require(e.revision().equals(referenceRevision));ids.add(e.id());evidenceBytes+=e.quote().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
            require(evidenceBytes<=DEFAULT_BUDGET.maxEvidenceBytes());
            for(var relation:evidenceRelations){
                String key=relation.subquestionId()+"|"+relation.targetId();var t=targets.get(key);
                require(ids.contains(relation.evidenceId())&&t!=null&&t.role()==relation.role());
                used.add(relation.evidenceId());counts.merge(key,1,Integer::sum);
            }
            // 核验原文也必须有用途关系，不能把整批候选直接推给学生自己拼答案。
            require(used.size()==evidence.size());
            targets.forEach((key,target)->{int count=counts.getOrDefault(key,0);if(target.role()==EvidenceRole.CLAIM)require(count>=1);if(target.role()==EvidenceRole.CONFLICT)require(count>=2);});
        }
    }
    public record TaskSnapshot(int schemaVersion,UUID taskId,UUID turnId,UUID runId,long version,
            TaskStatus taskStatus,AgentRunState runState,boolean terminated,ResultEnvelope result,
            UUID continuationToken,Execution execution) {
        public TaskSnapshot {
            require(schemaVersion==SCHEMA_VERSION);required(taskId);required(turnId);required(runId);RuleAgentContract.version(version);
            required(taskStatus);required(runState);required(execution);require(!terminated||runState.isTerminal());
            int questions=result==null?0:result.items().stream().mapToInt(q->q.clarification().size()).sum();
            if(result!=null)require(result.publishedVersion()<=version);
            if(taskStatus==TaskStatus.ACTIVE){
                require(!terminated&&continuationToken==null);
                // 新run可以保留上轮已核验解释，但不能冒充本轮新发布的结论。
                if(result!=null)require(result.publishedVersion()<version);
            }else{
                require(terminated&&result!=null&&result.publishedVersion()==version&&result.originatingRunId().equals(runId));
                if(taskStatus==TaskStatus.WAITING_USER)require(runState==AgentRunState.INCOMPLETE&&questions==1&&continuationToken!=null);
                else{
                    require(questions==0&&continuationToken==null);
                    if(taskStatus==TaskStatus.RESOLVED)require(runState==AgentRunState.SUCCEEDED||runState==AgentRunState.INCOMPLETE);
                    if(taskStatus==TaskStatus.FAILED)require(runState==AgentRunState.FAILED&&hasKind(result,IssueKind.PROCESSING_FAILURE));
                    if(taskStatus==TaskStatus.STOPPED)require((runState==AgentRunState.CANCELLED||runState==AgentRunState.INCOMPLETE)
                            &&result.items().stream().flatMap(q->q.issues().stream()).anyMatch(i->Set.of(ReasonCode.CANCELLED,ReasonCode.BUDGET_EXCEEDED,ReasonCode.NO_PROGRESS).contains(i.reasonCode())));
                }
            }
            if(execution.replayCoverage()==ReplayCoverage.MISS&&taskStatus!=TaskStatus.ACTIVE)
                require(result.items().stream().flatMap(q->q.issues().stream()).anyMatch(i->i.reasonCode()==ReasonCode.REPLAY_NOT_COVERED));
        }
    }

    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="type")
    @JsonSubTypes({@JsonSubTypes.Type(value=Search.class,name="SEARCH_REVIEWED_RULES"),
            @JsonSubTypes.Type(value=Read.class,name="READ_VERIFIED_RULE"),
            @JsonSubTypes.Type(value=Ask.class,name="ASK_NECESSARY_CONDITION"),
            @JsonSubTypes.Type(value=Finish.class,name="FINISH_CHECK")})
    public sealed interface NextAction permits Search,Read,Ask,Finish {}
    public record Search(String subquestionId,String query) implements NextAction {
        public Search {identifier(subquestionId);RuleAgentContract.text(query,2048);}
    }
    public record Read(String subquestionId,List<UUID> handles) implements NextAction {
        public Read {
            identifier(subquestionId);
            handles=RuleAgentContract.items(handles,MIN_READ_HANDLES,DEFAULT_BUDGET.maxReadHandles());
            unique(handles);
        }
    }
    public record Ask(String subquestionId,String conditionId,String reason,List<String> supportingFactIds) implements NextAction {
        public Ask {identifier(subquestionId);identifier(conditionId);RuleAgentContract.text(reason,2048);supportingFactIds=RuleAgentContract.items(supportingFactIds,0,16);supportingFactIds.forEach(RuleAgentContract::identifier);unique(supportingFactIds);}
    }
    public record ProposedIssue(IssueKind kind,ReasonCode reasonCode) {
        public ProposedIssue {required(kind);required(reasonCode);require(reasonCode.kind==kind);}
    }
    public enum Comparison { GT,GE,EQ,LT,LE }
    public record InputSpan(int startInclusive,int endExclusive,String text,ConditionSource source) {
        public InputSpan {RuleAgentContract.text(text,128);required(source);require(startInclusive>=0&&endExclusive>startInclusive&&endExclusive-startInclusive==text.length());}
    }
    public record Operation(Comparison comparison,String factOperandId,String declaredValue,InputSpan sourceSpan) {
        public Operation {required(comparison);identifier(factOperandId);RuleAgentContract.text(declaredValue,32);required(sourceSpan);require(sourceSpan.text().equals(declaredValue));
            require(declaredValue.matches("[0-9]{1,9}(\\.[0-9]{1,6})?"));}
    }
    public record Proposal(String subquestionId,List<String> factIds,List<ProposedIssue> issues,Operation operation) {
        public Proposal {identifier(subquestionId);factIds=RuleAgentContract.items(factIds,0,16);factIds.forEach(RuleAgentContract::identifier);unique(factIds);issues=RuleAgentContract.items(issues,0,8);require(!factIds.isEmpty()||!issues.isEmpty());if(operation!=null)require(!factIds.isEmpty());}
    }
    public record Finish(List<Proposal> items) implements NextAction {
        public Finish {items=RuleAgentContract.items(items,1,4);unique(items.stream().map(Proposal::subquestionId).toList());}
    }

    // owner仅属于服务端任务边界，不放入学生快照或模型载荷。
    public record Owner(UUID actorId,UUID sessionNonce) {public Owner {required(actorId);required(sessionNonce);}}
    public enum RequestAction { CREATE,MESSAGE,CANCEL }
    public record RequestKey(Owner owner,RequestAction action,UUID clientRequestId) {
        public RequestKey {required(owner);required(action);required(clientRequestId);}
    }
    public record CreateTask(String question,UUID clientRequestId) {public CreateTask {RuleAgentContract.text(question,2048);required(clientRequestId);}}
    public record Message(String message,UUID continuationToken,long expectedVersion,UUID clientRequestId) {
        public Message {RuleAgentContract.text(message,2048);required(continuationToken);RuleAgentContract.version(expectedVersion);required(clientRequestId);}
    }
    public record Cancel(UUID runId,long expectedVersion,UUID clientRequestId) {
        public Cancel {required(runId);RuleAgentContract.version(expectedVersion);required(clientRequestId);}
    }
    public enum ObservationStatus { SUCCEEDED,NO_MATCH,REJECTED,UNAVAILABLE }
    public enum DataLevel { NONE,CANDIDATE,VERIFIED_EVIDENCE,VERIFIED_FACT }
    public record Candidate(UUID handle,String excerpt,String revision) {public Candidate {required(handle);RuleAgentContract.text(excerpt,4096);RuleAgentContract.text(revision,128);}}
    public record ToolObservation(UUID actionId,UUID runId,int step,ObservationStatus status,ReasonCode reasonCode,
            DataLevel dataLevel,String catalogueVersion,List<Candidate> candidates,List<Evidence> evidence,List<String> factIds) {
        public ToolObservation {
            required(actionId);required(runId);require(step>=1&&step<=6);required(status);required(dataLevel);RuleAgentContract.text(catalogueVersion,128);
            candidates=RuleAgentContract.items(candidates,0,20);evidence=RuleAgentContract.items(evidence,0,8);factIds=RuleAgentContract.items(factIds,0,16);factIds.forEach(RuleAgentContract::identifier);unique(factIds);
            if(status!=ObservationStatus.SUCCEEDED){
                require(reasonCode!=null&&dataLevel==DataLevel.NONE&&candidates.isEmpty()&&evidence.isEmpty()&&factIds.isEmpty());
                // NO_MATCH是健康检索的观察，UNAVAILABLE是执行故障，不能互相冒充。
                if(status==ObservationStatus.NO_MATCH)require(reasonCode==ReasonCode.RETRIEVAL_NO_MATCH);
                if(status==ObservationStatus.UNAVAILABLE)require(Set.of(ReasonCode.RETRIEVAL_UNAVAILABLE,ReasonCode.AUDIT_UNAVAILABLE).contains(reasonCode));
                if(status==ObservationStatus.REJECTED)require(Set.of(ReasonCode.UNKNOWN_TOOL,ReasonCode.EVIDENCE_CHANGED,ReasonCode.MATERIAL_SCOPE_MISMATCH,ReasonCode.BUDGET_EXCEEDED).contains(reasonCode));
            }
            else{
                require(reasonCode==null&&dataLevel!=DataLevel.NONE);
                if(dataLevel==DataLevel.CANDIDATE)require(!candidates.isEmpty()&&evidence.isEmpty()&&factIds.isEmpty());
                else require(candidates.isEmpty()&&!evidence.isEmpty());
                if(dataLevel==DataLevel.VERIFIED_EVIDENCE)require(factIds.isEmpty());
                if(dataLevel==DataLevel.VERIFIED_FACT)require(!factIds.isEmpty());
            }
        }
    }
    public static boolean canTransition(String from,String to){
        TaskStatus a=TaskStatus.valueOf(from),b=TaskStatus.valueOf(to);
        if(a==b)return true;
        return a==TaskStatus.ACTIVE||a==TaskStatus.WAITING_USER&&(b==TaskStatus.ACTIVE||b==TaskStatus.STOPPED||b==TaskStatus.FAILED);
    }
    private static boolean hasKind(ResultEnvelope r,IssueKind kind){return r.items().stream().flatMap(q->q.issues().stream()).anyMatch(i->i.kind()==kind);}
    private static Coverage coverage(List<Subquestion> items){return items.stream().allMatch(q->q.coverage()==Coverage.COMPLETE)?Coverage.COMPLETE:items.stream().anyMatch(q->!q.claims().isEmpty())?Coverage.PARTIAL:Coverage.NONE;}
    private static void target(Map<String,Target> targets,String question,String id,EvidenceRole role){require(targets.putIfAbsent(question+"|"+id,new Target(role))==null);}
    private static void version(long version){require(version>=1&&version<=MAX_SAFE_VERSION);}
    private static void identifier(String text){RuleAgentContract.text(text,128);require(text.matches("[A-Za-z0-9][A-Za-z0-9_./:-]{0,127}"));}
    private static void text(String value,int limit){
        require(value!=null&&!value.isBlank()&&value.length()<=limit);
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c))require(++i<value.length()&&Character.isLowSurrogate(value.charAt(i)));
            else require(!Character.isLowSurrogate(c));
        }
    }
    private static <T> List<T> items(List<T> list,int min,int max){required(list);require(list.size()>=min&&list.size()<=max);list.forEach(RuleAgentContract::required);return List.copyOf(list);}
    private static void unique(List<?> list){require(new HashSet<>(list).size()==list.size());}
    private static void required(Object value){require(value!=null);}
    private static void require(boolean valid){if(!valid)throw new IllegalArgumentException("非法规则查证合同");}
}
