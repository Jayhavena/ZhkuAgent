package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.ruleagent.RuleAgentRunner;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 纯内存边界测试：合成快照不代表真实问答通过，不创建端口、工厂、数据库或 Provider。 */
class RuleAgentThreeQuestionScopeTest {
    private static final String ERROR = "三题隔离执行范围已关闭或不匹配";
    private static final List<String> QUESTIONS = List.of(
            "有人说公选必须上满16学时，24学时的安排肯定违规，是这样吗？",
            "我那门选修挂了，下次直接再上一遍就好了吧？",
            "这学期公选补选今晚几点关？现在还能帮我补上吗？");
    private final Owner owner = new Owner(UUID.randomUUID(), UUID.randomUUID());
    private record Round(RuleAgentRunner.Input input, UUID runId) {}

    private ObjectNode plan() {
        var plan = new ObjectMapper().createObjectNode();
        plan.put("schemaVersion", 1).put("maximumTasks", 3).put("maximumRuns", 4);
        var cases = plan.putArray("cases");
        for (int i = 0; i < 3; i++) {
            var item = cases.addObject().put("id", List.of("E02", "E04", "E13").get(i))
                    .put("question", QUESTIONS.get(i)).put("maximumRuns", i == 1 ? 2 : 1);
            if (i == 1) item.put("reply", "PUBLIC");
        }
        return plan;
    }
    private RuleAgentThreeQuestionScope scope() { return new RuleAgentThreeQuestionScope(plan()); }
    private RuleAgentRunner.Input input(Owner who, UUID task, long version, String question, Map<String,String> conditions) {
        return new RuleAgentRunner.Input(who, task, version, question,
                List.of(new RuleAgentRunner.Question("q1", question)), conditions);
    }
    private Round first(int index) { return new Round(input(owner, UUID.randomUUID(), 2, QUESTIONS.get(index), Map.of()), UUID.randomUUID()); }
    private Round continuation(Round first) {
        return new Round(input(owner, first.input().taskId(), 4, QUESTIONS.get(1), Map.of("courseType", "PUBLIC")), UUID.randomUUID());
    }
    private void begin(RuleAgentThreeQuestionScope scope, Round round) {
        scope.begin(round.input(), round.input().version() == 4 ? "PUBLIC" : round.input().question(), round.runId());
    }
    private RuleAgentRunner.Outcome outcome(Round round, TaskStatus status) {
        return outcome(round, status, "courseType", List.of("PUBLIC", "PROFESSIONAL"), Mode.ONLINE);
    }
    private RuleAgentRunner.Outcome outcome(Round round, TaskStatus status, String condition, List<String> values, Mode mode) {
        boolean waiting = status == TaskStatus.WAITING_USER;
        Issue issue = switch (status) {
            case WAITING_USER -> new Issue("condition", IssueKind.CLARIFICATION, ReasonCode.MISSING_CONDITION, "合成追问", "补充类别");
            case FAILED -> new Issue("failure", IssueKind.PROCESSING_FAILURE, ReasonCode.MODEL_UNAVAILABLE, "合成失败", "人工核查");
            case STOPPED -> new Issue("stopped", IssueKind.PROCESSING_FAILURE, ReasonCode.NO_PROGRESS, "合成停止", "人工核查");
            default -> new Issue("gap", IssueKind.DATA_GAP, ReasonCode.CURRENT_NOTICE_MISSING, "合成缺口", "核查通知");
        };
        var item = new Subquestion("q1", Coverage.NONE, List.of(), List.of(issue),
                waiting ? List.of(new Clarification(condition, "类别？", values)) : List.of());
        var result = new ResultEnvelope(round.input().version(), round.runId(), "synthetic-scope-only", "2025.9.2",
                Coverage.NONE, List.of(item), List.of(), List.of());
        var state = waiting ? AgentRunState.INCOMPLETE : status == TaskStatus.FAILED ? AgentRunState.FAILED
                : status == TaskStatus.STOPPED ? AgentRunState.INCOMPLETE : AgentRunState.SUCCEEDED;
        var execution = new Execution(mode, mode == Mode.ONLINE ? 1 : 0, 0, mode == Mode.ONLINE,
                AnswerSource.NONE, mode == Mode.ONLINE ? ReplayCoverage.NOT_APPLICABLE : ReplayCoverage.HIT);
        return new RuleAgentRunner.Outcome(new TaskSnapshot(3, round.input().taskId(), UUID.randomUUID(), round.runId(),
                round.input().version(), status, state, true, result, waiting ? UUID.randomUUID() : null, execution), List.of());
    }
    private void complete(RuleAgentThreeQuestionScope scope, Round round, TaskStatus status) {
        scope.complete(round.input(), outcome(round, status));
    }
    private void finishE02(RuleAgentThreeQuestionScope scope) {
        var first = first(0); begin(scope, first); complete(scope, first, TaskStatus.RESOLVED);
    }
    private Round waitingE04(RuleAgentThreeQuestionScope scope) {
        finishE02(scope);
        var round = first(1); begin(scope, round); complete(scope, round, TaskStatus.WAITING_USER);
        return round;
    }
    private void rejected(RuleAgentThreeQuestionScope scope, Runnable action) {
        var portCreations = new AtomicInteger();
        assertThatThrownBy(() -> { action.run(); portCreations.incrementAndGet(); })
                .isInstanceOf(IllegalStateException.class).hasMessage(ERROR);
        assertThat(portCreations).hasValue(0);
        assertThat(scope.closed()).isTrue();
        assertThatThrownBy(() -> begin(scope, first(0))).isInstanceOf(IllegalStateException.class).hasMessage(ERROR);
    }

