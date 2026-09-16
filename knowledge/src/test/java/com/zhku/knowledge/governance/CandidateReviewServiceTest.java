package com.zhku.knowledge.governance;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateReviewServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-12T05:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void shouldMoveSubmissionIntoReviewWhenEditingPendingCandidate() {
        InMemoryGovernanceRepository repository = repository(
                KnowledgeSubmissionStatus.EXTRACTED
        );
        KnowledgeCandidate candidate = repository.onlyCandidate();
        CandidateReviewService service = new CandidateReviewService(
                repository,
                FIXED_CLOCK
        );

        KnowledgeCandidate updated = service.update(candidate.id(), edit());

        assertThat(updated).satisfies(value -> {
            assertThat(value.reviewStatus())
                    .isEqualTo(CandidateReviewStatus.PENDING_REVIEW);
            assertThat(value.editedText()).isEqualTo("公共选修课最高不得超过 4 学分。");
            assertThat(value.sectionTitle()).isEqualTo("公共选修课程学分上限");
            assertThat(value.sourceLocator()).isEqualTo("PDF 第 77 页，第 10 条");
        });
        assertThat(repository.onlySubmission().status())
                .isEqualTo(KnowledgeSubmissionStatus.IN_REVIEW);
        assertThat(repository.events)
                .extracting(KnowledgeReviewEvent::actionType)
                .containsExactly("UPDATE_CANDIDATE");
    }

    @Test
    void shouldRequireReasonBeforeDiscardingCandidate() {
        InMemoryGovernanceRepository repository = repository(
                KnowledgeSubmissionStatus.EXTRACTED
        );
        KnowledgeCandidate candidate = repository.onlyCandidate();
        CandidateReviewService service = new CandidateReviewService(
                repository,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.discard(candidate.id(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("丢弃原因");

        KnowledgeCandidate discarded = service.discard(
                candidate.id(),
                "该页是目录，不构成可回答的校园规则。"
        );

        assertThat(discarded.reviewStatus())
                .isEqualTo(CandidateReviewStatus.DISCARDED);
        assertThat(discarded.discardReason())
                .isEqualTo("该页是目录，不构成可回答的校园规则。");
        assertThat(repository.onlySubmission().status())
                .isEqualTo(KnowledgeSubmissionStatus.IN_REVIEW);
    }

    @Test
    void shouldApproveEditedCandidateWithTraceableReviewNote() {
        InMemoryGovernanceRepository repository = repository(
                KnowledgeSubmissionStatus.EXTRACTED
        );
        CandidateReviewService service = new CandidateReviewService(
                repository,
                FIXED_CLOCK
        );
        KnowledgeCandidate edited = service.update(
                repository.onlyCandidate().id(),
                edit()
        );

        KnowledgeCandidate approved = service.approve(
                edited.id(),
                "已对照学生手册原页核验。"
        );

        assertThat(approved.reviewStatus())
                .isEqualTo(CandidateReviewStatus.APPROVED);
        assertThat(approved.reviewNote())
                .isEqualTo("已对照学生手册原页核验。");
        assertThat(repository.events)
                .extracting(KnowledgeReviewEvent::actionType)
                .containsExactly("UPDATE_CANDIDATE", "APPROVE_CANDIDATE");
    }

    @Test
    void shouldSplitPendingCandidateIntoTwoPendingCandidates() {
        InMemoryGovernanceRepository repository = repository(
                KnowledgeSubmissionStatus.EXTRACTED
        );
        KnowledgeCandidate candidate = repository.onlyCandidate();
        CandidateReviewService service = new CandidateReviewService(
                repository,
                FIXED_CLOCK
        );

        List<KnowledgeCandidate> splitCandidates = service.split(
                candidate.id(),
                new CandidateSplitCommand(
                        "第一段候选内容",
                        "第二段候选内容"
                )
        );

        assertThat(splitCandidates).hasSize(2).allSatisfy(value -> {
            assertThat(value.reviewStatus())
                    .isEqualTo(CandidateReviewStatus.PENDING_REVIEW);
            assertThat(value.rawText()).isEqualTo(candidate.rawText());
            assertThat(value.reviewNote()).isNull();
        });
        assertThat(splitCandidates).extracting(KnowledgeCandidate::editedText)
                .containsExactly("第一段候选内容", "第二段候选内容");
        assertThat(repository.onlySubmission().status())
                .isEqualTo(KnowledgeSubmissionStatus.IN_REVIEW);
    }

    @Test
    void shouldRejectAnyReviewActionForArchivedSubmission() {
        InMemoryGovernanceRepository repository = repository(
                KnowledgeSubmissionStatus.ARCHIVED
        );
        CandidateReviewService service = new CandidateReviewService(
                repository,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.update(
                repository.onlyCandidate().id(),
                edit()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已归档");
    }

    private static CandidateEditCommand edit() {
        return new CandidateEditCommand(
                77,
                77,
                "公共选修课最高不得超过 4 学分。",
                "公共选修课程学分上限",
                "PDF 第 77 页，第 10 条"
        );
    }

    private static InMemoryGovernanceRepository repository(
            KnowledgeSubmissionStatus status
    ) {
        UUID submissionId = UUID.fromString("2d662a42-d984-464c-a4a0-0a8d4f964a51");
        UUID candidateId = UUID.fromString("48a32f3d-274f-4d4f-9260-a0477a3c4f04");
        KnowledgeSubmission submission = new KnowledgeSubmission(
                submissionId, "STUDENT_HANDBOOK", "PUBLIC_ELECTIVE",
                "本科生学生手册（测试版）", "2025版", "学校公开资料",
                null, "全日制普通本科生", LocalDate.of(2025, 9, 1), true,
                LocalDate.of(2025, 9, 1), true, "review-test.pdf",
                "测试学生手册.pdf", "B".repeat(64), status,
                "仅供本机审核。", FIXED_CLOCK.instant(), FIXED_CLOCK.instant()
        );
        KnowledgeCandidate candidate = new KnowledgeCandidate(
                candidateId, submissionId, 77, 77, "原始整页文本", null,
                null, null, CandidateReviewStatus.PENDING_REVIEW, null, null,
                FIXED_CLOCK.instant(), FIXED_CLOCK.instant()
        );
        return new InMemoryGovernanceRepository(submission, candidate);
    }

    private static final class InMemoryGovernanceRepository
            implements KnowledgeGovernanceRepository {

        private final Map<UUID, KnowledgeSubmission> submissions = new HashMap<>();
        private final Map<UUID, KnowledgeCandidate> candidates = new HashMap<>();
        private final List<KnowledgeReviewEvent> events = new ArrayList<>();

        private InMemoryGovernanceRepository(
                KnowledgeSubmission submission,
                KnowledgeCandidate candidate
        ) {
            submissions.put(submission.id(), submission);
            candidates.put(candidate.id(), candidate);
        }

        @Override
        public void createSubmission(KnowledgeSubmission submission) {
            submissions.put(submission.id(), submission);
        }

        @Override
        public void createSubmissionWithEvent(
                KnowledgeSubmission submission,
                KnowledgeReviewEvent event
        ) {
            createSubmission(submission);
            appendEvent(event);
        }

        @Override
        public Optional<KnowledgeSubmission> findSubmission(UUID submissionId) {
            return Optional.ofNullable(submissions.get(submissionId));
        }

        @Override
        public List<KnowledgeSubmission> listSubmissions() {
            return List.copyOf(submissions.values());
        }

        @Override
        public Optional<KnowledgeCandidate> findCandidate(UUID candidateId) {
            return Optional.ofNullable(candidates.get(candidateId));
        }

        @Override
        public List<KnowledgeCandidate> listCandidates(UUID submissionId) {
            return candidates.values().stream()
                    .filter(candidate -> candidate.submissionId().equals(submissionId))
                    .sorted(Comparator.comparing(KnowledgeCandidate::createdAt)
                            .thenComparing(KnowledgeCandidate::id))
                    .toList();
        }

        @Override
        public void saveCandidates(
                UUID submissionId,
                List<KnowledgeCandidate> values
        ) {
            values.forEach(candidate -> candidates.put(candidate.id(), candidate));
        }

        @Override
        public void completeExtraction(
                UUID submissionId,
                List<KnowledgeCandidate> values,
                KnowledgeReviewEvent event
        ) {
            saveCandidates(submissionId, values);
            appendEvent(event);
        }

        @Override
        public void updateCandidate(KnowledgeCandidate candidate) {
            candidates.put(candidate.id(), candidate);
        }

        @Override
        public void appendEvent(KnowledgeReviewEvent event) {
            events.add(event);
        }

        @Override
        public void markSubmissionInReview(UUID submissionId) {
            KnowledgeSubmission current = submissions.get(submissionId);
            submissions.put(submissionId, new KnowledgeSubmission(
                    current.id(), current.documentFamily(), current.knowledgeScope(),
                    current.title(), current.sourceVersion(), current.sourceDescription(),
                    current.sourceUrl(), current.applicableScope(),
                    current.publicationDate(), current.publicationDateConfirmed(),
                    current.effectiveDate(), current.effectiveDateConfirmed(),
                    current.storageKey(), current.originalFileName(), current.contentChecksum(),
                    KnowledgeSubmissionStatus.IN_REVIEW, current.reviewNotice(),
                    current.createdAt(), FIXED_CLOCK.instant()
            ));
        }

        @Override
        public Optional<UUID> findPublishedSourceId(
                String documentFamily,
                String knowledgeScope
        ) {
            return Optional.empty();
        }

        @Override
        public void markSubmissionPublished(UUID submissionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markSubmissionArchived(UUID submissionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void completePublicationInTransaction(
                Connection connection,
                UUID submissionId,
                KnowledgeReviewEvent event
        ) throws SQLException {
            throw new UnsupportedOperationException();
        }

        private KnowledgeSubmission onlySubmission() {
            return submissions.values().stream().findFirst().orElseThrow();
        }

        private KnowledgeCandidate onlyCandidate() {
            return candidates.values().stream()
                    .min(Comparator.comparing(KnowledgeCandidate::createdAt)
                            .thenComparing(KnowledgeCandidate::id))
                    .orElseThrow();
        }
    }
}
