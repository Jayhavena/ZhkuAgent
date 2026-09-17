package com.zhku.knowledge.conversation;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** JDBC 会话存储的 S01–S11 可执行契约。 */
class JdbcConversationStoreContractTest {

    private static final Instant START = Instant.parse("2026-09-14T00:00:00Z");
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url",
            "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
    );
    private static final String DATABASE_USER = System.getProperty("zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();
    private static final DataSource DATA_SOURCE = new DriverManagerDataSource(
            DATABASE_URL,
            DATABASE_USER,
            DATABASE_PASSWORD
    );

    private final Set<UUID> createdSessionIds = new HashSet<>();
    private ConversationStore store;

    @BeforeAll
    static void migrateSchema() {
        assertThat(DATABASE_PASSWORD)
                .as("运行 JDBC 会话存储契约测试时必须显式提供本机密码")
                .isNotBlank();
        Flyway.configure()
                .dataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void createStore() {
        store = newStore();
    }

    @AfterEach
    void removeOnlyCreatedSessions() throws SQLException {
        try (Connection connection = DATA_SOURCE.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM conversation_session WHERE session_id = ?"
             )) {
            for (UUID sessionId : createdSessionIds) {
                statement.setObject(1, sessionId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Test
    void shouldCreateConversationAndReturnVisibleSnapshot() {
        ConversationState initial = active(newSessionId(), expiresAfter(START));

        StoreResult result = store.create(initial);

        assertThat(result.outcome()).isEqualTo(StoreResult.Outcome.CREATED);
        assertThat(result.state()).isEqualTo(initial);
    }

    @Test
    void shouldRejectDuplicateCreationWithoutReplacingOriginalConversation() {
        UUID sessionId = newSessionId();
        ConversationState original = active(sessionId, expiresAfter(START));
        ConversationState duplicate = active(sessionId, expiresAfter(START.plus(SESSION_TTL)));
        store.create(original);

        StoreResult result = store.create(duplicate);

        assertThat(result.outcome()).isEqualTo(StoreResult.Outcome.ALREADY_EXISTS);
        assertThat(result.state()).isEqualTo(original);
    }

    @Test
    void shouldKeepSessionsIsolatedWhenOnlyOneSessionReceivesPendingInformation() {
        UUID firstSessionId = newSessionId();
        UUID secondSessionId = newSessionId();
        ConversationState second = active(secondSessionId, expiresAfter(START));
        store.create(active(firstSessionId, expiresAfter(START)));
        store.create(second);

        StoreResult update = store.savePending(
                firstSessionId,
                0,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                expiresAfter(START.plus(Duration.ofMinutes(1))),
                clockAt(START.plus(Duration.ofMinutes(1)))
        );

        assertThat(update.outcome()).isEqualTo(StoreResult.Outcome.UPDATED);
        assertThat(store.find(secondSessionId, clockAt(START.plus(Duration.ofMinutes(1)))))
                .extracting(StoreResult::outcome, StoreResult::state)
                .containsExactly(StoreResult.Outcome.FOUND, second);
    }

    @Test
    void shouldNotRefreshExpiryWhenFindingAnActiveConversation() {
        UUID sessionId = newSessionId();
        Instant expiresAt = expiresAfter(START);
        ConversationState initial = active(sessionId, expiresAt);
        store.create(initial);

        StoreResult beforeDeadline = store.find(sessionId, clockAt(expiresAt.minusSeconds(1)));
        StoreResult atDeadline = store.find(sessionId, clockAt(expiresAt));

        assertThat(beforeDeadline.outcome()).isEqualTo(StoreResult.Outcome.FOUND);
        assertThat(beforeDeadline.state().expiresAt()).isEqualTo(expiresAt);
        assertThat(atDeadline.outcome()).isEqualTo(StoreResult.Outcome.EXPIRED);
        assertThat(atDeadline.state()).isEqualTo(initial);
    }

    @Test
    void shouldRefreshTenMinuteExpiryOnlyAfterSavingPendingInformation() {
        UUID sessionId = newSessionId();
        Instant savedAt = START.plus(Duration.ofMinutes(9));
        Instant refreshedExpiresAt = expiresAfter(savedAt);
        store.create(active(sessionId, expiresAfter(START)));

        StoreResult update = store.savePending(
                sessionId,
                0,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                refreshedExpiresAt,
                clockAt(savedAt)
        );

        assertThat(update.state())
                .extracting(
                        ConversationState::pendingIntent,
                        ConversationState::pendingField,
                        ConversationState::expiresAt,
                        ConversationState::revision
                )
                .containsExactly(
                        PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                        PendingField.COURSE_TYPE,
                        refreshedExpiresAt,
                        1L
                );
        assertThat(store.find(sessionId, clockAt(refreshedExpiresAt)).outcome())
                .isEqualTo(StoreResult.Outcome.EXPIRED);
    }

    @Test
    void shouldRefreshTenMinuteExpiryOnlyAfterClearingPendingInformation() {
        UUID sessionId = newSessionId();
        Instant pendingSavedAt = START.plus(Duration.ofMinutes(1));
        Instant clearedAt = START.plus(Duration.ofMinutes(2));
        store.create(active(sessionId, expiresAfter(START)));
        store.savePending(
                sessionId,
                0,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                expiresAfter(pendingSavedAt),
                clockAt(pendingSavedAt)
        );

        StoreResult clear = store.clearPending(
                sessionId,
                1,
                expiresAfter(clearedAt),
                clockAt(clearedAt)
        );

        assertThat(clear.state())
                .extracting(
                        ConversationState::pendingIntent,
                        ConversationState::pendingField,
                        ConversationState::expiresAt,
                        ConversationState::revision
                )
                .containsExactly(null, null, expiresAfter(clearedAt), 2L);
    }

    @Test
    void shouldReturnExpiredWithoutRefreshingOrWritingAtDeadline() {
        UUID sessionId = newSessionId();
        ConversationState initial = active(sessionId, expiresAfter(START));
        store.create(initial);

        StoreResult result = store.savePending(
                sessionId,
                0,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                expiresAfter(initial.expiresAt()),
                clockAt(initial.expiresAt())
        );

        assertThat(result.outcome()).isEqualTo(StoreResult.Outcome.EXPIRED);
        assertThat(result.state()).isEqualTo(initial);
    }

    @Test
    void shouldCancelIdempotentlyWithoutRefreshingExpiry() {
        UUID sessionId = newSessionId();
        Instant expiresAt = expiresAfter(START);
        store.create(active(sessionId, expiresAt));

        StoreResult firstCancel = store.cancel(sessionId, 0, clockAt(START.plusSeconds(1)));
        StoreResult secondCancel = store.cancel(sessionId, 1, clockAt(START.plusSeconds(2)));

        assertThat(firstCancel.state())
                .extracting(
                        ConversationState::status,
                        ConversationState::pendingIntent,
                        ConversationState::pendingField,
                        ConversationState::expiresAt,
                        ConversationState::revision
                )
                .containsExactly(ConversationStatus.CANCELLED, null, null, expiresAt, 1L);
        assertThat(secondCancel)
                .extracting(StoreResult::outcome, StoreResult::state)
                .containsExactly(StoreResult.Outcome.CANCELLED, firstCancel.state());
    }

    @Test
    void shouldNotReactivateCancelledConversationThroughNormalPendingWrite() {
        UUID sessionId = newSessionId();
        store.create(active(sessionId, expiresAfter(START)));
        store.cancel(sessionId, 0, clockAt(START.plusSeconds(1)));

        StoreResult write = store.savePending(
                sessionId,
                1,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                expiresAfter(START.plusSeconds(2)),
                clockAt(START.plusSeconds(2))
        );

        assertThat(write.outcome()).isEqualTo(StoreResult.Outcome.CANCELLED);
        assertThat(store.find(sessionId, clockAt(START.plusSeconds(2))).state())
                .extracting(ConversationState::pendingIntent, ConversationState::pendingField)
                .containsExactly(null, null);
    }

    @Test
    void shouldRemoveOnlyExpiredSessionsDuringCleanup() {
        UUID expiredSessionId = newSessionId();
        UUID activeSessionId = newSessionId();
        UUID cancelledSessionId = newSessionId();
        Instant deadline = expiresAfter(START);
        store.create(active(expiredSessionId, deadline));
        store.create(active(activeSessionId, deadline.plusSeconds(1)));
        store.create(active(cancelledSessionId, deadline.plusSeconds(1)));
        StoreResult cancelled = store.cancel(cancelledSessionId, 0, clockAt(START.plusSeconds(1)));

        assertThat(store.clearExpired(clockAt(deadline))).isEqualTo(1);
        assertThat(store.find(expiredSessionId, clockAt(deadline)).outcome())
                .isEqualTo(StoreResult.Outcome.NOT_FOUND);
        assertThat(store.find(activeSessionId, clockAt(deadline)).outcome())
                .isEqualTo(StoreResult.Outcome.FOUND);
        assertThat(store.find(cancelledSessionId, clockAt(deadline)))
                .extracting(StoreResult::outcome, StoreResult::state)
                .containsExactly(StoreResult.Outcome.CANCELLED, cancelled.state());
    }

    @Test
    void shouldRejectLatePendingWriteAfterCancellationByItsOldRevision() {
        UUID sessionId = newSessionId();
        store.create(active(sessionId, expiresAfter(START)));
        StoreResult cancelled = store.cancel(sessionId, 0, clockAt(START.plusSeconds(1)));

        StoreResult lateWrite = store.savePending(
                sessionId,
                0,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                expiresAfter(START.plusSeconds(2)),
                clockAt(START.plusSeconds(2))
        );

        assertThat(cancelled.state().revision()).isEqualTo(1L);
        assertThat(lateWrite.outcome())
                .isIn(StoreResult.Outcome.STALE_WRITE, StoreResult.Outcome.CANCELLED);
        assertThat(store.find(sessionId, clockAt(START.plusSeconds(2))).state())
                .extracting(
                        ConversationState::pendingIntent,
                        ConversationState::pendingField,
                        ConversationState::revision
                )
                .containsExactly(null, null, 1L);
    }

    private static ConversationStore newStore() {
        try {
            Class<?> implementation = Class.forName(
                    "com.zhku.knowledge.conversation.JdbcConversationStore"
            );
            Constructor<?> constructor = implementation.getDeclaredConstructor(DataSource.class);
            return ConversationStore.class.cast(constructor.newInstance(DATA_SOURCE));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("阶段 8 必须提供 JdbcConversationStore 实现", exception);
        }
    }

    private UUID newSessionId() {
        UUID sessionId = UUID.randomUUID();
        createdSessionIds.add(sessionId);
        return sessionId;
    }

    private static ConversationState active(UUID sessionId, Instant expiresAt) {
        return ConversationState.active(sessionId, expiresAt);
    }

    private static Instant expiresAfter(Instant now) {
        return now.plus(SESSION_TTL);
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
