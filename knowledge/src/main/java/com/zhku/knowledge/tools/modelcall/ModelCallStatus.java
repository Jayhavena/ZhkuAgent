package com.zhku.knowledge.tools.modelcall;

/** 本机演示的受控状态；失败时绝不附带猜测的学分。 */
public enum ModelCallStatus {
    SUCCESS,
    UNKNOWN_TOOL,
    INVALID_ARGUMENT,
    NO_TOOL_CALL,
    TOOL_CALL_LIMIT,
    MODEL_TIMEOUT,
    MODEL_FAILURE
}
