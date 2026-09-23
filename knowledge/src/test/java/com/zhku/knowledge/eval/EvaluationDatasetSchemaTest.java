package com.zhku.knowledge.eval;

import com.zhku.knowledge.routing.AnswerRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 版本化评测集的**结构契约**（阶段 10 批2）。
 *
 * <p>评测集是基线数字的唯一出处，所以这里锁四件事：语料指纹必须带 `[EVAL-CORPUS]` 前缀
 * （否则数字变没变不可解释）；路由名必须是既有五级之一（防拼写漂移）；caseId 不得重复
 * （防同一条用例被悄悄改了两次）；manifest 与数据集必须交叉核对一致（防"改了数据忘了改清单"）。</p>
 */
class EvaluationDatasetSchemaTest {

    private static final String CORPUS_FINGERPRINT_PREFIX = "[EVAL-CORPUS]";

    @TempDir
    Path tempDirectory;

    @Test
    void shouldLoadVersionedDatasetAndCrossCheckWithManifest() throws Exception {
        EvaluationDataset dataset = loader().loadDataset(resource("eval/datasets/five-level-routing-v1.json"));
        EvaluationManifest manifest = loader().loadManifest(resource("eval/manifest.json"));

        assertThat(dataset.datasetId()).isEqualTo("five-level-routing");
        assertThat(dataset.datasetVersion()).isEqualTo("v1");
        assertThat(dataset.corpusFingerprint()).startsWith(CORPUS_FINGERPRINT_PREFIX);
        assertThat(dataset.cases()).hasSize(5);

        EvaluationManifest.Entry entry = manifest.datasets().getFirst();
        EvaluationDatasetLoader.verifyAgainst(manifest, entry, dataset);

        // 断言覆盖三类场景：预期路由 / 预期引用 / 应拒答，且都带版本号（来自数据集而非硬编码）。
        assertThat(dataset.cases())
                .extracting(EvaluationDataset.Case::caseId)
                .containsExactly("adjustment-how-to", "credit-limit-per-term",
                        "retake-after-failure", "notice-window", "scholarship-must-refuse");
        assertThat(dataset.cases().getFirst().expectedRoute())
                .isEqualTo(AnswerRoute.HIGH_RISK_RULE);
        assertThat(dataset.cases().getFirst().expectedCitations())
                .extracting(EvaluationDataset.Citation::sourceLocator)
                .containsExactly("PDF 第 77 页，第 9、13 条");
        assertThat(dataset.cases().getLast().expectRefusal()).isTrue();
        assertThat(dataset.cases().getLast().expectedRoute())
                .isEqualTo(AnswerRoute.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void shouldRejectDatasetWithoutCorpusFingerprintPrefix() throws Exception {
        Path file = writeDataset("""
                {
                  "datasetId": "five-level-routing",
                  "datasetVersion": "v1",
                  "corpusFingerprint": "共享演示库",
                  "cases": []
                }
                """);

        assertThatThrownBy(() -> loader().loadDataset(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(CORPUS_FINGERPRINT_PREFIX);
    }

    @Test
    void shouldRejectRouteOutsideTheFiveLevels() throws Exception {
        Path file = writeDataset("""
                {
                  "datasetId": "five-level-routing",
                  "datasetVersion": "v1",
                  "corpusFingerprint": "[EVAL-CORPUS] 测试",
                  "cases": [
                    { "caseId": "c1", "question": "问题",
                      "expectedRoute": "LEVEL_SIX", "expectedHandoffRequired": false,
                      "expectRefusal": false, "expectedCitations": [] }
                  ]
                }
                """);

        assertThatThrownBy(() -> loader().loadDataset(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LEVEL_SIX");
    }

    @Test
    void shouldRejectDuplicateCaseId() throws Exception {
        Path file = writeDataset("""
                {
                  "datasetId": "five-level-routing",
                  "datasetVersion": "v1",
                  "corpusFingerprint": "[EVAL-CORPUS] 测试",
                  "cases": [
                    { "caseId": "c1", "question": "问题一",
                      "expectedRoute": "CURRENT_NOTICE", "expectedHandoffRequired": false,
                      "expectRefusal": false, "expectedCitations": [] },
                    { "caseId": "c1", "question": "问题二",
                      "expectedRoute": "CURRENT_NOTICE", "expectedHandoffRequired": false,
                      "expectRefusal": false, "expectedCitations": [] }
                  ]
                }
                """);

        assertThatThrownBy(() -> loader().loadDataset(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caseId 重复");
    }

    @Test
    void shouldRejectManifestThatDisagreesWithDataset() throws Exception {
        EvaluationDataset dataset = loader().loadDataset(resource("eval/datasets/five-level-routing-v1.json"));
        EvaluationManifest manifest = loader().loadManifest(resource("eval/manifest.json"));
        EvaluationManifest.Entry tampered = new EvaluationManifest.Entry(
                manifest.datasets().getFirst().datasetId(),
                manifest.datasets().getFirst().file(),
                manifest.datasets().getFirst().datasetVersion(),
                // 数据集实际 5 条，清单却登记 4 条：这种漂移必须当场报错，否则基线不可信。
                4);

        assertThatThrownBy(() -> EvaluationDatasetLoader.verifyAgainst(manifest, tampered, dataset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用例数");
    }

    private EvaluationDatasetLoader loader() {
        return new EvaluationDatasetLoader();
    }

    private Path resource(String resourcePath) throws Exception {
        return Paths.get(getClass().getClassLoader().getResource(resourcePath).toURI());
    }

    private Path writeDataset(String json) throws Exception {
        Path file = tempDirectory.resolve("dataset.json");
        Files.writeString(file, json);
        return file;
    }
}
