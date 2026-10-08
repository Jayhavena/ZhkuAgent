package com.zhku.knowledge.feedback;

import com.zhku.knowledge.web.ZhkuLocalPreviewApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 从本机预览应用验证学生反馈端点，避免只在 standalone MockMvc 中“看起来可用”。 */
@SpringBootTest(
        classes = {
                ZhkuLocalPreviewApplication.class,
                FeedbackPreviewEndpointTest.FeedbackPreviewTestConfiguration.class
        },
        properties = {
                "spring.profiles.active=zhku-local-preview",
                "zhku.local-preview.enabled=true",
                "server.address=127.0.0.1",
                "ZHKU_DB_PASSWORD=test-only"
        }
)
@AutoConfigureMockMvc
class FeedbackPreviewEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAcceptAnonymousFeedbackFromPreviewApplicationEntry() throws Exception {
        mockMvc.perform(post("/zhku/feedback")
                        .contentType(APPLICATION_JSON)
                        .content(validRequest("OUTDATED", "a".repeat(64))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.anonymousSessionHash").doesNotExist());
    }

    @Test
    void shouldRejectIllegalReasonAndOverlongHashFromPreviewApplicationEntry()
            throws Exception {
        mockMvc.perform(post("/zhku/feedback")
                        .contentType(APPLICATION_JSON)
                        .content(validRequest("PLEASE_CONTACT_ME", "a".repeat(64))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/zhku/feedback")
                        .contentType(APPLICATION_JSON)
                        .content(validRequest("NOT_HELPFUL", "a".repeat(65))))
                .andExpect(status().isBadRequest());
    }

    private static String validRequest(String reason, String hash) {
        return """
                {"guideIdentifier":"course-selection","guideVersion":"2026-1",
                "reason":"%s","anonymousSessionHash":"%s"}
                """.formatted(reason, hash);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FeedbackPreviewTestConfiguration {

        @Bean
        @Primary
        FeedbackRepository feedbackPreviewTestRepository() {
            return new InMemoryFeedbackRepository();
        }
    }

    private static final class InMemoryFeedbackRepository implements FeedbackRepository {

        private final Map<String, AnonymousFeedbackEvent> events = new ConcurrentHashMap<>();

        @Override
        public boolean saveIfAbsent(AnonymousFeedbackEvent event) {
            return events.putIfAbsent(event.dedupeKey(), event) == null;
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
            throw new UnsupportedOperationException("预览端点测试不执行保留清理");
        }

        @Override
        public List<FeedbackAggregate> listAggregates() {
            return List.of();
        }
    }
}
