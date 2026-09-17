package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 双路候选融合的降级与可重复排名测试。
 *
 * <p>词法/向量两路各自先产出 ranked candidates，仅在双路均可用时做 RRF 融合；
 * 单路不可用时不算融合分，直接沿用幸存的一路排名（降级而非造数）。
 * 当前词法路为受控关键词匹配，本测试不涉及任何 BM25 实现。</p>
 */
class HybridCandidateFusionTest {

    private final HybridCandidateFuser fuser = new HybridCandidateFuser();

    @Test
    void shouldFuseBothSourcesWithRrf() {
        List<RankedCandidate> lexical = List.of(
                new RankedCandidate("A", 1),
                new RankedCandidate("B", 2),
                new RankedCandidate("C", 3)
        );
        List<RankedCandidate> vector = List.of(
                new RankedCandidate("B", 1),
                new RankedCandidate("A", 2),
                new RankedCandidate("D", 3)
        );

        List<ScoredCandidate> fused = fuser.fuse(lexical, vector);

        // A、B 同分两路互证，应排在仅在单路出现的 C、D 之前。
        assertThat(fused).extracting(ScoredCandidate::candidateId)
                .containsExactly("A", "B", "C", "D");
        assertThat(fused.get(0).score()).isGreaterThan(fused.get(2).score());
    }

    @Test
    void shouldProduceRepeatableRanking() {
        List<RankedCandidate> lexical = List.of(
                new RankedCandidate("X", 1), new RankedCandidate("Y", 2));
        List<RankedCandidate> vector = List.of(
                new RankedCandidate("Y", 1), new RankedCandidate("Z", 2));

        List<ScoredCandidate> first = fuser.fuse(lexical, vector);
        List<ScoredCandidate> second = fuser.fuse(lexical, vector);

        // 融合必须确定性：相同输入两次运行排名一致，否则评测基线不可复核。
        assertThat(first).extracting(ScoredCandidate::candidateId)
                .isEqualTo(List.of("Y", "X", "Z"));
        assertThat(second).extracting(ScoredCandidate::candidateId)
                .containsExactlyElementsOf(
                        first.stream().map(ScoredCandidate::candidateId).toList());
    }

    @Test
    void shouldDegradeToLexicalOnlyWhenVectorUnavailable() {
        List<RankedCandidate> lexical = List.of(
                new RankedCandidate("A", 1), new RankedCandidate("B", 2));

        List<ScoredCandidate> fused = fuser.fuse(lexical, List.of());

        assertThat(fused).extracting(ScoredCandidate::candidateId)
                .containsExactly("A", "B");
    }

    @Test
    void shouldDegradeToVectorOnlyWhenLexicalUnavailable() {
        List<RankedCandidate> vector = List.of(
                new RankedCandidate("V1", 1), new RankedCandidate("V2", 2));

        List<ScoredCandidate> fused = fuser.fuse(List.of(), vector);

        assertThat(fused).extracting(ScoredCandidate::candidateId)
                .containsExactly("V1", "V2");
    }

    @Test
    void shouldReturnEmptyWhenBothSourcesEmpty() {
        assertThat(fuser.fuse(List.of(), List.of())).isEmpty();
    }

    @Test
    void shouldBreakTiesDeterministicallyByCandidateId() {
        // 两个候选排名镜像对称、RRF 分数相同：按标识字典序决胜，保证可重复。
        List<RankedCandidate> lexical = List.of(
                new RankedCandidate("M", 1), new RankedCandidate("N", 2));
        List<RankedCandidate> vector = List.of(
                new RankedCandidate("N", 1), new RankedCandidate("M", 2));

        List<ScoredCandidate> fused = fuser.fuse(lexical, vector);

        assertThat(fused.get(0).score()).isEqualTo(fused.get(1).score());
        assertThat(fused).extracting(ScoredCandidate::candidateId)
                .containsExactly("M", "N");
    }
}
