package com.zhku.knowledge.eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 评测执行器（阶段 10 批3）：按版本化数据集逐用例求"预期 vs 实际"，产出报告。
 *
 * <p><b>为什么入口必须经加载器</b>：数据集与清单的交叉核对（id/版本/用例数/语料指纹）是批2
 * 建立的约定，执行器若自己读 JSON 就等于把这道核对绕开——报告看起来正常，但可能跑的是
 * "清单说的那版"之外的另一份数据。`runVerified` 强制先核对再执行，核对不过直接拒绝。</p>
 *
 * <p><b>执行器只对比、不判定产品是否合格</b>：报告给出逐例差异与指标，是否达标由使用方决定；
 * 把"门禁"混进执行器会让基线数字与放行标准纠缠不清。</p>
 */
public final class EvaluationRunner {

    private final AnswerTarget target;
    private final EvaluationDatasetLoader loader;

    public EvaluationRunner(AnswerTarget target, EvaluationDatasetLoader loader) {
        this.target = Objects.requireNonNull(target, "被测系统端口不能为空");
        this.loader = Objects.requireNonNull(loader, "评测集加载器不能为空");
    }

    /**
     * 经清单核对后执行：先加载数据集、与清单登记四处交叉核对，通过才跑。
     *
     * @throws IOException 读取数据集失败（文件缺失/JSON 非法）
     */
    public EvaluationReport runVerified(
            EvaluationManifest manifest,
            EvaluationManifest.Entry entry,
            Path datasetFile) throws IOException {
        EvaluationDataset dataset = loader.loadDataset(datasetFile);
        EvaluationDatasetLoader.verifyAgainst(manifest, entry, dataset);
        return run(dataset);
    }

    public EvaluationReport run(EvaluationDataset dataset) {
        Objects.requireNonNull(dataset, "评测集不能为空");
        List<EvaluationReport.CaseOutcome> outcomes = new ArrayList<>();
        for (EvaluationDataset.Case evalCase : dataset.cases()) {
            ObservedAnswer observed = Objects.requireNonNull(
                    target.answer(evalCase.question()),
                    "被测系统返回了空结果：" + evalCase.caseId());
            outcomes.add(new EvaluationReport.CaseOutcome(
                    evalCase.caseId(),
                    evalCase.question(),
                    evalCase.expectedRoute(),
                    observed.route(),
                    evalCase.expectedCitations().stream()
                            .map(EvaluationDataset.Citation::sourceLocator).toList(),
                    observed.citationLocators(),
                    evalCase.expectRefusal(),
                    observed.refused()));
        }
        return new EvaluationReport(dataset.datasetId(), dataset.datasetVersion(),
                dataset.corpusFingerprint(), outcomes);
    }
}
