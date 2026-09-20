# 阶段 9：匿名枚举反馈闭环实现计划

> **执行方式：** 按用户既定的“内联执行”方式，严格 TDD；每个任务的测试先达到 RED，随后只写令其转绿的最小实现。

**目标：** 在 `knowledge` 模块建立只接受匿名散列和受控枚举的反馈闭环：按匿名会话和反馈维度去重、90 天到期时转入不可识别聚合、管理员仅查看聚合。反馈绝不携带自由文本或身份，绝不自动影响资料、索引、检索、提示词或制度结论。

**依据：** `docs/superpowers/plans/2026-09-16-stage-9-service-guides-feedback.md`、`docs/plans/agent-sync/SYNC-STATUS.md` 第四、五、八节，以及用户 2026-09-20 的裁决。

**架构：**

```text
匿名枚举 POST
  -> feedback/ 纯领域校验 + 规范化 + 去重键
  -> JdbcFeedbackRepository
  -> anonymous_feedback_event (最多保留 90 天)

到期清理（注入 Clock）
  -> 按办事清单软引用 + 版本 + 枚举原因汇总
  -> anonymous_feedback_aggregate
  -> 删除已聚合事件

本机管理员 GET
  -> 仅 aggregate DTO，不含事件 ID、匿名散列或任何原始输入
```

## 固定约束

- 仅创建一个 `V16__...sql`，且与线 A 的 V15 不建外键；清单标识和版本只是软引用。
- V16 不得出现 `message`、`student_id`、`name`、`contact`、原问题或原回答字段。
- 请求 JSON 只能包含 `guideIdentifier`、`guideVersion`、`reason`、`anonymousSessionHash`；未知字段（包括任何自由文本/PII 常见字段）必须拒绝。
- 成功或重复结果不回显匿名散列、去重键、事件 ID 或其他逐条事件资料。
- `reason` 使用封闭 enum；清理任务与查询都不调用发布、索引、检索排序、提示词或外部系统。
- 资料候选只能由管理员在既有资料治理流程中显式创建；本交付不新增自动转资料或自动发布路径。

## 实现步骤

### 1. 先建立匿名输入的 RED 契约测试

**新增：** `knowledge/src/test/java/com/zhku/knowledge/feedback/AnonymousFeedbackContractTest.java`

覆盖：

1. 有效枚举及合格 64 位十六进制匿名散列能被接受；响应没有散列/去重键/事件 ID。
2. `message`、`name`、`studentId`、`contact`、`question`、`answer` 等自由文本或身份字段会被 JSON 边界拒绝。
3. 同一匿名散列、清单标识、版本、原因、自然日第二次提交返回明确 `duplicate=true`，仓储仅保留一条。
4. 未知原因和错误散列格式不能进入持久化层。

先执行：

```powershell
$dbPassword = [Environment]::GetEnvironmentVariable('ZHKU_DB_PASSWORD', 'User')
if ([string]::IsNullOrWhiteSpace($dbPassword)) { throw '用户级 ZHKU_DB_PASSWORD 不可用。' }
$env:ZHKU_DB_PASSWORD = $dbPassword
mvn -pl knowledge -Dtest=AnonymousFeedbackContractTest test
```

预期：由于生产类尚未创建而失败（RED）。

### 2. 最小领域模型、JDBC 仓储与 V16

**新增：**

- `knowledge/src/main/java/com/zhku/knowledge/feedback/FeedbackReason.java`
- `knowledge/src/main/java/com/zhku/knowledge/feedback/AnonymousFeedbackSubmission.java`
- `knowledge/src/main/java/com/zhku/knowledge/feedback/AnonymousFeedbackReceipt.java`
- `knowledge/src/main/java/com/zhku/knowledge/feedback/FeedbackRepository.java`
- `knowledge/src/main/java/com/zhku/knowledge/feedback/JdbcFeedbackRepository.java`
- `knowledge/src/main/java/com/zhku/knowledge/feedback/AnonymousFeedbackService.java`
- `knowledge/src/main/resources/db/migration/V16__create_anonymous_feedback.sql`

迁移只创建：

