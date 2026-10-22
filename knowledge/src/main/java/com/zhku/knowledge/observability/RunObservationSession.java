package com.zhku.knowledge.observability;

import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** 测试夹具每 run 显式创建；不注册长期 run 表，不保存原始事件或诊断历史。 */
public final class RunObservationSession implements Consumer<AgentRunEvent>, AutoCloseable {
    private final UUID runId;
    private ObservationSink sink;
    private long lastSequence, producedCount, dropCount, deliveryFailureCount, duplicateCount;
    private boolean incomplete, terminatedObserved, closed;

    public record Status(long producedCount, long dropCount, long deliveryFailureCount, long duplicateCount,
            boolean incomplete, boolean terminatedObserved, boolean closed) { }

    public RunObservationSession(UUID runId, ObservationSink sink) {
        this.runId = Objects.requireNonNull(runId); this.sink = Objects.requireNonNull(sink);
    }

    @Override public synchronized void accept(AgentRunEvent event) {
        Objects.requireNonNull(event);
        if (!runId.equals(event.runId())) { incomplete = true; return; }
        if (closed) {
            if (terminatedObserved && event.type() == AgentRunEvent.Type.RUN_TERMINATED) duplicateCount++;
            return;
        }
        if (event.sequence() <= lastSequence) { duplicateCount++; incomplete = true; return; }
        if (event.sequence() - lastSequence != 1) incomplete = true;
        lastSequence = event.sequence();
        boolean terminal = event.type() == AgentRunEvent.Type.RUN_TERMINATED && event.runSnapshot().terminated();
        try {
            var metadata = ObservationProjector.anonymous(event);
            producedCount++;
            var delivery = sink.offer(metadata);
            if (delivery == ObservationSink.Delivery.FULL || delivery == ObservationSink.Delivery.DISABLED) {
                dropCount++; incomplete = true;
            } else if (delivery != ObservationSink.Delivery.ACCEPTED) {
                deliveryFailureCount++; incomplete = true;
            }
        } catch (RuntimeException failure) {
            // 仅包围新旁路，不接管 provider 调用或强制审计；失败不再写回同一 sink。
            deliveryFailureCount++; incomplete = true;
        } finally {
            if (terminal) { terminatedObserved = true; close(); }
        }
    }

    public synchronized Status status() {
        return new Status(producedCount, dropCount, deliveryFailureCount, duplicateCount, incomplete, terminatedObserved, closed);
    }
    @Override public synchronized void close() {
        if (!terminatedObserved) incomplete = true;
        closed = true; sink = null;
    }
}
