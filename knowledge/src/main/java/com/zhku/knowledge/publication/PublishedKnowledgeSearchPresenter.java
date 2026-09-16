package com.zhku.knowledge.publication;

import java.util.List;
import java.util.Objects;

/** 将本地检索结果展示为可人工核验的文本。 */
public final class PublishedKnowledgeSearchPresenter {

    public String present(String keyword, List<PublishedKnowledgeChunk> chunks) {
        Objects.requireNonNull(keyword, "检索词不能为空");
        Objects.requireNonNull(chunks, "检索结果不能为空");

        String normalizedKeyword = keyword.strip();
        if (normalizedKeyword.isEmpty()) {
            throw new IllegalArgumentException("检索词不能为空");
        }

        StringBuilder result = new StringBuilder()
                .append("检索词：").append(normalizedKeyword).append(System.lineSeparator())
                .append("命中片段：").append(chunks.size()).append(" 条")
                .append(System.lineSeparator());
        if (chunks.isEmpty()) {
            return result.append("未找到已发布的匹配资料。")
                    .toString();
        }

        for (int index = 0; index < chunks.size(); index++) {
            PublishedKnowledgeChunk chunk = chunks.get(index);
            result.append(index + 1).append(". ")
                    .append(chunk.sourceTitle()).append(System.lineSeparator())
                    .append("   定位：").append(chunk.sourceLocator())
                    .append(System.lineSeparator())
                    .append("   物理页码：").append(chunk.pageNumber())
                    .append(System.lineSeparator())
                    .append("   摘要：").append(chunk.content())
                    .append(System.lineSeparator());
        }
        return result.toString();
    }
}
