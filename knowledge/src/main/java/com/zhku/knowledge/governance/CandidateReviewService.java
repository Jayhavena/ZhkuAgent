package com.zhku.knowledge.governance;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 将 PDF 候选片段转为可发布资料前的人工审核边界。
 */
public final class CandidateReviewService {

    private static final String LOCAL_ADMIN = "本机管理员";

    private final KnowledgeGovernanceRepository repository;
    private final Clock clock;

    public CandidateReviewService(
            KnowledgeGovernanceRepository repository,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "资料治理仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public KnowledgeCandidate update(
            UUID candidateId,
            CandidateEditCommand command
    ) {
        KnowledgeCandidate current = editableCandidate(candidateId);
        Instant now = clock.instant();
        KnowledgeCandidate updated = new KnowledgeCandidate(
                current.id(), current.submissionId(), command.startPageNumber(),
                command.endPageNumber(), current.rawText(), command.editedText(),
                command.sectionTitle(), command.sourceLocator(),
                CandidateReviewStatus.PENDING_REVIEW, null, null,
                current.createdAt(), now
        );
        repository.updateCandidate(updated);
        moveSubmissionIntoReview(current.submissionId());
        repository.appendEvent(event(
                current.submissionId(), current.id(), "UPDATE_CANDIDATE",
                "已更新候选片段正文、标题和资料定位。", now
        ));
        return updated;
    }

    public KnowledgeCandidate approve(UUID candidateId, String reviewNote) {
        KnowledgeCandidate current = editableCandidate(candidateId);
        Instant now = clock.instant();
        KnowledgeCandidate approved = new KnowledgeCandidate(
                current.id(), current.submissionId(), current.startPageNumber(),
                current.endPageNumber(), current.rawText(), current.editedText(),
                current.sectionTitle(), current.sourceLocator(),
                CandidateReviewStatus.APPROVED, reviewNote, null,
                current.createdAt(), now
        );
        repository.updateCandidate(approved);
        moveSubmissionIntoReview(current.submissionId());
        repository.appendEvent(event(
                current.submissionId(), current.id(), "APPROVE_CANDIDATE",
                "已通过候选片段审核。", now
        ));
        return approved;
    }

    public KnowledgeCandidate discard(UUID candidateId, String discardReason) {
        KnowledgeCandidate current = editableCandidate(candidateId);
        Instant now = clock.instant();
        KnowledgeCandidate discarded = new KnowledgeCandidate(
                current.id(), current.submissionId(), current.startPageNumber(),
                current.endPageNumber(), current.rawText(), current.editedText(),
                current.sectionTitle(), current.sourceLocator(),
                CandidateReviewStatus.DISCARDED, null, discardReason,
                current.createdAt(), now
        );
        repository.updateCandidate(discarded);
        moveSubmissionIntoReview(current.submissionId());
        repository.appendEvent(event(
                current.submissionId(), current.id(), "DISCARD_CANDIDATE",
                "已丢弃候选片段。", now
        ));
        return discarded;
    }

    public List<KnowledgeCandidate> split(
            UUID candidateId,
            CandidateSplitCommand command
    ) {
        KnowledgeCandidate current = editableCandidate(candidateId);
        Instant now = clock.instant();
        KnowledgeCandidate first = pendingSplitPart(
                current, current.id(), command.firstEditedText(), now
        );
        KnowledgeCandidate second = pendingSplitPart(
                current, UUID.randomUUID(), command.secondEditedText(), now
        );
        repository.updateCandidate(first);
        repository.saveCandidates(current.submissionId(), List.of(second));
        moveSubmissionIntoReview(current.submissionId());
        repository.appendEvent(event(
                current.submissionId(), current.id(), "SPLIT_CANDIDATE",
                "已拆分为两个待审核候选片段。", now
        ));
        return List.of(first, second);
    }

    private KnowledgeCandidate editableCandidate(UUID candidateId) {
        Objects.requireNonNull(candidateId, "候选片段编号不能为空");
        KnowledgeCandidate candidate = repository.findCandidate(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("候选片段不存在"));
        KnowledgeSubmission submission = repository.findSubmission(
                        candidate.submissionId()
                )
                .orElseThrow(() -> new IllegalStateException("候选片段所属投稿不存在"));
        if (submission.status() != KnowledgeSubmissionStatus.EXTRACTED
                && submission.status() != KnowledgeSubmissionStatus.IN_REVIEW) {
            if (submission.status() == KnowledgeSubmissionStatus.ARCHIVED) {
                throw new IllegalStateException("已归档投稿不能修改候选片段");
            }
            throw new IllegalStateException(
                    "投稿当前为" + submission.status() + "，不能修改候选片段"
            );
        }
        if (candidate.reviewStatus() != CandidateReviewStatus.PENDING_REVIEW) {
            throw new IllegalStateException("只有待审核候选片段可以继续操作");
        }
        return candidate;
    }

    private void moveSubmissionIntoReview(UUID submissionId) {
        repository.markSubmissionInReview(submissionId);
    }

    private KnowledgeCandidate pendingSplitPart(
            KnowledgeCandidate current,
            UUID candidateId,
            String editedText,
            Instant now
    ) {
        return new KnowledgeCandidate(
                candidateId, current.submissionId(), current.startPageNumber(),
                current.endPageNumber(), current.rawText(), editedText, null,
                null, CandidateReviewStatus.PENDING_REVIEW, null, null,
                candidateId.equals(current.id()) ? current.createdAt() : now, now
        );
    }

    private static KnowledgeReviewEvent event(
            UUID submissionId,
            UUID candidateId,
            String actionType,
            String actionNote,
            Instant occurredAt
    ) {
        return new KnowledgeReviewEvent(
                UUID.randomUUID(), submissionId, candidateId, actionType,
                actionNote, LOCAL_ADMIN, occurredAt
        );
    }
}
