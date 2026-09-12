package com.yupi.yuaiagent.zhku.knowledge.governance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资料治理暂存区的数据访问边界。
 */
public interface KnowledgeGovernanceRepository {

    void createSubmission(KnowledgeSubmission submission);

    Optional<KnowledgeSubmission> findSubmission(UUID submissionId);

    Optional<KnowledgeCandidate> findCandidate(UUID candidateId);

    List<KnowledgeCandidate> listCandidates(UUID submissionId);

    void saveCandidates(UUID submissionId, List<KnowledgeCandidate> candidates);

    void updateCandidate(KnowledgeCandidate candidate);

    void appendEvent(KnowledgeReviewEvent event);

    Optional<UUID> findPublishedSourceId(
            String documentFamily,
            String knowledgeScope
    );

    void markSubmissionPublished(UUID submissionId);

    void markSubmissionArchived(UUID submissionId);
}
