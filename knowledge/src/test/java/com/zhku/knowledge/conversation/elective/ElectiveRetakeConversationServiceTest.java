package com.zhku.knowledge.conversation.elective;

import com.zhku.knowledge.conversation.ConversationState;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.PendingField;
import com.zhku.knowledge.conversation.PendingIntent;
import org.junit.jupiter.api.Test;

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

/**
 * 首条制度问答链路的应用服务测试。
 *
 * <p>测试使用真实会话存储和可控时钟，确保澄清、完成和超时行为不是由 mock 假象造成的；
 * 证据读取则以受控实现替代 JDBC，仅隔离数据库访问，不替代“必须有已发布证据才可作答”的规则。</p>
 */
class ElectiveRetakeConversationServiceTest {

    private static final UUID SESSION_ID = UUID.fromString(
            "2d57f31e-b3aa-4f5b-a10e-e55b7f9d7c63"
    );
    private static final Instant START = Instant.parse("2026-09-14T00:00:00Z");

    @Test
    void shouldAskOnlyForCourseTypeWhenQuestionIsAmbiguous() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, evidenceFinder(), clock);

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "选修课没及格，还能重修吗？");
        ConversationState state = store.find(SESSION_ID, clock).state();

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.NEED_CLARIFICATION);
        assertThat(reply.message()).isEqualTo("你说的是公共选修课，还是专业选修课？");
        assertThat(reply.answer()).isNull();
        assertThat(state)
                .extracting(
                        ConversationState::pendingIntent,
                        ConversationState::pendingField,
                        ConversationState::revision
                )
                .containsExactly(
                        PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                        PendingField.COURSE_TYPE,
                        1L
                );
    }

    @Test
    void shouldAnswerPublicElectiveAndClearPendingAfterCourseTypeReply() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, evidenceFinder(), clock);
        service.handle(SESSION_ID, "选修课没及格，还能重修吗？");

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "公选课");
        ConversationState state = store.find(SESSION_ID, clock).state();

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer())
                .extracting(
                        ElectiveRetakeAnswer::conclusion,
                        ElectiveRetakeAnswer::sourceId,
                        ElectiveRetakeAnswer::sourceLocator,
                        ElectiveRetakeAnswer::sourceVersion,
                        ElectiveRetakeAnswer::applicableScope
                )
                .containsExactly(
                        "公共选修课成绩不合格，不安排重修。",
                        PUBLIC_EVIDENCE.sourceId(),
                        "PDF 第 77 页，第 18 条",
                        "2025版",
                        "全日制普通本科生的公共选修课"
                );
        assertThat(reply.answer().conclusion()).doesNotContain("补考");
        assertThat(state)
                .extracting(
                        ConversationState::pendingIntent,
                        ConversationState::pendingField,
                        ConversationState::revision
                )
                .containsExactly(null, null, 2L);
    }

    @Test
    void shouldAnswerProfessionalElectiveDirectlyWhenCourseTypeIsExplicit() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, evidenceFinder(), clock);

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "专业选修课挂科后可以重修吗？");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer())
                .extracting(
                        ElectiveRetakeAnswer::conclusion,
                        ElectiveRetakeAnswer::sourceId,
                        ElectiveRetakeAnswer::sourceLocator
                )
                .containsExactly(
                        "专业选修课成绩不合格，可申请重修学习。",
                        PROFESSIONAL_EVIDENCE.sourceId(),
                        "PDF 第 77 页，第 18 条"
                );
        assertThat(store.find(SESSION_ID, clock).outcome().name()).isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldAnswerPublicElectiveRetakeAbbreviationWithoutAskingForKnownCourseType() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, evidenceFinder(), clock);

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "我公选课不及格了要重修怎么办");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer().conclusion()).isEqualTo("公共选修课成绩不合格，不安排重修。");
        assertThat(reply.answer().sourceLocator()).isEqualTo("PDF 第 77 页，第 18 条");
        assertThat(store.find(SESSION_ID, clock).outcome().name()).isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldAnswerFullPublicElectiveRetakeQuestionWithoutClarification() {
        ElectiveRetakeConversationService service = service(
                new InMemoryConversationStore(), evidenceFinder(), new MutableClock(START));

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "公共选修课不及格了要重修怎么办");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer().conclusion()).isEqualTo("公共选修课成绩不合格，不安排重修。");
    }

    @Test
    void shouldNotTreatPublicElectiveWithdrawalQuestionAsRetakeIntent() {
        ElectiveRetakeConversationService service = service(
                new InMemoryConversationStore(), evidenceFinder(), new MutableClock(START));

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "公选课怎么退课？");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.OUT_OF_SCOPE);
        assertThat(reply.answer()).isNull();
    }

    @Test
    void shouldSafelyRefusePublicElectiveRetakeAbbreviationWithoutQualifiedEvidence() {
        MutableClock clock = new MutableClock(START);
        ElectiveRetakeConversationService service = service(
                new InMemoryConversationStore(), courseType -> Optional.empty(), clock);

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "我公选课不及格了要重修怎么办");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE);
        assertThat(reply.answer()).isNull();
    }

    @Test
    void shouldAskForCourseTypeWhenAmbiguousQuestionFollowsPreviousAnswer() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, evidenceFinder(), clock);
        service.handle(SESSION_ID, "选修课没及格，还能重修吗？");
        service.handle(SESSION_ID, "公选课");

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "选修课没及格，还能重修吗？");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.NEED_CLARIFICATION);
        assertThat(reply.message()).isEqualTo("你说的是公共选修课，还是专业选修课？");
    }

    @Test
    void shouldNotAnswerWhenPublishedEvidenceIsUnavailable() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, courseType -> Optional.empty(), clock);
        service.handle(SESSION_ID, "选修课没及格，还能重修吗？");

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "专业选修课");
        ConversationState state = store.find(SESSION_ID, clock).state();

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE);
        assertThat(reply.answer()).isNull();
        assertThat(state.pendingIntent()).isEqualTo(PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE);
        assertThat(state.pendingField()).isEqualTo(PendingField.COURSE_TYPE);
        assertThat(state.revision()).isEqualTo(1L);
    }

    @Test
    void shouldKeepPendingStateWhenFollowUpIsStillAmbiguous() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, evidenceFinder(), clock);
        service.handle(SESSION_ID, "选修课没及格，还能重修吗？");
        ConversationState before = store.find(SESSION_ID, clock).state();
        clock.advance(Duration.ofMinutes(1));

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "就是选修课");
        ConversationState after = store.find(SESSION_ID, clock).state();

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.NEED_CLARIFICATION);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void shouldNotTreatUnrelatedQuestionAsPendingCourseType() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, evidenceFinder(), clock);
        service.handle(SESSION_ID, "选修课没及格，还能重修吗？");
        ConversationState before = store.find(SESSION_ID, clock).state();

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "奖学金什么时候申请？");
        ConversationState after = store.find(SESSION_ID, clock).state();

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.OUT_OF_SCOPE);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void shouldNotTreatNewPublicElectiveTopicAsPendingCourseTypeReply() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, evidenceFinder(), clock);
        service.handle(SESSION_ID, "选修课没及格，还能重修吗？");
        ConversationState before = store.find(SESSION_ID, clock).state();

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "公选课怎么退课？");
        ConversationState after = store.find(SESSION_ID, clock).state();

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.OUT_OF_SCOPE);
        assertThat(reply.answer()).isNull();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void shouldRejectExpiredPendingConversationInsteadOfUsingOldCourseType() {
        MutableClock clock = new MutableClock(START);
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService service = service(store, evidenceFinder(), clock);
        service.handle(SESSION_ID, "选修课没及格，还能重修吗？");
        clock.advance(Duration.ofMinutes(10));

        ElectiveRetakeReply reply = service.handle(SESSION_ID, "专业选修课");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.SESSION_EXPIRED);
        assertThat(reply.answer()).isNull();
    }

    private static ElectiveRetakeConversationService service(
            ConversationStore store,
            ElectiveRetakeEvidenceFinder evidenceFinder,
            Clock clock
    ) {
        return new ElectiveRetakeConversationService(
                store,
                evidenceFinder,
                clock,
                Duration.ofMinutes(10)
        );
    }

    private static ElectiveRetakeEvidenceFinder evidenceFinder() {
        Map<CourseType, ElectiveRetakeEvidence> evidence = new EnumMap<>(CourseType.class);
        evidence.put(CourseType.PUBLIC_ELECTIVE, PUBLIC_EVIDENCE);
        evidence.put(CourseType.PROFESSIONAL_ELECTIVE, PROFESSIONAL_EVIDENCE);
        return courseType -> Optional.ofNullable(evidence.get(courseType));
    }

    private static final ElectiveRetakeEvidence PUBLIC_EVIDENCE = new ElectiveRetakeEvidence(
            UUID.fromString("72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14"),
            "本科生学生手册（2025版）",
            "2025版",
            "PDF 第 77 页，第 18 条",
            "全日制普通本科生的公共选修课",
            "公共选修课不办理缓考，成绩不合格者不安排重修。"
    );
    private static final ElectiveRetakeEvidence PROFESSIONAL_EVIDENCE = new ElectiveRetakeEvidence(
            UUID.fromString("72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14"),
            "本科生学生手册（2025版）",
            "2025版",
            "PDF 第 77 页，第 18 条",
            "全日制普通本科生的专业选修课",
            "专业选修课成绩不合格者可申请重修学习。"
    );

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
