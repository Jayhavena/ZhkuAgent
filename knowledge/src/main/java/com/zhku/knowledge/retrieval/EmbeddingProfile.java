package com.zhku.knowledge.retrieval;

import java.util.Objects;

/**
 * embedding profile 的领域视图（不可变）。
 *
 * <p>profile 一旦创建，其 provider/model/dimensions 永不修改——维度或模型变更
 * 必须新建 profile。这条不变量保证同一 profile 下的全部向量处于同一向量空间，
 * "禁止混用维度"由此在结构上成立。</p>
 */
public record EmbeddingProfile(
        String profileId,
        String provider,
        String model,
        int dimensions,
        ProfileStatus status
) {

    public EmbeddingProfile {
        Objects.requireNonNull(profileId, "profile 标识不能为空");
        Objects.requireNonNull(provider, "provider 不能为空");
        Objects.requireNonNull(model, "模型不能为空");
        Objects.requireNonNull(status, "profile 状态不能为空");
        if (dimensions <= 0) {
            throw new IllegalArgumentException("向量维度必须为正数");
        }
    }

    EmbeddingProfile withStatus(ProfileStatus newStatus) {
        return new EmbeddingProfile(profileId, provider, model, dimensions, newStatus);
    }
}
