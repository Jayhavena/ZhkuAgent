package com.zhku.knowledge.conversation.publicelective;

import com.zhku.knowledge.publication.ReviewedKnowledgeJsonReader;
import com.zhku.knowledge.publication.ReviewedKnowledgePublication;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 防止单学期学分上限的学生可见依据被误改为未审核或超出条款边界。 */
class CreditLimitEvidenceCurationTest {

    @Test
    void shouldReadApprovedStudentVisibleCreditLimitEvidence() throws Exception {
        ReviewedKnowledgePublication publication = new ReviewedKnowledgeJsonReader().read(Path.of(
                "data/curation/2025版学生手册2025.9.2-公共选修单学期学分上限审核知识片段.json"
        ));

        assertThat(publication)
                .extracting(
                        ReviewedKnowledgePublication::sourceVersion,
                        ReviewedKnowledgePublication::studentVisible,
                        ReviewedKnowledgePublication::sharingRightsConfirmed,
                        ReviewedKnowledgePublication::reviewStatus
                )
                .containsExactly("2025版", true, true, ReviewedKnowledgePublication.APPROVED_STATUS);
        assertThat(publication.chunks()).singleElement().satisfies(chunk -> {
            assertThat(chunk.sourceLocator()).isEqualTo("PDF 第 76—77 页，第 8、10 条");
            assertThat(chunk.content()).contains("每学期公共选修课最高不得超过 4 学分");
        });
    }
}
