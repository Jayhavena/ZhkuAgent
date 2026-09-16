package com.zhku.knowledge.conversation;

import java.util.Objects;

/**
 * 会话存储操作的可预期结果。
 *
 * <p>重复创建、到期、取消和陈旧写回是并发会话中的正常业务分支，不应都被包装成异常。
 * {@code state} 在没有可见会话时可以为 {@code null}。</p>
 */
public record StoreResult(Outcome outcome, ConversationState state) {

    public StoreResult {
        outcome = Objects.requireNonNull(outcome, "存储结果不能为空");
    }

    public enum Outcome {
        CREATED,
        FOUND,
        UPDATED,
        ALREADY_EXISTS,
        NOT_FOUND,
        EXPIRED,
        CANCELLED,
        STALE_WRITE
    }
}
