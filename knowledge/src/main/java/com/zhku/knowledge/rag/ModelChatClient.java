package com.zhku.knowledge.rag;

/**
 * provider-neutral 的模型调用抽象。
 *
 * <p>实现方负责把 {@link ModelRequest} 翻译为具体供应商协议（OpenAI-compatible
 * 等）并把响应解析为 {@link ModelResponse}；解析失败、超时、限流分别以
 * {@link ModelOutputFormatException}、{@link ModelCallTimeoutException}、
 * {@link ModelCallRateLimitException} 抛出，由生成层统一降级。</p>
 */
@FunctionalInterface
public interface ModelChatClient {

    ModelResponse call(ModelRequest request);
}
