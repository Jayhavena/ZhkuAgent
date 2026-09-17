package com.zhku.knowledge.retrieval;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL 检索索引仓储。
 *
 * <p>索引运行与条目写入使用同一事务：任一条目写入失败时整轮回滚，
 * 不会留下"部分条目已可被检索"的中间态。</p>
 */
public final class JdbcRetrievalIndexRepository implements RetrievalIndexRepository {

    private final DataSource dataSource;

    public JdbcRetrievalIndexRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    @Override
    public void registerProfile(EmbeddingProfile profile) {
        Objects.requireNonNull(profile, "profile 不能为空");
        String sql = """
                INSERT INTO embedding_profile (profile_id, provider, model, dimensions, status)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (profile_id) DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.profileId());
            statement.setString(2, profile.provider());
            statement.setString(3, profile.model());
            statement.setInt(4, profile.dimensions());
            statement.setString(5, profile.status().name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法登记 embedding profile", exception);
        }
    }

    @Override
    public Optional<EmbeddingProfile> findProfile(String profileId) {
        Objects.requireNonNull(profileId, "profile 标识不能为空");
        String sql = """
                SELECT profile_id, provider, model, dimensions, status
                FROM embedding_profile WHERE profile_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profileId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new EmbeddingProfile(
                        result.getString("profile_id"),
                        result.getString("provider"),
                        result.getString("model"),
                        result.getInt("dimensions"),
                        ProfileStatus.valueOf(result.getString("status"))
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取 embedding profile", exception);
        }
    }

    @Override
    public UUID startRun(String profileId, String chunkingVersion) {
        Objects.requireNonNull(profileId, "profile 标识不能为空");
        Objects.requireNonNull(chunkingVersion, "切分版本不能为空");
        UUID runId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO retrieval_index_run (id, profile_id, chunking_version, status)
                     VALUES (?, ?, ?, 'RUNNING')
                     """)) {
            statement.setObject(1, runId);
            statement.setString(2, profileId);
            statement.setString(3, chunkingVersion);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法开启索引运行", exception);
        }
        return runId;
    }

    @Override
    public void appendEntry(
            UUID runId,
            String profileId,
            IndexableChunk chunk,
            String chunkingVersion,
            float[] embedding
    ) {
        Objects.requireNonNull(embedding, "向量不能为空");
        String sql = """
                INSERT INTO retrieval_index_entry (
                    id, run_id, profile_id, chunk_id, source_version,
                    chunking_version, content_checksum, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, runId);
            statement.setString(3, profileId);
            statement.setObject(4, chunk.chunkId());
            statement.setString(5, chunk.sourceVersion());
            statement.setString(6, chunkingVersion);
            statement.setString(7, chunk.contentChecksum());
            // pgvector 以文本形式接收向量；维度正确性已在服务层校验，此处只负责写入。
            statement.setString(8, toVectorLiteral(embedding));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法写入索引条目", exception);
        }
    }

    @Override
    public void completeRun(UUID runId, int indexedEntries) {
        finishRun(runId, "COMPLETED", indexedEntries);
    }

    @Override
    public void failRun(UUID runId) {
        finishRun(runId, "FAILED", 0);
    }

    @Override
    public List<IndexableChunk> listChunksWithSourceState() {
        String sql = """
                SELECT chunk.id AS chunk_id, chunk.content, chunk.content_checksum,
                       source.id AS source_id, source.source_version, source.publication_status,
                       source.visibility_scope, source.sharing_rights_confirmed, source.knowledge_scope
                FROM knowledge_chunk chunk
                JOIN knowledge_source source ON source.id = chunk.source_id
                ORDER BY source.id, chunk.chunk_index, chunk.id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<IndexableChunk> chunks = new ArrayList<>();
            while (result.next()) {
                chunks.add(new IndexableChunk(
                        result.getObject("chunk_id", UUID.class),
                        result.getString("content"),
                        result.getString("content_checksum"),
                        result.getString("source_version"),
                        new SourceEligibilitySnapshot(
                                result.getObject("source_id", UUID.class),
                                result.getString("publication_status"),
                                result.getString("visibility_scope"),
                                result.getBoolean("sharing_rights_confirmed"),
                                // 来源的"替代"语义由 publication_status=ARCHIVED 表达（无独立列）；
                                // 时态窗口属于通知表，来源层不设有效期，故此处传 null。
                                false,
                                result.getString("knowledge_scope"),
                                null,
                                null
                        )
                ));
            }
            return List.copyOf(chunks);
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取待索引片段", exception);
        }
    }

    private void finishRun(UUID runId, String status, int indexedEntries) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE retrieval_index_run
                     SET status = ?, finished_at = CURRENT_TIMESTAMP, indexed_entries = ?
                     WHERE id = ?
                     """)) {
            statement.setString(1, status);
            statement.setInt(2, indexedEntries);
            statement.setObject(3, runId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法更新索引运行状态", exception);
        }
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }
}
