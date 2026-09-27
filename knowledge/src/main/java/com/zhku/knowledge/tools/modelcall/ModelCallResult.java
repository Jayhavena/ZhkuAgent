package com.zhku.knowledge.tools.modelcall;

/** 只保留演示所需的结构化结果，不保存原始问题、模型回复或身份。 */
public record ModelCallResult(
        ModelCallStatus status,
        String toolName,
        String resultJson,
        String boundaryMessage
) {
}
