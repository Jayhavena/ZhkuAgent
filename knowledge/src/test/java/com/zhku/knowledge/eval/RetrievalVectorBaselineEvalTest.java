package com.zhku.knowledge.eval;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.GroundedAnswerComposer;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.rag.SpringAiEmbeddingModelClientFactory;
import com.zhku.knowledge.retrieval.EmbeddingClient;
import com.zhku.knowledge.retrieval.EmbeddingProfile;
import com.zhku.knowledge.retrieval.EmbeddingProfileSwitcher;
import com.zhku.knowledge.retrieval.HybridCandidateFuser;
import com.zhku.knowledge.retrieval.HybridRetrievalService;
import com.zhku.knowledge.retrieval.IndexableChunk;
import com.zhku.knowledge.retrieval.IndexRunResult;
import com.zhku.knowledge.retrieval.JdbcEmbeddingProfileStore;
import com.zhku.knowledge.retrieval.JdbcLexicalCandidateRetriever;
import com.zhku.knowledge.retrieval.JdbcRetrievalIndexRepository;
import com.zhku.knowledge.retrieval.JdbcVectorCandidateRetriever;
import com.zhku.knowledge.retrieval.ProfileStatus;
import com.zhku.knowledge.retrieval.RetrievalEligibilityFilter;
import com.zhku.knowledge.retrieval.RetrievalIndexingService;
import com.zhku.knowledge.retrieval.RetrievalOutcome;
import com.zhku.knowledge.retrieval.SourceEligibilitySnapshot;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第二份基线：**含真实向量召回**的检索评测（版本化黄金集，与首份同口径）。
 *
 * <p>本轮把"新 profile → 重建索引 → 评测 → 原子切换 → 保留回滚窗口"这条流程在**真实库 +
 * 真实 embedding** 上跑一遍：索引与查询向量都由 DashScope `text-embedding-v4` 产生，
 * 维度 1024 与 profile 声明一致（不符会被服务层响亮拒绝，禁止混用维度）。</p>
 *
 * <p><b>口径与首份基线的可对照性</b>：同一 `retrieval-golden-v1`、同 K=3、同分母（4 题各自的
 * 相关片段集合）、同可用性标记位、同样携带 prompt/profile/chunking 版本指纹。
 * 唯一变量是"向量路从不可用变为可用"，因此两份报告的差异才具备归因意义。</p>
 *
 * <p><b>诚实边界</b>：本测试通过只说明"该链路在真实 embedding 下可用且未越权召回"，
 * **不构成质量结论**——N=4、问法与条款同形、语料为评测专用小集合；未过评测前不得宣称
 * "效果提升"，也不得据此称生产上线。</p>
 *
 * <p><b>为什么自清</b>：本用例产生的索引条目引用评测语料，而 V9 对 `retrieval_index_entry →
 * knowledge_chunk` 是 ON DELETE RESTRICT。若留下条目，日常回归里清理评测语料的用例就会删不掉
 * 数据。因此本用例收尾删除自己造的行，并恢复本机原有 ACTIVE profile（见 {@link #foreignActive}）；
 * "在演示库上长期保留一份向量索引"属运维动作，不放在测试里做。</p>
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "zhku.rag.smoke", matches = "true")
class RetrievalVectorBaselineEvalTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
    );
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent"
    );
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();

    /** 真实索引 profile：名字即"供应商 + 模型 + 维度"，维度写进名字避免手工配错。 */
    private static final String PROFILE = "dashscope-text-embedding-v4-1024";
    private static final String PROVIDER = "dashscope";
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String EMBEDDING_MODEL = "text-embedding-v4";
    private static final int DIMENSIONS = 1024;

    private static final String SCOPE = "PUBLIC_ELECTIVE";
    private static final String CHUNKING_VERSION = "fact-aware-v1";
    private static final int K = 3;

    private DataSource dataSource;
    private JdbcEmbeddingProfileStore profileStore;
    private JdbcRetrievalIndexRepository indexRepository;
    /** 本机原有的 ACTIVE profile（若有）：测试期间暂退，收尾原样恢复。 */
    private EmbeddingProfile foreignActive;

    @BeforeEach
    void setUp() throws SQLException {
        Flyway.configure()
                .dataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dataSource = new DriverManagerDataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        profileStore = new JdbcEmbeddingProfileStore(dataSource);
        indexRepository = new JdbcRetrievalIndexRepository(dataSource);
        deleteFixtures();
        seedCorpus();
        foreignActive = profileStore.findActive()
                .filter(profile -> !PROFILE.equals(profile.profileId()))
                .map(profile -> {
                    // "全局最多一个 ACTIVE"是库级不变量，切换必然牵动在役 profile；
                    // 测试不能顺手把演示/真实 profile 退役掉，故暂退并记录、收尾恢复。
                    profileStore.save(new EmbeddingProfile(profile.profileId(), profile.provider(),
                            profile.model(), profile.dimensions(), ProfileStatus.RETIRED));
                    return profile;
                })
                .orElse(null);
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteFixtures();
        if (foreignActive != null) {
            profileStore.save(new EmbeddingProfile(foreignActive.profileId(), foreignActive.provider(),
                    foreignActive.model(), foreignActive.dimensions(), ProfileStatus.ACTIVE));
        }
    }

    @Test
    void shouldProduceSecondBaselineWithRealVectorRecall() {
        EmbeddingClient embeddingClient = realEmbeddingClient();

        // ① 新 profile（BUILDING）：维度 1024 与客户端一致，未走完"重建→评测"不得上线。
        profileStore.save(new EmbeddingProfile(
                PROFILE, PROVIDER, EMBEDDING_MODEL, DIMENSIONS, ProfileStatus.BUILDING));
        assertThat(profileStore.find(PROFILE)).map(EmbeddingProfile::status)
                .contains(ProfileStatus.BUILDING);

        // ② 用真实 embedding 重建索引：资格过滤与召回共用同一份规则，内部资料在索引侧就被挡掉。
        RetrievalEligibilityFilter filter = new RetrievalEligibilityFilter(
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC));
        RetrievalIndexingService indexing = new RetrievalIndexingService(
                indexRepository, embeddingClient, filter);
        IndexRunResult run = indexing.indexProfile(PROFILE, CHUNKING_VERSION, SCOPE);
        System.out.println("[EVAL-INDEX] profile=" + PROFILE + ", runId=" + run.runId()
                + ", candidateChunks=" + run.candidateChunks() + ", indexedEntries=" + run.indexedEntries()
                + ", embeddingModel=" + EMBEDDING_MODEL + ", dimensions=" + DIMENSIONS);
        // 口径事实（必须随报告一起看）：索引任务按"整库合格语料"建索引，本机演示库与本评测
        // 共用同一个数据库，所以候选池 = 评测夹具 + 演示库中已发布且学生可见的同范围片段。
        // 因此**不断言条目总数**（写死总数会在演示语料增长后无故变红——2026-09-20 实际发生），
        // 只断言"夹具的 4 条已发布片段都在索引里"，并把当时的语料状态打印留痕。
        System.out.println("[EVAL-CORPUS] indexEntries=" + run.indexedEntries()
                + ", eligibleChunksByScope=" + eligibleChunksByScope());
        assertThat(run.indexedEntries()).isGreaterThanOrEqualTo(4);
        assertThat(indexedChunkIds(PROFILE)).contains(
                RetrievalGoldenSet.CHUNK_CREDIT_LIMIT,
                RetrievalGoldenSet.CHUNK_NO_DEFERRAL,
                RetrievalGoldenSet.CHUNK_WITHDRAWAL_WINDOW,
                RetrievalGoldenSet.CHUNK_NO_SELECTION);
        // 内部资料在**索引侧**就应被资格过滤挡住（索引与召回共用同一份判定）。
        assertThat(indexedChunkIds(PROFILE)).doesNotContain(RetrievalGoldenSet.CHUNK_INTERNAL_DECOY);

        // 内部诱饵另行强制入索引（用真实向量）：评测要证明"检索侧自己会挡掉"，
        // 而不是靠"它压根没被索引"——后者无法证明召回侧的资格过滤真的生效。
        appendInternalDecoyWithRealEmbedding(embeddingClient);

        // ③ 评测：词法路 + 真实向量路，RRF 融合；与首份基线同 K、同分母、同数据集版本。
        HybridRetrievalService hybrid = new HybridRetrievalService(new HybridCandidateFuser());
        JdbcLexicalCandidateRetriever lexical = new JdbcLexicalCandidateRetriever(
                dataSource, filter, PROFILE, SCOPE);
        JdbcVectorCandidateRetriever vector = new JdbcVectorCandidateRetriever(
                dataSource, filter, PROFILE, SCOPE, embeddingClient);

        List<Double> recalls = new ArrayList<>();
        List<Double> rrs = new ArrayList<>();
        List<Double> citationAccuracies = new ArrayList<>();
        boolean lexicalAvailable = true;
        boolean vectorAvailable = true;

        for (RetrievalGoldenSet.Case goldenCase : RetrievalGoldenSet.cases()) {
            RetrievalOutcome outcome = hybrid.retrieve(goldenCase.query(), K, lexical, vector);
            List<UUID> ranked = outcome.chunks().stream().map(chunk -> chunk.chunkId()).toList();

            // 逐题记录两路各自与融合后的候选条数：引用准确率的分母是"实际返回的候选数"，
            // 首份基线词法路常常凑不满 K 条（分母小、指标偏高），第二轮向量路补满 K 条（分母变大）。
            // 不记录分母就会把"分母变化"误读成"质量退化"，因此这里把分母一并留痕。
            System.out.println("[EVAL-DETAIL] q=" + goldenCase.query()
                    + ", lexicalCandidates=" + lexical.retrieve(goldenCase.query(), K).size()
                    + ", vectorCandidates=" + vector.retrieve(goldenCase.query(), K).size()
                    + ", rankedCandidates=" + ranked.size()
                    + ", relevant=" + goldenCase.relevantChunkIds().size());

            assertThat(ranked)
                    .as("内部资料在真实向量路下同样不可召回：" + goldenCase.query())
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
                // 本轮只评测检索，生成侧未参与（真实模型 profile 已可用于冒烟，但不在本报告范围内）。
                "未参与（本轮只评测检索，生成侧未调用模型）",
                GroundedAnswerComposer.promptVersion(),
                CHUNKING_VERSION);
        System.out.println("[EVAL] " + report.render());

        // 口径一致性：与首份基线必须逐项可比，否则数字差异无法归因。
        assertThat(report.datasetVersion()).isEqualTo(RetrievalGoldenSet.VERSION);
        assertThat(report.k()).isEqualTo(3);
        assertThat(report.caseCount()).isEqualTo(4);
        // 本轮的可变性就在这里：两路都可用（首份基线的向量路为不可用）。
        assertThat(report.vectorAvailable()).isTrue();
        assertThat(report.lexicalAvailable()).isTrue();
        // 只做"没有退化成空结果"的下界守卫；具体数值记入账本，不在这里断言成"好看的数字"。
        assertThat(report.recallAtK()).isGreaterThan(0.0);

        // ④ 原子切换 + 回滚窗口：评测通过后才允许上线；旧 profile 退役但索引保留，可回滚。
        EmbeddingProfileSwitcher switcher = new EmbeddingProfileSwitcher(profileStore);
        switcher.activate(PROFILE);
        assertThat(profileStore.findActive()).map(EmbeddingProfile::profileId).contains(PROFILE);
        assertThat(activeCount()).isOne();

        if (foreignActive != null) {
            assertThat(profileStore.find(foreignActive.profileId())).map(EmbeddingProfile::status)
                    .contains(ProfileStatus.RETIRED);
            // 回滚窗口的实质：退役 profile 的索引条目仍在，回滚即可用（不必重新花钱重建）。
            switcher.rollback(foreignActive.profileId());
            assertThat(profileStore.find(foreignActive.profileId())).map(EmbeddingProfile::status)
                    .contains(ProfileStatus.ACTIVE);
            assertThat(profileStore.find(PROFILE)).map(EmbeddingProfile::status)
                    .contains(ProfileStatus.RETIRED);
            switcher.activate(PROFILE);
            System.out.println("[EVAL-SWITCH] 原子切换与回滚均验证："
                    + foreignActive.profileId() + " ⇄ " + PROFILE + "（现役=" + PROFILE + "）");
        } else {
            // 本机没有在役 profile 时，这是"首个 profile"路径：没有回滚目标可验，如实说明。
            System.out.println("[EVAL-SWITCH] 本机原无 ACTIVE profile：本次为首次启用（"
                    + PROFILE + "），无回滚目标可验");
        }
        assertThat(activeCount()).isOne();
    }

    private EmbeddingClient realEmbeddingClient() {
        RagProviderProfile profile = new RagProviderProfile(
                PROVIDER, BASE_URL, "DASHSCOPE_API_KEY", "qwen-plus", EMBEDDING_MODEL,
                DIMENSIONS, Duration.ofSeconds(30), PROFILE);
        return SpringAiEmbeddingModelClientFactory.create(profile, ApiKeyResolver.systemEnvironment());
    }

    private void appendInternalDecoyWithRealEmbedding(EmbeddingClient embeddingClient) {
        UUID runId = indexRepository.startRun(PROFILE, CHUNKING_VERSION);
        String content = RetrievalGoldenSet.CONTENT_INTERNAL_DECOY;
        indexRepository.appendEntry(runId, PROFILE, new IndexableChunk(
                        RetrievalGoldenSet.CHUNK_INTERNAL_DECOY, content, checksum(content),
                        "内部细则", new SourceEligibilitySnapshot(
                        RetrievalGoldenSet.INTERNAL_SOURCE_ID, "PUBLISHED", "INTERNAL_ONLY",
                        true, false, SCOPE, null, null)),
                CHUNKING_VERSION, embeddingClient.embed(content));
        indexRepository.completeRun(runId, 1);
    }

    private long activeCount() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM embedding_profile WHERE status = 'ACTIVE'");
             var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("无法统计 ACTIVE profile", exception);
        }
    }

    /** 某 profile 下已写入的索引条目对应的片段标识（用于"夹具是否都进了索引"的形状断言）。 */
    private java.util.Set<UUID> indexedChunkIds(String profileId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT chunk_id FROM retrieval_index_entry WHERE profile_id = ?")) {
            statement.setString(1, profileId);
            try (var result = statement.executeQuery()) {
                java.util.Set<UUID> ids = new java.util.HashSet<>();
                while (result.next()) {
                    ids.add(result.getObject("chunk_id", UUID.class));
                }
                return ids;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取索引条目", exception);
        }
    }

    /**
     * 当前库里"已发布 + 学生可见 + 权利已确认"的片段数（按知识范围分组）。
     *
     * <p>这是本评测的**语料指纹**：索引任务按整库语料建索引，演示语料增长会改变候选池，
     * 不记录它就说不清两份基线之间的差异是"代码变了"还是"语料变了"。</p>
     */
    private String eligibleChunksByScope() {
        String sql = """
                SELECT source.knowledge_scope, COUNT(chunk.id)
                FROM knowledge_chunk chunk
                JOIN knowledge_source source ON source.id = chunk.source_id
                WHERE source.publication_status = 'PUBLISHED'
                  AND source.visibility_scope = 'STUDENT_VISIBLE'
                  AND source.sharing_rights_confirmed
                GROUP BY source.knowledge_scope ORDER BY source.knowledge_scope
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            List<String> entries = new ArrayList<>();
            while (result.next()) {
                entries.add(result.getString(1) + "=" + result.getInt(2));
            }
            return String.join(",", entries);
        } catch (SQLException exception) {
            throw new IllegalStateException("无法统计语料状态", exception);
        }
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
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
}
