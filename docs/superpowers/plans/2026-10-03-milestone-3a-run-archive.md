# Milestone 3A Run Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在明确开启归档的单人本机预览中，保存校园 Run 的准入元数据与最终结局，查询保留期内历史，并将旧进程未归档的结局明确标为中断。

**Architecture:** 只在校园 Manager 的准入与结果发布边界接线。knowledge 内新增窄归档接口、JDBC 适配、owner 排他与领域历史门禁；共享 Runtime 和两条业务循环保持不变。归档与执行终态分离，不自动续跑。

**Tech Stack:** 现有 Java 21、Spring Boot 3.4.4、Spring AI 1.0.9、JDBC、PostgreSQL 16、Flyway、JUnit/AssertJ/MockMvc、PowerShell；不增加依赖。

**Spec:** [已确认的技术设计](../specs/2026-10-03-milestone-3a-run-archive-design.md)。实施者必须读设计和本计划，不能只读任务片段。

日期：2026-10-03，Asia/Shanghai。状态：**实施计划，待审阅及选择执行方式；没有实施代码，没有运行本轮测试**。用户已确认的关闭修订已同步写回 Spec：关闭准入、有界排空，宿主主动停止另立后续修复。

## Global Constraints

- 只接校园 Manager；不改 `agent-runtime/`、`agent/`、校园 Service 主循环、政策链、默认 Session、旧迁移、框架或依赖。
- 不新增业务、多用户认证、真实模型、MCP、多 Agent、长期记忆、自动重放、分布式调度或 exactly-once 承诺。
- 开关 `zhku.campus-agent.archive.enabled` 默认 false；开启必须显式提供数据库 URL，不能默认写演示库。
- 状态为 ADMITTED、FINALIZED、INTERRUPTED；历史没有 terminated。正常线上成功响应仍为原九字段。
- 准入/最终写/历史读取等待上限 2 秒（含排队），启动 5 秒，关闭排空 5 秒；后者不是任务或整个 JVM 强制退出保证。
- 写线程 1、写队列 16、历史同时在途最多 4、锁探针间隔 1 秒；失败不无限增线程或自动恢复准入。
- 默认保留 7 天、最大 10,000 条、清理批量 200、payload 最多 16 KiB UTF-8；只删到期关闭记录，容量仍满则拒绝。
- 不保存原问题、课程输入、Prompt、模型消息、工具原始参数/结果、steps 明细、密钥或异常原文。
- 不借用 USER_REQUEST/RUN_TIMEOUT 表示宿主关闭/失锁；已有任务保持原用户取消/超时路径，未归档的遗留运行重启对账。
- 数据库验收只用无卷专用临时库；原脚本 55432，本阶段脚本 55433，拒绝 54329。操作前重新检查本次所需环境凭据，值不进入 argv/日志/报告。
- 本阶段保持未提交、未推送。任务中的“检查点”是增量差异/哈希/测试记录，不执行 git add/commit，也不整体 reset/clean。
- 关键边界写简明中文注释，解释为何提交未知不能重放、为何历史没有 terminated、为何正文要重新授权。

## Review Focus

1. SQL CHECK 的 NULL 三值逻辑可能让残缺 FINALIZED 被插入：Task 3 用真实非法 INSERT 固定此边界。
2. 准入 commit 成功但返回前关闭门禁：Task 7 保证不投递、不泄漏占位，历史记录保留待对账。
3. 中文多字节、未知 JSON/schema 或 null sourceId：Task 5/8 按字节限制并关闭正文展示，不能按字符数或最新来源误授权。
4. Future 已取消但 action 未退出、工作线程带 interrupt：Task 7 验证真实清理/发布顺序和中断位恢复，Future.cancel 不当作已停止执行。
5. owner 会话丢失但旧工具忽略中断、关闭后晚归档：Task 4/7/10 验证禁写与 epoch fencing，不伪造取消原因，不重建存储资源。

## 文件布局与交付批次

本计划中的路径均相对于仓库根目录 `D:\alldesk\Projects\ZhkuAgent`。以下“新增”均为实施时创建，本轮只创建此计划。

| 批次 | 任务 | 独立审查产物 |
| --- | --- | --- |
| A | 1–3 | 基线、临时库保护、归档契约、迁移约束；不开启生产接线 |
| B | 4–6 | owner、领域门禁、有界 JDBC 存储与真库竞争；不开启生产接线 |
| C | 7 | Manager 的准入/最终发布接线与取消、超时、关闭回归 |
| D | 8–9 | 独立只读历史 API、默认关闭配置、销毁顺序 |
| E | 10–11 | 两个实际 JVM 的故障/重启验收、全量本阶段证据与 README |

生产新文件集中在 `knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/`：`CampusRunArchive.java`（嵌套数据类型/固定错误）、`CampusRunArchiveStore.java`（唯一新存储接口）、`CampusRunArchiveOwner.java`（排他/epoch/健康）、`CampusRunHistoryVisibility.java`（来源门禁/载荷白名单）、`JdbcCampusRunArchiveStore.java`（事务/预算/读写）、`CampusRunHistoryService.java`（历史 DTO/游标）。web 新建 `CampusAgentHistoryPreviewController.java`、`CampusRunArchiveConfiguration.java`；只改原 Manager、原校园 Controller 与校园配置的必要边界。

测试新增 archive 子包内契约、结构、owner、visibility、store、budget、history service、process restart 测试；Manager 组合测试放原 campusagent 包，以沿用包级测试构造器。测试辅助只有临时库入口、JDBC 故障代理与 child-JVM probe；不增加生产测试钩子。

## 命令约定与失败判据

以下是实施时命令，**计划编写没有执行**。PowerShell 在仓库根运行。

```powershell
$m3aRepo = Join-Path $env:USERPROFILE '.m2/repository'
# 无数据库的选定测试；每个 Task 给出 $m3aTests 的实际类名。
& .\mvnw.cmd "-Dmaven.repo.local=$m3aRepo" -pl knowledge -am `
  "-Dtest=$m3aTests" '-Dsurefire.failIfNoSpecifiedTests=false' test
if ($LASTEXITCODE -ne 0) { throw 'M3A 所选测试未通过' }

# 新真库 runner 创建后使用；$m3aTests 仅允许逗号分隔类名。
& .\knowledge\scripts\verify-campus-run-archive-temp-db.ps1 -Port 55433 -Tests $m3aTests
```

RED 必须是目标断言失败；编译/环境失败可以记录，但不算已经证明行为缺陷。创建类型时先记录缺失类型的编译失败，再让编译成功、目标断言 RED，随后实现边界并 GREEN。每批只重跑本批受影响用例；最终再跑原回归与原真库。

---

## Task 1：保存基线并准备受控临时库测试入口

**Files:**
- Create: `knowledge/scripts/verify-campus-run-archive-temp-db.ps1`
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/ArchiveTestDatabase.java`
- Create during implementation: `docs/evidence/milestone-3a/00-基线.md`、`00-文件基线.csv`、`00-历史测试来源.csv`、`before/`、`logs/`
- Read only: `knowledge/scripts/verify-campus-temp-db.ps1`、`docs/evidence/milestone-2/verify.ps1`

**Interfaces:**
- Produces runner parameters `-Port 55433 -Tests <CSV> -MavenRepo <path>`；缺省 Tests=`KnowledgeSchemaTest`。
- Produces `ArchiveTestDatabase.dataSource(): DataSource`、`ArchiveTestDatabase.migrate(): void`；没有目标或凭据时拒绝，不回退演示库。

- [ ] 保存 HEAD、status、Spec 哈希、受影响原文件逐文件备份/哈希，并记录工作区已有前序修改。前序 2.6 的 188/11/8 标为历史结果；复验输出另存 `logs/baseline-regression.log`。

```powershell
& .\docs\evidence\milestone-2\verify.ps1 *>&1 |
  Tee-Object -FilePath .\docs\evidence\milestone-3a\logs\baseline-regression.log
# verify.ps1 会检查底层 Maven 退出码；同时保存本轮 surefire XML，不能读取旧 XML 冒充新结果。
```

