package com.zhku.knowledge.observability;

import com.zhku.agent.agent.runtime.contract.AgentRunEvent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** 匿名旁路可跨 Run 共享；不维护运行身份、顺序、完成状态或原始事件历史。 */
public final class AnonymousRuntimeObserver implements Consumer<AgentRunEvent> {
    private final ObservationSink sink;
    private final AtomicLong acceptedCount = new AtomicLong();
    private final AtomicLong dropCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();

    /** 固定聚合计数不代表完整 Trace；并发读取各计数不是同一时刻的原子快照。 */
    public record Status(long acceptedCount, long dropCount, long failureCount) { }

    public AnonymousRuntimeObserver(ObservationSink sink) {
        this.sink = Objects.requireNonNull(sink);
    }

    public static AnonymousRuntimeObserver noop() {
        return new AnonymousRuntimeObserver(ObservationSink.noop());
    }

    @Override public void accept(AgentRunEvent event) {
        try {
            // 原始事件只能经过现有白名单投影；不得把身份或内容传给 sink。
            var delivery = sink.offer(ObservationProjector.anonymous(event));
            if (delivery == ObservationSink.Delivery.ACCEPTED) increment(acceptedCount);
            else if (delivery == ObservationSink.Delivery.FULL || delivery == ObservationSink.Delivery.DISABLED)
                increment(dropCount);
            else increment(failureCount);
        } catch (RuntimeException failure) {
            // 仅隔离投影/投递旁路，不保留异常、不递归上报，也不接管 provider 强制审计。
            increment(failureCount);
        }
    }

    public Status status() {
        return new Status(acceptedCount.get(), dropCount.get(), failureCount.get());
    }

    private static void increment(AtomicLong counter) {
        // 长期共享时饱和而不溢出，避免聚合计数回绕成负数。
        counter.updateAndGet(value -> value == Long.MAX_VALUE ? value : value + 1);
    }
}
