package com.zhku.knowledge.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 会话存储契约。
 *
 * <p>写入操作显式携带 {@code expectedRevision}，让实现能够把“检查当前状态”和
 * “写入新状态”做成原子步骤，从而阻止取消后的旧请求写回待补信息。阶段 1 只定义契约；
 * 阶段 3 才提供 {@code InMemoryConversationStore} 实现。</p>
 */
public interface ConversationStore {

    StoreResult create(ConversationState initialState);

    StoreResult find(UUID sessionId, Clock clock);

    StoreResult savePending(
            UUID sessionId,
            long expectedRevision,
            PendingIntent pendingIntent,
            PendingField pendingField,
            Instant nextExpiresAt,
            Clock clock
    );

    StoreResult clearPending(
            UUID sessionId,
            long expectedRevision,
            Instant nextExpiresAt,
            Clock clock
    );

    StoreResult cancel(UUID sessionId, long expectedRevision, Clock clock);

    int clearExpired(Clock clock);
}
