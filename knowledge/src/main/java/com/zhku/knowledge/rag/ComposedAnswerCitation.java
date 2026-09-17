package com.zhku.knowledge.rag;

import java.util.Objects;
import java.util.UUID;

/**
 * 一条引用的明细：不透明标识 + 可展示的来源定位。
 *
 * <p>拆开的原因：模型只允许看到不透明 `citationId`（防伪造），而学生端引用卡
 * 需要标题/定位/页码。两者在服务端配对产生，明细不经过模型，模型无法影响它。</p>
 *
 * @param citationId 服务端分配的不透明标识
 * @param chunkId 该标识对应的知识片段
 * @param sourceTitle 来源标题（展示用）
 * @param sourceLocator 来源定位（章节/页码等展示用）
 * @param pageNumber 页码，可为 null（网页来源无页码）
 */
public record ComposedAnswerCitation(
        String citationId,
        UUID chunkId,
        String sourceTitle,
        String sourceLocator,
        Integer pageNumber
) {

    public ComposedAnswerCitation {
        Objects.requireNonNull(citationId, "引用标识不能为空");
        Objects.requireNonNull(chunkId, "片段标识不能为空");
        Objects.requireNonNull(sourceTitle, "来源标题不能为空");
        Objects.requireNonNull(sourceLocator, "来源定位不能为空");
    }
}
