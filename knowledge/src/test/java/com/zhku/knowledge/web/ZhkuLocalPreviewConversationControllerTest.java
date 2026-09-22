package com.zhku.knowledge.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.CourseType;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeEvidence;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.AdjustmentEvidence;
import com.zhku.knowledge.conversation.publicelective.CreditLimitEvidence;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveAdjustmentService;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.serviceguide.GuideStep;
import com.zhku.knowledge.serviceguide.OfficialChannel;
import com.zhku.knowledge.serviceguide.ServiceGuide;
import com.zhku.knowledge.serviceguide.ServiceGuideBindings;
import com.zhku.knowledge.serviceguide.ServiceGuideCardProvider;
import com.zhku.knowledge.serviceguide.ServiceGuideResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    void shouldKeepTheSameMessagePathWhenSseIsNotEnabled() throws Exception {
        MockMvc mockMvc = mockMvc(new MutableClock(START));
        String sessionId = createSession(mockMvc);

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"公共选修课每学期最多可以选多少学分？\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_SERVICE_UNAVAILABLE"));
    }

    @Test
    void shouldNegotiateSseAndWriteAllFramesWhenClientRequestsEventStream() throws Exception {
        MockMvc mockMvc = mockMvc(new MutableClock(START), true);
        String sessionId = createSession(mockMvc);

        MvcResult result = mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"公共选修课每学期最多可以选多少学分？\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getContentAsString())
                .contains("event:accepted", "event:reply", "event:complete");
    }

    @Test
    void shouldKeepJsonResponseWhenSseIsAvailableButClientRequestsJson() throws Exception {
        MockMvc mockMvc = mockMvc(new MutableClock(START), true);
        String sessionId = createSession(mockMvc);

        mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"公共选修课每学期最多可以选多少学分？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ANSWER"));
    }

    @Test
    void shouldKeepFullGuideCardShapeInReplySseFrame() throws Exception {
        MockMvc mockMvc = guideCardStreamingMockMvc(new MutableClock(START));
        String sessionId = createSession(mockMvc);

        MvcResult result = mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"公共选修课选课调整怎么办理\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode reply = replyFrame(result);
        assertThat(reply.path("guideCard").isObject()).isTrue();
        assertThat(reply.path("guideCard").path("guideIdentifier").asText())
                .isEqualTo("elective-adjust");
        assertThat(reply.path("guideCard").path("nextSteps").isArray()).isTrue();
        assertThat(reply.path("guideCard").path("confirmations").isArray()).isTrue();
        assertThat(reply.path("guideCard").path("officialChannels").isArray()).isTrue();
        assertThat(reply.path("guideCard").path("handoffOnly").asBoolean()).isFalse();
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
        return mockMvc(clock, false);
    }

    private static MockMvc mockMvc(MutableClock clock, boolean sseEnabled) {
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
        com.zhku.knowledge.web.streaming.SseConversationStreamer streamer = sseEnabled
                ? new com.zhku.knowledge.web.streaming.SseConversationStreamer(
                new com.zhku.knowledge.web.streaming.ConversationStreamProcessor(facade, store, clock),
                Runnable::run,
                5_000L
        )
                : null;
        return MockMvcBuilders.standaloneSetup(
                new ZhkuLocalPreviewConversationController(facade, new ObjectProvider<>() {
                    @Override
                    public com.zhku.knowledge.web.streaming.SseConversationStreamer getObject() {
                        return streamer;
                    }

                    @Override
                    public com.zhku.knowledge.web.streaming.SseConversationStreamer getObject(Object... arguments) {
                        return streamer;
                    }
                })
        ).build();
    }

    private static MockMvc guideCardStreamingMockMvc(MutableClock clock) {
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService retakeService = new ElectiveRetakeConversationService(
                store, courseType -> Optional.empty(), clock, SESSION_TTL);
        PublicElectiveAdjustmentService adjustmentService = new PublicElectiveAdjustmentService(
                () -> Optional.of(new AdjustmentEvidence(
                        UUID.fromString("6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05"),
                        "本科生学生手册（2025版）·公共选修课修读与考核规则",
                        "2025版-公共选修修读与考核-学生可见",
                        "PDF 第 77 页，第 9、13 条",
                        "办理公共选修课选课与修读的全日制普通本科生",
                        "选课、退选、补选和改选应按照每学期《课程选课工作通知》办理。")));
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store,
                retakeService,
                new PublicElectiveCreditLimitService(Optional::empty),
                null,
                adjustmentService,
                clock
        );
        ServiceGuideCardProvider guideCards = new ServiceGuideCardProvider(
                ServiceGuideBindings.firstBatch(),
                new ServiceGuideResolver((identifier, version) -> Optional.of(guide(clock)), clock)
        );
        LocalPreviewConversationFacade facade = new LocalPreviewConversationFacade(
                store,
                new LocalPreviewAnswerChain(store, router, null, null, clock),
                clock,
                SESSION_TTL,
                guideCards,
                ServiceGuideBindings.ELECTIVE_ADJUST
        );
        com.zhku.knowledge.web.streaming.SseConversationStreamer streamer =
                new com.zhku.knowledge.web.streaming.SseConversationStreamer(
                        new com.zhku.knowledge.web.streaming.ConversationStreamProcessor(
                                facade, store, clock),
                        Runnable::run,
                        5_000L
                );
        return MockMvcBuilders.standaloneSetup(
                new ZhkuLocalPreviewConversationController(facade, new ObjectProvider<>() {
                    @Override
                    public com.zhku.knowledge.web.streaming.SseConversationStreamer getObject() {
                        return streamer;
                    }

                    @Override
                    public com.zhku.knowledge.web.streaming.SseConversationStreamer getObject(Object... arguments) {
                        return streamer;
                    }
                })
        ).build();
    }

    private static JsonNode replyFrame(MvcResult result) throws Exception {
        Matcher matcher = Pattern.compile(
                "event:reply\\Rdata:(\\{[\\s\\S]*?})\\R\\R"
        ).matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).as("SSE reply 帧").isTrue();
        return OBJECT_MAPPER.readTree(matcher.group(1));
    }

    private static ServiceGuide guide(Clock clock) {
        return new ServiceGuide(
                "elective-adjust",
                "2025版",
                "选课调整办理指引（公共选修课）",
                "全日制本科生（公共选修课）",
                List.of(new GuideStep(1, "查看教务处当学期《课程选课工作通知》。", false)),
                List.of(),
                List.of(new OfficialChannel(
                        1,
                        OfficialChannel.ChannelType.SCHOOL_OFFICE,
                        "【演示占位】教务处学籍科",
                        "【演示占位】教务处办公室",
                        "演示占位：无审核资料依据",
                        clock.instant()
                )),
                List.of(new ServiceGuide.RiskNotice(1, "以当学期《课程选课工作通知》为准。")),
                "PUBLIC_ELECTIVE_RULE#2025版",
                "COURSE_NOTICE#2026-2027-1",
                "审核说明",
                true,
                clock.instant(),
                null
        );
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
