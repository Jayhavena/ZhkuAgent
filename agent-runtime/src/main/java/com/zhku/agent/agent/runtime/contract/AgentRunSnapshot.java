package com.zhku.agent.agent.runtime.contract;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import java.util.Objects;
import java.util.UUID;

/** 生命周期事实；不包含业务答案、会话、身份或政策证据。 */
public record AgentRunSnapshot(UUID runId, AgentRunState state, AgentStopReason stopReason,
        int currentStep, boolean terminated, EndReason endReason, AgentRuntimeError error) {
    public enum EndReason {
        COMPLETED, STEP_LIMIT, BLOCKED, DOMAIN_DECISION, STOPPED, ERROR
    }

    public AgentRunSnapshot {
        Objects.requireNonNull(runId, "运行编号不能为空");
        Objects.requireNonNull(state, "运行状态不能为空");
        Objects.requireNonNull(stopReason, "停止原因不能为空");
        if (currentStep < 0 || (terminated && !state.isTerminal()))
            throw new IllegalArgumentException("步骤或终止标记非法");
        if ((state == AgentRunState.CREATED || state == AgentRunState.RUNNING
                || state == AgentRunState.SUCCEEDED || state == AgentRunState.INCOMPLETE)
                && stopReason != AgentStopReason.NONE
                || state == AgentRunState.STOP_REQUESTED && stopReason == AgentStopReason.NONE)
            throw new IllegalArgumentException("运行状态与停止原因不一致");
        // 状态、停止原因、结束原因和错误必须整体一致，不能只凭模型文本宣称任务成功。
        boolean valid = switch (state) {
            case CREATED, RUNNING, STOP_REQUESTED -> endReason == null && error == null;
            case SUCCEEDED -> endReason == EndReason.COMPLETED && error == null;
            case INCOMPLETE -> (endReason == EndReason.STEP_LIMIT || endReason == EndReason.BLOCKED
                    || endReason == EndReason.DOMAIN_DECISION) && error == null;
            case CANCELLED -> endReason == EndReason.STOPPED && error == null && stopReason != AgentStopReason.NONE;
            case FAILED -> endReason == EndReason.ERROR && error != null;
        };
        if (!valid)
            throw new IllegalArgumentException("运行状态与结束事实不一致");
    }
}
