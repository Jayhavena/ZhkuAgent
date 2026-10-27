package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhku.knowledge.ruleagent.RuleAgentRunner;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 仅供隔离宿主使用；它限制已审自然原句，不承担费用授权或产品通过判定。 */
final class RuleAgentThreeQuestionScope {
    private static final String REJECTED = "三题隔离执行范围已关闭或不匹配";
    private record Scenario(String id, String question) {}
    private final List<Scenario> scenarios;
    private final Set<UUID> taskIds = new HashSet<>();
    private final Set<UUID> runIds = new HashSet<>();
    private Owner owner;
    private UUID scenarioTask;
    private RuleAgentRunner.Input inFlight;
    private UUID inFlightRun;
    private int scenarioIndex;
    private boolean waiting;
    private boolean closed;

    RuleAgentThreeQuestionScope(JsonNode plan) {
        if (plan == null || !plan.isObject() || !exactInteger(plan.path("schemaVersion"), 1)
                || !exactInteger(plan.path("maximumTasks"), 3) || !exactInteger(plan.path("maximumRuns"), 4)
                || !plan.path("cases").isArray() || plan.path("cases").size() != 3) throw reject();
        var cases = plan.path("cases");
        var ids = List.of("E02", "E04", "E13");
        var parsed = new java.util.ArrayList<Scenario>();
        for (int i = 0; i < ids.size(); i++) {
            var item = cases.get(i);
            int maxRuns = i == 1 ? 2 : 1;
            if (!item.isObject() || !item.path("id").isTextual() || !ids.get(i).equals(item.path("id").textValue())
                    || !item.path("question").isTextual() || item.path("question").textValue().isBlank()
                    || !exactInteger(item.path("maximumRuns"), maxRuns)
                    || (i == 1 ? !"PUBLIC".equals(item.path("reply").textValue()) : item.has("reply"))) throw reject();
            parsed.add(new Scenario(ids.get(i), item.path("question").textValue()));
        }
        if (parsed.stream().map(Scenario::question).distinct().count() != 3) throw reject();
        // 复制字符串而不保留可变 JsonNode，防止审包后外部修改计划改变执行范围。
        scenarios = List.copyOf(parsed);
    }

    synchronized void begin(RuleAgentRunner.Input input, String rawMessage, UUID runId) {
        if (closed || inFlight != null || input == null || runId == null || scenarioIndex >= scenarios.size()
                || runIds.size() >= 4 || runIds.contains(runId)) throw reject();
        var scenario = scenarios.get(scenarioIndex);
        if ((owner != null && !owner.equals(input.owner())) || !scenario.question().equals(input.question())
                || !input.questions().equals(List.of(new RuleAgentRunner.Question("q1", scenario.question())))) throw reject();
        if (waiting) {
            // 必须核对 HTTP 原文；只核对已规范化条件会把未授权的中文改问伪装成 PUBLIC。
            if (!"E04".equals(scenario.id()) || !scenarioTask.equals(input.taskId()) || input.version() != 4
                    || !"PUBLIC".equals(rawMessage) || !Map.of("courseType", "PUBLIC").equals(input.userConditions())) throw reject();
        } else {
            if (input.version() != 2 || !input.userConditions().isEmpty() || !scenario.question().equals(rawMessage)
                    || taskIds.size() >= 3 || taskIds.contains(input.taskId())) throw reject();
            taskIds.add(input.taskId());
            scenarioTask = input.taskId();
            if (owner == null) owner = input.owner();
        }
        runIds.add(runId);
        inFlight = input;
        inFlightRun = runId;
    }

    synchronized void complete(RuleAgentRunner.Input input, RuleAgentRunner.Outcome outcome) {
        if (closed || inFlight == null || !inFlight.equals(input) || outcome == null || outcome.snapshot() == null) throw reject();
        var snapshot = outcome.snapshot();
        if (!scenarioTask.equals(snapshot.taskId()) || !inFlightRun.equals(snapshot.runId())
                || snapshot.version() != input.version() || !snapshot.terminated()
                || snapshot.execution().mode() != Mode.ONLINE || !snapshot.execution().providerResponseReceived()
                || snapshot.execution().modelAttemptCount() < 1
                || snapshot.taskStatus() == TaskStatus.FAILED || snapshot.taskStatus() == TaskStatus.ACTIVE) throw reject();
        if (snapshot.taskStatus() == TaskStatus.WAITING_USER) {
            var clarifications = snapshot.result().items().stream().flatMap(item -> item.clarification().stream()).toList();
            if (!"E04".equals(scenarios.get(scenarioIndex).id()) || waiting || input.version() != 2
                    || clarifications.size() != 1 || !"courseType".equals(clarifications.get(0).conditionId())
                    || !Set.of("PUBLIC", "PROFESSIONAL").equals(new HashSet<>(clarifications.get(0).allowedValues()))
                    || clarifications.get(0).allowedValues().size() != 2) throw reject();
            waiting = true;
        } else {
            // 非追问终态只耗尽该题的机会；安全停下不等于产品完成，由宿主另记判定。
            waiting = false;
            scenarioIndex++;
            scenarioTask = null;
            if (scenarioIndex == scenarios.size()) closed = true;
        }
        inFlight = null;
        inFlightRun = null;
    }

    synchronized void fail() {
        closed = true;
        inFlight = null;
        inFlightRun = null;
    }

    synchronized int runs() { return runIds.size(); }
    synchronized int tasks() { return taskIds.size(); }
    synchronized boolean closed() { return closed; }

    private static boolean exactInteger(JsonNode node, int expected) {
        return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() == expected;
    }

    private IllegalStateException reject() {
        // 拒绝也永久封批，避免调用方把同一授权当作可重试的余额池；错误不带问题、身份或 token。
        fail();
        return new IllegalStateException(REJECTED);
    }
}
