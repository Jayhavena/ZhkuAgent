package com.zhku.knowledge.observability;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/** 离线测试用容量界限，不代表持久化或数据保留政策。 */
public final class BoundedObservationBuffer implements ObservationSink, AutoCloseable {
    private final int capacity;
    private final ArrayDeque<ObservationEventV1> events = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong drops = new AtomicLong();
    private volatile boolean closed;

    public BoundedObservationBuffer() { this(32); }
    public BoundedObservationBuffer(int capacity) {
        if (capacity < 1 || capacity > 32) throw new IllegalArgumentException("容量必须为 1..32");
        this.capacity = capacity;
    }

    @Override public Delivery offer(ObservationEventV1 event) {
        Objects.requireNonNull(event);
        if (closed) return Delivery.DISABLED;
        // 争用时也丢新事件，不能为了观测而等待业务线程。
        if (!lock.tryLock()) { drops.incrementAndGet(); return Delivery.FULL; }
        try {
            if (closed) return Delivery.DISABLED;
            if (events.size() >= capacity) { drops.incrementAndGet(); return Delivery.FULL; }
            events.addLast(event); return Delivery.ACCEPTED;
        } finally { lock.unlock(); }
    }

    public long dropCount() { return drops.get(); }
    public List<ObservationEventV1> drain() {
        lock.lock();
        try { var copy = List.copyOf(events); events.clear(); return copy; }
        finally { lock.unlock(); }
    }
    @Override public void close() {
        lock.lock();
        try { closed = true; events.clear(); }
        finally { lock.unlock(); }
    }
}
