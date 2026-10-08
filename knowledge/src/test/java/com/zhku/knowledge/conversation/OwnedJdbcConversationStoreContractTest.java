package com.zhku.knowledge.conversation;

import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import com.zhku.knowledge.security.TrustedActor;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** B2真实JDBC、身份许可、CAS、终态和锁；须先通过迁移/匿名隔离前置组。 */
@Timeout(30)
class OwnedJdbcConversationStoreContractTest {
    static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test void ownerCanRebuildViewButOtherUserAndAnonymousCannotAccessAnyOperation() throws Exception {
        try (var fixture = new Fixture()) {
            UUID id = fixture.create();
            var saved = fixture.a.savePending(id, 0, PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE, PendingField.COURSE_TYPE,
                    NOW.plusSeconds(9999), CLOCK);
            assertThat(saved.outcome()).isEqualTo(StoreResult.Outcome.UPDATED);
            var rebuilt = new JdbcConversationStore(fixture.access, fixture.actorA, CLOCK);
            assertThat(rebuilt.find(id, CLOCK).state()).isEqualTo(saved.state());
            for (ConversationStore other : List.of(fixture.b, new JdbcConversationStore(fixture.source))) {
                assertThat(other.find(id, CLOCK)).isEqualTo(new StoreResult(StoreResult.Outcome.NOT_FOUND, null));
                assertThat(other.create(ConversationState.active(id, NOW.plusSeconds(600))))
                        .isEqualTo(new StoreResult(StoreResult.Outcome.ALREADY_EXISTS, null));
                assertThat(other.savePending(id, 1, PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE, PendingField.COURSE_TYPE,
                        NOW.plusSeconds(600), CLOCK).outcome()).isEqualTo(StoreResult.Outcome.NOT_FOUND);
                assertThat(other.clearPending(id, 1, NOW.plusSeconds(600), CLOCK).outcome()).isEqualTo(StoreResult.Outcome.NOT_FOUND);
                assertThat(other.cancel(id, 1, CLOCK).outcome()).isEqualTo(StoreResult.Outcome.NOT_FOUND);
            }
            assertThat(fixture.a.find(id, CLOCK).state()).isEqualTo(saved.state());
        }
    }

    @Test void duplicateCreateDoesNotReplacePendingOrExtendTtl() throws Exception {
        try (var fixture = new Fixture()) {
            UUID id = fixture.create();
            var saved = fixture.a.savePending(id, 0, PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE, PendingField.COURSE_TYPE,
                    NOW.plusSeconds(600), CLOCK).state();
            assertThat(fixture.a.create(ConversationState.active(id, NOW.plusSeconds(9000))))
                    .isEqualTo(new StoreResult(StoreResult.Outcome.ALREADY_EXISTS, saved));
        }
    }

