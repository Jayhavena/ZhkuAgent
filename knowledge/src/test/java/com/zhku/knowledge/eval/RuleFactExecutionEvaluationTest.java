package com.zhku.knowledge.eval;

import com.zhku.knowledge.ruleagent.RuleAgentRunTrace;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.eval.RuleFactExecutionEvaluation.*;
import static com.zhku.knowledge.eval.RuleFactOfflineScorer.*;

class RuleFactExecutionEvaluationTest {
    com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator.Expectation expected() {
        return new com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator.Expectation("RFE-001", "RESOLVED", "SUCCEEDED",
                "COMPLETE", List.of(), null, false, null, null, false, false);
    }
    Result checked(Execution e) { return evaluate(dataset, hash, e, expected()); }
    final UUID task = UUID.randomUUID(), run = UUID.randomUUID();
    final String hash = "a".repeat(64);
    final Dataset dataset = new Dataset(Map.of("RFE-001", new Case("RFE-001", Set.of("fact"), Set.of(), Set.of("loc"))));
    Execution execution(String answer, RuleAgentRunTrace trace, boolean complete) {
        var review = new Review(Verification.SUPPORTED, "independent reviewer", "checked against frozen expectations", "answer");
        return new Execution(new Identity("RFE-001", task, run, "v1.0", hash, "model-test", hash), answer, trace,
                Set.of("loc"), complete, Set.of("fact"), complete,
                List.of(new Citation("loc", Verification.SUPPORTED)), complete,
                Map.of("unsupportedClaims", review, "scopeAndApplicability", review,
                        "clarificationAndGap", review, "executionSafety", review));
    }
    RuleAgentRunTrace trace(String status, List<RuleAgentRunTrace.Action> actions) {
        return new RuleAgentRunTrace(task, UUID.randomUUID(), run, 1, status,
                status.equals("FAILED") ? "FAILED" : "SUCCEEDED", false, false, actions,
                new RuleAgentRunTrace.ResultView("COMPLETE", List.of(), List.of(), List.of(), List.of()));
    }
    @Test void sixDimensionsExplainCompleteReviewedExecution() {
        var r = checked(execution("answer", trace("RESOLVED", List.of(
                new RuleAgentRunTrace.Action("finish", "FINISH_CHECK", 1, 1, null, null, null))), true));
        assertThat(r.outcome()).isEqualTo("PASS");
        assertThat(r.dimensions()).hasSize(6);
        assertThat(r.dimensions().values()).allSatisfy(d -> assertThat(d.reasons()).isNotEmpty());
    }
    @Test void missingAnswerTerminalOrInventoriesNeverPass() {
        assertThat(checked(execution(null, trace("RESOLVED", List.of()), true)).outcome()).isEqualTo("UNJUDGEABLE");
        assertThat(checked(execution("answer", null, true)).outcome()).isEqualTo("UNJUDGEABLE");
        assertThat(checked(execution("answer", trace("RESOLVED", List.of()), false)).outcome()).isEqualTo("UNJUDGEABLE");
    }
    @Test void missingToolObservationUsesExistingGrader() {
        var t = trace("RESOLVED", List.of(new RuleAgentRunTrace.Action("search", "SEARCH_REVIEWED_RULES", 1, 1, null, null, null)));
        var r = checked(execution("answer", t, true));
        assertThat(r.outcome()).isEqualTo("UNJUDGEABLE");
        assertThat(r.dimensions().get("executionSafety").reasons()).contains("TOOL_OBSERVATION_MISSING");
    }
    @Test void failedExecutionAndWrongIdentityCannotPass() {
        assertThat(checked(execution(null, trace("FAILED", List.of()), true)).outcome()).isEqualTo("FAIL");
        assertThat(checked(execution("answer", trace("FAILED", List.of()), true)).outcome()).isEqualTo("FAIL");
        assertThatThrownBy(() -> evaluate(dataset, "b".repeat(64), execution("answer", null, true), expected()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingContractAndTerminalDriftCannotPass() {
        var t = trace("RESOLVED", List.of(new RuleAgentRunTrace.Action("finish", "FINISH_CHECK", 1, 1, null, null, null)));
        var e = execution("answer", t, true);
        assertThat(evaluate(dataset, hash, e, null).outcome()).isEqualTo("UNJUDGEABLE");
        var wrong = new com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator.Expectation("RFE-001", "WAITING_USER", "INCOMPLETE",
                "NONE", List.of("ASK_NECESSARY_CONDITION"), null, false, null, null, false, false);
        var result = evaluate(dataset, hash, e, wrong);
        assertThat(result.outcome()).isEqualTo("FAIL");
        assertThat(result.dimensions().get("executionSafety").reasons()).contains("TASK_STATUS_MISMATCH", "COVERAGE_MISMATCH", "REQUIRED_ACTIONS_MISSING");
    }
    @Test void captureOutputRemovesCapabilitiesAndNeverOverwrites(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        var path = tmp.resolve("capture.json");
        var bundle = new RuleFactExecutionCapture.Bundle(null,
                Map.of("evidence", List.of(Map.of("originalToken", "private", "quote", "source")),
                        "continuationToken", "private"), List.of());
        RuleFactExecutionCapture.write(path, bundle);
        assertThat(java.nio.file.Files.readString(path)).contains("source").doesNotContain("private", "originalToken", "continuationToken");
        assertThatThrownBy(() -> RuleFactExecutionCapture.write(path, bundle)).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
    }
    @Test void batchRetainsTwentyCasesAndIsolatesInvalidIdentity(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        var root = java.nio.file.Path.of("").toAbsolutePath();
        while (root != null && !java.nio.file.Files.isDirectory(root.resolve("docs/evidence/agent-eval"))) root = root.getParent();
        assertThat(root).isNotNull();
        var frozen = root.resolve("docs/evidence/agent-eval/2026-11-17-frozen-gold-v1");
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var input = tmp.resolve("executions.json");
        var contracts = tmp.resolve("contracts.json");
        json.writeValue(input.toFile(), List.of(execution("answer", null, true)));
        json.writeValue(contracts.toFile(), List.of(expected()));
        var bytes = new java.io.ByteArrayOutputStream();
        var previous = System.out;
        try {
            System.setOut(new java.io.PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8));
            RuleFactExecutionEvaluation.main(new String[]{frozen.resolve("rule-fact-check-effect-v1.0.json").toString(),
                    frozen.resolve("manifest.json").toString(), input.toString(), contracts.toString()});
        } finally { System.setOut(previous); }
        var report = json.readTree(bytes.toByteArray());
        assertThat(report.size()).isEqualTo(20);
        assertThat(report.path("RFE-001").path("outcome").asText()).isEqualTo("ERROR");
        assertThat(report.path("RFE-020").path("outcome").asText()).isEqualTo("UNJUDGEABLE");
        assertThat(report.path("RFE-020").path("dimensions").size()).isEqualTo(6);
    }
}
