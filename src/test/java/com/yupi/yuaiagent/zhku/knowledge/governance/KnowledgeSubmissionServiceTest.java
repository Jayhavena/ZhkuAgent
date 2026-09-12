package com.yupi.yuaiagent.zhku.knowledge.governance;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSubmissionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-12T03:00:00Z"),
            ZoneOffset.UTC
    );

    @TempDir
    Path storageDirectory;

    @Test
    void shouldCreateDraftThenExtractOnePendingCandidatePerPdfPage()
            throws IOException {
        InMemoryGovernanceRepository repository =
                new InMemoryGovernanceRepository();
        KnowledgeSubmissionService service = new KnowledgeSubmissionService(
                repository,
                new LocalKnowledgeFileStore(storageDirectory),
                new com.yupi.yuaiagent.zhku.knowledge.ingestion.PdfPageTextExtractor(),
                FIXED_CLOCK
        );

        KnowledgeSubmission submission = service.createSubmission(
                command(),
                new ByteArrayInputStream(twoPagePdfBytes())
        );

        assertThat(submission.status())
                .isEqualTo(KnowledgeSubmissionStatus.DRAFT);
        assertThat(submission.originalFileName())
                .isEqualTo("2025版学生手册-测试.pdf");
        assertThat(repository.events).singleElement().satisfies(event -> {
            assertThat(event.actionType()).isEqualTo("CREATE_SUBMISSION");
            assertThat(event.submissionId()).isEqualTo(submission.id());
        });

        List<KnowledgeCandidate> candidates = service.extractCandidates(
                submission.id()
        );

        assertThat(repository.findSubmission(submission.id()))
                .get()
                .extracting(KnowledgeSubmission::status)
                .isEqualTo(KnowledgeSubmissionStatus.EXTRACTED);
        assertThat(candidates).hasSize(2).allSatisfy(candidate -> {
            assertThat(candidate.reviewStatus())
                    .isEqualTo(CandidateReviewStatus.PENDING_REVIEW);
            assertThat(candidate.submissionId()).isEqualTo(submission.id());
        });
        assertThat(candidates).extracting(KnowledgeCandidate::startPageNumber)
                .containsExactly(1, 2);
        assertThat(repository.events).extracting(KnowledgeReviewEvent::actionType)
                .containsExactly("CREATE_SUBMISSION", "EXTRACT_CANDIDATES");
    }

    private static SubmissionCreateCommand command() {
        return new SubmissionCreateCommand(
                "本科生学生手册（测试版）",
                "测试版",
                "学校公开学生手册",
                "https://example.edu.cn/handbook",
                "全日制普通本科生",
                LocalDate.of(2025, 9, 1),
                true,
                LocalDate.of(2025, 9, 1),
                true,
                "仅供本机审核。",
                "ACADEMIC_STATUS",
                "2025版学生手册-测试.pdf"
        );
    }

    private static byte[] twoPagePdfBytes() throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {
            addPage(document, "first handbook page");
            addPage(document, "second handbook page");
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void addPage(PDDocument document, String text)
            throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(
                document,
                page
        )) {
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 12);
            stream.newLineAtOffset(72, 720);
            stream.showText(text);
            stream.endText();
        }
    }

    private static final class InMemoryGovernanceRepository
            implements KnowledgeGovernanceRepository {

        private final Map<UUID, KnowledgeSubmission> submissions =
                new HashMap<>();
        private final Map<UUID, List<KnowledgeCandidate>> candidates =
                new HashMap<>();
        private final List<KnowledgeReviewEvent> events = new ArrayList<>();

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
        public Optional<KnowledgeCandidate> findCandidate(UUID candidateId) {
            return candidates.values().stream()
                    .flatMap(List::stream)
                    .filter(candidate -> candidate.id().equals(candidateId))
                    .findFirst();
        }

        @Override
        public List<KnowledgeCandidate> listCandidates(UUID submissionId) {
            return List.copyOf(candidates.getOrDefault(submissionId, List.of()));
        }

        @Override
        public void saveCandidates(
                UUID submissionId,
                List<KnowledgeCandidate> extractedCandidates
        ) {
            candidates.put(submissionId, List.copyOf(extractedCandidates));
        }

        @Override
        public void completeExtraction(
                UUID submissionId,
                List<KnowledgeCandidate> extractedCandidates,
                KnowledgeReviewEvent event
        ) {
            saveCandidates(submissionId, extractedCandidates);
            markSubmissionExtracted(submissionId);
            appendEvent(event);
        }

        @Override
        public void updateCandidate(KnowledgeCandidate candidate) {
            throw new UnsupportedOperationException("本测试不需要更新候选片段");
        }

        @Override
        public void appendEvent(KnowledgeReviewEvent event) {
            events.add(event);
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
            throw new UnsupportedOperationException("本测试不需要发布资料");
        }

        @Override
        public void markSubmissionArchived(UUID submissionId) {
            throw new UnsupportedOperationException("本测试不需要归档资料");
        }

        void markSubmissionExtracted(UUID submissionId) {
            KnowledgeSubmission current = submissions.get(submissionId);
            submissions.put(submissionId, new KnowledgeSubmission(
                    current.id(),
                    current.documentFamily(),
                    current.knowledgeScope(),
                    current.title(),
                    current.sourceVersion(),
                    current.sourceDescription(),
                    current.sourceUrl(),
                    current.applicableScope(),
                    current.publicationDate(),
                    current.publicationDateConfirmed(),
                    current.effectiveDate(),
                    current.effectiveDateConfirmed(),
                    current.storageKey(),
                    current.originalFileName(),
                    current.contentChecksum(),
                    KnowledgeSubmissionStatus.EXTRACTED,
                    current.reviewNotice(),
                    current.createdAt(),
                    FIXED_CLOCK.instant()
            ));
        }
    }
}
