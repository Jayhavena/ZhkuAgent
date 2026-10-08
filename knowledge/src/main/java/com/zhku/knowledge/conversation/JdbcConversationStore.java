package com.zhku.knowledge.conversation;

import javax.sql.DataSource;
import com.zhku.knowledge.security.TrustedActor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL 会话存储，使用条件更新守住 revision 与取消终态。 */
public final class JdbcConversationStore implements ConversationStore {

    private final DataSource dataSource;
    private final ConversationJdbcAccess access;
    private final TrustedActor actor;
    private final Clock createClock;
    private final ConversationOperationBudget requestBudget;
    private static final Duration OWNED_TTL = Duration.ofMinutes(10);
    private static final String COLUMNS = "session_id, status, pending_intent, pending_field, expires_at, revision";
    private static final String OWNED_PERMISSION = """
            owner_user_id = ? AND EXISTS (
                SELECT 1 FROM preview_identity i WHERE i.user_id = ?
                  AND i.enabled AND i.role = 'USER' AND i.auth_revision = ?
            )
            """;

    public JdbcConversationStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
        access = null;
        actor = null;
        createClock = null;
        requestBudget = null;
    }

    public JdbcConversationStore(ConversationJdbcAccess access, TrustedActor actor, Clock createClock) {
        this(access, actor, createClock, null, false);
    }

    public JdbcConversationStore(ConversationJdbcAccess access, TrustedActor actor, Clock createClock,
            ConversationOperationBudget budget) {
        this(access, actor, createClock, Objects.requireNonNull(budget, "请求预算不能为空"), true);
    }

    private JdbcConversationStore(ConversationJdbcAccess access, TrustedActor actor, Clock createClock,
            ConversationOperationBudget budget, boolean sharedBudget) {
        dataSource = null;
        this.access = Objects.requireNonNull(access, "受控数据库资源不能为空");
        this.actor = Objects.requireNonNull(actor, "可信主体不能为空");
        this.createClock = Objects.requireNonNull(createClock, "创建时钟不能为空");
        // 消息入口传入同一budget；无budget重载仅为单次独立Store调用提供预算。
        requestBudget = sharedBudget ? budget : null;
    }

    @Override
    public StoreResult create(ConversationState initialState) {
        Objects.requireNonNull(initialState, "初始会话不能为空");
        if (access != null) return ownedCreate(initialState);
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
        if (access != null) return owned(ConversationJdbcAccess.WriteKind.NONE, transaction -> {
            requireCurrentUser(transaction);
            return visibleResult(ownedState(transaction, sessionId, false), clock);
        });
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
        if (access != null) return ownedUpdate(sessionId, expectedRevision, pendingIntent, pendingField, clock, Mutation.SAVE);
        return updateActive(
                sessionId,
                expectedRevision,
                clock,
                """
                        UPDATE conversation_session
                        SET pending_intent = ?, pending_field = ?, expires_at = ?,
                            revision = revision + 1
                        WHERE session_id = ? AND owner_user_id IS NULL AND status = 'ACTIVE'
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
        if (access != null) return ownedUpdate(sessionId, expectedRevision, null, null, clock, Mutation.CLEAR);
        StoreResult result = updateActive(
                sessionId,
                expectedRevision,
                clock,
                """
                        UPDATE conversation_session
                        SET pending_intent = NULL, pending_field = NULL, expires_at = ?,
                            revision = revision + 1
                        WHERE session_id = ? AND owner_user_id IS NULL AND status = 'ACTIVE' AND pending_intent IS NOT NULL
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
        if (access != null) return ownedUpdate(sessionId, expectedRevision, null, null, clock, Mutation.CANCEL);
        return updateActive(
                sessionId,
                expectedRevision,
                clock,
                """
                        UPDATE conversation_session
                        SET status = 'CANCELLED', pending_intent = NULL, pending_field = NULL,
                            revision = revision + 1
                        WHERE session_id = ? AND owner_user_id IS NULL AND status = 'ACTIVE'
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
        if (access != null) return ownedClearExpired(clock);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM conversation_session WHERE owner_user_id IS NULL AND expires_at <= ?"
             )) {
            statement.setTimestamp(1, Timestamp.from(clock.instant()));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw databaseFailure("清理过期会话", exception);
        }
    }

    private StoreResult ownedCreate(ConversationState initial) {
        if (initial.status() != ConversationStatus.ACTIVE || initial.revision() != 0 || initial.pendingIntent() != null)
            throw new IllegalArgumentException("受控创建只允许空的ACTIVE初始状态");
        return owned(ConversationJdbcAccess.WriteKind.CREATE, transaction -> {
            requireCurrentUser(transaction);
            // TTL只取服务端时钟，不能借Store输入或客户端字段扩展持久会话的寿命。
            ConversationState candidate = ConversationState.active(initial.sessionId(), createClock.instant().plus(OWNED_TTL));
            try (PreparedStatement statement = transaction.prepare("""
                    INSERT INTO conversation_session (
                        session_id, status, pending_intent, pending_field, expires_at, revision, owner_user_id
                    ) SELECT ?, ?, ?, ?, ?, ?, i.user_id FROM preview_identity i
                    WHERE i.user_id = ? AND i.enabled AND i.role = 'USER' AND i.auth_revision = ?
                    ON CONFLICT (session_id) DO NOTHING
                    RETURNING session_id, status, pending_intent, pending_field, expires_at, revision
                    """)) {
                statement.setObject(1, candidate.sessionId());
                statement.setString(2, candidate.status().name());
                statement.setString(3, null);
                statement.setString(4, null);
                statement.setTimestamp(5, Timestamp.from(candidate.expiresAt()));
                statement.setLong(6, candidate.revision());
                statement.setObject(7, actor.userId());
                statement.setLong(8, actor.authRevision());
                transaction.writeIssued();
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) return new StoreResult(StoreResult.Outcome.CREATED, stateOf(result));
                }
            }
            // 冲突可能是他人/无主行，诊断仍先过滤本人许可；不换UUID、不认领、不续期。
            ConversationState existing = ownedState(transaction, initial.sessionId(), false);
            if (existing != null && (existing.isExpired(createClock) || existing.status() != ConversationStatus.ACTIVE))
                existing = null;
            return new StoreResult(StoreResult.Outcome.ALREADY_EXISTS, existing);
        });
    }

    private enum Mutation { SAVE, CLEAR, CANCEL }

    private StoreResult ownedUpdate(UUID id, long expectedRevision, PendingIntent intent, PendingField field,
            Clock clock, Mutation mutation) {
        return owned(ConversationJdbcAccess.WriteKind.WRITE, transaction -> {
            requireCurrentUser(transaction);
            ConversationState locked = ownedState(transaction, id, true);
            // 锁等待可能跨过TTL，必须锁成功后重新取时间；身份锁固定在Session行锁之前。
            Instant now = clock.instant();
            StoreResult visible = visibleResult(locked, Clock.fixed(now, clock.getZone()));
            if (visible.outcome() != StoreResult.Outcome.FOUND) return visible;
            if (locked.revision() != expectedRevision || expectedRevision == Long.MAX_VALUE
                    || (mutation == Mutation.CLEAR && locked.pendingIntent() == null))
                return new StoreResult(StoreResult.Outcome.STALE_WRITE, locked);

            String assignments = switch (mutation) {
                case SAVE -> "pending_intent = ?, pending_field = ?, expires_at = ?, revision = revision + 1";
                case CLEAR -> "pending_intent = NULL, pending_field = NULL, expires_at = ?, revision = revision + 1";
                case CANCEL -> "status = 'CANCELLED', pending_intent = NULL, pending_field = NULL, revision = revision + 1";
            };
            String sql = "UPDATE conversation_session SET " + assignments + " WHERE session_id = ? AND "
                    + OWNED_PERMISSION + " AND status = 'ACTIVE' AND revision = ? AND expires_at > ?"
                    + " AND revision < 9223372036854775807"
                    + (mutation == Mutation.CLEAR ? " AND pending_intent IS NOT NULL" : "")
                    + " RETURNING " + COLUMNS;
            try (PreparedStatement statement = transaction.prepare(sql)) {
                int parameter = 1;
                if (mutation == Mutation.SAVE) {
                    statement.setString(parameter++, intent.name());
                    statement.setString(parameter++, field.name());
                }
                // 原接口保留nextExpiresAt参数；owned路径固定由锁后服务端时钟滑动10分钟。
                if (mutation != Mutation.CANCEL) statement.setTimestamp(parameter++, Timestamp.from(now.plus(OWNED_TTL)));
                statement.setObject(parameter++, id);
                parameter = bindPermission(statement, parameter);
                statement.setLong(parameter++, expectedRevision);
                statement.setTimestamp(parameter, Timestamp.from(now));
                transaction.writeIssued();
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) return new StoreResult(mutation == Mutation.CANCEL
                            ? StoreResult.Outcome.CANCELLED : StoreResult.Outcome.UPDATED, stateOf(result));
                }
            }
            ConversationState current = ownedState(transaction, id, false);
            StoreResult diagnostic = visibleResult(current, clock);
            return diagnostic.outcome() == StoreResult.Outcome.FOUND
                    ? new StoreResult(StoreResult.Outcome.STALE_WRITE, current) : diagnostic;
        });
    }

    private int ownedClearExpired(Clock clock) {
        return owned(ConversationJdbcAccess.WriteKind.WRITE, transaction -> {
            requireCurrentUser(transaction);
            List<ConversationState> candidates = new ArrayList<>();
            try (PreparedStatement statement = transaction.prepare("SELECT " + COLUMNS
                    + " FROM conversation_session WHERE " + OWNED_PERMISSION
                    + " AND expires_at <= ? ORDER BY session_id LIMIT 100 FOR UPDATE SKIP LOCKED")) {
                int parameter = bindPermission(statement, 1);
                statement.setTimestamp(parameter, Timestamp.from(clock.instant()));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        if (candidates.size() == 100) throw new ConversationStoreFailure(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
                        candidates.add(stateOf(rows));
                    }
                }
            }
            if (candidates.isEmpty()) return 0;
            Instant afterLockNow = clock.instant();
            // VALUES脱离列赋值上下文，未知时间参数会被推断为text，必须明确候选列类型。
            String tuples = String.join(",", java.util.Collections.nCopies(candidates.size(),
                    "(?::uuid, ?::bigint, ?::varchar, ?::timestamptz)"));
            String sql = "DELETE FROM conversation_session WHERE " + OWNED_PERMISSION
                    + " AND expires_at <= ? AND (session_id, revision, status, expires_at) IN (VALUES " + tuples + ")";
            try (PreparedStatement statement = transaction.prepare(sql)) {
                int parameter = bindPermission(statement, 1);
                statement.setTimestamp(parameter++, Timestamp.from(afterLockNow));
                // 删除重新核对候选版本、终态和到期时间；不能扫到他人或刚被刷新过的记录。
                for (ConversationState candidate : candidates) {
                    statement.setObject(parameter++, candidate.sessionId());
                    statement.setLong(parameter++, candidate.revision());
                    statement.setString(parameter++, candidate.status().name());
                    statement.setTimestamp(parameter++, Timestamp.from(candidate.expiresAt()));
                }
                transaction.writeIssued();
                return statement.executeUpdate();
            }
        });
    }

    private <T> T owned(ConversationJdbcAccess.WriteKind kind, ConversationJdbcAccess.Job<T> job) {
        if (actor.role() != TrustedActor.Role.USER)
            throw new ConversationStoreFailure(ConversationStoreFailure.Code.ROLE_DENIED);
        return access.execute(requestBudget == null ? new ConversationOperationBudget() : requestBudget, kind, job);
    }

    private void requireCurrentUser(ConversationJdbcAccess.Transaction transaction) throws SQLException {
        // 在取identity锁之前设置有限锁等待；配置是事务局部，不污染下一请求的连接。
        try (PreparedStatement settings = transaction.prepare("""
                SELECT set_config('lock_timeout', '500ms', true), set_config('statement_timeout', '1000ms', true)
                """)) { settings.execute(); }
        try (PreparedStatement statement = transaction.prepare("""
                SELECT role, auth_revision, enabled FROM preview_identity WHERE user_id = ? FOR SHARE
                """)) {
            statement.setObject(1, actor.userId());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next() || !row.getBoolean("enabled") || row.getLong("auth_revision") != actor.authRevision()
                        || !actor.role().name().equals(row.getString("role")))
                    throw new ConversationStoreFailure(ConversationStoreFailure.Code.AUTH_REVOKED);
            }
        }
    }

    private ConversationState ownedState(ConversationJdbcAccess.Transaction transaction, UUID id, boolean lock)
            throws SQLException {
        try (PreparedStatement statement = transaction.prepare("SELECT " + COLUMNS
                + " FROM conversation_session WHERE session_id = ? AND " + OWNED_PERMISSION
                + (lock ? " FOR UPDATE" : ""))) {
            statement.setObject(1, id);
            bindPermission(statement, 2);
            try (ResultSet row = statement.executeQuery()) { return row.next() ? stateOf(row) : null; }
        }
    }

    private int bindPermission(PreparedStatement statement, int parameter) throws SQLException {
        statement.setObject(parameter++, actor.userId());
        statement.setObject(parameter++, actor.userId());
        statement.setLong(parameter++, actor.authRevision());
        return parameter;
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
        // UUID冲突不代表匿名调用者有权查看该行；私有/已消失行只返回空状态。
        return findState(connection, sessionId);
    }

    private ConversationState findState(Connection connection, UUID sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT session_id, status, pending_intent, pending_field, expires_at, revision
                FROM conversation_session WHERE session_id = ? AND owner_user_id IS NULL
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
