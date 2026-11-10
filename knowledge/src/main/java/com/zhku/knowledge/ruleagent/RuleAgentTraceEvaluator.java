package com.zhku.knowledge.ruleagent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 最小离线 Agent Eval：只消费 Runner 的白名单 Trace 投影，不读取题面、原文或模型输出。
 * 这里验证可观察执行契约，不宣称证明自然语言答案或规则语义正确。
 */
public final class RuleAgentTraceEvaluator {
    private RuleAgentTraceEvaluator() {}

    public enum Verdict { PASS, FAIL, NOT_EVALUABLE }

    public record Expectation(String caseId, String taskStatus, String runState, String coverage,
            List<String> requiredActions, String issueReason, boolean expectClaims,
            String observedAction, String observationStatus, boolean requireEvidenceLinks,
            boolean expectRejectedUnknownTool) {
        public Expectation {
            if (caseId == null || !caseId.matches("[A-Za-z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("Invalid case id");
            }
            Objects.requireNonNull(taskStatus);
            Objects.requireNonNull(runState);
            Objects.requireNonNull(coverage);
            requiredActions = List.copyOf(requiredActions);
            if ((observedAction == null) != (observationStatus == null)) {
                throw new IllegalArgumentException("Observation action/status must be paired");
            }
            if (requireEvidenceLinks && !expectClaims) {
                throw new IllegalArgumentException("Evidence links require claims");
            }
        }
    }

    /** 输出仅有枚举、案例代号及检查结果，不包含用户数据或可还原的标识符。 */
    public record CaseResult(String caseId, Verdict verdict, List<String> details,
            String actualTaskStatus, String actualCoverage, List<String> actionTypes) {
        public CaseResult {
            details = List.copyOf(details);
            actionTypes = List.copyOf(actionTypes);
        }
    }

    public record Report(int schemaVersion, String source, List<CaseResult> cases,
            long passed, long failed, long notEvaluable) {
        public Report {
            cases = List.copyOf(cases);
        }
        public boolean passedGate() {
            return !cases.isEmpty() && failed == 0 && notEvaluable == 0
                    && passed == cases.size();
        }
    }

    public static Report report(List<CaseResult> cases) {
        var checked = List.copyOf(cases);
        if (checked.stream().map(CaseResult::caseId).distinct().count() != checked.size()) {
            throw new IllegalArgumentException("Duplicate eval case id");
        }
        return new Report(1, "JAVA_RUNNER_TRACE_PROJECTION", checked,
                checked.stream().filter(c -> c.verdict() == Verdict.PASS).count(),
                checked.stream().filter(c -> c.verdict() == Verdict.FAIL).count(),
                checked.stream().filter(c -> c.verdict() == Verdict.NOT_EVALUABLE).count());
    }

    public static CaseResult evaluate(Expectation expected, RuleAgentRunTrace trace) {
        Objects.requireNonNull(expected);
        if (trace == null) {
            return new CaseResult(expected.caseId(), Verdict.NOT_EVALUABLE,
                    List.of("TRACE_MISSING"), null, null, List.of());
        }
        var types = trace.executionActions().stream().map(RuleAgentRunTrace.ActionView::type).toList();
        // Trace 最多保存24条，截断后不能声称已看见完整执行或终止路径。
        if (trace.truncated()) {
            return new CaseResult(expected.caseId(), Verdict.NOT_EVALUABLE,
                    List.of("TRACE_TRUNCATED"), trace.taskStatus(), trace.result().coverage(), types);
        }
        var failures = new ArrayList<String>();
        var unknowns = new ArrayList<String>();
        if (trace.exceptionalFailure()) failures.add("EXCEPTIONAL_RUN");
        if (!expected.taskStatus().equals(trace.taskStatus())) failures.add("TASK_STATUS_MISMATCH");
        if (!expected.runState().equals(trace.runState())) failures.add("RUN_STATE_MISMATCH");
        if (!expected.coverage().equals(trace.result().coverage())) failures.add("COVERAGE_MISMATCH");
        if (!subsequence(types, expected.requiredActions())) failures.add("REQUIRED_ACTIONS_MISSING");
        if (expected.issueReason() != null && trace.result().issues().stream()
                .noneMatch(i -> expected.issueReason().equals(i.reasonCode()))) {
            failures.add("ISSUE_REASON_MISMATCH");
        }
        var claims = trace.result().items().stream().flatMap(i -> i.claims().stream()).toList();
        if (expected.expectClaims() != !claims.isEmpty()) failures.add("CLAIM_PRESENCE_MISMATCH");

        var seen = new HashSet<String>();
        int index = 0;
        for (var action : trace.actions()) {
            if (action.step() != ++index || !seen.add(action.actionId())) {
                failures.add("ACTION_SEQUENCE_INVALID");
                break;
            }
            if (action.modelAttempt() > RuleAgentContract.DEFAULT_BUDGET.maxModelAttemptsPerRun()) {
                failures.add("MODEL_ATTEMPT_OVER_BUDGET");
            }
        }
        for (var action : trace.actions()) {
            // 检索/阅读没有真实观察、且没有明确拒绝原因时，不得推断工具成功。
            if (Set.of("SEARCH_REVIEWED_RULES", "READ_VERIFIED_RULE").contains(action.type())
                    && action.observation() == null && action.reasonCode() == null) {
                unknowns.add("TOOL_OBSERVATION_MISSING");
                break;
            }
        }
        if (expected.observedAction() != null) {
            boolean observed = trace.actions().stream().anyMatch(a ->
                    expected.observedAction().equals(a.type()) && a.observation() != null
                    && expected.observationStatus().equals(a.observation().status()));
            if (!observed) unknowns.add("REQUIRED_OBSERVATION_NOT_SEEN");
        }
        if (expected.requireEvidenceLinks()) {
            var evidences = trace.result().evidence();
            var relations = trace.result().evidenceRelations();
            var claimIds = new HashSet<String>();
            var evidenceIds = new HashSet<String>();
            claims.forEach(c -> claimIds.add(c.id()));
            evidences.forEach(e -> evidenceIds.add(e.id()));
            if (evidences.isEmpty() || relations.isEmpty()) failures.add("EVIDENCE_LINKS_MISSING");
            if (claimIds.size() != claims.size() || evidenceIds.size() != evidences.size()) {
                failures.add("DUPLICATE_RESULT_ID");
            }
            if (relations.stream().anyMatch(r -> !claimIds.contains(r.targetId())
                    || !evidenceIds.contains(r.evidenceId()))) {
                failures.add("ORPHAN_EVIDENCE_LINK");
            }
            if (claims.stream().anyMatch(c -> relations.stream().noneMatch(r ->
                    c.id().equals(r.targetId()) && "CLAIM".equals(r.role())))) {
                failures.add("UNSUPPORTED_CLAIM");
            }
        }
        if (expected.expectRejectedUnknownTool()) {
            boolean rejected = trace.actions().stream().anyMatch(a -> "INVALID".equals(a.type())
                    && "UNKNOWN_TOOL".equals(a.reasonCode()) && a.observation() == null);
            if (!rejected || !claims.isEmpty() || !trace.result().evidence().isEmpty()) {
                failures.add("UNKNOWN_TOOL_NOT_SAFELY_REJECTED");
            }
        }
        Verdict verdict = !failures.isEmpty() ? Verdict.FAIL
                : !unknowns.isEmpty() ? Verdict.NOT_EVALUABLE : Verdict.PASS;
        var details = new ArrayList<String>();
        details.addAll(failures);
        details.addAll(unknowns);
        if (details.isEmpty()) details.add("EXPECTATIONS_MET");
        return new CaseResult(expected.caseId(), verdict, details,
                trace.taskStatus(), trace.result().coverage(), types);
    }

    private static boolean subsequence(List<String> actual, List<String> required) {
        int cursor = 0;
        for (var action : actual) {
            if (cursor < required.size() && required.get(cursor).equals(action)) cursor++;
        }
        return cursor == required.size();
    }
}
