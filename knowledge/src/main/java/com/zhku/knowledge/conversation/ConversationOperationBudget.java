package com.zhku.knowledge.conversation;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** 一次消息共用单调deadline；逐层重新计时会把5秒边界放大为多次2秒。 */
public final class ConversationOperationBudget {
    private static final Duration REQUEST_MAXIMUM = Duration.ofSeconds(5);
    private final long started = System.nanoTime();
    private final long durationNanos;
    private int operations;

    public ConversationOperationBudget() { this(REQUEST_MAXIMUM); }

    public ConversationOperationBudget(Duration remaining) {
        Objects.requireNonNull(remaining, "请求预算不能为空");
        if (remaining.isNegative() || remaining.compareTo(REQUEST_MAXIMUM) > 0)
            throw new IllegalArgumentException("请求预算须在0至5秒内");
        durationNanos = remaining.toNanos();
    }

    public void check() {
        if (remainingNanos() <= 0 || Thread.currentThread().isInterrupted())
            throw new ConversationStoreFailure(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
    }

    public long remainingNanos() { return durationNanos - (System.nanoTime() - started); }

    synchronized Operation begin() {
        check();
        if (operations >= 12) throw new ConversationStoreFailure(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
        operations++;
        return new Operation(Math.min(TimeUnit.SECONDS.toNanos(2), remainingNanos()));
    }

    /** 单操作deadline从准入前开始，连接建立、锁、SQL和commit共用，清理另有2秒边界。 */
    static final class Operation {
        private final long started = System.nanoTime();
        private final long durationNanos;
        Operation(long durationNanos) { this.durationNanos = durationNanos; }
        long remainingNanos() { return durationNanos - (System.nanoTime() - started); }
        void check() {
            if (remainingNanos() <= 0 || Thread.currentThread().isInterrupted())
                throw new ConversationStoreFailure(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
        }
    }
}
