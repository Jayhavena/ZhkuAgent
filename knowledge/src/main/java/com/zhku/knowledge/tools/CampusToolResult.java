package com.zhku.knowledge.tools;

import java.util.Objects;

/** 工具的结构化返回值；边界提示必须随结果返回，避免调用方把计算误述为校务结论。 */
public record CampusToolResult(
        String toolName,
        CampusToolStatus status,
        String resultJson,
        String boundaryMessage
) {

    public CampusToolResult {
        toolName = requireText(toolName, "工具名称不能为空");
        status = Objects.requireNonNull(status, "工具状态不能为空");
        resultJson = requireText(resultJson, "工具结果不能为空");
        boundaryMessage = requireText(boundaryMessage, "工具边界提示不能为空");
    }

    public static CampusToolResult success(
            String toolName,
            String resultJson,
            String boundaryMessage
    ) {
        return new CampusToolResult(
                toolName,
                CampusToolStatus.SUCCESS,
                resultJson,
                boundaryMessage
        );
    }

    public static CampusToolResult failure(
            String toolName,
            CampusToolStatus status,
            String resultJson,
            String boundaryMessage
    ) {
        if (status == CampusToolStatus.SUCCESS) {
            throw new IllegalArgumentException("失败结果不能使用 SUCCESS 状态");
        }
        return new CampusToolResult(toolName, status, resultJson, boundaryMessage);
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
