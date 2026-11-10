package com.zhku.knowledge.eval.candidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator;
import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator.CaseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator.Verdict.*;
import static org.assertj.core.api.Assertions.*;

/** 已保存的 Level 1 资产与明确标注的负向变异；不启动 Agent、Provider、数据库或 Spring。 */
class CandidateCompatibilityTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final String RESOURCE = "knowledge/src/test/resources/eval/candidates/";
    private static final Path CASE = ROOT.resolve(RESOURCE + "java-offline-runner-pass.json");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final Path OUTPUT = Path.of("target", "agent-eval-phase1");
    private static final String GRADER_SHA = "fa8694acc8d2ab849df4ada750f608a2c7316005365f4ed1245cf35c00911650";

    @BeforeAll static void removeStaleReports() throws Exception {
        Files.createDirectories(OUTPUT);
        Files.deleteIfExists(OUTPUT.resolve("candidate-report.json"));
        Files.deleteIfExists(OUTPUT.resolve("three-state-diagnostics.json"));
    }

    @Test void scoresSavedRunnerTraceWithoutExecutingAgent() throws Exception {
        var loaded = CandidateTraceLoader.load(ROOT, CASE);
        assertThat(loaded.candidate().status()).isEqualTo("CANDIDATE");
        assertThat(loaded.candidate().reviewStatus()).isEqualTo("PENDING");
        assertThat(loaded.evaluate().verdict()).isEqualTo(PASS);
        assertThat(loaded.evaluate().details()).containsExactly("EXPECTATIONS_MET");
        assertThat(loaded.trace().executionActions()).extracting(a -> a.type())
                .containsExactly("SEARCH_REVIEWED_RULES", "READ_VERIFIED_RULE", "FINISH_CHECK");
        assertThat(loaded.trace().executionObservations()).extracting(o -> o.status())
                .containsExactly("SUCCEEDED", "SUCCEEDED", "NOT_OBSERVED");
        assertThat(loaded.trace().result().evidence()).allSatisfy(e -> {
            assertThat(e.sourceId()).isNull(); assertThat(e.chunkId()).isNull();
        });
        assertThat(sha(Files.readAllBytes(ROOT.resolve(
                "knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentTraceEvaluator.java"))))
                .as("Grader 必须与 d825bb0d 原文逐字节相同").isEqualTo(GRADER_SHA);
    }

    @Test void rejectsAmbiguousOrUnknownCandidateJson(@TempDir Path temp) throws Exception {
        String original = Files.readString(CASE);
        for (String malformed : List.of(
                original.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1"),
                original + " {}",
                original.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"unexpected\": true"))) {
            Path file = temp.resolve("candidate.json");
            Files.writeString(file, malformed);
            assertThatThrownBy(() -> CandidateTraceLoader.load(ROOT, file))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsMissingFieldsCoercionUnreviewedStatusAndUnknownEnums(@TempDir Path temp) throws Exception {
        var changes = List.<Consumer<ObjectNode>>of(
                c -> c.remove("status"), c -> c.put("status", "GOLD"), c -> c.put("reviewStatus", "APPROVED"),
                c -> c.put("schemaVersion", 1.0), c -> c.put("caseId", "wrong-case"),
                c -> object(c, "provenance").remove("sourceSha"),
                c -> object(c, "provenance").put("sourceSha", "MISSING"),
                c -> object(c, "provenance").put("datasetSha256", "bad"),
                c -> object(c, "expectation").putNull("expectClaims"),
                c -> object(c, "expectation").remove("requireEvidenceLinks"),
                c -> object(c, "expectation").put("expectClaims", "true"),
                c -> object(c, "expectation").put("taskStatus", "ARBITRARY"),
                c -> object(c, "expectation").put("surprise", true));
        for (var change : changes) assertThatThrownBy(() -> variant(temp, change, t -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsAlteredFilesWithoutMatchingDigest(@TempDir Path temp) throws Exception {
        Path root = assets(temp);
        var c = candidate(root);
        Path trace = root.resolve(c.path("provenance").path("traceFile").asText());
        Files.writeString(trace, Files.readString(trace) + " ");
        assertThatThrownBy(() -> CandidateTraceLoader.load(root, root.resolve(RESOURCE + CASE.getFileName())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CANDIDATE_SHA256_MISMATCH");
        var fixedRoot = assets(temp);
        Path dataset = fixedRoot.resolve(candidate(fixedRoot).path("provenance").path("datasetFile").asText());
        Files.writeString(dataset, Files.readString(dataset) + " ");
        assertThatThrownBy(() -> CandidateTraceLoader.load(fixedRoot, fixedRoot.resolve(RESOURCE + CASE.getFileName())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CANDIDATE_SHA256_MISMATCH");
    }

    @Test void rejectsEscapingReferencesAndMissingFiles(@TempDir Path temp) throws Exception {
        for (String path : List.of("../outside.json", CASE.toAbsolutePath().toString())) {
            assertThatThrownBy(() -> variant(temp, c -> object(c, "provenance").put("traceFile", path), t -> {}))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("CANDIDATE_REFERENCE_PATH");
        }
        Path root = assets(temp);
        Files.delete(root.resolve(candidate(root).path("provenance").path("traceFile").asText()));
        assertThatThrownBy(() -> CandidateTraceLoader.load(root, root.resolve(RESOURCE + CASE.getFileName())))
                .isInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    @Test void rejectsUnknownTraceFieldsModesTypesAndBrokenIdentity(@TempDir Path temp) throws Exception {
        var changes = List.<Consumer<ObjectNode>>of(
                t -> t.put("executionMode", "LIVE_AGENT"), t -> t.put("providerCalls", 1),
                t -> t.put("candidateId", "other-candidate"), t -> t.put("sourceSha", "0".repeat(64)),
                t -> t.put("datasetSha256", "0".repeat(64)), t -> t.put("sourceLayer", "SYNTHETIC"),
                t -> t.put("rawProviderResponse", "not-allowed"),
                t -> turn(t).remove("result"), t -> turn(t).put("truncated", "false"),
                t -> turn(t).put("version", 2.0), t -> turn(t).put("taskId", "task-2"),
                t -> object(array(turn(t), "actions").get(0)).put("type", "EXECUTE_SQL"),
                t -> object(array(turn(t), "observations").get(0)).put("status", "MAGIC_SUCCESS"),
                t -> object(array(turn(t), "observations").get(2)).put("dataLevel", "VERIFIED_FACT"),
                t -> object(array(turn(t), "observations").get(2)).put("status", "SUCCEEDED"));
        for (var change : changes) assertThatThrownBy(() -> variant(temp, c -> {}, change))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsDuplicateIdsAndOrphanObservations(@TempDir Path temp) throws Exception {
        var changes = List.<Consumer<ObjectNode>>of(
                t -> array(turn(t), "actions").add(array(turn(t), "actions").get(0).deepCopy()),
                t -> array(turn(t), "observations").add(array(turn(t), "observations").get(0).deepCopy()),
                t -> object(array(turn(t), "observations").get(0)).put("actionId", "action-9"),
                t -> array(object(turn(t), "result"), "evidence").add(array(object(turn(t), "result"), "evidence").get(0).deepCopy()));
        for (var change : changes) assertThatThrownBy(() -> variant(temp, c -> {}, change))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsDuplicateDatasetCaseIds(@TempDir Path temp) throws Exception {
        Path root = assets(temp);
        var c = candidate(root);
        Path file = root.resolve(c.path("provenance").path("datasetFile").asText());
        var dataset = (ObjectNode) JSON.readTree(Files.readAllBytes(file));
        array(dataset, "cases").add(array(dataset, "cases").get(0).deepCopy());
        Files.write(file, JSON.writeValueAsBytes(dataset));
        object(c, "provenance").put("datasetSha256", sha(Files.readAllBytes(file)));
        Files.write(root.resolve(RESOURCE + CASE.getFileName()), JSON.writeValueAsBytes(c));
        assertThatThrownBy(() -> CandidateTraceLoader.load(root, root.resolve(RESOURCE + CASE.getFileName())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CANDIDATE_DUPLICATE_CASE_ID");
    }

    @Test void graderLocatesEvidenceFailuresAndBudgetViolations(@TempDir Path temp) throws Exception {
        var missingLinks = variant(temp, c -> {}, t -> array(object(turn(t), "result"), "evidenceRelations").removeAll()).evaluate();
        assertThat(missingLinks.verdict()).isEqualTo(FAIL);
        assertThat(missingLinks.details()).contains("EVIDENCE_LINKS_MISSING", "UNSUPPORTED_CLAIM");
        var orphan = variant(temp, c -> {}, t -> object(array(object(turn(t), "result"), "evidenceRelations").get(0))
                .put("evidenceId", "evidence-9")).evaluate();
        assertThat(orphan.verdict()).isEqualTo(FAIL);
        assertThat(orphan.details()).contains("ORPHAN_EVIDENCE_LINK");
        var budget = variant(temp, c -> {}, t -> object(array(turn(t), "actions").get(2)).put("modelAttempt", 6)).evaluate();
        assertThat(budget.verdict()).isEqualTo(FAIL);
        assertThat(budget.details()).contains("MODEL_ATTEMPT_OVER_BUDGET");
    }

    @Test void producesDeterministicThreeStateReportsWithExplicitNegativeControls(@TempDir Path temp) throws Exception {
        var loaded = CandidateTraceLoader.load(ROOT, CASE);
        var pass = loaded.evaluate();
        var mismatch = variant(temp, c -> object(c, "expectation").put("taskStatus", "STOPPED"), t -> {}).evaluate();
        var truncated = variant(temp, c -> {}, t -> turn(t).put("truncated", true)).evaluate();
        var missingObservation = variant(temp, c -> {}, t -> array(turn(t), "observations").remove(1)).evaluate();
        var terminalOnly = variant(temp, c -> {}, t -> {
            array(turn(t), "actions").removeAll(); array(turn(t), "observations").removeAll();
        }).evaluate();
        var missingTrace = variant(temp, c -> {}, t -> array(t, "traces").removeAll()).evaluate();
        assertThat(mismatch.verdict()).isEqualTo(FAIL);
        assertThat(mismatch.details()).contains("TASK_STATUS_MISMATCH");
        assertThat(truncated.verdict()).isEqualTo(NOT_EVALUABLE);
        assertThat(truncated.details()).containsExactly("TRACE_TRUNCATED");
        assertThat(missingObservation.verdict()).isEqualTo(NOT_EVALUABLE);
        assertThat(missingObservation.details()).contains("TOOL_OBSERVATION_MISSING", "REQUIRED_OBSERVATION_NOT_SEEN");
        assertThat(terminalOnly.verdict()).isEqualTo(NOT_EVALUABLE);
        assertThat(terminalOnly.details()).containsExactly("FULL_CHAIN_NOT_ARCHIVED");
        assertThat(missingTrace.verdict()).isEqualTo(NOT_EVALUABLE);
        assertThat(missingTrace.details()).containsExactly("TRACE_MISSING");
        var results = List.of(pass, rename(mismatch, "-expected-mismatch"), rename(truncated, "-truncated"),
                rename(missingObservation, "-missing-observation"), rename(terminalOnly, "-terminal-only"), rename(missingTrace, "-missing-trace"));
        var report = RuleAgentTraceEvaluator.report(results);
        assertThat(report.passed()).isEqualTo(1); assertThat(report.failed()).isEqualTo(1);
        assertThat(report.notEvaluable()).isEqualTo(4); assertThat(report.passedGate()).isFalse();
        var baseline = RuleAgentTraceEvaluator.report(List.of(pass));
        assertThat(baseline.passedGate()).isTrue();
        var baselineBytes = render(loaded, baseline, "SAVED_RUNNER_TRACE");
        var independent = CandidateTraceLoader.load(ROOT, CASE);
        assertThat(render(independent, RuleAgentTraceEvaluator.report(List.of(independent.evaluate())), "SAVED_RUNNER_TRACE"))
                .isEqualTo(baselineBytes);
        byte[] diagnosticBytes = render(loaded, report, "ORIGINAL_PLUS_IN_MEMORY_NEGATIVE_CONTROLS_NOT_ADDITIONAL_RUNS");
        assertThat(render(loaded, report, "ORIGINAL_PLUS_IN_MEMORY_NEGATIVE_CONTROLS_NOT_ADDITIONAL_RUNS")).isEqualTo(diagnosticBytes);
        String serialized = new String(diagnosticBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(serialized).doesNotContain("选修课程", "合成分类规则", "sourceId", "chunkId", "factId", "originalToken", "continuationToken");
        assertThat(serialized).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        Files.write(OUTPUT.resolve("candidate-report.json"), baselineBytes);
        Files.write(OUTPUT.resolve("three-state-diagnostics.json"), diagnosticBytes);
    }

    @Test void reportRejectsDuplicateCaseIdsAndEmptyGate() throws Exception {
        var result = CandidateTraceLoader.load(ROOT, CASE).evaluate();
        assertThatThrownBy(() -> RuleAgentTraceEvaluator.report(List.of(result, result)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate eval case id");
        assertThat(RuleAgentTraceEvaluator.report(List.of()).passedGate()).isFalse();
    }

    private static byte[] render(CandidateTraceLoader.LoadedCandidate loaded,
            RuleAgentTraceEvaluator.Report report, String scenarioKind) throws Exception {
        var c = loaded.candidate();
        var doc = new LinkedHashMap<String, Object>();
        doc.put("schemaVersion", report.schemaVersion()); doc.put("source", report.source());
        doc.put("candidateId", c.candidateId()); doc.put("candidateStatus", c.status()); doc.put("reviewStatus", c.reviewStatus());
        doc.put("sourceSha", c.provenance().sourceSha()); doc.put("datasetSha256", c.provenance().datasetSha256());
        doc.put("traceSha256", c.provenance().traceSha256()); doc.put("graderSha256", GRADER_SHA);
        doc.put("executionMode", "OFFLINE_SIMULATED"); doc.put("providerCalls", 0); doc.put("executionLevel", 1);
        doc.put("scope", "TRACE_CONTRACT_ONLY_NOT_AGENT_QUALITY_OR_GOLD_GATE");
        doc.put("answerSemanticEvaluation", "NOT_EVALUABLE_TEXT_NOT_ARCHIVED");
        doc.put("scenarioKind", scenarioKind); doc.put("cases", report.cases());
        doc.put("passed", report.passed()); doc.put("failed", report.failed()); doc.put("notEvaluable", report.notEvaluable());
        doc.put("passedGate", report.passedGate());
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(doc);
    }
    private static CaseResult rename(CaseResult result, String suffix) {
        return new CaseResult(result.caseId() + suffix, result.verdict(), result.details(),
                result.actualTaskStatus(), result.actualCoverage(), result.actionTypes());
    }
    private static Path assets(Path temp) throws Exception {
        Path root = Files.createTempDirectory(temp, "variant-");
        var c = (ObjectNode) JSON.readTree(Files.readAllBytes(CASE));
        for (String relative : List.of(RESOURCE + CASE.getFileName(), c.path("provenance").path("datasetFile").asText(),
                c.path("provenance").path("traceFile").asText())) {
            Path target = root.resolve(relative); Files.createDirectories(target.getParent());
            Files.copy(ROOT.resolve(relative), target);
        }
        return root;
    }
    private static ObjectNode candidate(Path root) throws Exception {
        return (ObjectNode) JSON.readTree(Files.readAllBytes(root.resolve(RESOURCE + CASE.getFileName())));
    }
    private static CandidateTraceLoader.LoadedCandidate variant(Path temp, Consumer<ObjectNode> changeCandidate,
            Consumer<ObjectNode> changeTrace) throws Exception {
        Path root = assets(temp);
        var c = candidate(root);
        Path tracePath = root.resolve(c.path("provenance").path("traceFile").asText());
        var trace = (ObjectNode) JSON.readTree(Files.readAllBytes(tracePath));
        changeTrace.accept(trace);
        Files.write(tracePath, JSON.writeValueAsBytes(trace));
        // 仅负向测试重签结构变异的 SHA；它们不是新执行或新的候选 Case。
        object(c, "provenance").put("traceSha256", sha(Files.readAllBytes(tracePath)));
        changeCandidate.accept(c);
        Files.write(root.resolve(RESOURCE + CASE.getFileName()), JSON.writeValueAsBytes(c));
        return CandidateTraceLoader.load(root, root.resolve(RESOURCE + CASE.getFileName()));
    }
    private static ObjectNode object(com.fasterxml.jackson.databind.JsonNode node) { return (ObjectNode) node; }
    private static ObjectNode object(ObjectNode node, String key) { return object(node.get(key)); }
    private static ArrayNode array(ObjectNode node, String key) { return (ArrayNode) node.get(key); }
    private static ObjectNode turn(ObjectNode trace) { return object(array(trace, "traces").get(0)); }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
