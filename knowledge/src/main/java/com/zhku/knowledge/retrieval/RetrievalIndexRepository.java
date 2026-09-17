package com.zhku.knowledge.retrieval;

import java.util.List;
import java.util.UUID;

/**
 * 检索索引仓储：索引运行留痕 + 版本化条目写入 + 在册来源读取。
 *
 * <p>实现方必须保证"运行状态与条目写入"的可见性一致：一次失败的重建
 * 绝不能留下部分条目被检索到。</p>
 */
public interface RetrievalIndexRepository {

    /** 登记 embedding profile（幂等：已存在则忽略）。 */
    void registerProfile(EmbeddingProfile profile);

    /** 读取 profile；用于索引前校验 embedding 维度与 profile 声明一致。 */
    java.util.Optional<EmbeddingProfile> findProfile(String profileId);

    /** 开启一次索引运行，返回运行标识（状态 RUNNING）。 */
    UUID startRun(String profileId, String chunkingVersion);

    /** 追写一条索引条目。 */
    void appendEntry(UUID runId, String profileId, IndexableChunk chunk, String chunkingVersion, float[] embedding);

    /** 标记运行成功完成。 */
    void completeRun(UUID runId, int indexedEntries);

    /** 标记运行失败（留痕：失败的重建必须可审计）。 */
    void failRun(UUID runId);

    /**
     * 读取在册来源的全部片段及其资格状态快照。
     *
     * <p>返回未过滤的原始行：资格判定由 {@link RetrievalEligibilityFilter} 统一执行，
     * 仓储不重复实现一套口径。</p>
     */
    List<IndexableChunk> listChunksWithSourceState();
}
