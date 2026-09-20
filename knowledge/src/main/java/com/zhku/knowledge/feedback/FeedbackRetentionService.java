package com.zhku.knowledge.feedback;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 到期事件的聚合与删除服务，不连接资料、发布或检索链路。 */
public final class FeedbackRetentionService {

    private final FeedbackRepository repository;
    private final Clock clock;

    public FeedbackRetentionService(FeedbackRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "反馈仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /** @return 本次转入聚合并删除的事件数 */
    public int aggregateExpired() {
        Instant now = clock.instant();
        List<AnonymousFeedbackEvent> expiredEvents = repository.findExpiredAtOrBefore(now);
        if (expiredEvents.isEmpty()) {
            return 0;
        }

        Map<AggregateKey, Long> counts = new LinkedHashMap<>();
        for (AnonymousFeedbackEvent event : expiredEvents) {
            AggregateKey key = new AggregateKey(
                    event.guideIdentifier(),
                    event.guideVersion(),
                    event.reason(),
                    event.createdAt().atZone(ZoneOffset.UTC).toLocalDate()
            );
            counts.merge(key, 1L, Long::sum);
        }
        List<FeedbackAggregate> aggregates = counts.entrySet().stream()
                .map(entry -> entry.getKey().toAggregate(entry.getValue()))
                .sorted(Comparator.comparing(FeedbackAggregate::feedbackDate)
                        .thenComparing(FeedbackAggregate::guideIdentifier)
                        .thenComparing(aggregate -> aggregate.reason().name()))
                .toList();
        repository.replaceExpiredWithAggregates(expiredEvents, aggregates);
        return expiredEvents.size();
    }

    private record AggregateKey(
            String guideIdentifier,
            String guideVersion,
            FeedbackReason reason,
            java.time.LocalDate feedbackDate
    ) {
        private FeedbackAggregate toAggregate(long count) {
            return new FeedbackAggregate(
                    guideIdentifier, guideVersion, reason, feedbackDate, count
            );
        }
    }
}
