package com.zhku.agent.agent.model;

/** 模型和工具的异常原文可能含凭据；跨运行边界只传固定分类且不携带原始因果链。 */
public final class AgentExecutionFailure extends IllegalStateException {
    public enum Category {
        MODEL_FAILURE, TOOL_FAILURE, CLEANUP_FAILURE
    }

    private final Category category;

    public AgentExecutionFailure(Category category) {
        super(category.name());
        this.category = category;
    }

    public Category category() {
        return category;
    }

    public com.zhku.agent.agent.runtime.contract.AgentRuntimeError runtimeError() {
        return com.zhku.agent.agent.runtime.contract.AgentRuntimeError.of(
                com.zhku.agent.agent.runtime.contract.AgentRuntimeError.Category.valueOf(category.name()));
    }
}
