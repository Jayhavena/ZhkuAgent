package com.zhku.knowledge.rag;

import java.util.List;
import java.util.Objects;

/**
 * 一次受控生成调用的完整输入。
 *
 * @param systemConstraints 不可变系统约束（由生成层统一注入，调用方不可覆盖）
 * @param question 学生问题
 * @param evidence 当轮获准片段，模型上下文除此之外不得包含任何其他资料
 */
public record ModelRequest(
        String systemConstraints,
        String question,
        List<ApprovedEvidence> evidence
) {

    public ModelRequest {
        Objects.requireNonNull(systemConstraints, "系统约束不能为空");
        Objects.requireNonNull(question, "问题不能为空");
        Objects.requireNonNull(evidence, "证据列表不能为空");
        evidence = List.copyOf(evidence);
    }

    /** 证据正文视图，供提示构造与测试断言使用。 */
    public List<String> evidenceTexts() {
        return evidence.stream().map(ApprovedEvidence::text).toList();
    }

    /** 本轮白名单引用标识：模型只能用这些 ID 标注出处，不得自造。 */
    public List<String> evidenceCitationIds() {
        return evidence.stream().map(ApprovedEvidence::citationId).toList();
    }
}
