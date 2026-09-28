package com.zhku.knowledge.retrieval;

import java.util.Objects;
import java.util.UUID;

/**
 * 索引任务：把"通过资格过滤的在册片段"向量化并写入指定 profile 的索引。
 *
 * <p>三条硬约束：① 资格过滤与召回共用 {@link RetrievalEligibilityFilter}；
 * ② embedding 维度与 profile 声明不符时整轮失败并留痕，禁止混用维度；
 * ③ 任何失败都必须把运行标成 FAILED，不允许留下"看不见的半成品重建"。</p>
 */
public final class RetrievalIndexingService {

    private final RetrievalIndexRepository repository;
    private final EmbeddingClient embeddingClient;
    private final RetrievalEligibilityFilter eligibilityFilter;

    public RetrievalIndexingService(
            RetrievalIndexRepository repository,
            EmbeddingClient embeddingClient,
            RetrievalEligibilityFilter eligibilityFilter
    ) {
        this.repository = Objects.requireNonNull(repository, "索引仓储不能为空");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embedding 客户端不能为空");
        this.eligibilityFilter = Objects.requireNonNull(eligibilityFilter, "资格过滤器不能为空");
    }

    /**
     * 为指定 profile 执行一次全量索引。
     *
     * @param profileId 目标 profile（必须已在册）
     * @param chunkingVersion 切分版本；变更切分方式时必须递增，否则条目唯一约束会拒绝重写
     * @param requiredScope 限定知识范围；null 表示不限定
     */
    public IndexRunResult indexProfile(String profileId, String chunkingVersion, String requiredScope) {
        Objects.requireNonNull(profileId, "profile 标识不能为空");
        Objects.requireNonNull(chunkingVersion, "切分版本不能为空");
        if (embeddingClient.dimensions() <= 0) {
            throw new IllegalStateException("embedding 客户端维度非法：" + embeddingClient.dimensions());
        }

        UUID runId = repository.startRun(profileId, chunkingVersion);
        int indexed = 0;
        int candidates = 0;
        try {
            EmbeddingProfile profile = repository.findProfile(profileId)
                    .orElseThrow(() -> new IllegalStateException("profile 未在册：" + profileId));
            if (embeddingClient.dimensions() != profile.dimensions()) {
                // 禁止混用维度：同一 profile 下的向量必须同维，否则向量空间不可比。
                throw new IllegalStateException(
                        "embedding 维度与 profile 声明不符：profile " + profile.dimensions()
                                + "，客户端 " + embeddingClient.dimensions());
            }
            for (IndexableChunk chunk : repository.listChunksWithSourceState()) {
                if (!eligibilityFilter.evaluate(chunk.source(), requiredScope).eligible()) {
                    continue;
                }
                candidates++;
                float[] embedding = embeddingClient.embed(chunk.content());
                if (embedding.length != profile.dimensions()) {
                    throw new IllegalStateException(
                            "embedding 实际长度与 profile 声明不符：声明 " + profile.dimensions()
                                    + "，实际 " + embedding.length);
                }
                if (repository.appendEntry(runId, profileId, chunk, chunkingVersion, embedding)) {
                    indexed++;
                }
            }
        } catch (RuntimeException exception) {
            repository.failRun(runId);
            throw exception;
        }
        repository.completeRun(runId, indexed);
        return new IndexRunResult(runId, candidates, indexed);
    }
}
