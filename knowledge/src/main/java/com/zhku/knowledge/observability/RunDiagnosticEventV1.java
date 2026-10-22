package com.zhku.knowledge.observability;

import java.util.Objects;
import java.util.UUID;

/** 仅离线合成夹具显式使用；不能送入匿名 sink 或通用日志。 */
public record RunDiagnosticEventV1(int schemaVersion, UUID runId, long sequence, boolean terminated,
        ObservationEventV1 metadata) {
    public RunDiagnosticEventV1 {
        Objects.requireNonNull(runId); Objects.requireNonNull(metadata);
        if (schemaVersion != 1 || sequence < 1 || terminated && metadata.kind() != ObservationEventV1.Kind.RUN_TERMINATED)
            throw new IllegalArgumentException("诊断事实不一致");
    }

    // 即使误传 logger，也不使用 record 默认 toString 暴露关联标识；无诊断日志入口。
    @Override public String toString() { return "RunDiagnosticEventV1[REDACTED]"; }
}
