package com.zhku.knowledge.conversation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话存储的可执行契约。
 *
 * <p>阶段 2 刻意不提供存储实现。本测试通过类名约束阶段 3 的实现入口，使测试能够先以
 * "实现尚不存在" 的原因红灯，而不是为尚未确定的容器或锁策略绑定实现细节。</p>
 */
class ConversationStoreContractTest {

    private static final Instant START = Instant.parse("2026-09-14T00:00:00Z");
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);

    @Test
    void shouldCreateConversationAndReturnVisibleSnapshot() {
        ConversationStore store = newStore();
        ConversationState initial = active(sessionId("create"), expiresAfter(START));

        StoreResult result = store.create(initial);

        assertThat(result.outcome()).isEqualTo(StoreResult.Outcome.CREATED);
        assertThat(result.state()).isEqualTo(initial);
    }

    @Test
    void shouldRejectDuplicateCreationWithoutReplacingOriginalConversation() {
        ConversationStore store = newStore();
        UUID sessionId = sessionId("duplicate");
        ConversationState original = active(sessionId, expiresAfter(START));
        ConversationState duplicate = active(sessionId, expiresAfter(START.plus(SESSION_TTL)));
        store.create(original);

        StoreResult result = store.create(duplicate);

        assertThat(result.outcome()).isEqualTo(StoreResult.Outcome.ALREADY_EXISTS);
        assertThat(result.state()).isEqualTo(original);
    }

    @Test
    void shouldKeepSessionsIsolatedWhenOnlyOneSessionReceivesPendingInformation() {
        ConversationStore store = newStore();
        UUID firstSessionId = sessionId("first");
        UUID secondSessionId = sessionId("second");
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
        StoreResult secondLookup = store.find(secondSessionId, clockAt(START.plus(Duration.ofMinutes(1))));

        assertThat(update.outcome()).isEqualTo(StoreResult.Outcome.UPDATED);
        assertThat(secondLookup.outcome()).isEqualTo(StoreResult.Outcome.FOUND);
        assertThat(secondLookup.state()).isEqualTo(second);
    }

    @Test
    void shouldNotRefreshExpiryWhenFindingAnActiveConversation() {
        ConversationStore store = newStore();
        UUID sessionId = sessionId("read-does-not-refresh");
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
        ConversationStore store = newStore();
        UUID sessionId = sessionId("save-pending");
        Instant initialExpiresAt = expiresAfter(START);
        Instant savedAt = START.plus(Duration.ofMinutes(9));
        Instant refreshedExpiresAt = expiresAfter(savedAt);
        store.create(active(sessionId, initialExpiresAt));

        StoreResult update = store.savePending(
                sessionId,
                0,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                refreshedExpiresAt,
                clockAt(savedAt)
        );
        StoreResult beforeRefreshedDeadline = store.find(
                sessionId,
                clockAt(refreshedExpiresAt.minusSeconds(1))
        );
        StoreResult atRefreshedDeadline = store.find(sessionId, clockAt(refreshedExpiresAt));

        assertThat(update.outcome()).isEqualTo(StoreResult.Outcome.UPDATED);
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
        assertThat(beforeRefreshedDeadline.outcome()).isEqualTo(StoreResult.Outcome.FOUND);
        assertThat(atRefreshedDeadline.outcome()).isEqualTo(StoreResult.Outcome.EXPIRED);
    }

    @Test
    void shouldRefreshTenMinuteExpiryOnlyAfterClearingPendingInformation() {
        ConversationStore store = newStore();
        UUID sessionId = sessionId("clear-pending");
        Instant pendingSavedAt = START.plus(Duration.ofMinutes(1));
        Instant pendingExpiresAt = expiresAfter(pendingSavedAt);
        Instant clearedAt = START.plus(Duration.ofMinutes(2));
        Instant clearedExpiresAt = expiresAfter(clearedAt);
        store.create(active(sessionId, expiresAfter(START)));
        store.savePending(
                sessionId,
                0,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                pendingExpiresAt,
                clockAt(pendingSavedAt)
        );

        StoreResult clear = store.clearPending(
                sessionId,
                1,
                clearedExpiresAt,
                clockAt(clearedAt)
        );

        assertThat(clear.outcome()).isEqualTo(StoreResult.Outcome.UPDATED);
        assertThat(clear.state())
                .extracting(
                        ConversationState::pendingIntent,
                        ConversationState::pendingField,
                        ConversationState::expiresAt,
                        ConversationState::revision
                )
                .containsExactly(null, null, clearedExpiresAt, 2L);
        assertThat(store.find(sessionId, clockAt(clearedExpiresAt.minusSeconds(1))).outcome())
                .isEqualTo(StoreResult.Outcome.FOUND);
        assertThat(store.find(sessionId, clockAt(clearedExpiresAt)).outcome())
                .isEqualTo(StoreResult.Outcome.EXPIRED);
    }

    @Test
    void shouldReturnExpiredWithoutRefreshingOrWritingAtDeadline() {
        ConversationStore store = newStore();
        UUID sessionId = sessionId("expired-write");
        Instant expiresAt = expiresAfter(START);
        ConversationState initial = active(sessionId, expiresAt);
        store.create(initial);

        StoreResult result = store.savePending(
                sessionId,
                0,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                expiresAfter(expiresAt),
                clockAt(expiresAt)
        );

        assertThat(result.outcome()).isEqualTo(StoreResult.Outcome.EXPIRED);
        assertThat(result.state()).isEqualTo(initial);
    }

    @Test
    void shouldCancelIdempotentlyWithoutRefreshingExpiry() {
        ConversationStore store = newStore();
        UUID sessionId = sessionId("cancel-idempotently");
        Instant expiresAt = expiresAfter(START);
        store.create(active(sessionId, expiresAt));

        StoreResult firstCancel = store.cancel(sessionId, 0, clockAt(START.plusSeconds(1)));
        StoreResult secondCancel = store.cancel(sessionId, 1, clockAt(START.plusSeconds(2)));

        assertThat(firstCancel.outcome()).isEqualTo(StoreResult.Outcome.CANCELLED);
        assertThat(firstCancel.state())
                .extracting(
                        ConversationState::status,
                        ConversationState::pendingIntent,
                        ConversationState::pendingField,
                        ConversationState::expiresAt,
                        ConversationState::revision
                )
                .containsExactly(ConversationStatus.CANCELLED, null, null, expiresAt, 1L);
        assertThat(secondCancel.outcome()).isEqualTo(StoreResult.Outcome.CANCELLED);
        assertThat(secondCancel.state()).isEqualTo(firstCancel.state());
    }

    @Test
    void shouldNotReactivateCancelledConversationThroughNormalPendingWrite() {
        ConversationStore store = newStore();
        UUID sessionId = sessionId("cancelled-normal-write");
        Instant expiresAt = expiresAfter(START);
        store.create(active(sessionId, expiresAt));
        store.cancel(sessionId, 0, clockAt(START.plusSeconds(1)));

        StoreResult write = store.savePending(
                sessionId,
                1,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                expiresAfter(START.plusSeconds(2)),
                clockAt(START.plusSeconds(2))
        );
        StoreResult visible = store.find(sessionId, clockAt(START.plusSeconds(2)));

        assertThat(write.outcome()).isEqualTo(StoreResult.Outcome.CANCELLED);
        assertThat(visible.outcome()).isEqualTo(StoreResult.Outcome.CANCELLED);
        assertThat(visible.state().pendingIntent()).isNull();
        assertThat(visible.state().pendingField()).isNull();
    }

    @Test
    void shouldRemoveOnlyExpiredSessionsDuringCleanup() {
        ConversationStore store = newStore();
        UUID expiredSessionId = sessionId("expired-cleanup");
        UUID activeSessionId = sessionId("active-cleanup");
        UUID cancelledSessionId = sessionId("cancelled-cleanup");
        Instant deadline = expiresAfter(START);
        store.create(active(expiredSessionId, deadline));
        store.create(active(activeSessionId, deadline.plusSeconds(1)));
        store.create(active(cancelledSessionId, deadline.plusSeconds(1)));
        StoreResult cancelled = store.cancel(cancelledSessionId, 0, clockAt(START.plusSeconds(1)));

        int removedCount = store.clearExpired(clockAt(deadline));

        assertThat(removedCount).isEqualTo(1);
        assertThat(store.find(expiredSessionId, clockAt(deadline)).outcome())
                .isEqualTo(StoreResult.Outcome.NOT_FOUND);
        assertThat(store.find(activeSessionId, clockAt(deadline)).outcome())
                .isEqualTo(StoreResult.Outcome.FOUND);
        assertThat(store.find(cancelledSessionId, clockAt(deadline)).outcome())
                .isEqualTo(StoreResult.Outcome.CANCELLED);
        assertThat(store.find(cancelledSessionId, clockAt(deadline)).state())
                .isEqualTo(cancelled.state());
    }

    @Test
    void shouldRejectLatePendingWriteAfterCancellationByItsOldRevision() {
        ConversationStore store = newStore();
        UUID sessionId = sessionId("late-write");
        Instant expiresAt = expiresAfter(START);
        store.create(active(sessionId, expiresAt));
        StoreResult cancelled = store.cancel(sessionId, 0, clockAt(START.plusSeconds(1)));

        StoreResult lateWrite = store.savePending(
                sessionId,
                0,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                expiresAfter(START.plusSeconds(2)),
                clockAt(START.plusSeconds(2))
        );
        StoreResult visible = store.find(sessionId, clockAt(START.plusSeconds(2)));

        assertThat(cancelled.state().revision()).isEqualTo(1L);
        assertThat(lateWrite.outcome())
                .isIn(StoreResult.Outcome.STALE_WRITE, StoreResult.Outcome.CANCELLED);
        assertThat(visible.outcome()).isEqualTo(StoreResult.Outcome.CANCELLED);
        assertThat(visible.state())
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
                    "com.zhku.knowledge.conversation.InMemoryConversationStore"
            );
            return ConversationStore.class.cast(implementation.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("阶段 3 必须提供 InMemoryConversationStore 实现", exception);
        }
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

    private static UUID sessionId(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }
}
