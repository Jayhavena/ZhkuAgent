package com.zhku.knowledge.retrieval;

import java.util.Objects;

/**
 * embedding profile 原子切换器。
 *
 * <p>embedding 变更的唯一合法路径：新 profile 完成索引重建与评测后，
 * 由本切换器"退旧启新"——旧 ACTIVE 退役但保留（回滚窗口），新 profile 上线。
 * 两次写操作由存储实现保证同事务（见 {@link EmbeddingProfileStore#switchActive}）；
 * 切换器本身只负责状态机约束。</p>
 */
public final class EmbeddingProfileSwitcher {

    private final EmbeddingProfileStore store;

    public EmbeddingProfileSwitcher(EmbeddingProfileStore store) {
        this.store = Objects.requireNonNull(store, "profile 存储不能为空");
    }

    /**
     * 激活一个已完成重建与评测的 BUILDING profile，同时退役当前 ACTIVE。
     */
    public void activate(String profileId) {
        Objects.requireNonNull(profileId, "profile 标识不能为空");
        EmbeddingProfile candidate = store.find(profileId)
                .orElseThrow(() -> new IllegalStateException("profile 不存在：" + profileId));
        if (candidate.status() != ProfileStatus.BUILDING) {
            // 只允许 BUILDING → ACTIVE：未走完整"重建→评测"流程的 profile 不得上线。
            throw new IllegalStateException(
                    "只有 BUILDING 状态的 profile 可以激活，当前状态：" + candidate.status());
        }
        store.findActive().ifPresentOrElse(
                // 有在役 profile → 一次事务内退旧启新，避免出现"两个 ACTIVE"或"零 ACTIVE"的中间态。
                active -> store.switchActive(active.profileId(), profileId),
                // 首个 profile：库中本来没有 ACTIVE，直接启用。
                () -> store.save(new EmbeddingProfile(candidate.profileId(), candidate.provider(),
                        candidate.model(), candidate.dimensions(), ProfileStatus.ACTIVE)));
    }

    /**
     * 在回滚窗口内回退到指定的 RETIRED profile。
     */
    public void rollback(String profileId) {
        Objects.requireNonNull(profileId, "profile 标识不能为空");
        EmbeddingProfile target = store.find(profileId)
                .orElseThrow(() -> new IllegalStateException("profile 不存在：" + profileId));
        if (target.status() != ProfileStatus.RETIRED) {
            // 只允许回滚到 RETIRED：BUILDING 从未上线、ACTIVE 无需回滚。
            throw new IllegalStateException(
                    "只能回滚到 RETIRED 状态的 profile，当前状态：" + target.status());
        }
        store.findActive().ifPresentOrElse(
                active -> store.switchActive(active.profileId(), profileId),
                () -> store.save(new EmbeddingProfile(target.profileId(), target.provider(),
                        target.model(), target.dimensions(), ProfileStatus.ACTIVE)));
    }
}