- [ ] 新 runner 沿用 2.6 已验证的秘密按名称继承逻辑：随机密码仅进当前环境，Docker `--env 'POSTGRES_PASSWORD'`；数据库名固定 `zhkuagent_run_archive_e2e`，镜像仍 `pgvector/pgvector:0.8.6-pg16-bookworm`，无卷，随机容器名，绑定回环。整个脚本一个 try/finally，关闭本次子进程/容器并恢复所有被修改环境变量；原未定义变量使用 Remove-Item 恢复，不输出环境字典。

```powershell
param([int]$Port = 55433,
      [string]$Tests = 'KnowledgeSchemaTest',
      [string]$MavenRepo = (Join-Path $env:USERPROFILE '.m2/repository'))
$ErrorActionPreference = 'Stop'
if ($Port -lt 1024 -or $Port -gt 65535 -or $Port -eq 54329) {
    throw '拒绝演示库或无效临时端口'
}
if ($Tests -notmatch '^[A-Za-z][A-Za-z0-9]*(,[A-Za-z][A-Za-z0-9]*)*$' -or
    $Tests -match 'LiveProvider') { throw '只允许明确的非真实模型测试类名' }
# 在创建库后以数组传参；不把密码写成 -D 属性。
$m3aArguments = @("-Dmaven.repo.local=$MavenRepo", '-pl', 'knowledge', '-am',
    "-Dtest=$Tests", '-Dsurefire.failIfNoSpecifiedTests=false',
    "-Dzhku.db.url=jdbc:postgresql://127.0.0.1:$Port/zhkuagent_run_archive_e2e",
    '-Dzhku.db.user=zhkuagent', 'test')
```

辅助 Java 的目标保护如下；migrate 使用现有 `ZhkuFlywayTestSupport`，密码每次调用从当前进程重新读取，不做静态密钥缓存。

```java
public static javax.sql.DataSource dataSource() {
    String url = System.getProperty("zhku.db.url", System.getenv("ZHKU_DB_URL"));
    if (url == null) throw new IllegalArgumentException("缺少专用临时库地址");
    if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException("拒绝非专用临时库");
    java.net.URI target = java.net.URI.create(url.substring("jdbc:".length()));
    if (!"127.0.0.1".equals(target.getHost()) || target.getPort() < 1024 ||
        target.getPort() > 65535 || target.getPort() == 54329 ||
        !"/zhkuagent_run_archive_e2e".equals(target.getPath()) ||
        target.getUserInfo() != null || target.getQuery() != null) {
        throw new IllegalArgumentException("拒绝非专用临时库");
    }
    String password = com.zhku.knowledge.ZhkuDatabaseTestConfig.databasePassword();
    if (password == null || password.isBlank()) throw new IllegalArgumentException("缺少本次数据库凭据");
    return new org.springframework.jdbc.datasource.DriverManagerDataSource(url,
        System.getProperty("zhku.db.user", "zhkuagent"), password);
}
public static void migrate() {
    var validated = (org.springframework.jdbc.datasource.DriverManagerDataSource)dataSource();
    com.zhku.knowledge.ZhkuFlywayTestSupport.migrate(validated.getUrl(),
        validated.getUsername(), com.zhku.knowledge.ZhkuDatabaseTestConfig.databasePassword());
}
```

辅助类声明 public，只有 src/test 使用；web 配置真库测试能导入它，但生产代码不能依赖它。

- [ ] 运行端口 54329、1023、65536 和非法 Tests 的保护检查，要求创建容器次数为零。再运行新 runner 的 `KnowledgeSchemaTest`，迁移仍为当前 V17 及以前；记录目标 Flyway 最大版本，源码和新目标均为 17 才进入 Task 3。出现不同编号、非整数版本或共享库编号冲突就停止报告，不自行改旧迁移。
- [ ] runner 的 Tests 额外限定为 KnowledgeSchemaTest 及本计划 Task 11 的七个新真库类，不能仅靠名称不含 LiveProvider 判断安全。每次运行记录开始时间，检查 knowledge 下每个所选类都有本轮更新的 XML、tests>0、failures/errors=0；关键用例不能 skipped。Maven reactor 的 failIfNoSpecifiedTests=false 仅用于无该测试的依赖模块，不能允许知识模块零测试“通过”。
- [ ] 记录本批命令、退出码、容器无残留及环境恢复。不要修改 2.6 原脚本或验收材料。

## Task 2：冻结归档数据类型与唯一存储接口

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/CampusRunArchive.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveStore.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveContractTest.java`

**Interfaces:** 全部数据类型放 CampusRunArchive 的嵌套 public 类型，避免新增类型散落。名称和后续调用固定为：

```java
enum ArchiveStatus { ADMITTED, FINALIZED, INTERRUPTED }
enum CleanupOutcome { SUCCESS, FAILED }
enum PayloadDisposition { STORED, NOT_APPLICABLE, SUPPRESSED }
enum VisibilityStatus { VISIBLE, HIDDEN, NOT_STORED }
enum Reason { NONE, NO_RESULT_FOR_OUTCOME, SOURCE_REFERENCE_MISSING,
    SOURCE_UNAVAILABLE, SOURCE_VERSION_CHANGED, SOURCE_NOT_VISIBLE,
    PAYLOAD_TOO_LARGE, UNSUPPORTED_SCHEMA }
enum Finalization { CONFIRMED, UNCONFIRMED, OWNER_LOST }
enum Code { ARCHIVE_UNAVAILABLE, ARCHIVE_ADMISSION_UNCONFIRMED, ARCHIVE_BUSY,
    ARCHIVE_NOT_READY, ARCHIVE_CAPACITY_EXCEEDED, ARCHIVE_FINALIZATION_UNCONFIRMED,
    ARCHIVE_OWNER_LOST, ARCHIVE_READ_UNAVAILABLE, ARCHIVE_NOT_FOUND, INVALID_REQUEST }
record OwnerToken(java.util.UUID instanceId, long epoch) { }
record Admission(java.util.UUID runId, OwnerToken owner, long revision) { }
record Cursor(java.time.Instant admittedAt, java.util.UUID runId) { }
record Visibility(VisibilityStatus status, Reason reason) { }
record PayloadCandidate(String answer,
    com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer source) { }
record Encoded(PayloadDisposition disposition, Reason reason, byte[] bytes) { }
record FinalFacts(java.util.UUID runId,
    com.zhku.agent.agent.runtime.AgentRunState state,
    com.zhku.agent.agent.runtime.AgentStopReason stopReason,
    com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason endReason,
    int currentStep,
    com.zhku.agent.agent.runtime.contract.AgentRuntimeError.Category errorCategory,
    String errorCode, String businessStatus, String failureCategory,
    CleanupOutcome cleanupOutcome, java.time.Instant observedAt) { }
