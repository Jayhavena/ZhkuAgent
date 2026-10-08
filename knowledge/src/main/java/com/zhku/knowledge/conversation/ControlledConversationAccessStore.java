package com.zhku.knowledge.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 四层领域链必须共用本实例；不可访问状态不能以NOT_FOUND流入隐式创建/普通作答路径。 */
public final class ControlledConversationAccessStore implements ConversationStore {
    public enum Code { SESSION_NOT_FOUND, SESSION_EXPIRED, SESSION_CANCELLED, SESSION_STATE_CHANGED, SESSION_CREATE_CONFLICT }
    public static final class Failure extends RuntimeException {
        private final Code code;
        public Failure(Code code) { super(Objects.requireNonNull(code).name(), null, false, false); this.code = code; }
        public Code code() { return code; }
    }

    private final ConversationStore owned;
    private final UUID sessionId;
    private long revision;

    public ControlledConversationAccessStore(ConversationStore owned, UUID sessionId, long expectedRevision) {
        this.owned = Objects.requireNonNull(owned, "受控存储不能为空");
        this.sessionId = Objects.requireNonNull(sessionId, "会话编号不能为空");
        if (expectedRevision < 0) throw new IllegalArgumentException("期望版本不能为负数");
        revision = expectedRevision;
    }

    @Override public StoreResult create(ConversationState initialState) { throw changed(); }
    @Override public int clearExpired(Clock clock) { throw changed(); }

    @Override public synchronized StoreResult find(UUID id, Clock clock) {
        requireId(id);
        return requireActive(owned.find(id, clock), revision, clock, StoreResult.Outcome.FOUND);
    }

    @Override public synchronized StoreResult savePending(UUID id, long expectedRevision, PendingIntent intent,
            PendingField field, Instant nextExpiresAt, Clock clock) {
        requireWrite(id, expectedRevision);
        StoreResult result = requireActive(owned.savePending(id, expectedRevision, intent, field, nextExpiresAt, clock),
                nextRevision(), clock, StoreResult.Outcome.UPDATED);
        revision = result.state().revision();
        return result;
    }

    @Override public synchronized StoreResult clearPending(UUID id, long expectedRevision, Instant nextExpiresAt, Clock clock) {
        requireWrite(id, expectedRevision);
        StoreResult result = requireActive(owned.clearPending(id, expectedRevision, nextExpiresAt, clock),
                nextRevision(), clock, StoreResult.Outcome.UPDATED);
        revision = result.state().revision();
        return result;
    }

    @Override public synchronized StoreResult cancel(UUID id, long expectedRevision, Clock clock) {
        requireWrite(id, expectedRevision);
        StoreResult result = owned.cancel(id, expectedRevision, clock);
        requireOutcome(result, StoreResult.Outcome.CANCELLED);
        ConversationState state = result.state();
        // 消息strict view不替代公共cancel幂等入口，只接受本次明确取消产生的新revision。
        if (state == null || !sessionId.equals(state.sessionId()) || state.revision() != nextRevision()
                || state.status() != ConversationStatus.CANCELLED || state.isExpired(clock)) throw changed();
        revision = state.revision();
        return result;
    }

    private StoreResult requireActive(StoreResult result, long expectedRevision, Clock clock, StoreResult.Outcome success) {
        requireOutcome(result, success);
        ConversationState state = result.state();
        if (state == null || !sessionId.equals(state.sessionId()) || state.revision() != expectedRevision) throw changed();
        if (state.isExpired(clock)) throw new Failure(Code.SESSION_EXPIRED);
        if (state.status() != ConversationStatus.ACTIVE) throw new Failure(Code.SESSION_CANCELLED);
        return result;
    }

    private static void requireOutcome(StoreResult result, StoreResult.Outcome success) {
        if (result == null) throw changed();
        if (result.outcome() == success) return;
        throw new Failure(switch (result.outcome()) {
            case NOT_FOUND -> Code.SESSION_NOT_FOUND;
            case EXPIRED -> Code.SESSION_EXPIRED;
            case CANCELLED -> Code.SESSION_CANCELLED;
            default -> Code.SESSION_STATE_CHANGED;
        });
    }

    private void requireId(UUID id) { if (!sessionId.equals(id)) throw changed(); }
    private void requireWrite(UUID id, long expectedRevision) {
        requireId(id);
        if (expectedRevision != revision) throw changed();
    }
    private long nextRevision() {
        if (revision == Long.MAX_VALUE) throw changed();
        return revision + 1;
    }
    private static Failure changed() { return new Failure(Code.SESSION_STATE_CHANGED); }
}
