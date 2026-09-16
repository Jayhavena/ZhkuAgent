package com.zhku.knowledge.publication;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 一次人工审核后的资料发布输入。
 *
 * <p>审核状态作为显式字段保留在输入中，使发布器能拒绝“待审核”资料，而不是依赖调用方记得先检查。</p>
 */
public record ReviewedKnowledgePublication(
        UUID sourceId,
        String sourceTitle,
        String sourceType,
        String sourceVersion,
        String sourceUrl,
        String publicationScope,
        String reviewNotice,
        boolean studentVisible,
        boolean sharingRightsConfirmed,
        String reviewStatus,
        Instant reviewedAt,
        List<ReviewedKnowledgeChunk> chunks
) {

    public static final String APPROVED_STATUS = "审核通过";

    public ReviewedKnowledgePublication {
        sourceId = Objects.requireNonNull(sourceId, "资料来源 ID 不能为空");
        sourceTitle = requireText(sourceTitle, "资料标题不能为空");
        sourceType = requireText(sourceType, "资料类型不能为空");
        sourceVersion = requireText(sourceVersion, "资料版本不能为空");
        sourceUrl = normalizeOptionalText(sourceUrl);
        publicationScope = requireText(publicationScope, "资料发布范围不能为空");
        reviewNotice = requireText(reviewNotice, "资料复核提示不能为空");
        reviewStatus = requireText(reviewStatus, "审核状态不能为空");
        reviewedAt = Objects.requireNonNull(reviewedAt, "审核时间不能为空");
        chunks = List.copyOf(Objects.requireNonNull(chunks, "知识片段不能为空"));
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个知识片段");
        }
        if (chunks.stream().map(ReviewedKnowledgeChunk::chunkIndex).distinct().count()
                != chunks.size()) {
            throw new IllegalArgumentException("同一资料内的片段序号不能重复");
        }
        // 允许用户上传资料公开，但必须让审核结论包含明确的分享权限确认。
        if (studentVisible && !sharingRightsConfirmed) {
            throw new IllegalArgumentException("学生可见资料必须确认分享权限");
        }
    }

    public void requireApproved() {
        if (!APPROVED_STATUS.equals(reviewStatus)) {
            throw new IllegalStateException("只有审核通过的资料才能发布");
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

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
