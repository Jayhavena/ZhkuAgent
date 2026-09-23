package com.zhku.knowledge.feedback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 匿名反馈 HTTP 契约：入口只能接受受控枚举，不能把可识别输入带入闭环。 */
class AnonymousFeedbackContractTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC
    );
    private static final String ANONYMOUS_SESSION_HASH = "a".repeat(64);

    private InMemoryFeedbackRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFeedbackRepository();
        AnonymousFeedbackService service = new AnonymousFeedbackService(repository, CLOCK);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AnonymousFeedbackController(service)
        ).build();
    }

    @Test
    void shouldAcceptControlledEnumWithoutReturningAnonymousHash() throws Exception {
        mockMvc.perform(post("/zhku/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("NOT_HELPFUL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.anonymousSessionHash").doesNotExist())
                .andExpect(jsonPath("$.dedupeKey").doesNotExist())
                .andExpect(jsonPath("$.eventId").doesNotExist());

        assertThat(repository.size()).isOne();
    }

    @Test
    void shouldAcceptThePublishedUnicodeGuideVersionOverHttp() throws Exception {
        mockMvc.perform(post("/zhku/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guideIdentifier":"elective-adjust","guideVersion":"2025版",
                                "reason":"NOT_HELPFUL","anonymousSessionHash":"%s"}
                                """.formatted(ANONYMOUS_SESSION_HASH)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.duplicate").value(false));

        assertThat(repository.size()).isOne();
    }

    @Test
    void shouldRejectWhitespaceInGuideVersionOverHttp() throws Exception {
        mockMvc.perform(post("/zhku/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guideIdentifier":"elective-adjust","guideVersion":"2025 版",
                                "reason":"NOT_HELPFUL","anonymousSessionHash":"%s"}
                                """.formatted(ANONYMOUS_SESSION_HASH)))
                .andExpect(status().isBadRequest());

        assertThat(repository.size()).isZero();
    }

    @Test
    void shouldRejectFreeTextAndPiiFieldsBeforeTheyReachStorage() throws Exception {
        for (String forbiddenField : new String[]{
                "message", "studentId", "student_id", "name", "contact", "question", "answer"
        }) {
            String payload = requestWith(forbiddenField, "不应接收的自由文本或身份信息");
            assertThat(payload.charAt(1)).isEqualTo('"');
            mockMvc.perform(post("/zhku/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());
        }

        assertThat(repository.size()).isZero();
    }

    @Test
    void shouldDeduplicateRepeatedFeedbackWithoutExposingTheAnonymousHash()
            throws Exception {
        mockMvc.perform(post("/zhku/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("OUTDATED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/zhku/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("OUTDATED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.anonymousSessionHash").doesNotExist());

        assertThat(repository.size()).isOne();
    }

    @Test
    void shouldRejectUnknownReasonAndInvalidAnonymousHash() throws Exception {
        mockMvc.perform(post("/zhku/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("PLEASE_CALL_ME")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/zhku/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guideIdentifier":"course-selection","guideVersion":"2026-1",
                                "reason":"NOT_HELPFUL","anonymousSessionHash":"not-a-hash"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(repository.size()).isZero();
    }

    private static String validRequest(String reason) {
        return """
                {"guideIdentifier":"course-selection","guideVersion":"2026-1",
                "reason":"%s","anonymousSessionHash":"%s"}
                """.formatted(reason, ANONYMOUS_SESSION_HASH);
    }

    private static String requestWith(String field, String value) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("guideIdentifier", "course-selection");
        values.put("guideVersion", "2026-1");
        values.put("reason", "NOT_HELPFUL");
        values.put("anonymousSessionHash", ANONYMOUS_SESSION_HASH);
        values.put(field, value);
        return values.entrySet().stream()
                .map(entry -> "\"%s\":\"%s\"".formatted(
                        entry.getKey(), entry.getValue()
                ))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private static final class InMemoryFeedbackRepository implements FeedbackRepository {

        private final Map<String, AnonymousFeedbackEvent> eventsByDedupeKey = new LinkedHashMap<>();

        @Override
        public boolean saveIfAbsent(AnonymousFeedbackEvent event) {
            return eventsByDedupeKey.putIfAbsent(event.dedupeKey(), event) == null;
        }

        @Override
        public List<AnonymousFeedbackEvent> findExpiredAtOrBefore(Instant expiresAt) {
            return List.of();
        }

        @Override
        public void replaceExpiredWithAggregates(
                List<AnonymousFeedbackEvent> expiredEvents,
                List<FeedbackAggregate> aggregates
        ) {
            throw new UnsupportedOperationException("匿名接收契约不调用清理操作");
        }

        @Override
        public List<FeedbackAggregate> listAggregates() {
            return List.of();
        }

        private int size() {
            return eventsByDedupeKey.size();
        }
    }
}
