package com.yupi.yuaiagent.zhku.knowledge.governance;

import com.yupi.yuaiagent.zhku.knowledge.publication.ReviewedKnowledgeChunk;
import com.yupi.yuaiagent.zhku.knowledge.publication.ReviewedKnowledgePublication;
import com.yupi.yuaiagent.zhku.knowledge.publication.ReviewedKnowledgePublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 将完整审核后的暂存投稿原子发布到学生问答可读取的知识表。
 */
public final class KnowledgeGovernancePublisher {

    private static final String LOCAL_ADMIN = "本机管理员";

    private final KnowledgeGovernanceRepository repository;
    private final ReviewedKnowledgePublisher reviewedPublisher;
    private final Clock clock;

    public KnowledgeGovernancePublisher(
            KnowledgeGovernanceRepository repository,
            ReviewedKnowledgePublisher reviewedPublisher,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "资料治理仓储不能为空");
        this.reviewedPublisher = Objects.requireNonNull(
                reviewedPublisher,
                "已发布知识发布器不能为空"
        );
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public PublishedSubmissionResult publish(UUID submissionId) {
        Objects.requireNonNull(submissionId, "投稿编号不能为空");
        KnowledgeSubmission submission = repository.findSubmission(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("资料投稿不存在"));
        if (submission.status() != KnowledgeSubmissionStatus.IN_REVIEW) {
            throw new IllegalStateException("只有审核中的投稿可以发布");
        }

        List<KnowledgeCandidate> candidates = repository.listCandidates(
                submissionId
        );
        List<KnowledgeCandidate> approvedCandidates = approvedCandidates(candidates);
        UUID sourceId = UUID.randomUUID();
        Instant publishedAt = clock.instant();
        ReviewedKnowledgePublication publication = new ReviewedKnowledgePublication(
                sourceId,
                submission.title(),
                "学生手册",
                submission.sourceVersion(),
                submission.sourceUrl(),
                submission.applicableScope(),
                submission.reviewNotice(),
                false,
                false,
                "审核通过",
                publishedAt,
                reviewedChunks(approvedCandidates)
        );

        // 发布器持有同一连接；暂存状态和操作记录不能另开连接提前提交。
        return reviewedPublisher.publishReplacingScope(
                publication,
                submission.documentFamily(),
                submission.knowledgeScope(),
                connection -> {
                    repository.completePublicationInTransaction(
                            connection,
                            submissionId,
                            new KnowledgeReviewEvent(
                                    UUID.randomUUID(),
                                    submissionId,
                                    null,
                                    "PUBLISH_SUBMISSION",
                                    "已将审核通过的候选片段发布到知识库。",
                                    LOCAL_ADMIN,
                                    publishedAt
                            )
                    );
                    return new PublishedSubmissionResult(submissionId, sourceId);
                }
        );
    }

    private static List<KnowledgeCandidate> approvedCandidates(
            List<KnowledgeCandidate> candidates
    ) {
        List<KnowledgeCandidate> copiedCandidates = List.copyOf(
                Objects.requireNonNull(candidates, "候选片段不能为空")
        );
        if (copiedCandidates.isEmpty()) {
            throw new IllegalStateException("投稿没有可发布的候选片段");
        }
        if (copiedCandidates.stream().anyMatch(candidate ->
                candidate.reviewStatus() == CandidateReviewStatus.PENDING_REVIEW)) {
            throw new IllegalStateException("候选片段尚未全部审核，不能发布");
        }
        List<KnowledgeCandidate> approved = copiedCandidates.stream()
                .filter(candidate ->
                        candidate.reviewStatus() == CandidateReviewStatus.APPROVED
                )
                .sorted(Comparator.comparingInt(
                                KnowledgeCandidate::startPageNumber
                        )
                        .thenComparingInt(KnowledgeCandidate::endPageNumber)
                        .thenComparing(KnowledgeCandidate::createdAt)
                        .thenComparing(KnowledgeCandidate::id))
                .toList();
        if (approved.isEmpty()) {
            throw new IllegalStateException("至少需要一个通过的候选片段才能发布");
        }
        return approved;
    }

    private static List<ReviewedKnowledgeChunk> reviewedChunks(
            List<KnowledgeCandidate> approvedCandidates
    ) {
        List<ReviewedKnowledgeChunk> chunks = new ArrayList<>();
        for (int index = 0; index < approvedCandidates.size(); index++) {
            KnowledgeCandidate candidate = approvedCandidates.get(index);
            chunks.add(new ReviewedKnowledgeChunk(
                    index,
                    candidate.sectionTitle(),
                    locator(candidate),
                    candidate.startPageNumber(),
                    candidate.editedText()
            ));
        }
        return List.copyOf(chunks);
    }

    private static String locator(KnowledgeCandidate candidate) {
        if (candidate.startPageNumber() == candidate.endPageNumber()) {
            return "PDF 第 " + candidate.startPageNumber() + " 页";
        }
        return "PDF 第 " + candidate.startPageNumber() + "—"
                + candidate.endPageNumber() + " 页";
    }
}
