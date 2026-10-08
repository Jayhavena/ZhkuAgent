package com.zhku.knowledge.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 混合检索编排：两路各自出候选，双路可用时用 RRF 融合，单路不可用时降级。
 *
 * <p>刻意不做"某路分高就多取"的加权：融合只消费名次（见
 * {@link HybridCandidateFuser}），让降级与融合行为都可复现。</p>
 */
public final class HybridRetrievalService {

    private final HybridCandidateFuser fuser;

    public HybridRetrievalService(HybridCandidateFuser fuser) {
        this.fuser = Objects.requireNonNull(fuser, "融合器不能为空");
    }

    /**
     * 执行一轮混合检索。
     *
     * @param query 用户问题
     * @param limit 融合后返回的候选上限
     * @param lexical 词法路检索器
     * @param vector 向量路检索器
     */
    public RetrievalOutcome retrieve(
            String query,
            int limit,
            ChunkCandidateRetriever lexical,
            ChunkCandidateRetriever vector
    ) {
        Objects.requireNonNull(query, "查询不能为空");
        Objects.requireNonNull(lexical, "词法检索器不能为空");
        Objects.requireNonNull(vector, "向量检索器不能为空");
        if (limit <= 0) {
            throw new IllegalArgumentException("返回上限必须为正数");
        }

        // 两路各自最多取 limit 条：融合后的前 limit 名不可能来自单路更靠后的位置。
        PathResult lexicalResult = invoke(lexical, query, limit);
        PathResult vectorResult = invoke(vector, query, limit);

        Map<String, RetrievedChunk> byChunkId = new LinkedHashMap<>();
        lexicalResult.chunks().forEach(chunk -> byChunkId.put(chunk.chunkId().toString(), chunk));
        vectorResult.chunks().forEach(chunk -> byChunkId.putIfAbsent(chunk.chunkId().toString(), chunk));

        List<ScoredCandidate> fused = fuser.fuse(lexicalResult.ranked(), vectorResult.ranked());
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (ScoredCandidate candidate : fused) {
            RetrievedChunk chunk = byChunkId.get(candidate.candidateId());
            if (chunk != null) {
                chunks.add(chunk);
            }
            if (chunks.size() == limit) {
                break;
            }
        }
        return new RetrievalOutcome(chunks, lexicalResult.available(), vectorResult.available());
    }

    private static PathResult invoke(ChunkCandidateRetriever retriever, String query, int limit) {
        List<RetrievedChunk> chunks;
        try {
            chunks = retriever.retrieve(query, limit);
        } catch (RetrieverUnavailableException exception) {
            // 单路不可用按"该路无候选"参与融合，另一路结果照常返回。
            return new PathResult(List.of(), List.of(), false);
        }
        List<RankedCandidate> ranked = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ranked.add(new RankedCandidate(chunks.get(i).chunkId().toString(), i + 1));
        }
        return new PathResult(chunks, ranked, true);
    }

    private record PathResult(
            List<RetrievedChunk> chunks,
            List<RankedCandidate> ranked,
            boolean available
    ) {
    }
}
