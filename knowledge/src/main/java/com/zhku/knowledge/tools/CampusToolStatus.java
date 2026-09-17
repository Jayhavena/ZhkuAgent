package com.zhku.knowledge.tools;

/** 工具调用的受控结果状态，不能把实现异常直接泄漏给模型调用方。 */
public enum CampusToolStatus {
    SUCCESS,
    INVALID_ARGUMENT,
    DATA_UNAVAILABLE,
    UNKNOWN_TOOL
}
