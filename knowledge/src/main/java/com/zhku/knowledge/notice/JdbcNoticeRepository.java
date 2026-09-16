package com.zhku.knowledge.notice;

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
import java.util.UUID;

/** PostgreSQL 通知仓储，发布替代与新版本写入共用一个事务。 */
public final class JdbcNoticeRepository implements NoticeRepository {

    private final DataSource dataSource;

    public JdbcNoticeRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    /**
     * 将一个审核完成的当前通知发布到其学期和类型范围。
     *
     * <p>先锁定并替代旧通知，再写入新版；若写入失败，事务回滚，旧通知不会变成无依据状态。</p>
     */
    public void publishReplacingScope(PublishedNotice notice) {
        Objects.requireNonNull(notice, "通知不能为空");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                publishReplacingScope(connection, notice);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法发布当前通知", exception);
        }
    }

    /**
     * 复用资料发布器已经打开的事务写入通知。
     *
     * <p>调用方不得自行提交该连接；同一事务才能保证来源替代、通知替代和审核
     * 记录要么全部可见，要么全部回滚。</p>
     */
    public void publishReplacingScope(Connection connection, PublishedNotice notice)
            throws SQLException {
        Objects.requireNonNull(connection, "数据库连接不能为空");
        validatePublishedStudentVisibleNotice(notice);
        List<UUID> replacedIds = lockAndSupersedeCurrent(connection, notice);
        insertNotice(connection, notice);
        insertFacts(connection, notice);
        linkReplacedNotices(connection, replacedIds, notice.noticeId());
    }

    private static void validatePublishedStudentVisibleNotice(
            PublishedNotice notice
    ) {
        Objects.requireNonNull(notice, "通知不能为空");
        if (notice.status() != NoticeLifecycleStatus.PUBLISHED
                || !notice.studentVisible()
                || !notice.sharingRightsConfirmed()) {
            throw new IllegalArgumentException("发布通知必须已审核且学生可见");
        }
    }

    @Override
    public List<PublishedNotice> listNotices() {
        String sql = """
                SELECT notice.id, notice.source_id, source.title, source.source_version,
                       source.publication_scope, notice.academic_term, notice.notice_type,
                       notice.lifecycle_status, notice.valid_from, notice.valid_to,
                       notice.official_url, notice.evidence_channel, notice.issuing_unit,
                       notice.delivery_channel, notice.material_reference, notice.snapshot_storage_key,
                       notice.snapshot_checksum, notice.snapshot_captured_at,
                       notice.snapshot_media_type, notice.student_visible,
                       notice.sharing_rights_confirmed
                FROM official_notice notice
                JOIN knowledge_source source ON source.id = notice.source_id
                ORDER BY notice.created_at, notice.id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<PublishedNotice> notices = new ArrayList<>();
            while (result.next()) {
                UUID noticeId = result.getObject("id", UUID.class);
                notices.add(new PublishedNotice(
                        noticeId,
                        result.getObject("source_id", UUID.class),
                        result.getString("title"),
                        result.getString("source_version"),
                        result.getString("publication_scope"),
                        new AcademicTerm(result.getString("academic_term")),
                        NoticeType.valueOf(result.getString("notice_type")),
                        NoticeLifecycleStatus.valueOf(
                                result.getString("lifecycle_status")
                        ),
                        instantOf(result, "valid_from"),
                        instantOf(result, "valid_to"),
                        new OfficialWebSnapshot(
                                result.getString("official_url"),
                                result.getString("snapshot_storage_key"),
                                result.getString("snapshot_checksum"),
                                instantOf(result, "snapshot_captured_at"),
                                result.getString("snapshot_media_type"),
                                new NoticeEvidenceOrigin(
                                        NoticeEvidenceChannel.valueOf(result.getString("evidence_channel")),
                                        result.getString("official_url"), result.getString("issuing_unit"),
                                        result.getString("delivery_channel"), result.getString("material_reference")
                                )
                        ),
                        result.getBoolean("student_visible"),
                        result.getBoolean("sharing_rights_confirmed"),
                        factsFor(connection, noticeId)
                ));
            }
            return List.copyOf(notices);
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取通知资料", exception);
        }
    }

    private static List<UUID> lockAndSupersedeCurrent(
            Connection connection,
            PublishedNotice replacement
    ) throws SQLException {
        List<UUID> replacedIds = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id FROM official_notice
                WHERE academic_term = ? AND notice_type = ?
                  AND lifecycle_status = 'PUBLISHED'
                FOR UPDATE
                """)) {
            select.setString(1, replacement.academicTerm().value());
            select.setString(2, replacement.type().name());
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    replacedIds.add(result.getObject(1, UUID.class));
                }
            }
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE official_notice
                SET lifecycle_status = 'SUPERSEDED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)) {
            for (UUID replacedId : replacedIds) {
                update.setObject(1, replacedId);
                update.addBatch();
            }
            update.executeBatch();
        }
        return List.copyOf(replacedIds);
    }

    private static void insertNotice(Connection connection, PublishedNotice notice)
            throws SQLException {
        String sql = """
                INSERT INTO official_notice (
                    id, source_id, academic_term, notice_type, lifecycle_status,
                    valid_from, valid_to, official_url, evidence_channel, issuing_unit,
                    delivery_channel, material_reference, snapshot_storage_key,
                    snapshot_checksum, snapshot_captured_at, snapshot_media_type,
                    student_visible, sharing_rights_confirmed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, notice.noticeId());
            statement.setObject(2, notice.sourceId());
            statement.setString(3, notice.academicTerm().value());
            statement.setString(4, notice.type().name());
            statement.setString(5, notice.status().name());
            statement.setTimestamp(6, Timestamp.from(notice.validFrom()));
            statement.setTimestamp(7, Timestamp.from(notice.validTo()));
            statement.setString(8, notice.officialSnapshot().officialUrl());
            NoticeEvidenceOrigin origin = notice.officialSnapshot().origin();
            statement.setString(9, origin.channel().name());
            statement.setString(10, origin.issuingUnit());
            statement.setString(11, origin.deliveryChannel());
            statement.setString(12, origin.materialReference());
            statement.setString(13, notice.officialSnapshot().storageKey());
            statement.setString(14, notice.officialSnapshot().sha256());
            statement.setTimestamp(15, Timestamp.from(
                    notice.officialSnapshot().capturedAt()
            ));
            statement.setString(16, notice.officialSnapshot().mediaType());
            statement.setBoolean(17, notice.studentVisible());
            statement.setBoolean(18, notice.sharingRightsConfirmed());
            statement.executeUpdate();
        }
    }

    private static void insertFacts(Connection connection, PublishedNotice notice)
            throws SQLException {
        String sql = """
                INSERT INTO official_notice_fact (
                    id, notice_id, fact_type, starts_at, ends_at,
                    official_entry_url, starts_at_confirmed, ends_at_confirmed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (NoticeFact fact : notice.facts()) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, notice.noticeId());
                statement.setString(3, fact.type().name());
                statement.setTimestamp(4, Timestamp.from(fact.startsAt()));
                statement.setTimestamp(5, Timestamp.from(fact.endsAt()));
                statement.setString(6, fact.officialEntryUrl());
                statement.setBoolean(7, fact.startsAtConfirmed());
                statement.setBoolean(8, fact.endsAtConfirmed());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void linkReplacedNotices(
            Connection connection,
            List<UUID> replacedIds,
            UUID replacementNoticeId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE official_notice
                SET superseded_by_notice_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)) {
            for (UUID replacedId : replacedIds) {
                statement.setObject(1, replacementNoticeId);
                statement.setObject(2, replacedId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<NoticeFact> factsFor(
            Connection connection,
            UUID noticeId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT fact_type, starts_at, ends_at, official_entry_url,
                       starts_at_confirmed, ends_at_confirmed
                FROM official_notice_fact WHERE notice_id = ? ORDER BY created_at, id
                """)) {
            statement.setObject(1, noticeId);
            try (ResultSet result = statement.executeQuery()) {
                List<NoticeFact> facts = new ArrayList<>();
                while (result.next()) {
                    facts.add(new NoticeFact(
                            NoticeFactType.valueOf(result.getString("fact_type")),
                            instantOf(result, "starts_at"),
                            instantOf(result, "ends_at"),
                            result.getString("official_entry_url"),
                            result.getBoolean("starts_at_confirmed"),
                            result.getBoolean("ends_at_confirmed")
                    ));
                }
                return List.copyOf(facts);
            }
        }
    }

    private static Instant instantOf(ResultSet result, String column)
            throws SQLException {
        return result.getTimestamp(column).toInstant();
    }
}
