package com.zhku.knowledge.rag;

import java.util.Objects;

/**
 * 当轮获准进入模型上下文的一段证据。
 *
 * <p>citationId 是服务端为本轮获准证据分配的不透明标识（冻结契约）：
 * 生成层只能原样引用它，不能解析、构造或推测其内部结构。</p>
 */
public record ApprovedEvidence(String citationId, String text, String sourceLabel) {

    public ApprovedEvidence {
        Objects.requireNonNull(citationId, "引用标识不能为空");
        Objects.requireNonNull(text, "证据正文不能为空");
        Objects.requireNonNull(sourceLabel, "来源标签不能为空");
        if (text.isBlank()) {
            throw new IllegalArgumentException("证据正文不能为空");
        }
    }
}
