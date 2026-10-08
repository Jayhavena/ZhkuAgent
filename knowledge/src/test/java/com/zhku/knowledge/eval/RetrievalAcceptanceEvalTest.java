package com.zhku.knowledge.eval;

import com.zhku.knowledge.ZhkuFlywayTestSupport;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.rag.GroundedAnswerComposer;
import com.zhku.knowledge.retrieval.EmbeddingClient;
import com.zhku.knowledge.retrieval.HybridCandidateFuser;
import com.zhku.knowledge.retrieval.HybridRetrievalService;
import com.zhku.knowledge.retrieval.IndexableChunk;
import com.zhku.knowledge.retrieval.JdbcLexicalCandidateRetriever;
import com.zhku.knowledge.retrieval.JdbcRetrievalIndexRepository;
import com.zhku.knowledge.retrieval.RetrievalEligibilityFilter;
import com.zhku.knowledge.retrieval.RetrievalOutcome;
import com.zhku.knowledge.retrieval.RetrieverUnavailableException;
import com.zhku.knowledge.retrieval.SourceEligibilitySnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 检索验收评测：在本机开发库跑最小版本化黄金集，输出 Recall@K / MRR / 引用准确率。
 *
 * <p><b>诚实边界</b>：本轮向量路不可用（无真实供应商 embedding profile），
 * 报告里的可用性标记如实记录这一点——指标只反映"词法路 + 降级融合"的链路能力，
 * **不得**解读为语义检索效果或质量提升。真实语义评测需接入真实 embedding profile 后另跑。</p>
 */
