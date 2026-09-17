package com.zhku.knowledge.retrieval;

import java.util.Objects;

/**
 * 切分产出的 chunk 草稿（未入库）。
 *
 * @param chunkIndex 源内序号，从 0 开始连续编号，与 knowledge_chunk 的唯一约束对齐
 * @param sectionTitle 所在条款/章节标题；首个标记之前的前言 chunk 为 null
 * @param content chunk 正文（含标题行），引用时按原文呈现
 * @param contentChecksum 正文的 SHA-256（64 位十六进制），供索引过期审计比对
 */
public record ChunkDraft(int chunkIndex, String sectionTitle, String content, String contentChecksum) {

    public ChunkDraft {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunk 序号不能为负");
        }
        Objects.requireNonNull(content, "chunk 正文不能为空");
        if (content.isBlank()) {
            throw new IllegalArgumentException("chunk 正文不能为空");
        }
        Objects.requireNonNull(contentChecksum, "内容哈希不能为空");
        if (contentChecksum.length() != 64) {
            throw new IllegalArgumentException("内容哈希必须是 64 位十六进制 SHA-256");
        }
    }
}
