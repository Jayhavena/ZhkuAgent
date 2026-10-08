package com.zhku.knowledge.conversation.publicelective;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.publication.ReviewedKnowledgeJsonReader;
import com.zhku.knowledge.publication.ReviewedKnowledgePublication;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 学分上限规则只能使用其独立范围内已发布、学生可见的数据库证据。 */
class JdbcCreditLimitEvidenceFinderIntegrationTest {

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty("zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();

    @Test
    void shouldReadPublishedStudentVisibleCreditLimitEvidenceFromDedicatedScope() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        );
        ReviewedKnowledgePublication publication = new ReviewedKnowledgeJsonReader().read(Path.of(
                "data/curation/2025版学生手册2025.9.2-公共选修单学期学分上限审核知识片段.json"
        ));
        new ReviewedKnowledgePublisher(dataSource).publishReplacingScope(
                publication,
                "STUDENT_HANDBOOK",
                "PUBLIC_ELECTIVE_CREDIT_LIMIT"
        );

        JdbcCreditLimitEvidenceFinder finder = new JdbcCreditLimitEvidenceFinder(dataSource);

        assertThat(finder.find()).hasValueSatisfying(evidence -> assertThat(evidence)
                .extracting(
                        CreditLimitEvidence::sourceId,
                        CreditLimitEvidence::sourceVersion,
                        CreditLimitEvidence::sourceLocator,
                        CreditLimitEvidence::content
                )
                .containsExactly(
                        publication.sourceId(),
                        "2025版",
                        "PDF 第 76—77 页，第 8、10 条",
                        "学生应以人才培养方案为选课依据，在规定学习年限内修读并取得规定学分；每学期公共选修课最高不得超过 4 学分。"
                ));
    }
}
