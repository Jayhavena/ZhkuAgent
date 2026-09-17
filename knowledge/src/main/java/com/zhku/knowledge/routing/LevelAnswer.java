package com.zhku.knowledge.routing;

import java.util.List;
import java.util.Objects;

/**
 * 单个路由级别的回答负载（不含路由结论）。
 *
 * <p>路由结论由编排器统一盖章，级别处理器只提供内容——这样"后级覆盖前级"
 * 在结构上不可能发生，严格短路不需要靠处理器自觉。</p>
 *
 * <p>citationIds 为服务端分配的不透明标识（冻结契约）：编排器只透传经
 * 服务端校验后的 ID，不自行构造、不解析其含义。</p>
 */
public record LevelAnswer(String message, List<String> citationIds, boolean handoffRequired) {

    public LevelAnswer {
        Objects.requireNonNull(message, "回答内容不能为空");
        if (message.isBlank()) {
            throw new IllegalArgumentException("回答内容不能为空");
        }
        Objects.requireNonNull(citationIds, "引用标识列表不能为空");
        // 防御性复制：引用白名单标识在传递链路上必须不可变，防止下游意外篡改。
        citationIds = List.copyOf(citationIds);
    }
}
