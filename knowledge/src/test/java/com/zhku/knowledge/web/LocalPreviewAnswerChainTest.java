package com.zhku.knowledge.web;

import com.zhku.knowledge.conversation.ConversationState;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.CourseType;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeEvidence;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeEvidenceFinder;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.conversation.publicelective.CreditLimitEvidence;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.notice.AcademicTerm;
import com.zhku.knowledge.notice.CourseSelectionNoticeService;
import com.zhku.knowledge.notice.CurrentNoticeFinder;
import com.zhku.knowledge.notice.NoticeFact;
import com.zhku.knowledge.notice.NoticeFactType;
import com.zhku.knowledge.notice.NoticeLifecycleStatus;
import com.zhku.knowledge.notice.NoticeType;
import com.zhku.knowledge.notice.OfficialWebSnapshot;
import com.zhku.knowledge.notice.PublishedNotice;
import com.zhku.knowledge.rag.AnswerEvidenceGuard;
import com.zhku.knowledge.rag.CitationAuditRecord;
import com.zhku.knowledge.rag.CitationIdFactory;
import com.zhku.knowledge.rag.ModelResponse;
import com.zhku.knowledge.rag.RetrievalAugmentedAnswerService;
import com.zhku.knowledge.retrieval.HybridCandidateFuser;
import com.zhku.knowledge.retrieval.HybridRetrievalService;
import com.zhku.knowledge.retrieval.RetrievedChunk;
import com.zhku.knowledge.routing.AnswerRoute;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 答案链路装配测试：五级顺序在真实装配下成立，且只追加字段正确产出。
 *
 * <p>重点验证三条容易出错的边界：通知级"无有效证据"必须短路为拒答（不回落规则级）；
 * 检索级命中时引用标识必须透传到响应（前端引用卡与审计反查都依赖它）；
 * 全不命中时**旧字段 type 语义不变**（仍为 OUT_OF_SCOPE），新结论只落在 route/handoffRequired。</p>
 */
class LocalPreviewAnswerChainTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final UUID SESSION = UUID.fromString("aa111111-1111-4111-8111-111111111111");

    private final ConversationStore store = new InMemoryConversationStore();
    private final List<CitationAuditRecord> audits = new ArrayList<>();

    @Test
    void shouldRouteToCurrentNoticeBeforeRules() {
        createSession();

        LocalPreviewAnswerChain.RoutedReply reply =
                chain(noticeService(withNotice()), null).reply(SESSION, "本学期什么时候选课？");

        assertThat(reply.route()).isEqualTo(AnswerRoute.CURRENT_NOTICE);
        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        // 回答卡不得因链路改造而丢失。
        assertThat(reply.answer()).isNotNull();
        assertThat(reply.answer().sourceTitle()).isEqualTo("2026-2027 学年第一学期本科生选课通知");
        assertThat(reply.handoffRequired()).isFalse();
    }

    @Test
    void shouldShortCircuitAsRefusalWhenNoticeHasNoEvidence() {
        createSession();
        // 通知服务存在但查不到有效通知：必须短路为拒答，不能回落到规则级用旧手册补答。
        CourseSelectionNoticeService noEvidence = new CourseSelectionNoticeService(
                List::<PublishedNotice>of,
                new AcademicTerm("2026-2027-1"),
                "https://jwc.zhku.edu.cn");

        LocalPreviewAnswerChain.RoutedReply reply =
                chain(noEvidence, null).reply(SESSION, "什么时候可以补选？");

        assertThat(reply.route()).isEqualTo(AnswerRoute.INSUFFICIENT_EVIDENCE);
        assertThat(reply.handoffRequired()).isTrue();
        assertThat(reply.answer()).isNull();
    }

    @Test
    void shouldRouteWithCitationsWhenRetrievalLevelAnswers() {
        createSession();

        LocalPreviewAnswerChain.RoutedReply reply =
                chain(null, retrievalService()).reply(SESSION, "每学期最多修多少学分？");

        assertThat(reply.route()).isEqualTo(AnswerRoute.RETRIEVAL_AUGMENTED);
        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.citationIds()).isNotEmpty();
        // 引用明细必须与标识一一对应（前端引用卡依赖标题/定位/页码）。
        assertThat(reply.citations()).hasSameSizeAs(reply.citationIds());
        assertThat(reply.citations().getFirst().citationId()).isEqualTo(reply.citationIds().getFirst());
        assertThat(reply.citations().getFirst().sourceTitle()).isEqualTo("公共选修规则手册");
        assertThat(reply.citations().getFirst().sourceLocator()).isEqualTo("第 1 页");
        assertThat(reply.citations().getFirst().pageNumber()).isEqualTo(1);
        assertThat(audits).isNotEmpty();
        assertThat(reply.handoffRequired()).isFalse();
    }

    @Test
    void shouldKeepLegacyTypeAndRefuseWithHandoffWhenNothingMatches() {
        createSession();

        LocalPreviewAnswerChain.RoutedReply reply =
                chain(null, null).reply(SESSION, "完全无关的问题");

        // 旧字段语义不变（OUT_OF_SCOPE），新字段承载路由结论与转人工标记。
        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.OUT_OF_SCOPE);
        assertThat(reply.route()).isEqualTo(AnswerRoute.INSUFFICIENT_EVIDENCE);
        assertThat(reply.handoffRequired()).isTrue();
        assertThat(reply.citationIds()).isEmpty();
    }

    private void createSession() {
        store.create(ConversationState.active(SESSION, CLOCK.instant().plus(TTL)));
    }

    private LocalPreviewAnswerChain chain(
            CourseSelectionNoticeService noticeService,
            RetrievalAugmentedAnswerService retrievalService
    ) {
        ElectiveRetakeConversationService electiveService = new ElectiveRetakeConversationService(
                store, evidenceFinder(), CLOCK, TTL);
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store,
                electiveService,
                new PublicElectiveCreditLimitService(() -> Optional.of(creditLimitEvidence())),
                CLOCK);
        return new LocalPreviewAnswerChain(store, router, noticeService, retrievalService, CLOCK);
    }

    private static CourseSelectionNoticeService noticeService(PublishedNotice notice) {
        return new CourseSelectionNoticeService(
                new CurrentNoticeFinder(() -> List.of(notice), CLOCK),
                new AcademicTerm("2026-2027-1"),
                "https://jwc.zhku.edu.cn");
    }

    private static PublishedNotice withNotice() {
        return new PublishedNotice(
                UUID.fromString("cc111111-1111-4111-8111-111111111111"),
                UUID.fromString("cc222222-2222-4222-8222-222222222222"),
                "2026-2027 学年第一学期本科生选课通知",
                "2026 年第 1 号",
                "2026-2027-1 学期本科生",
                new AcademicTerm("2026-2027-1"),
                NoticeType.COURSE_SELECTION,
                NoticeLifecycleStatus.PUBLISHED,
                Instant.parse("2026-09-10T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"),
                new OfficialWebSnapshot(
                        "https://jwc.zhku.edu.cn/notices/selection",
                        "2026-selection.html",
                        "A".repeat(64),
                        Instant.parse("2026-08-30T00:00:00Z"),
                        "text/html"
                ),
                true,
                true,
                List.of(new NoticeFact(
                        NoticeFactType.COURSE_SELECTION,
                        Instant.parse("2026-09-15T00:00:00Z"),
                        Instant.parse("2026-09-18T00:00:00Z"),
                        "https://jwc.zhku.edu.cn/course-selection",
                        true,
                        true
                ))
        );
    }

    private RetrievalAugmentedAnswerService retrievalService() {
        RetrievedChunk chunk = new RetrievedChunk(
                UUID.fromString("bb111111-1111-4111-8111-111111111111"),
                "第一条 每学期公共选修课最高修读 4 学分。",
                "公共选修规则手册", "第 1 页", 1, "学生手册 2026 版", "a".repeat(64));
        HybridRetrievalService hybrid = new HybridRetrievalService(new HybridCandidateFuser());
        int[] counter = {0};
        CitationIdFactory ids = () -> "cit-" + (++counter[0]);
        return new RetrievalAugmentedAnswerService(
                hybrid,
                (query, limit) -> List.of(chunk),
                (query, limit) -> List.of(),
                request -> new ModelResponse(
                        "每学期公共选修课最高修读 4 学分。", 0.95,
                        List.of(request.evidenceCitationIds().getFirst()), false),
                new AnswerEvidenceGuard(),
                ids,
                audits::add,
                CLOCK,
                3);
    }

    private static ElectiveRetakeEvidenceFinder evidenceFinder() {
        Map<CourseType, ElectiveRetakeEvidence> evidence = new EnumMap<>(CourseType.class);
        evidence.put(CourseType.PUBLIC_ELECTIVE, new ElectiveRetakeEvidence(
                UUID.fromString("72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14"),
                "本科生学生手册（2025版）", "2025版", "PDF 第 77 页，第 18 条",
                "全日制普通本科生的公共选修课", "公共选修课不办理缓考，成绩不合格者不安排重修。"));
        evidence.put(CourseType.PROFESSIONAL_ELECTIVE, new ElectiveRetakeEvidence(
                UUID.fromString("72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14"),
                "本科生学生手册（2025版）", "2025版", "PDF 第 77 页，第 18 条",
                "全日制普通本科生的专业选修课", "专业选修课成绩不合格者可申请重修学习。"));
        return courseType -> Optional.ofNullable(evidence.get(courseType));
    }

    private static CreditLimitEvidence creditLimitEvidence() {
        return new CreditLimitEvidence(
                UUID.fromString("f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5"),
                "本科生学生手册（2025版）", "2025版", "PDF 第 12 页",
                "全日制普通本科生", "每学期公共选修课最高修读 4 学分。");
    }
}
