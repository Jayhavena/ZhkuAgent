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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 词法/向量候选检索的资格与排名集成测试（真实 PostgreSQL + pgvector）。
 *
 * <p>两路检索共享同一份资格口径：未发布、内部、权利未确认的来源无论
 * 词面多匹配、向量多接近都不能进入候选——"不可召回"必须在检索层成立，
 * 不能只靠生成层拒答。</p>
 */
class JdbcCandidateRetrieverTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
    );
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent"
    );
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();

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
        UUID runId = indexRepository.startRun(PROFILE, CHUNKING_VERSION);
        indexRepository.appendEntry(runId, PROFILE, new IndexableChunk(
                ELIGIBLE_CHUNK_A, "第一条 每学期公共选修课最高修读 4 学分。", "a".repeat(64),
                "学生手册 2026 版", snapshot(ELIGIBLE_SOURCE, "PUBLISHED", "STUDENT_VISIBLE", true)),
                CHUNKING_VERSION, new float[]{1f, 0f, 0f});
        indexRepository.appendEntry(runId, PROFILE, new IndexableChunk(
                ELIGIBLE_CHUNK_B, "第二条 公共选修课不办理缓考，不及格者应重修。", "b".repeat(64),
                "学生手册 2026 版", snapshot(ELIGIBLE_SOURCE, "PUBLISHED", "STUDENT_VISIBLE", true)),
                CHUNKING_VERSION, new float[]{0f, 1f, 0f});
        // 内部资料也写入索引，验证检索侧必须自行挡掉——不能假设"只有合格资料才会被索引"。
        indexRepository.appendEntry(runId, PROFILE, new IndexableChunk(
                INTERNAL_CHUNK, "内部条款 公共选修课缓考特殊处理办法。", "c".repeat(64),
                "内部细则", snapshot(INTERNAL_SOURCE, "PUBLISHED", "INTERNAL_ONLY", true)),
                CHUNKING_VERSION, new float[]{0f, 0f, 1f});
        indexRepository.completeRun(runId, 3);
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteFixtures();
    }

    @Test
    void shouldRankLexicalCandidatesByMatchedTermsAndExcludeIneligible() {
        JdbcLexicalCandidateRetriever retriever =
                new JdbcLexicalCandidateRetriever(dataSource, filter, PROFILE, "PUBLIC_ELECTIVE");

        List<RetrievedChunk> chunks = retriever.retrieve("公共选修课 重修 学分", 10);

        assertThat(chunks).extracting(RetrievedChunk::chunkId)
                .containsExactly(ELIGIBLE_CHUNK_A, ELIGIBLE_CHUNK_B)
                .doesNotContain(INTERNAL_CHUNK);
        assertThat(chunks.getFirst().content()).contains("最高修读 4 学分");
    }

    @Test
    void shouldReturnNoLexicalCandidateWhenQueryHasNoUsableTerm() {
        JdbcLexicalCandidateRetriever retriever =
                new JdbcLexicalCandidateRetriever(dataSource, filter, PROFILE, "PUBLIC_ELECTIVE");

        assertThat(retriever.retrieve("？。！", 10)).isEmpty();
    }

    @Test
    void shouldRankVectorCandidatesByCosineDistanceAndExcludeIneligible() {
        JdbcVectorCandidateRetriever retriever = new JdbcVectorCandidateRetriever(
                dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", new FixedEmbeddingClient(3));

        List<RetrievedChunk> chunks = retriever.retrieve("缓考能不能办理", 10);

        // 固定客户端返回 [0,1,0]，应最接近 ELIGIBLE_CHUNK_B；内部资料即便更近也须被过滤。
        assertThat(chunks.getFirst().chunkId()).isEqualTo(ELIGIBLE_CHUNK_B);
        assertThat(chunks).extracting(RetrievedChunk::chunkId).doesNotContain(INTERNAL_CHUNK);
    }

    @Test
    void shouldRespectLimitOnBothPaths() {
        JdbcLexicalCandidateRetriever lexical =
                new JdbcLexicalCandidateRetriever(dataSource, filter, PROFILE, "PUBLIC_ELECTIVE");
        JdbcVectorCandidateRetriever vector = new JdbcVectorCandidateRetriever(
                dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", new FixedEmbeddingClient(3));

        assertThat(lexical.retrieve("公共选修课", 1)).hasSize(1);
        assertThat(vector.retrieve("公共选修课", 1)).hasSize(1);
    }

    private static SourceEligibilitySnapshot snapshot(
            UUID sourceId, String status, String visibility, boolean rights
    ) {
        return new SourceEligibilitySnapshot(
                sourceId, status, visibility, rights, false, "PUBLIC_ELECTIVE", null, null);
    }

    private void insertSource(
            UUID sourceId, String status, String visibility, boolean rights, String version
    ) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
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
            statement.setString(8, "e".repeat(64));
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
