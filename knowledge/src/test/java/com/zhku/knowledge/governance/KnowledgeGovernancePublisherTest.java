package com.zhku.knowledge.governance;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.publication.PublishedKnowledgeChunk;
import com.zhku.knowledge.publication.ReviewedKnowledgeChunk;
import com.zhku.knowledge.publication.ReviewedKnowledgePublication;
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

class KnowledgeGovernancePublisherTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url",
            "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
    );
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user",
            "zhkuagent"
    );
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();
    private static final String DOCUMENT_FAMILY = "STUDENT_HANDBOOK";
    private static final String KNOWLEDGE_SCOPE = "TASK3_PUBLISH_TEST";
    private static final UUID OLD_SUBMISSION_ID = UUID.fromString(
            "2ca7e0c8-c3cd-44a5-aad3-d1bb2b07206d"
    );
    private static final UUID NEW_SUBMISSION_ID = UUID.fromString(
            "a35a2f7c-a1d2-4c7f-a92c-808f342a3e87"
    );
    private static final UUID OLD_SOURCE_ID = UUID.fromString(
            "0818eb70-ae40-4b25-aecd-63224f3cda0b"
    );
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-12T06:00:00Z"),
            ZoneOffset.UTC
    );

    private KnowledgeGovernanceRepository repository;
    private ReviewedKnowledgePublisher reviewedPublisher;
    private KnowledgeGovernancePublisher publisher;

    @BeforeEach
    void setUp() throws SQLException {
        Flyway.configure()
                .dataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        deleteTestData();
        DataSource dataSource = new DriverManagerDataSource(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        );
        repository = new JdbcKnowledgeGovernanceRepository(dataSource);
        reviewedPublisher = new ReviewedKnowledgePublisher(dataSource);
        publisher = new KnowledgeGovernancePublisher(
                repository,
                reviewedPublisher,
                FIXED_CLOCK
        );
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteTestData();
    }

    @Test
    void shouldReplacePublishedScopeAndArchivePreviousSubmission() {
        createOldPublishedVersion();
        KnowledgeSubmission newSubmission = submission(
                NEW_SUBMISSION_ID,
                "新版公共选修规则",
                KnowledgeSubmissionStatus.IN_REVIEW,
                "B"
        );
        repository.createSubmission(newSubmission);
        repository.saveCandidates(newSubmission.id(), List.of(
                approvedCandidate(newSubmission.id(), 1, "新版学分上限规则"),
                approvedCandidate(newSubmission.id(), 2, "新版选课范围规则")
        ));

        PublishedSubmissionResult result = publisher.publish(newSubmission.id());

        assertThat(result.submissionId()).isEqualTo(newSubmission.id());
        assertThat(repository.findSubmission(OLD_SUBMISSION_ID))
                .get()
                .extracting(KnowledgeSubmission::status)
                .isEqualTo(KnowledgeSubmissionStatus.ARCHIVED);
        assertThat(repository.findSubmission(NEW_SUBMISSION_ID))
                .get()
                .extracting(KnowledgeSubmission::status)
                .isEqualTo(KnowledgeSubmissionStatus.PUBLISHED);
        assertThat(reviewedPublisher.searchPublished("旧版公共选修规则"))
                .isEmpty();
        assertThat(reviewedPublisher.searchPublished("新版学分上限规则"))
                .extracting(PublishedKnowledgeChunk::sourceId)
                .containsExactly(result.sourceId());
    }

    @Test
    void shouldExposeApprovedSubmissionWhenAdministratorConfirmsPublication() {
        KnowledgeSubmission submission = submission(
                NEW_SUBMISSION_ID,
                "公开的公共选修重修规则",
                KnowledgeSubmissionStatus.IN_REVIEW,
                "B"
        );
        repository.createSubmission(submission);
        repository.saveCandidates(submission.id(), List.of(
                approvedCandidate(submission.id(), 1, "公开的公共选修重修规则内容")
        ));

        PublishedSubmissionResult result = publisher.publish(
                submission.id(),
                true,
                true
        );

        assertThat(reviewedPublisher.searchStudentVisiblePublished(
                "公开的公共选修重修规则内容"
        )).extracting(PublishedKnowledgeChunk::sourceId)
                .containsExactly(result.sourceId());
    }

    @Test
    void shouldRollbackScopeArchiveWhenNewSourceCannotBeWritten() {
        createOldPublishedVersion();
        // 暂存区允许较长适用范围，而已发布表只允许 100 个字符；
        // 这能稳定让失败发生在归档旧版之后、事务提交之前。
        KnowledgeSubmission invalidSubmission = submission(
                NEW_SUBMISSION_ID,
                "无法发布的新版公共选修规则",
                KnowledgeSubmissionStatus.IN_REVIEW,
                "C",
                "全日制普通本科生适用。".repeat(12)
        );
        repository.createSubmission(invalidSubmission);
        repository.saveCandidates(invalidSubmission.id(), List.of(
                approvedCandidate(invalidSubmission.id(), 1, "无法发布的新规则")
        ));

        assertThatThrownBy(() -> publisher.publish(invalidSubmission.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法发布");

        assertThat(repository.findSubmission(OLD_SUBMISSION_ID))
                .get()
                .extracting(KnowledgeSubmission::status)
                .isEqualTo(KnowledgeSubmissionStatus.PUBLISHED);
        assertThat(repository.findSubmission(NEW_SUBMISSION_ID))
                .get()
                .extracting(KnowledgeSubmission::status)
                .isEqualTo(KnowledgeSubmissionStatus.IN_REVIEW);
        assertThat(reviewedPublisher.searchPublished("旧版公共选修规则"))
                .extracting(PublishedKnowledgeChunk::sourceId)
                .containsExactly(OLD_SOURCE_ID);
        assertThat(reviewedPublisher.searchPublished("无法发布的新规则"))
                .isEmpty();
    }

    private void createOldPublishedVersion() {
        reviewedPublisher.publishReplacingScope(
                new ReviewedKnowledgePublication(
                        OLD_SOURCE_ID,
                        "旧版公共选修规则",
                        "学生手册",
                        "旧版",
                        null,
                        "全日制普通本科生",
                        "仅供事务替换测试。",
                        false,
                        false,
                        "审核通过",
                        FIXED_CLOCK.instant(),
                        List.of(new ReviewedKnowledgeChunk(
                                0,
                                "旧版规则",
                                "PDF 第 77 页",
                                77,
                                "旧版公共选修规则"
                        ))
                ),
                DOCUMENT_FAMILY,
                KNOWLEDGE_SCOPE
        );
        repository.createSubmission(submission(
                OLD_SUBMISSION_ID,
                "旧版公共选修规则",
                KnowledgeSubmissionStatus.PUBLISHED,
                "A"
        ));
    }

    private static KnowledgeSubmission submission(
            UUID id,
            String title,
            KnowledgeSubmissionStatus status,
            String checksumCharacter
    ) {
        return submission(
                id,
                title,
                status,
                checksumCharacter,
                "全日制普通本科生"
        );
    }

    private static KnowledgeSubmission submission(
            UUID id,
            String title,
            KnowledgeSubmissionStatus status,
            String checksumCharacter,
            String applicableScope
    ) {
        return new KnowledgeSubmission(
                id,
                DOCUMENT_FAMILY,
                KNOWLEDGE_SCOPE,
                title,
                "测试版",
                "学校公开学生手册",
                null,
                applicableScope,
                LocalDate.of(2025, 9, 1),
                true,
                LocalDate.of(2025, 9, 1),
                true,
                id + ".pdf",
                "测试学生手册.pdf",
                checksumCharacter.repeat(64),
                status,
                "仅供本机审核。",
                FIXED_CLOCK.instant(),
                FIXED_CLOCK.instant()
        );
    }

    private static KnowledgeCandidate approvedCandidate(
            UUID submissionId,
            int chunkIndex,
            String text
    ) {
        return new KnowledgeCandidate(
                UUID.randomUUID(),
                submissionId,
                77 + chunkIndex,
                77 + chunkIndex,
                "原始页文本",
                text,
                "公共选修规则",
                "PDF 第 " + (77 + chunkIndex) + " 页",
                CandidateReviewStatus.APPROVED,
                "已人工核对原始页文本。",
                null,
                FIXED_CLOCK.instant().plusSeconds(chunkIndex),
                FIXED_CLOCK.instant().plusSeconds(chunkIndex)
        );
    }

    private static void deleteTestData() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        ); PreparedStatement deleteEvents = connection.prepareStatement("""
                DELETE FROM knowledge_review_event
                WHERE submission_id IN (?, ?)
                """); PreparedStatement deleteCandidates = connection.prepareStatement("""
                DELETE FROM knowledge_candidate
                WHERE submission_id IN (?, ?)
                """); PreparedStatement deleteSubmissions = connection.prepareStatement("""
                DELETE FROM knowledge_submission
                WHERE id IN (?, ?)
                """); PreparedStatement deleteChunks = connection.prepareStatement("""
                DELETE FROM knowledge_chunk
                WHERE source_id IN (
                    SELECT id FROM knowledge_source
                    WHERE document_family = ? AND knowledge_scope = ?
                )
                """); PreparedStatement deleteSources = connection.prepareStatement("""
                DELETE FROM knowledge_source
                WHERE document_family = ? AND knowledge_scope = ?
                """)) {
            deleteEvents.setObject(1, OLD_SUBMISSION_ID);
            deleteEvents.setObject(2, NEW_SUBMISSION_ID);
            deleteEvents.executeUpdate();
            deleteCandidates.setObject(1, OLD_SUBMISSION_ID);
            deleteCandidates.setObject(2, NEW_SUBMISSION_ID);
            deleteCandidates.executeUpdate();
            deleteSubmissions.setObject(1, OLD_SUBMISSION_ID);
            deleteSubmissions.setObject(2, NEW_SUBMISSION_ID);
            deleteSubmissions.executeUpdate();
            deleteChunks.setString(1, DOCUMENT_FAMILY);
            deleteChunks.setString(2, KNOWLEDGE_SCOPE);
            deleteChunks.executeUpdate();
            deleteSources.setString(1, DOCUMENT_FAMILY);
            deleteSources.setString(2, KNOWLEDGE_SCOPE);
            deleteSources.executeUpdate();
        }
    }
}
