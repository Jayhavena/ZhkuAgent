package com.yupi.yuaiagent.zhku.knowledge.publication;

import java.util.Objects;

/**
 * 一条已审核、可引用的知识片段。
 *
 * <p>片段必须保留物理页码和定位说明；仅存摘要而不保留定位会使后续回答无法回到原资料核验。</p>
 */
public record ReviewedKnowledgeChunk(
        int chunkIndex,
        String sectionTitle,
        String sourceLocator,
        int pageNumber,
        String content
) {

    public ReviewedKnowledgeChunk {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("片段序号不能小于 0");
        }
        sectionTitle = requireText(sectionTitle, "片段标题不能为空");
        sourceLocator = requireText(sourceLocator, "资料定位不能为空");
        if (pageNumber < 1) {
            throw new IllegalArgumentException("PDF 页码必须从 1 开始");
        }
        content = requireText(content, "片段正文不能为空");
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
