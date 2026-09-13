package com.yupi.yuaiagent.task.infrastructure;

import com.yupi.yuaiagent.task.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证迁移在真实 PostgreSQL 中形成的关键数据不变量。
 *
 * <p>这些用例故意尝试写入违法数据；若数据库接受，说明约束不可靠，任务恢复代码也
 * 不能以该不变量为前提。</p>
 */
class DatabaseConstraintTest extends PostgresIntegrationTestSupport {

    private UUID taskId;

    @BeforeEach
    void insertTaskFixture() {
        UUID ownerId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        Instant now = Instant.now();
        // JdbcTemplate 不会为 Instant 推断 PostgreSQL 的 TIMESTAMPTZ 类型；显式转换
        // 使夹具能到达真正要验证的数据库约束，而不是在参数绑定阶段提前失败。
        Timestamp sqlNow = Timestamp.from(now);

        jdbcTemplate().update("""
                INSERT INTO api_consumer (
                    id, display_name, api_key_hash, api_key_prefix,
                    max_concurrent_tasks, max_model_calls_per_task,
                    max_github_calls_per_task, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ownerId, "test-user", "hash-" + ownerId, "test",
                2, 10, 20, true, sqlNow, sqlNow);

        jdbcTemplate().update("""
                INSERT INTO agent_task (
                    id, owner_id, repository_owner, repository_name,
                    research_question, status, deadline_at,
                    last_activity_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, ownerId, "spring-projects", "spring-ai",
                "分析工具调用相关 Issue", "CREATED",
                Timestamp.from(now.plus(30, ChronoUnit.MINUTES)), sqlNow, sqlNow, sqlNow);
    }

    @Test
    void shouldRejectTwoActivePlansForSameTask() {
        insertPlan(1, "ACTIVE");

        assertThatThrownBy(() -> insertPlan(2, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectTwoActiveExecutionAttemptsForSameTask() {
        insertExecutionAttempt("STARTING");

        assertThatThrownBy(() -> insertExecutionAttempt("RUNNING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateEventSequenceWithinTask() {
        insertEvent(1L);

        assertThatThrownBy(() -> insertEvent(1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectUnknownTaskStatus() {
        assertThatThrownBy(() -> jdbcTemplate().update(
                "UPDATE agent_task SET status = 'UNKNOWN' WHERE id = ?", taskId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertPlan(int version, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate().update("""
                INSERT INTO research_plan (
                    id, task_id, plan_version, status, created_at, activated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), taskId, version, status, now, now);
    }

    private void insertExecutionAttempt(String status) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate().update("""
                INSERT INTO task_execution_attempt (
                    id, task_id, run_id, executor_instance_id, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), taskId, UUID.randomUUID(), "test-instance", status, now);
    }

    private void insertEvent(long sequence) {
        jdbcTemplate().update("""
                INSERT INTO task_event (
                    id, task_id, event_seq, event_type,
                    schema_version, payload_json, created_at
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                """, UUID.randomUUID(), taskId, sequence,
                "TASK_CREATED", 1, "{}", Timestamp.from(Instant.now()));
    }
}
