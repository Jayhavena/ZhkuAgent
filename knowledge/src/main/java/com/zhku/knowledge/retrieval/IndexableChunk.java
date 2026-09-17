package com.zhku.knowledge.retrieval;

import java.util.Objects;
import java.util.UUID;

/**
 * 待索引片段：原知识片段 + 其来源的资格状态快照。
 *
 * <p>把来源状态一并带出，是为了让索引侧与召回侧共用同一个
 * {@link RetrievalEligibilityFilter}，避免"索引时合格、召回时放松"的口径漂移。</p>
 *
 * @param chunkId 原 knowledge_chunk 标识（索引与引用的锚点）
 * @param content 片段正文（切分后含标题行）
 * @param contentChecksum 片段内容哈希，写入索引条目供过期审计比对
 * @param sourceVersion 来源版本（如"学生手册 2026 版"）
 * @param source 来源资格状态快照
 */
public record IndexableChunk(
        UUID chunkId,
        String content,
        String contentChecksum,
        String sourceVersion,
        SourceEligibilitySnapshot source
) {

    public IndexableChunk {
        Objects.requireNonNull(chunkId, "片段标识不能为空");
        Objects.requireNonNull(content, "片段正文不能为空");
        Objects.requireNonNull(contentChecksum, "片段哈希不能为空");
        Objects.requireNonNull(sourceVersion, "来源版本不能为空");
        Objects.requireNonNull(source, "来源状态快照不能为空");
    }
}
