package com.zhku.agent.agent.runtime.contract;

import java.util.Objects;
import java.util.UUID;

/** result 为 null 只表示结果未被观测；不能据此推断工具已经执行或停止。 */
public record AgentToolCall(UUID runId, String callId, String providerCallId, int step,
        String toolName, String argumentsJson, Result result) {
    public enum Status {
        SUCCEEDED, REJECTED, FAILED, CANCELLED
    }

    public enum PayloadFormat {
        TEXT, JSON
    }

    public record Result(Status status, String payload, PayloadFormat payloadFormat, AgentRuntimeError error) {
        public Result {
            Objects.requireNonNull(status, "工具结果状态不能为空");
            // 载荷类型必须与正文成对出现，避免下游把缺失结果误读成某种格式的空答案。
            if ((payload == null) != (payloadFormat == null))
                throw new IllegalArgumentException("载荷和格式必须同时存在");
            if (status == Status.SUCCEEDED && (payload == null || error != null)
                    || (status == Status.FAILED || status == Status.REJECTED) && error == null
                    || status == Status.CANCELLED && error != null)
                throw new IllegalArgumentException("工具状态与结果不一致");
        }
    }

    public AgentToolCall {
        Objects.requireNonNull(runId, "运行编号不能为空");
        if (callId == null || callId.isBlank() || step < 1)
            throw new IllegalArgumentException("工具调用关联信息不完整");
        // 提议是未校验的模型原文：名称或参数缺失也要能表达，随后由宿主守卫拒绝。
        // Runtime 若抢先抛错，会把 UNKNOWN_TOOL/INVALID_TOOL_ARGUMENT 错改为内部故障。
    }

    public AgentToolCall withResult(Result value) {
        // 结果只能追加一次；重试或重复回调不能覆盖已经观测到的工具事实。
        if (result != null)
            throw new IllegalStateException("不能覆盖已观测工具结果");
        return new AgentToolCall(runId, callId, providerCallId, step, toolName, argumentsJson,
                Objects.requireNonNull(value));
    }
}
