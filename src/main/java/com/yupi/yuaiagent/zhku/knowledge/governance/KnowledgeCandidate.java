package com.yupi.yuaiagent.zhku.knowledge.governance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 从 PDF 提取、等待人工确认的候选知识片段。
 */
public record KnowledgeCandidate(
        UUID id,
        UUID submissionId,
        int startPageNumber,
        int endPageNumber,
        String rawText,
        String editedText,
        String sectionTitle,
        String sourceLocator,
        CandidateReviewStatus reviewStatus,
        String reviewNote,
        String discardReason,
        Instant createdAt,
        Instant updatedAt
) {

    public KnowledgeCandidate {
        id = Objects.requireNonNull(id, "候选片段编号不能为空");
        submissionId = Objects.requireNonNull(submissionId, "投稿编号不能为空");
        if (startPageNumber < 1 || endPageNumber < startPageNumber) {
            throw new IllegalArgumentException("候选片段页码范围无效");
        }
        // PDF 的空白物理页也要进入审核队列，不能在提取阶段被静默丢弃。
        rawText = Objects.requireNonNull(rawText, "原始文本不能为空");
        editedText = normalizeOptionalText(editedText);
        sectionTitle = normalizeOptionalText(sectionTitle);
        sourceLocator = normalizeOptionalText(sourceLocator);
        reviewStatus = Objects.requireNonNull(reviewStatus, "候选片段状态不能为空");
        reviewNote = normalizeOptionalText(reviewNote);
        discardReason = normalizeOptionalText(discardReason);
        if (reviewStatus == CandidateReviewStatus.APPROVED) {
            requireText(editedText, "通过片段的编辑后文本不能为空");
            requireText(sectionTitle, "通过片段的片段标题不能为空");
            requireText(sourceLocator, "通过片段的资料定位不能为空");
            requireText(reviewNote, "通过片段的审核说明不能为空");
        }
        if (reviewStatus == CandidateReviewStatus.DISCARDED) {
            requireText(discardReason, "丢弃片段必须填写丢弃原因");
        }
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
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
