package com.zhku.knowledge.eval.chain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.ruleagent.RuleAgentRunTrace;
import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评测链路回归：真实归档证据 → 采集 → 现有 Grader → 报告。
 *
 * <p>这些用例不访问网络、数据库或浏览器；它们验证的是链路契约本身：
 * 期望命中、失败样本仍失败关闭、不可判定样本绝不被判 PASS。
 */
class AgentEvalChainRegressionTest {
    private static final String SUITE = "knowledge/src/test/resources/agent-eval-chain/suite-v1.json";

    @Test
    void realEvidenceChainMatchesApprovedExpectations(@TempDir Path work) throws Exception {
        Path root = repoRoot();
        EvalChainReport report = EvalChainRunner.run(root, root.resolve(SUITE), work);

        assertTrue(report.regressionPassed(), () -> "回归未通过: " + describe(report));
        assertEquals(6, report.cases().size());
        assertEquals(6, report.matched());
        assertEquals(4, report.verdictCount("PASS"));
        assertEquals(2, report.verdictCount("NOT_EVALUABLE"));

        assertEquals("COMPLETE", block(report, "live-t05-69").collectionStatus());
        assertEquals("PARTIAL_TERMINAL_FALLBACK",
                block(report, "failed-probe-t05-46").collectionStatus());
        assertEquals("INCOMPLETE", block(report, "incomplete-probe-t05").collectionStatus());

        report.write(work.resolve("out"));
        assertTrue(Files.isRegularFile(work.resolve("out/report.json")));
        assertTrue(Files.isRegularFile(work.resolve("out/report.md")));
        JsonNode json = new ObjectMapper().readTree(
                Files.readString(work.resolve("out/report.json"), StandardCharsets.UTF_8));
        assertEquals("agent-eval-chain-v1", json.path("suiteId").asText());
        assertEquals(6, json.path("matched").asInt());
        assertTrue(json.path("regressionPassed").asBoolean());
    }

    @Test
    void missingObservationOnRealTraceIsNotEvaluable(@TempDir Path work) throws Exception {
        Path root = repoRoot();
        var collection = EvalEvidenceCollector.collect(
                root.resolve("docs/evidence/rule-agent-provider/2026-10-16/t05-69-review-condition-request/"
                        + "live-04994adbb93a4932bed3aa7b1b8b9047"),
                "strip-observation", work.resolve("collected"), root);
        var run = collection.runs().stream()
                .filter(r -> "RESOLVED".equals(r.taskStatus())).findFirst().orElseThrow();
        RuleAgentRunTrace original = EvalTraceFixture.load(run.fixture(), root).toTrace();

        // 把真实成功的检索/阅读观察抹掉，模拟 R1 暴露过的“只归档决策序列”缺口。
        var stripped = new RuleAgentRunTrace(original.taskId(), original.turnId(), original.runId(),
                original.version(), original.taskStatus(), original.runState(),
                original.exceptionalFailure(), original.truncated(),
                original.actions().stream().map(a -> new RuleAgentRunTrace.Action(a.actionId(),
                        a.type(), a.step(), a.modelAttempt(), null, a.reasonCode(),
                        a.publicationReason())).toList(), original.result());

        var expectation = new RuleAgentTraceEvaluator.Expectation("STRIPPED-OBSERVATION",
                "RESOLVED", "SUCCEEDED", "COMPLETE",
                List.of("SEARCH_REVIEWED_RULES", "READ_VERIFIED_RULE", "FINISH_CHECK", "PUBLICATION_REVIEW"),
                null, true, null, null, true, false);
        var result = RuleAgentTraceEvaluator.evaluate(expectation, stripped);
        assertEquals(RuleAgentTraceEvaluator.Verdict.NOT_EVALUABLE, result.verdict());
        assertTrue(result.details().contains("TOOL_OBSERVATION_MISSING"),
                () -> "缺少工具观察必须阻断 PASS，实际 " + result.details());
    }

