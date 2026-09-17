package com.zhku.knowledge.eval;

import com.zhku.knowledge.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评测指标的边界与口径测试。
 *
 * <p>口径必须先钉死再谈数字：Recall@K 的分母是"该问题的相关片段数"，
 * MRR 只看第一条相关命中的位置，引用准确率只看"实际引用的片段中有多少是相关的"。
 * 相关集为空时不计入（避免 0/0 被当成满分）。</p>
 */
class RetrievalMetricsTest {

    private static final UUID R1 = UUID.fromString("e1e11111-1111-4111-8111-111111111111");
    private static final UUID R2 = UUID.fromString("e2e22222-2222-4222-8222-222222222222");
    private static final UUID OTHER = UUID.fromString("e3e33333-3333-4333-8333-333333333333");

    @Test
    void shouldComputeRecallAtK() {
        List<UUID> ranked = List.of(R1, OTHER, R2);

        assertThat(RetrievalMetrics.recallAtK(ranked, Set.of(R1, R2), 3)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.recallAtK(ranked, Set.of(R1, R2), 1)).isEqualTo(0.5);
        assertThat(RetrievalMetrics.recallAtK(ranked, Set.of(R1, R2), 2)).isEqualTo(0.5);
        assertThat(RetrievalMetrics.recallAtK(List.of(OTHER), Set.of(R1), 5)).isEqualTo(0.0);
    }

    @Test
    void shouldComputeReciprocalRankFromFirstRelevantHit() {
        assertThat(RetrievalMetrics.reciprocalRank(List.of(R1, R2), Set.of(R2))).isEqualTo(0.5);
        assertThat(RetrievalMetrics.reciprocalRank(List.of(R2, R1), Set.of(R2))).isEqualTo(1.0);
        assertThat(RetrievalMetrics.reciprocalRank(List.of(OTHER), Set.of(R1))).isZero();
        assertThat(RetrievalMetrics.reciprocalRank(List.of(), Set.of(R1))).isZero();
    }

    @Test
    void shouldComputeCitationAccuracyOverRetrievedChunks() {
        List<RetrievedChunk> retrieved = List.of(chunk(R1), chunk(R2), chunk(OTHER));

        // 引用了 3 条，其中 2 条相关 → 2/3，不做任何"宽松"处理。
        assertThat(RetrievalMetrics.citationAccuracy(retrieved, Set.of(R1, R2)))
                .isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(RetrievalMetrics.citationAccuracy(List.of(), Set.of(R1))).isZero();
    }

    @Test
    void shouldTreatEmptyRelevantSetAsZeroInsteadOfPerfectScore() {
        // 相关集为空属于评测集构造错误；返回 0 并要求评测集自检，而不是判满分。
        assertThat(RetrievalMetrics.recallAtK(List.of(R1), Set.of(), 3)).isZero();
        assertThat(RetrievalMetrics.reciprocalRank(List.of(R1), Set.of())).isZero();
    }

    private static RetrievedChunk chunk(UUID chunkId) {
        return new RetrievedChunk(
                chunkId, "内容", "学生手册 2026 版", "第 1 页", 1, "学生手册 2026 版", "a".repeat(64));
    }
}
