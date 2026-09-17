package com.zhku.knowledge.routing;

import java.util.Optional;

/**
 * 五级路由中的单级处理器。
 *
 * <p>返回 {@link Optional#empty()} 表示本级不命中，编排器继续下一级；
 * 返回非空即命中，编排器立即盖章返回，后续级别不再执行（严格短路）。</p>
 */
@FunctionalInterface
public interface RoutingLevel {

    /**
     * 尝试在本级回答该请求。
     *
     * @param request 路由请求
     * @return 命中时的回答负载；不命中返回空
     */
    Optional<LevelAnswer> handle(RoutingRequest request);
}
