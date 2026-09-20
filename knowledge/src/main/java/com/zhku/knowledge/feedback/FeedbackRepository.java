package com.zhku.knowledge.feedback;

import java.time.Instant;
import java.util.List;

/** 匿名反馈持久化边界；返回值让服务在并发写入时也能可靠地识别重复提交。 */
public interface FeedbackRepository {

    boolean saveIfAbsent(AnonymousFeedbackEvent event);

    List<AnonymousFeedbackEvent> findExpiredAtOrBefore(Instant expiresAt);

    /**
     * 在一个持久化原子操作中累计聚合并删除已到期事件。
     *
     * <p>先删后写会丢失统计，先写后删却没有事务会在重试时重复计数，因此两者不能拆开。</p>
     */
    void replaceExpiredWithAggregates(
            List<AnonymousFeedbackEvent> expiredEvents,
            List<FeedbackAggregate> aggregates
    );

    List<FeedbackAggregate> listAggregates();
}
