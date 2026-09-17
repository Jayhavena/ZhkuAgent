# JDBC 会话存储 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变本机预览当前 Bean 装配的前提下，交付 PostgreSQL `ConversationStore`、V12 迁移和数据库契约测试。

**Architecture:** 新表只保存既有不可变会话快照的控制字段。`JdbcConversationStore` 用包含状态、版本和到期时间的条件更新实现原子乐观并发；更新未命中时读取快照并返回既有 `StoreResult.Outcome`。工厂预留 `zhku.conversation.store`，但不接入 `ZhkuLocalPreviewConfiguration`。

**Tech Stack:** Java 21、Spring JDBC `DataSource`、PostgreSQL 16、Flyway、JUnit 5、AssertJ。

**Spec:** `docs/superpowers/specs/2026-09-17-jdbc-conversation-store-design.md`

## Global Constraints

- 只追加 V12；不得改写 V1–V8、数据库卷、DataRoot 或 `.env`。
- 保持 S01–S11：`now >= expiresAt` 即到期，读取不续期，取消为幂等终态。
- 时间只来自调用者传入的 `Clock`；不得使用系统时间或 `Thread.sleep`。
- 默认选择内存，精确值 `jdbc` 选择 JDBC；不修改预览配置、Controller、页面、SSE 或路由器。
- 密码仅来自 `ZHKU_DB_PASSWORD` 或既有 `zhku.db.password` 系统属性。
- 每项能力同批有证据账本；中文注释只解释并发与状态风险。

---

### Task 1: V12 会话快照表与结构迁移验证

**Files:**
- Create: `knowledge/src/main/resources/db/migration/V12__create_conversation_session_store.sql`
- Create: `knowledge/src/test/java/com/zhku/knowledge/conversation/ConversationSessionSchemaTest.java`

**Interfaces:**
- Consumes: `ConversationState(sessionId, status, pendingIntent, pendingField, expiresAt, revision)`。
- Produces: PostgreSQL `conversation_session`，供 JDBC 存储使用。

- [ ] **Step 1: 写失败的结构测试**

```java
@Test
void shouldProvideVersionedConversationSessionStoreSchema() throws SQLException {
    migrate();
    DatabaseMetaData metadata = connection().getMetaData();
    assertThat(tableExists(metadata, "conversation_session")).isTrue();
    assertThat(columnsOf(metadata, "conversation_session")).containsExactlyInAnyOrder(
            "session_id", "status", "pending_intent", "pending_field", "expires_at", "revision");
    assertThat(indexColumns(metadata, "conversation_session")).contains("expires_at");
}
```

- [ ] **Step 2: 运行测试确认红灯**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ConversationSessionSchemaTest test`

Expected: FAIL，因为 `conversation_session` 尚不存在。

- [ ] **Step 3: 追加 V12 迁移**

```sql
CREATE TABLE conversation_session (
    session_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    pending_intent VARCHAR(64), pending_field VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    CONSTRAINT conversation_session_pending_pair_check
        CHECK ((pending_intent IS NULL) = (pending_field IS NULL)),
    CONSTRAINT conversation_session_cancelled_pending_check
        CHECK (status <> 'CANCELLED' OR pending_intent IS NULL)
);
CREATE INDEX conversation_session_expires_at_idx ON conversation_session (expires_at);
```

- [ ] **Step 4: 运行结构测试确认绿灯**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ConversationSessionSchemaTest test`

Expected: PASS，Flyway 顺序执行至 V12。

- [ ] **Step 5: 提交独立迁移与结构测试**

```powershell
git add knowledge/src/main/resources/db/migration/V12__create_conversation_session_store.sql knowledge/src/test/java/com/zhku/knowledge/conversation/ConversationSessionSchemaTest.java
git commit -m "feat(zhku): 新增会话持久化结构迁移"
```

### Task 2: JDBC 存储与数据库版 S01–S11 契约

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/JdbcConversationStore.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/conversation/JdbcConversationStoreContractTest.java`

**Interfaces:**
- Consumes: `ConversationStore` 的方法与 Task 1 的 `conversation_session`。
- Produces: `JdbcConversationStore(DataSource dataSource)`。

- [ ] **Step 1: 写失败的 JDBC 契约测试**

测试使用随机 UUID 跟踪自身创建的会话，`@AfterEach` 仅逐个删除自身 ID，绝不清空整表。实现与内存版同等的 S01–S11：创建/重复创建、隔离、到期边界、pending 写入与清理的版本和续期、到期写入、幂等取消、取消后写入、过期清理、旧 revision。

```java
@Test
void shouldRejectLatePendingWriteAfterCancellationByItsOldRevision() {
    UUID sessionId = createActiveSession();
    StoreResult cancelled = store.cancel(sessionId, 0, clockAt(START.plusSeconds(1)));
    StoreResult lateWrite = store.savePending(sessionId, 0,
            PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE, PendingField.COURSE_TYPE,
            expiresAfter(START.plusSeconds(2)), clockAt(START.plusSeconds(2)));
    assertThat(cancelled.state().revision()).isEqualTo(1L);
    assertThat(lateWrite.outcome()).isIn(StoreResult.Outcome.STALE_WRITE,
            StoreResult.Outcome.CANCELLED);
}
```

- [ ] **Step 2: 运行测试确认红灯**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=JdbcConversationStoreContractTest test`

Expected: FAIL，因为 `JdbcConversationStore` 不存在。

- [ ] **Step 3: 实现最小 JDBC 存储**

