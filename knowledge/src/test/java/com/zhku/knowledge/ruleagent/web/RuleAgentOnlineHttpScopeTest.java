package com.zhku.knowledge.ruleagent.web;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.ruleagent.RuleAgentRunner;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 纯边界测试：不创建 Provider、HTTP、数据库或浏览器。 */
class RuleAgentOnlineHttpScopeTest {
    static final String QUESTION = "我这门选修课没过，能不能重修？";
    final Owner owner = new Owner(UUID.randomUUID(), UUID.randomUUID());
    final UUID task = UUID.randomUUID();

    RuleAgentRunner.Input input(Owner who, UUID id, long version, String question, Map<String,String> conditions) {
        return new RuleAgentRunner.Input(who,id,version,question,List.of(new RuleAgentRunner.Question("q1",question)),conditions);
    }
    RuleAgentRunner.Input first() { return input(owner,task,2,QUESTION,Map.of()); }
    RuleAgentRunner.Input second() { return input(owner,task,4,QUESTION,Map.of("courseType","PUBLIC")); }
    RuleAgentRunner.Outcome outcome(TaskStatus status, Mode mode, Coverage coverage, long version) {
        UUID run = UUID.randomUUID();
        boolean waiting = status == TaskStatus.WAITING_USER;
        var issue = new Issue("condition",IssueKind.CLARIFICATION,ReasonCode.MISSING_CONDITION,"确认课程类别","补充课程类别");
        var error = new Issue("failure",IssueKind.PROCESSING_FAILURE,ReasonCode.MODEL_UNAVAILABLE,"处理失败","人工核实");
        List<Evidence> evidence = coverage == Coverage.COMPLETE ? List.of(new Evidence("e1",UUID.randomUUID(),UUID.randomUUID(),"2025.9.2","a".repeat(64),0,4,"审核条款",UUID.randomUUID())) : List.of();
        var item = new Subquestion("q1",coverage,coverage == Coverage.COMPLETE ? List.of(new Claim("c1","f1","合成边界测试结论",Map.of(),ConditionSource.USER_DECLARED)) : List.of(),
                waiting ? List.of(issue) : status == TaskStatus.FAILED ? List.of(error) : List.of(),
                waiting ? List.of(new Clarification("courseType","课程类别？",List.of("PUBLIC","PROFESSIONAL"))) : List.of());
        var result = new ResultEnvelope(version,run,"synthetic-scope-only","2025.9.2",coverage,List.of(item),evidence,
                evidence.isEmpty() ? List.of() : List.of(new EvidenceRelation("e1","q1",EvidenceRole.CLAIM,"c1")));
        var execution = new Execution(mode,mode == Mode.ONLINE ? 1 : 0,0,mode == Mode.ONLINE,
                mode == Mode.ONLINE ? AnswerSource.LIVE_AGENT : AnswerSource.NONE,
                mode == Mode.ONLINE ? ReplayCoverage.NOT_APPLICABLE : ReplayCoverage.HIT);
        return new RuleAgentRunner.Outcome(new TaskSnapshot(3,task,UUID.randomUUID(),run,version,status,
                waiting ? AgentRunState.INCOMPLETE : status == TaskStatus.FAILED ? AgentRunState.FAILED : AgentRunState.SUCCEEDED,
                true,result,waiting ? UUID.randomUUID() : null,execution),List.of());
    }
    void waiting(RuleAgentOnlineHttpScope scope) { scope.begin(first()); scope.complete(outcome(TaskStatus.WAITING_USER,Mode.ONLINE,Coverage.NONE,2)); }
    void rejectsBeforeProvider(RuleAgentOnlineHttpScope scope, RuleAgentRunner.Input value) {
        var created = new AtomicInteger();
        assertThatThrownBy(() -> { scope.begin(value); created.incrementAndGet(); }).isInstanceOf(IllegalStateException.class);
        assertThat(created).hasValue(0);
    }
    @Test void exactAskPublicCompleteIsAccepted() {
        var scope = new RuleAgentOnlineHttpScope(); scope.begin(first(),QUESTION);
        scope.complete(outcome(TaskStatus.WAITING_USER,Mode.ONLINE,Coverage.NONE,2)); scope.begin(second(),"PUBLIC");
        scope.complete(outcome(TaskStatus.RESOLVED,Mode.ONLINE,Coverage.COMPLETE,4));
        assertThatCode(scope::requireSuccessfulAskPublicComplete).doesNotThrowAnyException();
    }
    @Test void changedQuestionIsRejectedBeforeProviderConstruction() {
        for (String question : List.of(QUESTION+" ","公共选修课可以重修吗？")) rejectsBeforeProvider(new RuleAgentOnlineHttpScope(),input(owner,task,2,question,Map.of()));
    }
    @Test void initialConditionsAreRejectedBeforeProviderConstruction() { rejectsBeforeProvider(new RuleAgentOnlineHttpScope(),second()); }
    @Test void concurrentRunIsRejectedBeforeProviderConstruction() { var scope = new RuleAgentOnlineHttpScope(); scope.begin(first()); rejectsBeforeProvider(scope,first()); }
    @Test void differentActorSessionAndTaskCannotReuseTheAllowance() {
        for (var value : List.of(input(new Owner(UUID.randomUUID(),owner.sessionNonce()),task,4,QUESTION,Map.of("courseType","PUBLIC")),
                input(new Owner(owner.actorId(),UUID.randomUUID()),task,4,QUESTION,Map.of("courseType","PUBLIC")),
                input(owner,UUID.randomUUID(),4,QUESTION,Map.of("courseType","PUBLIC")))) {
            var scope = new RuleAgentOnlineHttpScope(); waiting(scope); rejectsBeforeProvider(scope,value);
        }
    }
    @Test void onlyTheSinglePublicConditionCanContinue() {
        for (var conditions : List.of(Map.<String,String>of(),Map.of("courseType","PROFESSIONAL"),Map.of("other","PUBLIC"),Map.of("courseType","PUBLIC","other","PUBLIC"))) {
            var scope = new RuleAgentOnlineHttpScope(); waiting(scope); rejectsBeforeProvider(scope,input(owner,task,4,QUESTION,conditions));
        }
    }
    @Test void normalizedPublicCannotHideAnUnapprovedRawReply() {
        var scope = new RuleAgentOnlineHttpScope(); waiting(scope);
        assertThatThrownBy(() -> scope.begin(second(),"公选课")).isInstanceOf(IllegalStateException.class);
    }
    @Test void staleOrSkippedVersionCannotStartProvider() { var scope = new RuleAgentOnlineHttpScope(); waiting(scope); rejectsBeforeProvider(scope,input(owner,task,2,QUESTION,Map.of("courseType","PUBLIC"))); }
    @Test void replayWaitingCannotAuthorizeSecondRun() { var scope = new RuleAgentOnlineHttpScope(); scope.begin(first()); scope.complete(outcome(TaskStatus.WAITING_USER,Mode.ENGINEERING_REPLAY,Coverage.NONE,2)); rejectsBeforeProvider(scope,second()); }
    @Test void resolvedFirstRunCannotAuthorizeAnExtraQuestion() { var scope = new RuleAgentOnlineHttpScope(); scope.begin(first()); scope.complete(outcome(TaskStatus.RESOLVED,Mode.ONLINE,Coverage.COMPLETE,2)); rejectsBeforeProvider(scope,second()); assertThatThrownBy(scope::requireSuccessfulAskPublicComplete).isInstanceOf(IllegalStateException.class); }
    @Test void failedFirstRunCannotBeRetried() { var scope = new RuleAgentOnlineHttpScope(); scope.begin(first()); scope.complete(outcome(TaskStatus.FAILED,Mode.ONLINE,Coverage.NONE,2)); rejectsBeforeProvider(scope,first()); rejectsBeforeProvider(scope,second()); }
    @Test void thirdRunCannotConsumeMoreProviderAllowance() { var scope = new RuleAgentOnlineHttpScope(); waiting(scope); scope.begin(second()); scope.complete(outcome(TaskStatus.RESOLVED,Mode.ONLINE,Coverage.COMPLETE,4)); rejectsBeforeProvider(scope,input(owner,task,6,QUESTION,Map.of("courseType","PUBLIC"))); }
    @Test void incompleteAndFailedSecondRunsNeverCountAsSuccess() {
        for (var status : List.of(TaskStatus.RESOLVED,TaskStatus.FAILED)) {
            var scope = new RuleAgentOnlineHttpScope(); waiting(scope); scope.begin(second()); scope.complete(outcome(status,Mode.ONLINE,Coverage.NONE,4));
            assertThatThrownBy(scope::requireSuccessfulAskPublicComplete).isInstanceOf(IllegalStateException.class); rejectsBeforeProvider(scope,second());
        }
    }
    @Test void noRunAndWaitingAloneDoNotCountAsComplete() { assertThatThrownBy(new RuleAgentOnlineHttpScope()::requireSuccessfulAskPublicComplete).isInstanceOf(IllegalStateException.class); var scope = new RuleAgentOnlineHttpScope(); waiting(scope); assertThatThrownBy(scope::requireSuccessfulAskPublicComplete).isInstanceOf(IllegalStateException.class); }
}
