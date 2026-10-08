package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        return (query, limit) -> List.of(chunks);
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
