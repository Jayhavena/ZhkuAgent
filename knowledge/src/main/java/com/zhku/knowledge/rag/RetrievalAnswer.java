package com.zhku.knowledge.rag;

import java.util.List;
import java.util.Objects;

/**
 * 检索增强级的完整作答：受控生成结果 + 引用明细。
 *
 * <p>{@link #composed()} 里的 `citationIds` 是守卫通过后可下发的引用；
 * {@link #citations()} 是本轮检索候选的服务端来源明细。生成降级时后者只供内部归因，
 * 不得把候选明细当作已验证引用卡下发给学生。</p>
 */
public record RetrievalAnswer(ComposedAnswer composed, List<ComposedAnswerCitation> citations) {

    public RetrievalAnswer {
        Objects.requireNonNull(composed, "回答不能为空");
        Objects.requireNonNull(citations, "引用明细不能为空");
        citations = List.copyOf(citations);
    }
}