record Limits(java.time.Duration operation, java.time.Duration startup,
    java.time.Duration drain, java.time.Duration retention, int maxRecords) {
    public static Limits defaults() {
        return new Limits(java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(5),
            java.time.Duration.ofSeconds(5), java.time.Duration.ofDays(7), 10_000);
    }
}
record Read(CampusRunArchive archive, Visibility visibility, PayloadCandidate result) { }
record Page(java.util.List<CampusRunArchive> items, Cursor nextCursor) { }
```

外层 `CampusRunArchive` record 固定字段：`UUID runId, int schemaVersion, String writerVersion, ArchiveStatus archiveStatus, OwnerToken owner, long revision, Instant admittedAt, Instant executionFinishedAt, Instant closedAt, Instant retainedUntil, FinalFacts execution, PayloadDisposition payloadDisposition, Reason payloadReason, String interruptionReason`。它不携带原始问题、steps、未授权正文或 terminated；未知 schema 可保留元数据、execution 为空。

`Failure extends RuntimeException` 作为嵌套类型，仅保存 Code；`code(): Code`、`safeMessage(): String` 来自固定映射，不附带 SQLException cause。writerVersion 固定 `milestone-3a-preview`，不假装代表未提交工作区的准确版本。

```java
public interface CampusRunArchiveStore extends AutoCloseable {
    boolean isAccepting();
    CampusRunArchive.Admission admit(java.util.UUID runId);
    CampusRunArchive.Finalization finalizeRun(CampusRunArchive.Admission admission,
        CampusRunArchive.FinalFacts facts, CampusRunArchive.PayloadCandidate candidate);
    java.util.Optional<CampusRunArchive.Read> find(java.util.UUID runId);
    CampusRunArchive.Page list(int limit, CampusRunArchive.Cursor cursor);
    void stopAdmission();
    void sealWrites();
    void close(java.time.Duration remainingBudget);
    @Override default void close() { close(java.time.Duration.ZERO); }
}
```

- [ ] 写契约失败用例：非终态 FinalFacts、SUCCEEDED 带 error、CANCELLED 无 stopReason、负步数/epoch/revision、null ID、无效时限/容量；序列化 Read 不出现 terminated/prompt/steps。示例目标断言：

```java
@Test void historicalInterruptedHasNoExecutionTerminationClaim() throws Exception {
    var id = java.util.UUID.randomUUID();
    var time = java.time.Instant.EPOCH;
    var record = new CampusRunArchive(id, 1, "milestone-3a-preview",
        CampusRunArchive.ArchiveStatus.INTERRUPTED,
        new CampusRunArchive.OwnerToken(java.util.UUID.randomUUID(), 1), 1,
        time, null, time, time.plusSeconds(604800), null, null, null,
        "PREVIOUS_OWNER_RESULT_UNCONFIRMED");
    var json = new com.fasterxml.jackson.databind.ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        .writeValueAsString(record);
    assertThat(json).doesNotContain("terminated", "USER_REQUEST", "CANCELLED", "steps");
    assertThat(record.execution()).isNull();
}
```

- [ ] 选择 `CampusRunArchiveContractTest`，按前述无库命令记录 RED。
- [ ] 实现构造校验与固定错误映射。FinalFacts 用共享 `AgentRunSnapshot(runId,state,stopReason,currentStep,false,endReason,error)` 校验语义；error 通过 `AgentRuntimeError.of(category,code)` 组成，不复制供应商消息。元数据归档状态的校验单独做，未知 schema 只读元数据不套用当前结果解码。
- [ ] 重跑契约 GREEN；保存本批增量和逐文件哈希。Limits 的生产配置接受 retention-days 1–30、max-records 1–10,000，等待预算均正数且不超过规格上限；测试可注入更短等待。

## Task 3：真实迁移约束与关闭记录的原子结构

**Files:**
- Create only after Task 1 version gate: `knowledge/src/main/resources/db/migration/V18__create_campus_agent_run_archive.sql`
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveSchemaIntegrationTest.java`

**Interfaces:** 两张表的列名完全采用 Spec §5。owner_key 固定 `campus-credit-preview`。所有写入入口以后统一先锁该行，再锁 Run 行。

- [ ] 在专用库写真实结构 RED：表存在、所有列存在、索引存在；直接 INSERT FINALIZED 且缺 state/closed_at/cleanup，必须抛 SQLSTATE 23514；ADMITTED 带正文/终态亦拒绝。用 JDBC 元数据与执行结果检查，不用字符串包含迁移文本替代数据库验证。

```java
@Test void finalizedWithoutExecutionFactsIsRejectedByDatabase() throws Exception {
    ArchiveTestDatabase.migrate();
    try (var c = ArchiveTestDatabase.dataSource().getConnection();
         var p = c.prepareStatement("""
            INSERT INTO campus_agent_run_archive
                (run_id,owner_instance_id,owner_epoch,task_kind,archive_status,
                 schema_version,writer_version)
            VALUES (?, ?, 1, 'CAMPUS_CREDIT_SUMMARY', 'FINALIZED', 1, 'schema-test')
            """)) {
        p.setObject(1, java.util.UUID.randomUUID());
        p.setObject(2, java.util.UUID.randomUUID());
        assertThatThrownBy(p::executeUpdate).isInstanceOf(java.sql.SQLException.class)
            .extracting(e -> ((java.sql.SQLException)e).getSQLState()).isEqualTo("23514");
    }
}
```

- [ ] 用新 runner 选择 `CampusRunArchiveSchemaIntegrationTest`，确认 RED 不是凭据/库地址问题。新增类型编译成功后再证明断言失败。
- [ ] 创建迁移。字段约束：UUID 主键；epoch/revision/current_step 非负；schema_version>0；writer_version varchar(64)；状态/固定任务种类/cleanup/payload disposition 用 CHECK；JSON 使用 json 而不是 jsonb，按原序列化文本限制 `octet_length(result_payload::text) <= 16384`；字段 nullable 与分支 CHECK 显式 `IS NULL/IS NOT NULL`，不得依赖 NULL 比较通过。

核心状态约束写为以下分支，再补全 Spec 中各最终字段的空值限制：

```sql
CHECK (
  (archive_status = 'ADMITTED'
   AND execution_state IS NULL AND stop_reason IS NULL AND end_reason IS NULL
   AND current_step IS NULL AND error_category IS NULL AND error_code IS NULL
   AND business_status IS NULL AND failure_category IS NULL AND cleanup_outcome IS NULL
   AND execution_finished_at IS NULL AND closed_at IS NULL AND retained_until IS NULL
   AND result_payload IS NULL AND payload_disposition IS NULL AND payload_reason IS NULL
   AND interruption_reason IS NULL)
  OR
  (archive_status = 'FINALIZED'
   AND execution_state IS NOT NULL
   AND execution_state IN ('SUCCEEDED','INCOMPLETE','FAILED','CANCELLED')
   AND stop_reason IS NOT NULL AND end_reason IS NOT NULL AND current_step IS NOT NULL
   AND failure_category IS NOT NULL AND cleanup_outcome IS NOT NULL
   AND execution_finished_at IS NOT NULL AND closed_at IS NOT NULL
   AND retained_until IS NOT NULL AND retained_until > closed_at
   AND payload_disposition IS NOT NULL AND interruption_reason IS NULL)
  OR
  (archive_status = 'INTERRUPTED'
   AND execution_state IS NULL AND stop_reason IS NULL AND end_reason IS NULL
   AND current_step IS NULL AND error_category IS NULL AND error_code IS NULL
   AND business_status IS NULL AND failure_category IS NULL AND cleanup_outcome IS NULL
   AND execution_finished_at IS NULL AND closed_at IS NOT NULL AND retained_until IS NOT NULL
   AND retained_until > closed_at AND result_payload IS NULL
   AND payload_disposition IS NULL AND payload_reason IS NULL
   AND interruption_reason IS NOT NULL
   AND interruption_reason = 'PREVIOUS_OWNER_RESULT_UNCONFIRMED')
)
```

STORED 必须非空 JSON 且 payload_reason=NONE；SUPPRESSED 必须正文为空、固定抑制原因非空；NOT_APPLICABLE 必须正文为空、NO_RESULT_FOR_OUTCOME。错误 category/code 同时空或同时非空；FAILED 必须有错误，其他执行终态遵循共享构造校验。除 FINALIZED 外不带 RuntimeError。

建立分页、到期清理、旧 owner ADMITTED 三个索引；不对 knowledge_source 建级联删除关系，不修改任何旧 migration。
- [ ] 迁移 GREEN，再重复迁移验证无额外变化；保存实际库版本/列/索引/非法 INSERT 结果。原 KnowledgeSchemaTest 在本临时库通过，不把结构验证算业务通过。

