package com.zhku.knowledge.serviceguide;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 清单解析器：只有"有当前有效证据"的清单才返回，其余一律转人工。
 *
 * <p>解析器是清单能否下发的唯一闸门——过期、替代、未发布（权限不足/资格待确认）在仓储层
 * 就表现为"查不到已发布学生可见版本"，解析器统一按规则缺失转人工，不区分具体原因
 * （区分会诱导把内部细节泄露给学生）。</p>
 */
class ServiceGuideResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");

    private static OfficialChannel office() {
        return new OfficialChannel(1, OfficialChannel.ChannelType.SCHOOL_OFFICE,
                "教务处学籍科", "教务处 201 室", "PDF 第 77 页",
                Instant.parse("2026-09-20T00:00:00Z"));
    }

    private static ServiceGuide guide(Instant publishedAt, Instant validUntil) {
        return new ServiceGuide("elective-adjust", "2025版", "选课调整办理指引",
                "全日制本科生",
                List.of(new GuideStep(1, "查看当学期选课通知", false),
                        new GuideStep(2, "按通知到指定办公室确认", true)),
                List.of(new ServiceGuide.Material(1, "学生证", null, true)),
                List.of(office()),
                List.of(new ServiceGuide.RiskNotice(1, "以当学期官方通知为准")),
                "PUBLIC_ELECTIVE_RULE#2025版", "COURSE_NOTICE#2026-2027-1",
                "审核说明", true, publishedAt, validUntil);
    }

    @Test
    void shouldReturnGuideWithStepsMaterialsChannelsAndNoticesWhenCurrent() {
        ServiceGuideResolver resolver = new ServiceGuideResolver(
                (id, version) -> Optional.of(guide(NOW.minusSeconds(86400), null)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        GuideResolution resolution = resolver.resolve("elective-adjust", "2025版");

        assertThat(resolution.handoffRequired()).isFalse();
        assertThat(resolution.guide()).isNotNull();
        assertThat(resolution.guide().steps()).extracting(GuideStep::stepOrder)
                .containsExactly(1, 2);
        assertThat(resolution.guide().materials()).hasSize(1);
        assertThat(resolution.guide().channels()).hasSize(1);
        assertThat(resolution.guide().riskNotices()).hasSize(1);
        assertThat(resolution.guide().sourceRuleReference())
                .isEqualTo("PUBLIC_ELECTIVE_RULE#2025版");
    }

    @Test
    void shouldHandOffWhenGuideIsMissingOrNotStudentVisible() {
        // 未发布 / 内部参考 / 资格待确认：仓储层都不会给出学生可见版本 → 规则缺失 → 转人工。
        ServiceGuideResolver resolver = new ServiceGuideResolver(
                (id, version) -> Optional.empty(), Clock.fixed(NOW, ZoneOffset.UTC));

        GuideResolution resolution = resolver.resolve("elective-adjust", "2025版");

        assertThat(resolution.handoffRequired()).isTrue();
        assertThat(resolution.guide()).isNull();
        assertThat(resolution.handoffReason())
                .isEqualTo(HumanHandoffReason.RULE_MISSING);
        assertThat(resolution.guideResolutionMessage()).contains("人工");
    }

    @Test
    void shouldHandOffWhenGuideValidityHasEnded() {
        // 有效期上界为排他：validUntil = now 时已失效，不能按过期清单给"下一步"。
        ServiceGuideResolver resolver = new ServiceGuideResolver(
                (id, version) -> Optional.of(guide(NOW.minusSeconds(86400), NOW)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        GuideResolution resolution = resolver.resolve("elective-adjust", "2025版");

        assertThat(resolution.handoffRequired()).isTrue();
        assertThat(resolution.handoffReason()).isEqualTo(HumanHandoffReason.RULE_MISSING);
    }

    @Test
    void shouldHandOffWhenGuideHasNoReviewedChannel() {
        // 清单存在但没有一条带来源定位的官方渠道：无法告诉学生"去哪办"，只能转人工，
        // 不得自己补一个渠道（渠道只能来自审核资料）。
        ServiceGuideResolver resolver = new ServiceGuideResolver(
                (id, version) -> Optional.of(new ServiceGuide("elective-adjust", "2025版", "选课调整办理指引",
                        "全日制本科生",
                        List.of(new GuideStep(1, "第一步", false)), List.of(), List.of(),
                        List.of(new ServiceGuide.RiskNotice(1, "提示")),
                        null, null, "审核说明", true, NOW.minusSeconds(86400), null)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        GuideResolution resolution = resolver.resolve("elective-adjust", "2025版");

        assertThat(resolution.handoffRequired()).isTrue();
        assertThat(resolution.handoffReason()).isEqualTo(HumanHandoffReason.RULE_MISSING);
    }
}
