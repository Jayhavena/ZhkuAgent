package com.zhku.knowledge.conversation;

import com.zhku.knowledge.security.TrustedActor;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** B1仅是明确的JDBC协议替身，真库归属/锁/匿名隔离由B2验收。 */
@Timeout(10)
class OwnedConversationStoreContractTest {
    static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final TrustedActor ACTOR = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 7);
    static final UUID ID = UUID.randomUUID();
    static final ConversationState INITIAL = ConversationState.active(ID, NOW.plusSeconds(600));

    @Test void createdStateUsesServerClockAndOwnedInsert() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.rows(INITIAL);
            var result = fixture.store.create(ConversationState.active(ID, NOW.plusSeconds(3600)));
            assertThat(result).isEqualTo(new StoreResult(StoreResult.Outcome.CREATED, INITIAL));
            assertThat(fixture.sql).anyMatch(sql -> sql.contains("INSERT INTO conversation_session")
                    && sql.contains("preview_identity") && sql.contains("owner_user_id"));
            verify(fixture.connection).commit();
        }
    }

    @Test void invisibleCollisionDoesNotExposeStateOrRetryCreate() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.rows(null, null);
            assertThat(fixture.store.create(INITIAL))
                    .isEqualTo(new StoreResult(StoreResult.Outcome.ALREADY_EXISTS, null));
            assertThat(fixture.sql.stream().filter(sql -> sql.startsWith("INSERT")).count()).isEqualTo(1);
        }
    }

    @Test void visibleCollisionPreservesOriginalPendingExpiryAndRevision() throws Exception {
        try (var fixture = new Fixture()) {
            var original = INITIAL.withPending(PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                    PendingField.COURSE_TYPE, INITIAL.expiresAt());
            fixture.rows(null, original);
            assertThat(fixture.store.create(INITIAL))
                    .isEqualTo(new StoreResult(StoreResult.Outcome.ALREADY_EXISTS, original));
        }
    }

    @Test void missingExpiredAndCancelledReadsHaveDistinctOutcomesWithoutRenewal() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.rows(null, ConversationState.active(ID, NOW), INITIAL.cancel());
            assertThat(fixture.store.find(ID, CLOCK).outcome()).isEqualTo(StoreResult.Outcome.NOT_FOUND);
            assertThat(fixture.store.find(ID, CLOCK).outcome()).isEqualTo(StoreResult.Outcome.EXPIRED);
            assertThat(fixture.store.find(ID, CLOCK)).isEqualTo(new StoreResult(StoreResult.Outcome.CANCELLED, INITIAL.cancel()));
            assertThat(fixture.sql).noneMatch(sql -> sql.startsWith("UPDATE"));
            fixture.assertOwnedSql();
        }
    }

    @Test void staleRevisionDoesNotIssueAnUpdate() throws Exception {
        try (var fixture = new Fixture()) {
            var current = INITIAL.withPending(PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                    PendingField.COURSE_TYPE, INITIAL.expiresAt());
            fixture.rows(current);
            assertThat(fixture.store.cancel(ID, 0, CLOCK)).isEqualTo(new StoreResult(StoreResult.Outcome.STALE_WRITE, current));
            assertThat(fixture.sql).noneMatch(sql -> sql.startsWith("UPDATE"));
            fixture.assertOwnedSql();
        }
    }

    @Test void pendingWriteUsesPostLockClockAndCancelKeepsExpiry() throws Exception {
        try (var fixture = new Fixture()) {
            var mutable = new MutableClock(NOW);
            fixture.afterSessionLock = () -> mutable.now = NOW.plusSeconds(30);
            var saved = INITIAL.withPending(PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                    PendingField.COURSE_TYPE, NOW.plusSeconds(630));
            fixture.rows(INITIAL, saved);
            var result = fixture.store.savePending(ID, 0, saved.pendingIntent(), saved.pendingField(),
                    NOW.plusSeconds(600), mutable);
            assertThat(result.state()).isEqualTo(saved);
            assertThat(fixture.bindings).anyMatch(binding -> binding.containsValue(Timestamp.from(NOW.plusSeconds(630))));
            fixture.afterSessionLock = () -> { };
            fixture.rows(saved, saved.cancel());
            assertThat(fixture.store.cancel(ID, 1, mutable))
                    .isEqualTo(new StoreResult(StoreResult.Outcome.CANCELLED, saved.cancel()));
        }
    }

    @Test void lockWaitPastExpiryCannotWriteOrReviveTheSession() throws Exception {
        try (var fixture = new Fixture()) {
            var mutable = new MutableClock(NOW);
            fixture.afterSessionLock = () -> mutable.now = INITIAL.expiresAt();
            fixture.rows(INITIAL);
            assertThat(fixture.store.savePending(ID, 0, PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                    PendingField.COURSE_TYPE, INITIAL.expiresAt(), mutable).outcome())
                    .isEqualTo(StoreResult.Outcome.EXPIRED);
            assertThat(fixture.sql).noneMatch(sql -> sql.startsWith("UPDATE"));
        }
    }

    @Test void clearWithoutPendingDoesNotRenewAndRepeatedCancelDoesNotUpdate() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.rows(INITIAL, INITIAL.cancel());
            assertThat(fixture.store.clearPending(ID, 0, NOW.plusSeconds(600), CLOCK).outcome())
                    .isEqualTo(StoreResult.Outcome.STALE_WRITE);
            assertThat(fixture.store.cancel(ID, 0, CLOCK).state()).isEqualTo(INITIAL.cancel());
            assertThat(fixture.sql).noneMatch(sql -> sql.startsWith("UPDATE"));
        }
    }

    @Test void maintainerCannotUseOwnedStoreAndRevokedActorFailsBeforeSessionRead() throws Exception {
        try (var fixture = new Fixture()) {
            var maintainer = new TrustedActor(ACTOR.userId(), TrustedActor.Role.MAINTAINER, 7);
            var store = new JdbcConversationStore(fixture.access, maintainer, CLOCK);
            assertThatThrownBy(() -> store.find(ID, CLOCK)).isInstanceOf(ConversationStoreFailure.class)
                    .extracting("code").isEqualTo(ConversationStoreFailure.Code.ROLE_DENIED);
            fixture.enabled = false;
            assertThatThrownBy(() -> fixture.store.find(ID, CLOCK)).isInstanceOf(ConversationStoreFailure.class)
                    .extracting("code").isEqualTo(ConversationStoreFailure.Code.AUTH_REVOKED);
            assertThat(fixture.sql).noneMatch(sql -> sql.contains("FROM conversation_session"));
        }
    }

    @Test void cleanupIsOneOwnedBatchWithLockAndDeletionRechecks() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.rows(ConversationState.active(ID, NOW));
            assertThat(fixture.store.clearExpired(CLOCK)).isEqualTo(1);
            assertThat(fixture.sql).anyMatch(sql -> sql.contains("LIMIT 100") && sql.contains("SKIP LOCKED"));
            assertThat(fixture.sql).anyMatch(sql -> sql.startsWith("DELETE") && sql.contains("revision")
                    && sql.contains("status") && sql.contains("expires_at"));
            fixture.assertOwnedSql();
        }
    }

    @Test void eachImmutableViewBindsItsOwnActorToEveryRead() throws Exception {
        try (var fixture = new Fixture()) {
            var other = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, ACTOR.authRevision());
            var otherView = new JdbcConversationStore(fixture.access, other, CLOCK, new ConversationOperationBudget());
            fixture.rows(INITIAL, null);
            assertThat(fixture.store.find(ID, CLOCK).outcome()).isEqualTo(StoreResult.Outcome.FOUND);
            assertThat(otherView.find(ID, CLOCK).outcome()).isEqualTo(StoreResult.Outcome.NOT_FOUND);
            var owners = new ArrayList<UUID>();
            for (int index = 0; index < fixture.sql.size(); index++) {
                if (fixture.sql.get(index).contains("FROM conversation_session")) {
                    var parameters = fixture.bindings.get(index);
                    owners.add((UUID) parameters.get(2));
                    assertThat(parameters.get(3)).isEqualTo(parameters.get(2));
                    assertThat(parameters.get(4)).isEqualTo(ACTOR.authRevision());
                }
            }
            assertThat(owners).containsExactly(ACTOR.userId(), other.userId());
        }
    }

    @Test void changedAuthRevisionAndRoleFailBeforeAnySessionOperation() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.identityRevision = ACTOR.authRevision() + 1;
            assertThatThrownBy(() -> fixture.store.find(ID, CLOCK)).hasMessage("AUTH_REVOKED");
            fixture.identityRevision = ACTOR.authRevision();
            fixture.identityRole = "MAINTAINER";
            assertThatThrownBy(() -> fixture.store.cancel(ID, 0, CLOCK)).hasMessage("AUTH_REVOKED");
            assertThat(fixture.sql).noneMatch(sql -> sql.contains("conversation_session"));
        }
    }

    @Test void pendingClearAdvancesOnceAndRetainsNoHistoryOrPending() throws Exception {
        try (var fixture = new Fixture()) {
            var pending = INITIAL.withPending(PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                    PendingField.COURSE_TYPE, INITIAL.expiresAt());
            var cleared = pending.clearPending(NOW.plusSeconds(660));
            fixture.rows(pending, cleared);
            var clock = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
            assertThat(fixture.store.clearPending(ID, pending.revision(), NOW.plusSeconds(9000), clock))
                    .isEqualTo(new StoreResult(StoreResult.Outcome.UPDATED, cleared));
            assertThat(fixture.bindings).anyMatch(binding -> binding.containsValue(Timestamp.from(NOW.plusSeconds(660))));
            assertThat(cleared.pendingIntent()).isNull();
            assertThat(cleared.pendingField()).isNull();
        }
    }

    @Test void failedAtomicWriteStillDiagnosesOnlyOwnedCurrentState() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.rows(INITIAL, null, null);
            assertThat(fixture.store.cancel(ID, 0, CLOCK))
                    .isEqualTo(new StoreResult(StoreResult.Outcome.NOT_FOUND, null));
            fixture.assertOwnedSql();
            assertThat(fixture.sql.stream().filter(sql -> sql.startsWith("UPDATE")).count()).isEqualTo(1);
        }
    }

    static final class MutableClock extends Clock {
        volatile Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final List<String> sql = new ArrayList<>();
        final List<Map<Integer, Object>> bindings = new ArrayList<>();
        final Deque<Optional<ConversationState>> results = new ArrayDeque<>();
        final ConversationJdbcAccess access;
        final JdbcConversationStore store;
        boolean enabled = true;
        long identityRevision = ACTOR.authRevision();
        String identityRole = "USER";
        Runnable afterSessionLock = () -> { };
        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                String query = ((String) call.getArgument(0)).strip();
                sql.add(query);
                var params = new HashMap<Integer, Object>();
                bindings.add(params);
                var statement = mock(PreparedStatement.class);
                doAnswer(bind -> { params.put(bind.getArgument(0), bind.getArgument(1)); return null; })
                        .when(statement).setObject(anyInt(), any());
                doAnswer(bind -> { params.put(bind.getArgument(0), bind.getArgument(1)); return null; })
                        .when(statement).setString(anyInt(), any());
                doAnswer(bind -> { params.put(bind.getArgument(0), bind.getArgument(1)); return null; })
                        .when(statement).setLong(anyInt(), anyLong());
                doAnswer(bind -> { params.put(bind.getArgument(0), bind.getArgument(1)); return null; })
                        .when(statement).setTimestamp(anyInt(), any());
                when(statement.executeQuery()).thenAnswer(ignored -> {
                    var row = mock(ResultSet.class);
                    if (query.startsWith("SELECT role")) {
                        when(row.next()).thenReturn(true, false);
                        when(row.getString("role")).thenReturn(identityRole);
                        when(row.getLong("auth_revision")).thenReturn(identityRevision);
                        when(row.getBoolean("enabled")).thenReturn(enabled);
                    } else {
                        if (results.isEmpty()) throw new AssertionError("未预设Session查询结果");
                        var state = results.removeFirst().orElse(null);
                        when(row.next()).thenReturn(state != null, false);
                        if (state != null) {
                            when(row.getObject("session_id", UUID.class)).thenReturn(state.sessionId());
                            when(row.getString("status")).thenReturn(state.status().name());
                            when(row.getString("pending_intent")).thenReturn(state.pendingIntent() == null ? null : state.pendingIntent().name());
                            when(row.getString("pending_field")).thenReturn(state.pendingField() == null ? null : state.pendingField().name());
                            when(row.getLong("revision")).thenReturn(state.revision());
                            when(row.getTimestamp("expires_at")).thenReturn(Timestamp.from(state.expiresAt()));
                        }
                        if (query.contains("FOR UPDATE")) afterSessionLock.run();
                    }
                    return row;
                });
                when(statement.executeUpdate()).thenReturn(1);
                return statement;
            });
            access = new ConversationJdbcAccess(source);
            store = new JdbcConversationStore(access, ACTOR, CLOCK);
        }
        void rows(ConversationState... states) { for (var state : states) results.add(Optional.ofNullable(state)); }
        void assertOwnedSql() {
            assertThat(sql.stream().filter(query -> query.contains("conversation_session")).toList())
                    .allMatch(query -> query.contains("owner_user_id") && query.contains("preview_identity")
                            && query.contains("auth_revision") && query.contains("enabled"));
        }
        @Override public void close() { access.close(Duration.ofSeconds(2)); }
    }
}
