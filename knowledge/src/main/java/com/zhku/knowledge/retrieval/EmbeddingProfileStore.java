package com.zhku.knowledge.retrieval;

import java.util.Optional;

/**
 * embedding profile 存储抽象。
 *
 * <p>生产实现为 JDBC（对应 V9 的 embedding_profile 表）；切换与回滚的
 * "退旧启新"必须在同一事务内完成，配合表上的部分唯一索引兜底并发切换。</p>
 */
public interface EmbeddingProfileStore {

    Optional<EmbeddingProfile> find(String profileId);

    Optional<EmbeddingProfile> findActive();

    void save(EmbeddingProfile profile);

    /**
     * 一次性完成"退旧 + 启新"。
     *
     * <p>为什么要单独给一个方法，而不是让调用方连写两次 {@link #save}：两次独立写入之间
     * 存在崩窗口——若先启新后退旧，会出现两个 ACTIVE（被唯一索引拒绝）；若先退旧再启新，
     * 中途失败就会留下"零 ACTIVE"的空档。生产实现必须在**同一事务**里做这两件事，
     * 所以原子性属于存储的职责，状态机校验仍留在 {@link EmbeddingProfileSwitcher}。</p>
     *
     * <p>默认实现按顺序两次写入，仅适用于内存实现与测试；依赖强原子性的生产实现应覆盖它。</p>
     */
    default void switchActive(String retiringProfileId, String activatingProfileId) {
        EmbeddingProfile retiring = find(retiringProfileId)
                .orElseThrow(() -> new IllegalStateException("待退役 profile 不存在：" + retiringProfileId));
        EmbeddingProfile activating = find(activatingProfileId)
                .orElseThrow(() -> new IllegalStateException("待激活 profile 不存在：" + activatingProfileId));
        save(new EmbeddingProfile(retiring.profileId(), retiring.provider(), retiring.model(),
                retiring.dimensions(), ProfileStatus.RETIRED));
        save(new EmbeddingProfile(activating.profileId(), activating.provider(), activating.model(),
                activating.dimensions(), ProfileStatus.ACTIVE));
    }
}
