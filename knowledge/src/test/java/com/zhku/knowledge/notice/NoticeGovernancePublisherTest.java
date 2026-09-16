package com.zhku.knowledge.notice;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.governance.CandidateReviewStatus;
import com.zhku.knowledge.governance.JdbcKnowledgeGovernanceRepository;
import com.zhku.knowledge.governance.KnowledgeCandidate;
import com.zhku.knowledge.governance.KnowledgeGovernancePublisher;
import com.zhku.knowledge.governance.KnowledgeGovernanceRepository;
import com.zhku.knowledge.governance.KnowledgeSubmission;
import com.zhku.knowledge.governance.KnowledgeSubmissionStatus;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoticeGovernancePublisherTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
    );
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent"
    );
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();
    private static final UUID SUBMISSION_ID = UUID.fromString(
            "e93b4f47-1d12-4878-a8db-7ad5d9de3061"
    );
    private static final String KNOWLEDGE_SCOPE = "COURSE_SELECTION_NOTICES";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-16T04:00:00Z"), ZoneOffset.UTC
    );

    private KnowledgeGovernanceRepository governanceRepository;
    private ReviewedKnowledgePublisher reviewedPublisher;
    private NoticeGovernancePublisher noticePublisher;
    private NoticeRepository noticeRepository;

    @BeforeEach
    void setUp() throws SQLException {
        Flyway.configure().dataSource(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD
        ).locations("classpath:db/migration").load().migrate();
        deleteTestData();
        DataSource dataSource = new DriverManagerDataSource(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD
        );
        governanceRepository = new JdbcKnowledgeGovernanceRepository(dataSource);
        reviewedPublisher = new ReviewedKnowledgePublisher(dataSource);
        noticeRepository = new JdbcNoticeRepository(dataSource);
        noticePublisher = new NoticeGovernancePublisher(
                governanceRepository,
                new KnowledgeGovernancePublisher(
                        governanceRepository, reviewedPublisher, CLOCK
                ),
                (JdbcNoticeRepository) noticeRepository
        );
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteTestData();
    }

    @Test
    void shouldPublishOnlyFullyReviewedOfficialNoticeAsCurrentStudentEvidence() {
        KnowledgeSubmission submission = new KnowledgeSubmission(
                SUBMISSION_ID, "OFFICIAL_NOTICE", KNOWLEDGE_SCOPE,
                "2026-2027 学年第一学期选课通知", "2026-09-16",
                "教务部门发布的选课通知 PDF 快照", "https://jw.zhku.edu.cn/notices/1",
                "2026-2027 学年第一学期本科生", LocalDate.of(2026, 9, 16),
                true, null, false, "immutable-notice.pdf", "notice.pdf",
                "A".repeat(64), KnowledgeSubmissionStatus.IN_REVIEW,
                "已人工核验通知正文与官方链接。", CLOCK.instant(), CLOCK.instant()
        );
        governanceRepository.createSubmission(submission);
        governanceRepository.saveCandidates(SUBMISSION_ID, List.of(
                new KnowledgeCandidate(
                        UUID.randomUUID(), SUBMISSION_ID, 1, 1, "原始页文本",
                        "本科生选课安排以本通知为准。", "选课安排", "PDF 第 1 页",
                        CandidateReviewStatus.APPROVED, "已人工核验。", null,
                        CLOCK.instant(), CLOCK.instant()
                )
        ));

        PublishedNoticeResult result = noticePublisher.publish(
                SUBMISSION_ID,
                new NoticePublicationCommand(
                        new AcademicTerm("2026-2027-1"),
                        Instant.parse("2026-09-16T00:00:00Z"),
                        Instant.parse("2026-10-01T00:00:00Z"),
                        "https://jw.zhku.edu.cn/notices/1",
                        List.of(new NoticeFact(
                                NoticeFactType.COURSE_SELECTION,
                                Instant.parse("2026-09-17T01:00:00Z"),
                                Instant.parse("2026-09-18T10:00:00Z"),
                                "https://jw.zhku.edu.cn/select", true, true
                        ))
                )
        );

        assertThat(result.sourceId()).isNotNull();
        assertThat(new CurrentNoticeFinder(noticeRepository, CLOCK).find(
                new AcademicTerm("2026-2027-1"), NoticeType.COURSE_SELECTION
        )).contains(result.notice());
        assertThat(reviewedPublisher.searchStudentVisiblePublished("选课安排"))
                .extracting(chunk -> chunk.sourceId())
                .contains(result.sourceId());
    }

    @Test
    void shouldRejectUsingStudentHandbookSubmissionAsDynamicNoticeEvidence() {
        governanceRepository.createSubmission(new KnowledgeSubmission(
                SUBMISSION_ID, "STUDENT_HANDBOOK", KNOWLEDGE_SCOPE,
                "学生手册", "2025", "手册 PDF", "https://jw.zhku.edu.cn/notices/1",
                "本科生", LocalDate.of(2025, 9, 1), true, null, false,
                "handbook.pdf", "handbook.pdf", "B".repeat(64),
                KnowledgeSubmissionStatus.IN_REVIEW, "已审核。",
                CLOCK.instant(), CLOCK.instant()
        ));

        assertThatThrownBy(() -> noticePublisher.publish(
                SUBMISSION_ID,
                new NoticePublicationCommand(
                        new AcademicTerm("2026-2027-1"),
                        Instant.parse("2026-09-16T00:00:00Z"),
                        Instant.parse("2026-10-01T00:00:00Z"),
                        "https://jw.zhku.edu.cn/notices/1",
                        List.of(new NoticeFact(
                                NoticeFactType.COURSE_SELECTION,
                                Instant.parse("2026-09-17T01:00:00Z"),
                                Instant.parse("2026-09-18T10:00:00Z"),
                                "https://jw.zhku.edu.cn/select", true, true
                        ))
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有官方通知投稿可以发布为当前通知");
    }

    @Test
    void shouldPublishVerifiedInternalOfficialMaterialWithoutPublicUrl() {
        KnowledgeSubmission submission = new KnowledgeSubmission(
                SUBMISSION_ID, "OFFICIAL_NOTICE", KNOWLEDGE_SCOPE,
                "2026-2027 学年第一学期课程选课工作通知", "2026-09-16",
                "已接收的正式通知 PDF", null, "全体本科生",
                LocalDate.of(2026, 9, 16), true, null, false,
                "internal-notice.pdf", "notice.pdf", "C".repeat(64),
                KnowledgeSubmissionStatus.IN_REVIEW, "已人工核验。",
                CLOCK.instant(), CLOCK.instant()
        );
        governanceRepository.createSubmission(submission);
        governanceRepository.saveCandidates(SUBMISSION_ID, List.of(
                new KnowledgeCandidate(UUID.randomUUID(), SUBMISSION_ID, 1, 1,
                        "原文", "选课安排以本通知为准。", "选课安排", "PDF 第 1 页",
                        CandidateReviewStatus.APPROVED, "已核验。", null,
                        CLOCK.instant(), CLOCK.instant())
        ));

        PublishedNoticeResult result = noticePublisher.publish(SUBMISSION_ID,
                new NoticePublicationCommand(new AcademicTerm("2026-2027-1"),
                        Instant.parse("2026-09-16T00:00:00Z"),
                        Instant.parse("2026-10-01T00:00:00Z"), null,
                        List.of(new NoticeFact(NoticeFactType.COURSE_SELECTION,
                                Instant.parse("2026-09-17T01:00:00Z"),
                                Instant.parse("2026-09-18T10:00:00Z"),
                                "https://jw.zhku.edu.cn/select", true, true)),
                        NoticeEvidenceOrigin.internalOfficial(
                                "教务部", "教务系统正式下发", "2026-2027-1-选课通知"
                        )));

        assertThat(result.notice().officialSnapshot().officialUrl()).isNull();
        assertThat(result.notice().officialSnapshot().origin().studentCitationLabel())
                .contains("已审核校内正式通知", "教务部");
    }

    private static void deleteTestData() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD
        ); PreparedStatement deleteFacts = connection.prepareStatement("""
                DELETE FROM official_notice_fact
                WHERE notice_id IN (SELECT id FROM official_notice WHERE source_id IN (
                    SELECT id FROM knowledge_source WHERE document_family = 'OFFICIAL_NOTICE'
                      AND knowledge_scope = ?
                ))
                """); PreparedStatement deleteNotices = connection.prepareStatement("""
                DELETE FROM official_notice WHERE source_id IN (
                    SELECT id FROM knowledge_source WHERE document_family = 'OFFICIAL_NOTICE'
                      AND knowledge_scope = ?
                )
                """); PreparedStatement deleteEvents = connection.prepareStatement("""
                DELETE FROM knowledge_review_event WHERE submission_id = ?
                """); PreparedStatement deleteCandidates = connection.prepareStatement("""
                DELETE FROM knowledge_candidate WHERE submission_id = ?
                """); PreparedStatement deleteSubmissions = connection.prepareStatement("""
                DELETE FROM knowledge_submission WHERE id = ?
                """); PreparedStatement deleteChunks = connection.prepareStatement("""
                DELETE FROM knowledge_chunk WHERE source_id IN (
                    SELECT id FROM knowledge_source WHERE document_family = 'OFFICIAL_NOTICE'
                      AND knowledge_scope = ?
                )
                """); PreparedStatement deleteSources = connection.prepareStatement("""
                DELETE FROM knowledge_source WHERE document_family = 'OFFICIAL_NOTICE'
                  AND knowledge_scope = ?
                """)) {
            deleteFacts.setString(1, KNOWLEDGE_SCOPE);
            deleteFacts.executeUpdate();
            deleteNotices.setString(1, KNOWLEDGE_SCOPE);
            deleteNotices.executeUpdate();
            deleteEvents.setObject(1, SUBMISSION_ID);
            deleteEvents.executeUpdate();
            deleteCandidates.setObject(1, SUBMISSION_ID);
            deleteCandidates.executeUpdate();
            deleteSubmissions.setObject(1, SUBMISSION_ID);
            deleteSubmissions.executeUpdate();
            deleteChunks.setString(1, KNOWLEDGE_SCOPE);
            deleteChunks.executeUpdate();
            deleteSources.setString(1, KNOWLEDGE_SCOPE);
            deleteSources.executeUpdate();
        }
    }
}
