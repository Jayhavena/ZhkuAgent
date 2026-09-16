package com.zhku.knowledge.conversation.publicelective;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 单学期学分上限规则的命中、边界与证据降级测试。 */
class PublicElectiveCreditLimitServiceTest {

    private static final CreditLimitEvidence EVIDENCE = new CreditLimitEvidence(
            UUID.fromString("f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5"),
            "本科生学生手册（2025版）",
            "2025版",
            "PDF 第 76—77 页，第 8、10 条",
            "办理公共选修课程选课的本科生；个人培养方案与当期通知仍需核对",
            "学生应以人才培养方案为选课依据，在规定学习年限内修读并取得规定学分；每学期公共选修课最高不得超过 4 学分。"
    );

    @Test
    void shouldAnswerTermCreditLimitWithPublishedEvidence() {
        PublicElectiveCreditLimitService service = new PublicElectiveCreditLimitService(
                () -> Optional.of(EVIDENCE)
        );

        CreditLimitReply reply = service.handle("一学期最多能选多少公共选修课学分？");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer())
                .extracting(CreditLimitAnswer::conclusion, CreditLimitAnswer::sourceLocator)
                .containsExactly("每学期公共选修课最高不得超过 4 学分。", "PDF 第 76—77 页，第 8、10 条");
    }

    @Test
    void shouldKeepPersonalEligibilityOutsideConclusionBoundary() {
        PublicElectiveCreditLimitService service = new PublicElectiveCreditLimitService(
                () -> Optional.of(EVIDENCE)
        );

        CreditLimitReply reply = service.handle("我这学期已经选了 3 学分公选课，还能再选 2 学分吗？");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer().basis()).contains("不承诺个人是否仍可选课");
    }

    @Test
    void shouldNotTreatNetworkElectiveRatioQuestionAsTermCreditLimit() {
        PublicElectiveCreditLimitService service = new PublicElectiveCreditLimitService(
                () -> Optional.of(EVIDENCE)
        );

        assertThat(service.handle("网络公共选修课最多能占多少学分？").type())
                .isEqualTo(ElectiveRetakeReplyType.OUT_OF_SCOPE);
    }

    @Test
    void shouldRefuseDefiniteConclusionWhenEvidenceIsUnavailable() {
        PublicElectiveCreditLimitService service = new PublicElectiveCreditLimitService(Optional::empty);

        assertThat(service.handle("公共选修课每学期学分上限是多少？").type())
                .isEqualTo(ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE);
    }
}