## Task 4：owner 排他、epoch 接管与对账

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveOwner.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveOwnerIntegrationTest.java`

**Interfaces:**
类型实现 AutoCloseable；`CampusRunArchiveOwner(DataSource, Limits)`；`start(): OwnerToken`；`token(): OwnerToken`；`isAccepting(): boolean`；`mayWrite(): boolean`；`pause(): void`（不再准入，但可尝试收尾）；`fence(): void`（也不再写）；`close(): void`（关闭幂等，释放连接不透明重连）。连接设置/关闭预算由本类型与 Store 协作。

- [ ] 写真实双 owner RED：第一个取得锁，第二个 start 固定 ARCHIVE_OWNER_LOST 且 epoch 不变化；关闭后新 owner epoch 增加；旧 ADMITTED 变 INTERRUPTED，原 FINALIZED 不动。

```java
@Test void competingOwnerCannotReconcileWhileFirstSessionHoldsLock() {
    try (var first = new CampusRunArchiveOwner(ArchiveTestDatabase.dataSource(),
             CampusRunArchive.Limits.defaults());
         var second = new CampusRunArchiveOwner(ArchiveTestDatabase.dataSource(),
             CampusRunArchive.Limits.defaults())) {
        var token = first.start();
        assertThatThrownBy(second::start).isInstanceOf(CampusRunArchive.Failure.class);
        assertThat(first.token()).isEqualTo(token);
        assertThat(first.isAccepting()).isTrue();
    }
}
```

- [ ] 选择 `CampusRunArchiveOwnerIntegrationTest`，记录 RED。
- [ ] 专用非池化锁连接用 `pg_try_advisory_lock(?,?)`，固定两个 Java int 常量 `0x5A484B55`、`0x43524152`，锁范围为本数据库。独立写连接 startup 事务执行：

```sql
INSERT INTO campus_agent_runtime_owner(owner_key,epoch,instance_id,claimed_at)
VALUES ('campus-credit-preview',0,?,clock_timestamp()) ON CONFLICT DO NOTHING;
SELECT epoch, instance_id FROM campus_agent_runtime_owner
WHERE owner_key='campus-credit-preview' FOR UPDATE;
UPDATE campus_agent_runtime_owner SET epoch=epoch+1,instance_id=?,claimed_at=clock_timestamp()
WHERE owner_key='campus-credit-preview' RETURNING epoch;
UPDATE campus_agent_run_archive SET archive_status='INTERRUPTED',revision=revision+1,
    closed_at=clock_timestamp(),
    retained_until=clock_timestamp() + (? * interval '1 second'),
    interruption_reason='PREVIOUS_OWNER_RESULT_UNCONFIRMED'
WHERE archive_status='ADMITTED' AND owner_epoch < ?;
```

接管 commit 确认前不置 READY。异常/epoch 溢出释放锁并拒绝启动。对账不会 new Context，不调用业务/清理/工具。

探针不重取锁（防累计重入），核查当前 session 在 pg_locks 持有对应 advisory lock；独立小监控线程每 1 秒执行、连接读超时 2 秒。失锁只 fence，不伪造 stopReason，不触发自动接管。
- [ ] GREEN 并验证：切断锁连接、探针超时、旧 token 丢失、关闭重复调用、接管失败。可用临时库 `pg_terminate_backend` 终止**测试记录的特定 backend PID**，不得按模糊连接名称终止其他连接。
- [ ] 记录对账前后元数据、实例 UUID、epoch、特定 PID 与测试日志；不输出连接凭据。

## Task 5：knowledge 内的正文白名单与来源门禁

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/CampusRunHistoryVisibility.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunHistoryVisibilityIntegrationTest.java`

**Interfaces:** `evaluate(Connection, PayloadCandidate): Visibility`；`encode(PayloadCandidate): Encoded`（固定白名单 JSON；超限返回 SUPPRESSED/PAYLOAD_TOO_LARGE/bytes=null，不产生未定义的 Failure Code）；`decode(byte[]): PayloadCandidate`（不启用 polymorphic/default typing）。连接由调用者提供，门禁不自行打开另一快照。

- [ ] RED：null sourceId、来源缺失、同 ID 改版本、内部可见/ARCHIVED/分享撤回、locator/scope 不匹配、中文 payload 超 16KiB。来源按真实 JDBC 数据改变，不能用 gateway 替身冒充撤回验证。

示例源写入使用专用库合成资料：title=`合成学分规则`、source_type=`PDF`、source_version=`synthetic-v1`、checksum=`0`.repeat(64)、族/范围为既有 STUDENT_HANDBOOK/PUBLIC_ELECTIVE_CREDIT_LIMIT，已发布/学生可见/分享确认；chunk locator=`合成第1条`、content 为既有 4 学分短语。source、chunk ID 使用本测试独有 UUID，finally 删除本测试资料；不更新原演示资料。

```java
@Test void missingSourceIdCannotAuthorizeHistoricalText() throws Exception {
    var source = new com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer(
        "每学期公共选修课最高不得超过 4 学分", "仅合成验证", null,
        "合成学分规则", "synthetic-v1", "合成第1条", "合成本科生");
    try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
        var actual = new CampusRunHistoryVisibility().evaluate(c,
            new CampusRunArchive.PayloadCandidate("当时的合成结果", source));
        assertThat(actual.status()).isEqualTo(CampusRunArchive.VisibilityStatus.HIDDEN);
        assertThat(actual.reason()).isEqualTo(CampusRunArchive.Reason.SOURCE_REFERENCE_MISSING);
    }
}
```

- [ ] 选择 `CampusRunHistoryVisibilityIntegrationTest`，记录 RED。
- [ ] 门禁查询按原 sourceId，不调用最新 find。单次读取所有门禁字段，检查版本、公开状态、族/范围、locator/适用范围：

```sql
SELECT source.source_version,source.publication_status,source.visibility_scope,
       source.sharing_rights_confirmed,source.document_family,source.knowledge_scope,
       source.publication_scope,
       EXISTS(SELECT 1 FROM knowledge_chunk chunk
              WHERE chunk.source_id=source.id AND chunk.source_locator=?) AS locator_exists
FROM knowledge_source source WHERE source.id=?;
```

未知 source/version/locator/scope 一律隐藏正文；查询失败抛固定读失败，不返回授权通过。字段按 UTF-8 字节限制：answer≤8192、conclusion/basis 各≤2048、title/locator/scope 各≤512、version≤128，整体 JSON≤16384。encode(null) 返回 NOT_APPLICABLE/NO_RESULT_FOR_OUTCOME/null；合法载荷返回 STORED/NONE/bytes；任一字段或整体超限返回 SUPPRESSED/PAYLOAD_TOO_LARGE/null，不截断。非 SUCCEEDED+ANSWERED 候选为空，不保存任意澄清或异常消息。
- [ ] GREEN；测试同 ID 同 version 改正文不被宣称不可变证据，history provenance 永远 REFERENCE_ONLY。不在此阶段新增 provenance 链或修改资料发布规则。

## Task 6：有界 JDBC 原子读写、配额与过期清理

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/JdbcCampusRunArchiveStore.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveStoreIntegrationTest.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveBudgetTest.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/ArchiveJdbcFaults.java`

**Interfaces:** `JdbcCampusRunArchiveStore(DataSource, CampusRunArchiveOwner, Limits, CampusRunHistoryVisibility)` 实现 Task 2 的全部 Store 方法；owner 已 start。无自动连接重试，无后台自动重新准入。故障代理仅 src/test 使用。

- [ ] RED 真库用例：admit 返回 revision0/同 owner；final 全部字段一起可读；final 二次写拒绝覆盖；配额最后一席并发无超卖；ADMITTED 不过期，关闭且未到期不被压力删，到期清理最多 200。
- [ ] RED 故障与预算：事务 commit 前失败、真实 commit 后 throw SQLTransientConnectionException、写排队超时、队列满、read 并发第五项拒绝、generation 失效后排队任务不写、不无限开线程。测试代理的 commit 行为明确为：

```java
// ArchiveJdbcFaults 的 Connection InvocationHandler 分支；只用于测试。
if (method.getName().equals("commit") && failAfterCommit.compareAndSet(true, false)) {
    delegate.commit();
    throw new java.sql.SQLTransientConnectionException("SYNTHETIC_ACK_LOSS", "08006");
}
```

通过 `java.lang.reflect.Proxy` 包装标准 DataSource/Connection，无新代理框架；其他方法反射委托并解开 InvocationTargetException，close 原连接。用真实数据库查询证明确实提交，不能只检查异常。
- [ ] 选择 `CampusRunArchiveStoreIntegrationTest,CampusRunArchiveBudgetTest`，用新 runner 记录 RED。
- [ ] 按固定顺序实现事务：owner row FOR UPDATE 并验证 token → prune batch → count quota → INSERT ADMITTED；count+INSERT 不分事务。清理 SQL：

```sql
DELETE FROM campus_agent_run_archive
WHERE run_id IN (
  SELECT run_id FROM campus_agent_run_archive
  WHERE archive_status IN ('FINALIZED','INTERRUPTED') AND retained_until <= clock_timestamp()
  ORDER BY retained_until, run_id LIMIT 200
);
```

final 先锁 owner 行校验当前 epoch/instanceId，再门禁/编码候选，再一次 UPDATE 绑定全部最终列。WHERE 精确为：

```sql
WHERE run_id=? AND archive_status='ADMITTED' AND revision=?
  AND owner_instance_id=? AND owner_epoch=?
