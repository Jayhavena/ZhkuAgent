package com.zhku.knowledge.notice;

import com.zhku.knowledge.governance.KnowledgeGovernancePublisher;
import com.zhku.knowledge.governance.KnowledgeGovernanceRepository;
import com.zhku.knowledge.governance.KnowledgeSubmission;

import java.util.Objects;
import java.util.UUID;

/**
 * 将审核后的官方通知接入既有资料治理发布事务。
 *
 * <p>学生可见的动态结论必须同时拥有已发布来源、不可变 PDF 快照与完整时态事实；
 * 任一条件缺失时不允许把通知写入当前通知表。</p>
 */
public final class NoticeGovernancePublisher {

    private static final String OFFICIAL_NOTICE = "OFFICIAL_NOTICE";

    private final KnowledgeGovernanceRepository governanceRepository;
    private final KnowledgeGovernancePublisher governancePublisher;
    private final JdbcNoticeRepository noticeRepository;

    public NoticeGovernancePublisher(
            KnowledgeGovernanceRepository governanceRepository,
            KnowledgeGovernancePublisher governancePublisher,
            JdbcNoticeRepository noticeRepository
    ) {
        this.governanceRepository = Objects.requireNonNull(
                governanceRepository, "资料治理仓储不能为空"
        );
        this.governancePublisher = Objects.requireNonNull(
                governancePublisher, "资料发布器不能为空"
        );
        this.noticeRepository = Objects.requireNonNull(
                noticeRepository, "通知仓储不能为空"
        );
    }

    public PublishedNoticeResult publish(
            UUID submissionId,
            NoticePublicationCommand command
    ) {
        Objects.requireNonNull(submissionId, "投稿编号不能为空");
        Objects.requireNonNull(command, "通知发布信息不能为空");
        KnowledgeSubmission submission = governanceRepository.findSubmission(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("资料投稿不存在"));
        requireOfficialNoticeSubmission(submission, command);

        return governancePublisher.publishWithPublicationTransaction(
                submissionId,
                true,
                true,
                (connection, publishedSubmission, sourceId, publishedAt) -> {
                    PublishedNotice notice = new PublishedNotice(
                            UUID.randomUUID(),
                            sourceId,
                            publishedSubmission.title(),
                            publishedSubmission.sourceVersion(),
                            publishedSubmission.applicableScope(),
                            command.academicTerm(),
                            NoticeType.COURSE_SELECTION,
                            NoticeLifecycleStatus.PUBLISHED,
                            command.validFrom(),
                            command.validTo(),
                            new OfficialWebSnapshot(
                                    command.officialUrl(),
                                    publishedSubmission.storageKey(),
                                    publishedSubmission.contentChecksum(),
                                    publishedSubmission.createdAt(),
                                    "application/pdf",
                                    command.origin()
                            ),
                            true,
                            true,
                            command.facts()
                    );
                    noticeRepository.publishReplacingScope(connection, notice);
                    return new PublishedNoticeResult(
                            publishedSubmission.id(), sourceId, notice
                    );
                }
        );
    }

    private static void requireOfficialNoticeSubmission(
            KnowledgeSubmission submission,
            NoticePublicationCommand command
    ) {
        if (!OFFICIAL_NOTICE.equals(submission.documentFamily())) {
            throw new IllegalArgumentException("只有官方通知投稿可以发布为当前通知");
        }
        if (!"COURSE_SELECTION_NOTICES".equals(submission.knowledgeScope())) {
            throw new IllegalArgumentException("选课通知必须使用 COURSE_SELECTION_NOTICES 范围");
        }
        if (command.origin().channel() == NoticeEvidenceChannel.PUBLIC_WEB
                && !command.officialUrl().equals(submission.sourceUrl())) {
            // 防止管理员把已审核 PDF 快照与另一条网页链接拼接，破坏来源可追溯性。
            throw new IllegalArgumentException("通知官方链接必须与已审核投稿来源一致");
        }
        if (command.origin().channel() == NoticeEvidenceChannel.INTERNAL_OFFICIAL
                && submission.sourceUrl() != null) throw new IllegalArgumentException("校内正式材料投稿不应填写虚构官网链接");
    }
}
