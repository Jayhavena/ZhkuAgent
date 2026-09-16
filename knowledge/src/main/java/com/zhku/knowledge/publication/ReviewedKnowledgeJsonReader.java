package com.zhku.knowledge.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将人工审核清单转换为可发布的知识输入。
 *
 * <p>不直接读取原始 PDF，而是只读取已审核的候选片段文件；这样可避免提取出的未审核文本绕过审核流程进入数据库。</p>
 */
public final class ReviewedKnowledgeJsonReader {

    private static final Pattern FIRST_PDF_PAGE = Pattern.compile(
            "PDF\\s*第\\s*(\\d+)"
    );

    private final ObjectMapper objectMapper;

    public ReviewedKnowledgeJsonReader() {
        this(new ObjectMapper());
    }

    ReviewedKnowledgeJsonReader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 读取器不能为空");
    }

    public ReviewedKnowledgePublication read(Path reviewedCandidateFile)
            throws IOException {
        Objects.requireNonNull(reviewedCandidateFile, "审核文件路径不能为空");
        if (!Files.isRegularFile(reviewedCandidateFile)) {
            throw new IllegalArgumentException(
                    "审核文件不存在或不是普通文件：" + reviewedCandidateFile
            );
        }

        JsonNode root = objectMapper.readTree(reviewedCandidateFile.toFile());
        String reviewStatus = requiredText(root, "review_status");
        if (!ReviewedKnowledgePublication.APPROVED_STATUS.equals(reviewStatus)) {
            throw new IllegalStateException("只有审核通过的候选资料才能导入");
        }
        boolean studentVisible = requiredBoolean(root, "student_visible");
        boolean sharingRightsConfirmed = requiredBoolean(
                root,
                "sharing_rights_confirmed"
        );
        // 审核通过只说明内容可用；向全体学生公开前还必须确认上传者拥有分享权限。
        if (studentVisible && !sharingRightsConfirmed) {
            throw new IllegalArgumentException(
                    "学生可见资料必须确认分享权限"
            );
        }

        List<ReviewedKnowledgeChunk> chunks = readChunks(root.path("candidate_chunks"));
        return new ReviewedKnowledgePublication(
                UUID.fromString(requiredText(root, "source_id")),
                requiredText(root, "source_title"),
                requiredText(root, "source_type"),
                requiredText(root, "source_version"),
                optionalText(root, "source_url"),
                requiredText(root, "publication_scope"),
                requiredText(root, "review_notice"),
                studentVisible,
                sharingRightsConfirmed,
                reviewStatus,
                Instant.parse(requiredText(root, "reviewed_at")),
                chunks
        );
    }

    private List<ReviewedKnowledgeChunk> readChunks(JsonNode candidateChunks) {
        if (!candidateChunks.isArray() || candidateChunks.isEmpty()) {
            throw new IllegalArgumentException("审核文件必须包含至少一个候选知识片段");
        }

        List<ReviewedKnowledgeChunk> chunks = new ArrayList<>();
        for (JsonNode candidateChunk : candidateChunks) {
            String chunkReviewStatus = requiredText(
                    candidateChunk,
                    "review_status"
            );
            // 整份资料通过审核不代表每个片段自动通过，逐条检查能阻止混入未审核规则。
            if (!ReviewedKnowledgePublication.APPROVED_STATUS.equals(
                    chunkReviewStatus
            )) {
                throw new IllegalStateException("候选知识片段尚未审核通过");
            }

            String sourceLocator = requiredText(candidateChunk, "source_locator");
            chunks.add(new ReviewedKnowledgeChunk(
                    requiredInteger(candidateChunk, "chunk_index"),
                    requiredText(candidateChunk, "title"),
                    sourceLocator,
                    firstPdfPage(sourceLocator),
                    requiredText(candidateChunk, "evidence_summary")
            ));
        }
        return List.copyOf(chunks);
    }

    private int firstPdfPage(String sourceLocator) {
        Matcher matcher = FIRST_PDF_PAGE.matcher(sourceLocator);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "资料定位缺少 PDF 物理页码：" + sourceLocator
            );
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String requiredText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isTextual() || field.asText().isBlank()) {
            throw new IllegalArgumentException("审核文件缺少文本字段：" + fieldName);
        }
        return field.asText().strip();
    }

    private static int requiredInteger(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.canConvertToInt()) {
            throw new IllegalArgumentException("审核文件缺少整数字段：" + fieldName);
        }
        return field.asInt();
    }

    private static boolean requiredBoolean(
            JsonNode node,
            String fieldName
    ) {
        JsonNode field = node.get(fieldName);
        if (!field.isBoolean()) {
            throw new IllegalArgumentException(
                    "审核文件缺少布尔字段：" + fieldName
            );
        }
        return field.asBoolean();
    }

    private static String optionalText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (!field.isTextual()) {
            throw new IllegalArgumentException("审核文件字段格式错误：" + fieldName);
        }
        return field.asText();
    }
}
