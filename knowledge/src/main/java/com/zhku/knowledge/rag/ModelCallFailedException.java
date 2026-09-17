package com.zhku.knowledge.rag;

/**
 * 模型调用失败（非限流、非超时的其他错误：服务端 5xx、连接失败、协议不符）。
 *
 * <p>与限流/超时区分开是为了诊断口径；对生成层而言三者同样触发安全降级。</p>
 */
public class ModelCallFailedException extends RuntimeException {

    public ModelCallFailedException(String message) {
        super(message);
    }

    public ModelCallFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
