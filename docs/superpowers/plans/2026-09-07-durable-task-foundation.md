# Durable Task Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立可恢复研究任务的 PostgreSQL 结构、任务与步骤状态机、关键唯一约束和可复现测试基线，但暂不实现 REST API、调度器、GitHub 或 RAG。

**Architecture:** PostgreSQL 保存持久任务事实，Java 领域状态机拒绝非法迁移，Flyway 管理版本化结构，真实 PostgreSQL 测试验证数据库特有约束。本阶段只交付后续子系统共同依赖的最小基础，不创建线程、SSE 或外部 API 调用。

**Tech Stack:** Java 21、Spring Boot 3.4.4、Spring JDBC、Flyway、PostgreSQL 16、Testcontainers、JUnit 5、AssertJ

**Spec:** `docs/superpowers/specs/2026-09-07-open-source-research-agent-design.md`

## Global Constraints

- 保留提交 `8ed4707` 已验证的 Agent 运行时生命周期语义和相关回归测试。
- 第一版只保证单应用实例与单 PostgreSQL，不实现分布式任务领取。
- 状态值使用 Java 枚举和数据库 CHECK 约束双重限定，不使用数据库原生 ENUM。
- 所有时间字段使用 `TIMESTAMPTZ`，Java 使用 `Instant`。
- 所有主键使用 UUID；事件序号在单任务内使用 BIGINT 递增。
- 外部调用不得位于数据库事务中；本阶段不添加任何外部调用。
- Testcontainers 只操作其创建的临时 PostgreSQL，禁止清理开发或生产数据库。
- 当前未跟踪的 `task` 文件是空骨架；实施时由本计划中的正式包结构替换，不擅自处理其他未跟踪文件。
- 原始测试输出写入固定的阶段目录 `target/evidence/research-task/phase-01/`，RED、GREEN、
  mutation 和回归分别使用不同文件名；该目录已由 `target/` 忽略规则排除，不进入 Git。
- 结构化证据进入 `docs/evidence/ResearchTask-证据账本.md`。
- 每个提交前先展示差异、测试、空白检查和精确暂存范围，并等待用户授权；禁止 `git add .`。
- 本计划完成不代表断点续跑已经实现，只证明领域状态与数据库基础满足后续接入条件。

---

## File Structure

### 本阶段修改

- Modify: `pom.xml` — 增加 Flyway、Spring Boot Testcontainers 和 PostgreSQL Testcontainers 测试依赖。

### 本阶段创建

- Create: `src/main/resources/db/migration/V1__create_research_task_foundation.sql` — 创建调用方、任务、计划、步骤、执行尝试、步骤尝试和事件表。
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/TaskState.java` — 定义持久任务状态和终态判断。
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/TaskStateMachine.java` — 定义唯一合法任务状态迁移入口。
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/InvalidTaskTransitionException.java` — 表达非法任务迁移。
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/TaskStepState.java` — 定义持久步骤状态和终态判断。
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/TaskStepStateMachine.java` — 定义唯一合法步骤迁移入口。
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/InvalidTaskStepTransitionException.java` — 表达非法步骤迁移。
- Create: `src/test/java/com/yupi/yuaiagent/task/support/PostgresIntegrationTestSupport.java` — 启动并重置专用 PostgreSQL 测试容器。
- Create: `src/test/java/com/yupi/yuaiagent/task/infrastructure/DatabaseMigrationTest.java` — 验证 Flyway 结构完整。
- Create: `src/test/java/com/yupi/yuaiagent/task/infrastructure/DatabaseConstraintTest.java` — 验证 PostgreSQL 唯一约束和检查约束。
- Create: `src/test/java/com/yupi/yuaiagent/task/domain/TaskStateMachineTest.java` — 验证任务状态迁移。
- Create: `src/test/java/com/yupi/yuaiagent/task/domain/TaskStepStateMachineTest.java` — 验证步骤状态迁移。
- Create: `docs/evidence/ResearchTask-证据账本.md` — 保存本阶段结构化结论和证据边界。

### 本阶段删除的空骨架

