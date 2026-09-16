package com.zhku.knowledge.conversation.publicelective;

import com.zhku.knowledge.conversation.ConversationState;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.CourseType;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeEvidence;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReply;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 规则分发必须保留先前重修澄清的语义，不能把类别回复挪作新规则输入。 */
class PublicElectiveConversationRouterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-15T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void shouldPrioritizePendingRetakeClarificationOverCreditLimitRouting() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        UUID sessionId = UUID.randomUUID();
        store.create(ConversationState.active(sessionId, CLOCK.instant().plus(Duration.ofMinutes(10))));
        ElectiveRetakeConversationService retakeService = retakeService(store);
        retakeService.handle(sessionId, "选修课没及格，还能重修吗？");
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store,
                retakeService,
                creditLimitService(),
                CLOCK
        );

        ElectiveRetakeReply reply = router.route(sessionId, "公选课");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer().conclusion()).isEqualTo("公共选修课成绩不合格，不安排重修。");
    }

    @Test
    void shouldRouteExplicitCreditLimitQuestionToCreditLimitService() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        UUID sessionId = UUID.randomUUID();
        store.create(ConversationState.active(sessionId, CLOCK.instant().plus(Duration.ofMinutes(10))));
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store,
                retakeService(store),
                creditLimitService(),
                CLOCK
        );

        ElectiveRetakeReply reply = router.route(sessionId, "公共选修课一学期最多几学分？");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer().conclusion()).isEqualTo("每学期公共选修课最高不得超过 4 学分。");
    }

    private static ElectiveRetakeConversationService retakeService(InMemoryConversationStore store) {
        return new ElectiveRetakeConversationService(
                store,
                courseType -> Optional.of(new ElectiveRetakeEvidence(
                        UUID.randomUUID(),
                        "本科生学生手册（2025版）",
                        "2025版",
                        "PDF 第 77 页，第 18 条",
                        "全日制普通本科生的选修课",
                        courseType == CourseType.PUBLIC_ELECTIVE
                                ? "公共选修课不办理缓考，成绩不合格者不安排重修。"
                                : "专业选修课成绩不合格者可申请重修学习。"
                )),
                CLOCK,
                Duration.ofMinutes(10)
        );
    }

    private static PublicElectiveCreditLimitService creditLimitService() {
        return new PublicElectiveCreditLimitService(() -> Optional.of(new CreditLimitEvidence(
                UUID.randomUUID(),
                "本科生学生手册（2025版）",
                "2025版",
                "PDF 第 76—77 页，第 8、10 条",
                "办理公共选修课程选课的本科生",
                "每学期公共选修课最高不得超过 4 学分。"
        )));
    }
}
