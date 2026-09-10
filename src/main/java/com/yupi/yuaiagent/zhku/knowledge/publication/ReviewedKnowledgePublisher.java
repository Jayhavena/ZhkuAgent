package com.yupi.yuaiagent.zhku.knowledge.publication;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 将人工审核资料写入知识库，并提供开发阶段的关键词检索。
 *
 * <p>这里刻意不接入嵌入模型或通用 Agent：先证明“审核状态—片段—出处”的数据链正确，再进入 RAG 阶段。</p>
 */
public final class ReviewedKnowledgePublisher {

    private final DataSource dataSource;

    public ReviewedKnowledgePublisher(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    /**
     * 在同一事务中发布来源与片段。
     *
     * <p>若任一片段写入失败，整个事务回滚，避免来源显示为已发布却没有完整引用片段。</p>
     */
    public void publish(ReviewedKnowledgePublication publication) {
        Objects.requireNonNull(publication, "发布资料不能为空");
        publication.requireApproved();

        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsertSource(connection, publication);
                for (ReviewedKnowledgeChunk chunk : publication.chunks().stream()
                        .sorted(Comparator.comparingInt(
                                ReviewedKnowledgeChunk::chunkIndex
                        ))
                        .toList()) {
                    upsertChunk(connection, publication.sourceId(), chunk);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法发布已审核知识资料", exception);
        }
    }

    /**
     * 仅从当前已发布资料中按关键词返回片段。
     *
     * <p>这是首个数据闭环的可验证检索，不替代后续的语义检索；小规模资料使用它能避免过早引入模型依赖。</p>
     */
    public List<PublishedKnowledgeChunk> searchPublished(String keyword) {
        return search(keyword, false);
    }

    /**
     * 面向未来学生接口的检索边界。
     *
     * <p>不能复用本地验证检索：PUBLISHED 的内部资料尚未自动获得学生公开资格。</p>
     */
    public List<PublishedKnowledgeChunk> searchStudentVisiblePublished(
            String keyword
    ) {
        return search(keyword, true);
    }

    private List<PublishedKnowledgeChunk> search(
            String keyword,
            boolean studentVisibleOnly
    ) {
        String normalizedKeyword = requireKeyword(keyword);
        String pattern = "%" + escapeLike(normalizedKeyword) + "%";
        String studentVisibilityCondition = studentVisibleOnly
                ? """
                  AND source.visibility_scope = 'STUDENT_VISIBLE'
                  AND source.sharing_rights_confirmed = TRUE
                  """
                : "";
        String sql = """
                SELECT source.id, source.title, chunk.source_locator,
                       chunk.page_number, chunk.content
                FROM knowledge_chunk chunk
                JOIN knowledge_source source ON source.id = chunk.source_id
                WHERE source.publication_status = 'PUBLISHED'
                """ + studentVisibilityCondition + """
                  AND (chunk.content ILIKE ? ESCAPE '\\'
                       OR chunk.section_title ILIKE ? ESCAPE '\\')
                ORDER BY source.published_at DESC, chunk.chunk_index ASC
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            try (ResultSet result = statement.executeQuery()) {
                List<PublishedKnowledgeChunk> chunks = new ArrayList<>();
                while (result.next()) {
                    chunks.add(new PublishedKnowledgeChunk(
                            result.getObject(1, UUID.class),
                            result.getString(2),
                            result.getString(3),
                            result.getInt(4),
                            result.getString(5)
                    ));
                }
                return List.copyOf(chunks);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法检索已发布知识片段", exception);
        }
    }

    private void upsertSource(
            Connection connection,
            ReviewedKnowledgePublication publication
    ) throws SQLException {
        String sql = """
                INSERT INTO knowledge_source (
                    id, title, source_type, source_url, source_version,
                    publication_status, published_at, content_checksum,
                    visibility_scope, publication_scope, review_notice,
                    sharing_rights_confirmed
                ) VALUES (?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    title = EXCLUDED.title,
                    source_type = EXCLUDED.source_type,
                    source_url = EXCLUDED.source_url,
                    source_version = EXCLUDED.source_version,
                    publication_status = 'PUBLISHED',
                    published_at = EXCLUDED.published_at,
                    content_checksum = EXCLUDED.content_checksum,
                    visibility_scope = EXCLUDED.visibility_scope,
                    publication_scope = EXCLUDED.publication_scope,
                    review_notice = EXCLUDED.review_notice,
                    sharing_rights_confirmed = EXCLUDED.sharing_rights_confirmed,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, publication.sourceId());
            statement.setString(2, publication.sourceTitle());
            statement.setString(3, publication.sourceType());
            statement.setString(4, publication.sourceUrl());
            statement.setString(5, publication.sourceVersion());
            // PostgreSQL JDBC 不会推断 Instant 的 SQL 类型，显式转为时间戳才能稳定写入 TIMESTAMPTZ。
            statement.setTimestamp(6, Timestamp.from(publication.reviewedAt()));
            statement.setString(7, checksumOfPublication(publication));
            statement.setString(
                    8,
                    publication.studentVisible()
                            ? "STUDENT_VISIBLE"
                            : "INTERNAL_ONLY"
            );
            statement.setString(9, publication.publicationScope());
            statement.setString(10, publication.reviewNotice());
            statement.setBoolean(11, publication.sharingRightsConfirmed());
            statement.executeUpdate();
        }
    }

    private void upsertChunk(
            Connection connection,
            UUID sourceId,
            ReviewedKnowledgeChunk chunk
    ) throws SQLException {
        String sql = """
                INSERT INTO knowledge_chunk (
                    id, source_id, chunk_index, section_title,
                    source_locator, page_number, content, content_checksum
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_id, chunk_index) DO UPDATE SET
                    section_title = EXCLUDED.section_title,
                    source_locator = EXCLUDED.source_locator,
                    page_number = EXCLUDED.page_number,
                    content = EXCLUDED.content,
                    content_checksum = EXCLUDED.content_checksum
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, stableChunkId(sourceId, chunk.chunkIndex()));
            statement.setObject(2, sourceId);
            statement.setInt(3, chunk.chunkIndex());
            statement.setString(4, chunk.sectionTitle());
            statement.setString(5, chunk.sourceLocator());
            statement.setInt(6, chunk.pageNumber());
            statement.setString(7, chunk.content());
            statement.setString(8, sha256(chunk.content()));
            statement.executeUpdate();
        }
    }

    private static String requireKeyword(String keyword) {
        Objects.requireNonNull(keyword, "检索关键词不能为空");
        String normalized = keyword.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("检索关键词不能为空");
        }
        return normalized;
    }

    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static UUID stableChunkId(UUID sourceId, int chunkIndex) {
        String identity = sourceId + ":" + chunkIndex;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static String checksumOfPublication(
            ReviewedKnowledgePublication publication
    ) {
        String canonicalContent = publication.chunks().stream()
                .sorted(Comparator.comparingInt(ReviewedKnowledgeChunk::chunkIndex))
                .map(chunk -> chunk.chunkIndex() + "\n" + chunk.content())
                .reduce("", (left, right) -> left + right + "\n");
        return sha256(canonicalContent);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            ).toUpperCase();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }
}
