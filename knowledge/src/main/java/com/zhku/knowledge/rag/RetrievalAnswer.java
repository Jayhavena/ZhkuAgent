package com.zhku.knowledge.rag;

import java.util.List;
import java.util.Objects;

/**
 * 检索增强级的完整作答：受控生成结果 + 引用明细。
 *
 * <p>{@link #composed()} 里的 `citationIds` 是模型可见的白名单标识；
 * {@link #citations()} 是同一批标识的来源明细（标题/定位/页码），
 * 供服务端组装引用卡。两者由服务端在同一次分配中产生，长度与顺序一致。</p>
 */
public record RetrievalAnswer(ComposedAnswer composed, List<ComposedAnswerCitation> citations) {

    public RetrievalAnswer {
        Objects.requireNonNull(composed, "回答不能为空");
        Objects.requireNonNull(citations, "引用明细不能为空");
        citations = List.copyOf(citations);
    }
}