```

同一次更新设置 archive_status、revision+1、execution_finished_at、所有执行分类、cleanup_outcome、payload disposition/reason/JSON、closed_at、retained_until。retained_until 从该关闭时间加 retention 得出。零更新返回 OWNER_LOST 并关闭准入，不执行无条件补写。final ACK 未确认时不重试写；若在原总预算内查询同一 ID 发现相同 final，可返回 CONFIRMED，否则 UNCONFIRMED。

find 将读归档和门禁置于同一个只读 REPEATABLE READ 事务，来源不可见只返回无正文的 Read；不一致快照不能拼接。schemaVersion≠1 只返回元数据与 UNSUPPORTED_SCHEMA，不能解码未知正文。list 只取元数据，`limit+1` 判断下一页，以 `(admitted_at,run_id) < (?,?)` 做降序 keyset；ADMITTED 可查，关闭且到期的记录不返回。任何读取失败固定 ARCHIVE_READ_UNAVAILABLE。

I/O 使用 1 写线程+16 队列，4 读线程+SynchronousQueue（不额外排队），单独 owner 监控；每个 job 带 nanoTime deadline、generation。提交、等待、JDBC 网络/语句/锁预算均检查剩余时间。socket/connect/cancel 属性只作用归档数据源：connectTimeout=1、socketTimeout=2、cancelSignalTimeout=1；事务局部 statement_timeout≤1000ms、lock_timeout≤250ms。不能对现有知识仓储数据源全局设置这些属性。

宿主 get 等待到期：关闭准入、使故障 generation 失效，尝试关闭特定连接；不能假设 cancel(true) 回滚了事务。尚未开始或在 commit 前检测到 generation 失效的 job 不发新写。已发出的 commit 可能成功，保持未确认分类；新 owner 由 epoch/final state 防止覆盖。

sealWrites 原子禁写且幂等；close(remainingBudget) 只等待剩余预算，不重开 I/O pool；关闭操作不在 admissionLock 内，资源关闭任务使用已有有界监控资源收敛，不为每次超时新建线程。
- [ ] GREEN 后验证 old final / new epoch 两种事务顺序；父测试用 latch 而不是 sleep 碰运气。负例检查原始 JSON 没有题目、课程列表、工具 arguments、steps、异常原文；每测试只清理独有 ID，owner epoch 不倒退。
- [ ] 保存真实提交/未知回执的区分、线程/连接收敛及到期容量结果。无法有界收敛就停止，不增加新连接池或新数据库。

## Task 7：Manager 准入、最终发布与有界排空

**Files:**
- Modify: `knowledge/src/main/java/com/zhku/knowledge/campusagent/CampusCreditAgentManager.java`，`start`、拒绝分支、构造器及关闭方法
- Modify: `knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/CampusRunArchive.java`，增加 capture 工厂
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/CampusCreditAgentArchiveTest.java`
- Preserve and run: `CampusCreditAgentManagerTest`、`CampusCreditAgentRetentionTest`、`CampusRuntimeIntegrationTest`

**Interfaces:** 原 public 四参数与包级七参数构造器保留；新增 public `(Service, Executor, ScheduledExecutorService, Duration, CampusRunArchiveStore)`，新增包级原七参数加 Store 的八参数构造器。旧构造器传 archive=null，仍无归档。Manager 实现 AutoCloseable，新增 `close(Duration budget)`（0–5 秒）、`close()`（5 秒）；无归档旧模式 close 为 no-op。

`CampusRunArchive.capture(AgentRunContext, CampusCreditAgentService.Result, boolean rejected, Instant observedAt): FinalFacts`；拒绝时 result=null，但共享 error/endReason 有值。ManagedRun 新增 Admission 字段，不给 HTTP Snapshot 增加字段。

- [ ] RED：准入失败/未知时模型执行次数为零、占位释放；final 明确失败/未知/抛出意外 RuntimeException 时仍发布真实结果；正常、INCOMPLETE、FAILED、取消和 timeout 映射保持；timer/worker 拒绝归档无伪造业务 result；final 回调与 Future 回调的时间区别。

下面是独立可实现的“final 异常不能卡死发布”测试，使用已有 starter-test 的 Mockito；不是测试真实 JDBC：

```java
@Test void finalStoreExceptionDoesNotTurnClarificationIntoModelFailure() {
    var store = org.mockito.Mockito.mock(CampusRunArchiveStore.class);
    var accepting = new java.util.concurrent.atomic.AtomicBoolean(true);
    org.mockito.Mockito.when(store.isAccepting()).thenAnswer(i -> accepting.get());
    org.mockito.Mockito.when(store.admit(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(i -> new CampusRunArchive.Admission(i.getArgument(0),
            new CampusRunArchive.OwnerToken(java.util.UUID.randomUUID(), 1), 0));
    org.mockito.Mockito.when(store.finalizeRun(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("SYNTHETIC_PRIVATE_TEXT"));
    org.mockito.Mockito.doAnswer(i -> { accepting.set(false); return null; })
        .when(store).stopAdmission();
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var service = new CampusCreditAgentService(prompt -> {
        calls.incrementAndGet();
        return new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(
            new org.springframework.ai.chat.model.Generation(
                new org.springframework.ai.chat.messages.AssistantMessage("补充课程"))));
    }, new com.zhku.knowledge.tools.CampusToolRegistry(java.util.List.of(
        new com.zhku.knowledge.tools.CourseCreditSummaryTool())),
        q -> { throw new AssertionError("不应查询政策"); }, 3);
    try (var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()) {
        var manager = new CampusCreditAgentManager(service, Runnable::run, timer,
            java.time.Duration.ofSeconds(30), store);
        var result = manager.start(new CampusCreditAgentService.Request("合成课程追问", java.util.List.of()));
        assertThat(result.terminated()).isTrue();
        assertThat(result.state()).isEqualTo(com.zhku.agent.agent.runtime.AgentRunState.INCOMPLETE);
        assertThat(result.failureCategory()).isEqualTo("NONE");
        assertThat(calls.get()).isEqualTo(1);
        org.mockito.Mockito.verify(store).stopAdmission();
    }
}
```

- [ ] 选择 `CampusCreditAgentArchiveTest`，记录目标 RED。
- [ ] 接线准入：原 admissionLock 内只检查/占位；锁外 admit；commit 确认后再次在锁内检查宿主关闭/健康门禁，获准后锁外绑定 Task、安排 timer、提交 worker。若在这次检查前门禁关闭则不投递，finally 移除尚未投递的占位；已提交 DB 行保留待对账。该检查是投递许可的线性化点，后续关门不撤销已获许可任务，不伪造新宿主取消语义。

capture 的关键映射明确为：

```java
var snapshot = context.snapshot();
var error = snapshot.error();
String failure = rejected ? "EXECUTOR_REJECTED" : context.hasCleanupFailure()
    ? "CLEANUP_FAILURE" : result.failureCategory();
return new CampusRunArchive.FinalFacts(snapshot.runId(), snapshot.state(),
    snapshot.stopReason(), snapshot.endReason(), snapshot.currentStep(),
    error == null ? null : error.category(), error == null ? null : error.code(),
    result == null ? null : result.status().name(), failure,
    context.hasCleanupFailure() ? CampusRunArchive.CleanupOutcome.FAILED
        : CampusRunArchive.CleanupOutcome.SUCCESS, observedAt);
```