- Delete: `src/main/java/com/yupi/yuaiagent/task/AgentTask.java`
- Delete: `src/main/java/com/yupi/yuaiagent/task/TaskState.java`
- Delete: `src/main/java/com/yupi/yuaiagent/task/TaskStateMachine.java`
- Delete: `src/main/java/com/yupi/yuaiagent/task/validTaskTransitionException.java`
- Delete: `src/test/java/com/yupi/yuaiagent/task/TaskStateMachineTest.java`

这些文件当前均为未跟踪空骨架。删除动作只发生在用户批准执行本计划之后，并由新的
`task/domain` 正式实现替代；其他未跟踪文件不在本计划范围内。

---

### Task 1: PostgreSQL 测试环境与 Flyway 初始结构

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/yupi/yuaiagent/task/support/PostgresIntegrationTestSupport.java`
- Create: `src/test/java/com/yupi/yuaiagent/task/infrastructure/DatabaseMigrationTest.java`
- Create: `src/main/resources/db/migration/V1__create_research_task_foundation.sql`

**Interfaces:**
- Consumes: 当前项目已有的 PostgreSQL JDBC Driver 和 Spring JDBC。
- Produces: `PostgresIntegrationTestSupport.jdbcTemplate()`；七张基础业务表；后续 JDBC Repository 可依赖的稳定列名和约束。

- [ ] **Step 1: 增加数据库迁移与测试容器依赖**

在 `pom.xml` 的 PostgreSQL 依赖附近加入：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

版本由 Spring Boot 3.4.4 dependency management 管理，不在单个依赖上重复写版本。

在第一次运行测试前创建本阶段原始证据目录：

```powershell
New-Item -ItemType Directory -Force `
  -Path "target/evidence/research-task/phase-01" | Out-Null
```

后续实现时，每次测试都必须把控制台完整输出保存为独立日志；即使 RED 失败，也要保留
其退出状态和失败原因，不能只保存最终 GREEN。

- [ ] **Step 2: 创建只操作测试容器的 PostgreSQL 支持类**

创建 `PostgresIntegrationTestSupport.java`：

```java
package com.yupi.yuaiagent.task.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 为持久任务集成测试提供真实 PostgreSQL。
 *
 * <p>clean 只作用于 Testcontainers 创建的临时数据库，绝不能把本类的数据源
 * 替换为本地开发库或生产库。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTestSupport {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
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

        // 测试隔离必须先清空临时容器数据库，再重新执行正式 Flyway 迁移。
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
```

- [ ] **Step 3: 写迁移结构的 RED 测试**

创建 `DatabaseMigrationTest.java`：

```java
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
```

- [ ] **Step 4: 运行 RED 并保存原始输出**

运行：

```powershell
.\mvnw.cmd "-Dtest=DatabaseMigrationTest" test
```

预期：测试失败，原因是七张业务表尚不存在。Docker 不可用导致测试跳过不属于有效
RED；此时应先修复测试环境，不能把跳过结果当作数据库验证。

将完整输出保存到本阶段 `target/evidence/research-task/` 目录下的迁移 RED 日志。

- [ ] **Step 5: 创建正式 Flyway 迁移**

创建 `V1__create_research_task_foundation.sql`：

