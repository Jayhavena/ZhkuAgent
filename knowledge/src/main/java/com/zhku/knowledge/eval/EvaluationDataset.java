package com.zhku.knowledge.eval;

import com.zhku.knowledge.routing.AnswerRoute;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 版本化评测集（阶段 10 批2）：一次评测的**输入与预期**快照。
 *
 * <p><b>为什么每条都必须带语料指纹</b>：评测数字只有在"语料版本相同"的前提下才可比；
 * 指纹缺失或含糊，"数字变没变"就无法归因（是模型变了还是语料变了）。指纹用
 * `[EVAL-CORPUS]` 前缀标记，加载时强制校验。</p>
 *
 * <p><b>为什么路由名用既有枚举校验</b>：五级路由是冻结口径，评测集里出现第六级
 * 不是"扩展"而是拼写漂移——在加载时直接拒绝，避免基线悄悄对错对象。</p>
 */
public record EvaluationDataset(
        String datasetId,
        String datasetVersion,
        String corpusFingerprint,
        List<Case> cases
) {

    public static final String CORPUS_FINGERPRINT_PREFIX = "[EVAL-CORPUS]";

    public EvaluationDataset {
        datasetId = requireText(datasetId, "datasetId 不能为空");
        datasetVersion = requireText(datasetVersion, "datasetVersion 不能为空");
        corpusFingerprint = requireText(corpusFingerprint, "语料指纹不能为空");
        if (!corpusFingerprint.startsWith(CORPUS_FINGERPRINT_PREFIX)) {
            throw new IllegalArgumentException(
                    "语料指纹必须以 " + CORPUS_FINGERPRINT_PREFIX + " 开头，否则基线数字不可归因");
        }
        Objects.requireNonNull(cases, "评测用例不能为空");
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("评测集必须包含至少一条用例");
        }
        Set<String> seen = new HashSet<>();
        for (Case evalCase : cases) {
            if (!seen.add(evalCase.caseId())) {
                throw new IllegalArgumentException("caseId 重复：" + evalCase.caseId());
            }
        }
        cases = List.copyOf(cases);
    }

    /** 一条评测用例：问题 + 预期路由/是否转人工/是否应拒答 + 预期引用（条级定位）。 */
    public record Case(
            String caseId,
            String question,
            AnswerRoute expectedRoute,
            boolean expectedHandoffRequired,
            boolean expectRefusal,
            List<Citation> expectedCitations
    ) {

        public Case {
            caseId = requireText(caseId, "caseId 不能为空");
            question = requireText(question, "question 不能为空");
            Objects.requireNonNull(expectedRoute, "expectedRoute 不能为空");
            expectedCitations = List.copyOf(expectedCitations == null
                    ? List.of() : expectedCitations);
        }
    }

    /** 预期引用：条级定位必填（页码可选）——这是"依据哪一条"的可核验锚点。 */
    public record Citation(String sourceLocator, Integer pageNumber) {

        public Citation {
            sourceLocator = requireText(sourceLocator, "引用定位不能为空");
        }
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return stripped;
    }
}
