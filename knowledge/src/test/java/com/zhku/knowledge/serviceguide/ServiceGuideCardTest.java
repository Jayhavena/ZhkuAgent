package com.zhku.knowledge.serviceguide;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 回答卡清单视图：正常路径映射完整，转人工路径"只留渠道与需确认事项"。
 */
class ServiceGuideCardTest {

    private static ServiceGuide guide() {
        return new ServiceGuide("elective-adjust", "2025版", "选课调整办理指引",
                "全日制本科生",
                List.of(new GuideStep(1, "查看当学期选课通知", false),
                        new GuideStep(2, "按通知到指定办公室确认", true)),
                List.of(new ServiceGuide.Material(1, "学生证", null, true)),
                List.of(new OfficialChannel(1, OfficialChannel.ChannelType.SCHOOL_OFFICE,
                                "教务处学籍科", "教务处 201 室", "PDF 第 77 页",
                                Instant.parse("2026-09-20T00:00:00Z")),
                        new OfficialChannel(2, OfficialChannel.ChannelType.PHONE,
                                "选课咨询专线", "0755-12345678", "PDF 第 77 页",
                                Instant.parse("2026-09-20T00:00:00Z"))),
                List.of(new ServiceGuide.RiskNotice(1, "以当学期官方通知为准")),
                "PUBLIC_ELECTIVE_RULE#2025版", null, "审核说明", true,
                Instant.parse("2026-09-20T00:00:00Z"), null);
    }

    @Test
    void shouldMapStepsConfirmationsAndChannelsFromGuide() {
        ServiceGuideCard card = ServiceGuideCard.from(guide());

        assertThat(card.handoffOnly()).isFalse();
        assertThat(card.nextSteps()).extracting(ServiceGuideCard.NextStep::order)
                .containsExactly(1, 2);
        assertThat(card.nextSteps().get(1).requiresIdentityCheck()).isTrue();
        assertThat(card.confirmations()).containsExactly("以当学期官方通知为准");
        assertThat(card.officialChannels()).extracting(ServiceGuideCard.Channel::channelType)
                .containsExactly("SCHOOL_OFFICE", "PHONE");
        assertThat(card.officialChannels()).extracting(ServiceGuideCard.Channel::value)
                .containsExactly("教务处 201 室", "0755-12345678");
    }

    @Test
    void shouldBuildHandoffOnlyCardWithoutAnyStep() {
        var decision = HumanHandoffRoute.decide(HumanHandoffReason.INDIVIDUAL_ASSESSMENT,
                guide().channels());

        ServiceGuideCard card = ServiceGuideCard.ofHandoff(
                HumanHandoffReason.INDIVIDUAL_ASSESSMENT, decision,
                List.of("个人剩余学分以培养方案为准"));

        assertThat(card.handoffOnly()).isTrue();
        // 高风险/个案场景：只保留人工渠道与需确认事项，绝不允许伪装成可办结步骤。
        assertThat(card.nextSteps()).isEmpty();
        assertThat(card.officialChannels()).extracting(ServiceGuideCard.Channel::channelType)
                .containsExactly("SCHOOL_OFFICE");
        assertThat(card.confirmations()).containsExactly("个人剩余学分以培养方案为准");
    }

    @Test
    void shouldRejectStepsOnHandoffOnlyCard() {
        assertThatThrownBy(() -> new ServiceGuideCard("g", "v", "t", null,
                List.of(new ServiceGuideCard.NextStep(1, "第一步", false)),
                List.of(), List.of(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("转人工");
    }
}