正常和拒绝分支共用 Manager 私有 `archiveFinal(ManagedRun, Result, boolean): void`。只在 Service 已返回/拒绝清理已收敛之后调用；archive=null 直接返回、不新增旧模式 Clock 调用。候选只来自 runtime SUCCEEDED 且 result.status()==ANSWERED。收尾中断与兜底如下（放在 archiveFinal，变量均来自参数或 Manager 字段）：

```java
boolean restoreInterrupt = Thread.interrupted();
try {
    var facts = CampusRunArchive.capture(managed.context, result, rejected, clock.instant());
    var candidate = !rejected && facts.state() == AgentRunState.SUCCEEDED &&
        result.status() == CampusCreditAgentService.Status.ANSWERED
        ? new CampusRunArchive.PayloadCandidate(result.answer(), result.source()) : null;
    var outcome = archive.finalizeRun(managed.admission, facts, candidate);
    if (outcome != CampusRunArchive.Finalization.CONFIRMED) archive.stopAdmission();
} catch (RuntimeException failure) {
    // 只记录固定归档分类；必须继续原 completion 和最终事件发布。
    archive.stopAdmission();
} finally {
    if (restoreInterrupt) Thread.currentThread().interrupt();
}
```

预期 Store 方法自身遵循预算，不把 stopAdmission 的实现做成会抛出数据库异常的操作。若 Store 缺陷导致自身 wait 不有界，用 Task 6 的 budget 测试定位，不增加重复的 Manager 无限重试。fatal Error/OOM 不伪造正常业务成功。

原 completion volatile 发布、clock 完成时间、原失败分类覆盖及 publishTermination 顺序仍保留；归档尝试在其前。不能在 observer 中可靠写入，不能让 Runtime Future 承担新的归档语义。

close：只对归档模式关闭新准入，锁外按 nanoTime 等待 completion+清理收敛至剩余预算；然后 sealWrites、close(remaining)。不清空活动记录，不取消成 USER_REQUEST，不把关闭当 RUN_TIMEOUT，不重开 Store。关门/失锁后的迟到结果仍可在线发布，但不再归档。
- [ ] GREEN 并补齐真实控制屏障：final 阻塞时清理 Future done、HTTP terminated=false、status=null；release 后最终事件恰好一次、回调读完整结果。回调内保存异常、回调外断言，防观察器异常隔离吞掉测试失败。
- [ ] 用户取消/timeout 测试用真实 blocked model、latch 和原 context 路径；工作线程在 final 归档等待区中断位临时清除、action 完成后恢复；不只手工构造 CANCELLED record。关闭超预算后 seal、晚写不重开资源、停止原因不变。
- [ ] 重跑原 Manager/Retention/RuntimeIntegration 三类及新的 ArchiveTest，保存这一批前后 HTTP 字段集合与事件顺序。

## Task 8：独立只读历史服务与 HTTP 合同

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/CampusRunHistoryService.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/web/CampusAgentHistoryPreviewController.java`
- Modify: `knowledge/src/main/java/com/zhku/knowledge/web/CampusAgentPreviewController.java`，仅新增 Failure 错误映射
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunHistoryServiceTest.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/web/CampusAgentHistoryPreviewControllerTest.java`

**Interfaces:** `CampusRunHistoryService(CampusRunArchiveStore)`；`find(UUID): Detail`；`list(int limit, String cursor): Listing`。Detail 字段严格为 Spec §10，result 使用 PayloadCandidate；execution 投影移除内部 owner/revision/runId/observedAt，provenance=`REFERENCE_ONLY`。Listing 为 `List<Summary> items, String nextCursor`，Summary 只含 runId、schemaVersion、archiveStatus、admittedAt、closedAt、retainedUntil，不含正文、来源、owner。

Controller 构造 `CampusAgentHistoryPreviewController(CampusRunHistoryService)`，映射 `/zhku/local-preview/campus-agent/v1/history`；MockMvc standalone 路径不包含 `/api`，真实外部文档请求包含全局 `/api`。

- [ ] RED：正常 detail、ADMITTED/INTERRUPTED 无 execution、正文隐藏、未知 schema、limit 0/101、坏 cursor、到期/不存在 404、门禁/数据库失败 503、POST/cancel/resume 不存在、旧成功字段九项不变。

```java
@Test void historicalWithdrawalHidesAnswerWithoutChangingPastSuccess() throws Exception {
    var store = org.mockito.Mockito.mock(CampusRunArchiveStore.class);
    var time = java.time.Instant.EPOCH;
    var id = java.util.UUID.randomUUID();
    var facts = new CampusRunArchive.FinalFacts(id,
        com.zhku.agent.agent.runtime.AgentRunState.SUCCEEDED,
        com.zhku.agent.agent.runtime.AgentStopReason.NONE,
        com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason.COMPLETED,
        2,null,null,"ANSWERED","NONE",CampusRunArchive.CleanupOutcome.SUCCESS,time);
    var record = new CampusRunArchive(id,1,"milestone-3a-preview",
        CampusRunArchive.ArchiveStatus.FINALIZED,
        new CampusRunArchive.OwnerToken(java.util.UUID.randomUUID(),1),1,
        time,time,time,time.plusSeconds(604800),facts,
        CampusRunArchive.PayloadDisposition.STORED,CampusRunArchive.Reason.NONE,null);
    org.mockito.Mockito.when(store.find(id)).thenReturn(java.util.Optional.of(
        new CampusRunArchive.Read(record,
            new CampusRunArchive.Visibility(CampusRunArchive.VisibilityStatus.HIDDEN,
                CampusRunArchive.Reason.SOURCE_NOT_VISIBLE),null)));
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
        new CampusAgentHistoryPreviewController(new CampusRunHistoryService(store))).build();
    mvc.perform(get("/zhku/local-preview/campus-agent/v1/history/"+id))
        .andExpect(status().isOk()).andExpect(jsonPath("$.execution.state").value("SUCCEEDED"))
        .andExpect(jsonPath("$.result").doesNotExist())
        .andExpect(jsonPath("$.resultVisibility.status").value("HIDDEN"))
        .andExpect(jsonPath("$.terminated").doesNotExist());
}
```

- [ ] 选择 `CampusRunHistoryServiceTest,CampusAgentHistoryPreviewControllerTest`，记录 RED。
- [ ] 游标格式固定 `Base64URL无填充(UTF-8(Instant.toString + LF + UUID.toString))`，长度≤256、只含 base64url 字符、恰好两段；Instant/UUID 必须严格往返格式，时间限定 PostgreSQL 可表示的公元 1–9999 年。解码错误固定 400 INVALID_REQUEST，不回传原 cursor。limit 默认20，1–100。

find 缺失抛 Failure(ARCHIVE_NOT_FOUND)，history Controller 将其映射404；Failure读失败→503；非法 UUID/limit/cursor→固定400。不可见/未知 schema→200 元数据+result=null。使用 Controller 内异常处理，不改其他校园/会话 endpoint 的全局错误。

原 Controller start 在现有 IllegalArgumentException/RejectedExecutionException 分支之外捕获归档 Failure，并以现有 ErrorReply(code,message) 返回503；原已执行任务快速业务失败仍202，原拒绝仍503快照。find/cancel 不查 Store。
- [ ] GREEN；增加同一 admittedAt 下 UUID 高位跨符号边界的真库分页，确认按 PostgreSQL tuple 排序无重复/漏页，不用 Java UUID 的 signed compare 模拟数据库顺序。门禁一致快照与并发撤回测试回 Task 6 真实连接完成。
- [ ] 重跑原 `CampusAgentPreviewControllerTest`、`CampusRuntimeIntegrationTest`，确认成功结构不变；MockMvc 结果明确标为框架合同，未称真实监听。

