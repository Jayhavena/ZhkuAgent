package com.zhku.knowledge.publication;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 本地检索展示测试，确保结果保留可核验出处且不暴露原始文件路径。 */
class PublishedKnowledgeSearchPresenterTest {

    private final PublishedKnowledgeSearchPresenter presenter =
            new PublishedKnowledgeSearchPresenter();

    @Test
    void shouldRenderKeywordHitWithSourceLocatorAndPageNumber() {
        String result = presenter.present(
                "每学期公共选修课最高",
                List.of(new PublishedKnowledgeChunk(
                        UUID.fromString("2a04fd61-529e-4e6b-8ad6-9666f21a5037"),
                        "本科生学生手册（2025版）",
                        "PDF 第 77 页，第 10 条",
                        77,
                        "每学期公共选修课最高不得超过 4 学分。"
                ))
        );

        assertThat(result).contains(
                "检索词：每学期公共选修课最高",
                "命中片段：1 条",
                "本科生学生手册（2025版）",
                "PDF 第 77 页，第 10 条",
                "物理页码：77",
                "每学期公共选修课最高不得超过 4 学分。"
        );
        assertThat(result).doesNotContain("data/raw");
    }

    @Test
    void shouldRenderClearMessageWhenKeywordHasNoPublishedHit() {
        String result = presenter.present("不存在的测试词", List.of());

        assertThat(result).contains(
                "检索词：不存在的测试词",
                "命中片段：0 条",
                "未找到已发布的匹配资料"
        );
    }
}
