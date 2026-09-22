package com.zhku.knowledge.governance.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zhku.knowledge.feedback.AnonymousFeedbackEvent;
import com.zhku.knowledge.feedback.FeedbackAggregate;
import com.zhku.knowledge.feedback.FeedbackAggregateQueryService;
import com.zhku.knowledge.feedback.FeedbackReason;
import com.zhku.knowledge.feedback.FeedbackRepository;
import com.zhku.knowledge.feedback.FeedbackRetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 本机管理员只能审核不可识别聚合，不能读取或导出单条匿名反馈。 */
class FeedbackAdminControllerTest {

    private FeedbackAggregateQueryService aggregateQueryService;
    private FeedbackRetentionService retentionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aggregateQueryService = mock(FeedbackAggregateQueryService.class);
        retentionService = mock(FeedbackRetentionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FeedbackAdminController(
                aggregateQueryService, retentionService
        )).build();
    }

    @Test
    void shouldReturnOnlyAggregateFieldsForAdministratorReview() throws Exception {
        when(aggregateQueryService.listAggregates()).thenReturn(List.of(
                new FeedbackAggregate(
                        "course-selection", "2026-1", FeedbackReason.OUTDATED,
                        LocalDate.of(2026, 9, 20), 4L
                )
        ));

        mockMvc.perform(get("/zhku/local-admin/feedback/aggregates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].guideIdentifier").value("course-selection"))
                .andExpect(jsonPath("$[0].guideVersion").value("2026-1"))
                .andExpect(jsonPath("$[0].reason").value("OUTDATED"))
                .andExpect(jsonPath("$[0].feedbackDate").value("2026-09-20"))
                .andExpect(jsonPath("$[0].count").value(4))
                .andExpect(jsonPath("$[0].anonymousSessionHash").doesNotExist())
                .andExpect(jsonPath("$[0].dedupeKey").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].apiKeyEnvVar").doesNotExist())
                .andExpect(jsonPath("$[0].*", hasSize(5)));
    }

    @Test
    void shouldExposeOnlyAllowlistedConfigurationFieldsOverAdministratorHttp()
            throws Exception {
        System.setProperty("zhku.rag.enabled", "true");
        System.setProperty("zhku.rag.active-profile", "dashscope");
        System.setProperty("zhku.rag.profiles.dashscope.chat-model", "qwen-plus");
        System.setProperty("zhku.rag.profiles.dashscope.api-key-env-var", "DASHSCOPE_API_KEY");
        System.setProperty("zhku.rag.profiles.dashscope.base-url", "https://example.invalid/v1");
        try {
            mockMvc.perform(get("/zhku/local-admin/feedback/configuration"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provider").value("dashscope"))
                    .andExpect(jsonPath("$.model").value("qwen-plus"))
                    .andExpect(jsonPath("$.profile").value("dashscope"))
                    .andExpect(jsonPath("$.status").value("ENABLED"))
                    .andExpect(jsonPath("$.apiKeyEnvVar").doesNotExist())
                    .andExpect(jsonPath("$.baseUrl").doesNotExist())
                    .andExpect(jsonPath("$.*", hasSize(4)));
        } finally {
            System.clearProperty("zhku.rag.enabled");
            System.clearProperty("zhku.rag.active-profile");
            System.clearProperty("zhku.rag.profiles.dashscope.chat-model");
            System.clearProperty("zhku.rag.profiles.dashscope.api-key-env-var");
            System.clearProperty("zhku.rag.profiles.dashscope.base-url");
        }
    }

    @Test
    void shouldWriteOnlyAnonymousStructuredTelemetryWhenAdministratorReadsAggregates()
            throws Exception {
        when(aggregateQueryService.listAggregates()).thenReturn(List.of());
        Logger logger = (Logger) LoggerFactory.getLogger(FeedbackAdminController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get("/zhku/local-admin/feedback/aggregates"))
                    .andExpect(status().isOk());

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("zhku.anonymousTelemetry");
                assertThat(event.getArgumentArray()).hasSize(1);
                assertThat(event.getArgumentArray()[0]).isInstanceOf(java.util.Map.class);
                java.util.Map<?, ?> telemetry = (java.util.Map<?, ?>) event.getArgumentArray()[0];
                assertThat(telemetry.get("route")).isEqualTo("LOCAL_ADMIN_FEEDBACK");
                assertThat(telemetry.keySet()).noneMatch(Set.of(
                        "message", "question", "answer", "name",
                        "studentId", "contact", "apiKey"
                )::contains);
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldRunRetentionOnlyWhenLocalAdministratorExplicitlyRequestsIt()
            throws Exception {
        when(retentionService.aggregateExpired()).thenReturn(3);

        mockMvc.perform(post("/zhku/local-admin/feedback/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregatedEventCount").value(3))
                .andExpect(jsonPath("$.published").doesNotExist())
                .andExpect(jsonPath("$.indexRunId").doesNotExist())
                .andExpect(jsonPath("$.candidateId").doesNotExist());

        verify(retentionService).aggregateExpired();
    }

    @Test
    void shouldDeleteAtExpiryBoundaryWhenRetentionRunsThroughAdministratorHttp()
            throws Exception {
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        NonFilteringFeedbackRepository repository = new NonFilteringFeedbackRepository(List.of(
                event("expired", now.minusSeconds(90L * 24 * 60 * 60), now),
                event("future", now.minusSeconds(90L * 24 * 60 * 60).plusSeconds(1), now.plusSeconds(1))
        ));
        MockMvc retentionMvc = MockMvcBuilders.standaloneSetup(new FeedbackAdminController(
                new FeedbackAggregateQueryService(repository),
                new FeedbackRetentionService(repository, Clock.fixed(now, ZoneOffset.UTC))
        )).build();

        retentionMvc.perform(post("/zhku/local-admin/feedback/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregatedEventCount").value(1));

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

    private static final class NonFilteringFeedbackRepository implements FeedbackRepository {

        private final List<AnonymousFeedbackEvent> events;
        private final List<FeedbackAggregate> aggregates = new ArrayList<>();

        private NonFilteringFeedbackRepository(List<AnonymousFeedbackEvent> events) {
            this.events = new ArrayList<>(events);
        }

        @Override
        public boolean saveIfAbsent(AnonymousFeedbackEvent event) {
            return false;
        }

        @Override
        public List<AnonymousFeedbackEvent> findExpiredAtOrBefore(Instant expiresAt) {
            // 模拟仓储越界返回，HTTP 入口仍应只删除已经到期的匿名明细。
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
