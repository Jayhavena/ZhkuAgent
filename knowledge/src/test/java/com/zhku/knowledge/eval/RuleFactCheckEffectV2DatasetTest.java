package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v2-dev 数据集的独立结构校验。
 *
 * <p>v1-dev 仍由 Eval 冻结工作树中的原测试负责，本类不引用 v1 文件、也不替代它：
 * v2 只是新增版本，尚未取得人工复核与 gold 冻结，因此这里只验证结构、标签归属、
 * 来源锚点与“除四处修订外无意外变化”，不代表 Agent 质量或 Gold Dataset 通过。
 *
 * <p>本模块资源目录内只允许存在 v2 一份数据，避免两版数据同时被测试加载造成漂移。
 */
class RuleFactCheckEffectV2DatasetTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DATASET_RESOURCE = "agent-effect-eval/rule-fact-check-effect-v2-dev.json";
    private static final String MANIFEST_RESOURCE = "agent-effect-eval/manifest-v2-dev.json";
    private static final String V1_RESOURCE = "agent-effect-eval/rule-fact-check-effect-v1-dev.json";
    private static final String V2_FILE_NAME = "rule-fact-check-effect-v2-dev.json";

    /** v1-dev 各题原有的四条关键失败标签；v2 只允许 RFE-013/RFE-018 追加，其他题不得漂移。 */
    private static final Set<String> BASELINE_TAGS = Set.of(
            "UNSUPPORTED_CLAIM", "CITATION_MISMATCH", "WRONG_RULE_VERSION", "SCOPE_OVERREACH");

    @Test
    void v2ResourceAndManifestArePinnedAndSelfConsistent() throws Exception {
        JsonNode dataset = resource(DATASET_RESOURCE);
        JsonNode manifest = resource(MANIFEST_RESOURCE);

        assertThat(dataset.path("datasetId").asText()).isEqualTo("zhku-rule-fact-check-effect");
        assertThat(dataset.path("datasetVersion").asText()).isEqualTo("v2-dev");
        assertThat(dataset.path("datasetStatus").asText()).isEqualTo("DRAFT_NEEDS_HUMAN_CASE_REVIEW");
        assertThat(dataset.path("samplingStatus").asText()).isEqualTo("EXPLORATORY");
        assertThat(dataset.path("schemaVersion").asInt()).isPositive();

        JsonNode cases = dataset.path("cases");
        assertThat(cases.size()).isEqualTo(20);
        assertThat(dataset.path("caseCount").asInt()).isEqualTo(cases.size());

        Set<String> ids = new HashSet<>();
        for (JsonNode evalCase : cases) {
            assertThat(ids.add(evalCase.path("caseId").asText()))
                    .as("duplicate case id").isTrue();
        }
        assertThat(ids).hasSize(20);
        for (int i = 1; i <= 20; i++) {
            assertThat(ids).contains(String.format("RFE-%03d", i));
        }

        assertThat(manifest.path("datasetId").asText()).isEqualTo(dataset.path("datasetId").asText());
        assertThat(manifest.path("datasetVersion").asText()).isEqualTo(dataset.path("datasetVersion").asText());
        assertThat(manifest.path("caseCount").asInt()).isEqualTo(cases.size());
        assertThat(manifest.path("corpusFingerprint").asText())
                .isEqualTo(dataset.path("corpusFingerprint").asText());
        assertThat(manifest.path("datasetFile").asText()).isEqualTo(V2_FILE_NAME);
        assertThat(sha256(Files.readAllBytes(resourcePath(DATASET_RESOURCE))))
                .isEqualTo(manifest.path("datasetSha256").asText());
    }

    @Test
    void onlyV2IsLoadableFromThisModule() {
        // 单一份权威数据：若有人把 v1 再放进本模块资源目录，这里会失败，提醒先决定由哪一版生效。
        assertThat(getClass().getClassLoader().getResource(V1_RESOURCE)).isNull();
    }

    @Test
    void everyCaseTagIsDeclaredByTheScoringContractAndUnique() throws Exception {
        JsonNode dataset = resource(DATASET_RESOURCE);
        Set<String> contractTags = new HashSet<>();
        for (JsonNode tag : dataset.path("scoringContract").path("criticalFailureTags")) {
            contractTags.add(tag.asText());
        }
        assertThat(contractTags).hasSize(8);

        for (JsonNode evalCase : dataset.path("cases")) {
            String caseId = evalCase.path("caseId").asText();
            Set<String> seen = new HashSet<>();
            assertThat(evalCase.path("criticalFailureTags").size())
                    .as("%s must declare at least one critical tag", caseId).isPositive();
            for (JsonNode tag : evalCase.path("criticalFailureTags")) {
                String value = tag.asText();
                assertThat(seen.add(value)).as("%s duplicate tag %s", caseId, value).isTrue();
                assertThat(contractTags).as("%s uses undeclared tag %s", caseId, value).contains(value);
            }
        }
    }

    @Test
    void rfe013AndRfe018CarryExactlyTheIntendedRevision() throws Exception {
        JsonNode cases = resource(DATASET_RESOURCE).path("cases");

        JsonNode rfe013 = byId(cases, "RFE-013");
        assertThat(rfe013.path("mustNotClaim").get(1).asText())
                .isEqualTo("把“可申请”说成“必然获批”");
        assertThat(tagsOf(rfe013)).contains("MISSED_REQUIRED_CLARIFICATION");

        JsonNode rfe018 = byId(cases, "RFE-018");
        assertThat(tagsOf(rfe018)).contains(
                "HALLUCINATED_CURRENT_NOTICE", "FABRICATED_PERSONAL_STATUS", "UNAUTHORIZED_ACTION");

        // 其余 18 题保持 v1 的四条基线标签，防止顺手改动扩散。
        for (JsonNode evalCase : cases) {
            String caseId = evalCase.path("caseId").asText();
            if (caseId.equals("RFE-013") || caseId.equals("RFE-018")) {
                continue;
            }
            assertThat(tagsOf(evalCase)).as("%s tags must stay at v1 baseline", caseId)
                    .containsExactlyInAnyOrderElementsOf(BASELINE_TAGS);
        }
    }

    @Test
    void everyEvidenceAnchorAndExpectedFactStillMatchesB02AndB03() throws Exception {
        JsonNode dataset = resource(DATASET_RESOURCE);
        Path repository = repositoryRoot();
        JsonNode b02 = JSON.readTree(repository.resolve(
                "docs/product/rule-check-2025-b02-semantic-review.json").toFile());
        JsonNode b03 = JSON.readTree(repository.resolve(
                "docs/product/rule-check-2025-b03-publication-review.json").toFile());

        Set<Integer> articles = new HashSet<>();
        for (JsonNode evalCase : dataset.path("cases")) {
            String caseId = evalCase.path("caseId").asText();
            assertThat(evalCase.path("split").asText()).isEqualTo("DEVELOPMENT");
            assertThat(evalCase.path("samplingStatus").asText()).isEqualTo("EXPLORATORY");
            assertThat(evalCase.path("humanReviewStatus").asText())
                    .as("%s still awaits human review", caseId).isEqualTo("PENDING");

            Set<String> locators = new HashSet<>();
            for (JsonNode evidence : evalCase.path("requiredEvidence")) {
                String locatorId = evidence.path("locatorId").asText();
                locators.add(locatorId);
                articles.add(evidence.path("article").asInt());
                JsonNode clause = find(b03.path("clauses"),
                        node -> locatorId.equals(node.path("locatorId").asText()));
                assertThat(clause.isMissingNode())
                        .as("%s locator exists in B03: %s", caseId, locatorId).isFalse();
                assertThat(evidence.path("article").asInt()).isEqualTo(clause.path("article").asInt());
                assertThat(evidence.path("physicalPage").asInt()).isEqualTo(clause.path("physicalPage").asInt());
                assertThat(evidence.path("printedPage").asInt()).isEqualTo(clause.path("printedPage").asInt());
                assertThat(evidence.path("contentChecksum").asText())
                        .isEqualTo(clause.path("contentChecksum").asText());
                assertThat(sha256(clause.path("content").asText().getBytes(StandardCharsets.UTF_8)))
                        .as("%s clause text hash", locatorId)
                        .isEqualTo(clause.path("contentChecksum").asText());
            }

            for (JsonNode expectedFact : evalCase.path("expectedFacts")) {
                String reviewItemId = expectedFact.path("reviewItemId").asText();
                String semanticFactId = expectedFact.path("semanticFactId").asText();
                JsonNode rule = find(b02.path("rules"),
                        node -> reviewItemId.equals(node.path("reviewItemId").asText()));
                assertThat(rule.isMissingNode())
                        .as("%s B02 review item exists: %s", caseId, reviewItemId).isFalse();
                JsonNode fact = find(rule.path("facts"),
                        node -> semanticFactId.equals(node.path("id").asText()));
                assertThat(fact.isMissingNode())
                        .as("%s B02 fact exists: %s/%s", caseId, reviewItemId, semanticFactId).isFalse();
                assertThat(fact.path("semanticReviewState").asText()).isEqualTo("CONFIRMED");
                assertThat(expectedFact.path("expectedClaim").asText()).isEqualTo(fact.path("text").asText());
                assertThat(locators)
                        .as("%s must cite %s", caseId, semanticFactId)
                        .contains(fact.path("evidenceDraft").path("locatorId").asText());
            }
        }
        assertThat(articles).containsExactlyInAnyOrder(2, 4, 5, 6, 8, 9, 10, 11, 12, 18);
    }

    @Test
    void expectedDispositionMatrixIsUnchanged() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("RFE-001", "ANSWER_WITH_EVIDENCE|IN_SCOPE|2|1");
        expected.put("RFE-002", "QUALIFIED_ANSWER|IN_SCOPE|1|1");
        expected.put("RFE-003", "ANSWER_WITH_EVIDENCE|IN_SCOPE|2|1");
        expected.put("RFE-004", "QUALIFIED_ANSWER|IN_SCOPE|2|1");
        expected.put("RFE-005", "QUALIFIED_ANSWER|IN_SCOPE|2|1");
        expected.put("RFE-006", "PARTIAL_WITH_GAP|IN_SCOPE|2|1");
        expected.put("RFE-007", "QUALIFIED_ANSWER|IN_SCOPE|1|1");
        expected.put("RFE-008", "PARTIAL_WITH_GAP|PERSONAL_RECORD_GAP|1|1");
        expected.put("RFE-009", "QUALIFIED_ANSWER|IN_SCOPE|2|1");
        expected.put("RFE-010", "ANSWER_WITH_EVIDENCE|IN_SCOPE|1|1");
        expected.put("RFE-011", "ANSWER_WITH_EVIDENCE|IN_SCOPE|1|1");
        expected.put("RFE-012", "ANSWER_WITH_EVIDENCE|IN_SCOPE|1|1");
        expected.put("RFE-013", "ASK_CLARIFICATION|IN_SCOPE|2|1");
        expected.put("RFE-014", "ANSWER_WITH_EVIDENCE|IN_SCOPE|1|1");
        expected.put("RFE-015", "QUALIFIED_ANSWER|IN_SCOPE|1|1");
        expected.put("RFE-016", "ANSWER_WITH_EVIDENCE|IN_SCOPE|1|1");
        expected.put("RFE-017", "ANSWER_WITH_EVIDENCE|IN_SCOPE|2|1");
        expected.put("RFE-018", "GAP_AND_NEXT_STEP|CURRENT_NOTICE_GAP|1|1");
        expected.put("RFE-019", "GAP_AND_NEXT_STEP|CURRENT_APPLICABILITY_UNKNOWN|1|1");
        expected.put("RFE-020", "GAP_AND_NEXT_STEP|OUT_OF_CORPUS|0|0");

        Map<String, String> actual = new LinkedHashMap<>();
        for (JsonNode evalCase : resource(DATASET_RESOURCE).path("cases")) {
            actual.put(evalCase.path("caseId").asText(),
                    evalCase.path("expectedDisposition").asText() + "|"
                            + evalCase.path("sourceCoverage").asText() + "|"
                            + evalCase.path("expectedFacts").size() + "|"
                            + evalCase.path("requiredEvidence").size());
        }
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void sourceSnapshotIsStillPinnedToTheReviewedArtifacts() throws Exception {
        JsonNode dataset = resource(DATASET_RESOURCE);
        JsonNode source = dataset.path("sourceSnapshot");
        Path repository = repositoryRoot();

        for (JsonNode artifact : source.path("sourceArtifacts")) {
            Path sourcePath = repository.resolve(artifact.path("path").asText()).normalize();
            assertThat(Files.isRegularFile(sourcePath))
                    .as("source artifact exists: %s", artifact.path("path").asText()).isTrue();
            assertThat(sha256(Files.readAllBytes(sourcePath)))
                    .as("source artifact fingerprint: %s", artifact.path("path").asText())
                    .isEqualTo(artifact.path("sha256").asText());
        }

        JsonNode runtime = source.path("runtimeState");
        assertThat(runtime.path("runtimeActivated").asBoolean()).isFalse();
        assertThat(runtime.path("studentReleaseApproved").asBoolean()).isFalse();
        assertThat(runtime.path("catalogueMode").asText()).isEqualTo("CLOSED");
        assertThat(runtime.path("publicationStatus").asText()).isEqualTo("DRAFT");
        assertThat(runtime.path("visibilityScope").asText()).isEqualTo("INTERNAL_ONLY");
        assertThat(runtime.path("currentAcademicYearApplicability").asText()).isEqualTo("UNKNOWN");
        assertThat(source.path("originalPdfBytesAvailableInWorktree").asBoolean()).isFalse();
    }

    private static JsonNode byId(JsonNode cases, String caseId) {
        JsonNode found = find(cases, node -> caseId.equals(node.path("caseId").asText()));
        assertThat(found.isMissingNode()).as("case exists: %s", caseId).isFalse();
        return found;
    }

    private static Set<String> tagsOf(JsonNode evalCase) {
        Set<String> tags = new HashSet<>();
        for (JsonNode tag : evalCase.path("criticalFailureTags")) {
            tags.add(tag.asText());
        }
        return tags;
    }

    private static JsonNode resource(String name) throws IOException {
        try (var stream = RuleFactCheckEffectV2DatasetTest.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IllegalArgumentException("Missing test resource: " + name);
            return JSON.readTree(stream);
        }
    }

    private static Path resourcePath(String name) throws Exception {
        return Path.of(RuleFactCheckEffectV2DatasetTest.class.getClassLoader().getResource(name).toURI());
    }

    private static JsonNode find(JsonNode nodes, java.util.function.Predicate<JsonNode> predicate) {
        for (JsonNode node : nodes) {
            if (predicate.test(node)) return node;
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("docs/product/rule-check-2025-b02-semantic-review.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from test working directory");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
