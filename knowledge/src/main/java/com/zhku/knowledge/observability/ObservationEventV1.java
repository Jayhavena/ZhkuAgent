package com.zhku.knowledge.observability;

import java.util.Objects;

/** 匿名元数据不提供关联或自由文本槽位，避免调用方误把内容编码为标签。 */
public record ObservationEventV1(int schemaVersion, Kind kind, Outcome outcome, ToolClass toolClass,
        FailureClass failureClass, Long inputTokens, Long outputTokens, Long elapsedMillis,
        Long estimatedCostMicros) {
    public enum Kind { RUN_STARTED, STEP_STARTED, TOOL_PROPOSED, TOOL_RESULT, STOP_REQUESTED, ERROR, RUN_TERMINATED }
    public enum Outcome { UNKNOWN, OBSERVED, SUCCEEDED, REJECTED, FAILED, CANCELLED, INCOMPLETE }
    public enum ToolClass { NONE, OTHER }
    public enum FailureClass { NONE, MODEL, TOOL, EXECUTOR, SEND, CLEANUP, EXECUTION, INTERNAL, DOMAIN, OTHER }

    public ObservationEventV1 {
        if (schemaVersion != 1) throw new IllegalArgumentException("不支持的元数据版本");
        Objects.requireNonNull(kind); Objects.requireNonNull(outcome);
        Objects.requireNonNull(toolClass); Objects.requireNonNull(failureClass);
        nonNegative(inputTokens); nonNegative(outputTokens); nonNegative(elapsedMillis); nonNegative(estimatedCostMicros);
    }

    private static void nonNegative(Long value) {
        // null 是未知，不能补零；只有显式测得的零才允许成为零。
        if (value != null && value < 0) throw new IllegalArgumentException("指标不能为负数");
    }
}
