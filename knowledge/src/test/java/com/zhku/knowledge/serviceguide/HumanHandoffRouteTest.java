package com.zhku.knowledge.serviceguide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 人工渠道路由：证据冲突、个案认定、规则缺失、模型拒答都必须进入**正确**的人工渠道。
 *
 * <p>路由只做"选择"，不做"生成"——渠道一律来自清单已审核的渠道集合，选不出合适类型时
 * 退回全部已审核渠道，绝不允许拼出审核资料里没有的电话、链接或地点。</p>
 */
class HumanHandoffRouteTest {

    private static OfficialChannel channel(int order, OfficialChannel.ChannelType type, String value) {
        return new OfficialChannel(order, type, "名称", value, "PDF 第 77 页",
                java.time.Instant.parse("2026-09-20T00:00:00Z"));
    }

    @Test
    void shouldRouteEvidenceConflictAndIndividualAssessmentToSchoolOffice() {
        OfficialChannel office = channel(1, OfficialChannel.ChannelType.SCHOOL_OFFICE, "教务处 201 室");
        OfficialChannel website = channel(2, OfficialChannel.ChannelType.WEBSITE, "https://jwc.example.edu.cn");

        for (HumanHandoffReason reason : List.of(
                HumanHandoffReason.EVIDENCE_CONFLICT,
                HumanHandoffReason.INDIVIDUAL_ASSESSMENT)) {
            HumanHandoffRoute.HandoffDecision decision = HumanHandoffRoute.decide(reason, List.of(website, office));

            assertThat(decision.handoffReason()).isEqualTo(reason);
            // 个案与证据冲突需要当面/人工核对，优先给办公室渠道，而不是把学生推向网页。
            assertThat(decision.channels()).containsExactly(office);
        }
    }

    @Test
    void shouldFallBackToAllReviewedChannelsWhenPreferredTypeAbsent() {
        OfficialChannel phone = channel(1, OfficialChannel.ChannelType.PHONE, "0755-12345678");

        HumanHandoffRoute.HandoffDecision decision = HumanHandoffRoute.decide(
                HumanHandoffReason.EVIDENCE_CONFLICT, List.of(phone));

        // 已审核渠道里没有办公室类渠道：退回全部已审核渠道，仍不发明任何联系方式。
        assertThat(decision.channels()).containsExactly(phone);
    }

    @Test
    void shouldNeverInventChannelsWhenReviewedListIsEmpty() {
        for (HumanHandoffReason reason : HumanHandoffReason.values()) {
            HumanHandoffRoute.HandoffDecision decision = HumanHandoffRoute.decide(reason, List.of());

            assertThat(decision.channels()).isEmpty();
            assertThat(decision.message()).contains("学校官方渠道");
            assertThat(decision.message()).doesNotContain("0755", "http");
        }
    }

    @Test
    void shouldRouteModelRefusalToAnyReviewedChannelInReviewedOrder() {
        OfficialChannel office = channel(1, OfficialChannel.ChannelType.SCHOOL_OFFICE, "教务处 201 室");
        OfficialChannel phone = channel(2, OfficialChannel.ChannelType.PHONE, "0755-12345678");

        HumanHandoffRoute.HandoffDecision decision = HumanHandoffRoute.decide(
                HumanHandoffReason.MODEL_REFUSAL, List.of(office, phone));

        // 模型拒答没有特定渠道偏好：按审核资料给定的顺序原样呈现，不重排、不筛选。
        assertThat(decision.channels()).containsExactly(office, phone);
    }
}