```sql
CREATE TABLE api_consumer (
    id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL UNIQUE,
    api_key_prefix VARCHAR(16) NOT NULL,
    max_concurrent_tasks INTEGER NOT NULL CHECK (max_concurrent_tasks > 0),
    max_model_calls_per_task INTEGER NOT NULL CHECK (max_model_calls_per_task > 0),
    max_github_calls_per_task INTEGER NOT NULL CHECK (max_github_calls_per_task > 0),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE agent_task (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES api_consumer(id),
    repository_owner VARCHAR(100) NOT NULL,
    repository_name VARCHAR(100) NOT NULL,
    research_question TEXT NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'CREATED', 'PLANNING', 'READY', 'RUNNING', 'WAITING_RETRY',
        'CANCEL_REQUESTED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT'
    )),
    active_plan_version INTEGER,
    deadline_at TIMESTAMPTZ NOT NULL,
    deadline_exceeded_at TIMESTAMPTZ,
    termination_pending BOOLEAN NOT NULL DEFAULT FALSE,
    next_retry_at TIMESTAMPTZ,
    last_activity_at TIMESTAMPTZ NOT NULL,
    last_subscriber_at TIMESTAMPTZ,
    model_call_count INTEGER NOT NULL DEFAULT 0 CHECK (model_call_count >= 0),
    embedding_call_count INTEGER NOT NULL DEFAULT 0 CHECK (embedding_call_count >= 0),
    github_call_count INTEGER NOT NULL DEFAULT 0 CHECK (github_call_count >= 0),
    next_event_seq BIGINT NOT NULL DEFAULT 1 CHECK (next_event_seq > 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE research_plan (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    plan_version INTEGER NOT NULL CHECK (plan_version > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    change_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    superseded_at TIMESTAMPTZ,
    UNIQUE (task_id, plan_version)
);

CREATE UNIQUE INDEX uq_research_plan_one_active
    ON research_plan(task_id)
    WHERE status = 'ACTIVE';

CREATE TABLE task_step (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    plan_version INTEGER NOT NULL CHECK (plan_version > 0),
    step_no INTEGER NOT NULL CHECK (step_no > 0),
    step_type VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    input_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'PENDING', 'RUNNING', 'SUCCEEDED', 'WAITING_RETRY',
        'FAILED', 'INTERRUPTED', 'SKIPPED'
    )),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    result_summary TEXT,
    last_error_type VARCHAR(64),
    last_error_message TEXT,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (task_id, plan_version, step_no),
    FOREIGN KEY (task_id, plan_version)
        REFERENCES research_plan(task_id, plan_version) ON DELETE CASCADE
);

CREATE TABLE task_execution_attempt (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    run_id UUID NOT NULL UNIQUE,
    executor_instance_id VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'STARTING', 'RUNNING', 'SUCCEEDED', 'FAILED',
        'INTERRUPTED', 'REJECTED'
    )),
    stop_reason VARCHAR(64),
    heartbeat_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_type VARCHAR(64),
    error_message TEXT
);

CREATE UNIQUE INDEX uq_task_execution_one_active
    ON task_execution_attempt(task_id)
    WHERE status IN ('STARTING', 'RUNNING');

CREATE TABLE task_step_attempt (
    id UUID PRIMARY KEY,
    task_step_id UUID NOT NULL REFERENCES task_step(id) ON DELETE CASCADE,
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'STARTING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'INTERRUPTED'
    )),
    request_summary TEXT,
    result_summary TEXT,
    error_type VARCHAR(64),
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    UNIQUE (task_step_id, attempt_no)
);

CREATE TABLE task_event (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    event_seq BIGINT NOT NULL CHECK (event_seq > 0),
    event_type VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (task_id, event_seq)
);

CREATE INDEX idx_agent_task_owner_status
    ON agent_task(owner_id, status, created_at);

CREATE INDEX idx_agent_task_dispatch
    ON agent_task(status, next_retry_at, created_at);

CREATE INDEX idx_task_step_task_plan
    ON task_step(task_id, plan_version, step_no);

CREATE INDEX idx_task_event_task_seq
    ON task_event(task_id, event_seq);

CREATE INDEX idx_execution_attempt_heartbeat
    ON task_execution_attempt(status, heartbeat_at);
```

- [ ] **Step 6: 运行 GREEN**

运行：

```powershell
.\mvnw.cmd "-Dtest=DatabaseMigrationTest" test
```

预期：1 项测试通过，0 失败、0 错误、0 跳过。若 Docker 不可用导致跳过，本任务不能
标记完成。

- [ ] **Step 7: 准备 Task 1 提交审阅**

检查：

```powershell
git diff --check
git status --short
git diff --stat
```

建议暂存范围仅包含 `pom.xml`、迁移、测试支持类和迁移测试。建议提交信息：

```text
feat(task): add durable task database foundation
```

展示测试和差异后等待用户授权，再精确暂存和提交。

---

### Task 2: 持久任务状态机

