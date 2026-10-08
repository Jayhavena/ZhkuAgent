package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.ZhkuFlywayTestSupport;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.datasource.AbstractDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 词法/向量候选检索的资格与排名集成测试（真实 PostgreSQL + pgvector）。
 *
 * <p>两路检索共享同一份资格口径：未发布、内部、权利未确认的来源无论
 * 词面多匹配、向量多接近都不能进入候选——"不可召回"必须在检索层成立，
 * 不能只靠生成层拒答。</p>
 */
class JdbcCandidateRetrieverTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent");
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();

    private static final UUID ELIGIBLE_SOURCE = UUID.fromString("6b111111-1111-4111-8111-111111111111");
    private static final UUID INTERNAL_SOURCE = UUID.fromString("6b222222-2222-4222-8222-222222222222");
    private static final UUID ELIGIBLE_CHUNK_A = UUID.fromString("6bc11111-1111-4111-8111-111111111111");
    private static final UUID ELIGIBLE_CHUNK_B = UUID.fromString("6bc22222-2222-4222-8222-222222222222");
    private static final UUID INTERNAL_CHUNK = UUID.fromString("6bc33333-3333-4333-8333-333333333333");
    private static final String PROFILE = "test-retrieve-profile";
    private static final String CHUNKING_VERSION = "fact-aware-v1";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    private DataSource dataSource;
    private JdbcRetrievalIndexRepository indexRepository;
    private RetrievalEligibilityFilter filter;
    private UUID runId;

    @BeforeEach
    void setUp() throws SQLException {
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        dataSource = new DriverManagerDataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        filter = new RetrievalEligibilityFilter(CLOCK);
        deleteFixtures();
        insertSource(ELIGIBLE_SOURCE, "PUBLISHED", "STUDENT_VISIBLE", true, "学生手册 2026 版");
        insertSource(INTERNAL_SOURCE, "PUBLISHED", "INTERNAL_ONLY", true, "内部细则");
        insertChunk(ELIGIBLE_CHUNK_A, ELIGIBLE_SOURCE, 0,
                "第一条 每学期公共选修课最高修读 4 学分。");
        insertChunk(ELIGIBLE_CHUNK_B, ELIGIBLE_SOURCE, 1,
                "第二条 公共选修课不办理缓考，不及格者应重修。");
        insertChunk(INTERNAL_CHUNK, INTERNAL_SOURCE, 0,
                "内部条款 公共选修课缓考特殊处理办法。");

        indexRepository = new JdbcRetrievalIndexRepository(dataSource);
        indexRepository.registerProfile(new EmbeddingProfile(
                PROFILE, "test-provider", "test-model", 3, ProfileStatus.BUILDING));
        runId = indexRepository.startRun(PROFILE, CHUNKING_VERSION);
        indexRepository.appendEntry(runId, PROFILE, new IndexableChunk(
                ELIGIBLE_CHUNK_A, "第一条 每学期公共选修课最高修读 4 学分。", checksum("第一条 每学期公共选修课最高修读 4 学分。"),
                "学生手册 2026 版", snapshot(ELIGIBLE_SOURCE, "PUBLISHED", "STUDENT_VISIBLE", true)),
                CHUNKING_VERSION, new float[] { 1f, 0f, 0f });
        indexRepository.appendEntry(runId, PROFILE, new IndexableChunk(
                ELIGIBLE_CHUNK_B, "第二条 公共选修课不办理缓考，不及格者应重修。", checksum("第二条 公共选修课不办理缓考，不及格者应重修。"),
                "学生手册 2026 版", snapshot(ELIGIBLE_SOURCE, "PUBLISHED", "STUDENT_VISIBLE", true)),
                CHUNKING_VERSION, new float[] { 0f, 1f, 0f });
        // 内部资料也写入索引，验证检索侧必须自行挡掉——不能假设"只有合格资料才会被索引"。
        indexRepository.appendEntry(runId, PROFILE, new IndexableChunk(
                INTERNAL_CHUNK, "内部条款 公共选修课缓考特殊处理办法。", checksum("内部条款 公共选修课缓考特殊处理办法。"),
                "内部细则", snapshot(INTERNAL_SOURCE, "PUBLISHED", "INTERNAL_ONLY", true)),
                CHUNKING_VERSION, new float[] { 0f, 0f, 1f });
        indexRepository.completeRun(runId, 3);
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteFixtures();
    }

    @Test
    void shouldRankLexicalCandidatesByMatchedTermsAndExcludeIneligible() {
        activateProfile();
        JdbcLexicalCandidateRetriever retriever = new JdbcLexicalCandidateRetriever(dataSource, filter, PROFILE,
                "PUBLIC_ELECTIVE");

        List<RetrievedChunk> chunks = retriever.retrieve("公共选修课 重修 学分", 10);

        assertThat(chunks).extracting(RetrievedChunk::chunkId)
                .containsExactly(ELIGIBLE_CHUNK_A, ELIGIBLE_CHUNK_B)
                .doesNotContain(INTERNAL_CHUNK);
        assertThat(chunks.getFirst().content()).contains("最高修读 4 学分");
    }

    @Test
    void shouldReturnNoLexicalCandidateWhenQueryHasNoUsableTerm() {
        activateProfile();
        JdbcLexicalCandidateRetriever retriever = new JdbcLexicalCandidateRetriever(dataSource, filter, PROFILE,
                "PUBLIC_ELECTIVE");

        assertThat(retriever.retrieve("？。！", 10)).isEmpty();
    }

    @Test
    void shouldRankVectorCandidatesByCosineDistanceAndExcludeIneligible() {
        activateProfile();
        JdbcVectorCandidateRetriever retriever = new JdbcVectorCandidateRetriever(
                dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", new FixedEmbeddingClient(3));

        List<RetrievedChunk> chunks = retriever.retrieve("缓考能不能办理", 10);

        // 固定客户端返回 [0,1,0]，应最接近 ELIGIBLE_CHUNK_B；内部资料即便更近也须被过滤。
        assertThat(chunks.getFirst().chunkId()).isEqualTo(ELIGIBLE_CHUNK_B);
        assertThat(chunks).extracting(RetrievedChunk::chunkId).doesNotContain(INTERNAL_CHUNK);
    }

    @Test
    void shouldRespectLimitOnBothPaths() {
        activateProfile();
        JdbcLexicalCandidateRetriever lexical = new JdbcLexicalCandidateRetriever(dataSource, filter, PROFILE,
                "PUBLIC_ELECTIVE");
        JdbcVectorCandidateRetriever vector = new JdbcVectorCandidateRetriever(
                dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", new FixedEmbeddingClient(3));

        assertThat(lexical.retrieve("公共选修课", 1)).hasSize(1);
        assertThat(vector.retrieve("公共选修课", 1)).hasSize(1);
    }

    @Test
    void buildingProfileMustNotServeOnlineOrCallEmbedding() {
        assertOnlineUnavailableWithoutEmbedding(PROFILE);
    }

    @Test
    void retiredProfileMustNotServeOnlineOrCallEmbedding() throws SQLException {
        activateProfile();
        update("UPDATE embedding_profile SET status='RETIRED' WHERE profile_id=?", PROFILE);
        assertOnlineUnavailableWithoutEmbedding(PROFILE);
    }

    @Test
    void missingProfileMustNotCallEmbedding() {
        assertOnlineUnavailableWithoutEmbedding("missing-r4-profile");
    }

    @Test
    void clientDimensionMismatchMustNotCallEmbedding() {
        activateProfile();
        AtomicInteger calls = new AtomicInteger();
        var retriever = new JdbcVectorCandidateRetriever(dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", countingClient(calls, 4, new float[]{0, 1, 0, 0}));
        assertThatThrownBy(() -> retriever.retrieve("公共选修课", 1)).isInstanceOf(RetrieverUnavailableException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void actualVectorLengthMismatchMustFailThisPath() {
        activateProfile();
        AtomicInteger calls = new AtomicInteger();
        var retriever = new JdbcVectorCandidateRetriever(dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", countingClient(calls, 3, new float[]{0, 1}));
        assertThatThrownBy(() -> retriever.retrieve("公共选修课", 1)).isInstanceOf(RetrieverUnavailableException.class);
        assertThat(calls).hasValue(1);
        assertThat(lexical(dataSource, "PUBLIC_ELECTIVE").retrieve("公共选修课", 1)).hasSize(1);
    }

    @Test
    void draftSourceMustBeExcludedOnBothPaths() throws SQLException {
        activateProfile();
        update("UPDATE knowledge_source SET publication_status='DRAFT', visibility_scope='INTERNAL_ONLY' WHERE id=?", ELIGIBLE_SOURCE);
        assertBothEmpty("PUBLIC_ELECTIVE");
    }

    @Test
    void internalSourceMustBeExcludedOnBothPaths() throws SQLException {
        activateProfile();
        update("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY' WHERE id=?", ELIGIBLE_SOURCE);
        assertBothEmpty("PUBLIC_ELECTIVE");
    }

    @Test
    void unconfirmedSharingMustBeExcludedOnBothPaths() throws SQLException {
        activateProfile();
        // 当前schema禁止未授权或未发布资料仍学生可见；先验证库约束，再测试合法的内部未授权状态。
        assertThatThrownBy(() -> update("UPDATE knowledge_source SET sharing_rights_confirmed=false WHERE id=?", ELIGIBLE_SOURCE))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_knowledge_source_student_visibility");
        update("UPDATE knowledge_source SET sharing_rights_confirmed=false, visibility_scope='INTERNAL_ONLY' WHERE id=?", ELIGIBLE_SOURCE);
        assertBothEmpty("PUBLIC_ELECTIVE");
    }

    @Test
    void scopeMismatchMustBeExcludedButNullScopeIsSupported() {
        activateProfile();
        assertBothEmpty("OTHER_SCOPE");
        assertThat(lexical(dataSource, null).retrieve("公共选修课", 10)).hasSize(2);
        assertThat(vector(dataSource, null).retrieve("公共选修课", 10)).hasSize(2);
    }

    @Test
    void sourceVersionDriftMustExcludeStaleEntries() throws SQLException {
        activateProfile();
        update("UPDATE knowledge_source SET source_version='changed-version' WHERE id=?", ELIGIBLE_SOURCE);
        assertBothEmpty("PUBLIC_ELECTIVE");
    }

    @Test
    void chunkChecksumDriftMustExcludeOnlyStaleChunk() throws SQLException {
        activateProfile();
        update("UPDATE knowledge_chunk SET content_checksum=? WHERE id=?", "f".repeat(64), ELIGIBLE_CHUNK_A);
        assertOnlyBOnBothPaths();
    }

    @Test
    void indexChecksumDriftMustExcludeOnlyStaleChunk() throws SQLException {
        activateProfile();
        update("UPDATE retrieval_index_entry SET content_checksum=? WHERE chunk_id=? AND profile_id=?", "f".repeat(64), ELIGIBLE_CHUNK_A, PROFILE);
        assertOnlyBOnBothPaths();
    }

    @Test
    void chunkingVersionDriftMustExcludeOnlyStaleChunk() throws SQLException {
        activateProfile();
        update("UPDATE retrieval_index_entry SET chunking_version='changed-version' WHERE chunk_id=? AND profile_id=?", ELIGIBLE_CHUNK_A, PROFILE);
        assertOnlyBOnBothPaths();
    }

    @Test
    void retirementDuringEmbeddingMustPreventCandidateRead() {
        activateProfile();
        EmbeddingClient client = new EmbeddingClient() {
            public int dimensions() { return 3; }
            public float[] embed(String text) {
                try { update("UPDATE embedding_profile SET status='RETIRED' WHERE profile_id=?", PROFILE); }
                catch (SQLException exception) { throw new AssertionError(exception); }
                return new float[]{0, 1, 0};
            }
        };
        assertThat(new JdbcVectorCandidateRetriever(dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", client)
                .retrieve("公共选修课", 1)).isEmpty();
    }

    @Test
    void unfinishedFailedOrRolledBackRunMustBeExcluded() throws SQLException {
        activateProfile();
        for (String status : List.of("RUNNING", "FAILED", "ROLLED_BACK")) {
            update("UPDATE retrieval_index_run SET status=? WHERE id=?", status, runId);
            assertBothEmpty("PUBLIC_ELECTIVE");
        }
    }

    @Test
    void runProfileMismatchMustNotMakeEntriesReadable() throws SQLException {
        activateProfile();
        indexRepository.registerProfile(new EmbeddingProfile("r4-foreign-profile", "test-provider", "test-model", 3, ProfileStatus.BUILDING));
        try {
            update("UPDATE retrieval_index_run SET profile_id='r4-foreign-profile' WHERE id=?", runId);
            assertBothEmpty("PUBLIC_ELECTIVE");
        } finally {
            update("UPDATE retrieval_index_run SET profile_id=? WHERE id=?", PROFILE, runId);
            update("DELETE FROM embedding_profile WHERE profile_id='r4-foreign-profile'");
        }
    }

    @Test
    void ineligibleNearestMustNotDisplaceEligibleResultAndJdbcReadIsBounded() throws Exception {
        activateProfile();
        update("UPDATE retrieval_index_entry SET embedding=CAST(? AS vector) WHERE chunk_id=? AND profile_id=?", "[0,1,0]", INTERNAL_CHUNK, PROFILE);
        update("UPDATE retrieval_index_entry SET embedding=CAST(? AS vector) WHERE chunk_id=? AND profile_id=?", "[1,1,0]", ELIGIBLE_CHUNK_B, PROFILE);
        var observation = new QueryObservation();
        DataSource tracked = trackedDataSource(observation);
        assertThat(vector(tracked, "PUBLIC_ELECTIVE").retrieve("公共选修课", 1))
                .extracting(RetrievedChunk::chunkId).containsExactly(ELIGIBLE_CHUNK_B);
        // 测真实ResultSet产出的行数，防止“结果一条、JDBC仍物化全库”被误报为有界读取。
        saveExplain(observation);
        assertThat(observation.rowsRead).hasValue(1);
    }

    @Test
    void equalCosineDistanceKeepsStableChunkOrder() throws SQLException {
        activateProfile();
        update("UPDATE retrieval_index_entry SET embedding=CAST(? AS vector) WHERE profile_id=?", "[0,1,0]", PROFILE);
        assertThat(vector(dataSource, "PUBLIC_ELECTIVE").retrieve("公共选修课", 2))
                .extracting(RetrievedChunk::chunkId).containsExactly(ELIGIBLE_CHUNK_A, ELIGIBLE_CHUNK_B);
    }

    @Test
    void explicitOfflineEvaluationCanReadBuildingWithoutActivatingIt() {
        assertThat(JdbcLexicalCandidateRetriever.forOfflineEvaluation(dataSource, filter, PROFILE, "PUBLIC_ELECTIVE")
                .retrieve("公共选修课", 10)).hasSize(2);
        assertThat(JdbcVectorCandidateRetriever.forOfflineEvaluation(dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", new FixedEmbeddingClient(3))
                .retrieve("公共选修课", 10)).hasSize(2);
        assertThat(indexRepository.findProfile(PROFILE)).get().extracting(EmbeddingProfile::status).isEqualTo(ProfileStatus.BUILDING);
        assertOnlineUnavailableWithoutEmbedding(PROFILE);
    }

    @Test
    void offlineEvaluationStillRejectsRetiredAndIneligibleSource() throws SQLException {
        var lexical = JdbcLexicalCandidateRetriever.forOfflineEvaluation(dataSource, filter, PROFILE, "OTHER_SCOPE");
        assertThat(lexical.retrieve("公共选修课", 10)).isEmpty();
        AtomicInteger calls = new AtomicInteger();
        var vector = JdbcVectorCandidateRetriever.forOfflineEvaluation(dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", countingClient(calls, 3, new float[]{0, 1, 0}));
        update("UPDATE embedding_profile SET status='RETIRED' WHERE profile_id=?", PROFILE);
        assertThatThrownBy(() -> vector.retrieve("公共选修课", 1)).isInstanceOf(RetrieverUnavailableException.class);
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> lexical.retrieve("公共选修课", 1)).isInstanceOf(RetrieverUnavailableException.class);
    }

    private void activateProfile() {
        new EmbeddingProfileSwitcher(new JdbcEmbeddingProfileStore(dataSource)).activate(PROFILE);
    }

    private JdbcLexicalCandidateRetriever lexical(DataSource source, String scope) {
        return new JdbcLexicalCandidateRetriever(source, filter, PROFILE, scope);
    }

    private JdbcVectorCandidateRetriever vector(DataSource source, String scope) {
        return new JdbcVectorCandidateRetriever(source, filter, PROFILE, scope, new FixedEmbeddingClient(3));
    }

    private void assertBothEmpty(String scope) {
        assertThat(lexical(dataSource, scope).retrieve("公共选修课", 10)).isEmpty();
        assertThat(vector(dataSource, scope).retrieve("公共选修课", 10)).isEmpty();
    }

    private void assertOnlyBOnBothPaths() {
        assertThat(lexical(dataSource, "PUBLIC_ELECTIVE").retrieve("公共选修课", 10))
                .extracting(RetrievedChunk::chunkId).containsExactly(ELIGIBLE_CHUNK_B);
        assertThat(vector(dataSource, "PUBLIC_ELECTIVE").retrieve("公共选修课", 10))
                .extracting(RetrievedChunk::chunkId).containsExactly(ELIGIBLE_CHUNK_B);
    }

    private void assertOnlineUnavailableWithoutEmbedding(String profile) {
        AtomicInteger calls = new AtomicInteger();
        var vector = new JdbcVectorCandidateRetriever(dataSource, filter, profile, "PUBLIC_ELECTIVE", countingClient(calls, 3, new float[]{0, 1, 0}));
        assertThatThrownBy(() -> vector.retrieve("公共选修课", 1)).isInstanceOf(RetrieverUnavailableException.class);
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> new JdbcLexicalCandidateRetriever(dataSource, filter, profile, "PUBLIC_ELECTIVE")
                .retrieve("公共选修课", 1)).isInstanceOf(RetrieverUnavailableException.class);
    }

    private static EmbeddingClient countingClient(AtomicInteger calls, int dimensions, float[] value) {
        return new EmbeddingClient() {
            public int dimensions() { return dimensions; }
            public float[] embed(String text) { calls.incrementAndGet(); return value; }
        };
    }

    private void update(String sql, Object... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) { statement.setObject(i + 1, parameters[i]); }
            statement.executeUpdate();
        }
    }

    private DataSource trackedDataSource(QueryObservation observation) {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new java.sql.SQLFeatureNotSupportedException("测试包装仅允许已配置数据源的无参连接");
            }

            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = dataSource.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    Object value = invoke(connection, method, args);
                    if (!method.getName().equals("prepareStatement") || !((String) args[0]).contains("FROM retrieval_index_entry")) { return value; }
                    observation.sql.set((String) args[0]);
                    PreparedStatement statement = (PreparedStatement) value;
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                        if (m.getName().startsWith("set") && a != null && a.length >= 2 && a[0] instanceof Integer index) {
                            observation.parameters.put(index, a[1]);
                        }
                        Object result = invoke(statement, m, a);
                        if (!m.getName().equals("executeQuery")) { return result; }
                        return Proxy.newProxyInstance(java.sql.ResultSet.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class}, (r, rm, ra) -> {
                            Object next = invoke(result, rm, ra);
                            if (rm.getName().equals("next") && Boolean.TRUE.equals(next)) { observation.rowsRead.incrementAndGet(); }
                            return next;
                        });
                    });
                });
            }
        };
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] arguments) throws Throwable {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException exception) { throw exception.getCause(); }
    }

    private void saveExplain(QueryObservation observation) throws Exception {
        String directory = System.getProperty("zhku.rulecheck.evidenceDir");
        if (directory == null) { return; }
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + observation.sql.get())) {
            for (var parameter : observation.parameters.entrySet()) { statement.setObject(parameter.getKey(), parameter.getValue()); }
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                var mapper = new ObjectMapper();
                Files.writeString(Path.of(directory).resolve("r4-vector-query-plan.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                        Map.of("sql", observation.sql.get(), "boundParameters", observation.parameters,
                                "jdbcRowsRead", observation.rowsRead.get(), "fixtureKind", "SYNTHETIC",
                                "explain", mapper.readTree(result.getString(1)))), StandardCharsets.UTF_8);
            }
        }
    }

    private static final class QueryObservation {
        private final AtomicInteger rowsRead = new AtomicInteger();
        private final AtomicReference<String> sql = new AtomicReference<>();
        private final Map<Integer, Object> parameters = new TreeMap<>();
    }

    private static String checksum(String content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
    }

    private static SourceEligibilitySnapshot snapshot(
            UUID sourceId, String status, String visibility, boolean rights) {
        return new SourceEligibilitySnapshot(
                sourceId, status, visibility, rights, false, "PUBLIC_ELECTIVE", null, null);
    }

    private void insertSource(
            UUID sourceId, String status, String visibility, boolean rights, String version) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                     INSERT INTO knowledge_source (
                         id, title, source_type, source_version, publication_status,
                         published_at, content_checksum, visibility_scope, publication_scope,
                         review_notice, sharing_rights_confirmed, document_family, knowledge_scope
                     ) VALUES (?, ?, 'STUDENT_HANDBOOK', ?, ?, ?, ?, ?, '本地验证', '测试资料', ?, 'STUDENT_HANDBOOK', 'PUBLIC_ELECTIVE')
                     """)) {
            statement.setObject(1, sourceId);
            statement.setString(2, "测试来源 " + sourceId);
            statement.setString(3, version);
            statement.setString(4, status);
            statement.setTimestamp(5, java.sql.Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")));
            statement.setString(6, "d".repeat(64));
            statement.setString(7, visibility);
            statement.setBoolean(8, rights);
            statement.executeUpdate();
        }
    }

    private void insertChunk(UUID chunkId, UUID sourceId, int index, String content) throws SQLException {
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
            statement.setString(4, "第 " + (index + 1) + " 条");
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
                    "DELETE FROM retrieval_index_entry WHERE profile_id = '" + PROFILE + "'");
            connection.createStatement().executeUpdate(
                    "DELETE FROM retrieval_index_run WHERE profile_id = '" + PROFILE + "'");
            connection.createStatement().executeUpdate(
                    "DELETE FROM embedding_profile WHERE profile_id = '" + PROFILE + "'");
            connection.createStatement().executeUpdate(
                    "DELETE FROM knowledge_chunk WHERE source_id IN ('"
                            + ELIGIBLE_SOURCE + "','" + INTERNAL_SOURCE + "')");
            connection.createStatement().executeUpdate(
                    "DELETE FROM knowledge_source WHERE id IN ('"
                            + ELIGIBLE_SOURCE + "','" + INTERNAL_SOURCE + "')");
        }
    }

    /** 固定返回 [0,1,0]，使向量排名在测试中完全确定。 */
    private static final class FixedEmbeddingClient implements EmbeddingClient {

        private final int dimensions;

        private FixedEmbeddingClient(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public float[] embed(String text) {
            float[] vector = new float[dimensions];
            if (dimensions > 1) {
                vector[1] = 1f;
            }
            return vector;
        }

        @Override
        public int dimensions() {
            return dimensions;
        }
    }
}
