package com.zhku.knowledge.conversation.elective;

/** 阶段 4 应用服务的有限结果集合，HTTP 映射留待阶段 5。 */
public enum ElectiveRetakeReplyType {
    NEED_CLARIFICATION,
    ANSWER,
    EVIDENCE_UNAVAILABLE,
    SESSION_EXPIRED,
    SESSION_CANCELLED,
    SESSION_INVALID,
    OUT_OF_SCOPE
}
