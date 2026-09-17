package com.zhku.knowledge.retrieval;

import java.util.Objects;

/**
 * 检索资格判定结论。
 *
 * @param eligible 是否允许进入候选集/模型上下文
 * @param reason 拒绝原因（面向审计与治理台展示）；eligible 为 true 时为 null
 */
public record EligibilityDecision(boolean eligible, String reason) {

    // 命名避开组件访问器 eligible()：记录组件与静态工厂同名同参会冲突。
    public static EligibilityDecision accepted() {
        return new EligibilityDecision(true, null);
    }

    public static EligibilityDecision rejected(String reason) {
        Objects.requireNonNull(reason, "拒绝原因不能为空");
        if (reason.isBlank()) {
            // 拒绝必须留原因：无原因的拒绝对审计不可解释，属于编程错误。
            throw new IllegalArgumentException("拒绝原因不能为空");
        }
        return new EligibilityDecision(false, reason);
    }
}
