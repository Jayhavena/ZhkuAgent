package com.zhku.knowledge.eval;

import com.zhku.knowledge.routing.AnswerRoute;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 评测执行器与对比报告（阶段 10 批3）。
 *
 * <p>三条判据：① 逐用例给出"预期 vs 实际"（路由 / 条级引用定位符 / 应拒答）与汇总指标；
 * ② 报告**必须**携带 `[EVAL-CORPUS]` 语料指纹（缺指纹的数字按约定视为无效）；
 * ③ 执行器**必须经批2 的加载器与清单交叉核对**——绕开它自己读 JSON 是不允许的，
 * 清单与数据集不一致时必须拒绝执行（而不是"照样跑出一份看起来正常的报告"）。</p>
 */
class EvaluationRunnerTest {

    private static final String FINGERPRINT = "[EVAL-CORPUS] 测试语料@v1";

    @Test
    void shouldReportExpectedVersusActualForEveryCaseAndPassWhenAllMatch() throws Exception {
        EvaluationDataset dataset = dataset("c1", "c2", "c3");
        EvaluationRunner runner = new EvaluationRunner(questions -> answers().get(questions),
                new EvaluationDatasetLoader());

        EvaluationReport report = runner.run(dataset);

        assertThat(report.datasetId()).isEqualTo("runner-test");
        assertThat(report.datasetVersion()).isEqualTo("v1");
        assertThat(report.corpusFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(report.totalCases()).isEqualTo(3);
        assertThat(report.passedCases()).isEqualTo(3);
        // 每条都记下"预期 vs 实际"两侧，报告才有可核验性（不能只给一个通过率）。
        assertThat(report.outcomes()).hasSize(3);
        EvaluationReport.CaseOutcome first = report.outcomes().getFirst();
        assertThat(first.expectedRoute()).isEqualTo(AnswerRoute.HIGH_RISK_RULE);
        assertThat(first.actualRoute()).isEqualTo(AnswerRoute.HIGH_RISK_RULE);
        assertThat(first.expectedLocators())
                .containsExactly("PDF 第 77 页，第 9、13 条");
        assertThat(first.actualLocators())
                .containsExactly("PDF 第 77 页，第 9、13 条");
    }

    @Test
    void shouldDetectRouteClauseAndRefusalMismatches() throws Exception {
        EvaluationDataset dataset = dataset("c1", "c2", "c3");
        Map<String, ObservedAnswer> broken = new LinkedHashMap<>(answers());
        // c1：路由答错；c2：引用了别处（条级定位不符）；c3：该拒答却作答了。
        broken.put("问题一", new ObservedAnswer(AnswerRoute.CURRENT_NOTICE,
                List.of("PDF 第 77 页，第 9、13 条"), false));
        broken.put("问题二", new ObservedAnswer(AnswerRoute.RETRIEVAL_AUGMENTED,
                List.of("PDF 第 88 页"), false));
        broken.put("问题三", new ObservedAnswer(AnswerRoute.HIGH_RISK_RULE, List.of(), false));

        EvaluationReport report = new EvaluationRunner(broken::get,
                new EvaluationDatasetLoader()).run(dataset);

        assertThat(report.totalCases()).isEqualTo(3);
        assertThat(report.passedCases()).isZero();
        assertThat(report.routeMatchedCases()).isEqualTo(1); // 只有 c3 的路由碰巧对
        // c1 引用正确、c3 无引用预期（不作断言）→ 条级引用命中 2 条。
        assertThat(report.citationMatchedCases()).isEqualTo(2);
        assertThat(report.refusalMatchedCases()).isEqualTo(2);
        assertThat(report.outcomes().getFirst().routeMatched()).isFalse();
        assertThat(report.outcomes().get(1).citationMatched()).isFalse();
        assertThat(report.outcomes().get(2).refusalMatched()).isFalse();
    }

    @Test
    void shouldRefuseToRunWhenManifestDisagreesWithDataset() throws Exception {
        // 清单登记 3 条、数据集实际 3 条；这里故意把清单写成 2 条来模拟"改了数据忘了改清单"。
        EvaluationManifest manifest = new EvaluationManifest("v1", FINGERPRINT,
                List.of(new EvaluationManifest.Entry("runner-test", "dataset.json", "v1", 2)));
        Path datasetFile = writeDataset();

        assertThatThrownBy(() -> new EvaluationRunner(questions -> answers().get(questions),
                new EvaluationDatasetLoader())
                .runVerified(manifest, manifest.datasets().getFirst(), datasetFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用例数");
    }

    @Test
    void shouldRenderMarkdownReportWithFingerprintAndPerCaseRows() throws Exception {
        EvaluationReport report = new EvaluationRunner(questions -> answers().get(questions),
                new EvaluationDatasetLoader()).run(dataset("c1", "c2", "c3"));

        String markdown = EvaluationReportMarkdown.render(report);

        // 没有指纹的报告一律视为无效数字：渲染器必须把它放在开头显眼位置。
        assertThat(markdown).contains("[EVAL-CORPUS]").contains(FINGERPRINT);
        assertThat(markdown).contains("| c1 |").contains("HIGH_RISK_RULE");
        assertThat(markdown).contains("PDF 第 77 页，第 9、13 条");
        assertThat(markdown).contains("| 通过 | 3 / 3 |");
    }

    private static EvaluationDataset dataset(String... caseIds) {
        return new EvaluationDataset("runner-test", "v1", FINGERPRINT, List.of(
                new EvaluationDataset.Case("c1", "问题一", AnswerRoute.HIGH_RISK_RULE, false, false,
                        List.of(new EvaluationDataset.Citation("PDF 第 77 页，第 9、13 条", 77))),
                new EvaluationDataset.Case("c2", "问题二", AnswerRoute.RETRIEVAL_AUGMENTED, false,
                        false, List.of(new EvaluationDataset.Citation("PDF 第 76 页", 76))),
                new EvaluationDataset.Case("c3", "问题三", AnswerRoute.INSUFFICIENT_EVIDENCE, true,
                        true, List.of())));
    }

    private static Map<String, ObservedAnswer> answers() {
        Map<String, ObservedAnswer> answers = new LinkedHashMap<>();
        answers.put("问题一", new ObservedAnswer(AnswerRoute.HIGH_RISK_RULE,
                List.of("PDF 第 77 页，第 9、13 条"), false));
        answers.put("问题二", new ObservedAnswer(AnswerRoute.RETRIEVAL_AUGMENTED,
                List.of("PDF 第 76 页"), false));
        answers.put("问题三", new ObservedAnswer(AnswerRoute.INSUFFICIENT_EVIDENCE, List.of(), true));
        return answers;
    }

    private Path writeDataset() throws Exception {
        Path file = Files.createTempFile("runner-test", ".json");
        Files.writeString(file, """
                {
                  "datasetId": "runner-test",
                  "datasetVersion": "v1",
                  "corpusFingerprint": "[EVAL-CORPUS] 测试语料@v1",
                  "cases": [
                    { "caseId": "c1", "question": "问题一", "expectedRoute": "HIGH_RISK_RULE",
                      "expectedHandoffRequired": false, "expectRefusal": false,
                      "expectedCitations": [] },
                    { "caseId": "c2", "question": "问题二", "expectedRoute": "RETRIEVAL_AUGMENTED",
                      "expectedHandoffRequired": false, "expectRefusal": false,
                      "expectedCitations": [] },
                    { "caseId": "c3", "question": "问题三",
                      "expectedRoute": "INSUFFICIENT_EVIDENCE",
                      "expectedHandoffRequired": true, "expectRefusal": true,
                      "expectedCitations": [] }
                  ]
                }
                """);
        return file;
    }
}
