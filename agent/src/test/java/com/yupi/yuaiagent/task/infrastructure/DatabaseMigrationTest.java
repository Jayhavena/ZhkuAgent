package com.yupi.yuaiagent.task.infrastructure;

import com.yupi.yuaiagent.task.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationTest extends PostgresIntegrationTestSupport {

    @Test
    void shouldCreateDurableTaskFoundationTables() {
        List<String> tables = jdbcTemplate().queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                ORDER BY table_name
                """, String.class);

        // 断言真实 PostgreSQL 中可观察到的业务表，而不是检查 SQL 文件文本。
        assertThat(tables).contains(
                "api_consumer",
                "agent_task",
                "research_plan",
                "task_step",
                "task_execution_attempt",
                "task_step_attempt",
                "task_event"
        );
    }
}
