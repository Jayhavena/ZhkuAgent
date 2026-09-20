package com.zhku.knowledge.serviceguide;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 办事清单模型约束：步骤顺序、渠道与来源必填、材料与风险提示约束。
 *
 * <p>这些约束是"渠道只能来自审核资料"在模型层的落点：没有来源定位的渠道不允许存在，
 * 否则模型或人都能往清单里塞未经审核的联系方式。</p>
 */
class ServiceGuideModelTest {

    private static OfficialChannel channel(int order, String value, String locator) {
        return new OfficialChannel(order, OfficialChannel.ChannelType.SCHOOL_OFFICE,
                "教务处学籍科", value, locator, Instant.parse("2026-09-20T00:00:00Z"));
    }

    @Test
    void shouldRejectStepsThatDoNotStartAtOneOrSkipNumbers() {
        var valid = List.of(new GuideStep(1, "在教务系统提交申请", false),
                new GuideStep(2, "按通知到指定办公室确认", true));
        var guide = new ServiceGuide("elective-adjust", "2025版", "选课调整办理指引",
                "全日制本科生", valid, List.of(), List.of(channel(1, "教务处 201 室", "PDF 第 77 页")),
                List.of(new ServiceGuide.RiskNotice(1, "以当学期通知为准")),
                "PUBLIC_ELECTIVE_RULE#2025版", null, "审核说明", true,
                Instant.parse("2026-09-20T00:00:00Z"), null);
        assertThat(guide.steps()).hasSize(2);

        // 步骤从 2 开始（缺 1）：禁止——引用卡要按序号展示，断号会被读成"漏了一步"。
        assertThatThrownBy(() -> new ServiceGuide("g", "v", "t", "s",
                List.of(new GuideStep(2, "第二步", false)), List.of(),
                List.of(channel(1, "教务处", "PDF 第 77 页")),
                List.of(new ServiceGuide.RiskNotice(1, "提示")),
                null, null, "审核", true, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("步骤");

        // 步骤断号（1、3）：同样禁止。
        assertThatThrownBy(() -> new ServiceGuide("g", "v", "t", "s",
                List.of(new GuideStep(1, "第一步", false), new GuideStep(3, "第三步", false)),
                List.of(), List.of(channel(1, "教务处", "PDF 第 77 页")),
                List.of(new ServiceGuide.RiskNotice(1, "提示")),
                null, null, "审核", true, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("连续");
    }

    @Test
    void shouldRejectChannelWithoutValueOrSourceLocator() {
        // 渠道没有可下发的值（电话/地点/网址为空）：禁止——它无法回答"去哪办"。
        assertThatThrownBy(() -> new OfficialChannel(1, OfficialChannel.ChannelType.PHONE,
                "咨询电话", "  ", "PDF 第 77 页", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("渠道");

        // 渠道没有来源定位：禁止——这是"渠道只能来自审核资料"的落点，
        // 没有定位就无法核对这条渠道是不是审核资料里写明的。
        assertThatThrownBy(() -> new OfficialChannel(1, OfficialChannel.ChannelType.PHONE,
                "咨询电话", "0755-12345678", "  ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("来源");
    }

    @Test    void shouldRequireRiskNoticeAndRejectBlankMaterialName() {
        // 清单没有风险提示：禁止——清单是"知道下一步怎么做"，缺提示会让人误以为可以照单全收。
        assertThatThrownBy(() -> new ServiceGuide("g", "v", "t", "s",
                List.of(new GuideStep(1, "第一步", false)), List.of(),
                List.of(channel(1, "教务处", "PDF 第 77 页")),
                List.of(),
                null, null, "审核", true, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("风险提示");

        // 材料名为空：禁止——空材料名在回答卡上无法展示。
        assertThatThrownBy(() -> new ServiceGuide.Material(1, "  ", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("材料");
    }

    @Test
    void shouldBeImmutableAgainstCallerMutations() {
        List<GuideStep> steps = new ArrayList<>();
        steps.add(new GuideStep(1, "第一步", false));
        List<OfficialChannel> channels = new ArrayList<>();
        channels.add(channel(1, "教务处 201 室", "PDF 第 77 页"));

        ServiceGuide guide = new ServiceGuide("g", "v", "t", "s", steps,
                List.of(), channels, List.of(new ServiceGuide.RiskNotice(1, "提示")),
                null, null, "审核", true, Instant.now(), null);

        // 调用方在构造后修改传入列表，不得影响清单本身——清单是不可变事实，不是可变草稿。
        steps.add(new GuideStep(2, "第二步", false));
        channels.clear();
        assertThat(guide.steps()).hasSize(1);
        assertThat(guide.channels()).hasSize(1);
    }
}
