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
        String originalFilename,
        String documentFamily
) {

    private static final Pattern KNOWLEDGE_SCOPE_PATTERN =
            Pattern.compile("^[A-Z0-9_]+$");
    private static final Pattern DOCUMENT_FAMILY_PATTERN =
            Pattern.compile("^(STUDENT_HANDBOOK|OFFICIAL_NOTICE)$");

    /** 保持旧管理员调用兼容；通知投稿必须由入口显式声明资料族。 */
    public SubmissionCreateCommand(
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
        this(
                title, sourceVersion, sourceDescription, sourceUrl,
                applicableScope, publicationDate, publicationDateConfirmed,
                effectiveDate, effectiveDateConfirmed, reviewNotice,
                knowledgeScope, originalFilename, "STUDENT_HANDBOOK"
        );
    }

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
        documentFamily = requireText(documentFamily, "资料族不能为空");
        if (!DOCUMENT_FAMILY_PATTERN.matcher(documentFamily).matches()) {
            throw new IllegalArgumentException("资料族只能是 STUDENT_HANDBOOK 或 OFFICIAL_NOTICE");
        }
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
