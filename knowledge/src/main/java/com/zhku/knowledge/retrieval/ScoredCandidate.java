package com.zhku.knowledge.retrieval;

import java.util.Objects;

/**
 * 融合后的带分候选。
 *
 * @param candidateId 候选标识
 * @param score RRF 融合分；单路降级时为该路的 RRF 分量，仅用于排序，不对外承诺语义
 */
public record ScoredCandidate(String candidateId, double score) {

    public ScoredCandidate {
        Objects.requireNonNull(candidateId, "候选标识不能为空");
    }
}
