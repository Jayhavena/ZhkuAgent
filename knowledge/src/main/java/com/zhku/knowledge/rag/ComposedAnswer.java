package com.zhku.knowledge.rag;

import java.util.List;
import java.util.Objects;

/**
 * 受控生成的最终结果。
 *
 * <p>DECLINED 时 answer 为面向学生的拒答说明（不含任何模型结论），
 * citationIds 必为空、handoffRequired 必为 true——拒答与转人工绑定，
 * 防止"拒绝回答但不给出口"的死胡同体验。</p>
 */
public record ComposedAnswer(
        ComposedAnswerStatus status,
        String answer,
        List<String> citationIds,
        boolean handoffRequired
) {

    public ComposedAnswer {
        Objects.requireNonNull(status, "结论状态不能为空");
        Objects.requireNonNull(answer, "回答内容不能为空");
        Objects.requireNonNull(citationIds, "引用标识列表不能为空");
        citationIds = List.copyOf(citationIds);
        if (status == ComposedAnswerStatus.DECLINED && (!citationIds.isEmpty() || !handoffRequired)) {
            throw new IllegalArgumentException("拒答结论必须清空引用并标记转人工");
        }
    }

    static ComposedAnswer answered(String answer, List<String> citationIds, boolean handoffRequired) {
        return new ComposedAnswer(ComposedAnswerStatus.ANSWERED, answer, citationIds, handoffRequired);
    }

    static ComposedAnswer declined(String reason) {
        return new ComposedAnswer(ComposedAnswerStatus.DECLINED, reason, List.of(), true);
    }
}
