package com.zhku.knowledge.governance.web;

import com.zhku.knowledge.feedback.FeedbackAggregate;
import com.zhku.knowledge.feedback.FeedbackAggregateQueryService;
import com.zhku.knowledge.feedback.FeedbackReason;
import com.zhku.knowledge.feedback.FeedbackRetentionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * 本机管理员的匿名反馈审核入口。
 *
 * <p>管理员只能看到聚合结果；若把逐条事件或匿名散列接到这里，即使没有真实身份也会
 * 重新获得可关联轨迹，破坏反馈最小化收集的前提。</p>
 */
@RestController
@Profile("zhku-local-admin")
@ConditionalOnProperty(
        prefix = "zhku.local-admin",
        name = "enabled",
        havingValue = "true"
)
@RequestMapping("/zhku/local-admin/feedback")
public final class FeedbackAdminController {

    private final FeedbackAggregateQueryService aggregateQueryService;
    private final FeedbackRetentionService retentionService;

    public FeedbackAdminController(
            FeedbackAggregateQueryService aggregateQueryService,
            FeedbackRetentionService retentionService
    ) {
        this.aggregateQueryService = Objects.requireNonNull(
                aggregateQueryService, "反馈聚合查询服务不能为空"
        );
        this.retentionService = Objects.requireNonNull(retentionService, "反馈保留服务不能为空");
    }

    @GetMapping(value = "/aggregates", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FeedbackAggregateResponse>> listAggregates() {
        List<FeedbackAggregateResponse> response = aggregateQueryService.listAggregates().stream()
                .map(FeedbackAggregateResponse::from)
                .toList();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }

    /** 由本机管理员显式触发，避免后台反馈静默影响其他业务链路。 */
    @PostMapping(value = "/retention", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RetentionResponse> aggregateExpired() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RetentionResponse(retentionService.aggregateExpired()));
    }

    public record FeedbackAggregateResponse(
            String guideIdentifier,
            String guideVersion,
            FeedbackReason reason,
            String feedbackDate,
            long count
    ) {
        static FeedbackAggregateResponse from(FeedbackAggregate aggregate) {
            return new FeedbackAggregateResponse(
                    aggregate.guideIdentifier(),
                    aggregate.guideVersion(),
                    aggregate.reason(),
                    aggregate.feedbackDate().toString(),
                    aggregate.count()
            );
        }
    }

    public record RetentionResponse(long aggregatedEventCount) {
    }
}
