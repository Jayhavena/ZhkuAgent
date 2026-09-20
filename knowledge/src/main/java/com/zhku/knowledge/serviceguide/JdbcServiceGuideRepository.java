package com.zhku.knowledge.serviceguide;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 办事清单仓储的 JDBC 实现（读侧）。
 *
 * <p><b>为什么只查"已发布且学生可见"</b>：与 {@link ServiceGuideRepository} 端口语义一致——
 * 内部参考、草稿、资格待确认的版本在这里就收敛为"查不到"，由解析器统一转人工，
 * 避免内部状态以任何形式进入学生可见的回答。</p>
 */
public final class JdbcServiceGuideRepository implements ServiceGuideRepository {

    private final DataSource dataSource;

    public JdbcServiceGuideRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    @Override
    public Optional<ServiceGuide> findPublished(String guideIdentifier, String guideVersion) {
        try (Connection connection = dataSource.getConnection()) {
            Optional<GuidedRow> guide = findGuide(connection, guideIdentifier, guideVersion);
            if (guide.isEmpty()) {
                return Optional.empty();
            }
            GuidedRow row = guide.get();
            return Optional.of(new ServiceGuide(
                    row.identifier(),
                    row.version(),
                    row.title(),
                    row.applicableScope(),
                    findSteps(connection, row.id()),
                    findMaterials(connection, row.id()),
                    findChannels(connection, row.id()),
                    findRiskNotices(connection, row.id()),
                    row.sourceRuleReference(),
                    row.sourceNoticeReference(),
                    row.reviewNotice(),
                    row.studentVisible(),
                    row.publishedAt(),
                    row.validUntil()));
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取办事清单", exception);
        }
    }

    private Optional<GuidedRow> findGuide(
            Connection connection, String guideIdentifier, String guideVersion)
            throws SQLException {
        String sql = """
                SELECT id, title, applicable_scope, source_rule_reference, source_notice_reference,
                       review_notice, student_visible, published_at, valid_until
                FROM service_guide
                WHERE guide_identifier = ? AND guide_version = ?
                  AND student_visible AND published_at IS NOT NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guideIdentifier);
            statement.setString(2, guideVersion);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GuidedRow(
                        result.getObject("id", UUID.class),
                        guideIdentifier,
                        guideVersion,
                        result.getString("title"),
                        result.getString("applicable_scope"),
                        result.getString("source_rule_reference"),
                        result.getString("source_notice_reference"),
                        result.getString("review_notice"),
                        result.getBoolean("student_visible"),
                        toInstant(result.getTimestamp("published_at")),
                        toInstant(result.getTimestamp("valid_until"))));
            }
        }
    }

    private List<GuideStep> findSteps(Connection connection, UUID guideId) throws SQLException {
        String sql = """
                SELECT step_order, instruction, requires_identity_check
                FROM service_guide_step WHERE guide_id = ? ORDER BY step_order
                """;
        List<GuideStep> steps = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guideId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    steps.add(new GuideStep(
                            result.getInt("step_order"),
                            result.getString("instruction"),
                            result.getBoolean("requires_identity_check")));
                }
            }
        }
        return List.copyOf(steps);
    }

    private List<ServiceGuide.Material> findMaterials(Connection connection, UUID guideId)
            throws SQLException {
        String sql = """
                SELECT material_order, material_name, note, required
                FROM service_guide_material WHERE guide_id = ? ORDER BY material_order
                """;
        List<ServiceGuide.Material> materials = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guideId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    materials.add(new ServiceGuide.Material(
                            result.getInt("material_order"),
                            result.getString("material_name"),
                            result.getString("note"),
                            result.getBoolean("required")));
                }
            }
        }
        return List.copyOf(materials);
    }

    private List<OfficialChannel> findChannels(Connection connection, UUID guideId)
            throws SQLException {
        String sql = """
                SELECT channel_order, channel_type, channel_name, channel_value,
                       source_locator, verified_at
                FROM service_guide_channel WHERE guide_id = ? ORDER BY channel_order
                """;
        List<OfficialChannel> channels = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guideId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    channels.add(new OfficialChannel(
                            result.getInt("channel_order"),
                            OfficialChannel.ChannelType.valueOf(result.getString("channel_type")),
                            result.getString("channel_name"),
                            result.getString("channel_value"),
                            result.getString("source_locator"),
                            toInstant(result.getTimestamp("verified_at"))));
                }
            }
        }
        return List.copyOf(channels);
    }

    private List<ServiceGuide.RiskNotice> findRiskNotices(Connection connection, UUID guideId)
            throws SQLException {
        String sql = """
                SELECT notice_order, notice_text
                FROM service_guide_risk_notice WHERE guide_id = ? ORDER BY notice_order
                """;
        List<ServiceGuide.RiskNotice> notices = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guideId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    notices.add(new ServiceGuide.RiskNotice(
                            result.getInt("notice_order"),
                            result.getString("notice_text")));
                }
            }
        }
        return List.copyOf(notices);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record GuidedRow(
            UUID id,
            String identifier,
            String version,
            String title,
            String applicableScope,
            String sourceRuleReference,
            String sourceNoticeReference,
            String reviewNotice,
            boolean studentVisible,
            Instant publishedAt,
            Instant validUntil) {
    }
}
