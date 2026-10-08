package com.zhku.knowledge.retrieval;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * pgvector 向量候选检索：在指定 profile 的索引条目上按余弦距离排序。
 *
 * <p>查询向量维度必须与 profile 声明一致——维度不符直接判定该路不可用
 * （降级），而不是让数据库返回难以解释的距离排序。</p>
 */
public final class JdbcVectorCandidateRetriever implements ChunkCandidateRetriever {

    private final DataSource dataSource;
    private final RetrievalEligibilityFilter eligibilityFilter;
    private final String profileId;
    private final String requiredScope;
    private final EmbeddingClient embeddingClient;

    public JdbcVectorCandidateRetriever(
            DataSource dataSource,
            RetrievalEligibilityFilter eligibilityFilter,
            String profileId,
            String requiredScope,
            EmbeddingClient embeddingClient
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
        this.eligibilityFilter = Objects.requireNonNull(eligibilityFilter, "资格过滤器不能为空");
        this.profileId = Objects.requireNonNull(profileId, "profile 标识不能为空");
        this.requiredScope = requiredScope;
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embedding 客户端不能为空");
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int declaredDimensions = profileDimensions();
        if (embeddingClient.dimensions() != declaredDimensions) {
            throw new RetrieverUnavailableException(
                    "embedding 维度与 profile 不符：profile " + declaredDimensions
                            + "，客户端 " + embeddingClient.dimensions());
        }
        float[] queryVector = embeddingClient.embed(query);
        if (queryVector.length != declaredDimensions) {
            throw new RetrieverUnavailableException(
                    "查询向量长度与 profile 不符：profile " + declaredDimensions
                            + "，实际 " + queryVector.length);
        }

        List<RetrievedChunk> candidates = new ArrayList<>();
        for (CandidateRow row : loadRows(toVectorLiteral(queryVector))) {
            if (!eligibilityFilter.evaluate(row.source(), requiredScope).eligible()) {
                continue;
            }
            candidates.add(row.chunk());
            if (candidates.size() == limit) {
                break;
            }
        }
        return List.copyOf(candidates);
    }

    private int profileDimensions() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT dimensions FROM embedding_profile WHERE profile_id = ?")) {
            statement.setString(1, profileId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new RetrieverUnavailableException("profile 未在册：" + profileId);
                }
                return result.getInt("dimensions");
            }
        } catch (SQLException exception) {
            throw new RetrieverUnavailableException("无法读取 profile 维度", exception);
        }
    }

    private List<CandidateRow> loadRows(String vectorLiteral) {
        // 不在此处 LIMIT：资格过滤发生在 Java 侧，先截断会让不合格条目挤掉合格候选。
        String sql = """
                SELECT entry.chunk_id, chunk.content, chunk.source_locator, chunk.page_number,
                       chunk.content_checksum, source.id AS source_id, source.title AS source_title,
                       source.source_version, source.publication_status, source.visibility_scope,
                       source.sharing_rights_confirmed, source.knowledge_scope
                FROM retrieval_index_entry entry
                JOIN knowledge_chunk chunk ON chunk.id = entry.chunk_id
                JOIN knowledge_source source ON source.id = chunk.source_id
                WHERE entry.profile_id = ? AND entry.embedding IS NOT NULL
                ORDER BY entry.embedding <=> CAST(? AS vector), entry.chunk_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profileId);
            statement.setString(2, vectorLiteral);
            try (ResultSet result = statement.executeQuery()) {
                List<CandidateRow> rows = new ArrayList<>();
                while (result.next()) {
                    UUID sourceId = result.getObject("source_id", UUID.class);
                    rows.add(new CandidateRow(
                            new RetrievedChunk(
                                    result.getObject("chunk_id", UUID.class),
                                    result.getString("content"),
                                    result.getString("source_title"),
                                    result.getString("source_locator"),
                                    (Integer) result.getObject("page_number"),
                                    result.getString("source_version"),
                                    result.getString("content_checksum")
                            ),
                            new SourceEligibilitySnapshot(
                                    sourceId,
                                    result.getString("publication_status"),
                                    result.getString("visibility_scope"),
                                    result.getBoolean("sharing_rights_confirmed"),
                                    false,
                                    result.getString("knowledge_scope"),
                                    null,
                                    null
                            )
                    ));
                }
                return rows;
            }
        } catch (SQLException exception) {
            throw new RetrieverUnavailableException("无法执行向量候选检索", exception);
        }
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    private record CandidateRow(RetrievedChunk chunk, SourceEligibilitySnapshot source) {
    }
}