## Task 9：默认关闭配置、显式 URL 与销毁顺序

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/web/CampusRunArchiveConfiguration.java`
- Modify: `knowledge/src/main/java/com/zhku/knowledge/web/CampusAgentPreviewConfiguration.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/web/CampusRunArchiveConfigurationTest.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/web/CampusRunArchiveConfigurationIntegrationTest.java`

**Interfaces:** profile=`zhku-local-preview` 且 campus-agent.enabled 与 archive.enabled 同为 true 才注册 owner/store/history。归档配置在自身 Bean 内调用已存在 `LocalKnowledgePublisherFactory.createDataSource(Environment)`，先检查显式 zhku.db.url；不重构全项目 DataSource。

- [ ] RED：profile缺失、campus开关false、archive缺省/false都没有 Store/Owner/历史 Controller；开启却没有URL、使用含密码URL或非法 limits 时拒绝，不连接默认54329。示例无库配置测试只加载新配置，不加载真实 ChatModel 工厂：

```java
@Test void archiveAbsentDoesNotCreateDatabaseResources() {
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withInitializer(c -> c.getEnvironment().setActiveProfiles("zhku-local-preview"))
        .withPropertyValues("zhku.campus-agent.enabled=true")
        .withUserConfiguration(CampusRunArchiveConfiguration.class)
        .run(c -> assertThat(c).doesNotHaveBean(CampusRunArchiveStore.class)
            .doesNotHaveBean(CampusRunArchiveOwner.class));
}
```

- [ ] 无库选择 `CampusRunArchiveConfigurationTest` 记录 RED；真库配置单独由新 runner 选择 IntegrationTest，不能在无库命令里误选它。
- [ ] 配置的条件与最小注入采用：

```java
@org.springframework.context.annotation.Profile("zhku-local-preview")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name={"zhku.campus-agent.enabled","zhku.campus-agent.archive.enabled"},havingValue="true")
@org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
public class CampusRunArchiveConfiguration { }
```

归档 bean 创建时先独立调用现有 `LocalPreviewBindingGuard(environment.getProperty("server.address"))`，再检查环境 URL 非空、无 userinfo/password query、目标明确；不能仅依赖可能被 `zhku.local-preview.enabled=false` 关闭的原 guard Bean。外部绑定/缺少地址必须在连接库之前失败。新增无库测试覆盖这两种输入，继续不修改原 guard 或默认配置。

仅为归档连接附加 Task 6 超时，不修改原工厂。limits 从 `zhku.campus-agent.archive.retention-days`（默认7）、`max-records`（默认10000）读取并验证；固定其他资源预算。无新 YAML 默认值，不开启默认归档。

原校园配置 Manager Bean 增加 `ObjectProvider<CampusRunArchiveStore>` 参数，`getIfAvailable()` 为空时沿用原四参数构造器，有值时用新五参数。Bean destroyMethod 明确 close；Manager 对 Store 的依赖登记确保 Manager 先排空、Store 后销毁。Owner 生命周期由 Store 收敛，不再单独抢先释放 lock；重复关闭幂等。

固定 Store Bean 名为 `zhkuCampusRunArchiveStore`。Manager 工厂在 archive 非空时，通过注入的 `ConfigurableBeanFactory.registerDependentBean("zhkuCampusRunArchiveStore", "zhkuCampusCreditAgentManager")` 明确登记销毁依赖；不要只推测 ObjectProvider 的动态解析一定登记了关系。Owner Bean 禁止自动抢先 destroy，释放由 Store.close 驱动。归档关闭不登记不存在的依赖。

enabled 真库配置测试只装载 archive 配置/显式依赖与测试侧模型，不调用生产 ChatModel；Service/Manager 真实实例通过 Task 7 构造接线。Spring 关闭测试使用记录型依赖捕获 Manager排空→sealWrites→锁释放的实际顺序，不能只检查注解字符串。
- [ ] GREEN；重跑原 preview/binding/configuration 回归，确认 loopback、原 Session store 及校园工具白名单无变化。记录未开启时连接计数0、开启时URL明确、销毁顺序与超限排空行为。

API 使用已核对的 [Spring Boot 3.4 ContextRunner 文档](https://docs.spring.io/spring-boot/3.4/api/java/org/springframework/boot/test/context/runner/AbstractApplicationContextRunner.html)，版本保持当前 pom，不把 docs 更新作为升级理由。

## Task 10：真实子进程退出、同库重启和迟到写验收

**Files:**
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveProcessProbe.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveProcessRestartTest.java`
- Extend: `knowledge/scripts/verify-campus-run-archive-temp-db.ps1`，只增加明确测试选择/子进程清理，不变临时库核心行为

**Interfaces:** Probe `public static void main(String[] args) throws Exception`，args 精确为 `[mode, controlDirectory]`；数据库URL/用户/密码仅进环境。mode 为 ADMIT_BEFORE_DISPATCH、TOOL_BEFORE_RETURN、CLEANUP_BEFORE_FINAL、FINAL_BEFORE_PUBLICATION、RECONCILE、HOLD_LOCK。父测试进程为唯一调度者。

controlDirectory 下原子写 `phase.json`：pid、runId、instanceId、epoch、phase、modelCalls、toolCalls、archiveStatus；不写问题/答案/工具参数/密钥。parent 创建 `release` 文件解除屏障；退出测试在屏障处 destroyForcibly 而不释放。RECONCILE 只 start owner/Store 后读历史，不跑 Agent，计数均0。

- [ ] RED 真进程测试：不同 PID；同一临时库；四个故障窗口、第二实例取锁失败、旧工具忽略中断迟到 final、关闭超预算未伪造 CANCELLED。每个子进程设置独立控制目录，等待屏障最多10秒，process等待/清理最多10秒；失败也清理本测试所有 ProcessHandle。

```java
String javaExecutable = java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
String classpath = System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
var builder = new ProcessBuilder(javaExecutable,"-cp",classpath,
    CampusRunArchiveProcessProbe.class.getName(),"CLEANUP_BEFORE_FINAL",controlDirectory.toString());
// runner 已设置 ZHKU_DB_URL/USER/PASSWORD；不要把这些值打印或转成 JVM 参数。
builder.environment().put("ZHKU_DB_URL",System.getProperty("zhku.db.url"));
builder.environment().put("ZHKU_DB_USER",System.getProperty("zhku.db.user","zhkuagent"));
Process previous = builder.start();
// 从 phase.json 读到明确屏障后再退出；不用固定 sleep 假设任务到了窗口。
var phase = awaitPhase(controlDirectory,"CLEANUP_BEFORE_FINAL",java.time.Duration.ofSeconds(10),previous);
assertThat(phase.path("pid").asLong()).isEqualTo(previous.pid());
previous.destroyForcibly();
assertThat(previous.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
```

等待函数 `awaitPhase(Path directory,String phase,Duration timeout,Process process): JsonNode` 轮询原子文件，nanoTime预算，每10ms等待，遇到进程提前退出立即失败；输出仅固定元数据。实现边界如下：

```java
static com.fasterxml.jackson.databind.JsonNode awaitPhase(java.nio.file.Path directory,
    String phase,java.time.Duration timeout,Process process) throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    long deadline = System.nanoTime() + timeout.toNanos();
    var file = directory.resolve("phase.json");
    while (System.nanoTime() < deadline) {
        if (!process.isAlive()) throw new AssertionError("子进程在屏障前退出");
        if (java.nio.file.Files.isRegularFile(file)) {
            var result = mapper.readTree(java.nio.file.Files.readString(file));
            if (phase.equals(result.path("phase").asText())) return result;
        }
        Thread.sleep(10);
    }
    throw new AssertionError("没有在期限内到达预期屏障");
}
```

