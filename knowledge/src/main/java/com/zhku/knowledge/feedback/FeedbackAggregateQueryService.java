package com.zhku.knowledge.feedback;

import java.util.List;
import java.util.Objects;

/** 管理员聚合查询的窄服务边界，避免管理端取得逐条匿名事件。 */
public final class FeedbackAggregateQueryService {

    private final FeedbackRepository repository;

    public FeedbackAggregateQueryService(FeedbackRepository repository) {
        this.repository = Objects.requireNonNull(repository, "反馈仓储不能为空");
    }

    public List<FeedbackAggregate> listAggregates() {
        return repository.listAggregates();
    }
}
