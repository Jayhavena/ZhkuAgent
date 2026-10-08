package com.zhku.agent.agent.runtime.contract;

import com.zhku.agent.agent.runtime.AgentRunState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 进程内运行事实；不替换 HTTP/SSE 协议，也不构成持久事件日志。
 */
public record AgentRunEvent(
        UUID runId,
        long sequence,
        Instant occurredAt,
        Type type,
        Integer step,
        AgentToolCall toolCall,
        AgentRunSnapshot runSnapshot,
        AgentRuntimeError error
) {

    public enum Type {
        RUN_STARTED,
        STEP_STARTED,
        TOOL_CALL_PROPOSED,
        TOOL_CALL_RESULT,
        STOP_REQUESTED,
        ERROR_RECORDED,
        RUN_TERMINATED
    }

    public AgentRunEvent {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(type);

        if (sequence < 1 || step != null && step < 1) {
            throw new IllegalArgumentException("事件序号非法");
        }

        if (toolCall != null && (!runId.equals(toolCall.runId()) || !Objects.equals(step, toolCall.step()))
                || runSnapshot != null && !runId.equals(runSnapshot.runId())) {
            throw new IllegalArgumentException("事件不能跨运行关联");
        }

        boolean valid = switch (type) {
            case TOOL_CALL_PROPOSED -> toolCall != null && toolCall.result() == null;
            case TOOL_CALL_RESULT -> toolCall != null && toolCall.result() != null;
            case STEP_STARTED -> step != null;
            case ERROR_RECORDED -> error != null;
            case RUN_TERMINATED -> runSnapshot != null && runSnapshot.terminated();
            case RUN_STARTED -> runSnapshot != null
                    && runSnapshot.state() == AgentRunState.RUNNING;
            case STOP_REQUESTED -> runSnapshot != null
                    && runSnapshot.state() == AgentRunState.STOP_REQUESTED;
        };

        if (!valid) {
            throw new IllegalArgumentException("事件载荷与类型不一致");
        }
    }
}
