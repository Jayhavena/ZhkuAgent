package com.zhku.knowledge.rag;

/** 模型输出结构非法（非约定 JSON、缺字段等）。触发安全降级。 */
public class ModelOutputFormatException extends RuntimeException {

    public ModelOutputFormatException(String message) {
        super(message);
    }

    public ModelOutputFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
