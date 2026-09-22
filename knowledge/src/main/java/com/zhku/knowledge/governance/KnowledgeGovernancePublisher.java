package com.zhku.knowledge.governance;

import com.zhku.knowledge.publication.ReviewedKnowledgeChunk;
import com.zhku.knowledge.publication.ReviewedKnowledgePublication;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;

import java.time.Clock;
import java.time.Instant;
import java.sql.Connection;
import java.sql.SQLException;
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
        return publish(submissionId, false, false);
    }

    /**
     * 将完整审核后的投稿发布到当前知识范围。
     *
     * <p>学生公开与分享权确认必须在发布时成对明确传入；默认发布保持内部可见，
     * 防止调用方遗漏确认时把审核资料意外暴露给学生。</p>
     */
    public PublishedSubmissionResult publish(
            UUID submissionId,
            boolean studentVisible,
            boolean sharingRightsConfirmed
    ) {
        return publishWithPublicationTransaction(
                submissionId,
                studentVisible,
                sharingRightsConfirmed,
                (connection, submission, sourceId, publishedAt) ->
                        new PublishedSubmissionResult(submission.id(), sourceId)
        );
    }

    /**
     * 在资料来源、审核状态和扩展治理记录同一事务内发布。
     *
     * <p>通知等动态资料必须在来源替代成功后才写入可见记录；若拆成两个事务，
     * 会出现“新来源已发布但当前通知未切换”的错误窗口。</p>
     */
    public <T> T publishWithPublicationTransaction(
            UUID submissionId,
            boolean studentVisible,
            boolean sharingRightsConfirmed,
            PublicationTransactionWork<T> afterPublication
    ) {
        Objects.requireNonNull(submissionId, "投稿编号不能为空");
        Objects.requireNonNull(afterPublication, "发布事务回调不能为空");
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
                sourceTypeOf(submission.documentFamily()),
                submission.sourceVersion(),
                submission.sourceUrl(),
                submission.applicableScope(),
                submission.reviewNotice(),
                studentVisible,
                sharingRightsConfirmed,
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
                    return afterPublication.execute(
                            connection, submission, sourceId, publishedAt
                    );
                }
        );
    }

    @FunctionalInterface
    public interface PublicationTransactionWork<T> {
        T execute(
                Connection connection,
                KnowledgeSubmission submission,
                UUID sourceId,
                Instant publishedAt
        ) throws SQLException;
    }

    private static String sourceTypeOf(String documentFamily) {
        return switch (documentFamily) {
            case "STUDENT_HANDBOOK" -> "学生手册";
            case "OFFICIAL_NOTICE" -> "官方通知";
            default -> throw new IllegalArgumentException("不支持的资料族");
        };
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

    /**
     * 出处定位：**审核阶段填写的定位优先**。
     *
     * <p>为什么：审核人填的是条级定位（如「PDF 第 76 页，第 2、4 条」），它直接决定回答卡
     * 能不能回答"依据哪一条"；按页码现拼只能给页级（「PDF 第 76 页」），信息在发布这一步被丢掉，
     * 学生就无法核对到具体条款（本项目已实际踩过：同一范围两种定位粒度并存，引用可核验性下降）。
     * 未填写时才回退到页级合成，保持对历史数据与"只标了页"的候选兼容。</p>
     */
    private static String locator(KnowledgeCandidate candidate) {
        String reviewedLocator = candidate.sourceLocator();
        if (reviewedLocator != null && !reviewedLocator.isBlank()) {
            return reviewedLocator.strip();
        }
        if (candidate.startPageNumber() == candidate.endPageNumber()) {
            return "PDF 第 " + candidate.startPageNumber() + " 页";
        }
        return "PDF 第 " + candidate.startPageNumber() + "—"
                + candidate.endPageNumber() + " 页";
    }
}