    @Test void exactThreeQuestionsAndPublicContinuationConsumeFourRuns() {
        var scope = scope();
        var e04 = waitingE04(scope);
        var reply = continuation(e04); begin(scope, reply); complete(scope, reply, TaskStatus.RESOLVED);
        var e13 = first(2); begin(scope, e13); complete(scope, e13, TaskStatus.RESOLVED);
        assertThat(scope.runs()).isEqualTo(4);
        assertThat(scope.tasks()).isEqualTo(3);
        assertThat(scope.closed()).isTrue();
    }
    @Test void resolvedOrStoppedE04WithoutWaitingAdvancesWithoutInventingAReply() {
        for (var status : List.of(TaskStatus.RESOLVED, TaskStatus.STOPPED)) {
            var scope = scope(); finishE02(scope);
            var e04 = first(1); begin(scope, e04); complete(scope, e04, status);
            var e13 = first(2); begin(scope, e13); complete(scope, e13, TaskStatus.RESOLVED);
            assertThat(scope.runs()).isEqualTo(3);
            assertThat(scope.tasks()).isEqualTo(3);
            assertThat(scope.closed()).isTrue();
        }
    }
    @Test void onlineRunnerFailureStopsTheBatchWithoutMaskingTheOriginalFailureStatus() {
        var scope = scope();
        finishE02(scope);
        var e04 = first(1); begin(scope, e04); complete(scope, e04, TaskStatus.RESOLVED);
        var e13 = first(2); begin(scope, e13);
        var failed = outcome(e13, TaskStatus.FAILED);
        var priorScope = RuleAgentThreeQuestionHostTest.scope;
        var priorStopped = RuleAgentThreeQuestionHostTest.stopped;
        try {
            RuleAgentThreeQuestionHostTest.scope = scope;
            RuleAgentThreeQuestionHostTest.stopped = false;
            assertThat(RuleAgentThreeQuestionHostTest.failClosedTerminal(failed)).isTrue();
            assertThat(failed.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(failed.snapshot().result().items().getFirst().issues().getFirst().reasonCode())
                    .isEqualTo(ReasonCode.MODEL_UNAVAILABLE);
            assertThat(RuleAgentThreeQuestionHostTest.stopped).isTrue();
            assertThat(scope.closed()).isTrue();
            assertThat(scope.runs()).isEqualTo(3);
            assertThatThrownBy(() -> begin(scope, first(2)))
                    .isInstanceOf(IllegalStateException.class).hasMessage(ERROR);
        } finally {
            RuleAgentThreeQuestionHostTest.scope = priorScope;
            RuleAgentThreeQuestionHostTest.stopped = priorStopped;
        }
    }
    @Test void initialConditionsAreRejected() {
        var scope = scope(); var original = first(0);
        var changed = input(owner, original.input().taskId(), 2, QUESTIONS.get(0), Map.of("courseType", "PUBLIC"));
        rejected(scope, () -> scope.begin(changed, QUESTIONS.get(0), original.runId()));
        assertThat(scope.runs()).isZero(); assertThat(scope.tasks()).isZero();
    }
    @Test void changedQuestionOrRawQuestionIsRejected() {
        for (boolean changedQuestion : List.of(false, true)) {
            var scope = scope(); var original = first(0);
            var value = changedQuestion ? input(owner, original.input().taskId(), 2, QUESTIONS.get(0) + " ", Map.of()) : original.input();
            rejected(scope, () -> scope.begin(value, QUESTIONS.get(0) + " ", original.runId()));
        }
    }
    @Test void questionsCannotBeRunOutOfOrder() {
        var scope = scope(); rejected(scope, () -> begin(scope, first(1)));
    }
    @Test void normalizedPublicCannotHideAnUnapprovedRawReply() {
        for (String raw : List.of("公选课", "PUBLIC ", "PROFESSIONAL", QUESTIONS.get(1))) {
            var scope = scope(); var reply = continuation(waitingE04(scope));
            rejected(scope, () -> scope.begin(reply.input(), raw, reply.runId()));
            assertThat(scope.runs()).isEqualTo(2);
        }
    }
    @Test void onlyTheExactPublicConditionMapMayContinue() {
        for (var conditions : List.of(Map.<String,String>of(), Map.of("courseType", "PROFESSIONAL"),
                Map.of("other", "PUBLIC"), Map.of("courseType", "PUBLIC", "other", "PUBLIC"))) {
            var scope = scope(); var e04 = waitingE04(scope);
            var changed = input(owner, e04.input().taskId(), 4, QUESTIONS.get(1), conditions);
            rejected(scope, () -> scope.begin(changed, "PUBLIC", UUID.randomUUID()));
        }
    }
    @Test void actorAndSessionAreBoundAcrossAllTasks() {
        for (var changedOwner : List.of(new Owner(UUID.randomUUID(), owner.sessionNonce()), new Owner(owner.actorId(), UUID.randomUUID()))) {
            var scope = scope(); finishE02(scope);
            var changed = input(changedOwner, UUID.randomUUID(), 2, QUESTIONS.get(1), Map.of());
            rejected(scope, () -> scope.begin(changed, QUESTIONS.get(1), UUID.randomUUID()));
        }
    }
    @Test void continuationCannotChangeOwnerSessionOrTask() {
        for (int change = 0; change < 3; change++) {
            var scope = scope(); var e04 = waitingE04(scope);
            Owner who = change == 0 ? new Owner(UUID.randomUUID(), owner.sessionNonce())
                    : change == 1 ? new Owner(owner.actorId(), UUID.randomUUID()) : owner;
            var changed = input(who, change == 2 ? UUID.randomUUID() : e04.input().taskId(), 4, QUESTIONS.get(1), Map.of("courseType", "PUBLIC"));
            rejected(scope, () -> scope.begin(changed, "PUBLIC", UUID.randomUUID()));
        }
    }
    @Test void newScenarioCannotReuseAnEarlierTask() {
        var scope = scope(); var first = first(0); begin(scope, first); complete(scope, first, TaskStatus.RESOLVED);
        var reused = input(owner, first.input().taskId(), 2, QUESTIONS.get(1), Map.of());
        rejected(scope, () -> scope.begin(reused, QUESTIONS.get(1), UUID.randomUUID()));
    }
    @Test void initialAndContinuationVersionsCannotBeSkippedOrReused() {
        for (long version : List.of(1L, 3L, 4L, 6L)) {
            var scope = scope(); var changed = input(owner, UUID.randomUUID(), version, QUESTIONS.get(0), Map.of());
            rejected(scope, () -> scope.begin(changed, QUESTIONS.get(0), UUID.randomUUID()));
        }
        for (long version : List.of(2L, 3L, 5L, 6L)) {
            var scope = scope(); var e04 = waitingE04(scope);
            var changed = input(owner, e04.input().taskId(), version, QUESTIONS.get(1), Map.of("courseType", "PUBLIC"));
            rejected(scope, () -> scope.begin(changed, "PUBLIC", UUID.randomUUID()));
        }
    }
    @Test void runIdentifiersCannotBeReused() {
        var scope = scope(); var first = first(0); begin(scope, first); complete(scope, first, TaskStatus.RESOLVED);
        var next = first(1); rejected(scope, () -> scope.begin(next.input(), QUESTIONS.get(1), first.runId()));
    }
    @Test void concurrentBeginClosesBatchWithoutIncreasingCounters() {
        var scope = scope(); var first = first(0); begin(scope, first);
        rejected(scope, () -> begin(scope, first(0)));
        assertThat(scope.runs()).isEqualTo(1); assertThat(scope.tasks()).isEqualTo(1);
    }
    @Test void explicitFailureCannotBeRetriedAndDoesNotRefillCounters() {
        var scope = scope(); begin(scope, first(0)); scope.fail(); scope.fail();
        rejected(scope, () -> begin(scope, first(0)));
        assertThat(scope.runs()).isEqualTo(1); assertThat(scope.tasks()).isEqualTo(1);
    }
    @Test void failedOutcomeSealsBatch() {
        var scope = scope(); var round = first(0); begin(scope, round);
        rejected(scope, () -> complete(scope, round, TaskStatus.FAILED));
    }
    @Test void e02AndE13CannotAuthorizeClarification() {
        var startScope = scope(); var e02 = first(0); begin(startScope, e02);
        rejected(startScope, () -> complete(startScope, e02, TaskStatus.WAITING_USER));
        var endScope = scope(); finishE02(endScope); var e04 = first(1); begin(endScope, e04); complete(endScope, e04, TaskStatus.RESOLVED);
        var e13 = first(2); begin(endScope, e13);
        rejected(endScope, () -> complete(endScope, e13, TaskStatus.WAITING_USER));
    }
    @Test void e04ClarificationMustMatchTheApprovedConditionAndBothChoices() {
        for (String condition : List.of("other", "courseType")) {
            var scope = scope(); finishE02(scope); var e04 = first(1); begin(scope, e04);
            var values = condition.equals("other") ? List.of("PUBLIC", "PROFESSIONAL") : List.of("PUBLIC", "UNKNOWN");
            rejected(scope, () -> scope.complete(e04.input(), outcome(e04, TaskStatus.WAITING_USER, condition, values, Mode.ONLINE)));
        }
    }
    @Test void secondE04RunCannotAskAgainOrPermitAThirdRun() {
        var scope = scope(); var reply = continuation(waitingE04(scope)); begin(scope, reply);
        rejected(scope, () -> complete(scope, reply, TaskStatus.WAITING_USER));
    }
    @Test void replayOutcomeCannotAuthorizeContinuation() {
        var scope = scope(); finishE02(scope); var e04 = first(1); begin(scope, e04);
        rejected(scope, () -> scope.complete(e04.input(), outcome(e04, TaskStatus.WAITING_USER,
                "courseType", List.of("PUBLIC", "PROFESSIONAL"), Mode.ENGINEERING_REPLAY)));
    }
    @Test void completionMustMatchAdmittedInputAndRun() {
        for (boolean changedInput : List.of(false, true)) {
            var scope = scope(); var round = first(0); begin(scope, round);
            var foreign = changedInput ? first(0) : new Round(round.input(), UUID.randomUUID());
            rejected(scope, () -> scope.complete(changedInput ? foreign.input() : round.input(), outcome(foreign, TaskStatus.RESOLVED)));
        }
    }
    @Test void completionBeforeBeginOrRepeatedCompletionIsRejected() {
        var empty = scope(); var round = first(0);
        rejected(empty, () -> complete(empty, round, TaskStatus.RESOLVED));
        var scope = scope(); begin(scope, round); complete(scope, round, TaskStatus.RESOLVED);
        rejected(scope, () -> complete(scope, round, TaskStatus.RESOLVED));
    }
    @Test void completedBatchRejectsAdditionalTasks() {
        var scope = scope(); var reply = continuation(waitingE04(scope)); begin(scope, reply); complete(scope, reply, TaskStatus.RESOLVED);
        var e13 = first(2); begin(scope, e13); complete(scope, e13, TaskStatus.RESOLVED);
        rejected(scope, () -> begin(scope, first(0)));
        assertThat(scope.runs()).isEqualTo(4); assertThat(scope.tasks()).isEqualTo(3);
    }
    @Test void planMutationCannotExpandAnAlreadyConstructedScope() {
        var plan = plan(); var scope = new RuleAgentThreeQuestionScope(plan);
        ((ObjectNode) plan.path("cases").get(0)).put("question", "变更后的问题");
        var first = first(0); begin(scope, first); complete(scope, first, TaskStatus.RESOLVED);
        assertThat(scope.closed()).isFalse();
    }
    @Test void malformedOrExpandedPlanIsRejectedWithFixedError() {
        for (String field : List.of("maximumTasks", "maximumRuns", "schemaVersion")) {
            var plan = plan(); plan.put(field, 99);
            assertThatThrownBy(() -> new RuleAgentThreeQuestionScope(plan)).isInstanceOf(IllegalStateException.class).hasMessage(ERROR);
        }
        var plan = plan(); ((ObjectNode) plan.path("cases").get(1)).put("reply", "PROFESSIONAL");
        assertThatThrownBy(() -> new RuleAgentThreeQuestionScope(plan)).isInstanceOf(IllegalStateException.class).hasMessage(ERROR);
        assertThatThrownBy(() -> new RuleAgentThreeQuestionScope(null)).isInstanceOf(IllegalStateException.class).hasMessage(ERROR);
    }
}
