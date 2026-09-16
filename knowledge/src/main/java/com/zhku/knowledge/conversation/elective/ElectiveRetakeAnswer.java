package com.zhku.knowledge.conversation.elective;

import java.util.Objects;
import java.util.UUID;

/** 学生可见回答卡，结论与来源快照不可分离。 */
public record ElectiveRetakeAnswer(
        String conclusion,
        String basis,
        UUID sourceId,
        String sourceTitle,
        String sourceVersion,
        String sourceLocator,
        String applicableScope
) {

    public ElectiveRetakeAnswer {
        conclusion = requireText(conclusion, "结论不能为空");
        basis = requireText(basis, "依据说明不能为空");
        sourceId = Objects.requireNonNull(sourceId, "资料来源编号不能为空");
        sourceTitle = requireText(sourceTitle, "资料标题不能为空");
        sourceVersion = requireText(sourceVersion, "资料版本不能为空");
        sourceLocator = requireText(sourceLocator, "资料定位不能为空");
        applicableScope = requireText(applicableScope, "适用范围不能为空");
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
