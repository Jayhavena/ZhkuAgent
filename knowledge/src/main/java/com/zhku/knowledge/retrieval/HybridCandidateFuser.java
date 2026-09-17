package com.zhku.knowledge.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 词法/向量双路候选的 RRF（Reciprocal Rank Fusion）融合器。
 *
 * <p>选择 RRF 而非分数加权：两路原始分数量纲不同（词法命中数 vs 向量余弦），
 * 直接加权会把调参错觉带进基线；RRF 只消费名次，对各路分数分布无假设。</p>
 *
 * <p>降级语义：一路为空时不做融合，幸存一路的排名原样保留——
 * 单路检索的相对顺序不应被"零分参与融合"稀释。</p>
 */
public final class HybridCandidateFuser {

    /** RRF 平滑常数，取常见经验值 60；调整它属于基线变更，必须在证据账本登记。 */
    static final int RRF_K = 60;

    /**
     * 融合两路候选。
     *
     * @param lexicalRanked 词法路 ranked candidates（可为空 = 该路不可用）
     * @param vectorRanked 向量路 ranked candidates（可为空 = 该路不可用）
     * @return 按融合分降序、同分按标识字典序的确定性排名
     */
    public List<ScoredCandidate> fuse(
            List<RankedCandidate> lexicalRanked,
            List<RankedCandidate> vectorRanked
    ) {
        Objects.requireNonNull(lexicalRanked, "词法候选列表不能为空");
        Objects.requireNonNull(vectorRanked, "向量候选列表不能为空");

        Map<String, Double> scores = new HashMap<>();
        accumulate(scores, lexicalRanked);
        accumulate(scores, vectorRanked);

        List<ScoredCandidate> fused = new ArrayList<>();
        scores.forEach((id, score) -> fused.add(new ScoredCandidate(id, score)));
        // 同分按标识字典序：排名必须可重复，否则 Recall@K/MRR 基线无法复核。
        fused.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(ScoredCandidate::candidateId));
        return fused;
    }

    private static void accumulate(Map<String, Double> scores, List<RankedCandidate> ranked) {
        for (RankedCandidate candidate : ranked) {
            scores.merge(candidate.candidateId(), 1.0 / (RRF_K + candidate.rank()), Double::sum);
        }
    }
}
