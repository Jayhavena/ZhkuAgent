package com.zhku.agent.agent.model;

import org.springframework.ai.chat.model.ChatResponse;

import java.util.Objects;

/**
 * 工具调用步骤的不可变决策。
 *
 * @param response  think 阶段得到的本步骤模型响应
 * @param shouldAct 当前响应是否要求执行工具
 */
public record ToolCallDecision(
        ChatResponse response,
        boolean shouldAct
) implements AgentStepDecision {

    /**
     * record 的紧凑构造器，用于保护决策对象的不变量。
     */
    public ToolCallDecision {
        Objects.requireNonNull(response, "response 不能为空");
    }
}