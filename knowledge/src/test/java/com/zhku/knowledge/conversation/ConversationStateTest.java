package com.zhku.knowledge.conversation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * 会话状态领域约束测试。
 *
 * <p>这里固定状态组合与时间边界，避免后续存储实现为了迁就错误行为而放宽状态机规则。</p>
 */
class ConversationStateTest {

    private static final UUID SESSION_ID =
            UUID.fromString("7fe7cad8-2d2e-4304-aa8c-b2c7502a6bd3");
    private static final Instant INITIAL_EXPIRES_AT =
            Instant.parse("2026-09-14T07:00:00Z");

    @Test
    void shouldExposeOnlyActiveAndCancelledAsPersistedStatuses() {
        assertThat(ConversationStatus.values())
                .containsExactly(
                        ConversationStatus.ACTIVE,
                        ConversationStatus.CANCELLED
                );
    }

    @Test
    void shouldRejectPendingIntentAndFieldWhenOnlyOneIsPresent() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ConversationState(
                SESSION_ID,
                ConversationStatus.ACTIVE,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                null,
                INITIAL_EXPIRES_AT,
                0
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new ConversationState(
                SESSION_ID,
                ConversationStatus.ACTIVE,
                null,
                PendingField.COURSE_TYPE,
                INITIAL_EXPIRES_AT,
                0
        ));
    }

    @Test
    void shouldKeepCancelledConversationWithoutPendingInformation() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ConversationState(
                SESSION_ID,
                ConversationStatus.CANCELLED,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                INITIAL_EXPIRES_AT,
                0
        ));
    }

    @Test
    void shouldTreatExpiryDeadlineAsExpiredAtExactBoundary() {
        ConversationState state = ConversationState.active(
                SESSION_ID,
                INITIAL_EXPIRES_AT
        );
        Clock clock = Clock.fixed(INITIAL_EXPIRES_AT, ZoneOffset.UTC);

        assertThat(state.isExpired(clock)).isTrue();
    }

    @Test
    void shouldCreateNewRevisionsForPendingUpdatesAndCancellation() {
        ConversationState active = ConversationState.active(
                SESSION_ID,
                INITIAL_EXPIRES_AT
        );
        Instant pendingExpiresAt = Instant.parse("2026-09-14T07:10:00Z");
        ConversationState pending = active.withPending(
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                pendingExpiresAt
        );
        Instant clearedExpiresAt = Instant.parse("2026-09-14T07:20:00Z");
        ConversationState cleared = pending.clearPending(clearedExpiresAt);
        ConversationState cancelled = cleared.cancel();

        assertThat(active)
                .extracting(
                        ConversationState::pendingIntent,
                        ConversationState::pendingField,
                        ConversationState::revision
                )
                .containsExactly(null, null, 0L);
        assertThat(pending).isNotSameAs(active);
        assertThat(pending.pendingIntent())
                .isEqualTo(PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE);
        assertThat(pending.pendingField()).isEqualTo(PendingField.COURSE_TYPE);
        assertThat(pending.expiresAt()).isEqualTo(pendingExpiresAt);
        assertThat(pending.revision()).isEqualTo(1L);
        assertThat(cleared).isNotSameAs(pending);
        assertThat(cleared.pendingIntent()).isNull();
        assertThat(cleared.pendingField()).isNull();
        assertThat(cleared.expiresAt()).isEqualTo(clearedExpiresAt);
        assertThat(cleared.revision()).isEqualTo(2L);
        assertThat(cancelled).isNotSameAs(cleared);
        assertThat(cancelled.status()).isEqualTo(ConversationStatus.CANCELLED);
        assertThat(cancelled.pendingIntent()).isNull();
        assertThat(cancelled.pendingField()).isNull();
        assertThat(cancelled.expiresAt()).isEqualTo(clearedExpiresAt);
        assertThat(cancelled.revision()).isEqualTo(3L);
    }

    @Test
    void shouldRejectNormalPendingUpdateAfterCancellation() {
        ConversationState cancelled = ConversationState.active(
                SESSION_ID,
                INITIAL_EXPIRES_AT
        ).cancel();

        assertThatIllegalStateException().isThrownBy(() -> cancelled.withPending(
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                Instant.parse("2026-09-14T07:10:00Z")
        ));
    }
}
