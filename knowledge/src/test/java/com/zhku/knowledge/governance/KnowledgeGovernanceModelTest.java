package com.zhku.knowledge.governance;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeGovernanceModelTest {

    @Test
    void shouldRejectSubmissionOutsideStudentHandbookFamily() {
        assertThatThrownBy(() -> submission("OTHER_DOCUMENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STUDENT_HANDBOOK");
    }

    @Test
    void shouldRejectConfirmedPublicationDateWhenDateIsMissing() {
        assertThatThrownBy(() -> new KnowledgeSubmission(
                UUID.randomUUID(),
                "STUDENT_HANDBOOK",
                "ACADEMIC_STATUS",
                "本科生学生手册（2025版）",
                "2025版",
                "学校官网公开资料",
                null,
                "全日制普通本科生",
                null,
                true,
                LocalDate.of(2025, 9, 1),
                true,
                "submission.pdf",
                "2025版学生手册.pdf",
                "A".repeat(64),
                KnowledgeSubmissionStatus.DRAFT,
                "生效时间待官方确认。",
                Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发布日期");
    }

    @Test
    void shouldRejectApprovedCandidateWithoutTraceableReviewFields() {
        assertThatThrownBy(() -> new KnowledgeCandidate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                76,
                76,
                "原始文本",
                "编辑后文本",
                null,
                null,
                CandidateReviewStatus.APPROVED,
                "审核通过",
                null,
                Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("片段标题");
    }

    @Test
    void shouldKeepBlankPdfPageAsPendingCandidateForManualDiscard() {
        KnowledgeCandidate candidate = new KnowledgeCandidate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                12,
                12,
                "",
                null,
                null,
                null,
                CandidateReviewStatus.PENDING_REVIEW,
                null,
                null,
                Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z")
        );

        assertThat(candidate.rawText()).isEmpty();
        assertThat(candidate.reviewStatus())
                .isEqualTo(CandidateReviewStatus.PENDING_REVIEW);
    }

    private static KnowledgeSubmission submission(String documentFamily) {
        return new KnowledgeSubmission(
                UUID.randomUUID(),
                documentFamily,
                "ACADEMIC_STATUS",
                "本科生学生手册（2025版）",
                "2025版",
                "学校官网公开资料",
                "https://example.edu.cn/handbook",
                "全日制普通本科生",
                LocalDate.of(2025, 9, 1),
                true,
                LocalDate.of(2025, 9, 1),
                true,
                "submission.pdf",
                "2025版学生手册.pdf",
                "A".repeat(64),
                KnowledgeSubmissionStatus.DRAFT,
                "仅供本机审核。",
                Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z")
        );
    }
}
