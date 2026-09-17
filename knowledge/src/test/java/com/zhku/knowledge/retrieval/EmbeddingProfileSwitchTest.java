package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * embedding profile 原子切换与回滚测试。
 *
 * <p>embedding 变更只允许"新 profile → 重建 → 评测 → 原子切换 → 保留旧索引回滚窗口"：
 * 禁止修改在役 profile 的模型或维度（混用维度会让向量空间失去可比性），
 * 切换后旧 profile 保留 RETIRED 状态直至回滚窗口结束。</p>
 */
class EmbeddingProfileSwitchTest {

    private final InMemoryProfileStore store = new InMemoryProfileStore();
    private final EmbeddingProfileSwitcher switcher = new EmbeddingProfileSwitcher(store);

    @Test
    void shouldActivateFirstProfile() {
        EmbeddingProfile candidate = building("p1", "model-a", 1024);
        store.save(candidate);

        switcher.activate("p1");

        assertThat(store.find("p1")).map(EmbeddingProfile::status)
                .contains(ProfileStatus.ACTIVE);
    }

    @Test
    void shouldAtomicallyRetirePreviousActiveOnSwitch() {
        store.save(active("old", "model-a", 1024));
        store.save(building("new", "model-b", 1536));

        switcher.activate("new");

        // 维度变化只能通过新 profile 生效：旧 profile 整体退役，两个 profile 不同时 ACTIVE。
        assertThat(store.find("old")).map(EmbeddingProfile::status)
                .contains(ProfileStatus.RETIRED);
        assertThat(store.find("new")).map(EmbeddingProfile::status)
                .contains(ProfileStatus.ACTIVE);
        assertThat(store.activeCount()).isOne();
    }

    @Test
    void shouldRollbackToRetiredProfileWithinWindow() {
        store.save(active("old", "model-a", 1024));
        store.save(building("new", "model-b", 1536));
        switcher.activate("new");

        switcher.rollback("old");

        assertThat(store.find("old")).map(EmbeddingProfile::status)
                .contains(ProfileStatus.ACTIVE);
        assertThat(store.find("new")).map(EmbeddingProfile::status)
                .contains(ProfileStatus.RETIRED);
        assertThat(store.activeCount()).isOne();
    }

    @Test
    void shouldRejectActivatingAlreadyActiveProfile() {
        store.save(active("p1", "model-a", 1024));

        assertThatIllegalStateException().isThrownBy(() -> switcher.activate("p1"));
    }

    @Test
    void shouldRejectRollbackToNonRetiredProfile() {
        store.save(active("p1", "model-a", 1024));
        store.save(building("p2", "model-b", 1536));

        // BUILDING 中的 profile 从未通过评测上线，不能成为回滚目标。
        assertThatIllegalStateException().isThrownBy(() -> switcher.rollback("p2"));
        assertThatIllegalStateException().isThrownBy(() -> switcher.rollback("missing"));
    }

    @Test
    void shouldRejectActivatingUnknownProfile() {
        assertThatIllegalStateException().isThrownBy(() -> switcher.activate("missing"));
    }

    private static EmbeddingProfile building(String id, String model, int dimensions) {
        return new EmbeddingProfile(id, "test-provider", model, dimensions, ProfileStatus.BUILDING);
    }

    private static EmbeddingProfile active(String id, String model, int dimensions) {
        return new EmbeddingProfile(id, "test-provider", model, dimensions, ProfileStatus.ACTIVE);
    }

    /** 测试用内存存储；生产实现为 JDBC，原子性由数据库事务保证。 */
    private static final class InMemoryProfileStore implements EmbeddingProfileStore {

        private final Map<String, EmbeddingProfile> profiles = new HashMap<>();

        @Override
        public Optional<EmbeddingProfile> find(String profileId) {
            return Optional.ofNullable(profiles.get(profileId));
        }

        @Override
        public Optional<EmbeddingProfile> findActive() {
            return profiles.values().stream()
                    .filter(p -> p.status() == ProfileStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public void save(EmbeddingProfile profile) {
            profiles.put(profile.profileId(), profile);
        }

        long activeCount() {
            return profiles.values().stream()
                    .filter(p -> p.status() == ProfileStatus.ACTIVE)
                    .count();
        }
    }
}