- `anonymous_feedback_event`：软引用清单标识/版本、`reason`、匿名散列、去重键、创建/到期时刻；唯一去重键和到期索引。
- `anonymous_feedback_aggregate`：清单标识/版本、枚举原因、聚合日和计数；不含任意逐条身份或文本。

去重键由服务从匿名散列和已规范化的业务维度计算，数据库唯一约束承担并发竞态下的最终去重。注释说明：只保留散列是为了可控去重，绝不能把原始会话或身份作为替代品。

重复执行第 1 步测试，预期转绿。

### 3. 先建立 90 天与隔离边界的 RED 测试

**新增：** `knowledge/src/test/java/com/zhku/knowledge/feedback/FeedbackRetentionTest.java`

覆盖：

1. `Clock` 恰到 `createdAt + 90 days` 时，事件按清单软引用、版本、原因聚合并删除；边界前不处理。
2. 管理员查询只返回聚合字段和计数，没有散列、去重键、事件 ID 或单条创建时刻。
3. 反馈接收与清理不依赖、也不调用资料发布、候选创建、索引或检索组件。
4. 未经显式管理员动作，反馈事件只能停留在反馈表或聚合表，不会产生资料、候选或索引记录。

先执行：

```powershell
$dbPassword = [Environment]::GetEnvironmentVariable('ZHKU_DB_PASSWORD', 'User')
if ([string]::IsNullOrWhiteSpace($dbPassword)) { throw '用户级 ZHKU_DB_PASSWORD 不可用。' }
$env:ZHKU_DB_PASSWORD = $dbPassword
mvn -pl knowledge -Dtest=FeedbackRetentionTest test
```

预期：清理与聚合能力尚不存在，测试为 RED。

### 4. 最小清理/聚合实现与本机管理员读取 API

**新增：** `FeedbackRetentionService`、`FeedbackAggregate` 及只暴露聚合 DTO 的管理员控制器适配层和相应测试。

控制器仅提供本机管理员认证/开关之下的 `GET /zhku/local-admin/feedback/aggregates`；它不接收文本、不返回事件详情，也不提供自动转资料、发布或索引端点。

现有本机管理员应用只扫描 `com.zhku.knowledge.governance.web`。因此在**开始此步骤前**，先在 `SYNC-STATUS.md` 第三节登记新增的 `governance/web` 适配文件及其测试占用；领域模型仍全部留在 `feedback/`。如果该目录出现占用冲突，停止并在“待协商事项”登记，不修改 `ZhkuLocalAdminApplication` 的扫描范围。

为本机管理员配置新增独立的反馈 bean 装配；同样先登记配置文件占用。所有 bean 使用 `Clock` 作为可替换依赖，生产环境用 UTC 时钟，测试用固定时钟。

重复第 3 步测试并补 API 适配测试，预期全部转绿。

### 5. 集成验证、审计资料与同步状态

1. 用用户级 `ZHKU_DB_PASSWORD` 注入当前进程，执行 `mvn -pl knowledge test`。
2. 用精确文件列表暂存，依次执行 `git diff --cached --check`、`git status --porcelain=v1`，仅提交阶段 9 文件；不 push、不合并主线。
3. 新增 `docs/evidence/2026-09-20-阶段9-匿名反馈闭环-证据账本.md`，记录 RED/Green、API 字段、去重和 90 天边界、已证明与未证明的范围。
4. 在 `SYNC-STATUS.md` 第八节登记 WB-1 可对接的请求/响应契约，并更新线 B 行；不覆盖其他线决策文字。
5. 最终复跑相关测试与 `mvn -pl knowledge test`，然后停止等待验收。

## 验收命令

```powershell
$dbPassword = [Environment]::GetEnvironmentVariable('ZHKU_DB_PASSWORD', 'User')
if ([string]::IsNullOrWhiteSpace($dbPassword)) { throw '用户级 ZHKU_DB_PASSWORD 不可用。' }
$env:ZHKU_DB_PASSWORD = $dbPassword
mvn -pl knowledge -Dtest=AnonymousFeedbackContractTest,FeedbackRetentionTest test
mvn -pl knowledge test
```

