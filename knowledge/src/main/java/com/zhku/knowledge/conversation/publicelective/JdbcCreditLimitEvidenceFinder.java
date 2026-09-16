package com.zhku.knowledge.conversation.publicelective;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** 从独立知识范围读取公共选修课单学期学分上限的学生可见证据。 */
public final class JdbcCreditLimitEvidenceFinder implements CreditLimitEvidenceFinder {

    private static final String DOCUMENT_FAMILY = "STUDENT_HANDBOOK";
    private static final String KNOWLEDGE_SCOPE = "PUBLIC_ELECTIVE_CREDIT_LIMIT";
    private static final String REQUIRED_PHRASE = "每学期公共选修课最高不得超过 4 学分";

    private final DataSource dataSource;

    public JdbcCreditLimitEvidenceFinder(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    @Override
    public Optional<CreditLimitEvidence> find() {
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
            statement.setString(3, "%" + REQUIRED_PHRASE + "%");
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CreditLimitEvidence(
                        result.getObject(1, java.util.UUID.class),
                        result.getString(2),
                        result.getString(3),
                        result.getString(5),
                        result.getString(4),
                        result.getString(6)
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法查询已发布的公共选修课学分上限证据", exception);
        }
    }
}
