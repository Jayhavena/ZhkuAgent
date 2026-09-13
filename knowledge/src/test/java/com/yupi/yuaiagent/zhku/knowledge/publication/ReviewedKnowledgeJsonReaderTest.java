package com.yupi.yuaiagent.zhku.knowledge.publication;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/** 审核清单到发布输入的转换测试，防止导入器绕过人工审核状态。 */
class ReviewedKnowledgeJsonReaderTest {

    @Test
    void shouldReadApprovedPublicElectiveCandidateWithTraceableMetadata()
            throws IOException {
        ReviewedKnowledgeJsonReader reader = new ReviewedKnowledgeJsonReader();

        ReviewedKnowledgePublication publication = reader.read(Path.of(
                "data/curation/2025版学生手册2025.9.2-公共选修候选知识片段.json"
        ));

        assertThat(publication.sourceTitle()).isEqualTo("本科生学生手册（2025版）");
        assertThat(publication.reviewStatus()).isEqualTo("审核通过");
        assertThat(publication.studentVisible()).isFalse();
        assertThat(publication.sharingRightsConfirmed()).isFalse();
        assertThat(publication.publicationScope()).isEqualTo("本地验证");
        assertThat(publication.chunks())
                .extracting(
                        ReviewedKnowledgeChunk::chunkIndex,
                        ReviewedKnowledgeChunk::sourceLocator,
                        ReviewedKnowledgeChunk::pageNumber
                )
                .containsExactly(
                        tuple(7600, "PDF 第 76 页，第 2、4 条", 76),
                        tuple(7601, "PDF 第 76 页，第 5—6 条", 76),
                        tuple(7602, "PDF 第 76—77 页，第 8、10 条", 76),
                        tuple(7603, "PDF 第 77 页，第 9、13 条", 77),
                        tuple(7604, "PDF 第 77 页，第 11 条", 77),
                        tuple(7605, "PDF 第 77 页，第 14—15、18 条", 77)
                );
    }

    @Test
    void shouldRejectCandidateFileThatHasNotPassedReview() throws IOException {
        Path candidate = Files.createTempFile("zhku-unreviewed-", ".json");
        Files.writeString(candidate, """
                {
                  "source_id": "2a04fd61-529e-4e6b-8ad6-9666f21a5037",
                  "source_title": "测试资料",
                  "source_type": "学生手册",
                  "source_version": "测试版",
                  "review_status": "待审核",
                  "reviewed_at": "2026-09-10T00:00:00Z",
                  "candidate_chunks": []
                }
                """);

        assertThatThrownBy(() -> new ReviewedKnowledgeJsonReader().read(candidate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审核通过");
    }

    @Test
    void shouldRejectStudentVisibleCandidateWithoutConfirmedSharingRights()
            throws IOException {
        Path candidate = Files.createTempFile("zhku-unconfirmed-sharing-", ".json");
        Files.writeString(candidate, """
                {
                  "source_id": "a252f017-2de6-4d70-92e8-f4f775bdd532",
                  "source_title": "学生上传的课程资料",
                  "source_type": "用户上传资料",
                  "source_version": "测试版",
                  "review_status": "审核通过",
                  "reviewed_at": "2026-09-10T00:00:00Z",
                  "student_visible": true,
                  "publication_scope": "全体学生可见",
                  "review_notice": "仅供测试可见性边界。",
                  "sharing_rights_confirmed": false,
                  "candidate_chunks": [
                    {
                      "chunk_index": 1,
                      "title": "测试片段",
                      "source_locator": "PDF 第 1 页",
                      "evidence_summary": "测试资料内容。",
                      "review_status": "审核通过"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> new ReviewedKnowledgeJsonReader().read(candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分享权限");
    }
}
