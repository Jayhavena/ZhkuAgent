package com.zhku.knowledge.retrieval;

/**
 * 单路检索不可用（未配置、依赖缺失、外部服务故障）。
 *
 * <p>与"命中为空"严格区分：空命中是正常结果，不可用则触发单路降级并
 * 在审计中标记该路缺失，避免把降级运行当成"检索效果不好"来解读。</p>
 */
public class RetrieverUnavailableException extends RuntimeException {

    public RetrieverUnavailableException(String message) {
        super(message);
    }

    public RetrieverUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
