package com.zhku.knowledge.feedback;

/** 可枚举的反馈原因；不接受自由文本，避免反馈入口变成身份或问答内容的旁路。 */
public enum FeedbackReason {
    NOT_HELPFUL,
    INCORRECT,
    OUTDATED,
    MISSING_INFORMATION
}
