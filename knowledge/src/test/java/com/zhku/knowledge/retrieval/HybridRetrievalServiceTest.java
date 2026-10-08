package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 混合检索编排测试：双路融合、单路降级、双路不可用。
 *
 * <p>降级必须是"少一路结果"而不是"整轮失败"；两路都不可用时返回空候选，
 * 由编排层的第五级给出拒答——检索层不得凭记忆补内容。</p>
 */
class HybridRetrievalServiceTest {

    private static final RetrievedChunk A = chunk("chunk-a", "第一条 最高修读 4 学分。");
    private static final RetrievedChunk B = chunk("chunk-b", "第二条 不办理缓考。");
    private static final RetrievedChunk C = chunk("chunk-c", "第三条 选课入口在教务系统。");

    /** UUID 不可读，断言用可读标签映射回来，失败信息才可诊断。 */
    private static final java.util.Map<UUID, String> LABELS = java.util.Map.of(
            A.chunkId(), "chunk-a",
            B.chunkId(), "chunk-b",
            C.chunkId(), "chunk-c");

    private final HybridRetrievalService service = new HybridRetrievalService(
            new HybridCandidateFuser());

    @Test
    void sharedSecondRankMustSurviveBeforeFinalTruncation() {
        // 两路第二名共享证据的融合分高于单路第一名，不能先按最终 K 截掉它。
        RetrievalOutcome outcome = service.retrieve(
                "校园规则", 1, fakeRetriever(A, C), fakeRetriever(B, C));

        assertThat(idsOf(outcome)).containsExactly("chunk-c");
    }

    @Test
    void shouldFuseTwoCandidatesBeforeReturningOne() {
        RetrievalOutcome outcome = service.retrieve(
                "校园规则", 2, 1, fakeRetriever(A, C), fakeRetriever(B, C));

        assertThat(idsOf(outcome)).containsExactly("chunk-c");
        assertThat(outcome.lexicalAvailable()).isTrue();
        assertThat(outcome.vectorAvailable()).isTrue();
    }

    @Test
    void shouldForwardCandidateBudgetToBothPathsAndLimitFinalResults() {
        List<Integer> requestedLimits = new ArrayList<>();
        ChunkCandidateRetriever lexical = (query, limit) -> {
            assertThat(query).isEqualTo("校园规则");
            requestedLimits.add(limit);
            return fakeRetriever(A, B, C).retrieve(query, limit);
        };
        ChunkCandidateRetriever vector = (query, limit) -> {
            assertThat(query).isEqualTo("校园规则");
            requestedLimits.add(limit);
            return fakeRetriever(B, C).retrieve(query, limit);
        };

        RetrievalOutcome outcome = service.retrieve("校园规则", 3, 2, lexical, vector);

        assertThat(requestedLimits).containsExactly(3, 3);
        assertThat(idsOf(outcome)).containsExactly("chunk-b", "chunk-c");
    }

    @Test
    void shouldKeepLegacyCandidateBudgetAtLeastAsLargeAsRequestedResults() {
        List<Integer> requestedLimits = new ArrayList<>();
        ChunkCandidateRetriever retriever = (query, limit) -> {
            requestedLimits.add(limit);
            return List.of();
        };

        service.retrieve("校园规则", 1, retriever, retriever);
        service.retrieve("校园规则", 21, retriever, retriever);

        // 小 K 有更深候选，大 K 不被默认预算反向压缩。
        assertThat(requestedLimits).containsExactly(20, 20, 21, 21);
    }

    @Test
    void shouldRejectInvalidBudgetsBeforeInvokingEitherPath() {
        ChunkCandidateRetriever unexpected = (query, limit) -> {
            throw new AssertionError("非法预算不应触发检索");
        };
        for (int[] budgets : List.of(
                new int[]{0, 1}, new int[]{-1, 1}, new int[]{1, 0},
                new int[]{1, -1}, new int[]{1, 2})) {
            assertThatThrownBy(() -> service.retrieve(
                    "校园规则", budgets[0], budgets[1], unexpected, unexpected))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.retrieve("校园规则", 0, unexpected, unexpected))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.retrieve("校园规则", -1, unexpected, unexpected))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPreserveSurvivingPathOrderAndFinalLimitForEitherUnavailablePath() {
        ChunkCandidateRetriever unavailable = (query, limit) -> {
            throw new RetrieverUnavailableException("测试路不可用");
        };
        RetrievalOutcome lexicalOnly = service.retrieve(
                "校园规则", 2, 1, fakeRetriever(C, B), unavailable);
        RetrievalOutcome vectorOnly = service.retrieve(
                "校园规则", 2, 1, unavailable, fakeRetriever(C, B));

        assertThat(idsOf(lexicalOnly)).containsExactly("chunk-c");
        assertThat(lexicalOnly.lexicalAvailable()).isTrue();
        assertThat(lexicalOnly.vectorAvailable()).isFalse();
        assertThat(idsOf(vectorOnly)).containsExactly("chunk-c");
        assertThat(vectorOnly.lexicalAvailable()).isFalse();
        assertThat(vectorOnly.vectorAvailable()).isTrue();
    }

