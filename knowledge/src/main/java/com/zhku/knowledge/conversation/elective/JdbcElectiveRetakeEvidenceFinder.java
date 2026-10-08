package com.zhku.knowledge.conversation.elective;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.zhku.knowledge.conversation.ConversationJdbcAccess;
import com.zhku.knowledge.conversation.ConversationOperationBudget;

/**
 * 从学生可见的已发布 PostgreSQL 知识中读取首期重修规则。
 *
 * <p>查询同时限定资料族、知识范围、发布状态和公开权限；只按关键词命中旧资料会绕过版本替换
 * 与学生可见治理，因此不能复用现有的通用关键词检索入口。</p>
 */
public final class JdbcElectiveRetakeEvidenceFinder implements ElectiveRetakeEvidenceFinder {

    private static final String DOCUMENT_FAMILY = "STUDENT_HANDBOOK";
    private static final String KNOWLEDGE_SCOPE = "ELECTIVE_RETAKE";

    private final DataSource dataSource;
    private final ConversationJdbcAccess access;
    private final ConversationOperationBudget budget;

    public JdbcElectiveRetakeEvidenceFinder(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
        access = null;
        budget = null;
    }

    public JdbcElectiveRetakeEvidenceFinder(ConversationJdbcAccess access, ConversationOperationBudget budget) {
        dataSource = null;
        this.access = Objects.requireNonNull(access, "受控资源不能为空");
        this.budget = Objects.requireNonNull(budget, "请求预算不能为空");
    }

    @Override
    public Optional<ElectiveRetakeEvidence> find(CourseType courseType) {
        Objects.requireNonNull(courseType, "课程类别不能为空");
        String requiredPhrase = switch (courseType) {
            case PUBLIC_ELECTIVE -> "公共选修课不办理缓考";
            case PROFESSIONAL_ELECTIVE -> "专业选修课成绩不合格者可申请重修学习";
        };
        String sql = """
                SELECT source.id, source.title, source.source_version,
                       source.publication_scope, chunk.source_locator, chunk.content
                FROM knowledge_chunk chunk
                JOIN knowledge_source source ON source.id = chunk.source_id
                WHERE source.publication_status = 'PUBLISHED'
                  AND source.visibility_scope = 'STUDENT_VISIBLE'
                  AND source.sharing_rights_confirmed = TRUE
                  AND source.document_family = ?
                  AND source.knowledge_scope = ?
                  AND chunk.content ILIKE ?
                ORDER BY source.published_at DESC, chunk.chunk_index ASC
                LIMIT 1
                """;
        if (access != null) return access.execute(budget, ConversationJdbcAccess.WriteKind.NONE, transaction -> {
            try (PreparedStatement statement = transaction.prepare(sql)) { return readEvidence(statement, requiredPhrase); }
        });
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return readEvidence(statement, requiredPhrase);
        } catch (SQLException exception) {
            throw new IllegalStateException("无法查询已发布的选修课重修证据", exception);
        }
    }

    private static Optional<ElectiveRetakeEvidence> readEvidence(PreparedStatement statement, String phrase) throws SQLException {
        statement.setString(1, DOCUMENT_FAMILY); statement.setString(2, KNOWLEDGE_SCOPE); statement.setString(3, "%" + phrase + "%");
        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) return Optional.empty();
            return Optional.of(new ElectiveRetakeEvidence(result.getObject(1, UUID.class), result.getString(2),
                    result.getString(3), result.getString(5), result.getString(4), result.getString(6)));
        }
    }

    public boolean isCurrentlyVisible(ElectiveRetakeAnswer answer) {
        Objects.requireNonNull(answer, "原回答不能为空");
        // 只核验本次原引用，不能用更新的其他资料替换已生成的结论，也不能借Run的学分许可放行。
        String sql = """
                SELECT 1 FROM knowledge_source source JOIN knowledge_chunk chunk ON chunk.source_id=source.id
                WHERE source.id=? AND source.source_version=? AND chunk.source_locator=? AND source.publication_scope=?
                  AND source.document_family='STUDENT_HANDBOOK' AND source.knowledge_scope='ELECTIVE_RETAKE'
                  AND source.publication_status='PUBLISHED' AND source.visibility_scope='STUDENT_VISIBLE'
                  AND source.sharing_rights_confirmed=TRUE LIMIT 1
                """;
        if (access != null) return access.execute(budget, ConversationJdbcAccess.WriteKind.NONE, transaction -> {
            try (var statement = transaction.prepare(sql)) { return readVisible(statement, answer); }
        });
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            return readVisible(statement, answer);
        } catch (SQLException failure) { throw new IllegalStateException("无法核验原重修引用许可", failure); }
    }
    private static boolean readVisible(PreparedStatement statement, ElectiveRetakeAnswer answer) throws SQLException {
        statement.setObject(1,answer.sourceId()); statement.setString(2,answer.sourceVersion());
        statement.setString(3,answer.sourceLocator()); statement.setString(4,answer.applicableScope());
        try (var result = statement.executeQuery()) { return result.next(); }
    }
}
