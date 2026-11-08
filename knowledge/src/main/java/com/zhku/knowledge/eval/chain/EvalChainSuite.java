package com.zhku.knowledge.eval.chain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 评测链路的回归套件定义：把“证据目录 / 夹具文件”与“期望判定”绑在一起。
 *
 * <p>期望值是人工审过的契约，不是 Grader 的输出；CI 只验证 Grader 是否仍然给出同一判定，
 * 从而在实现漂移时立刻失败，而不是把当次输出当成基线。
 */
public record EvalChainSuite(int schemaVersion, String suiteId, List<Block> blocks) {
    public static final int SCHEMA_VERSION = 1;
    public static final String EVIDENCE_BUNDLE = "EVIDENCE_BUNDLE";
    public static final String FIXTURE_CORPUS = "FIXTURE_CORPUS";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public EvalChainSuite {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的套件 schemaVersion: " + schemaVersion);
        }
        if (suiteId == null || suiteId.isBlank()) throw new IllegalArgumentException("suiteId 不能为空");
        blocks = List.copyOf(blocks);
        if (blocks.isEmpty()) throw new IllegalArgumentException("套件至少需要一个证据块");
    }

    public record Block(String blockId, String kind, String evidenceDir, List<CaseDefinition> cases,
            BundleCase bundleCase) {
        public Block {
            if (!EVIDENCE_BUNDLE.equals(kind) && !FIXTURE_CORPUS.equals(kind)) {
                throw new IllegalArgumentException("未知证据块类型: " + kind);
            }
            cases = List.copyOf(cases);
        }
    }

    public record CaseDefinition(String caseId, String title, String runId, String fixture,
            ExpectationSpec expectation, String expectedVerdict, List<String> expectedDetails) {
        public CaseDefinition {
            if (caseId == null || !caseId.matches("[A-Za-z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("套件 caseId 无效: " + caseId);
            }
            expectedDetails = List.copyOf(expectedDetails);
            if (expectedVerdict == null) throw new IllegalArgumentException("缺少 expectedVerdict: " + caseId);
        }

        public RuleAgentTraceEvaluator.Expectation toExpectation() {
            return expectation.toExpectation(caseId);
        }
    }

    /** 证据块整体不可判定时的断言（例如终态快照缺失，整批只能记录 NOT_EVALUABLE）。 */
    public record BundleCase(String caseId, String title, String expectedVerdict,
            List<String> expectedDetails) {
        public BundleCase {
            if (expectedVerdict == null) throw new IllegalArgumentException("缺少 expectedVerdict: " + caseId);
            expectedDetails = List.copyOf(expectedDetails);
        }
    }

    public record ExpectationSpec(String taskStatus, String runState, String coverage,
            List<String> requiredActions, String issueReason, boolean expectClaims,
            String observedAction, String observationStatus, boolean requireEvidenceLinks,
            boolean expectRejectedUnknownTool) {
        public ExpectationSpec {
            requiredActions = List.copyOf(requiredActions);
        }

        RuleAgentTraceEvaluator.Expectation toExpectation(String caseId) {
            return new RuleAgentTraceEvaluator.Expectation(caseId, taskStatus, runState, coverage,
                    requiredActions, issueReason, expectClaims, observedAction, observationStatus,
                    requireEvidenceLinks, expectRejectedUnknownTool);
        }
    }

    public static EvalChainSuite load(Path file) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        var blocks = new ArrayList<Block>();
        for (JsonNode blockNode : root.path("blocks")) {
            var cases = new ArrayList<CaseDefinition>();
            for (JsonNode caseNode : blockNode.path("cases")) {
                cases.add(new CaseDefinition(caseNode.path("caseId").asText(),
                        caseNode.path("title").asText(),
                        caseNode.path("runId").isMissingNode() || caseNode.path("runId").isNull()
                                ? null : caseNode.path("runId").asText(),
                        caseNode.path("fixture").isMissingNode() || caseNode.path("fixture").isNull()
                                ? null : caseNode.path("fixture").asText(),
                        expectation(caseNode.path("expectation")),
                        caseNode.path("expectedVerdict").asText(),
                        strings(caseNode.path("expectedDetails"))));
            }
            var bundleNode = blockNode.path("bundleCase");
            BundleCase bundleCase = bundleNode.isObject()
                    ? new BundleCase(bundleNode.path("caseId").asText(), bundleNode.path("title").asText(),
                            bundleNode.path("expectedVerdict").asText(),
                            strings(bundleNode.path("expectedDetails")))
                    : null;
            blocks.add(new Block(blockNode.path("blockId").asText(), blockNode.path("kind").asText(),
                    blockNode.path("evidenceDir").asText(), cases, bundleCase));
        }
        return new EvalChainSuite(root.path("schemaVersion").asInt(), root.path("suiteId").asText(), blocks);
    }

    private static ExpectationSpec expectation(JsonNode node) {
        if (!node.isObject()) throw new IllegalArgumentException("缺少 expectation");
        return new ExpectationSpec(node.path("taskStatus").asText(), node.path("runState").asText(),
                node.path("coverage").asText(), strings(node.path("requiredActions")),
                node.path("issueReason").isNull() ? null : node.path("issueReason").asText(),
                node.path("expectClaims").asBoolean(false),
                node.path("observedAction").isNull() ? null : node.path("observedAction").asText(),
                node.path("observationStatus").isNull() ? null : node.path("observationStatus").asText(),
                node.path("requireEvidenceLinks").asBoolean(false),
                node.path("expectRejectedUnknownTool").asBoolean(false));
    }

    private static List<String> strings(JsonNode array) {
        if (array == null || !array.isArray()) return List.of();
        var values = new ArrayList<String>();
        array.forEach(node -> values.add(node.asText()));
        return List.copyOf(values);
    }

    public static UUID parseRunId(String value) {
        return UUID.fromString(value);
    }
}
