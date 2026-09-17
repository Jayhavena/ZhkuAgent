package com.zhku.knowledge.eval;

import com.zhku.knowledge.retrieval.RetrievedChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 检索评测指标：Recall@K、MRR、引用准确率。
 *
 * <p>口径固定（改动即基线变更，须登记证据账本）：
 * <ul>
 *   <li>Recall@K = 前 K 名中命中的相关片段数 / 该问题全部相关片段数；</li>
 *   <li>MRR = 第一条相关命中的名次倒数（1/rank），无命中记 0；</li>
 *   <li>引用准确率 = 实际引用的片段中属于相关集的比例。</li>
 * </ul>
 * 相关集为空视为评测集构造错误，返回 0 而非满分——避免 0/0 被误读成"完美"。</p>
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    public static double recallAtK(List<UUID> ranked, Set<UUID> relevant, int k) {
        Objects.requireNonNull(ranked, "排名列表不能为空");
        Objects.requireNonNull(relevant, "相关集不能为空");
        if (relevant.isEmpty() || k <= 0) {
            return 0.0;
        }
        Set<UUID> topK = new HashSet<>(ranked.subList(0, Math.min(k, ranked.size())));
        long hit = relevant.stream().filter(topK::contains).count();
        return (double) hit / relevant.size();
    }

    public static double reciprocalRank(List<UUID> ranked, Set<UUID> relevant) {
        Objects.requireNonNull(ranked, "排名列表不能为空");
        Objects.requireNonNull(relevant, "相关集不能为空");
        if (relevant.isEmpty()) {
            return 0.0;
        }
        for (int index = 0; index < ranked.size(); index++) {
            if (relevant.contains(ranked.get(index))) {
                return 1.0 / (index + 1);
            }
        }
        return 0.0;
    }

    public static double citationAccuracy(List<RetrievedChunk> retrieved, Set<UUID> relevant) {
        Objects.requireNonNull(retrieved, "引用列表不能为空");
        Objects.requireNonNull(relevant, "相关集不能为空");
        if (retrieved.isEmpty()) {
            return 0.0;
        }
        long relevantCitations = retrieved.stream()
                .filter(chunk -> relevant.contains(chunk.chunkId()))
                .count();
        return (double) relevantCitations / retrieved.size();
    }
}
