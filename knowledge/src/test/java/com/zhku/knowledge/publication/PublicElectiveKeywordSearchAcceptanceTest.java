package com.zhku.knowledge.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公共选修课程首批关键词检索验收。
 *
 * <p>直接检索已导入的本地数据库，验证测试问题不会只停留在 JSON 文档中。</p>
 */
class PublicElectiveKeywordSearchAcceptanceTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url",
            "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
    );
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user",
            "zhkuagent"
    );
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();

    @ParameterizedTest(name = "{0}")
    @MethodSource("keywordProbes")
    void shouldFindExpectedCitationForEachPublicElectiveProbe(
            String questionId,
            String keyword,
            String expectedSourceLocator
    ) {
        ReviewedKnowledgePublisher publisher = new ReviewedKnowledgePublisher(
                new DriverManagerDataSource(
                        DATABASE_URL,
                        DATABASE_USER,
                        DATABASE_PASSWORD
                )
        );

        assertThat(publisher.searchPublished(keyword))
                .extracting(PublishedKnowledgeChunk::sourceLocator)
                .as("测试问题 %s 应命中已审核出处", questionId)
                .contains(expectedSourceLocator);
    }

    @Test
    void shouldReturnNoResultForUnsupportedScholarshipQuestion() {
        ReviewedKnowledgePublisher publisher = new ReviewedKnowledgePublisher(
                new DriverManagerDataSource(
                        DATABASE_URL,
                        DATABASE_USER,
                        DATABASE_PASSWORD
                )
        );

        assertThat(publisher.searchPublished("奖学金申请截止"))
                .as("当前仅发布公共选修课程资料，不能假装回答奖学金问题")
                .isEmpty();
    }

    @Test
    void shouldExposeApprovedCreditLimitEvidenceToStudentVisibleSearch() {
        ReviewedKnowledgePublisher publisher = new ReviewedKnowledgePublisher(
                new DriverManagerDataSource(
                        DATABASE_URL,
                        DATABASE_USER,
                        DATABASE_PASSWORD
                )
        );

        assertThat(publisher.searchStudentVisiblePublished("每学期公共选修课最高"))
                .as("已审核并明确允许学生可见的学分上限规则应被学生接口检索")
                .extracting(PublishedKnowledgeChunk::sourceId)
                .containsExactly(java.util.UUID.fromString(
                        "f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5"
                ));
    }

    @Test
    void shouldKeepLegacyLocalOnlyPublicElectiveDataOutOfStudentVisibleSearch() {
        ReviewedKnowledgePublisher publisher = new ReviewedKnowledgePublisher(
                new DriverManagerDataSource(
                        DATABASE_URL,
                        DATABASE_USER,
                        DATABASE_PASSWORD
                )
        );

        assertThat(publisher.searchStudentVisiblePublished("网络公共选修课一般不能超过"))
                .as("旧 PUBLIC_ELECTIVE 范围仍是本地验证资料，不能进入学生接口")
                .isEmpty();
    }

    private static Stream<Arguments> keywordProbes() throws IOException {
        JsonNode root = new ObjectMapper().readTree(Path.of(
                "data/curation/2025版学生手册2025.9.2-公共选修候选知识片段.json"
        ).toFile());
        return StreamSupport.stream(root.path("test_questions").spliterator(), false)
                .map(question -> Arguments.of(
                        question.path("question_id").asText(),
                        question.path("keyword_probe").path("keyword").asText(),
                        question.path("keyword_probe")
                                .path("expected_source_locator").asText()
                ));
    }
}
