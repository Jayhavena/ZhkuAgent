package com.zhku.knowledge.conversation.elective;

import com.zhku.knowledge.publication.ReviewedKnowledgeJsonReader;
import com.zhku.knowledge.publication.ReviewedKnowledgePublication;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 防止阶段 4 的学生可见证据清单被误改为未审核或不可发布状态。 */
class ElectiveRetakeEvidenceCurationTest {

    @Test
    void shouldReadApprovedStudentVisibleRetakeEvidenceWithBothRuleBranches() throws Exception {
        ReviewedKnowledgePublication publication = new ReviewedKnowledgeJsonReader().read(Path.of(
                "data/curation/2025版学生手册2025.9.2-选修课重修审核知识片段.json"
        ));

        assertThat(publication)
                .extracting(
                        ReviewedKnowledgePublication::sourceVersion,
                        ReviewedKnowledgePublication::studentVisible,
                        ReviewedKnowledgePublication::sharingRightsConfirmed,
                        ReviewedKnowledgePublication::reviewStatus
                )
                .containsExactly("2025版", true, true, ReviewedKnowledgePublication.APPROVED_STATUS);
        assertThat(publication.chunks())
                .extracting(chunk -> chunk.content())
                .containsExactly(
                        "公共选修课不办理缓考，成绩不合格者不安排重修。",
                        "专业选修课成绩不合格者可申请重修学习。"
                );
    }
}
