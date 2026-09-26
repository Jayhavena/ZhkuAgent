package com.zhku.knowledge.eval;

import com.zhku.knowledge.routing.AnswerRoute;

import java.util.List;
import java.util.Objects;

/**
 * 被测系统对一条问题的实际观测结果（评测的"实际"一侧）。
 *
 * <p>只保留评测需要的三个可核验事实：路由结论、**条级引用定位符**、是否拒答/转人工。
 * 不收集回答正文、会话标识等——评测不需要它们，收集了反而扩大暴露面。</p>
 */
public record ObservedAnswer(
        AnswerRoute route,
        List<String> citationLocators,
        boolean refused
) {

    public ObservedAnswer {
        Objects.requireNonNull(route, "路由结论不能为空");
        citationLocators = List.copyOf(citationLocators == null ? List.of() : citationLocators);
    }
}
