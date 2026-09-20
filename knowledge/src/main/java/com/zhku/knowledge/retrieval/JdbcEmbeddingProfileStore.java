package com.zhku.knowledge.retrieval;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL embedding profile 存储（V9 的 `embedding_profile` 表）。
 *
 * <p>三条不变量在这里落地：</p>
 * <ol>
 *   <li><b>单一 ACTIVE</b>：由 `uq_embedding_profile_single_active` 部分唯一索引兜底，
 *       应用层顺序写错也只会"响亮失败"，不会静默双活；</li>
 *   <li><b>退旧启新同事务</b>：{@link #switchActive} 在一条连接里显式开启事务，
 *       任一步影响行数不为 1 就回滚——不会留下"零 ACTIVE"或"两个 ACTIVE"的中间态；</li>
 *   <li><b>profile 身份不可变</b>：provider/model/维度是同一向量空间的定义，
 *       对已存在 profile 的修改请求一律拒绝（变更必须新建 profile），
 *       否则旧向量与新查询向量会被放进不同空间比较。</li>
 * </ol>
 */
public final class JdbcEmbeddingProfileStore implements EmbeddingProfileStore {

    private static final String COLUMNS = "profile_id, provider, model, dimensions, status";

    private final DataSource dataSource;

    public JdbcEmbeddingProfileStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    @Override
    public Optional<EmbeddingProfile> find(String profileId) {
        Objects.requireNonNull(profileId, "profile 标识不能为空");
        return queryOne(
                "SELECT " + COLUMNS + " FROM embedding_profile WHERE profile_id = ?",
                statement -> statement.setString(1, profileId));
    }

    @Override
    public Optional<EmbeddingProfile> findActive() {
        return queryOne(
                "SELECT " + COLUMNS + " FROM embedding_profile WHERE status = 'ACTIVE'",
                statement -> {
                });
    }

    /**
     * 登记新 profile 或更新其状态。
     *
     * <p>已存在时**只允许状态变化**：provider/model/维度必须与已登记值完全一致，
     * 否则抛错而不是静默覆盖（静默覆盖会让该 profile 下已写入的向量与声明维度脱钩）。</p>
     */
    @Override
    public void save(EmbeddingProfile profile) {
        Objects.requireNonNull(profile, "profile 不能为空");
        find(profile.profileId()).ifPresent(existing -> {
            if (!existing.provider().equals(profile.provider())
                    || !existing.model().equals(profile.model())
                    || existing.dimensions() != profile.dimensions()) {
                throw new IllegalStateException(
                        "profile 的 provider/model/维度不可变更，请新建 profile："
                                + existing.profileId() + "（已登记 " + existing.provider() + "/"
                                + existing.model() + "/" + existing.dimensions() + "，请求 "
                                + profile.provider() + "/" + profile.model() + "/"
                                + profile.dimensions() + "）");
            }
        });
        String sql = """
                INSERT INTO embedding_profile (profile_id, provider, model, dimensions, status, activated_at)
                VALUES (?, ?, ?, ?, ?, CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP ELSE NULL END)
                ON CONFLICT (profile_id) DO UPDATE
                SET status = EXCLUDED.status,
                    activated_at = COALESCE(EXCLUDED.activated_at, embedding_profile.activated_at)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.profileId());
            statement.setString(2, profile.provider());
            statement.setString(3, profile.model());
            statement.setInt(4, profile.dimensions());
            statement.setString(5, profile.status().name());
            statement.setString(6, profile.status().name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            // 两个 ACTIVE 会被部分唯一索引拒绝——这正是期望的响亮失败。
            throw new IllegalStateException("无法写入 embedding profile：" + profile.profileId(), exception);
        }
    }

    @Override
    public void switchActive(String retiringProfileId, String activatingProfileId) {
        Objects.requireNonNull(retiringProfileId, "待退役 profile 不能为空");
        Objects.requireNonNull(activatingProfileId, "待激活 profile 不能为空");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int retired = update(connection,
                        "UPDATE embedding_profile SET status = 'RETIRED' "
                                + "WHERE profile_id = ? AND status = 'ACTIVE'",
                        retiringProfileId);
                if (retired != 1) {
                    throw new IllegalStateException(
                            "待退役 profile 不在 ACTIVE 状态：" + retiringProfileId);
                }
                // 允许 BUILDING（首次上线）或 RETIRED（回滚窗口内回退）；不允许已经是 ACTIVE。
                int activated = update(connection,
                        "UPDATE embedding_profile SET status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP "
                                + "WHERE profile_id = ? AND status <> 'ACTIVE'",
                        activatingProfileId);
                if (activated != 1) {
                    throw new IllegalStateException(
                            "待激活 profile 状态不合法或不存在：" + activatingProfileId);
                }
                connection.commit();
            } catch (RuntimeException | SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法完成 embedding profile 原子切换", exception);
        }
    }

    private static int update(Connection connection, String sql, String profileId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profileId);
            return statement.executeUpdate();
        }
    }

    private Optional<EmbeddingProfile> queryOne(String sql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new EmbeddingProfile(
                        result.getString("profile_id"),
                        result.getString("provider"),
                        result.getString("model"),
                        result.getInt("dimensions"),
                        ProfileStatus.valueOf(result.getString("status"))
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取 embedding profile", exception);
        }
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
