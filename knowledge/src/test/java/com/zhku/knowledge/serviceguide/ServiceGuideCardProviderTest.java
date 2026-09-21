package com.zhku.knowledge.serviceguide;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 卡片提供者：把"决策点 → 卡片"这一步做完整，并保证两条硬规则——
 * ① 清单当前有效时给完整卡；② 无论清单是否可用，转人工卡都不携带步骤、不发明渠道。
 */
class ServiceGuideCardProviderTest {

    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static OfficialChannel office() {
        return new OfficialChannel(1, OfficialChannel.ChannelType.SCHOOL_OFFICE,
                "教务处学籍科", "教务处 201 室", "PDF 第 77 页", NOW);
    }

    private static ServiceGuide guide() {
        return new ServiceGuide("elective-adjust", "2025版", "选课调整办理指引",
                "全日制本科生",
                List.of(new GuideStep(1, "查看当学期选课通知", false)),
                List.of(),
                List.of(office()),
                List.of(new ServiceGuide.RiskNotice(1, "以当学期官方通知为准")),
                "PUBLIC_ELECTIVE_RULE#2025版", null, "审核说明", true, NOW, null);
    }

    private static ServiceGuideCardProvider provider(Optional<ServiceGuide> found) {
        ServiceGuideResolver resolver = new ServiceGuideResolver(
                (identifier, version) -> found, CLOCK);
        return new ServiceGuideCardProvider(ServiceGuideBindings.firstBatch(), resolver);
    }

    @Test
    void shouldProvideFullCardWhenGuideIsCurrent() {
        var card = provider(Optional.of(guide()))
                .fullCard(ServiceGuideBindings.ELECTIVE_ADJUST);

        assertThat(card).isPresent();
        assertThat(card.get().handoffOnly()).isFalse();
        assertThat(card.get().nextSteps()).hasSize(1);
        assertThat(card.get().officialChannels()).hasSize(1);
        assertThat(card.get().confirmations()).containsExactly("以当学期官方通知为准");
    }

    @Test
    void shouldProvideNoFullCardWhenGuideIsNotResolvable() {
        // 清单未发布/已过期：不给"完整卡"——半个清单会被误读成可办结方案。
        assertThat(provider(Optional.empty())
                .fullCard(ServiceGuideBindings.ELECTIVE_ADJUST)).isEmpty();
    }

    @Test
    void shouldProvideNoCardForUnknownBindingKey() {
        var provider = provider(Optional.of(guide()));

        assertThat(provider.fullCard("not-bound")).isEmpty();
        assertThat(provider.handoffCard("not-bound", HumanHandoffReason.RULE_MISSING)).isEmpty();
    }

    @Test
    void shouldKeepReviewedChannelsOnHandoffCardWhenGuideExists() {
        var card = provider(Optional.of(guide()))
                .handoffCard(ServiceGuideBindings.ELECTIVE_ADJUST,
                        HumanHandoffReason.INDIVIDUAL_ASSESSMENT);

        assertThat(card).isPresent();
        // 转人工卡：只留人工渠道 + 需确认事项，绝不带步骤。
        assertThat(card.get().handoffOnly()).isTrue();
        assertThat(card.get().nextSteps()).isEmpty();
        assertThat(card.get().officialChannels()).extracting(ServiceGuideCard.Channel::value)
                .containsExactly("教务处 201 室");
    }

    @Test
    void shouldNotInventChannelsWhenGuideIsNotResolvable() {
        var card = provider(Optional.empty())
                .handoffCard(ServiceGuideBindings.ELECTIVE_ADJUST,
                        HumanHandoffReason.RULE_MISSING);

        assertThat(card).isPresent();
        assertThat(card.get().nextSteps()).isEmpty();
        // 无已审核渠道可下发：渠道列表为空，只给方向性说明，不编造电话/网址/地点。
        assertThat(card.get().officialChannels()).isEmpty();
    }
}
