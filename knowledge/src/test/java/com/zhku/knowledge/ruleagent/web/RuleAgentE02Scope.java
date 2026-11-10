package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhku.knowledge.ruleagent.RuleAgentRunner;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/**
 * 仅供隔离宿主使用：E02 单题单run的范围闸门。
 * 与三题Scope的差别只在“没有第二题、没有澄清续跑额度”，不承担费用授权或产品通过判定。
 */
final class RuleAgentE02Scope {
    private static final String REJECTED = "E02单题执行范围已关闭或不匹配";
    private final String question;
    private final String caseId;
    private Owner owner;
    private UUID taskId;
    private UUID runId;
    private RuleAgentRunner.Input inFlight;
    private boolean closed;

    RuleAgentE02Scope(JsonNode plan) { this(plan, "E02"); }

    /** E13复用同一单题生命周期；必须先过各自Policy，不能放宽成任意题。 */
    RuleAgentE02Scope(JsonNode plan, String caseId) {
        if (!Set.of("E02", "E13").contains(caseId)) throw reject();
        if (caseId.equals("E13")) RuleAgentE13Policy.validate(plan);
        if (plan == null || !plan.isObject() || !exactInteger(plan.path("schemaVersion"), 1)
                || !exactInteger(plan.path("maximumTasks"), 1) || !exactInteger(plan.path("maximumRuns"), 1)
                || !plan.path("cases").isArray() || plan.path("cases").size() != 1) throw reject();
        var item = plan.path("cases").get(0);
        if (!item.isObject() || !caseId.equals(item.path("id").asText())
                || !item.path("question").isTextual() || item.path("question").asText().isBlank()
                || item.has("reply") || !exactInteger(item.path("maximumRuns"), 1)
                || !exactInteger(item.path("maximumCalls"), 5)) throw reject();
        if (plan.path("cases").findValues("reply").size() != 0) throw reject();
        // 复制字符串，防止审包后外部修改计划改变执行范围。
        question = item.path("question").asText();
        this.caseId = caseId;
    }

    synchronized void begin(RuleAgentRunner.Input input, String rawMessage, UUID run) {
        if (closed || inFlight != null || input == null || run == null || runId != null) throw reject();
        // 单题单run：只允许一次初始提交，version=2、无条件、问题与审包原句完全一致。
        if ((owner != null && !owner.equals(input.owner())) || !question.equals(input.question())
                || !input.questions().equals(List.of(new RuleAgentRunner.Question("q1", question)))
                || input.version() != 2 || !input.userConditions().isEmpty()
                || !question.equals(rawMessage) || taskId != null) throw reject();
        owner = input.owner();
        taskId = input.taskId();
        runId = run;
        inFlight = input;
    }

    synchronized void complete(RuleAgentRunner.Input input, RuleAgentRunner.Outcome outcome) {
        if (closed || inFlight == null || !inFlight.equals(input) || outcome == null || outcome.snapshot() == null) throw reject();
        var snapshot = outcome.snapshot();
        if (!taskId.equals(snapshot.taskId()) || !runId.equals(snapshot.runId())
                || snapshot.version() != input.version() || !snapshot.terminated()
                || snapshot.execution().mode() != Mode.ONLINE || !snapshot.execution().providerResponseReceived()
                || snapshot.execution().modelAttemptCount() < 1
                || snapshot.taskStatus() == TaskStatus.FAILED || snapshot.taskStatus() == TaskStatus.ACTIVE) throw reject();
        // 本计划没有第二个run，因此任何追问/待补充条件都不属于可完成状态，只按安全停下记。
        if (snapshot.taskStatus() == TaskStatus.WAITING_USER) throw reject();
        if (caseId.equals("E13")) {
            try { RuleAgentE13Policy.terminal(snapshot); }
            catch (RuntimeException invalid) { throw reject(); }
        }
        // 终态即耗尽该题唯一机会；安全拒答不等于产品完成，由宿主另记判定。
        inFlight = null;
        closed = true;
    }

    synchronized void fail() {
        closed = true;
        inFlight = null;
    }

    synchronized int runs() { return runId == null ? 0 : 1; }
    synchronized int tasks() { return taskId == null ? 0 : 1; }
    synchronized boolean closed() { return closed; }
    /** 只给宿主回读用：页面提交后宿主据此读同一任务快照，不代替页面发POST。 */
    synchronized UUID taskId() { return taskId; }
    synchronized Owner owner() { return owner; }

    private static boolean exactInteger(JsonNode node, int expected) {
        return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() == expected;
    }

    private IllegalStateException reject() {
        // 拒绝也永久封批，避免把同一授权当作可重试的余额池；错误不带问题、身份或 token。
        fail();
        return new IllegalStateException(REJECTED);
    }
}