    @Test void pendingWritesAndClearSlideExactlyTenMinutesButReadAndCancelDoNot() throws Exception {
        try (var fixture = new Fixture()) {
            UUID id = fixture.create();
            var first = fixture.a.find(id, CLOCK).state();
            var later = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
            assertThat(fixture.a.find(id, later).state()).isEqualTo(first);
            var pending = fixture.a.savePending(id, 0, PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE, PendingField.COURSE_TYPE,
                    NOW.plusSeconds(9000), later).state();
            assertThat(pending.expiresAt()).isEqualTo(NOW.plusSeconds(660));
            var cleared = fixture.a.clearPending(id, 1, NOW.plusSeconds(9999), Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC)).state();
            assertThat(cleared.expiresAt()).isEqualTo(NOW.plusSeconds(720));
            assertThat(cleared.pendingIntent()).isNull();
            var cancelled = fixture.a.cancel(id, 2, later);
            assertThat(cancelled.outcome()).isEqualTo(StoreResult.Outcome.CANCELLED);
            assertThat(cancelled.state().expiresAt()).isEqualTo(cleared.expiresAt());
            assertThat(fixture.a.cancel(id, 0, later)).isEqualTo(cancelled);
            assertThat(fixture.a.savePending(id, 2, PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE, PendingField.COURSE_TYPE,
                    NOW.plusSeconds(9000), later)).isEqualTo(cancelled);
        }
    }

    @Test void expiryBoundaryAndRevisionOverflowDoNotWrite() throws Exception {
        try (var fixture = new Fixture()) {
            UUID id = fixture.create();
            assertThat(fixture.a.cancel(id, 0, Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC)).outcome())
                    .isEqualTo(StoreResult.Outcome.EXPIRED);
            try (var connection = fixture.source.getConnection()) {
                OwnedConversationSessionSchemaTest.execute(connection, "UPDATE conversation_session SET revision=? WHERE session_id=?", Long.MAX_VALUE, id);
            }
            assertThat(fixture.a.savePending(id, Long.MAX_VALUE, PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                    PendingField.COURSE_TYPE, NOW.plusSeconds(600), CLOCK).outcome()).isEqualTo(StoreResult.Outcome.STALE_WRITE);
            assertThat(fixture.a.find(id, CLOCK).state().revision()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Test void disabledReenabledOrChangedRoleNeverRevivesOldActor() throws Exception {
        try (var fixture = new Fixture(); var connection = fixture.source.getConnection()) {
            UUID id = fixture.create();
            OwnedConversationSessionSchemaTest.execute(connection,
                    "UPDATE preview_identity SET enabled=false,auth_revision=auth_revision+1 WHERE user_id=?", fixture.actorA.userId());
            assertThatThrownBy(() -> fixture.a.find(id, CLOCK)).hasMessage("AUTH_REVOKED");
            OwnedConversationSessionSchemaTest.execute(connection,
                    "UPDATE preview_identity SET enabled=true,auth_revision=auth_revision+1 WHERE user_id=?", fixture.actorA.userId());
            assertThatThrownBy(() -> fixture.a.cancel(id, 0, CLOCK)).hasMessage("AUTH_REVOKED");
            var current = new TrustedActor(fixture.actorA.userId(), TrustedActor.Role.USER, 2);
            assertThat(new JdbcConversationStore(fixture.access, current, CLOCK).find(id, CLOCK).outcome()).isEqualTo(StoreResult.Outcome.FOUND);
            OwnedConversationSessionSchemaTest.execute(connection,
                    "UPDATE preview_identity SET role='MAINTAINER',auth_revision=auth_revision+1 WHERE user_id=?", fixture.actorA.userId());
            assertThatThrownBy(() -> new JdbcConversationStore(fixture.access, current, CLOCK).find(id, CLOCK)).hasMessage("AUTH_REVOKED");
            assertThatThrownBy(() -> new JdbcConversationStore(fixture.access,
                    new TrustedActor(fixture.actorA.userId(), TrustedActor.Role.MAINTAINER, 3), CLOCK).find(id, CLOCK)).hasMessage("ROLE_DENIED");
        }
    }

    @Test void simultaneousSameRevisionWritesHaveExactlyOneWinner() throws Exception {
        try (var fixture = new Fixture()) {
            UUID id = fixture.create();
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            var callers = Executors.newFixedThreadPool(2);
            try {
                Callable<StoreResult> write = () -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("屏障超时");
                    return fixture.a.savePending(id, 0, PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE, PendingField.COURSE_TYPE, NOW.plusSeconds(600), CLOCK); };
                var first = callers.submit(write); var second = callers.submit(write);
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
                assertThat(List.of(first.get(3, TimeUnit.SECONDS).outcome(), second.get(3, TimeUnit.SECONDS).outcome()))
                        .containsExactlyInAnyOrder(StoreResult.Outcome.UPDATED, StoreResult.Outcome.STALE_WRITE);
                assertThat(fixture.a.find(id, CLOCK).state().revision()).isEqualTo(1);
            } finally { start.countDown(); callers.shutdownNow(); assertThat(callers.awaitTermination(2, TimeUnit.SECONDS)).isTrue(); }
        }
    }

    @Test void sessionLockWaitRechecksClockBeforeWriting() throws Exception {
        try (var fixture = new Fixture(); var blocker = fixture.source.getConnection()) {
            UUID id = fixture.create();
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT session_id FROM conversation_session WHERE session_id=? FOR UPDATE")) {
                lock.setObject(1, id); try (var row = lock.executeQuery()) { assertThat(row.next()).isTrue(); }
            }
            var mutable = new OwnedConversationStoreContractTest.MutableClock(NOW);
            var caller = Executors.newSingleThreadExecutor();
            try {
                var write = caller.submit(() -> fixture.a.cancel(id, 0, mutable));
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400);
                boolean waiting = false;
                try (var observer = fixture.source.getConnection(); var query = observer.prepareStatement(
                        "SELECT count(*) FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock'")) {
                    query.setString(1, fixture.applicationName); query.setQueryTimeout(1);
                    while (!waiting && System.nanoTime() < deadline) {
                        try (var row = query.executeQuery()) { row.next(); waiting = row.getInt(1) > 0; }
                        if (!waiting) TimeUnit.MILLISECONDS.sleep(2);
                    }
                }
                assertThat(waiting).as("数据库实际锁等待信号，不能只sleep猜测").isTrue();
                mutable.now = NOW.plusSeconds(600); blocker.commit();
                assertThat(write.get(2250, TimeUnit.MILLISECONDS).outcome()).isEqualTo(StoreResult.Outcome.EXPIRED);
                assertThat(fixture.a.find(id, CLOCK).state().revision()).isZero();
            } finally { blocker.rollback(); caller.shutdownNow(); assertThat(caller.awaitTermination(2, TimeUnit.SECONDS)).isTrue(); }
        }
    }

    @Test void cleanupDeletesAtMostOneHundredOnlyForCurrentOwner() throws Exception {
        try (var fixture = new Fixture(); var connection = fixture.source.getConnection()) {
            for (int index = 0; index < 101; index++) {
                UUID id = UUID.randomUUID(); fixture.ids.add(id);
                OwnedConversationSessionSchemaTest.execute(connection,
                        "INSERT INTO conversation_session(session_id,status,expires_at,revision,owner_user_id) VALUES (?,'ACTIVE',?,0,?)",
                        id, Timestamp.from(NOW), fixture.actorA.userId());
            }
            UUID other = UUID.randomUUID(); fixture.ids.add(other);
            OwnedConversationSessionSchemaTest.execute(connection,
                    "INSERT INTO conversation_session(session_id,status,expires_at,revision,owner_user_id) VALUES (?,'ACTIVE',?,0,?)",
                    other, Timestamp.from(NOW), fixture.actorB.userId());
            try {
                assertThat(fixture.a.clearExpired(CLOCK)).isEqualTo(100);
            } catch (ConversationStoreFailure failure) {
                // 产品异常不携带SQL；测试仅保留有限SQLState，帮助区分语法错误与预算耗尽。
                throw new AssertionError("cleanup SQLState=" + fixture.sqlStates, failure);
            }
            assertThat(fixture.b.find(other, CLOCK).outcome()).isEqualTo(StoreResult.Outcome.EXPIRED);
            assertThat(fixture.a.clearExpired(CLOCK)).isEqualTo(1);
            assertThat(fixture.a.clearExpired(CLOCK)).isZero();
        }
    }
    @Test void cleanupSkipsActuallyLockedRowAndKeepsTheCommittedRefresh() throws Exception {
        try(var fixture=new Fixture(); var blocker=fixture.source.getConnection()) {
            UUID id=fixture.create();
            OwnedConversationSessionSchemaTest.execute(blocker,"UPDATE conversation_session SET expires_at=? WHERE session_id=?",Timestamp.from(NOW),id);
            blocker.setAutoCommit(false);
            try(var lock=blocker.prepareStatement("SELECT session_id FROM conversation_session WHERE session_id=? FOR UPDATE")) {
                lock.setObject(1,id); lock.setQueryTimeout(1);
                try(var row=lock.executeQuery()) { assertThat(row.next()).isTrue(); }
            }
            // 已得到真实行锁信号，清理必须SKIP而非等待锁或删除他人/竞争中的候选。
            long began=System.nanoTime(); assertThat(fixture.a.clearExpired(CLOCK)).isZero();
            assertThat(Duration.ofNanos(System.nanoTime()-began)).isLessThan(Duration.ofMillis(2250));
            OwnedConversationSessionSchemaTest.execute(blocker,
                    "UPDATE conversation_session SET expires_at=?,revision=revision+1 WHERE session_id=?",Timestamp.from(NOW.plusSeconds(600)),id);
            blocker.commit();
            assertThat(fixture.a.clearExpired(CLOCK)).isZero();
            assertThat(fixture.a.find(id,CLOCK).state().revision()).isEqualTo(1);
        }
    }

    static final class Fixture implements AutoCloseable {
        final DataSource source;
        final String applicationName = "zhku-conversation-" + UUID.randomUUID();
        final ConversationJdbcAccess access;
        final TrustedActor actorA = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 0);
        final TrustedActor actorB = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 0);
        final JdbcConversationStore a, b;
        final Set<UUID> ids = new HashSet<>();
        final Queue<String> sqlStates = new ConcurrentLinkedQueue<>();
        Fixture() throws Exception {
            ArchiveTestDatabase.migrate();
            var driver = (DriverManagerDataSource) ArchiveTestDatabase.dataSource();
            var settings = new Properties(); settings.setProperty("connectTimeout", "1"); settings.setProperty("socketTimeout", "2");
            settings.setProperty("cancelSignalTimeout", "1"); settings.setProperty("ApplicationName", applicationName);
            driver.setConnectionProperties(settings);
            source = new AbstractDataSource() {
                @Override public Connection getConnection() throws SQLException { return observed(driver.getConnection()); }
                @Override public Connection getConnection(String user, String password) throws SQLException {
                    return observed(driver.getConnection(user, password));
                }
            };
            try (var connection = source.getConnection()) {
                OwnedConversationSessionSchemaTest.identity(connection, actorA.userId());
                OwnedConversationSessionSchemaTest.identity(connection, actorB.userId());
            }
            access = new ConversationJdbcAccess(source);
            a = new JdbcConversationStore(access, actorA, CLOCK); b = new JdbcConversationStore(access, actorB, CLOCK);
        }
        UUID create() { UUID id = UUID.randomUUID(); ids.add(id); assertThat(a.create(ConversationState.active(id, NOW.plusSeconds(600))).outcome())
                    .isEqualTo(StoreResult.Outcome.CREATED); return id; }
        private Connection observed(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        try {
                            Object value = method.invoke(connection, arguments);
                            if (value instanceof PreparedStatement statement)
                                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                        (statementProxy, statementMethod, statementArguments) -> {
                                            try { return statementMethod.invoke(statement, statementArguments); }
                                            catch (InvocationTargetException error) {
                                                if (error.getCause() instanceof SQLException sql && sql.getSQLState() != null) {
                                                    var operator = java.util.regex.Pattern.compile("operator does not exist: ([a-z ]+ [=<>]+ [a-z ]+)")
                                                            .matcher(sql.getMessage());
                                                    sqlStates.add(sql.getSQLState() + (operator.find() ? ":" + operator.group(1).trim() : ""));
                                                }
                                                throw error.getCause();
                                            }
                                        });
                            return value;
                        } catch (InvocationTargetException error) { throw error.getCause(); }
                    });
        }
        @Override public void close() throws Exception {
            access.close(Duration.ofSeconds(2));
            assertThat(access.status().inFlight()).isZero(); assertThat(access.status().liveThreads()).isZero();
            try (var connection = source.getConnection()) {
                for (UUID id : ids) OwnedConversationSessionSchemaTest.execute(connection, "DELETE FROM conversation_session WHERE session_id=?", id);
                for (UUID id : List.of(actorA.userId(), actorB.userId()))
                    OwnedConversationSessionSchemaTest.execute(connection, "DELETE FROM preview_identity WHERE user_id=?", id);
            }
        }
    }
}