**Files:**
- Delete: `src/main/java/com/yupi/yuaiagent/task/TaskState.java`
- Delete: `src/main/java/com/yupi/yuaiagent/task/TaskStateMachine.java`
- Delete: `src/main/java/com/yupi/yuaiagent/task/validTaskTransitionException.java`
- Delete: `src/test/java/com/yupi/yuaiagent/task/TaskStateMachineTest.java`
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/TaskState.java`
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/TaskStateMachine.java`
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/InvalidTaskTransitionException.java`
- Create: `src/test/java/com/yupi/yuaiagent/task/domain/TaskStateMachineTest.java`

**Interfaces:**
- Consumes: Task 1 数据库 CHECK 约束中的任务状态字符串。
- Produces: `TaskState.isTerminal()`；`TaskStateMachine.requireTransition(TaskState, TaskState)`。

- [ ] **Step 1: 用正式包替换空测试骨架并写 RED**

创建 `task/domain/TaskStateMachineTest.java`：

```java
package com.yupi.yuaiagent.task.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStateMachineTest {

    @ParameterizedTest
    @MethodSource("legalTransitions")
    void shouldAcceptLegalTransitions(TaskState from, TaskState to) {
        assertThatCode(() -> TaskStateMachine.requireTransition(from, to))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTerminalStateTransition() {
        assertThatThrownBy(() -> TaskStateMachine.requireTransition(
                TaskState.SUCCEEDED,
                TaskState.RUNNING
        ))
                .isInstanceOf(InvalidTaskTransitionException.class)
                .hasMessageContaining("SUCCEEDED")
                .hasMessageContaining("RUNNING");
    }

    @Test
    void shouldClassifyOnlyFinalStatesAsTerminal() {
        assertThat(Stream.of(TaskState.values())
                .filter(TaskState::isTerminal)
                .toList())
                .containsExactlyInAnyOrder(
                        TaskState.SUCCEEDED,
                        TaskState.FAILED,
                        TaskState.CANCELLED,
                        TaskState.TIMED_OUT
                );
    }

    private static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(TaskState.CREATED, TaskState.PLANNING),
                Arguments.of(TaskState.CREATED, TaskState.CANCEL_REQUESTED),
                Arguments.of(TaskState.PLANNING, TaskState.READY),
                Arguments.of(TaskState.PLANNING, TaskState.WAITING_RETRY),
                Arguments.of(TaskState.READY, TaskState.RUNNING),
                Arguments.of(TaskState.RUNNING, TaskState.SUCCEEDED),
                Arguments.of(TaskState.RUNNING, TaskState.WAITING_RETRY),
                Arguments.of(TaskState.RUNNING, TaskState.CANCEL_REQUESTED),
                Arguments.of(TaskState.RUNNING, TaskState.READY),
                Arguments.of(TaskState.WAITING_RETRY, TaskState.READY),
                Arguments.of(TaskState.CANCEL_REQUESTED, TaskState.CANCELLED),
                Arguments.of(TaskState.CANCEL_REQUESTED, TaskState.FAILED)
        );
    }
}
```

删除旧的四个空骨架文件。本任务不删除尚未使用的空 `AgentTask.java`，它在 Task 4
统一处理。

- [ ] **Step 2: 运行测试确认 RED 原因正确**

运行：

```powershell
.\mvnw.cmd "-Dtest=TaskStateMachineTest" test
```

预期：编译失败，原因是正式 `task.domain` 类型尚不存在。失败若来自旧包残留或无关
代码，则先修复测试结构，不能继续写实现掩盖错误。

- [ ] **Step 3: 创建任务状态枚举**

创建 `TaskState.java`：

```java
package com.yupi.yuaiagent.task.domain;

/**
 * 持久研究任务的业务状态。
 *
 * <p>该状态跨请求和进程重启存在，与单次 JVM 执行的 AgentRunState 不同。</p>
 */
public enum TaskState {
    CREATED,
    PLANNING,
    READY,
    RUNNING,
    WAITING_RETRY,
    CANCEL_REQUESTED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == SUCCEEDED
                || this == FAILED
                || this == CANCELLED
                || this == TIMED_OUT;
    }
}
```

- [ ] **Step 4: 创建异常与唯一迁移入口**

创建 `InvalidTaskTransitionException.java`：

```java
package com.yupi.yuaiagent.task.domain;

/**
 * 表示调用方试图执行未被领域状态机允许的任务迁移。
 */
public class InvalidTaskTransitionException extends IllegalStateException {

    public InvalidTaskTransitionException(TaskState from, TaskState to) {
        super("非法任务状态迁移: " + from + " -> " + to);
    }
}
```

创建 `TaskStateMachine.java`：

```java
package com.yupi.yuaiagent.task.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 持久任务状态迁移的唯一领域入口。
 *
 * <p>应用服务仍需通过数据库 version 条件更新处理并发；本类只负责业务合法性，
 * 不替代数据库乐观锁。</p>
 */
