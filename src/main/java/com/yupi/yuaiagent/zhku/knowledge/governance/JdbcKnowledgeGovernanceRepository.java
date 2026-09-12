package com.yupi.yuaiagent.zhku.knowledge.governance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 资料治理暂存区的 JDBC 实现。
 *
 * <p>审核草稿只写入暂存表，现有问答检索不会经过本类读取这些数据。</p>
 */
public final class JdbcKnowledgeGovernanceRepository
        implements KnowledgeGovernanceRepository {

    private final DataSource dataSource;

    public JdbcKnowledgeGovernanceRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    @Override
    public void createSubmission(KnowledgeSubmission submission) {
        Objects.requireNonNull(submission, "投稿不能为空");
        String sql = """
                INSERT INTO knowledge_submission (
                    id, document_family, knowledge_scope, title,
                    source_version, source_description, source_url,
                    applicable_scope, publication_date,
                    publication_date_confirmed, effective_date,
                    effective_date_confirmed, storage_key,
                    original_file_name, content_checksum,
                    submission_status, review_notice, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSubmission(statement, submission);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法创建资料投稿", exception);
        }
    }

    @Override
    public Optional<KnowledgeSubmission> findSubmission(UUID submissionId) {
        Objects.requireNonNull(submissionId, "投稿编号不能为空");
        String sql = """
                SELECT id, document_family, knowledge_scope, title,
                       source_version, source_description, source_url,
                       applicable_scope, publication_date,
                       publication_date_confirmed, effective_date,
                       effective_date_confirmed, storage_key,
                       original_file_name, content_checksum,
                       submission_status, review_notice, created_at, updated_at
                FROM knowledge_submission
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, submissionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(submissionFrom(result))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取资料投稿", exception);
        }
    }

    @Override
    public Optional<KnowledgeCandidate> findCandidate(UUID candidateId) {
        Objects.requireNonNull(candidateId, "候选片段编号不能为空");
        String sql = candidateSelectSql() + " WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, candidateId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(candidateFrom(result))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取候选片段", exception);
        }
    }

    @Override
    public List<KnowledgeCandidate> listCandidates(UUID submissionId) {
        Objects.requireNonNull(submissionId, "投稿编号不能为空");
        String sql = candidateSelectSql() + """
                WHERE submission_id = ?
                ORDER BY start_page_number, end_page_number, created_at, id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, submissionId);
            try (ResultSet result = statement.executeQuery()) {
                List<KnowledgeCandidate> candidates = new ArrayList<>();
                while (result.next()) {
                    candidates.add(candidateFrom(result));
                }
                return List.copyOf(candidates);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取投稿候选片段", exception);
        }
    }

    @Override
    public void saveCandidates(
            UUID submissionId,
            List<KnowledgeCandidate> candidates
    ) {
        Objects.requireNonNull(submissionId, "投稿编号不能为空");
        List<KnowledgeCandidate> copiedCandidates = List.copyOf(
                Objects.requireNonNull(candidates, "候选片段不能为空")
        );
        if (copiedCandidates.stream().anyMatch(candidate ->
                !submissionId.equals(candidate.submissionId()))) {
            throw new IllegalArgumentException("候选片段必须属于同一投稿");
        }
        String sql = """
                INSERT INTO knowledge_candidate (
                    id, submission_id, start_page_number, end_page_number,
                    raw_text, edited_text, section_title, source_locator,
                    review_status, review_note, discard_reason,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (KnowledgeCandidate candidate : copiedCandidates) {
                bindCandidate(statement, candidate);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法保存候选片段", exception);
        }
    }

    @Override
    public void updateCandidate(KnowledgeCandidate candidate) {
        Objects.requireNonNull(candidate, "候选片段不能为空");
        String sql = """
                UPDATE knowledge_candidate
                SET start_page_number = ?, end_page_number = ?, raw_text = ?,
                    edited_text = ?, section_title = ?, source_locator = ?,
                    review_status = ?, review_note = ?, discard_reason = ?,
                    updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, candidate.startPageNumber());
            statement.setInt(2, candidate.endPageNumber());
            statement.setString(3, candidate.rawText());
            statement.setString(4, candidate.editedText());
            statement.setString(5, candidate.sectionTitle());
            statement.setString(6, candidate.sourceLocator());
            statement.setString(7, candidate.reviewStatus().name());
            statement.setString(8, candidate.reviewNote());
            statement.setString(9, candidate.discardReason());
            statement.setTimestamp(10, Timestamp.from(candidate.updatedAt()));
            statement.setObject(11, candidate.id());
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("候选片段不存在");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法更新候选片段", exception);
        }
    }

    @Override
    public void appendEvent(KnowledgeReviewEvent event) {
        Objects.requireNonNull(event, "操作记录不能为空");
        String sql = """
                INSERT INTO knowledge_review_event (
                    id, submission_id, candidate_id, action_type,
                    action_note, actor_label, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, event.id());
            statement.setObject(2, event.submissionId());
            statement.setObject(3, event.candidateId());
            statement.setString(4, event.actionType());
            statement.setString(5, event.actionNote());
            statement.setString(6, event.actorLabel());
            statement.setTimestamp(7, Timestamp.from(event.occurredAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法追加资料治理操作记录", exception);
        }
    }

    @Override
    public Optional<UUID> findPublishedSourceId(
            String documentFamily,
            String knowledgeScope
    ) {
        String sql = """
                SELECT id
                FROM knowledge_source
                WHERE document_family = ?
                  AND knowledge_scope = ?
                  AND publication_status = 'PUBLISHED'
                ORDER BY published_at DESC
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentFamily);
            statement.setString(2, knowledgeScope);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(result.getObject(1, UUID.class))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法查询已发布资料来源", exception);
        }
    }

    @Override
    public void markSubmissionPublished(UUID submissionId) {
        updateSubmissionStatus(submissionId, KnowledgeSubmissionStatus.PUBLISHED);
    }

    @Override
    public void markSubmissionArchived(UUID submissionId) {
        updateSubmissionStatus(submissionId, KnowledgeSubmissionStatus.ARCHIVED);
    }

    private void updateSubmissionStatus(
            UUID submissionId,
            KnowledgeSubmissionStatus status
    ) {
        String sql = """
                UPDATE knowledge_submission
                SET submission_status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setObject(2, submissionId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("资料投稿不存在");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法更新资料投稿状态", exception);
        }
    }

    private static void bindSubmission(
            PreparedStatement statement,
            KnowledgeSubmission submission
    ) throws SQLException {
        statement.setObject(1, submission.id());
        statement.setString(2, submission.documentFamily());
        statement.setString(3, submission.knowledgeScope());
        statement.setString(4, submission.title());
        statement.setString(5, submission.sourceVersion());
        statement.setString(6, submission.sourceDescription());
        statement.setString(7, submission.sourceUrl());
        statement.setString(8, submission.applicableScope());
        statement.setObject(9, submission.publicationDate());
        statement.setBoolean(10, submission.publicationDateConfirmed());
        statement.setObject(11, submission.effectiveDate());
        statement.setBoolean(12, submission.effectiveDateConfirmed());
        statement.setString(13, submission.storageKey());
        statement.setString(14, submission.originalFileName());
        statement.setString(15, submission.contentChecksum());
        statement.setString(16, submission.status().name());
        statement.setString(17, submission.reviewNotice());
        statement.setTimestamp(18, Timestamp.from(submission.createdAt()));
        statement.setTimestamp(19, Timestamp.from(submission.updatedAt()));
    }

    private static void bindCandidate(
            PreparedStatement statement,
            KnowledgeCandidate candidate
    ) throws SQLException {
        statement.setObject(1, candidate.id());
        statement.setObject(2, candidate.submissionId());
        statement.setInt(3, candidate.startPageNumber());
        statement.setInt(4, candidate.endPageNumber());
        statement.setString(5, candidate.rawText());
        statement.setString(6, candidate.editedText());
        statement.setString(7, candidate.sectionTitle());
        statement.setString(8, candidate.sourceLocator());
        statement.setString(9, candidate.reviewStatus().name());
        statement.setString(10, candidate.reviewNote());
        statement.setString(11, candidate.discardReason());
        statement.setTimestamp(12, Timestamp.from(candidate.createdAt()));
        statement.setTimestamp(13, Timestamp.from(candidate.updatedAt()));
    }

    private static KnowledgeSubmission submissionFrom(ResultSet result)
            throws SQLException {
        return new KnowledgeSubmission(
                result.getObject("id", UUID.class),
                result.getString("document_family"),
                result.getString("knowledge_scope"),
                result.getString("title"),
                result.getString("source_version"),
                result.getString("source_description"),
                result.getString("source_url"),
                result.getString("applicable_scope"),
                result.getObject("publication_date", LocalDate.class),
                result.getBoolean("publication_date_confirmed"),
                result.getObject("effective_date", LocalDate.class),
                result.getBoolean("effective_date_confirmed"),
                result.getString("storage_key"),
                result.getString("original_file_name"),
                result.getString("content_checksum"),
                KnowledgeSubmissionStatus.valueOf(
                        result.getString("submission_status")
                ),
                result.getString("review_notice"),
                instantOf(result, "created_at"),
                instantOf(result, "updated_at")
        );
    }

    private static KnowledgeCandidate candidateFrom(ResultSet result)
            throws SQLException {
        return new KnowledgeCandidate(
                result.getObject("id", UUID.class),
                result.getObject("submission_id", UUID.class),
                result.getInt("start_page_number"),
                result.getInt("end_page_number"),
                result.getString("raw_text"),
                result.getString("edited_text"),
                result.getString("section_title"),
                result.getString("source_locator"),
                CandidateReviewStatus.valueOf(result.getString("review_status")),
                result.getString("review_note"),
                result.getString("discard_reason"),
                instantOf(result, "created_at"),
                instantOf(result, "updated_at")
        );
    }

    private static Instant instantOf(ResultSet result, String column)
            throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private static String candidateSelectSql() {
        return """
                SELECT id, submission_id, start_page_number, end_page_number,
                       raw_text, edited_text, section_title, source_locator,
                       review_status, review_note, discard_reason,
                       created_at, updated_at
                FROM knowledge_candidate
                """;
    }
}
