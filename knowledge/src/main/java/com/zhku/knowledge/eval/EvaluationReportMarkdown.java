package com.zhku.knowledge.eval;

import java.util.List;
import java.util.Objects;

/** 对比报告的 Markdown 渲染：**指纹在最前**，逐例明细在后，便于直接落 docs/ 与人工复核。 */
public final class EvaluationReportMarkdown {

    private EvaluationReportMarkdown() {
    }

    public static String render(EvaluationReport report) {
        Objects.requireNonNull(report, "评测报告不能为空");
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 评测对比报告：").append(report.datasetId())
                .append(" / ").append(report.datasetVersion()).append("\n\n");
        // 指纹放在最前面：任何引用本报告数字的地方都必须连它一起引用。
        markdown.append("**语料指纹**：`").append(report.corpusFingerprint()).append("`\n\n");
        markdown.append("> 报告中任何数字只有在与上述语料指纹同时出现时才有效；"
                + "语料变更后旧数字不可直接比较。\n\n");

        markdown.append("## 指标\n\n");
        markdown.append("| 指标 | 数值 |\n| --- | --- |\n");
        markdown.append("| 用例总数 | ").append(report.totalCases()).append(" |\n");
        markdown.append("| 通过 | ").append(report.passedCases()).append(" / ")
                .append(report.totalCases()).append(" |\n");
        markdown.append("| 路由命中 | ").append(report.routeMatchedCases()).append(" |\n");
        markdown.append("| 条级引用命中 | ").append(report.citationMatchedCases())
                .append(" / ").append(report.citationRequiredCases()).append(" |\n");
        markdown.append("| 转人工判定一致 | ").append(report.handoffMatchedCases()).append(" |\n");
        markdown.append("| 拒答判定一致 | ").append(report.refusalMatchedCases()).append(" |\n\n");
        markdown.append("> 条级引用命中的分母仅含预期引用非空的题；0/0 表示本次没有要求引用的题，"
                + "不计算命中率。\n\n");

        markdown.append("## 逐用例：预期 vs 实际\n\n");
        markdown.append("| caseId | 问题 | 预期路由 | 实际路由 | 预期条级引用 | 实际引用 | 应转人工 | 实际转人工 | 应拒答 | 实际拒答 | 结论 |\n");
        markdown.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (EvaluationReport.CaseOutcome outcome : report.outcomes()) {
            markdown.append("| ").append(outcome.caseId())
                    .append(" | ").append(outcome.question())
                    .append(" | ").append(outcome.expectedRoute())
                    .append(" | ").append(outcome.actualRoute())
                    .append(" | ").append(join(outcome.expectedLocators()))
                    .append(" | ").append(join(outcome.actualLocators()))
                    .append(" | ").append(outcome.expectedHandoffRequired() ? "是" : "否")
                    .append(" | ").append(outcome.actualHandoffRequired() ? "是" : "否")
                    .append(" | ").append(outcome.expectRefusal() ? "是" : "否")
                    .append(" | ").append(outcome.actualRefused() ? "是" : "否")
                    .append(" | ").append(outcome.passed() ? "✅" : "❌")
                    .append(" |\n");
        }
        return markdown.toString();
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? "—" : String.join("；", values);
    }
}
