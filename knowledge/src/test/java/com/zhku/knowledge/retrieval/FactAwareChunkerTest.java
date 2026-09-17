package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 事实感知切分的定位与哈希测试。
 *
 * <p>切分边界必须落在条款/章节标题上，使每个 chunk 自含可引用的最小事实单元；
 * 内容哈希随内容变化而变化，是索引审计判断"源片段是否已过期"的依据。</p>
 */
class FactAwareChunkerTest {

    private final FactAwareChunker chunker = new FactAwareChunker();

    @Test
    void shouldSplitAtClauseBoundaries() {
        String content = """
                学生手册节选
                第一条 学生每学期公共选修课最高修读 4 学分。
                第二条 公共选修课不办理缓考，不及格者应重修。
                """;

        List<ChunkDraft> chunks = chunker.chunk(content);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).sectionTitle()).isNull();
        assertThat(chunks.get(1).sectionTitle()).startsWith("第一条");
        assertThat(chunks.get(1).content()).contains("最高修读 4 学分");
        assertThat(chunks.get(2).sectionTitle()).startsWith("第二条");
        assertThat(chunks.get(2).content()).contains("不办理缓考");
        assertThat(chunks.get(2).content()).doesNotContain("第一条");
    }

    @Test
    void shouldSplitAtSectionHeadings() {
        String content = """
                一、选课时间
                本学期选课时间为 9 月 2 日至 9 月 5 日。
                二、选课入口
                学生通过教务系统网上选课。
                """;

        List<ChunkDraft> chunks = chunker.chunk(content);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).sectionTitle()).startsWith("一、");
        assertThat(chunks.get(0).content()).contains("9 月 2 日");
        assertThat(chunks.get(1).sectionTitle()).startsWith("二、");
        assertThat(chunks.get(1).content()).contains("网上选课");
    }

    @Test
    void shouldPreserveHandlingFactsInsideClauseChunk() {
        String content = """
                第三条 退改选办理时间为 2026-09-10 至 2026-09-15，办理入口为教务系统。
                """;

        List<ChunkDraft> chunks = chunker.chunk(content);

        // 通知办理事实（时间 + 入口）必须完整保留在同一 chunk 内，不得被拦腰截断。
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).contains("2026-09-10").contains("教务系统");
    }

    @Test
    void shouldAssignSequentialChunkIndex() {
        String content = """
                第一条 甲。
                第二条 乙。
                """;

        List<ChunkDraft> chunks = chunker.chunk(content);

        assertThat(chunks).extracting(ChunkDraft::chunkIndex).containsExactly(0, 1);
    }

    @Test
    void shouldComputeStableContentChecksum() {
        String content = "第一条 学生应按时选课。";

        List<ChunkDraft> first = chunker.chunk(content);
        List<ChunkDraft> second = chunker.chunk(content);

        assertThat(first.getFirst().contentChecksum())
                .isEqualTo(second.getFirst().contentChecksum())
                .hasSize(64);
        assertThat(chunker.chunk("第一条 学生应按时退课。").getFirst().contentChecksum())
                .as("内容变化必须导致哈希变化，否则索引过期审计失效")
                .isNotEqualTo(first.getFirst().contentChecksum());
    }

    @Test
    void shouldRejectBlankContent() {
        assertThatIllegalArgumentException().isThrownBy(() -> chunker.chunk("  "));
    }
}
