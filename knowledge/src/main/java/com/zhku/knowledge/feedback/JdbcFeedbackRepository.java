package com.zhku.knowledge.feedback;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** PostgreSQL 的匿名反馈仓储；所有查询都避开向上层泄露逐条事件。 */
public final class JdbcFeedbackRepository implements FeedbackRepository {

    private final DataSource dataSource;

    public JdbcFeedbackRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    @Override
    public boolean saveIfAbsent(AnonymousFeedbackEvent event) {
        String sql = """
                INSERT INTO anonymous_feedback_event (
                    guide_identifier, guide_version, feedback_reason, anonymous_session_hash,
                    dedupe_key, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (dedupe_key) DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.guideIdentifier());
            statement.setString(2, event.guideVersion());
            statement.setString(3, event.reason().name());
            statement.setString(4, event.anonymousSessionHash());
            statement.setString(5, event.dedupeKey());
            statement.setTimestamp(6, Timestamp.from(event.createdAt()));
            statement.setTimestamp(7, Timestamp.from(event.expiresAt()));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("保存匿名反馈失败", exception);
        }
    }

    @Override
    public List<AnonymousFeedbackEvent> findExpiredAtOrBefore(Instant expiresAt) {
        String sql = """
                SELECT guide_identifier, guide_version, feedback_reason, anonymous_session_hash,
                       dedupe_key, created_at, expires_at
                FROM anonymous_feedback_event
                WHERE expires_at <= ?
                ORDER BY expires_at, dedupe_key
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(expiresAt));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AnonymousFeedbackEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(new AnonymousFeedbackEvent(
                            resultSet.getString("guide_identifier"),
                            resultSet.getString("guide_version"),
                            FeedbackReason.valueOf(resultSet.getString("feedback_reason")),
                            resultSet.getString("anonymous_session_hash"),
                            resultSet.getString("dedupe_key"),
                            resultSet.getTimestamp("created_at").toInstant(),
                            resultSet.getTimestamp("expires_at").toInstant()
                    ));
                }
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取到期匿名反馈失败", exception);
        }
    }

    @Override
    public void replaceExpiredWithAggregates(
            List<AnonymousFeedbackEvent> expiredEvents,
            List<FeedbackAggregate> aggregates
    ) {
        if (expiredEvents.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                saveAggregates(connection, aggregates);
                deleteExpiredEvents(connection, expiredEvents);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new IllegalStateException("聚合并清理匿名反馈失败", exception);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("聚合并清理匿名反馈失败", exception);
        }
    }

    @Override
    public List<FeedbackAggregate> listAggregates() {
        String sql = """
                SELECT guide_identifier, guide_version, feedback_reason, feedback_date, feedback_count
                FROM anonymous_feedback_aggregate
                ORDER BY feedback_date, guide_identifier, guide_version, feedback_reason
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<FeedbackAggregate> aggregates = new ArrayList<>();
            while (resultSet.next()) {
                aggregates.add(new FeedbackAggregate(
                        resultSet.getString("guide_identifier"),
                        resultSet.getString("guide_version"),
                        FeedbackReason.valueOf(resultSet.getString("feedback_reason")),
                        resultSet.getObject("feedback_date", LocalDate.class),
                        resultSet.getLong("feedback_count")
                ));
            }
            return List.copyOf(aggregates);
        } catch (SQLException exception) {
            throw new IllegalStateException("读取匿名反馈聚合失败", exception);
        }
    }

    private static void saveAggregates(
            Connection connection,
            List<FeedbackAggregate> aggregates
    ) throws SQLException {
        String sql = """
                INSERT INTO anonymous_feedback_aggregate (
                    guide_identifier, guide_version, feedback_reason, feedback_date, feedback_count
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (guide_identifier, guide_version, feedback_reason, feedback_date)
                DO UPDATE SET feedback_count = anonymous_feedback_aggregate.feedback_count
                        + EXCLUDED.feedback_count
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (FeedbackAggregate aggregate : aggregates) {
                statement.setString(1, aggregate.guideIdentifier());
                statement.setString(2, aggregate.guideVersion());
                statement.setString(3, aggregate.reason().name());
                statement.setObject(4, aggregate.feedbackDate());
                statement.setLong(5, aggregate.count());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void deleteExpiredEvents(
            Connection connection,
            List<AnonymousFeedbackEvent> expiredEvents
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM anonymous_feedback_event WHERE dedupe_key = ?"
        )) {
            for (AnonymousFeedbackEvent event : expiredEvents) {
                statement.setString(1, event.dedupeKey());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
