package com.zhku.knowledge.feedback;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 反馈事件到期后只能留下不可识别的统计，不得变成资料或索引的自动输入。 */
class FeedbackRetentionTest {

    private static final Instant START = Instant.parse("2026-09-20T00:00:00Z");

    @Test
    void shouldKeepEventsBeforeNinetyDaysThenAggregateAndDeleteAtBoundary() {
        MutableClock clock = new MutableClock(START);
        InMemoryFeedbackRepository repository = new InMemoryFeedbackRepository();
        AnonymousFeedbackService submissionService = new AnonymousFeedbackService(repository, clock);
        FeedbackRetentionService retentionService = new FeedbackRetentionService(repository, clock);
        submissionService.submit(submission(FeedbackReason.NOT_HELPFUL, "a"));
        submissionService.submit(submission(FeedbackReason.NOT_HELPFUL, "b"));
        submissionService.submit(submission(FeedbackReason.OUTDATED, "c"));

        clock.advance(Duration.ofDays(89));
        assertThat(retentionService.aggregateExpired()).isZero();
        assertThat(repository.eventCount()).isEqualTo(3);
        assertThat(repository.listAggregates()).isEmpty();

        clock.advance(Duration.ofDays(1));
        assertThat(retentionService.aggregateExpired()).isEqualTo(3);
        assertThat(repository.eventCount()).isZero();
        assertThat(repository.listAggregates())
                .extracting(
                        FeedbackAggregate::guideIdentifier,
                        FeedbackAggregate::guideVersion,
                        FeedbackAggregate::reason,
                        FeedbackAggregate::feedbackDate,
                        FeedbackAggregate::count
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                "course-selection", "2026-1", FeedbackReason.NOT_HELPFUL,
                                LocalDate.of(2026, 9, 20), 2L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "course-selection", "2026-1", FeedbackReason.OUTDATED,
                                LocalDate.of(2026, 9, 20), 1L
                        )
                );
    }

    @Test
    void shouldExposeOnlyAggregateDimensionsToAdministrators() {
        assertThat(FeedbackAggregate.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly(
                        "guideIdentifier", "guideVersion", "reason", "feedbackDate", "count"
                );
    }

    @Test
    void shouldNotDependOnPublicationCandidateOrIndexingServices() {
        assertFeedbackServicesDoNotReference("com.zhku.knowledge.governance");
        assertFeedbackServicesDoNotReference("com.zhku.knowledge.publication");
        assertFeedbackServicesDoNotReference("com.zhku.knowledge.retrieval");
    }

    private static void assertFeedbackServicesDoNotReference(String forbiddenPackagePrefix) {
        for (Class<?> service : List.of(
                AnonymousFeedbackService.class,
                FeedbackRetentionService.class
        )) {
            assertThat(service.getDeclaredFields())
                    .extracting(Field::getType)
                    .extracting(Class::getPackageName)
                    .noneMatch(packageName -> packageName.startsWith(forbiddenPackagePrefix));
        }
    }

    private static AnonymousFeedbackSubmission submission(FeedbackReason reason, String hashSeed) {
        return new AnonymousFeedbackSubmission(
                "course-selection", "2026-1", reason, hashSeed.repeat(64)
        );
    }

    private static final class InMemoryFeedbackRepository implements FeedbackRepository {

        private final Map<String, AnonymousFeedbackEvent> eventsByDedupeKey = new LinkedHashMap<>();
        private final Map<AggregateKey, FeedbackAggregate> aggregatesByKey = new LinkedHashMap<>();

        @Override
        public boolean saveIfAbsent(AnonymousFeedbackEvent event) {
            return eventsByDedupeKey.putIfAbsent(event.dedupeKey(), event) == null;
        }

        @Override
        public List<AnonymousFeedbackEvent> findExpiredAtOrBefore(Instant expiresAt) {
            return eventsByDedupeKey.values().stream()
                    .filter(event -> !event.expiresAt().isAfter(expiresAt))
                    .toList();
        }

        @Override
        public void replaceExpiredWithAggregates(
                List<AnonymousFeedbackEvent> expiredEvents,
                List<FeedbackAggregate> aggregates
        ) {
            expiredEvents.forEach(event -> eventsByDedupeKey.remove(event.dedupeKey()));
            aggregates.forEach(aggregate -> aggregatesByKey.merge(
                    AggregateKey.from(aggregate), aggregate,
                    (current, incoming) -> new FeedbackAggregate(
                            current.guideIdentifier(), current.guideVersion(), current.reason(),
                            current.feedbackDate(), current.count() + incoming.count()
                    )
            ));
        }

        @Override
        public List<FeedbackAggregate> listAggregates() {
            return aggregatesByKey.values().stream()
                    .sorted(Comparator.comparing(FeedbackAggregate::feedbackDate)
                            .thenComparing(FeedbackAggregate::guideIdentifier)
                            .thenComparing(aggregate -> aggregate.reason().name()))
                    .toList();
        }

        private int eventCount() {
            return eventsByDedupeKey.size();
        }
    }

    private record AggregateKey(
            String guideIdentifier,
            String guideVersion,
            FeedbackReason reason,
            LocalDate feedbackDate
    ) {
        private static AggregateKey from(FeedbackAggregate aggregate) {
            return new AggregateKey(
                    aggregate.guideIdentifier(), aggregate.guideVersion(), aggregate.reason(),
                    aggregate.feedbackDate()
            );
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("测试仅使用 UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