public final class TaskStateMachine {

    private static final Map<TaskState, Set<TaskState>> ALLOWED = buildTransitions();

    private TaskStateMachine() {
    }

    public static void requireTransition(TaskState from, TaskState to) {
        Set<TaskState> targets = ALLOWED.getOrDefault(from, Set.of());
        if (!targets.contains(to)) {
            throw new InvalidTaskTransitionException(from, to);
        }
    }

    private static Map<TaskState, Set<TaskState>> buildTransitions() {
        EnumMap<TaskState, Set<TaskState>> transitions = new EnumMap<>(TaskState.class);
        transitions.put(TaskState.CREATED, EnumSet.of(
                TaskState.PLANNING,
                TaskState.CANCEL_REQUESTED,
                TaskState.FAILED,
                TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.PLANNING, EnumSet.of(
                TaskState.READY,
                TaskState.WAITING_RETRY,
                TaskState.CANCEL_REQUESTED,
                TaskState.FAILED,
                TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.READY, EnumSet.of(
                TaskState.RUNNING,
                TaskState.CANCEL_REQUESTED,
                TaskState.FAILED,
                TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.RUNNING, EnumSet.of(
                TaskState.READY,
                TaskState.WAITING_RETRY,
                TaskState.CANCEL_REQUESTED,
                TaskState.SUCCEEDED,
                TaskState.FAILED,
                TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.WAITING_RETRY, EnumSet.of(
                TaskState.READY,
                TaskState.CANCEL_REQUESTED,
                TaskState.FAILED,
                TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.CANCEL_REQUESTED, EnumSet.of(
                TaskState.CANCELLED,
                TaskState.FAILED
        ));
        return Map.copyOf(transitions);
    }
}
```

- [ ] **Step 5: 运行 GREEN**

运行：

```powershell
.\mvnw.cmd "-Dtest=TaskStateMachineTest" test
```

预期：参数化合法迁移和两个普通测试全部通过。记录 Surefire 实际测试总数，不根据
源码目测填写证据。

- [ ] **Step 6: 准备 Task 2 提交审阅**

建议暂存仅包含任务状态机、异常、测试以及被替换的旧空骨架。建议提交信息：

```text
feat(task): define durable task lifecycle
```

提交前运行 `git diff --check` 并等待用户授权。

---

### Task 3: 持久步骤状态机

**Files:**
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/TaskStepState.java`
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/TaskStepStateMachine.java`
- Create: `src/main/java/com/yupi/yuaiagent/task/domain/InvalidTaskStepTransitionException.java`
- Create: `src/test/java/com/yupi/yuaiagent/task/domain/TaskStepStateMachineTest.java`

**Interfaces:**
- Consumes: Task 1 数据库 CHECK 约束中的步骤状态字符串。
- Produces: `TaskStepState.isTerminal()`；`TaskStepStateMachine.requireTransition(TaskStepState, TaskStepState)`。

- [ ] **Step 1: 写步骤状态 RED 测试**

创建 `TaskStepStateMachineTest.java`：

```java
package com.yupi.yuaiagent.task.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStepStateMachineTest {

    @ParameterizedTest
    @MethodSource("legalTransitions")
    void shouldAcceptLegalTransitions(TaskStepState from, TaskStepState to) {
        assertThatCode(() -> TaskStepStateMachine.requireTransition(from, to))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectReexecutionOfSucceededStep() {
        assertThatThrownBy(() -> TaskStepStateMachine.requireTransition(
                TaskStepState.SUCCEEDED,
                TaskStepState.RUNNING
        ))
                .isInstanceOf(InvalidTaskStepTransitionException.class)
                .hasMessageContaining("SUCCEEDED")
                .hasMessageContaining("RUNNING");
    }

    @Test
    void shouldClassifyFinalStepStates() {
        assertThat(Stream.of(TaskStepState.values())
                .filter(TaskStepState::isTerminal)
                .toList())
                .containsExactlyInAnyOrder(
                        TaskStepState.SUCCEEDED,
                        TaskStepState.FAILED,
                        TaskStepState.SKIPPED
                );
    }

    private static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(TaskStepState.PENDING, TaskStepState.RUNNING),
                Arguments.of(TaskStepState.PENDING, TaskStepState.SKIPPED),
                Arguments.of(TaskStepState.RUNNING, TaskStepState.SUCCEEDED),
                Arguments.of(TaskStepState.RUNNING, TaskStepState.WAITING_RETRY),
                Arguments.of(TaskStepState.RUNNING, TaskStepState.FAILED),
                Arguments.of(TaskStepState.RUNNING, TaskStepState.INTERRUPTED),
                Arguments.of(TaskStepState.WAITING_RETRY, TaskStepState.RUNNING),
                Arguments.of(TaskStepState.WAITING_RETRY, TaskStepState.FAILED),
                Arguments.of(TaskStepState.INTERRUPTED, TaskStepState.RUNNING)
        );
    }
}
```

- [ ] **Step 2: 运行测试确认 RED**

```powershell
.\mvnw.cmd "-Dtest=TaskStepStateMachineTest" test
```

预期：编译失败，缺少正式步骤状态类型。

- [ ] **Step 3: 创建步骤状态枚举**

创建 `TaskStepState.java`：

```java
package com.yupi.yuaiagent.task.domain;

/**
 * 一个持久计划步骤的状态。
 *
 * <p>INTERRUPTED 表示本次进程执行未确认完成，可以在新的执行尝试中重新进入
 * RUNNING；SUCCEEDED 是恢复时唯一可以无条件跳过的成功检查点。</p>
 */
public enum TaskStepState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    WAITING_RETRY,
    FAILED,
    INTERRUPTED,
    SKIPPED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED;
    }
}
```

- [ ] **Step 4: 创建异常和步骤迁移入口**

创建 `InvalidTaskStepTransitionException.java`：

```java
package com.yupi.yuaiagent.task.domain;

/**
 * 表示持久步骤发生非法状态迁移。
 */
public class InvalidTaskStepTransitionException extends IllegalStateException {

    public InvalidTaskStepTransitionException(TaskStepState from, TaskStepState to) {
        super("非法任务步骤状态迁移: " + from + " -> " + to);
    }
}
```

创建 `TaskStepStateMachine.java`：

```java
package com.yupi.yuaiagent.task.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 持久任务步骤的合法迁移定义。
 */
public final class TaskStepStateMachine {

    private static final Map<TaskStepState, Set<TaskStepState>> ALLOWED =
            buildTransitions();

    private TaskStepStateMachine() {
    }

    public static void requireTransition(TaskStepState from, TaskStepState to) {
        Set<TaskStepState> targets = ALLOWED.getOrDefault(from, Set.of());
        if (!targets.contains(to)) {
            throw new InvalidTaskStepTransitionException(from, to);
        }
    }

    private static Map<TaskStepState, Set<TaskStepState>> buildTransitions() {
        EnumMap<TaskStepState, Set<TaskStepState>> transitions =
                new EnumMap<>(TaskStepState.class);
        transitions.put(TaskStepState.PENDING, EnumSet.of(
                TaskStepState.RUNNING,
                TaskStepState.SKIPPED
        ));
        transitions.put(TaskStepState.RUNNING, EnumSet.of(
                TaskStepState.SUCCEEDED,
                TaskStepState.WAITING_RETRY,
                TaskStepState.FAILED,
                TaskStepState.INTERRUPTED
        ));
        transitions.put(TaskStepState.WAITING_RETRY, EnumSet.of(
                TaskStepState.RUNNING,
                TaskStepState.FAILED,
                TaskStepState.INTERRUPTED
        ));
        transitions.put(TaskStepState.INTERRUPTED, EnumSet.of(
                TaskStepState.RUNNING,
                TaskStepState.FAILED
        ));
        return Map.copyOf(transitions);
    }
}
```

- [ ] **Step 5: 运行 GREEN**

```powershell
.\mvnw.cmd "-Dtest=TaskStepStateMachineTest" test
```

预期：所有参数化迁移和终态测试通过。

- [ ] **Step 6: 准备 Task 3 提交审阅**

建议暂存仅包含三个步骤领域类型和测试。建议提交信息：

```text
feat(task): define recoverable step lifecycle
```

提交前运行空白检查并等待用户授权。

---

### Task 4: PostgreSQL 关键约束行为验证

**Files:**
- Delete: `src/main/java/com/yupi/yuaiagent/task/AgentTask.java`
- Create: `src/test/java/com/yupi/yuaiagent/task/infrastructure/DatabaseConstraintTest.java`

**Interfaces:**
- Consumes: Task 1 的七张表和唯一索引。
- Produces: “单任务单活动计划、单活动执行尝试、事件序号唯一、状态值受限”的真实 PostgreSQL 证据。

- [ ] **Step 1: 删除最后一个无行为的旧空骨架**

删除 `src/main/java/com/yupi/yuaiagent/task/AgentTask.java`。正式 `AgentTask` 聚合将在任务
接纳计划中根据 Repository 接口一次定义；本阶段不保留同名空类制造完成假象。

- [ ] **Step 2: 写 PostgreSQL 约束测试**

创建 `DatabaseConstraintTest.java`：

```java
package com.yupi.yuaiagent.task.infrastructure;

import com.yupi.yuaiagent.task.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConstraintTest extends PostgresIntegrationTestSupport {

    private UUID taskId;

    @BeforeEach
    void insertTaskFixture() {
        UUID ownerId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate().update("""
                INSERT INTO api_consumer (
                    id, display_name, api_key_hash, api_key_prefix,
                    max_concurrent_tasks, max_model_calls_per_task,
                    max_github_calls_per_task, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ownerId, "test-user", "hash-" + ownerId, "test",
                2, 10, 20, true, now, now);

        jdbcTemplate().update("""
                INSERT INTO agent_task (
                    id, owner_id, repository_owner, repository_name,
                    research_question, status, deadline_at,
                    last_activity_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, ownerId, "spring-projects", "spring-ai",
                "分析工具调用相关 Issue", "CREATED",
                now.plus(30, ChronoUnit.MINUTES), now, now, now);
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
                "UPDATE agent_task SET status = 'UNKNOWN' WHERE id = ?",
                taskId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertPlan(int version, String status) {
        Instant now = Instant.now();
        jdbcTemplate().update("""
                INSERT INTO research_plan (
                    id, task_id, plan_version, status,
                    created_at, activated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), taskId, version, status, now, now);
    }

    private void insertExecutionAttempt(String status) {
        Instant now = Instant.now();
        jdbcTemplate().update("""
                INSERT INTO task_execution_attempt (
                    id, task_id, run_id, executor_instance_id,
                    status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), taskId, UUID.randomUUID(),
                "test-instance", status, now);
    }

    private void insertEvent(long sequence) {
        jdbcTemplate().update("""
                INSERT INTO task_event (
                    id, task_id, event_seq, event_type,
                    schema_version, payload_json, created_at
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                """,
                UUID.randomUUID(), taskId, sequence,
                "TASK_CREATED", 1, "{}", Instant.now());
    }
}
```

- [ ] **Step 3: 运行数据库聚焦测试**

```powershell
.\mvnw.cmd "-Dtest=DatabaseMigrationTest,DatabaseConstraintTest" test
```

预期：迁移测试和 4 个约束测试全部通过；Docker 不可用导致跳过不能算通过。

- [ ] **Step 4: 执行一次约束 mutation 验证测试敏感性**

仅在工作区临时删除 `uq_task_execution_one_active` 索引定义，再运行：

```powershell
.\mvnw.cmd "-Dtest=DatabaseConstraintTest#shouldRejectTwoActiveExecutionAttemptsForSameTask" test
```

预期：测试失败，因为第二个活动 attempt 被数据库接受。立即恢复迁移文件，再重新运行
聚焦测试。mutation 不得暂存或提交。

- [ ] **Step 5: 准备 Task 4 提交审阅**

提交前确认 mutation 已恢复、旧空骨架已删除、数据库测试无跳过。建议提交信息：

```text
test(task): verify durable task database constraints
```

展示差异后等待用户授权。

---

### Task 5: 阶段回归与证据账本

**Files:**
- Create: `docs/evidence/ResearchTask-证据账本.md`

**Interfaces:**
- Consumes: Task 1 至 Task 4 的 Surefire 输出和当前 Agent 生命周期证据。
- Produces: 可核验的阶段基线；下一计划可引用的数据库与状态机结论。

- [ ] **Step 1: 运行本阶段全部新测试并保存输出**

```powershell
.\mvnw.cmd "-Dtest=DatabaseMigrationTest,DatabaseConstraintTest,TaskStateMachineTest,TaskStepStateMachineTest" test
```

记录 Maven 实际报告的测试、失败、错误和跳过数量。Testcontainers 测试有任何跳过时，
本阶段不能声称 PostgreSQL 验证通过。

- [ ] **Step 2: 运行现有 Agent 生命周期阶段回归**

```powershell
.\mvnw.cmd "-Dtest=AgentRunContextTest,BaseAgentEncapsulationTest,BaseAgentExecutorRejectionTest,BaseAgentExecutorSelectionTest,BaseAgentExecutorTerminationTest,BaseAgentFailureLifecycleTest,BaseAgentLifecycleTest,BaseAgentSseLifecycleTest,BaseAgentStreamCancellationTest,ReActAgentDecisionIsolationTest,YuManusLifecycleOwnershipTest,AgentExecutorConfigTest" test
```

预期：原有 25 项相关测试继续通过。若实际测试数变化，以 Surefire 输出为准并先调查，
不能为了匹配 25 删除或跳过测试。

- [ ] **Step 3: 创建结构化证据账本**

先执行以下命令，得到可直接抄录且可复核的环境事实：

```powershell
Get-Date -Format o
git rev-parse HEAD
git status --short
java -version
.\mvnw.cmd -version
```

随后创建 `ResearchTask-证据账本.md`。账本必须逐项引用上述命令、本阶段 Surefire
报告和 mutation 日志中的真实输出，不得预填数字或保留占位内容。结构固定为：

1. `环境与版本`：验证时间、Git 基线、工作区状态、Java、Maven、PostgreSQL 镜像；
2. `执行结果`：每条测试命令、测试类、测试数、失败、错误、跳过、超时和原始日志路径；
3. `mutation`：移除的约束、预期失败断言、生产定义恢复后的回归结果；
4. `可以证明`：Flyway 建表、状态机非法迁移拒绝、活动计划与活动执行尝试唯一、
   事件序号与状态值约束、Agent 生命周期回归未退化；
5. `不能证明`：尚未实现 REST API、调度、事件补发、取消、重启恢复、GitHub、RAG，
   且数据库功能测试不代表性能、吞吐量、多实例安全或生产收益。

如果 Testcontainers 测试被跳过，`可以证明` 中不得保留任何 PostgreSQL 行为结论；
应在 `不能证明` 中明确写明运行环境缺少真实数据库验证。

- [ ] **Step 4: 检查本阶段变更范围**

```powershell
git diff --check
git status --short
git diff --stat
```

确认：

- 没有生产 API、调度器、GitHub 或 RAG 代码；
- 没有暂存其他用户文件；
- migration mutation 已恢复；
- 原始日志未被 Git 跟踪；
- 五个旧空骨架已由计划声明的正式结构替代或删除；
- 新测试和阶段回归均通过。

- [ ] **Step 5: 准备阶段证据提交审阅**

建议提交信息：

```text
docs(task): record durable task foundation evidence
```

向用户展示最终差异摘要、测试汇总、空白检查、建议暂存路径和提交信息。只有得到明确
授权后才执行精确 `git add` 和 `git commit`。

---

## Phase Completion Gate

本计划完成必须同时满足：

1. Flyway 在真实临时 PostgreSQL 16 上成功创建七张表；
2. 数据库测试未因 Docker 不可用而跳过；
3. 任务和步骤状态机聚焦测试通过；
4. 四项数据库约束行为测试通过；
5. 唯一活动 execution attempt 的 mutation 能让测试失败，恢复后重新通过；
6. 原有 Agent 生命周期阶段回归通过；
7. `git diff --check` 无空白错误；
8. 原始输出保存在 `target/evidence` 且未进入 Git；
9. 证据账本只写真实测试数字；
10. 没有声称已经实现 API、调度、恢复、GitHub 或 RAG。

通过此门后，下一份独立实施计划才开始 API Key、`AgentTask` 聚合、幂等任务接纳与
任务查询 API。
