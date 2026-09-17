package com.zhku.knowledge.retrieval;

import java.util.List;

/**
 * 单路候选检索（词法或向量）。
 *
 * <p>实现方只返回按相关度降序的候选；资格判定必须复用
 * {@link RetrievalEligibilityFilter}，且该路不可用时应抛
 * {@link RetrieverUnavailableException} 而非返回空列表——"没有命中"与
 * "这一路坏了"对上层降级决策是两个不同的信号。</p>
 */
@FunctionalInterface
public interface ChunkCandidateRetriever {

    List<RetrievedChunk> retrieve(String query, int limit);
}
