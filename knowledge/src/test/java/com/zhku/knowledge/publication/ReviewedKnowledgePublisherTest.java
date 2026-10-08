package com.zhku.knowledge.publication;

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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * 已审核资料发布器的真实数据库契约测试。
 *
 * <p>使用独立测试来源 ID，避免验证过程改动已经人工审核的实际资料。</p>
 */
class ReviewedKnowledgePublisherTest {

    private static final String REPLACEMENT_TEST_SCOPE =
            "TASK3_REPLACEMENT_TEST";

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user",
            "zhkuagent"
    );
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();
    private static final UUID TEST_SOURCE_ID = UUID.fromString(
            "a252f017-2de6-4d70-92e8-f4f775bdd532"
    );
    private static final UUID REPLACEMENT_SOURCE_ID = UUID.fromString(
            "f9744fd7-b8c2-4915-93e5-244d6c513b3c"
    );

    private ReviewedKnowledgePublisher publisher;

    @BeforeEach
    void setUp() throws SQLException {
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        deleteTestSource();
        publisher = new ReviewedKnowledgePublisher(dataSource());
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteTestSource();
    }

    @Test
    void shouldRejectUnreviewedPublicationBeforeWritingAnyKnowledge() throws SQLException {
        ReviewedKnowledgePublication publication = publication(
                "待审核",
                List.of(chunk(7600, "每学期公共选修课最高不得超过 4 个学分。"))
        );

        assertThatThrownBy(() -> publisher.publish(publication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审核通过");

        assertThat(countStoredChunks()).isZero();
    }

    @Test
    void shouldPublishApprovedChunkAndReturnTraceableKeywordResult() {
        ReviewedKnowledgePublication publication = publication(
                "审核通过",
                List.of(chunk(7600, "每学期公共选修课最高不得超过 4 个学分。"))
        );

        publisher.publish(publication);

        assertThat(publisher.searchPublished("4 个学分"))
                .extracting(
                        PublishedKnowledgeChunk::sourceId,
                        PublishedKnowledgeChunk::sourceTitle,
                        PublishedKnowledgeChunk::sourceLocator,
                        PublishedKnowledgeChunk::pageNumber,
                        PublishedKnowledgeChunk::content
                )
                .containsExactly(tuple(
                        TEST_SOURCE_ID,
                        "测试学生手册",
                        "PDF 第 77 页，第 10 条",
                        77,
                        "每学期公共选修课最高不得超过 4 个学分。"
                ));
    }

    @Test
    void shouldExcludeArchivedSourceFromKeywordResult() throws SQLException {
        publisher.publish(publication(
                "审核通过",
                List.of(chunk(7600, "每学期公共选修课最高不得超过 4 个学分。"))
        ));
        archiveTestSource();

        assertThat(publisher.searchPublished("4 个学分")).isEmpty();
    }

    @Test
    void shouldExcludePublishedPublicationWithoutStudentVisibilityAndSharingConfirmation() {
        publisher.publish(publication(
                "审核通过",
                List.of(chunk(7600, "每学期公共选修课最高不得超过 4 个学分。"))
        ));

        assertThat(publisher.searchStudentVisiblePublished("4 个学分"))
                .as("仅本地验证的资料不能被未来学生接口检索")
                .isEmpty();
    }

    @Test
    void shouldExcludeDraftSourceFromStudentVisibleSearch() throws SQLException {
        publisher.publish(new ReviewedKnowledgePublication(
                TEST_SOURCE_ID,
                "待发布公开规则",
                "学生手册",
                "测试版",
                null,
                "全日制普通本科生",
                "已审核且确认可向学生公开。",
                true,
                true,
                "审核通过",
                Instant.parse("2026-09-10T00:00:00Z"),
                List.of(chunk(7600, "待发布公开规则内容"))
        ));
        markTestSourceAsDraft();

        assertThat(publisher.searchStudentVisiblePublished("待发布公开规则内容"))
                .isEmpty();
    }

    @Test
    void shouldArchiveSameScopeWhenPublishingReplacement() {
        publisher.publishReplacingScope(
                publication(TEST_SOURCE_ID, "旧版规则", "旧版替换验证内容"),
                "STUDENT_HANDBOOK",
                REPLACEMENT_TEST_SCOPE
        );
        publisher.publishReplacingScope(
                publication(REPLACEMENT_SOURCE_ID, "新版规则", "新版替换验证内容"),
                "STUDENT_HANDBOOK",
                REPLACEMENT_TEST_SCOPE
        );

        assertThat(publisher.searchPublished("旧版替换验证内容")).isEmpty();
        assertThat(publisher.searchPublished("新版替换验证内容"))
                .extracting(PublishedKnowledgeChunk::sourceId)
                .containsExactly(REPLACEMENT_SOURCE_ID);
    }

    @Test
    void shouldExposeOnlyNewStudentVisibleVersionAfterScopeReplacement() {
        publisher.publishReplacingScope(
                publicPublication(
                        TEST_SOURCE_ID,
                        "旧版公开重修规则",
                        "旧版公开重修规则内容"
                ),
                "STUDENT_HANDBOOK",
                REPLACEMENT_TEST_SCOPE
        );

        publisher.publishReplacingScope(
                publicPublication(
                        REPLACEMENT_SOURCE_ID,
                        "新版公开重修规则",
                        "新版公开重修规则内容"
                ),
                "STUDENT_HANDBOOK",
                REPLACEMENT_TEST_SCOPE
        );

        assertThat(publisher.searchStudentVisiblePublished("旧版公开重修规则内容"))
                .isEmpty();
        assertThat(publisher.searchStudentVisiblePublished("新版公开重修规则内容"))
                .extracting(PublishedKnowledgeChunk::sourceId)
                .containsExactly(REPLACEMENT_SOURCE_ID);
    }

    @Test
    void shouldReturnReviewedUserUploadAfterSharingRightsConfirmed() {
        publisher.publish(new ReviewedKnowledgePublication(
                TEST_SOURCE_ID,
                "学生上传的课程资料",
                "用户上传资料",
                "测试版",
                null,
                "全体学生可见",
                "已经审核课程内容和分享权限。",
                true,
                true,
                "审核通过",
                Instant.parse("2026-09-10T00:00:00Z"),
                List.of(chunk(7600, "每学期公共选修课最高不得超过 4 个学分。"))
        ));

        assertThat(publisher.searchStudentVisiblePublished("4 个学分"))
                .extracting(PublishedKnowledgeChunk::sourceTitle)
                .containsExactly("学生上传的课程资料");
    }

    private ReviewedKnowledgePublication publication(
            String reviewStatus,
            List<ReviewedKnowledgeChunk> chunks
    ) {
        return new ReviewedKnowledgePublication(
                TEST_SOURCE_ID,
                "测试学生手册",
                "学生手册",
                "测试版",
                null,
                "本地验证",
                "仅供测试发布链路。",
                false,
                false,
                reviewStatus,
                Instant.parse("2026-09-10T00:00:00Z"),
                chunks
        );
    }

    private ReviewedKnowledgePublication publication(
            UUID sourceId,
            String title,
            String content
    ) {
        return new ReviewedKnowledgePublication(
                sourceId, title, "学生手册", "测试版", null, "本地验证",
                "仅供测试发布链路。", false, false, "审核通过",
                Instant.parse("2026-09-12T00:00:00Z"),
                List.of(new ReviewedKnowledgeChunk(
                        0, "范围替换", "PDF 第 1 页", 1, content
                ))
        );
    }

    private ReviewedKnowledgePublication publicPublication(
            UUID sourceId,
            String title,
            String content
    ) {
        return new ReviewedKnowledgePublication(
                sourceId, title, "学生手册", "测试版", null, "全日制普通本科生",
                "已审核且确认可向学生公开。", true, true, "审核通过",
                Instant.parse("2026-09-12T00:00:00Z"),
                List.of(new ReviewedKnowledgeChunk(
                        0, "范围替换", "PDF 第 77 页", 77, content
                ))
        );
    }

    private ReviewedKnowledgeChunk chunk(int chunkIndex, String content) {
        return new ReviewedKnowledgeChunk(
                chunkIndex,
                "选课依据与学分上限",
                "PDF 第 77 页，第 10 条",
                77,
                content
        );
    }

    private DataSource dataSource() {
        return new DriverManagerDataSource(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        );
    }

    private int countStoredChunks() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        ); PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM knowledge_chunk
                WHERE source_id = ?
                """)) {
            statement.setObject(1, TEST_SOURCE_ID);
            statement.executeQuery().next();
            return statement.getResultSet().getInt(1);
        }
    }

    private void archiveTestSource() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        ); PreparedStatement statement = connection.prepareStatement("""
                UPDATE knowledge_source
                SET publication_status = 'ARCHIVED'
                WHERE id = ?
                """)) {
            statement.setObject(1, TEST_SOURCE_ID);
            statement.executeUpdate();
        }
    }

    private void markTestSourceAsDraft() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        ); PreparedStatement statement = connection.prepareStatement("""
                UPDATE knowledge_source
                SET publication_status = 'DRAFT',
                    visibility_scope = 'INTERNAL_ONLY'
                WHERE id = ?
                """)) {
            statement.setObject(1, TEST_SOURCE_ID);
            statement.executeUpdate();
        }
    }

    private void deleteTestSource() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        ); PreparedStatement deleteChunks = connection.prepareStatement("""
                DELETE FROM knowledge_chunk WHERE source_id IN (?, ?)
                """ ); PreparedStatement deleteSource = connection.prepareStatement("""
                DELETE FROM knowledge_source WHERE id IN (?, ?)
                """)) {
            deleteChunks.setObject(1, TEST_SOURCE_ID);
            deleteChunks.setObject(2, REPLACEMENT_SOURCE_ID);
            deleteChunks.executeUpdate();
            deleteSource.setObject(1, TEST_SOURCE_ID);
            deleteSource.setObject(2, REPLACEMENT_SOURCE_ID);
            deleteSource.executeUpdate();
        }
    }

}
