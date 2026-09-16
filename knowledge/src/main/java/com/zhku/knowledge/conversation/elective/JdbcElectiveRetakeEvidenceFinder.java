package com.zhku.knowledge.conversation.elective;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    public JdbcElectiveRetakeEvidenceFinder(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DOCUMENT_FAMILY);
            statement.setString(2, KNOWLEDGE_SCOPE);
            statement.setString(3, "%" + requiredPhrase + "%");
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ElectiveRetakeEvidence(
                        result.getObject(1, UUID.class),
                        result.getString(2),
                        result.getString(3),
                        result.getString(5),
                        result.getString(4),
                        result.getString(6)
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法查询已发布的选修课重修证据", exception);
        }
    }
}
