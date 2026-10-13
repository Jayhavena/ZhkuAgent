package com.zhku.knowledge.retrieval;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 受控词法候选检索：在指定 profile 的索引条目上做词面匹配。
 *
 * <p>SQL先过滤当前schema可表达的资料与索引资格，仍保留
 * {@link RetrievalEligibilityFilter} 的最终复核；词面评分后截断，避免提前截无序集合。</p>
 */
public final class JdbcLexicalCandidateRetriever implements ChunkCandidateRetriever {

    private final DataSource dataSource;
    private final RetrievalEligibilityFilter eligibilityFilter;
    private final String profileId;
    private final String requiredScope;
    private final boolean offlineEvaluation;
    private final String chunkingVersion;
    private final boolean chineseBigram;
    private boolean diversity=true;

    public JdbcLexicalCandidateRetriever(DataSource dataSource, RetrievalEligibilityFilter eligibilityFilter,
            String profileId, String requiredScope, String chunkingVersion) {
        this(dataSource, eligibilityFilter, profileId, requiredScope, false, Objects.requireNonNull(chunkingVersion));
        if (chunkingVersion.isBlank()) throw new IllegalArgumentException("固定切片版本不能为空");
    }

    public JdbcLexicalCandidateRetriever(
            DataSource dataSource,
            RetrievalEligibilityFilter eligibilityFilter,
            String profileId,
            String requiredScope
    ) {
        this(dataSource, eligibilityFilter, profileId, requiredScope, false, null);
    }

