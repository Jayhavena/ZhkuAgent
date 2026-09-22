package com.zhku.knowledge.serviceguide;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * 办事清单的发布器（写侧）：按 {@code (guide_identifier, guide_version)} 幂等落库。
 *
 * <p><b>为什么删除后重插子表</b>：清单是"一次审核、整体替换"的产物，逐条 diff 只会让
 * 版本间的残留条目难以发现（例如上一版的多余步骤被悄悄留下）。整体替换 + 单事务提交让
 * "库里就是这一版审核内容"成为硬保证；重跑同一份 JSON 结果完全一致（可重复执行）。</p>
 *
 * <p><b>为什么不在这里做版本归档</b>：清单版本的启用/停用由 `valid_until` 与发布动作表达，
 * 归档逻辑属发布策略，尚未引入——此处只保证"写入的是审核内容"。</p>
 */
public final class JdbcServiceGuidePublisher {

    private final DataSource dataSource;

    public JdbcServiceGuidePublisher(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    /** 发布（或重发）一份清单：单事务内 upsert 主表 + 整体替换子表。 */
    public void publish(ServiceGuide guide) {
        Objects.requireNonNull(guide, "清单不能为空");
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                UUID guideId = upsertGuide(connection, guide);
                replaceChildren(connection, guideId, guide);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法发布办事清单", exception);
        }
    }

    private UUID upsertGuide(Connection connection, ServiceGuide guide) throws SQLException {
        String sql = """
                INSERT INTO service_guide (id, guide_identifier, guide_version, title,
                    applicable_scope, source_rule_reference, source_notice_reference,
                    review_notice, student_visible, published_at, valid_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (guide_identifier, guide_version) DO UPDATE SET
                    title = EXCLUDED.title,
                    applicable_scope = EXCLUDED.applicable_scope,
                    source_rule_reference = EXCLUDED.source_rule_reference,
                    source_notice_reference = EXCLUDED.source_notice_reference,
                    review_notice = EXCLUDED.review_notice,
                    student_visible = EXCLUDED.student_visible,
                    published_at = EXCLUDED.published_at,
                    valid_until = EXCLUDED.valid_until,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, guide.guideIdentifier());
            statement.setString(3, guide.guideVersion());
            statement.setString(4, guide.title());
            statement.setString(5, guide.applicableScope());
            statement.setString(6, guide.sourceRuleReference());
            statement.setString(7, guide.sourceNoticeReference());
            statement.setString(8, guide.reviewNotice());
            statement.setBoolean(9, guide.studentVisible());
            statement.setTimestamp(10, java.sql.Timestamp.from(guide.publishedAt()));
            statement.setTimestamp(11, guide.validUntil() == null
                    ? null : java.sql.Timestamp.from(guide.validUntil()));
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("清单写入未返回主键");
                }
                return result.getObject("id", UUID.class);
            }
        }
    }

    private void replaceChildren(Connection connection, UUID guideId, ServiceGuide guide)
            throws SQLException {
        for (String table : new String[]{"service_guide_step", "service_guide_material",
                "service_guide_channel", "service_guide_risk_notice"}) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE guide_id = ?")) {
                statement.setObject(1, guideId);
                statement.executeUpdate();
            }
        }
        for (GuideStep step : guide.steps()) {
            execute(connection, """
                    INSERT INTO service_guide_step (id, guide_id, step_order, instruction,
                        requires_identity_check) VALUES (?, ?, ?, ?, ?)
                    """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guideId);
                statement.setInt(3, step.stepOrder());
                statement.setString(4, step.instruction());
                statement.setBoolean(5, step.requiresIdentityCheck());
            });
        }
        for (ServiceGuide.Material material : guide.materials()) {
            execute(connection, """
                    INSERT INTO service_guide_material (id, guide_id, material_order,
                        material_name, note, required) VALUES (?, ?, ?, ?, ?, ?)
                    """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guideId);
                statement.setInt(3, material.materialOrder());
                statement.setString(4, material.materialName());
                statement.setString(5, material.note());
                statement.setBoolean(6, material.required());
            });
        }
        for (OfficialChannel channel : guide.channels()) {
            execute(connection, """
                    INSERT INTO service_guide_channel (id, guide_id, channel_order, channel_type,
                        channel_name, channel_value, source_locator, verified_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guideId);
                statement.setInt(3, channel.channelOrder());
                statement.setString(4, channel.channelType().name());
                statement.setString(5, channel.channelName());
                statement.setString(6, channel.channelValue());
                statement.setString(7, channel.sourceLocator());
                statement.setTimestamp(8, java.sql.Timestamp.from(channel.verifiedAt()));
            });
        }
        for (ServiceGuide.RiskNotice notice : guide.riskNotices()) {
            execute(connection, """
                    INSERT INTO service_guide_risk_notice (id, guide_id, notice_order, notice_text)
                    VALUES (?, ?, ?, ?)
                    """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guideId);
                statement.setInt(3, notice.noticeOrder());
                statement.setString(4, notice.noticeText());
            });
        }
    }

    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private void execute(Connection connection, String sql, StatementBinder binder)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        }
    }
}
