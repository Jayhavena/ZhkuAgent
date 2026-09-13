package com.yupi.yuaiagent.zhku.knowledge.answer;

import com.yupi.yuaiagent.zhku.knowledge.publication.PublishedKnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * 有依据问答服务测试。
 *
 * <p>测试使用固定检索结果验证服务边界，不依赖数据库状态；真实数据库检索另由验收测试覆盖。</p>
 */
class GroundedAnswerServiceTest {

    @Test
    void shouldReturnGroundedAnswerWithCitationForSupportedQuestion() {
        PublishedKnowledgeSearcher searcher = keyword -> {
            assertThat(keyword).isEqualTo("每学期公共选修课最高");
            return List.of(new PublishedKnowledgeChunk(
                    UUID.fromString("2a04fd61-529e-4e6b-8ad6-9666f21a5037"),
                    "本科生学生手册（2025版）",
                    "PDF 第 76—77 页，第 8、10 条",
                    76,
                    "每学期公共选修课最高不得超过 4 学分。"
            ));
        };
        GroundedAnswerService service = new GroundedAnswerService(searcher);

        GroundedAnswer answer = service.answer("一学期最多能选多少公共选修课学分？");

        assertThat(answer.mode()).isEqualTo(AnswerMode.GROUNDED);
        assertThat(answer.answer()).contains("最高不得超过 4 学分");
        assertThat(answer.citations())
                .extracting(
                        GroundedCitation::sourceTitle,
                        GroundedCitation::sourceLocator,
                        GroundedCitation::pageNumber
                )
                .containsExactly(tuple(
                        "本科生学生手册（2025版）",
                        "PDF 第 76—77 页，第 8、10 条",
                        76
                ));
    }

    @Test
    void shouldReturnInsufficientEvidenceWhenSupportedQuestionHasNoPublishedChunk() {
        GroundedAnswerService service = new GroundedAnswerService(
                keyword -> List.of()
        );

        GroundedAnswer answer = service.answer("一学期最多能选多少公共选修课学分？");

        assertThat(answer.mode()).isEqualTo(AnswerMode.INSUFFICIENT_EVIDENCE);
        assertThat(answer.citations()).isEmpty();
        assertThat(answer.answer()).contains("没有找到足够的已发布资料");
    }

    @Test
    void shouldReturnInsufficientEvidenceForQuestionOutsidePublishedScope() {
        GroundedAnswerService service = new GroundedAnswerService(
                keyword -> {
                    throw new AssertionError("资料范围外问题不应进入检索");
                }
        );

        GroundedAnswer answer = service.answer("奖学金申请截止日期是什么时候？");

        assertThat(answer.mode()).isEqualTo(AnswerMode.INSUFFICIENT_EVIDENCE);
        assertThat(answer.citations()).isEmpty();
        assertThat(answer.answer()).contains("当前已发布资料未覆盖");
    }
}
