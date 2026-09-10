package com.yupi.yuaiagent.zhku.knowledge.publication;

import java.util.UUID;

/**
 * 关键词检索返回的最小引用单元。
 *
 * <p>该对象不包含原始文件路径，避免将本地审核资料的位置泄露给后续公开接口。</p>
 */
public record PublishedKnowledgeChunk(
        UUID sourceId,
        String sourceTitle,
        String sourceLocator,
        int pageNumber,
        String content
) {
}