    @Test
    void tamperedTerminalIsCaughtByExpectation() throws Exception {
        Path root = repoRoot();
        EvalTraceFixture fixture = EvalTraceFixture.load(
                root.resolve("knowledge/src/test/resources/agent-eval-chain/fixtures/e13-20261029T0842.json"),
                root);
        RuleAgentRunTrace original = fixture.toTrace();
        var tampered = new RuleAgentRunTrace(original.taskId(), original.turnId(), original.runId(),
                original.version(), "RESOLVED", "SUCCEEDED", original.exceptionalFailure(),
                original.truncated(), original.actions(), original.result());

        var expectation = new RuleAgentTraceEvaluator.Expectation("TAMPER-0842",
                "FAILED", "FAILED", "NONE", List.of(), "INVALID_MODEL_RESPONSE",
                false, null, null, false, false);
        var result = RuleAgentTraceEvaluator.evaluate(expectation, tampered);
        assertEquals(RuleAgentTraceEvaluator.Verdict.FAIL, result.verdict());
        assertTrue(result.details().contains("TASK_STATUS_MISMATCH"));
        assertTrue(result.details().contains("RUN_STATE_MISMATCH"));
    }

    @Test
    void truncatedTraceFailsClosed() {
        RuleAgentRunTrace truncated = new RuleAgentRunTrace(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, "RESOLVED", "SUCCEEDED", false, true, List.of(),
                new RuleAgentRunTrace.ResultView("NONE", List.of(), List.of(), List.of(), List.of()));
        var expectation = new RuleAgentTraceEvaluator.Expectation("TRUNCATED", "RESOLVED",
                "SUCCEEDED", "NONE", List.of(), null, false, null, null, false, false);
        var result = RuleAgentTraceEvaluator.evaluate(expectation, truncated);
        assertEquals(RuleAgentTraceEvaluator.Verdict.NOT_EVALUABLE, result.verdict());
        assertEquals(List.of("TRACE_TRUNCATED"), result.details());
    }

    @Test
    void incompleteEvidenceNeverProducesPass(@TempDir Path work) throws Exception {
        Path root = repoRoot();
        var collection = EvalEvidenceCollector.collect(
                root.resolve("docs/evidence/rule-agent-provider/2026-10-14/"
                        + "t05-live-1c7d94d4c9e94870bb299b35b38b5c53"),
                "incomplete", work.resolve("collected"), root);
        assertEquals(EvalEvidenceCollector.Status.INCOMPLETE, collection.status());
        assertTrue(collection.runs().isEmpty());
        assertTrue(collection.reasons().contains("TERMINAL_SNAPSHOT_MISSING"));
    }

    @Test
    void collectedFixtureDropsToolExcerptsAndUserFacingText(@TempDir Path work) throws Exception {
        Path root = repoRoot();
        var collection = EvalEvidenceCollector.collect(
                root.resolve("docs/evidence/rule-agent-provider/2026-10-16/t05-69-review-condition-request/"
                        + "live-04994adbb93a4932bed3aa7b1b8b9047"),
                "redaction", work.resolve("collected"), root);
        assertEquals(2, collection.runs().size());
        for (var run : collection.runs()) {
            String text = Files.readString(run.fixture(), StandardCharsets.UTF_8);
            assertFalse(text.contains("\"excerpt\""), "夹具不得保留检索候选原文");
            assertFalse(text.contains("\"candidates\""), "夹具不得保留候选列表");
            assertFalse(text.contains("MISSING_CONDITION\",\"message\""), "夹具不得保留用户可见文案");
            assertFalse(text.contains("feedback"), "夹具不得保留请求/响应原文");
            assertTrue(Files.isRegularFile(work.resolve("collected/collection.json")));
        }
    }

    private static EvalChainReport.BlockReport block(EvalChainReport report, String blockId) {
        EvalChainReport.BlockReport found = report.blocks().stream()
                .filter(b -> b.blockId().equals(blockId)).findFirst().orElse(null);
        assertNotNull(found, "缺少证据块 " + blockId);
        return found;
    }

    private static String describe(EvalChainReport report) {
        var out = new StringBuilder();
        report.cases().forEach(c -> out.append(c.caseId()).append('=').append(c.verdict())
                .append("(expected ").append(c.expectedVerdict()).append(", matched=")
                .append(c.matched()).append(") "));
        return out.toString();
    }

    /** 从测试类输出目录回溯仓库根；Surefire 的工作目录可能被 ZHku 数据根改写，不能依赖 user.dir。 */
    static Path repoRoot() throws IOException {
        Path candidate;
        try {
            candidate = Path.of(AgentEvalChainRegressionTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        } catch (java.net.URISyntaxException ex) {
            throw new IOException("测试代码位置不是合法 URI", ex);
        }
        for (int depth = 0; depth < 6 && candidate != null; depth++) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("knowledge"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("无法定位仓库根目录");
    }
}
