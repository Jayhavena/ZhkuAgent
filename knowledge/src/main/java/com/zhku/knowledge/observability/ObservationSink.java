package com.zhku.knowledge.observability;

@FunctionalInterface
public interface ObservationSink {
    enum Delivery { ACCEPTED, DISABLED, FULL, FAILED }
    Delivery offer(ObservationEventV1 event);

    /** 默认丢弃，不登记 observer、保留引用或创建任何资源。 */
    static ObservationSink noop() { return event -> Delivery.DISABLED; }
}
