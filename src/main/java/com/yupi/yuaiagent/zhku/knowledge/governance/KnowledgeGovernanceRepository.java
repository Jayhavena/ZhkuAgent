package com.yupi.yuaiagent.zhku.knowledge.governance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资料治理暂存区的数据访问边界。
 */
public interface KnowledgeGovernanceRepository {

    void createSubmission(KnowledgeSubmission submission);

    /** 将草稿与创建记录一起写入，避免暂存区出现无操作记录的半成品。 */
    void createSubmissionWithEvent(
            KnowledgeSubmission submission,
            KnowledgeReviewEvent event
    );

    Optional<KnowledgeSubmission> findSubmission(UUID submissionId);

    Optional<KnowledgeCandidate> findCandidate(UUID candidateId);

    List<KnowledgeCandidate> listCandidates(UUID submissionId);

    void saveCandidates(UUID submissionId, List<KnowledgeCandidate> candidates);

    /** 候选片段、提取记录与投稿状态必须同一事务完成。 */
    void completeExtraction(
            UUID submissionId,
            List<KnowledgeCandidate> candidates,
            KnowledgeReviewEvent event
    );

    void updateCandidate(KnowledgeCandidate candidate);

    void appendEvent(KnowledgeReviewEvent event);

    Optional<UUID> findPublishedSourceId(
            String documentFamily,
            String knowledgeScope
    );

    void markSubmissionPublished(UUID submissionId);

    void markSubmissionArchived(UUID submissionId);
}
