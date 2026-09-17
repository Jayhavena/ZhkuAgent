package com.zhku.knowledge.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.CourseType;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeEvidence;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.CreditLimitEvidence;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 阶段 5 会话接口的 HTTP 契约测试，使用真实领域服务与可控时钟。 */
class ZhkuLocalPreviewConversationControllerTest {

    private static final Instant START = Instant.parse("2026-09-15T00:00:00Z");
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldCreateSessionAndCompleteTwoRoundAnswerWithSourceFields() throws Exception {
        MockMvc mockMvc = mockMvc(new MutableClock(START));
        String sessionId = createSession(mockMvc);

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"选修课没及格，还能重修吗？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("NEED_CLARIFICATION"))
                .andExpect(jsonPath("$.message").value("你说的是公共选修课，还是专业选修课？"))
                .andExpect(jsonPath("$.answer").doesNotExist());

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"公选课\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ANSWER"))
                .andExpect(jsonPath("$.answer.conclusion").value("公共选修课成绩不合格，不安排重修。"))
                .andExpect(jsonPath("$.answer.basis").isNotEmpty())
                .andExpect(jsonPath("$.answer.sourceId").value("72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14"))
                .andExpect(jsonPath("$.answer.sourceTitle").value("本科生学生手册（2025版）"))
                .andExpect(jsonPath("$.answer.sourceVersion").value("2025版"))
                .andExpect(jsonPath("$.answer.sourceLocator").value("PDF 第 77 页，第 18 条"))
                .andExpect(jsonPath("$.answer.applicableScope").value("全日制普通本科生的公共选修课"));
    }

    @Test
    void shouldRejectUnknownSessionWithoutCreatingItFromMessageRequest() throws Exception {
        MockMvc mockMvc = mockMvc(new MutableClock(START));

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"选修课没及格，还能重修吗？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SESSION_INVALID"));
    }

    @Test
    void shouldCancelSessionIdempotently() throws Exception {
        MockMvc mockMvc = mockMvc(new MutableClock(START));
        String sessionId = createSession(mockMvc);

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/cancel", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SESSION_CANCELLED"));
        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/cancel", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SESSION_CANCELLED"));
    }

    @Test
    void shouldRejectBlankMessage() throws Exception {
        MockMvc mockMvc = mockMvc(new MutableClock(START));
        String sessionId = createSession(mockMvc);

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MESSAGE"));
    }

    @Test
    void shouldReturnSessionExpiredAtTenMinuteBoundary() throws Exception {
        MutableClock clock = new MutableClock(START);
        MockMvc mockMvc = mockMvc(clock);
        String sessionId = createSession(mockMvc);
        clock.advance(SESSION_TTL);

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"公选课\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SESSION_EXPIRED"));
    }

    @Test
    void shouldAnswerTermCreditLimitWithFullSourceCard() throws Exception {
        MockMvc mockMvc = mockMvc(new MutableClock(START));
        String sessionId = createSession(mockMvc);

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"公共选修课每学期最多可以选多少学分？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ANSWER"))
                .andExpect(jsonPath("$.answer.conclusion").value("每学期公共选修课最高不得超过 4 学分。"))
                .andExpect(jsonPath("$.answer.sourceId").value("f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5"))
                .andExpect(jsonPath("$.answer.sourceTitle").value("本科生学生手册（2025版）"))
                .andExpect(jsonPath("$.answer.sourceVersion").value("2025版"))
                .andExpect(jsonPath("$.answer.sourceLocator").value("PDF 第 76—77 页，第 8、10 条"))
                .andExpect(jsonPath("$.answer.applicableScope").value("办理公共选修课程选课的本科生；个人培养方案与当期通知仍需核对"));
    }

    @Test
    void shouldKeepNetworkCreditQuestionOutOfScope() throws Exception {
        MockMvc mockMvc = mockMvc(new MutableClock(START));
        String sessionId = createSession(mockMvc);

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"网络公共选修课学分上限是多少？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    private static String createSession(MockMvc mockMvc) throws Exception {
        MvcResult result = mockMvc.perform(post("/zhku/local-preview/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").value("2026-09-15T00:10:00Z"))
                .andReturn();
        JsonNode response = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        return response.path("sessionId").asText();
    }

    private static MockMvc mockMvc(MutableClock clock) {
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = new ElectiveRetakeConversationService(
                store,
                evidenceFinder(),
                clock,
                SESSION_TTL
        );
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store,
                service,
                new PublicElectiveCreditLimitService(creditLimitEvidenceFinder()),
                clock
        );
        // 装配改为回答链路（五级顺序）：澄清/规则复用既有路由，通知级与检索级此处缺省。
        LocalPreviewAnswerChain chain = new LocalPreviewAnswerChain(
                store,
                router,
                null,
                null,
                clock
        );
        LocalPreviewConversationFacade facade = new LocalPreviewConversationFacade(
                store,
                chain,
                clock,
                SESSION_TTL
        );
        return MockMvcBuilders.standaloneSetup(
                new ZhkuLocalPreviewConversationController(facade)
        ).build();
    }

    private static ElectiveRetakeEvidenceFinder evidenceFinder() {
        Map<CourseType, ElectiveRetakeEvidence> evidence = new EnumMap<>(CourseType.class);
        evidence.put(CourseType.PUBLIC_ELECTIVE, new ElectiveRetakeEvidence(
                UUID.fromString("72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14"),
                "本科生学生手册（2025版）",
                "2025版",
                "PDF 第 77 页，第 18 条",
                "全日制普通本科生的公共选修课",
                "公共选修课不办理缓考，成绩不合格者不安排重修。"
        ));
        evidence.put(CourseType.PROFESSIONAL_ELECTIVE, new ElectiveRetakeEvidence(
                UUID.fromString("72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14"),
                "本科生学生手册（2025版）",
                "2025版",
                "PDF 第 77 页，第 18 条",
                "全日制普通本科生的专业选修课",
                "专业选修课成绩不合格者可申请重修学习。"
        ));
        return courseType -> Optional.ofNullable(evidence.get(courseType));
    }

    private static com.zhku.knowledge.conversation.publicelective.CreditLimitEvidenceFinder creditLimitEvidenceFinder() {
        CreditLimitEvidence evidence = new CreditLimitEvidence(
                UUID.fromString("f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5"),
                "本科生学生手册（2025版）",
                "2025版",
                "PDF 第 76—77 页，第 8、10 条",
                "办理公共选修课程选课的本科生；个人培养方案与当期通知仍需核对",
                "学生应以人才培养方案为选课依据，在规定学习年限内修读并取得规定学分；每学期公共选修课最高不得超过 4 学分。"
        );
        return () -> Optional.of(evidence);
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
