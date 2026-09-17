package com.zhku.knowledge.rag;

/** 模型调用超时。触发安全降级，不向学生返回半成品回答。 */
public class ModelCallTimeoutException extends RuntimeException {

    public ModelCallTimeoutException(String message) {
        super(message);
    }

    public ModelCallTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
