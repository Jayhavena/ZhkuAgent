package com.zhku.knowledge.observability;

import com.zhku.agent.agent.runtime.contract.*;
import java.util.Objects;
import static com.zhku.knowledge.observability.ObservationEventV1.*;

public final class ObservationProjector {
    private ObservationProjector() { }

    public static ObservationEventV1 anonymous(AgentRunEvent source) {
        Objects.requireNonNull(source);
        Kind kind = switch (source.type()) {
            case RUN_STARTED -> Kind.RUN_STARTED;
            case STEP_STARTED -> Kind.STEP_STARTED;
            case TOOL_CALL_PROPOSED -> Kind.TOOL_PROPOSED;
            case TOOL_CALL_RESULT -> Kind.TOOL_RESULT;
            case STOP_REQUESTED -> Kind.STOP_REQUESTED;
            case ERROR_RECORDED -> Kind.ERROR;
            case RUN_TERMINATED -> Kind.RUN_TERMINATED;
        };
        Outcome outcome = Outcome.OBSERVED;
        AgentRuntimeError error = null;
        if (source.type() == AgentRunEvent.Type.TOOL_CALL_RESULT) {
            var result = source.toolCall().result();
            outcome = switch (result.status()) {
                case SUCCEEDED -> Outcome.SUCCEEDED; case REJECTED -> Outcome.REJECTED;
                case FAILED -> Outcome.FAILED; case CANCELLED -> Outcome.CANCELLED;
            };
            error = result.error();
        } else if (source.type() == AgentRunEvent.Type.RUN_TERMINATED) {
            outcome = switch (source.runSnapshot().state()) {
                case SUCCEEDED -> Outcome.SUCCEEDED; case FAILED -> Outcome.FAILED;
                case CANCELLED -> Outcome.CANCELLED; case INCOMPLETE -> Outcome.INCOMPLETE;
                case CREATED, RUNNING, STOP_REQUESTED -> Outcome.UNKNOWN;
            };
            error = source.runSnapshot().error();
        } else if (source.type() == AgentRunEvent.Type.ERROR_RECORDED) {
            error = source.error();
        }
        // 建议不是执行成功；工具名称与合法形态的错误码也可能编码身份，均不能透传。
        ToolClass tool = kind == Kind.TOOL_PROPOSED || kind == Kind.TOOL_RESULT ? ToolClass.OTHER : ToolClass.NONE;
        return new ObservationEventV1(1, kind, outcome, tool, failure(error), null, null, null, null);
    }

    public static RunDiagnosticEventV1 diagnostic(AgentRunEvent source) {
        var metadata = anonymous(source);
        boolean terminated = source.type() == AgentRunEvent.Type.RUN_TERMINATED && source.runSnapshot().terminated();
        return new RunDiagnosticEventV1(1, source.runId(), source.sequence(), terminated, metadata);
    }

    private static FailureClass failure(AgentRuntimeError error) {
        if (error == null) return FailureClass.NONE;
        return switch (error.category()) {
            case MODEL_FAILURE -> FailureClass.MODEL; case TOOL_FAILURE -> FailureClass.TOOL;
            case EXECUTOR_REJECTED -> FailureClass.EXECUTOR; case SEND_FAILURE -> FailureClass.SEND;
            case CLEANUP_FAILURE -> FailureClass.CLEANUP; case EXECUTION_FAILURE -> FailureClass.EXECUTION;
            case INTERNAL_FAILURE -> FailureClass.INTERNAL; case DOMAIN_FAILURE -> FailureClass.DOMAIN;
        };
    }
}
