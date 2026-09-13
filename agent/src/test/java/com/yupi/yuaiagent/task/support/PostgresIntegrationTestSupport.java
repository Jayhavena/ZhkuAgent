package com.yupi.yuaiagent.task.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 为持久任务集成测试提供由 Testcontainers 独占的真实 PostgreSQL。
 *
 * <p>每个测试开始前都会清空并重新迁移临时数据库，确保约束测试之间互不污染。
 * {@code clean} 只能作用于本类创建的容器，禁止把这里的数据源替换为开发库或生产库。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTestSupport {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("research_agent_test")
                    .withUsername("research_test")
                    .withPassword("research_test");

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .cleanDisabled(false)
                .load();

        // 先清理再执行正式迁移，使每个测试都从同一数据库事实开始。
        flyway.clean();
        flyway.migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    protected JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }
}
