package com.zhku.agent.agent.runtime.contract;

import java.util.Objects;

/** 只传受控分类，不传供应商异常或原始因果链。 */
public record AgentRuntimeError(Category category, String code, String safeMessage) {
    public enum Category {
        MODEL_FAILURE, TOOL_FAILURE, EXECUTOR_REJECTED, SEND_FAILURE,
        CLEANUP_FAILURE, EXECUTION_FAILURE, INTERNAL_FAILURE, DOMAIN_FAILURE
    }

    public AgentRuntimeError {
        Objects.requireNonNull(category, "错误分类不能为空");
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}"))
            throw new IllegalArgumentException("错误码必须是受控标识");
        if (!message(category).equals(safeMessage))
            throw new IllegalArgumentException("错误消息必须来自固定映射");
    }

    public static AgentRuntimeError of(Category category) {
        return of(category, category.name());
    }

    public static AgentRuntimeError of(Category category, String code) {
        return new AgentRuntimeError(category, code, message(category));
    }

    private static String message(Category category) {
        // 对外文本由固定分类生成，不拼接供应商异常，防止凭据或请求内容随错误泄漏。
        return switch (category) {
            case MODEL_FAILURE -> "模型调用失败";
            case TOOL_FAILURE -> "工具调用失败";
            case EXECUTOR_REJECTED -> "任务未获准执行";
            case SEND_FAILURE -> "结果发送失败";
            case CLEANUP_FAILURE -> "运行清理失败";
            case EXECUTION_FAILURE -> "任务执行失败";
            case INTERNAL_FAILURE -> "运行内部错误";
            case DOMAIN_FAILURE -> "领域服务调用失败";
        };
    }
}
