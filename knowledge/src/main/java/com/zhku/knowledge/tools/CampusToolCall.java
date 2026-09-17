package com.zhku.knowledge.tools;

import java.util.Objects;

/** 由未来编排器传入的单次工具调用，不携带会话或任何学生身份信息。 */
public record CampusToolCall(String toolName, String argumentsJson) {

    public CampusToolCall {
        toolName = requireText(toolName, "工具名称不能为空");
        argumentsJson = requireText(argumentsJson, "工具参数不能为空");
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
