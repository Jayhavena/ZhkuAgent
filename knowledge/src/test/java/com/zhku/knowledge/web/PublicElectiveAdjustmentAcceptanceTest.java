package com.zhku.knowledge.web;

import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.conversation.publicelective.AdjustmentEvidence;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveAdjustmentService;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.serviceguide.OfficialChannel;
import com.zhku.knowledge.serviceguide.ServiceGuide;
import com.zhku.knowledge.serviceguide.ServiceGuideBindings;
import com.zhku.knowledge.serviceguide.ServiceGuideCard;
import com.zhku.knowledge.serviceguide.ServiceGuideCardProvider;
import com.zhku.knowledge.serviceguide.ServiceGuideResolver;
import com.zhku.knowledge.serviceguide.GuideStep;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A-4 批2 验收：「选课调整怎么办理」必须命中规则级并挂上**完整**清单卡。
 *
 * <p>这是"活体可达性"的回归锚点：规则证据（chunk 7612 对应的条级定位）已在库，
 * 缺的是该问法的**显式主题分支**——没有它，问题落到第五级拒答、`answer=null`，
 * 完整卡就永远没有挂载点（现场实测即如此）。</p>
 *
 * <p>用例不依赖共享演示库数据：清单用内存仓储提供，避免"别人删了演示数据导致本用例变红"
 * （真实数据的活体验证由 A-4 批2 的真机复验承担）。</p>
 */
class PublicElectiveAdjustmentAcceptanceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String ADJUSTMENT_QUESTION = "公共选修课选课调整怎么办理";
    private static final String UNBOUND_QUESTION = "公共选修课奖学金怎么申请";

    private final ConversationStore store = new InMemoryConversationStore();

    @Test
    void shouldAnswerAdjustmentQuestionAtRuleLevelWithFullGuideCard() {
        LocalPreviewConversationFacade facade = facade(ServiceGuideBindings.ELECTIVE_ADJUST);

        LocalPreviewConversationFacade.ConversationReply reply = facade.sendMessage(
                facade.createConversation().sessionId(), ADJUSTMENT_QUESTION);

        // 规则级命中：有回答卡（旧字段语义不变），路由结论为高风险规则级。
        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.route()).isEqualTo(AnswerRoute.HIGH_RISK_RULE);
        assertThat(reply.answer()).isNotNull();
        // 依据必须是那条已发布的条级证据，而不是清单本身当依据。
        assertThat(reply.answer().sourceLocator()).isEqualTo("PDF 第 77 页，第 9、13 条");
        assertThat(reply.handoffRequired()).isFalse();

        // 完整清单卡：下一步 + 需确认事项 + 官方渠道。
        ServiceGuideCard card = reply.guideCard();
        assertThat(card).isNotNull();
        assertThat(card.handoffOnly()).isFalse();
        assertThat(card.nextSteps()).isNotEmpty();
        assertThat(card.confirmations()).isNotEmpty();
        assertThat(card.officialChannels()).isNotEmpty();
    }

    @Test
    void shouldKeepOnlyChannelsAndConfirmationsWhenEvidenceIsInsufficient() {
        LocalPreviewConversationFacade facade = facade(ServiceGuideBindings.ELECTIVE_ADJUST);

        LocalPreviewConversationFacade.ConversationReply reply = facade.sendMessage(
                facade.createConversation().sessionId(), "公共选修课教室在哪里");

        // 证据不足：仍转人工，但卡片只留渠道与需确认事项，绝不给"下一步"。
        assertThat(reply.route()).isEqualTo(AnswerRoute.INSUFFICIENT_EVIDENCE);
        assertThat(reply.handoffRequired()).isTrue();
        assertThat(reply.guideCard()).isNotNull();
        assertThat(reply.guideCard().handoffOnly()).isTrue();
        assertThat(reply.guideCard().nextSteps()).isEmpty();
        assertThat(reply.guideCard().officialChannels()).isNotEmpty();
        assertThat(reply.guideCard().confirmations()).isNotEmpty();
    }

    @Test
    void shouldNotAttachGuideCardWhenDecisionPointIsUnbound() {
        LocalPreviewConversationFacade facade = facade("not-bound");

        LocalPreviewConversationFacade.ConversationReply reply = facade.sendMessage(
                facade.createConversation().sessionId(), ADJUSTMENT_QUESTION);

        // 未绑定：不附卡（宁可没有指引，也不挂一份不相干的清单）。
        assertThat(reply.guideCard()).isNull();
    }

    private LocalPreviewConversationFacade facade(String bindingKey) {
        ElectiveRetakeConversationService retakeService = new ElectiveRetakeConversationService(
                store, courseType -> Optional.empty(), CLOCK, TTL);
        PublicElectiveCreditLimitService creditLimitService =
                new PublicElectiveCreditLimitService(Optional::empty);
        // 证据用内存替身：本用例锚定"路由 + 卡片"，不依赖共享演示库数据。
        PublicElectiveAdjustmentService adjustmentService = new PublicElectiveAdjustmentService(
                () -> Optional.of(new AdjustmentEvidence(
                        UUID.fromString("6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05"),
                        "本科生学生手册（2025版）·公共选修课修读与考核规则",
                        "2025版-公共选修修读与考核-学生可见",
                        "PDF 第 77 页，第 9、13 条",
                        "办理公共选修课选课与修读的全日制普通本科生",
                        "学生应根据教务处公布的公共选修课开课计划，在规定时间内完成网上选课；"
                                + "未选课不得参加课程考核或考核成绩无效。"
                                + "选课、退选、补选和改选应按照每学期《课程选课工作通知》办理。")));
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store, retakeService, creditLimitService, null, adjustmentService, CLOCK);
        LocalPreviewAnswerChain chain = new LocalPreviewAnswerChain(
                store, router, null, null, CLOCK);
        ServiceGuideCardProvider guideCards = new ServiceGuideCardProvider(
                ServiceGuideBindings.firstBatch(),
                new ServiceGuideResolver((identifier, version) -> Optional.of(guide()), CLOCK));
        return new LocalPreviewConversationFacade(
                store, chain, CLOCK, TTL, guideCards, bindingKey);
    }

    private static ServiceGuide guide() {
        return new ServiceGuide("elective-adjust", "2025版", "选课调整办理指引（公共选修课）",
                "全日制本科生（公共选修课）",
                List.of(new GuideStep(1, "查看教务处当学期《课程选课工作通知》。", false),
                        new GuideStep(2, "在规定时间内登录教务系统完成选课或调整。", false)),
                List.of(),
                List.of(new OfficialChannel(1, OfficialChannel.ChannelType.SCHOOL_OFFICE,
                        "【演示占位】教务处学籍科", "【演示占位】教务处办公室",
                        "演示占位：无审核资料依据", CLOCK.instant())),
                List.of(new ServiceGuide.RiskNotice(1, "以当学期《课程选课工作通知》为准。")),
                "PUBLIC_ELECTIVE_RULE#2025版", "COURSE_NOTICE#2026-2027-1", "审核说明", true,
                CLOCK.instant(), null);
    }
}
