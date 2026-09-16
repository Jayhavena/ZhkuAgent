package com.zhku.knowledge.conversation;

/**
 * 会话的持久生命周期状态。
 *
 * <p>过期不是持久状态，而是由 {@code expiresAt} 与 {@link java.time.Clock} 推导，
 * 避免同一会话同时出现“状态仍有效、时间已到期”的两套事实。</p>
 */
public enum ConversationStatus {
    ACTIVE,
    CANCELLED
}
