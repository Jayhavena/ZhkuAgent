package com.zhku.knowledge.answer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 本地问答展示测试，确保有依据与证据不足两种状态对人工核验足够明确。 */
class GroundedAnswerPresenterTest {

    private final GroundedAnswerPresenter presenter = new GroundedAnswerPresenter();

    @Test
    void shouldRenderGroundedAnswerWithCitation() {
        String result = presenter.present(new GroundedAnswer(
                AnswerMode.GROUNDED,
                "每学期公共选修课最高不得超过 4 学分。",
                List.of(new GroundedCitation(
                        "本科生学生手册（2025版）",
                        "PDF 第 76—77 页，第 8、10 条",
                        76
                ))
        ));

        assertThat(result).contains(
                "回答模式：GROUNDED",
                "每学期公共选修课最高不得超过 4 学分。",
                "本科生学生手册（2025版）",
                "PDF 第 76—77 页，第 8、10 条",
                "物理页码：76"
        );
    }

    @Test
    void shouldRenderInsufficientEvidenceWithoutCitationSection() {
        String result = presenter.present(new GroundedAnswer(
                AnswerMode.INSUFFICIENT_EVIDENCE,
                "当前已发布资料未覆盖该问题，无法给出确定性回答。",
                List.of()
        ));

        assertThat(result).contains(
                "回答模式：INSUFFICIENT_EVIDENCE",
                "当前已发布资料未覆盖该问题，无法给出确定性回答。"
        );
        assertThat(result).doesNotContain("出处：");
    }
}
