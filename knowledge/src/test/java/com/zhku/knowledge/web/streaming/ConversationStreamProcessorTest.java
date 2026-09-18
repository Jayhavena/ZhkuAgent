package com.zhku.knowledge.web.streaming;

import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeEvidence;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.conversation.publicelective.CreditLimitEvidence;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.web.LocalPreviewAnswerChain;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 流式事件先复用真实会话链路，避免 SSE 分支绕过路由与 revision 保护。 */
class ConversationStreamProcessorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(10);

    @Test
    void shouldEmitAcceptedReplyAndCompleteWithFrozenConversationFields() throws IOException {
        ConversationStore store = new InMemoryConversationStore();
        LocalPreviewConversationFacade facade = facade(store);
        UUID sessionId = facade.createConversation().sessionId();
        RecordingWriter writer = new RecordingWriter();

        new ConversationStreamProcessor(facade, store, CLOCK).process(
                sessionId,
                "公共选修课每学期最多可以选多少学分？",
                writer
        );

        assertThat(writer.events).extracting(RecordedEvent::name)
                .containsExactly("accepted", "reply", "complete");
        assertThat(writer.events).allSatisfy(event -> {
            assertThat(event.payload().sessionId()).isEqualTo(sessionId);
            assertThat(event.payload().revision()).isZero();
            assertThat(event.payload().conversationStatus()).hasToString("ACTIVE");
        });
        assertThat(writer.events.get(0).payload())
                .extracting(ConversationStreamEvent::type, ConversationStreamEvent::route)
                .containsExactly(null, null);
        assertThat(writer.events.get(1).payload())
                .extracting(
                        ConversationStreamEvent::type,
                        ConversationStreamEvent::route,
                        ConversationStreamEvent::handoffRequired,
                        ConversationStreamEvent::citationIds)
                .containsExactly(
                        ElectiveRetakeReplyType.ANSWER,
                        AnswerRoute.HIGH_RISK_RULE,
                        false,
                        List.of());
        assertThat(writer.events.get(2).payload()).isEqualTo(writer.events.get(1).payload());
    }

    private static LocalPreviewConversationFacade facade(ConversationStore store) {
        ElectiveRetakeConversationService retakeService = new ElectiveRetakeConversationService(
                store,
                courseType -> Optional.of(new ElectiveRetakeEvidence(
                        UUID.fromString("72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14"),
                        "本科生学生手册（2025版）", "2025版", "PDF 第 77 页，第 18 条",
                        "全日制普通本科生的选修课", "公共选修课不安排重修。"
                )),
                CLOCK,
                TTL
        );
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store,
                retakeService,
                new PublicElectiveCreditLimitService(() -> Optional.of(new CreditLimitEvidence(
                        UUID.fromString("f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5"),
                        "本科生学生手册（2025版）", "2025版", "PDF 第 76—77 页，第 8、10 条",
                        "本科生公共选修课", "每学期公共选修课最高不得超过 4 学分。"
                ))),
                CLOCK
        );
        return new LocalPreviewConversationFacade(
                store,
                new LocalPreviewAnswerChain(store, router, null, null, CLOCK),
                CLOCK,
                TTL
        );
    }

    private record RecordedEvent(String name, ConversationStreamEvent payload) {
    }

    private static final class RecordingWriter implements ConversationStreamWriter {

        private final java.util.ArrayList<RecordedEvent> events = new java.util.ArrayList<>();

        @Override
        public void write(String name, ConversationStreamEvent payload) {
            events.add(new RecordedEvent(name, payload));
        }
    }
}
