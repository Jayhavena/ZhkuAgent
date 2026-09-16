package com.zhku.knowledge.answer;

import java.util.List;

/**
 * 有依据问答的内部结果。
 *
 * <p>回答模式与引用列表必须保持一致：有依据回答至少引用一条资料，证据不足时不附带引用。</p>
 */
public record GroundedAnswer(
        /** 回答是否由检索资料支撑。 */
        AnswerMode mode,
        /** 面向用户展示的回答正文。 */
        String answer,
        /** 支撑回答结论的资料出处。 */
        List<GroundedCitation> citations
) {

    /** 防御性复制引用列表，并校验回答模式与引用资料的对应关系。 */
    public GroundedAnswer {
        citations = List.copyOf(citations);
        if (mode == AnswerMode.GROUNDED && citations.isEmpty()) {
            throw new IllegalArgumentException("有依据回答必须包含至少一条出处");
        }
        if (mode == AnswerMode.INSUFFICIENT_EVIDENCE && !citations.isEmpty()) {
            throw new IllegalArgumentException("证据不足回答不能附带不相关出处");
        }
    }
}
