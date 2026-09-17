package com.zhku.knowledge.retrieval;

import java.util.List;
import java.util.Objects;

/**
 * 一轮混合检索的结果与可用性标记。
 *
 * @param chunks 融合后的候选片段（降序）
 * @param lexicalAvailable 词法路本轮是否可用
 * @param vectorAvailable 向量路本轮是否可用
 */
public record RetrievalOutcome(
        List<RetrievedChunk> chunks,
        boolean lexicalAvailable,
        boolean vectorAvailable
) {

    public RetrievalOutcome {
        Objects.requireNonNull(chunks, "候选片段列表不能为空");
        chunks = List.copyOf(chunks);
    }

    /** 两路都不可用：本轮检索质量不可评估，评测时必须单独计数。 */
    public boolean fullyDegraded() {
        return !lexicalAvailable && !vectorAvailable;
    }
}