class RetrievalAcceptanceEvalTest {

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent"
    );
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();

    private static final String PROFILE = "eval-profile-v1";
    private static final String CHUNKING_VERSION = "fact-aware-v1";
    private static final int K = 3;

    private DataSource dataSource;
    private JdbcRetrievalIndexRepository indexRepository;

    @BeforeEach
    void setUp() throws SQLException {
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        dataSource = new DriverManagerDataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        deleteFixtures();
        seedCorpus();
        seedIndex();
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteFixtures();
    }

    @Test
    void shouldMeetBaselineOnVersionedGoldenSet() {
        RetrievalEligibilityFilter filter = new RetrievalEligibilityFilter(
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC));
        HybridRetrievalService hybrid = new HybridRetrievalService(new HybridCandidateFuser());
        JdbcLexicalCandidateRetriever lexical = JdbcLexicalCandidateRetriever.forOfflineEvaluation(
                dataSource, filter, PROFILE, "PUBLIC_ELECTIVE");
        // 向量路：本机无真实 embedding profile → 明确不可用（降级），而不是用假向量凑分。
        var vectorUnavailable = (com.zhku.knowledge.retrieval.ChunkCandidateRetriever)
                (query, limit) -> {
                    throw new RetrieverUnavailableException("本机未配置真实 embedding profile");
                };

        List<Double> recalls = new ArrayList<>();
        List<Double> rrs = new ArrayList<>();
        List<Double> citationAccuracies = new ArrayList<>();
        boolean lexicalAvailable = true;
        boolean vectorAvailable = true;

        for (RetrievalGoldenSet.Case goldenCase : RetrievalGoldenSet.cases()) {
            RetrievalOutcome outcome = hybrid.retrieve(
                    goldenCase.query(), K, lexical, vectorUnavailable);
            List<UUID> ranked = outcome.chunks().stream().map(chunk -> chunk.chunkId()).toList();

            // 逐题记录候选条数：引用准确率的分母是"本轮实际返回的候选数"，不是固定 K——
            // 不记录分母，两份基线之间该指标的差异就无法归因（容易被误读成"退化"）。
            System.out.println("[EVAL-DETAIL] q=" + goldenCase.query()
                    + ", lexicalCandidates=" + lexical.retrieve(goldenCase.query(), K).size()
                    + ", rankedCandidates=" + ranked.size());

            assertThat(ranked)
                    .as("内部资料永恒不可召回：" + goldenCase.query())
                    .doesNotContain(RetrievalGoldenSet.CHUNK_INTERNAL_DECOY);

            recalls.add(RetrievalMetrics.recallAtK(ranked, goldenCase.relevantChunkIds(), K));
            rrs.add(RetrievalMetrics.reciprocalRank(ranked, goldenCase.relevantChunkIds()));
            citationAccuracies.add(
                    RetrievalMetrics.citationAccuracy(outcome.chunks(), goldenCase.relevantChunkIds()));
            lexicalAvailable &= outcome.lexicalAvailable();
            vectorAvailable &= outcome.vectorAvailable();
        }

        RetrievalEvalReport report = new RetrievalEvalReport(
                RetrievalGoldenSet.VERSION, K, RetrievalGoldenSet.cases().size(),
                mean(recalls), mean(rrs), mean(citationAccuracies),
                lexicalAvailable, vectorAvailable,
                // 生成侧本轮未参与（无真实模型 profile），如实标注而不是留空。
                "未参与（无真实模型 profile，检索级走降级）",
                GroundedAnswerComposer.promptVersion(),
                CHUNKING_VERSION);
        System.out.println("[EVAL] " + report.render());

        // 基线断言：词法路应覆盖全部问题的相关条款，且相关条款排在第一（可作为回归护栏）。
        assertThat(report.caseCount()).isEqualTo(4);
        assertThat(report.lexicalAvailable()).isTrue();
        assertThat(report.vectorAvailable()).isFalse();
        assertThat(report.recallAtK()).isEqualTo(1.0);
        assertThat(report.mrr()).isEqualTo(1.0);
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private void seedCorpus() throws SQLException {
        insertSource(RetrievalGoldenSet.SOURCE_ID, "公共选修规则手册", "PUBLISHED", "STUDENT_VISIBLE", true);
        insertSource(RetrievalGoldenSet.INTERNAL_SOURCE_ID, "内部细则", "PUBLISHED", "INTERNAL_ONLY", true);
        insertChunk(RetrievalGoldenSet.CHUNK_CREDIT_LIMIT, RetrievalGoldenSet.SOURCE_ID,
                0, "第一条", RetrievalGoldenSet.CONTENT_CREDIT_LIMIT);
        insertChunk(RetrievalGoldenSet.CHUNK_NO_DEFERRAL, RetrievalGoldenSet.SOURCE_ID,
                1, "第二条", RetrievalGoldenSet.CONTENT_NO_DEFERRAL);
        insertChunk(RetrievalGoldenSet.CHUNK_WITHDRAWAL_WINDOW, RetrievalGoldenSet.SOURCE_ID,
                2, "第三条", RetrievalGoldenSet.CONTENT_WITHDRAWAL_WINDOW);
        insertChunk(RetrievalGoldenSet.CHUNK_NO_SELECTION, RetrievalGoldenSet.SOURCE_ID,
                3, "第四条", RetrievalGoldenSet.CONTENT_NO_SELECTION);
        insertChunk(RetrievalGoldenSet.CHUNK_INTERNAL_DECOY, RetrievalGoldenSet.INTERNAL_SOURCE_ID,
                0, "内部", RetrievalGoldenSet.CONTENT_INTERNAL_DECOY);
    }

    private void seedIndex() {
        indexRepository = new JdbcRetrievalIndexRepository(dataSource);
        indexRepository.registerProfile(new com.zhku.knowledge.retrieval.EmbeddingProfile(
                PROFILE, "local-eval", "deterministic-placeholder", 3,
                com.zhku.knowledge.retrieval.ProfileStatus.BUILDING));
        UUID runId = indexRepository.startRun(PROFILE, CHUNKING_VERSION);
        appendEntry(runId, RetrievalGoldenSet.CHUNK_CREDIT_LIMIT, RetrievalGoldenSet.SOURCE_ID,
                "公共选修规则手册", "STUDENT_VISIBLE", RetrievalGoldenSet.CONTENT_CREDIT_LIMIT);
        appendEntry(runId, RetrievalGoldenSet.CHUNK_NO_DEFERRAL, RetrievalGoldenSet.SOURCE_ID,
                "公共选修规则手册", "STUDENT_VISIBLE", RetrievalGoldenSet.CONTENT_NO_DEFERRAL);
        appendEntry(runId, RetrievalGoldenSet.CHUNK_WITHDRAWAL_WINDOW, RetrievalGoldenSet.SOURCE_ID,
                "公共选修规则手册", "STUDENT_VISIBLE", RetrievalGoldenSet.CONTENT_WITHDRAWAL_WINDOW);
        appendEntry(runId, RetrievalGoldenSet.CHUNK_NO_SELECTION, RetrievalGoldenSet.SOURCE_ID,
                "公共选修规则手册", "STUDENT_VISIBLE", RetrievalGoldenSet.CONTENT_NO_SELECTION);
        // 内部资料同样入索引：评测要能证明"检索侧自己会挡掉"，而不是靠"没索引"。
        appendEntry(runId, RetrievalGoldenSet.CHUNK_INTERNAL_DECOY, RetrievalGoldenSet.INTERNAL_SOURCE_ID,
                "内部细则", "INTERNAL_ONLY", RetrievalGoldenSet.CONTENT_INTERNAL_DECOY);
        indexRepository.completeRun(runId, 5);
    }

    private void appendEntry(
            UUID runId, UUID chunkId, UUID sourceId, String version,
            String visibility, String content
    ) {
        indexRepository.appendEntry(runId, PROFILE, new IndexableChunk(
                chunkId, content, checksum(content), version,
                new SourceEligibilitySnapshot(sourceId, "PUBLISHED", visibility,
                        true, false, "PUBLIC_ELECTIVE", null, null)),
                CHUNKING_VERSION, new float[]{0.1f, 0.2f, 0.3f});
    }

    private static String checksum(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void insertSource(
            UUID sourceId, String title, String status, String visibility, boolean rights
    ) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO knowledge_source (
                         id, title, source_type, source_version, publication_status,
                         published_at, content_checksum, visibility_scope, publication_scope,
                         review_notice, sharing_rights_confirmed, document_family, knowledge_scope
                     ) VALUES (?, ?, 'STUDENT_HANDBOOK', ?, ?, CURRENT_TIMESTAMP, ?, ?, '本地验证',
                         '评测语料', ?, 'STUDENT_HANDBOOK', 'PUBLIC_ELECTIVE')
                     """)) {
            statement.setObject(1, sourceId);
            statement.setString(2, title);
            statement.setString(3, title);
            statement.setString(4, status);
            statement.setString(5, "a".repeat(64));
            statement.setString(6, visibility);
            statement.setBoolean(7, rights);
            statement.executeUpdate();
        }
    }

    private void insertChunk(
            UUID chunkId, UUID sourceId, int index, String sectionTitle, String content
    ) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO knowledge_chunk (
                         id, source_id, chunk_index, section_title, source_locator,
                         page_number, content, content_checksum
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, chunkId);
            statement.setObject(2, sourceId);
            statement.setInt(3, index);
            statement.setString(4, sectionTitle);
            statement.setString(5, "第 " + (index + 1) + " 页");
            statement.setInt(6, index + 1);
            statement.setString(7, content);
            statement.setString(8, checksum(content));
            statement.executeUpdate();
        }
    }

    private void deleteFixtures() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)) {
            connection.createStatement().executeUpdate(
                    "DELETE FROM citation_audit WHERE index_entry_id IN "
                            + "(SELECT id FROM retrieval_index_entry WHERE profile_id = '" + PROFILE + "')");
            connection.createStatement().executeUpdate(
                    "DELETE FROM retrieval_index_entry WHERE profile_id = '" + PROFILE + "'");
            connection.createStatement().executeUpdate(
                    "DELETE FROM retrieval_index_run WHERE profile_id = '" + PROFILE + "'");
            connection.createStatement().executeUpdate(
                    "DELETE FROM embedding_profile WHERE profile_id = '" + PROFILE + "'");
            connection.createStatement().executeUpdate(
                    "DELETE FROM knowledge_chunk WHERE source_id IN ('"
                            + RetrievalGoldenSet.SOURCE_ID + "','" + RetrievalGoldenSet.INTERNAL_SOURCE_ID + "')");
            connection.createStatement().executeUpdate(
                    "DELETE FROM knowledge_source WHERE id IN ('"
                            + RetrievalGoldenSet.SOURCE_ID + "','" + RetrievalGoldenSet.INTERNAL_SOURCE_ID + "')");
        }
    }

    /** 占位：仅用于让索引条目具备 embedding 列，不参与本轮评测（向量路标记不可用）。 */
    @SuppressWarnings("unused")
    private static final class PlaceholderEmbeddingClient implements EmbeddingClient {

        @Override
        public float[] embed(String text) {
            return new float[]{0.1f, 0.2f, 0.3f};
        }

        @Override
        public int dimensions() {
            return 3;
        }
    }
}