`create` 使用 `INSERT ... ON CONFLICT DO NOTHING`，冲突后读取原快照。pending 写入使用如下条件更新：

```sql
UPDATE conversation_session
SET pending_intent = ?, pending_field = ?, expires_at = ?, revision = revision + 1
WHERE session_id = ? AND status = 'ACTIVE' AND revision = ? AND expires_at > ?
RETURNING session_id, status, pending_intent, pending_field, expires_at, revision
```

`clearPending` 额外限制 `pending_intent IS NOT NULL`；首次取消设为 `CANCELLED`、清空待补字段并递增版本。未命中时读取当前快照并按 `EXPIRED`、`CANCELLED`、`STALE_WRITE` 映射；清理运行 `DELETE FROM conversation_session WHERE expires_at <= ?`。在条件更新处以中文注释说明：检查与写入必须同一 SQL，否则取消和晚到写入间会出现会话复活窗口。

- [ ] **Step 4: 运行 JDBC 契约和内存回归**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ConversationStoreContractTest,JdbcConversationStoreContractTest test`

Expected: PASS，JDBC 通过 S01–S11，内存实现无改动。

- [ ] **Step 5: 提交 JDBC 存储与测试**

```powershell
git add knowledge/src/main/java/com/zhku/knowledge/conversation/JdbcConversationStore.java knowledge/src/test/java/com/zhku/knowledge/conversation/JdbcConversationStoreContractTest.java
git commit -m "feat(zhku): 实现JDBC会话存储"
```

### Task 3: 显式存储选择工厂

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/ConversationStoreFactory.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/conversation/ConversationStoreFactoryTest.java`

**Interfaces:**
- Consumes: `InMemoryConversationStore`、`JdbcConversationStore`。
- Produces: `ConversationStoreFactory.create(String configuredStore, DataSource dataSource)`。

- [ ] **Step 1: 写失败的工厂测试**

```java
assertThat(ConversationStoreFactory.create(null, dataSource))
        .isInstanceOf(InMemoryConversationStore.class);
assertThat(ConversationStoreFactory.create("jdbc", dataSource))
        .isInstanceOf(JdbcConversationStore.class);
assertThatIllegalArgumentException().isThrownBy(() ->
        ConversationStoreFactory.create("redis", dataSource));
```

- [ ] **Step 2: 运行测试确认红灯**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ConversationStoreFactoryTest test`

Expected: FAIL，因为工厂不存在。

- [ ] **Step 3: 实现工厂**

```java
public static ConversationStore create(String configuredStore, DataSource dataSource) {
    if (configuredStore == null || configuredStore.isBlank()) return new InMemoryConversationStore();
    if ("jdbc".equals(configuredStore)) return new JdbcConversationStore(Objects.requireNonNull(dataSource));
    throw new IllegalArgumentException("不支持的 zhku.conversation.store: " + configuredStore);
}
```

Javadoc 说明该参数对应未来的 `zhku.conversation.store`，而运行时 Bean 接线必须另行登记、独立提交。

- [ ] **Step 4: 运行工厂与会话回归测试并提交**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ConversationStoreFactoryTest,ConversationStoreContractTest,JdbcConversationStoreContractTest test`

Expected: PASS。

```powershell
git add knowledge/src/main/java/com/zhku/knowledge/conversation/ConversationStoreFactory.java knowledge/src/test/java/com/zhku/knowledge/conversation/ConversationStoreFactoryTest.java
git commit -m "feat(zhku): 增加会话存储显式选择"
```

### Task 4: 证据账本、全量验证与协作收工

**Files:**
- Create: `docs/evidence/2026-09-17-JDBC会话持久化-证据账本.md`
- Modify: `docs/plans/agent-sync/SYNC-STATUS.md`

**Interfaces:**
- Consumes: Tasks 1–3 的测试结果。
- Produces: 可复核交付证据和更新后的 CX-1 状态。

- [ ] **Step 1: 写证据账本**

记录 V12 表/约束/索引、条件更新并发边界、默认内存/显式 `jdbc`、S01–S11、执行环境，以及不含 `ZhkuLocalPreviewConfiguration` 接线和后续需先登记占用的边界。

- [ ] **Step 2: 运行全量知识模块验证**

Run: `./mvnw.cmd -f knowledge/pom.xml "-Dzhku.db.password=<从环境变量读取>" test`

Expected: PASS；若密码或数据库不可用，账本记录具体失败步骤，绝不把失败说成通过。

- [ ] **Step 3: 更新状态板、精确提交与引用检查**

将 CX-1 改为“待合并”、记录最新提交并清除本任务占用；仅暂存两个本任务文档。

```powershell
git add docs/evidence/2026-09-17-JDBC会话持久化-证据账本.md docs/plans/agent-sync/SYNC-STATUS.md
git status --porcelain=v1
git commit -m "docs(zhku): 记录JDBC会话持久化证据"
git for-each-ref refs/heads
```

## Self-Review

- Spec coverage：Task 1 对应数据模型；Task 2 对应 JDBC 原子更新、到期与 S01–S11；Task 3 对应显式 `jdbc` 和默认内存；Task 4 对应证据、全量验证、收工登记。
- 占位语扫描：无未完成占位语或含糊步骤；配置接线被明确排除。
- Type consistency：新工厂签名固定为 `create(String, DataSource)`，JDBC 构造器固定为 `JdbcConversationStore(DataSource)`，其余使用既有会话领域类型。
