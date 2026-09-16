package com.zhku.knowledge.governance;

import com.zhku.knowledge.ingestion.ExtractedPdfPage;
import com.zhku.knowledge.ingestion.PdfPageTextExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 衔接本机文件、暂存投稿与按页候选片段的应用服务。
 */
public final class KnowledgeSubmissionService {

    private static final String LOCAL_ADMINISTRATOR = "本机管理员";

    private final KnowledgeGovernanceRepository repository;
    private final LocalKnowledgeFileStore fileStore;
    private final PdfPageTextExtractor pageTextExtractor;
    private final Clock clock;

    public KnowledgeSubmissionService(
            KnowledgeGovernanceRepository repository,
            LocalKnowledgeFileStore fileStore,
            PdfPageTextExtractor pageTextExtractor,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "资料治理仓储不能为空");
        this.fileStore = Objects.requireNonNull(fileStore, "本机文件存储不能为空");
        this.pageTextExtractor = Objects.requireNonNull(
                pageTextExtractor,
                "PDF 文本提取器不能为空"
        );
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public KnowledgeSubmission createSubmission(
            SubmissionCreateCommand command,
            InputStream content
    ) throws IOException {
        Objects.requireNonNull(command, "投稿命令不能为空");
        StoredKnowledgeFile storedFile = fileStore.store(
                command.originalFilename(),
                content
        );
        Instant now = clock.instant();
        KnowledgeSubmission submission = new KnowledgeSubmission(
                UUID.randomUUID(),
                command.documentFamily(),
                command.knowledgeScope(),
                command.title(),
                command.sourceVersion(),
                command.sourceDescription(),
                command.sourceUrl(),
                command.applicableScope(),
                command.publicationDate(),
                command.publicationDateConfirmed(),
                command.effectiveDate(),
                command.effectiveDateConfirmed(),
                storedFile.storageKey(),
                storedFile.originalFilename(),
                storedFile.sha256(),
                KnowledgeSubmissionStatus.DRAFT,
                command.reviewNotice(),
                now,
                now
        );
        KnowledgeReviewEvent event = new KnowledgeReviewEvent(
                UUID.randomUUID(),
                submission.id(),
                null,
                "CREATE_SUBMISSION",
                "已保存本机 PDF 并创建草稿投稿。",
                LOCAL_ADMINISTRATOR,
                now
        );
        try {
            repository.createSubmissionWithEvent(submission, event);
            return submission;
        } catch (RuntimeException exception) {
            deleteStoredFileAfterStagingFailure(storedFile.storageKey(), exception);
            throw exception;
        }
    }

    public List<KnowledgeCandidate> extractCandidates(UUID submissionId)
            throws IOException {
        KnowledgeSubmission submission = repository.findSubmission(
                Objects.requireNonNull(submissionId, "投稿编号不能为空")
        ).orElseThrow(() -> new IllegalArgumentException("资料投稿不存在"));
        if (submission.status() != KnowledgeSubmissionStatus.DRAFT) {
            throw new IllegalStateException("只有草稿投稿可以提取候选片段");
        }

        List<ExtractedPdfPage> pages = pageTextExtractor.extractAll(
                fileStore.locate(submission.storageKey())
        );
        Instant now = clock.instant();
        List<KnowledgeCandidate> candidates = pages.stream()
                .map(page -> candidateFrom(submission.id(), page, now))
                .toList();
        KnowledgeReviewEvent event = new KnowledgeReviewEvent(
                UUID.randomUUID(),
                submission.id(),
                null,
                "EXTRACT_CANDIDATES",
                "已按物理页码生成待审核候选片段。",
                LOCAL_ADMINISTRATOR,
                now
        );

        // 候选、提取记录和状态迁移必须原子提交；失败时草稿保持可重新提取。
        repository.completeExtraction(submission.id(), candidates, event);
        return candidates;
    }

    private static KnowledgeCandidate candidateFrom(
            UUID submissionId,
            ExtractedPdfPage page,
            Instant now
    ) {
        return new KnowledgeCandidate(
                UUID.randomUUID(),
                submissionId,
                page.pdfPageNumber(),
                page.pdfPageNumber(),
                page.text(),
                null,
                null,
                null,
                CandidateReviewStatus.PENDING_REVIEW,
                null,
                null,
                now,
                now
        );
    }

    private void deleteStoredFileAfterStagingFailure(
            String storageKey,
            RuntimeException originalFailure
    ) {
        try {
            fileStore.delete(storageKey);
        } catch (IOException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }
}
