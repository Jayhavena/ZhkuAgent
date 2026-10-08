package com.zhku.knowledge.rag;

import java.util.List;
import java.util.Objects;

/**
 * 模型受控输出的结构化视图。
 *
 * <p>四个字段与冻结契约一致：answer / uncertainty / citationIds / handoffRequired。
 * 该对象只是"模型说了什么"的记录，不代表回答可下发——能否下发由
 * {@link AnswerEvidenceGuard} 判定。</p>
 */
public record ModelResponse(
        String answer,
        double uncertainty,
        List<String> citationIds,
        boolean handoffRequired
) {

    public ModelResponse {
        Objects.requireNonNull(answer, "模型回答不能为空");
        Objects.requireNonNull(citationIds, "引用标识列表不能为空");
        citationIds = List.copyOf(citationIds);
    }
}
