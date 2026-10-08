package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.rag.CitationAuditRecord;
import com.zhku.knowledge.rag.CitationAuditRepository;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** 查证捕获的固定索引审计；不能沿用旧仓储按latest条目解析。 */
public final class JdbcRuleCheckCitationAuditRepository implements CitationAuditRepository {
    private final DataSource dataSource;
    private final String profileId, chunkingVersion, requiredScope;

    public JdbcRuleCheckCitationAuditRepository(DataSource dataSource, String profileId,
                                                String chunkingVersion, String requiredScope) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.profileId = nonBlank(profileId);
        this.chunkingVersion = nonBlank(chunkingVersion);
        this.requiredScope = nonBlank(requiredScope);
    }

    private static String nonBlank(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("审计必须有明确服务端索引及范围配置");
        return text;
    }

    @Override public void record(CitationAuditRecord record) {
        Objects.requireNonNull(record);
        // 四参旧记录没有捕获绑定；提前拒绝，不能连接后用latest“补齐”或由客户端自证。
        if (!"RULE_CHECK_CAPTURE".equals(record.route()) || record.citationId().isBlank()
                || record.sourceId() == null || record.sourceRevision() == null || record.sourceRevision().isBlank()
                || record.contentChecksum() == null || !record.contentChecksum().matches("[0-9a-fA-F]{64}")
                || record.content() == null || record.content().isBlank())
            throw new IllegalArgumentException("查证捕获审计要求完整服务端证据绑定");
        // 单条INSERT SELECT按一个SQL快照检查当前字段资格并绑定entry；不先查latest再插入。
        // 这是捕获尝试留痕，仍不能消除语句完成到最终HTTP下发之间的撤回窗口。
        String sql = """
                INSERT INTO citation_audit(id, citation_id, index_entry_id, route, session_id, created_at)
                SELECT ?, ?, entry.id, ?, NULL, ?
                FROM retrieval_index_entry entry
                JOIN knowledge_chunk chunk ON chunk.id = entry.chunk_id
                JOIN knowledge_source source ON source.id = chunk.source_id
                JOIN embedding_profile profile ON profile.profile_id = entry.profile_id
                JOIN retrieval_index_run index_run ON index_run.id = entry.run_id
                WHERE entry.profile_id = ? AND entry.chunking_version = ?
                  AND source.knowledge_scope = ? AND source.id = ? AND chunk.id = ?
                  AND profile.status = 'ACTIVE' AND index_run.status = 'COMPLETED'
                  AND index_run.profile_id = entry.profile_id
                  AND index_run.chunking_version = entry.chunking_version
                  AND source.publication_status = 'PUBLISHED'
                  AND source.visibility_scope = 'STUDENT_VISIBLE'
                  AND source.sharing_rights_confirmed = TRUE
                  AND source.source_version = ? AND entry.source_version = source.source_version
                  AND lower(entry.content_checksum) = ? AND lower(chunk.content_checksum) = ?
                  AND chunk.content = ?
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, record.citationId());
            statement.setString(3, record.route());
            statement.setTimestamp(4, Timestamp.from(record.issuedAt()));
            statement.setString(5, profileId); statement.setString(6, chunkingVersion);
            statement.setString(7, requiredScope); statement.setObject(8, record.sourceId());
            statement.setObject(9, record.chunkId()); statement.setString(10, record.sourceRevision());
            String checksum = record.contentChecksum().toLowerCase(Locale.ROOT);
            statement.setString(11, checksum); statement.setString(12, checksum);
            statement.setString(13, record.content());
            // schema的profile/chunk/切分唯一约束保证至多一行；零行绝不能按其他profile回退。
            if (statement.executeUpdate() != 1) throw new IllegalStateException("捕获证据当前不可审计或索引绑定不唯一");
        } catch (SQLException unavailable) {
            throw new IllegalStateException("无法写入绑定引用审计", unavailable);
        }
    }
}
