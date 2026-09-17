package com.zhku.knowledge.routing;

import java.util.List;
import java.util.Objects;

/**
 * 编排器盖章后的路由结论。
 *
 * <p>route 字段只能由 {@link KnowledgeAnswerOrchestrator} 写入，
 * 保证结论与实际命中的级别一致；handoffRequired 标识是否需要转人工渠道。</p>
 */
public record RoutedAnswer(AnswerRoute route, String message, List<String> citationIds, boolean handoffRequired) {

    public RoutedAnswer {
        Objects.requireNonNull(route, "路由结论不能为空");
        Objects.requireNonNull(message, "回答内容不能为空");
        if (message.isBlank()) {
            throw new IllegalArgumentException("回答内容不能为空");
        }
        Objects.requireNonNull(citationIds, "引用标识列表不能为空");
        citationIds = List.copyOf(citationIds);
    }
}
