package com.zhku.knowledge.conversation.elective;

import java.util.Objects;
import java.util.UUID;

/** 已发布且学生可见的制度证据快照。 */
public record ElectiveRetakeEvidence(
        UUID sourceId,
        String sourceTitle,
        String sourceVersion,
        String sourceLocator,
        String applicableScope,
        String content
) {

    public ElectiveRetakeEvidence {
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
