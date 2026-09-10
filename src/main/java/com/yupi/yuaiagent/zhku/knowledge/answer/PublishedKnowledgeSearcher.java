package com.yupi.yuaiagent.zhku.knowledge.answer;

import com.yupi.yuaiagent.zhku.knowledge.publication.PublishedKnowledgeChunk;

import java.util.List;

/** 已发布知识片段的只读检索边界。 */
@FunctionalInterface
public interface PublishedKnowledgeSearcher {

    List<PublishedKnowledgeChunk> searchPublished(String keyword);
}
