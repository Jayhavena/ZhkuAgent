package com.zhku.knowledge.conversation.publicelective;

import java.util.Objects;
import java.util.UUID;

/** 已发布且学生可见的公共选修课单学期学分上限证据快照。 */
public record CreditLimitEvidence(
        UUID sourceId,
        String sourceTitle,
        String sourceVersion,
        String sourceLocator,
        String applicableScope,
        String content
) {

    public CreditLimitEvidence {
        sourceId = Objects.requireNonNull(sourceId, "资料来源编号不能为空");
        sourceTitle = requireText(sourceTitle, "资料标题不能为空");
        sourceVersion = requireText(sourceVersion, "资料版本不能为空");
        sourceLocator = requireText(sourceLocator, "资料定位不能为空");
        applicableScope = requireText(applicableScope, "适用范围不能为空");
        content = requireText(content, "证据正文不能为空");
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
