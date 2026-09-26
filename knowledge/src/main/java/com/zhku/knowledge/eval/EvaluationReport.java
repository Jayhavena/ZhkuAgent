package com.zhku.knowledge.eval;

import com.zhku.knowledge.routing.AnswerRoute;

import java.util.List;
import java.util.Objects;

/**
 * 评测报告：每条用例的"预期 vs 实际"两侧 + 汇总指标。
 *
 * <p><b>为什么必须带语料指纹</b>：报告里的比例/计数只有在"同一语料"前提下才可比较。
 * 指纹随数据集一并进入报告，且是构造期必填（`EvaluationDataset` 已强制
 * `[EVAL-CORPUS]` 前缀）——缺指纹的数字按约定视为无效，这一点由类型系统兜住。</p>
 */
public record EvaluationReport(
        String datasetId,
        String datasetVersion,
        String corpusFingerprint,
        List<CaseOutcome> outcomes
) {

    public EvaluationReport {
        Objects.requireNonNull(datasetId, "datasetId 不能为空");
        Objects.requireNonNull(datasetVersion, "datasetVersion 不能为空");
        if (corpusFingerprint == null
                || !corpusFingerprint.startsWith(EvaluationDataset.CORPUS_FINGERPRINT_PREFIX)) {
            throw new IllegalArgumentException("评测报告必须携带 "
                    + EvaluationDataset.CORPUS_FINGERPRINT_PREFIX + " 语料指纹，否则数字无效");
        }
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "用例结果不能为空"));
    }

    public int totalCases() {
        return outcomes.size();
    }

    public long passedCases() {
        return outcomes.stream().filter(CaseOutcome::passed).count();
    }

    public long routeMatchedCases() {
        return outcomes.stream().filter(CaseOutcome::routeMatched).count();
    }

    public long citationMatchedCases() {
        // 逐题可跳过引用断言，但汇总不能把“无需断言”当成命中，否则会虚增指标。
        return outcomes.stream()
                .filter(outcome -> !outcome.expectedLocators().isEmpty())
                .filter(CaseOutcome::citationMatched)
                .count();
    }

    public long citationRequiredCases() {
        return outcomes.stream().filter(outcome -> !outcome.expectedLocators().isEmpty()).count();
    }

    public long refusalMatchedCases() {
        return outcomes.stream().filter(CaseOutcome::refusalMatched).count();
    }

    /** 一条用例的对比明细：两侧都留证，便于人工复核"差在哪"。 */
    public record CaseOutcome(
            String caseId,
            String question,
            AnswerRoute expectedRoute,
            AnswerRoute actualRoute,
            List<String> expectedLocators,
            List<String> actualLocators,
            boolean expectRefusal,
            boolean actualRefused
    ) {

        public CaseOutcome {
            Objects.requireNonNull(caseId, "caseId 不能为空");
            Objects.requireNonNull(expectedRoute, "预期路由不能为空");
            Objects.requireNonNull(actualRoute, "实际路由不能为空");
            expectedLocators = List.copyOf(expectedLocators == null ? List.of() : expectedLocators);
            actualLocators = List.copyOf(actualLocators == null ? List.of() : actualLocators);
        }

        public boolean routeMatched() {
            return expectedRoute == actualRoute;
        }

        /**
         * 条级引用是否命中：**预期为空时不作断言**（澄清/通知/拒答类用例本就不要求引用）；
         * 否则实际引用必须覆盖预期的每一条条级定位——少引一条就等于依据不完整。
         */
        public boolean citationMatched() {
            return expectedLocators.isEmpty() || actualLocators.containsAll(expectedLocators);
        }

        public boolean refusalMatched() {
            return expectRefusal == actualRefused;
        }

        public boolean passed() {
            return routeMatched() && citationMatched() && refusalMatched();
        }
    }
}
