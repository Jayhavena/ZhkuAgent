package com.zhku.knowledge.conversation.elective;

import java.util.Objects;

/** 会话编排的纯 Java 输出，不包含 HTTP 或页面语义。 */
public record ElectiveRetakeReply(
        ElectiveRetakeReplyType type,
        String message,
        ElectiveRetakeAnswer answer
) {

    public ElectiveRetakeReply {
        type = Objects.requireNonNull(type, "回复类型不能为空");
        message = requireText(message, "回复内容不能为空");
        if (type == ElectiveRetakeReplyType.ANSWER && answer == null) {
            throw new IllegalArgumentException("回答类型必须携带回答卡");
        }
        if (type != ElectiveRetakeReplyType.ANSWER && answer != null) {
            throw new IllegalArgumentException("非回答类型不能携带回答卡");
        }
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