    @Test
    void shouldReturnSharedCandidateOnceAfterFusion() {
        RetrievalOutcome outcome = service.retrieve(
                "校园规则", 3, 3, fakeRetriever(A, C), fakeRetriever(B, C));

        assertThat(idsOf(outcome)).containsExactly("chunk-c", "chunk-a", "chunk-b");
    }

    @Test
    void shouldKeepTieOrderWhenPathsAreSwapped() {
        RetrievalOutcome first = service.retrieve(
                "校园规则", 2, 2, fakeRetriever(A, B), fakeRetriever(B, A));
        RetrievalOutcome swapped = service.retrieve(
                "校园规则", 2, 2, fakeRetriever(B, A), fakeRetriever(A, B));

        assertThat(idsOf(first)).containsExactly("chunk-a", "chunk-b");
        assertThat(idsOf(swapped)).containsExactly("chunk-a", "chunk-b");
    }

    @Test
    void shouldFuseBothPathsAndMarkBothAvailable() {
        RetrievalOutcome outcome = service.retrieve(
                "公共选修课",
                10,
                fakeRetriever(A, B),
                fakeRetriever(B, C));

        assertThat(outcome.lexicalAvailable()).isTrue();
        assertThat(outcome.vectorAvailable()).isTrue();
        // B 两路互证，排在仅单路出现的 A、C 之前。
        assertThat(idsOf(outcome)).containsExactly("chunk-b", "chunk-a", "chunk-c");
    }

    @Test
    void shouldDegradeToSurvivingPathWhenOneUnavailable() {
        RetrievalOutcome outcome = service.retrieve(
                "公共选修课",
                10,
                (query, limit) -> {
                    throw new RetrieverUnavailableException("向量服务未配置");
                },
                fakeRetriever(B, C));

        assertThat(outcome.lexicalAvailable()).isFalse();
        assertThat(outcome.vectorAvailable()).isTrue();
        assertThat(idsOf(outcome)).containsExactly("chunk-b", "chunk-c");
    }

    @Test
    void shouldReturnEmptyOutcomeWhenBothPathsUnavailable() {
        RetrievalOutcome outcome = service.retrieve(
                "公共选修课",
                10,
                (query, limit) -> {
                    throw new RetrieverUnavailableException("词法不可用");
                },
                (query, limit) -> {
                    throw new RetrieverUnavailableException("向量不可用");
                });

        assertThat(outcome.chunks()).isEmpty();
        assertThat(outcome.lexicalAvailable()).isFalse();
        assertThat(outcome.vectorAvailable()).isFalse();
    }

    @Test
    void shouldRespectLimitAfterFusion() {
        RetrievalOutcome outcome = service.retrieve(
                "公共选修课", 2, fakeRetriever(A, B), fakeRetriever(B, C));

        assertThat(outcome.chunks()).hasSize(2);
    }

    @Test
    void shouldReturnEmptyWhenNoPathYieldsCandidate() {
        RetrievalOutcome outcome = service.retrieve(
                "无关问题", 10, fakeRetriever(), fakeRetriever());

        assertThat(outcome.chunks()).isEmpty();
        assertThat(outcome.lexicalAvailable()).isTrue();
        assertThat(outcome.vectorAvailable()).isTrue();
    }

    private static ChunkCandidateRetriever fakeRetriever(RetrievedChunk... chunks) {
        // 替身必须真的截断，否则融合前漏候选的问题会被测试掩盖。
        return (query, limit) -> List.of(chunks).stream().limit(limit).toList();
    }

    private static List<String> idsOf(RetrievalOutcome outcome) {
        return outcome.chunks().stream()
                .map(chunk -> LABELS.get(chunk.chunkId()))
                .toList();
    }

    private static RetrievedChunk chunk(String chunkId, String content) {
        return new RetrievedChunk(
                UUID.nameUUIDFromBytes(chunkId.getBytes()),
                content,
                "学生手册 2026 版",
                "第 1 页",
                1,
                "学生手册 2026 版",
                "f".repeat(64));
    }
}
