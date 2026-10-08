package com.zhku.knowledge.conversation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 防止领域服务把不可访问状态解释成新问题并隐式创建会话；此处是存储替身证据。 */
class ControlledConversationAccessStoreTest {
    private static final UUID ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);
    private static final ConversationState STATE = ConversationState.active(ID, CLOCK.instant().plusSeconds(600));

    @Test void missingSessionMustThrowInsteadOfAllowingImplicitCreate() {
        var owned = mock(ConversationStore.class);
        when(owned.find(ID, CLOCK)).thenReturn(new StoreResult(StoreResult.Outcome.NOT_FOUND, null));
        var strict = new ControlledConversationAccessStore(owned, ID, 0);
        assertThatThrownBy(() -> strict.find(ID, CLOCK))
                .isInstanceOf(ControlledConversationAccessStore.Failure.class)
                .extracting("code").isEqualTo(ControlledConversationAccessStore.Code.SESSION_NOT_FOUND);
        assertThatThrownBy(() -> strict.create(STATE)).isInstanceOf(ControlledConversationAccessStore.Failure.class);
        assertThatThrownBy(() -> strict.clearExpired(CLOCK)).isInstanceOf(ControlledConversationAccessStore.Failure.class);
        verify(owned).find(ID, CLOCK);
        verifyNoMoreInteractions(owned);
    }

    @Test void cancelledExpiredAndConcurrentStatesMustNotReachTheDomain() {
        for (var outcome : new StoreResult.Outcome[] {StoreResult.Outcome.EXPIRED, StoreResult.Outcome.CANCELLED,
                StoreResult.Outcome.STALE_WRITE}) {
            var owned = mock(ConversationStore.class);
            when(owned.find(ID, CLOCK)).thenReturn(new StoreResult(outcome, STATE));
            assertThatThrownBy(() -> new ControlledConversationAccessStore(owned, ID, 0).find(ID, CLOCK))
                    .isInstanceOf(ControlledConversationAccessStore.Failure.class);
        }
        var owned = mock(ConversationStore.class);
        when(owned.find(ID, CLOCK)).thenReturn(new StoreResult(StoreResult.Outcome.FOUND,
                STATE.withPending(PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE, PendingField.COURSE_TYPE, STATE.expiresAt())));
        assertThatThrownBy(() -> new ControlledConversationAccessStore(owned, ID, 0).find(ID, CLOCK))
                .isInstanceOf(ControlledConversationAccessStore.Failure.class);
    }

    @Test void onlyThisRequestsSuccessfulWriteCanAdvanceTheRevision() {
        var owned = mock(ConversationStore.class);
        var next = STATE.withPending(PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE, PendingField.COURSE_TYPE, STATE.expiresAt());
        when(owned.find(ID, CLOCK)).thenReturn(new StoreResult(StoreResult.Outcome.FOUND, STATE),
                new StoreResult(StoreResult.Outcome.FOUND, next));
        when(owned.savePending(ID, 0, next.pendingIntent(), next.pendingField(), next.expiresAt(), CLOCK))
                .thenReturn(new StoreResult(StoreResult.Outcome.UPDATED, next));
        var strict = new ControlledConversationAccessStore(owned, ID, 0);
        assertThat(strict.find(ID, CLOCK).state()).isEqualTo(STATE);
        assertThat(strict.savePending(ID, 0, next.pendingIntent(), next.pendingField(), next.expiresAt(), CLOCK).state())
                .isEqualTo(next);
        assertThat(strict.find(ID, CLOCK).state()).isEqualTo(next);
        assertThatThrownBy(() -> strict.clearPending(ID, 0, next.expiresAt(), CLOCK))
                .isInstanceOf(ControlledConversationAccessStore.Failure.class);
        verify(owned, never()).clearPending(any(), anyLong(), any(), any());
    }

    @Test void otherIdMalformedResultAndStoreFailureCannotBecomeAnswers() {
        var owned = mock(ConversationStore.class);
        var strict = new ControlledConversationAccessStore(owned, ID, 0);
        assertThatThrownBy(() -> strict.find(UUID.randomUUID(), CLOCK))
                .isInstanceOf(ControlledConversationAccessStore.Failure.class);
        verifyNoInteractions(owned);
        when(owned.find(ID, CLOCK)).thenReturn(new StoreResult(StoreResult.Outcome.FOUND, null));
        assertThatThrownBy(() -> strict.find(ID, CLOCK)).isInstanceOf(ControlledConversationAccessStore.Failure.class);
        var failure = new ConversationStoreFailure(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
        when(owned.find(ID, CLOCK)).thenThrow(failure);
        assertThatThrownBy(() -> strict.find(ID, CLOCK)).isSameAs(failure);
    }
}
