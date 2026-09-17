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
}
