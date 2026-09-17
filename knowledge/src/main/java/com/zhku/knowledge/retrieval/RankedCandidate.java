package com.zhku.knowledge.retrieval;

import java.util.Objects;

/**
 * 单路检索产出的带名次候选。
 *
 * @param candidateId 候选标识（检索条目/片段的不透明标识）
 * @param rank 名次，从 1 开始；RRF 融合只消费名次不消费原始分数，
 *             避免词法分与向量相似度量纲不可比的问题
 */
public record RankedCandidate(String candidateId, int rank) {

    public RankedCandidate {
        Objects.requireNonNull(candidateId, "候选标识不能为空");
        if (rank < 1) {
            throw new IllegalArgumentException("名次必须从 1 开始");
        }
    }
}
