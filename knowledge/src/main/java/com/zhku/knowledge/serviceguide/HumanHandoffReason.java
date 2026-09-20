package com.zhku.knowledge.serviceguide;

/**
 * 转人工的原因。四类覆盖计划要求的全部入口，且刻意与"具体哪个渠道"解耦——
 * 渠道选择由 {@link HumanHandoffRoute} 按已审核渠道集合完成，这里只表达"为什么要转"。
 */
public enum HumanHandoffReason {
    /** 证据冲突：通知/规则与清单给出的事实互相矛盾，系统不得替学生裁决。 */
    EVIDENCE_CONFLICT,
    /** 个案认定：涉及个人学分、成绩、资格等个案，须由教务/学院认定。 */
    INDIVIDUAL_ASSESSMENT,
    /** 规则缺失：没有当前有效证据（未发布、过期、被替代或清单不完整）。 */
    RULE_MISSING,
    /** 模型拒答：生成侧明确拒绝作答或降级，只能转人工。 */
    MODEL_REFUSAL
}
