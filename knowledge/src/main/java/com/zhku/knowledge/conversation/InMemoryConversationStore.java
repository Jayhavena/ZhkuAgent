package com.zhku.knowledge.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 基于内存的会话存储实现。
 *
 * <p>每个会话都保存不可变快照。写入通过条件替换提交：若校验期间有其他请求替换了同一快照，
 * 当前请求会重新读取并再次校验。这样不会出现“旧请求读到 ACTIVE，取消请求已写入，旧请求仍覆盖
 * 回 pending”的检查与写入分离风险。</p>
 */
public final class InMemoryConversationStore implements ConversationStore {

    private final ConcurrentHashMap<UUID, ConversationState> conversations = new ConcurrentHashMap<>();

    @Override
    public StoreResult create(ConversationState initialState) {
        Objects.requireNonNull(initialState, "初始会话不能为空");
        ConversationState existing = conversations.putIfAbsent(initialState.sessionId(), initialState);
        if (existing == null) {
            return new StoreResult(StoreResult.Outcome.CREATED, initialState);
        }
        return new StoreResult(StoreResult.Outcome.ALREADY_EXISTS, existing);
    }

    @Override
    public StoreResult find(UUID sessionId, Clock clock) {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        Objects.requireNonNull(clock, "时钟不能为空");
        ConversationState current = conversations.get(sessionId);
        if (current == null) {
            return new StoreResult(StoreResult.Outcome.NOT_FOUND, null);
        }
        if (current.isExpired(clock)) {
            return new StoreResult(StoreResult.Outcome.EXPIRED, current);
        }
        if (current.status() == ConversationStatus.CANCELLED) {
            return new StoreResult(StoreResult.Outcome.CANCELLED, current);
        }
        return new StoreResult(StoreResult.Outcome.FOUND, current);
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
        return updateActive(
                sessionId,
                expectedRevision,
                clock,
                state -> state.withPending(pendingIntent, pendingField, nextExpiresAt)
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
        return updateActive(
                sessionId,
                expectedRevision,
                clock,
                state -> state.clearPending(nextExpiresAt)
        );
    }

    @Override
    public StoreResult cancel(UUID sessionId, long expectedRevision, Clock clock) {
        validateWriteArguments(sessionId, expectedRevision, clock);
        while (true) {
            ConversationState current = conversations.get(sessionId);
            if (current == null) {
                return new StoreResult(StoreResult.Outcome.NOT_FOUND, null);
            }
            if (current.isExpired(clock)) {
                return new StoreResult(StoreResult.Outcome.EXPIRED, current);
            }
            if (current.status() == ConversationStatus.CANCELLED) {
                return new StoreResult(StoreResult.Outcome.CANCELLED, current);
            }
            if (current.revision() != expectedRevision) {
                return new StoreResult(StoreResult.Outcome.STALE_WRITE, current);
            }

            ConversationState cancelled = current.cancel();
            if (conversations.replace(sessionId, current, cancelled)) {
                return new StoreResult(StoreResult.Outcome.CANCELLED, cancelled);
            }
        }
    }

    @Override
    public int clearExpired(Clock clock) {
        Objects.requireNonNull(clock, "时钟不能为空");
        int removed = 0;
        for (var entry : conversations.entrySet()) {
            ConversationState current = entry.getValue();
            // 条件删除避免清理线程删掉并发请求刚写入的新快照。
            if (current.isExpired(clock) && conversations.remove(entry.getKey(), current)) {
                removed++;
            }
        }
        return removed;
    }

    private StoreResult updateActive(
            UUID sessionId,
            long expectedRevision,
            Clock clock,
            Function<ConversationState, ConversationState> transition
    ) {
        validateWriteArguments(sessionId, expectedRevision, clock);
        Objects.requireNonNull(transition, "状态转换不能为空");
        while (true) {
            ConversationState current = conversations.get(sessionId);
            if (current == null) {
                return new StoreResult(StoreResult.Outcome.NOT_FOUND, null);
            }
            if (current.isExpired(clock)) {
                return new StoreResult(StoreResult.Outcome.EXPIRED, current);
            }
            if (current.status() == ConversationStatus.CANCELLED) {
                return new StoreResult(StoreResult.Outcome.CANCELLED, current);
            }
            if (current.revision() != expectedRevision) {
                return new StoreResult(StoreResult.Outcome.STALE_WRITE, current);
            }

            ConversationState updated = transition.apply(current);
            if (conversations.replace(sessionId, current, updated)) {
                return new StoreResult(StoreResult.Outcome.UPDATED, updated);
            }
        }
    }

    private static void validateWriteArguments(UUID sessionId, long expectedRevision, Clock clock) {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        Objects.requireNonNull(clock, "时钟不能为空");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("期望版本不能为负数");
        }
    }
}
