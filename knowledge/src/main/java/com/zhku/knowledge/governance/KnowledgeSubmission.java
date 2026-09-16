package com.zhku.knowledge.governance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 一份等待审核并发布的本机学生手册资料。
 *
 * <p>该对象属于暂存区，不能直接被学生问答检索。</p>
 */
public record KnowledgeSubmission(
        UUID id,
        String documentFamily,
        String knowledgeScope,
        String title,
        String sourceVersion,
        String sourceDescription,
        String sourceUrl,
        String applicableScope,
        LocalDate publicationDate,
        boolean publicationDateConfirmed,
        LocalDate effectiveDate,
        boolean effectiveDateConfirmed,
        String storageKey,
        String originalFileName,
        String contentChecksum,
        KnowledgeSubmissionStatus status,
        String reviewNotice,
        Instant createdAt,
        Instant updatedAt
) {

    private static final Pattern KNOWLEDGE_SCOPE_PATTERN =
            Pattern.compile("^[A-Z0-9_]+$");

    public KnowledgeSubmission {
        id = Objects.requireNonNull(id, "投稿编号不能为空");
        documentFamily = requireText(documentFamily, "资料族不能为空");
        if (!"STUDENT_HANDBOOK".equals(documentFamily)) {
            throw new IllegalArgumentException(
                    "资料族只能是 STUDENT_HANDBOOK"
            );
        }
        knowledgeScope = requireText(knowledgeScope, "知识范围不能为空");
        if (!KNOWLEDGE_SCOPE_PATTERN.matcher(knowledgeScope).matches()) {
            throw new IllegalArgumentException(
                    "知识范围只能包含大写字母、数字和下划线"
            );
        }
        title = requireText(title, "资料标题不能为空");
        sourceVersion = requireText(sourceVersion, "资料版本不能为空");
        sourceDescription = requireText(sourceDescription, "来源说明不能为空");
        sourceUrl = normalizeOptionalText(sourceUrl);
        applicableScope = requireText(applicableScope, "适用范围不能为空");
        if (publicationDate == null && publicationDateConfirmed) {
            throw new IllegalArgumentException(
                    "发布日期为空时不能标记为已确认"
            );
        }
        if (effectiveDate == null && effectiveDateConfirmed) {
            throw new IllegalArgumentException(
                    "生效日期为空时不能标记为已确认"
            );
        }
        storageKey = requireText(storageKey, "本机文件标识不能为空");
        originalFileName = requireText(originalFileName, "原始文件名不能为空");
        contentChecksum = requireChecksum(contentChecksum);
        status = Objects.requireNonNull(status, "投稿状态不能为空");
        reviewNotice = requireText(reviewNotice, "审核提示不能为空");
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    private static String requireChecksum(String value) {
        String checksum = requireText(value, "文件摘要不能为空");
        if (!checksum.matches("^[A-F0-9]{64}$")) {
            throw new IllegalArgumentException(
                    "文件摘要必须是 64 位大写十六进制"
            );
        }
        return checksum;
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
