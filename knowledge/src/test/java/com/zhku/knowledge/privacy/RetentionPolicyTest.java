package com.zhku.knowledge.privacy;

import com.zhku.knowledge.feedback.AnonymousFeedbackEvent;
import com.zhku.knowledge.feedback.FeedbackAggregate;
import com.zhku.knowledge.feedback.FeedbackReason;
import com.zhku.knowledge.feedback.FeedbackRepository;
import com.zhku.knowledge.feedback.FeedbackRetentionService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 到期判断由服务层再次收敛，避免仓储实现失误提前删除匿名明细。 */
class RetentionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    @Test
    void shouldAggregateOnlyEventsWhoseExpiryHasReachedNow() {
        NonFilteringRepository repository = new NonFilteringRepository(List.of(
                event("expired", NOW.minusSeconds(90L * 24 * 60 * 60), NOW),
                event("future", NOW.minusSeconds(90L * 24 * 60 * 60).plusSeconds(1), NOW.plusSeconds(1))
        ));

        int aggregated = new FeedbackRetentionService(repository, Clock.fixed(NOW, ZoneOffset.UTC))
                .aggregateExpired();

        assertThat(aggregated).isEqualTo(1);
        assertThat(repository.events).extracting(AnonymousFeedbackEvent::dedupeKey)
                .containsExactly("future".repeat(64));
        assertThat(repository.aggregates).extracting(FeedbackAggregate::count).containsExactly(1L);
    }

    private static AnonymousFeedbackEvent event(String seed, Instant createdAt, Instant expiresAt) {
        return new AnonymousFeedbackEvent(
                "course-selection", "2026-1", FeedbackReason.NOT_HELPFUL,
                seed.substring(0, 1).repeat(64), seed.repeat(64), createdAt, expiresAt
        );
    }

    private static final class NonFilteringRepository implements FeedbackRepository {

        private final List<AnonymousFeedbackEvent> events;
        private final List<FeedbackAggregate> aggregates = new ArrayList<>();

        private NonFilteringRepository(List<AnonymousFeedbackEvent> events) {
            this.events = new ArrayList<>(events);
        }

        @Override
        public boolean saveIfAbsent(AnonymousFeedbackEvent event) {
            return false;
        }

        @Override
        public List<AnonymousFeedbackEvent> findExpiredAtOrBefore(Instant expiresAt) {
            // 故意模拟错误的仓储返回；服务层仍不得把未到期匿名明细带入删除事务。
            return List.copyOf(events);
        }

        @Override
        public void replaceExpiredWithAggregates(
                List<AnonymousFeedbackEvent> expiredEvents,
                List<FeedbackAggregate> replacementAggregates
        ) {
            events.removeAll(expiredEvents);
            aggregates.addAll(replacementAggregates);
        }

        @Override
        public List<FeedbackAggregate> listAggregates() {
            return List.copyOf(aggregates);
        }
    }
}
