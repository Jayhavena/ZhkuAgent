package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * 索引任务的准入与审计集成测试（真实 PostgreSQL + Flyway）。
 *
 * <p>只有已发布、学生可见、分享权利确认的来源可被索引；索引条目必须记录
 * 来源版本、切分版本与内容哈希，且维度不符时整轮索引失败——不允许把不同维度的
 * 向量混进同一 profile。</p>
 */
class JdbcRetrievalIndexingTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
    );
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent"
    );
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();

    private static final UUID ELIGIBLE_SOURCE = UUID.fromString("5a111111-1111-4111-8111-111111111111");
    private static final UUID DRAFT_SOURCE = UUID.fromString("5a222222-2222-4222-8222-222222222222");
    private static final UUID INTERNAL_SOURCE = UUID.fromString("5a333333-3333-4333-8333-333333333333");
    private static final UUID ELIGIBLE_CHUNK_1 = UUID.fromString("5ac11111-1111-4111-8111-111111111111");
    private static final UUID ELIGIBLE_CHUNK_2 = UUID.fromString("5ac22222-2222-4222-8222-222222222222");
    private static final UUID DRAFT_CHUNK = UUID.fromString("5ac33333-3333-4333-8333-333333333333");
    private static final UUID INTERNAL_CHUNK = UUID.fromString("5ac44444-4444-4444-8444-444444444444");
    private static final String PROFILE = "test-profile-v9";
    private static final String CHUNKING_VERSION = "fact-aware-v1";

    private DataSource dataSource;
    private JdbcRetrievalIndexRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        Flyway.configure()
                .dataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dataSource = new DriverManagerDataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        deleteFixtures();
        insertSource(ELIGIBLE_SOURCE, "PUBLISHED", "STUDENT_VISIBLE", true, "学生手册 2026 版");
        insertSource(DRAFT_SOURCE, "DRAFT", "INTERNAL_ONLY", false, "手册草稿");
        insertSource(INTERNAL_SOURCE, "PUBLISHED", "INTERNAL_ONLY", true, "内部细则");
        insertChunk(ELIGIBLE_CHUNK_1, ELIGIBLE_SOURCE, 0, "第一条 每学期公共选修课最高修读 4 学分。");
        insertChunk(ELIGIBLE_CHUNK_2, ELIGIBLE_SOURCE, 1, "第二条 公共选修课不办理缓考，不及格者应重修。");
        insertChunk(DRAFT_CHUNK, DRAFT_SOURCE, 0, "草稿条款不应进入索引。");
        insertChunk(INTERNAL_CHUNK, INTERNAL_SOURCE, 0, "内部条款不应进入索引。");
        repository = new JdbcRetrievalIndexRepository(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteFixtures();
    }

    @Test
    void shouldIndexOnlyEligiblePublishedChunks() throws SQLException {
        repository.registerProfile(new EmbeddingProfile(
                PROFILE, "test-provider", "test-model", 3, ProfileStatus.BUILDING));
        RetrievalIndexingService service = new RetrievalIndexingService(
                repository, new FixedEmbeddingClient(3), new RetrievalEligibilityFilter(
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC)));

        IndexRunResult result = service.indexProfile(PROFILE, CHUNKING_VERSION, "PUBLIC_ELECTIVE");

        assertThat(result.indexedEntries()).isEqualTo(2);
        assertThat(runStatus(result.runId())).isEqualTo("COMPLETED");
        assertThat(indexedChunkIds()).containsExactlyInAnyOrder(ELIGIBLE_CHUNK_1, ELIGIBLE_CHUNK_2);
        assertThat(indexedChunkIds()).doesNotContain(DRAFT_CHUNK, INTERNAL_CHUNK);
    }

    @Test
    void shouldRecordSourceVersionChunkingVersionAndChecksum() throws SQLException {
        repository.registerProfile(new EmbeddingProfile(
                PROFILE, "test-provider", "test-model", 3, ProfileStatus.BUILDING));
        RetrievalIndexingService service = new RetrievalIndexingService(
                repository, new FixedEmbeddingClient(3), new RetrievalEligibilityFilter(
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC)));

        service.indexProfile(PROFILE, CHUNKING_VERSION, "PUBLIC_ELECTIVE");

        // 审核追溯要求：每条索引都能反查来源版本、切分版本与内容哈希，向量不悬空。
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT source_version, chunking_version, content_checksum,
                            (embedding IS NOT NULL) AS has_embedding
                     FROM retrieval_index_entry WHERE chunk_id = ?
                     """)) {
            statement.setObject(1, ELIGIBLE_CHUNK_1);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("source_version")).isEqualTo("学生手册 2026 版");
                assertThat(result.getString("chunking_version")).isEqualTo(CHUNKING_VERSION);
                assertThat(result.getString("content_checksum")).hasSize(64);
                assertThat(result.getBoolean("has_embedding")).isTrue();
            }
        }
    }

    @Test
    void shouldFailRunWhenEmbeddingDimensionsMismatchProfile() throws SQLException {
        repository.registerProfile(new EmbeddingProfile(
                PROFILE, "test-provider", "test-model", 3, ProfileStatus.BUILDING));
        RetrievalIndexingService service = new RetrievalIndexingService(
                repository, new FixedEmbeddingClient(4), new RetrievalEligibilityFilter(
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC)));

        // 禁止混用维度：embedding 维度与 profile 声明不符时整轮失败并留痕。
        assertThatIllegalStateException()
                .isThrownBy(() -> service.indexProfile(PROFILE, CHUNKING_VERSION, "PUBLIC_ELECTIVE"));

        assertThat(lastRunStatus()).isEqualTo("FAILED");
        assertThat(indexedChunkIds()).isEmpty();
    }

    private String runStatus(UUID runId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status FROM retrieval_index_run WHERE id = ?")) {
            statement.setObject(1, runId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString("status");
            }
        }
    }

    private String lastRunStatus() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status FROM retrieval_index_run WHERE profile_id = ? "
                             + "ORDER BY started_at DESC, id DESC LIMIT 1")) {
            statement.setString(1, PROFILE);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString("status");
            }
        }
    }

    private List<UUID> indexedChunkIds() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT chunk_id FROM retrieval_index_entry WHERE profile_id = ?")) {
            statement.setString(1, PROFILE);
            try (ResultSet result = statement.executeQuery()) {
                List<UUID> ids = new java.util.ArrayList<>();
                while (result.next()) {
                    ids.add(result.getObject(1, UUID.class));
                }
                return List.copyOf(ids);
            }
        }
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
            statement.setString(6, "a".repeat(64));
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
            statement.setString(8, "b".repeat(64));
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
                            + ELIGIBLE_SOURCE + "','" + DRAFT_SOURCE + "','" + INTERNAL_SOURCE + "')");
            connection.createStatement().executeUpdate(
                    "DELETE FROM knowledge_source WHERE id IN ('"
                            + ELIGIBLE_SOURCE + "','" + DRAFT_SOURCE + "','" + INTERNAL_SOURCE + "')");
        }
    }

    /** 固定维度假 embedding：维度可控，用于验证"维度不符即失败"。 */
    private static final class FixedEmbeddingClient implements EmbeddingClient {

        private final int dimensions;

        private FixedEmbeddingClient(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public float[] embed(String text) {
            float[] vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                vector[i] = text.length() + i;
            }
            return vector;
        }

        @Override
        public int dimensions() {
            return dimensions;
        }
    }
}
