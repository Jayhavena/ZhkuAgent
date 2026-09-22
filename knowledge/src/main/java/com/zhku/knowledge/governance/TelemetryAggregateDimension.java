package com.zhku.knowledge.governance;

import java.util.Arrays;

/**
 * 可公开的匿名遥测聚合维度。
 *
 * <p>SQL 列名不能由请求参数直接决定；把可选项收敛为枚举，既固定了管理端契约，
 * 也避免聚合查询退化为可注入的动态 SQL。</p>
 */
public enum TelemetryAggregateDimension {

    ROUTE("route", "route"),
    FAILURE_CATEGORY("failureCategory", "failure_category"),
    HANDOFF_REQUIRED("handoffRequired", "handoff_required"),
    LATENCY_BUCKET("latencyBucket", "latency_bucket"),
    EVENT_DATE("eventDate", "event_date");

    private final String requestValue;
    private final String columnName;

    TelemetryAggregateDimension(String requestValue, String columnName) {
        this.requestValue = requestValue;
        this.columnName = columnName;
    }

    public String requestValue() {
        return requestValue;
    }

    public String columnName() {
        return columnName;
    }

    public static TelemetryAggregateDimension fromRequest(String requestValue) {
        return Arrays.stream(values())
                .filter(dimension -> dimension.requestValue.equals(requestValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的遥测聚合维度：" + requestValue));
    }
}
