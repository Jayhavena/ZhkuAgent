package com.zhku.knowledge.governance;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 创建本机学生手册投稿时必须由管理员确认的元数据。
 */
public record SubmissionCreateCommand(
        String title,
        String sourceVersion,
        String sourceDescription,
        String sourceUrl,
        String applicableScope,
        LocalDate publicationDate,
        boolean publicationDateConfirmed,
        LocalDate effectiveDate,
        boolean effectiveDateConfirmed,
        String reviewNotice,
        String knowledgeScope,
        String originalFilename
) {

    private static final Pattern KNOWLEDGE_SCOPE_PATTERN =
            Pattern.compile("^[A-Z0-9_]+$");

    public SubmissionCreateCommand {
        title = requireText(title, "资料标题不能为空");
        sourceVersion = requireText(sourceVersion, "资料版本不能为空");
        sourceDescription = requireText(sourceDescription, "来源说明不能为空");
        sourceUrl = normalizeOptionalText(sourceUrl);
        applicableScope = requireText(applicableScope, "适用范围不能为空");
        if (publicationDate == null && publicationDateConfirmed) {
            throw new IllegalArgumentException("发布日期为空时不能标记为已确认");
        }
        if (effectiveDate == null && effectiveDateConfirmed) {
            throw new IllegalArgumentException("生效日期为空时不能标记为已确认");
        }
        reviewNotice = requireText(reviewNotice, "审核提示不能为空");
        knowledgeScope = requireText(knowledgeScope, "知识范围不能为空");
        if (!KNOWLEDGE_SCOPE_PATTERN.matcher(knowledgeScope).matches()) {
            throw new IllegalArgumentException(
                    "知识范围只能包含大写字母、数字和下划线"
            );
        }
        originalFilename = requireText(originalFilename, "原始文件名不能为空");
    }

    private static String requireText(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
