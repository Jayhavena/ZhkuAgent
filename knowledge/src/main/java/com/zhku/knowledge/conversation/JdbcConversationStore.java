package com.zhku.knowledge.conversation;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL 会话存储，使用条件更新守住 revision 与取消终态。 */
public final class JdbcConversationStore implements ConversationStore {

    private final DataSource dataSource;

    public JdbcConversationStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    @Override
    public StoreResult create(ConversationState initialState) {
        Objects.requireNonNull(initialState, "初始会话不能为空");
        try (Connection connection = dataSource.getConnection()) {
            if (insert(connection, initialState) == 1) {
                return new StoreResult(StoreResult.Outcome.CREATED, initialState);
            }
            return new StoreResult(
                    StoreResult.Outcome.ALREADY_EXISTS,
                    requireState(connection, initialState.sessionId())
            );
        } catch (SQLException exception) {
            throw databaseFailure("创建会话", exception);
        }
    }

    @Override
    public StoreResult find(UUID sessionId, Clock clock) {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        Objects.requireNonNull(clock, "时钟不能为空");
        try (Connection connection = dataSource.getConnection()) {
            ConversationState state = findState(connection, sessionId);
            return visibleResult(state, clock);
        } catch (SQLException exception) {
            throw databaseFailure("读取会话", exception);
        }
    }

    @Override
    public StoreResult savePending(
            UUID sessionId,
            long expectedRevision,
            PendingIntent pendingIntent,
            PendingField pendingField,
            Instant nextExpiresAt,
            Clock clock
    ) {
        Objects.requireNonNull(pendingIntent, "待补意图不能为空");
        Objects.requireNonNull(pendingField, "待补字段不能为空");
        Objects.requireNonNull(nextExpiresAt, "过期时间不能为空");
        validateWriteArguments(sessionId, expectedRevision, clock);
        return updateActive(
                sessionId,
                expectedRevision,
                clock,
                """
                        UPDATE conversation_session
                        SET pending_intent = ?, pending_field = ?, expires_at = ?,
                            revision = revision + 1
                        WHERE session_id = ? AND status = 'ACTIVE'
                          AND revision = ? AND expires_at > ?
                        RETURNING session_id, status, pending_intent, pending_field, expires_at, revision
                        """,
                statement -> {
                    statement.setString(1, pendingIntent.name());
                    statement.setString(2, pendingField.name());
                    statement.setTimestamp(3, Timestamp.from(nextExpiresAt));
                    statement.setObject(4, sessionId);
                    statement.setLong(5, expectedRevision);
                    statement.setTimestamp(6, Timestamp.from(clock.instant()));
                }
        );
    }

    @Override
    public StoreResult clearPending(
            UUID sessionId,
            long expectedRevision,
            Instant nextExpiresAt,
            Clock clock
    ) {
        Objects.requireNonNull(nextExpiresAt, "过期时间不能为空");
        validateWriteArguments(sessionId, expectedRevision, clock);
        StoreResult result = updateActive(
                sessionId,
                expectedRevision,
                clock,
                """
                        UPDATE conversation_session
                        SET pending_intent = NULL, pending_field = NULL, expires_at = ?,
                            revision = revision + 1
                        WHERE session_id = ? AND status = 'ACTIVE' AND pending_intent IS NOT NULL
                          AND revision = ? AND expires_at > ?
                        RETURNING session_id, status, pending_intent, pending_field, expires_at, revision
                        """,
                statement -> {
                    statement.setTimestamp(1, Timestamp.from(nextExpiresAt));
                    statement.setObject(2, sessionId);
                    statement.setLong(3, expectedRevision);
                    statement.setTimestamp(4, Timestamp.from(clock.instant()));
                }
        );
        if (result.outcome() == StoreResult.Outcome.STALE_WRITE
                && result.state().revision() == expectedRevision
                && result.state().pendingIntent() == null) {
            throw new IllegalStateException("当前会话没有待补信息");
        }
        return result;
    }

    @Override
    public StoreResult cancel(UUID sessionId, long expectedRevision, Clock clock) {
        validateWriteArguments(sessionId, expectedRevision, clock);
        return updateActive(
                sessionId,
                expectedRevision,
                clock,
                """
                        UPDATE conversation_session
                        SET status = 'CANCELLED', pending_intent = NULL, pending_field = NULL,
                            revision = revision + 1
                        WHERE session_id = ? AND status = 'ACTIVE'
                          AND revision = ? AND expires_at > ?
                        RETURNING session_id, status, pending_intent, pending_field, expires_at, revision
                        """,
                statement -> {
                    statement.setObject(1, sessionId);
                    statement.setLong(2, expectedRevision);
                    statement.setTimestamp(3, Timestamp.from(clock.instant()));
                }
        );
    }