    private JdbcLexicalCandidateRetriever(DataSource dataSource, RetrievalEligibilityFilter eligibilityFilter,
            String profileId, String requiredScope, boolean offlineEvaluation, String chunkingVersion) {
        this(dataSource,eligibilityFilter,profileId,requiredScope,offlineEvaluation,chunkingVersion,false);
    }
    private JdbcLexicalCandidateRetriever(DataSource dataSource, RetrievalEligibilityFilter eligibilityFilter,
            String profileId, String requiredScope, boolean offlineEvaluation, String chunkingVersion,boolean chineseBigram) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
        this.eligibilityFilter = Objects.requireNonNull(eligibilityFilter, "资格过滤器不能为空");
        this.profileId = Objects.requireNonNull(profileId, "profile 标识不能为空");
        this.requiredScope = requiredScope;
        this.offlineEvaluation = offlineEvaluation;
        this.chunkingVersion = chunkingVersion;
        this.chineseBigram=chineseBigram;
    }

    /** 显式中文词法实验；默认构造/应用装配保留旧基线，不能凭开发集召回自动切换。 */
    public static JdbcLexicalCandidateRetriever forChineseBigramEvaluation(DataSource dataSource,
            RetrievalEligibilityFilter eligibilityFilter,String profileId,String requiredScope,String chunkingVersion){
        if(chunkingVersion==null||chunkingVersion.isBlank())throw new IllegalArgumentException("实验切片版本必须固定");
        return new JdbcLexicalCandidateRetriever(dataSource,eligibilityFilter,profileId,requiredScope,false,chunkingVersion,true);
    }
    /** 仅本地对照实验使用旧排序；默认应用装配和资料资格过滤不变。 */
    public static JdbcLexicalCandidateRetriever forChineseBigramBaselineEvaluation(DataSource dataSource,
            RetrievalEligibilityFilter eligibilityFilter,String profileId,String requiredScope,String chunkingVersion){
        var retriever=forChineseBigramEvaluation(dataSource,eligibilityFilter,profileId,requiredScope,chunkingVersion);
        retriever.diversity=false;return retriever;
    }

    /** 评测必须在激活前完成；此显式入口允许BUILDING，但不放宽资料资格或允许RETIRED。 */
    public static JdbcLexicalCandidateRetriever forOfflineEvaluation(DataSource dataSource,
            RetrievalEligibilityFilter eligibilityFilter, String profileId, String requiredScope) {
        return new JdbcLexicalCandidateRetriever(dataSource, eligibilityFilter, profileId, requiredScope, true, null);
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int limit) {
        List<String> terms = QueryTerms.extract(query);
        if (terms.isEmpty() || limit <= 0) {
            return List.of();
        }
        if(chineseBigram){
            var eligible=loadRows().stream().filter(row->eligibilityFilter.evaluate(row.source(),requiredScope).eligible()).map(CandidateRow::chunk).toList();
            return diversity?ChineseBigramLexicalRanker.rank(query,eligible,limit):ChineseBigramLexicalRanker.rankWithoutDiversification(query,eligible,limit);
        }
        List<Scored> matched = new ArrayList<>();
        for (CandidateRow row : loadRows()) {
            if (!eligibilityFilter.evaluate(row.source(), requiredScope).eligible()) {
                continue;
            }
            int hits = 0;
            for (String term : terms) {
                if (row.chunk().content().contains(term)) {
                    hits++;
                }
            }
            if (hits > 0) {
                matched.add(new Scored(row.chunk(), hits));
            }
        }
        // 排名必须可重复：先按命中词数降序，再按片段标识字典序。
        matched.sort(Comparator.comparingInt(Scored::hits).reversed()
                .thenComparing(scored -> scored.chunk().chunkId()));
        return matched.stream()
                .limit(limit)
                .map(Scored::chunk)
                .toList();
    }

    private List<CandidateRow> loadRows() {
        assertReadableProfile();
        String sql = """
                SELECT entry.chunk_id, chunk.content, chunk.source_locator, chunk.page_number,
                       chunk.content_checksum, source.id AS source_id, source.title AS source_title,
                       source.source_version, source.publication_status, source.visibility_scope,
                       source.sharing_rights_confirmed, source.knowledge_scope
                FROM retrieval_index_entry entry
                JOIN knowledge_chunk chunk ON chunk.id = entry.chunk_id
                JOIN knowledge_source source ON source.id = chunk.source_id
                JOIN embedding_profile profile ON profile.profile_id = entry.profile_id
                JOIN retrieval_index_run index_run ON index_run.id = entry.run_id
                WHERE entry.profile_id = ?
                  AND (profile.status = 'ACTIVE' OR (? AND profile.status = 'BUILDING'))
                  AND index_run.status = 'COMPLETED'
                  AND index_run.profile_id = entry.profile_id
                  AND index_run.chunking_version = entry.chunking_version
                  AND source.publication_status = 'PUBLISHED'
                  AND source.visibility_scope = 'STUDENT_VISIBLE'
                  AND source.sharing_rights_confirmed = TRUE
                  AND (CAST(? AS VARCHAR) IS NULL OR source.knowledge_scope = ?)
                  AND (CAST(? AS VARCHAR) IS NULL OR entry.chunking_version = ?)
                  AND entry.source_version = source.source_version
                  AND entry.content_checksum = chunk.content_checksum
                ORDER BY entry.chunk_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profileId);
            statement.setBoolean(2, offlineEvaluation);
            statement.setString(3, requiredScope);
            statement.setString(4, requiredScope);
            // 新应用固定切片，旧入口保留null行为；过滤必须早于评分及截断。
            statement.setString(5, chunkingVersion);
            statement.setString(6, chunkingVersion);
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
                                    result.getString("content_checksum"),
                                    sourceId
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
            throw new RetrieverUnavailableException("无法执行词法候选检索", exception);
        }
    }

    private void assertReadableProfile() {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM embedding_profile WHERE profile_id = ?")) {
            statement.setString(1, profileId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) { throw new RetrieverUnavailableException("profile 未在册：" + profileId); }
                String status = result.getString("status");
                if (!"ACTIVE".equals(status) && !(offlineEvaluation && "BUILDING".equals(status))) {
                    throw new RetrieverUnavailableException("profile 不允许当前读取模式：" + status);
                }
            }
        } catch (SQLException exception) {
            throw new RetrieverUnavailableException("无法读取 profile 状态", exception);
        }
    }

    private record CandidateRow(RetrievedChunk chunk, SourceEligibilitySnapshot source) {
    }

    private record Scored(RetrievedChunk chunk, int hits) {
    }
}
