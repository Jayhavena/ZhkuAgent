package com.zhku.knowledge.conversation.publicelective;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * 从共享库里取"选课调整办理"的那条已发布证据。
 *
 * <p><b>为什么按条款短语定位而不是按 chunk_index 写死</b>：chunk_index 是发布时的顺序号，
 * 重新发布就会变；用条款短语（"选课、退选、补选和改选"）定位，配合"已发布 + 学生可见 +
 * `PUBLIC_ELECTIVE` 范围"三重条件，能保证取到的永远是**审核过且允许给学生看**的那一条。
 * 取不到就返回空——上游据此降级为"资料不足"，绝不用相邻条款补全。</p>
 */
public final class JdbcAdjustmentEvidenceFinder implements AdjustmentEvidenceFinder {

    /** 该主题的判定短语，同时也是主题边界的声明：只有讲"选课/退选/补选/改选办理"的条款才算证据。 */
    static final String ADJUSTMENT_CLAUSE_PHRASE = "选课、退选、补选和改选";

    private static final String KNOWLEDGE_SCOPE = "PUBLIC_ELECTIVE";

    private final DataSource dataSource;

    public JdbcAdjustmentEvidenceFinder(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    @Override
    public Optional<AdjustmentEvidence> find() {
        String sql = """
                SELECT source.id, source.title, source.source_version,
                       chunk.source_locator, source.publication_scope, chunk.content
                FROM knowledge_chunk chunk
                JOIN knowledge_source source ON source.id = chunk.source_id
                WHERE source.publication_status = 'PUBLISHED'
                  AND source.visibility_scope = 'STUDENT_VISIBLE'
                  AND source.knowledge_scope = ?
                  AND chunk.content LIKE ?
                ORDER BY source.published_at DESC, chunk.chunk_index ASC
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, KNOWLEDGE_SCOPE);
            statement.setString(2, "%" + ADJUSTMENT_CLAUSE_PHRASE + "%");
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AdjustmentEvidence(
                        result.getObject("id", java.util.UUID.class),
                        result.getString("title"),
                        result.getString("source_version"),
                        result.getString("source_locator"),
                        result.getString("publication_scope"),
                        result.getString("content")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取选课调整办理证据", exception);
        }
    }
}
