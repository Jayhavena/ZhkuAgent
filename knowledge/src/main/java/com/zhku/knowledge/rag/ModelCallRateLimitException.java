package com.zhku.knowledge.rag;

/** 模型调用被限流。触发安全降级；重试策略属于部署决策，不在生成层硬编码。 */
public class ModelCallRateLimitException extends RuntimeException {

    public ModelCallRateLimitException(String message) {
        super(message);
    }

    public ModelCallRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
