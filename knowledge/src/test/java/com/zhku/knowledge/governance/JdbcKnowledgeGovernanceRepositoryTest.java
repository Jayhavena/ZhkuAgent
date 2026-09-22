package com.zhku.knowledge.governance;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcKnowledgeGovernanceRepositoryTest {

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
    private static final UUID TEST_SUBMISSION_ID = UUID.fromString(
            "b476fd94-69f8-4554-8917-1859e16b5398"
    );
    private static final UUID TEST_CANDIDATE_ID = UUID.fromString(
            "c0d890e0-a5aa-4eb0-9733-5f4a8b1eae1e"
    );
    private static final UUID SECOND_TEST_SUBMISSION_ID = UUID.fromString(
            "73e2fc6c-9dcc-40cd-b1ec-26822c5c6fc8"
    );

    private KnowledgeGovernanceRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        deleteTestSubmission();
        repository = new JdbcKnowledgeGovernanceRepository(dataSource());
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteTestSubmission();
    }

    @Test
    void shouldStoreSubmissionCandidatesAndReviewEventInStagingArea() {
        KnowledgeSubmission submission = submission();
        KnowledgeCandidate candidate = candidate();

        repository.createSubmission(submission);
        repository.saveCandidates(submission.id(), List.of(candidate));
        repository.appendEvent(new KnowledgeReviewEvent(
                UUID.randomUUID(),
                submission.id(),
                candidate.id(),
                "EXTRACT",
                "已按页生成候选片段。",
                "本机管理员",
                Instant.parse("2026-09-12T00:00:00Z")
        ));

        assertThat(repository.findSubmission(submission.id()))
                .contains(submission);
        assertThat(repository.listCandidates(submission.id()))
                .containsExactly(candidate);
        assertThat(repository.findCandidate(candidate.id()))
                .contains(candidate);
    }

    @Test
    void shouldAtomicallyCompleteExtractionWithCandidatesAndEvent() {
        KnowledgeSubmission submission = submission();
        KnowledgeCandidate candidate = candidate();

        repository.createSubmissionWithEvent(
                submission,
                event(submission.id(), null, "CREATE_SUBMISSION")
        );
        repository.completeExtraction(
                submission.id(),
                List.of(candidate),
                event(submission.id(), null, "EXTRACT_CANDIDATES")
        );

        assertThat(repository.findSubmission(submission.id()))
                .get()
                .extracting(KnowledgeSubmission::status)
                .isEqualTo(KnowledgeSubmissionStatus.EXTRACTED);
        assertThat(repository.listCandidates(submission.id()))
                .containsExactly(candidate);
    }

    @Test
    void shouldRejectActiveDuplicateButAllowArchivedChecksumToBeResubmitted() {
        KnowledgeSubmission activeSubmission = submission();
        KnowledgeSubmission archivedReplacement = submission(
                SECOND_TEST_SUBMISSION_ID,
                "reimported-submission.pdf"
        );

        repository.createSubmission(activeSubmission);

        assertThatThrownBy(() ->
                repository.createSubmission(archivedReplacement)
        ).isInstanceOf(IllegalStateException.class);

        repository.markSubmissionArchived(activeSubmission.id());
        repository.createSubmission(archivedReplacement);

        assertThat(repository.findSubmission(archivedReplacement.id()))
                .contains(archivedReplacement);
    }

    private KnowledgeSubmission submission() {
        return submission(TEST_SUBMISSION_ID, "test-submission.pdf");
    }

    private KnowledgeSubmission submission(UUID id, String storageKey) {
        return new KnowledgeSubmission(
                id,
                "STUDENT_HANDBOOK",
                "ACADEMIC_STATUS",
                "本科生学生手册（2025版）",
                "2025版",
                "学校官网公开资料",
                "https://example.edu.cn/handbook",
                "全日制普通本科生",
                LocalDate.of(2025, 9, 1),
                true,
                LocalDate.of(2025, 9, 1),
                true,
                storageKey,
                "2025版学生手册.pdf",
                "A".repeat(64),
                KnowledgeSubmissionStatus.DRAFT,
                "仅供本机审核。",
                Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z")
        );
    }

    private KnowledgeCandidate candidate() {
        return new KnowledgeCandidate(
                TEST_CANDIDATE_ID,
                TEST_SUBMISSION_ID,
                76,
                76,
                "原始候选文本",
                null,
                null,
                null,
                CandidateReviewStatus.PENDING_REVIEW,
                null,
                null,
                Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z")
        );
    }

    private static KnowledgeReviewEvent event(
            UUID submissionId,
            UUID candidateId,
            String actionType
    ) {
        return new KnowledgeReviewEvent(
                UUID.randomUUID(),
                submissionId,
                candidateId,
                actionType,
                "本机资料治理测试操作。",
                "本机管理员",
                Instant.parse("2026-09-12T00:00:00Z")
        );
    }

    private DataSource dataSource() {
        return new DriverManagerDataSource(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        );
    }

    private void deleteTestSubmission() throws SQLException {
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
                """); PreparedStatement deleteSubmission = connection.prepareStatement("""
                DELETE FROM knowledge_submission
                WHERE id IN (?, ?)
                """)) {
            deleteEvents.setObject(1, TEST_SUBMISSION_ID);
            deleteEvents.setObject(2, SECOND_TEST_SUBMISSION_ID);
            deleteEvents.executeUpdate();
            deleteCandidates.setObject(1, TEST_SUBMISSION_ID);
            deleteCandidates.setObject(2, SECOND_TEST_SUBMISSION_ID);
            deleteCandidates.executeUpdate();
            deleteSubmission.setObject(1, TEST_SUBMISSION_ID);
            deleteSubmission.setObject(2, SECOND_TEST_SUBMISSION_ID);
            deleteSubmission.executeUpdate();
        }
    }
}
