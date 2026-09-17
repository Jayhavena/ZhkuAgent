package com.zhku.knowledge.retrieval;

import java.util.Objects;
import java.util.UUID;

/**
 * 召回到的候选片段（已通过资格校验，可进入模型上下文）。
 *
 * <p>携带来源标题与定位，用于生成后的引用呈现；citationId 由服务端在
 * 组装当轮获准证据时分配，此处不预置——ID 是服务端事实，不是检索产物。</p>
 */
public record RetrievedChunk(
        UUID chunkId,
        String content,
        String sourceTitle,
        String sourceLocator,
        Integer pageNumber,
        String sourceVersion,
        String contentChecksum
) {

    public RetrievedChunk {
        Objects.requireNonNull(chunkId, "片段标识不能为空");
        Objects.requireNonNull(content, "片段正文不能为空");
        Objects.requireNonNull(sourceTitle, "来源标题不能为空");
        Objects.requireNonNull(sourceLocator, "来源定位不能为空");
        Objects.requireNonNull(sourceVersion, "来源版本不能为空");
        Objects.requireNonNull(contentChecksum, "片段哈希不能为空");
    }
}