    @Override
    public int clearExpired(Clock clock) {
        Objects.requireNonNull(clock, "时钟不能为空");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM conversation_session WHERE expires_at <= ?"
             )) {
            statement.setTimestamp(1, Timestamp.from(clock.instant()));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw databaseFailure("清理过期会话", exception);
        }
    }

    private StoreResult updateActive(
            UUID sessionId,
            long expectedRevision,
            Clock clock,
            String sql,
            StatementBinder binder
    ) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            // 状态、版本和到期时间必须由同一条 SQL 校验，否则取消与晚到写入间会留下会话复活窗口。
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new StoreResult(StoreResult.Outcome.UPDATED, stateOf(result));
                }
            }
            return failedWriteResult(connection, sessionId, clock);
        } catch (SQLException exception) {
            throw databaseFailure("更新会话", exception);
        }
    }

    private int insert(Connection connection, ConversationState state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO conversation_session (
                    session_id, status, pending_intent, pending_field, expires_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id) DO NOTHING
                """)) {
            statement.setObject(1, state.sessionId());
            statement.setString(2, state.status().name());
            statement.setString(3, enumName(state.pendingIntent()));
            statement.setString(4, enumName(state.pendingField()));
            statement.setTimestamp(5, Timestamp.from(state.expiresAt()));
            statement.setLong(6, state.revision());
            return statement.executeUpdate();
        }
    }

    private StoreResult failedWriteResult(Connection connection, UUID sessionId, Clock clock)
            throws SQLException {
        ConversationState state = findState(connection, sessionId);
        if (state == null) {
            return new StoreResult(StoreResult.Outcome.NOT_FOUND, null);
        }
        if (state.isExpired(clock)) {
            return new StoreResult(StoreResult.Outcome.EXPIRED, state);
        }
        if (state.status() == ConversationStatus.CANCELLED) {
            return new StoreResult(StoreResult.Outcome.CANCELLED, state);
        }
        return new StoreResult(StoreResult.Outcome.STALE_WRITE, state);
    }

    private ConversationState requireState(Connection connection, UUID sessionId) throws SQLException {
        ConversationState state = findState(connection, sessionId);
        if (state == null) {
            throw new IllegalStateException("创建冲突后未找到已有会话");
        }
        return state;
    }

    private ConversationState findState(Connection connection, UUID sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT session_id, status, pending_intent, pending_field, expires_at, revision
                FROM conversation_session WHERE session_id = ?
                """)) {
            statement.setObject(1, sessionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? stateOf(result) : null;
            }
        }
    }

    private static StoreResult visibleResult(ConversationState state, Clock clock) {
        if (state == null) {
            return new StoreResult(StoreResult.Outcome.NOT_FOUND, null);
        }
        if (state.isExpired(clock)) {
            return new StoreResult(StoreResult.Outcome.EXPIRED, state);
        }
        if (state.status() == ConversationStatus.CANCELLED) {
            return new StoreResult(StoreResult.Outcome.CANCELLED, state);
        }
        return new StoreResult(StoreResult.Outcome.FOUND, state);
    }

    private static ConversationState stateOf(ResultSet result) throws SQLException {
        String pendingIntent = result.getString("pending_intent");
        String pendingField = result.getString("pending_field");
        return new ConversationState(
                result.getObject("session_id", UUID.class),
                ConversationStatus.valueOf(result.getString("status")),
                pendingIntent == null ? null : PendingIntent.valueOf(pendingIntent),
                pendingField == null ? null : PendingField.valueOf(pendingField),
                result.getTimestamp("expires_at").toInstant(),
                result.getLong("revision")
        );
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static void validateWriteArguments(UUID sessionId, long expectedRevision, Clock clock) {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        Objects.requireNonNull(clock, "时钟不能为空");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("期望版本不能为负数");
        }
    }

    private static IllegalStateException databaseFailure(String action, SQLException exception) {
        return new IllegalStateException("无法" + action, exception);
    }

    @FunctionalInterface
    private interface StatementBinder {

        void bind(PreparedStatement statement) throws SQLException;
    }
}
