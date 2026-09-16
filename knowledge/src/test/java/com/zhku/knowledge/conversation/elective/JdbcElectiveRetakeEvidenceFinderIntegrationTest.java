package com.zhku.knowledge.conversation.elective;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.publication.ReviewedKnowledgeJsonReader;
import com.zhku.knowledge.publication.ReviewedKnowledgePublication;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 4 的 PostgreSQL 证据验收。
 *
 * <p>发布使用独立知识范围，避免测试替换既有公共选修资料；查询器必须能读到两条学生可见、
 * 已发布的第十八条证据，才能允许应用服务向学生给出对应结论。</p>
 */
class JdbcElectiveRetakeEvidenceFinderIntegrationTest {

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

    @Test
    void shouldReadBothPublishedStudentVisibleRetakeRulesFromPostgreSql() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        );
        ReviewedKnowledgePublication publication = new ReviewedKnowledgeJsonReader().read(Path.of(
                "data/curation/2025版学生手册2025.9.2-选修课重修审核知识片段.json"
        ));
        new ReviewedKnowledgePublisher(dataSource).publishReplacingScope(
                publication,
                "STUDENT_HANDBOOK",
                "ELECTIVE_RETAKE"
        );
        JdbcElectiveRetakeEvidenceFinder finder = new JdbcElectiveRetakeEvidenceFinder(dataSource);

        assertThat(finder.find(CourseType.PUBLIC_ELECTIVE))
                .hasValueSatisfying(evidence -> assertThat(evidence)
                        .extracting(
                                ElectiveRetakeEvidence::sourceId,
                                ElectiveRetakeEvidence::sourceVersion,
                                ElectiveRetakeEvidence::sourceLocator,
                                ElectiveRetakeEvidence::content
                        )
                        .containsExactly(
                                publication.sourceId(),
                                "2025版",
                                "PDF 第 77 页，第 18 条",
                                "公共选修课不办理缓考，成绩不合格者不安排重修。"
                        ));
        assertThat(finder.find(CourseType.PROFESSIONAL_ELECTIVE))
                .hasValueSatisfying(evidence -> assertThat(evidence)
                        .extracting(
                                ElectiveRetakeEvidence::sourceId,
                                ElectiveRetakeEvidence::sourceVersion,
                                ElectiveRetakeEvidence::sourceLocator,
                                ElectiveRetakeEvidence::content
                        )
                        .containsExactly(
                                publication.sourceId(),
                                "2025版",
                                "PDF 第 77 页，第 18 条",
                                "专业选修课成绩不合格者可申请重修学习。"
                        ));
    }
}