- [ ] 新 runner 选择 `CampusRunArchiveProcessRestartTest` 记录 RED。
- [ ] Probe 按原校园真库测试的真实 facade 组装：InMemoryConversationStore、JdbcElectiveRetakeEvidenceFinder、JdbcCreditLimitEvidenceFinder、公选路由、LocalPreviewAnswerChain、LocalPreviewConversationFacade；只将 ChatModel 替换为确定性的两次工具选择。资料只用本测试合成已发布规则和虚构 ART01/HIS02，2次模型、1次计算是正常 Golden 路径。课程/政策业务逻辑不改变。

屏障用**测试 Store 装饰器/测试工具装饰器**实现，不给生产增加 hook：

- ADMIT_BEFORE_DISPATCH：delegate.admit 真实提交后、方法返回前写屏障。
- TOOL_BEFORE_RETURN：真实纯计算工具完成后、execute 返回前屏障，Service 尚未清理。
- CLEANUP_BEFORE_FINAL：装饰 finalizeRun，在 delegate 写之前屏障，此时真实 Service 已清理。
- FINAL_BEFORE_PUBLICATION：delegate.finalizeRun CONFIRMED 后、方法返回前屏障，Manager completion 未发布。
- HOLD_LOCK：取得锁后保持到控制文件或进程退出；另一个 JVM 不得对账。
- RECONCILE：新 owner 取得锁并提升epoch后查询，禁止调用模型或工具。

仅测试侧屏障可超过 Store 常规 deadline；它模拟 crash point，不用于宣称生产 I/O 预算通过，预算另由 Task 6 测试。杀进程后新 JVM 先取得 lock（可在总10秒启动等待内重新**启动新进程**，不能同进程自动接管）再对账；前3窗口 INTERRUPTED/execution=null，final窗口 FINALIZED/真实完整结果。ACK未知另由真 commit 故障代理验证，不以这4窗口替代。

旧工具迟到测试：专用锁 backend 被终止、新 JVM完成接管后，父进程释放旧任务屏障；旧 final 写拒绝，新归档结论不覆盖；旧在线结果可完成，不出现虚构 USER_REQUEST/RUN_TIMEOUT。记录所有 PID和epoch、重启前后计数与实际 DB 行。
- [ ] GREEN；finally 验证子进程已退出、特定连接关闭、临时容器删除、秘密环境恢复。不能用重新 new Manager 的用例冒充本项验收，也不称受控模型是真实供应商。

## Task 11：全阶段复验、文档与验收证据

**Files:**
- Modify: `README.md`、`knowledge/README.md`
- Create: `docs/evidence/milestone-3a/01-范围与问题.md`、`02-实施记录.md`、`03-实际增量.patch`、`03-文件清单.csv`、`04-测试报告.md`、`05-验收记录.md`、`logs/` 与本轮测试统计
- Preserve: 所有前序验收材料和 2.6 原脚本

**Interfaces:** 证据链为问题→已确认规格及其关闭修订→本阶段实际增量→本轮测试→完成/未验证验收。测试统计分 baseline、post-change-unit、post-change-temp-db、process-restart、original-golden，不重复累计包含关系。

- [ ] 运行新增无库测试，选择：`CampusRunArchiveContractTest,CampusCreditAgentArchiveTest,CampusRunHistoryServiceTest,CampusAgentHistoryPreviewControllerTest,CampusRunArchiveConfigurationTest`。新增真库选择：`CampusRunArchiveSchemaIntegrationTest,CampusRunArchiveOwnerIntegrationTest,CampusRunHistoryVisibilityIntegrationTest,CampusRunArchiveStoreIntegrationTest,CampusRunArchiveBudgetTest,CampusRunArchiveConfigurationIntegrationTest,CampusRunArchiveProcessRestartTest`。所有名称本计划已定义，新文件未创建前不得声称这些命令已运行。
- [ ] 运行原相关回归与原专用库 Golden；每次数据库操作重新检查本次凭据，不设置 RunLiveProvider：

```powershell
& .\docs\evidence\milestone-2\verify.ps1
& .\scripts\Import-ZhkuCredentials.ps1 -UseDatabase
& .\knowledge\scripts\verify-campus-temp-db.ps1 -Port 55432
```

- [ ] 根据实际日志/本轮XML统计通过、失败、错误、跳过；关键真库/重启用例因环境跳过不能通过验收。若失败，保留失败记录，定位修复只在阶段范围内；不刷测试数字或只留最后成功日志。
- [ ] README 增加默认关闭、显式启用、历史查询、保留/撤回、关闭边界及故障运维流程；请求示例沿用虚构课程。没有生产假模型入口，不编造离线生产启动命令。说明服务真实监听/真实模型验收仍未做，历史控制权仍只有单人本机边界。
- [ ] 核对默认关闭三模块构建、文档链接/请求路径/PowerShell语法、原九字段成功响应、原缓存404；检查 agent-runtime/agent/Service/政策链/旧迁移/依赖/默认Session 与基线一致。逐文件 diff 相对 before，而不是把已有未提交差异都算成本阶段。
- [ ] 完成验收记录：原回归、原 Golden、新契约、真实JDBC、真正重启分别标注事实；真实模型、多用户、Session/完整聊天历史、备份恢复、真实监听/用户效果保持未验证。主动宿主停止不在本阶段完成能力中。
- [ ] 保持 HEAD 不变、未提交未推送；记录清理与回滚说明。仅新阶段 diff/hash检查点，无 git add/commit/push。

## 规格覆盖、停止与回滚门禁

| Spec 内容 | 责任任务 |
| --- | --- |
| 源码/测试基线、原范围与后续拆分 | 1、11 |
| 归档/执行/终止分离、白名单 | 2、5、7、8 |
| 两表约束、schema/version、atomic final | 3、6 |
| owner/epoch/探针/迟到写 | 4、6、10 |
| 准入与发布顺序、错误/中断预算 | 6、7 |
| 新历史 API、正文撤回与分页 | 5、6、8 |
| 默认关闭/显式目标/销毁顺序 | 9 |
| 真进程故障窗口、不重放 | 10 |
| 保留/配额/清理/元数据可查承诺 | 3、6、8 |
| 全矩阵、证据、未验证、回滚 | 11及本节 |

任一任务发现需要修改共享 Runtime/校园循环/政策治理或旧迁移，或要求宿主主动停止、不可变证据、自动续跑、身份认证、真实模型、原演示库写入，即停止报告。任何关键故障窗口只靠概率碰到或 I/O 无法收敛，也不进入下一批“已完成”。

回滚步骤：停止新增准入、有界排空并 seal归档 → 确认特定存储资源关闭 → 核对 before备份及后续修改 → 仅恢复本阶段原文件与关闭开关 → 重跑原相关回归。新表/历史数据保留，不自动DROP/TRUNCATE、不回退epoch、不改Flyway历史；临时验收库随本次容器销毁。不能用整体 reset/clean 删除前序未提交成果。

每批审查通过才继续，计划内并未预选执行方式。推荐**当前会话顺序实施**：任务共享 owner/token/归档接口与故障预算，连续维护这些边界能减少交接歧义；最后做一次独立整体审查。也可选择逐任务独立 Agent 实施与复核，成本更高但每个边界都有独立审查。正式实施仍需先复验 Task 1 基线。

## 本轮计划交付记录

本轮只新增本计划，并将用户明确选择的关闭/失锁修订同步回 Spec。编写前 HEAD 为 `135c9d3e58ddbf99f9507ed3f952bd839fd99b86`；486 个受控生产/测试/脚本/构建文件合并哈希为 `CB9DCD8BF5CFD1050EEE31A1236DC5AFD398796E98914874ABD9E055E7970FE4`。本轮未执行计划中的测试、迁移、数据库或模型操作，未创建上述候选 Java/SQL/runner；没有提交、推送。计划完成不代表代码完成。

自审已逐节对照 Spec，所有规格分配到上方任务；五项 Review Focus 各有所属任务。核对数据类型、方法名与错误码，修正 Encoded 与错误分类、临时库 URL 校验次序和子进程屏障示例；没有未解决的实现占位。计划/规格的本地链接存在、围栏成对、无尾随空白；写入后同范围486文件哈希和HEAD一致。以上是文档/源码范围检查，代码片段尚未编译，不称代码或测试验收。
