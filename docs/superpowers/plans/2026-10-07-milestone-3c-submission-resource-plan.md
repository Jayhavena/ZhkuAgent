# Milestone 3C：提交去重与资源约束实施计划

> **For agentic workers:** 实施时使用 `superpowers:executing-plans`，在当前会话按三批顺序执行；本计划不授权自动派生子代理、不授权提交或推送。步骤使用复选框跟踪。

**Goal:** 用户在网络中断、页面刷新或重新登录后，可以确认原提交；在约定保留期内，重复发送同一次提交不会再次派发任务，资源使用有明确上限。

**Architecture:** 在现有 PostgreSQL 准入事务中原子保存提交键、请求指纹、原 Run 和用户归属；既有 owner/epoch 继续保护写入。Manager 只为新建且提交已确认的 Run 派发一次，查询与重复提交没有派发能力。复用归档、工作池、定时器和模型传输，新增用户准入计数、排队期限与单任务模型预算。

**Tech Stack:** 沿用 Java 21、Spring Boot 3.4.4、Spring AI 1.0.9、JDBC/Flyway/JUnit、PostgreSQL 16、Vue 3、Vite 4 和现有 Nginx；不升级依赖，不新增基础设施。

**Spec:** 本文件第 2–6 节是本轮内嵌设计契约，承接用户的三批范围和前轮源码审阅。原路线为 [3A 之后的后端路线](2026-10-03-post-3a-backend-roadmap.md)，现有网页基线为 [受控校园网页计划](2026-10-06-controlled-campus-web-demo-plan.md)。发生冲突时，3C 使用本文件的独立学分任务、零自动重试和停止边界。

**日期与状态：** 2026-10-07；3C三批已实施并通过12项门禁，[最终验收](../../evidence/milestone-3c/2026-10-07/3c-3/acceptance.md)包含55类371项、前端46项、实际产品/Nginx/不同PID/回退与资源退出。本文件保留原设计步骤/代码示例及阶段过程注记；逐项执行状态以验收和ledger为准，不能把计划内容本身当测试结果。检查点实际保存于本轮证据目录，无提交/推送/3D。

## 1. 全局约束与完成定义

### 2026-10-07 身份并发有限范围修订（用户明确授权）

真实 HTTP 同键 20 并发在身份层出现 18 个 `IDENTITY_UNAVAILABLE`，原失败证据保留于 `docs/evidence/milestone-3c/2026-10-07/3c-1/STOP-identity-concurrency.md`。授权补充修改 `PreviewIdentityRepository.java`、`PreviewIdentityBudgetTest.java`、新增 `PreviewIdentityCurrentConcurrencyTest.java`，以及对应 HTTP 测试、验收脚本白名单和证据；不删除任何鉴权层。

- 采用 **requireCurrent 有界等待，不合并身份查询**：物理查询最多 2 个，额外最多 32 个调用者等待；只登记计数，不保存用户许可或密码，不新增数据库线程、执行器或执行队列。
- 同一次校验从进入至完成的原 2 秒预算包含等待；等待者中断只退出自身，关闭唤醒全部等待者。等待者撤销或超时不能归还正在查询/关闭连接的物理名额。
- 每个已取得名额的调用重新查询当前账号；迟到加入、不同 userId/role/revision 均不复用先前结果。密码认证保持原来的无等待、无合并策略。
- 零容量执行队列保留；只有 requireCurrent 在物理名额归还和 worker 重返接收状态的短窗口内重试 **尚未接受的执行器交付**，共享原截止点，绝不重试数据库查询。
- 固定测试门槛：等待/查询总预算 2000ms，检测 2250ms；已满等待登记的拒绝和关闭唤醒检测 425ms；关闭最多 2000ms、检测 2250ms；障碍释放后的实际线程退出检测 2000ms。屏障在实现前固定，不能为测试变绿改门槛。
- 原身份预算测试第三次 requireCurrent 改验总期限 2250ms（原 425ms 立即拒绝仅保留于密码认证/等待登记满/关闭后拒绝）；继续断言超时/close 不提前释放两条物理连接、无第三条连接及实际退出。
- 先失败再修复；同身份并发、跨身份、撤回/revision、等待者中断、超时、迟迟不退出连接、close、32 登记上限和真实 HTTP 20 并发全部复验。HTTP 正常环境必须全为 200/202、同一原 Run，最终提交/Run/派发/模型为 1/1/1/2，不能接受 18 个 503 或中间计数。

此次修订前检查点：`docs/evidence/milestone-3c/2026-10-07/3c-1/identity-authorized-checkpoint/`，保存 28 个当前增量、身份原文件和本计划，全部 zip 条目读回哈希验证通过。原完整检查点及历史“Runtime 未改”证据不改写。3C-1 全部门禁通过后继续原 3C-2、3C-3；供应商调用仍为 0。

- 学分 Run 不关联重修咨询 Session；不向请求体加入 userId、角色、authRevision 或 sessionId。
- 两条 Agent 主循环独立；Runtime 不承担身份、数据库、去重或预算。仅允许增加下文定义的指定 runId 构造重载，保持旧构造语义。
- 默认零自动重试，涵盖模型/SDK和客户端 POST；GET 的有界轮询不属于任务重执行。
- 不做自动续跑、多机调度、注册、完整聊天历史、页面重新设计或真实供应商故障矩阵。
- 所有数据库写入验收使用专用临时库；拒绝原演示库端口 54329，不挂载原数据卷。每次数据库操作按 AGENTS 重新检查凭据继承，凭据不进入参数、源码、日志和证据。
- 本轮供应商调用额度为 **0**。协议替身必须经现有产品模型工厂测试；生产不得静默切换为假模型。
- 同仓库不并行 Maven，不覆盖现有 dirty，不 reset/clean、不自动 Git 提交或推送。
- 中文注释解释原子边界、未知确认、资源释放和权限约束，不能只复述代码。
- 3C-1 → 3C-2 → 3C-3；上一批门禁通过后才进入下一批。完成 3C-3 后停止，不启动 3D。

**完成定义：** 在提交记录保留期内，同用户、同任务类型、同键、同内容只对应一个原 Run；同键不同内容拒绝；只有原调用取得新建且已确认的准入结果后可派发一次。允许存在已受理但未执行的 Run，明确报告派发未确认或中断；不承诺任意故障下恰好执行一次，也不承诺远端取消或计费停止。

## 2. 已核对的基线与最小文件边界

| 现有文件 | 当前事实 / 本轮职责 |
| --- | --- |
| `knowledge/.../campusagent/CampusCreditAgentManager.java` | 每次 start 创建新任务；容量混合活动与缓存；补新建/复用分支和原子用户预留 |
| `knowledge/.../campusagent/CampusCreditAgentService.java` | 手动工具控制、领域守卫、最多 3 步；补指定 Run 上下文与模型调用预算 |
| `knowledge/.../campusagent/archive/CampusRunArchiveStore.java` | 增加受控提交与按键读取；旧替身新增方法默认安全失败 |
| `knowledge/.../campusagent/archive/JdbcCampusRunArchiveStore.java` | 在现有同连接事务中保存提交和 ADMITTED；仍由现有 writer/reader 与 owner 管理 |
| `knowledge/.../campusagent/archive/CampusRunArchiveOwner.java` | 重启把旧 ADMITTED 标为 INTERRUPTED；清理顺序同步新增提交关联，不派发 |
| `knowledge/.../controlled/CampusRunAccessService.java` | 新提交协议与持久查询投影；继续当前许可和撤回检查 |
| `knowledge/.../controlled/ControlledCampusAgentController.java` | POST 必须携带提交键；保留现有请求体 |
| `knowledge/.../controlled/ControlledPreviewExceptionHandler.java` | 冻结错误码及受理未知语义 |
| `knowledge/.../security/PreviewSecurityConfiguration.java` | 精确放行新 GET；继续默认拒绝、CSRF 和当前身份校验 |
| `knowledge/.../controlled/ControlledCampusAgentConfiguration.java` | 装配 3C 参数；复用 2/2/16 工作池和既有传输 |
| `knowledge/.../tools/modelcall/CourseCreditChatModelFactory.java` | 保留零重试，显式输出 token 上限；不新建每 Run 传输 |
| `agent-runtime/.../AgentRunContext.java` | 增加指定 runId 的三个 create 重载；旧入口仍随机生成 ID |
| `agent/frontend/src/api/controlled.js`、`state/controlled-results.js`、`views/CampusRun.vue` | 提交键、确认查询、状态投影和局部按钮文案 |

上表 Java 路径中 `knowledge/...` 展开为 `knowledge/src/main/java/com/zhku/knowledge/`，`agent-runtime/...` 展开为 `agent-runtime/src/main/java/com/zhku/agent/agent/runtime/`。下方新增文件均给出完整仓库相对路径。

**新增生产文件：**

1. `knowledge/src/main/java/com/zhku/knowledge/campusagent/submission/CampusRunSubmission.java`：提交值对象、错误码、准入结果与只读结果。
2. `knowledge/src/main/java/com/zhku/knowledge/campusagent/submission/CampusRequestFingerprint.java`：受限规范化与 v1 指纹。
3. `knowledge/src/main/java/com/zhku/knowledge/campusagent/CampusAdmissionBudget.java`：短临界区使用的用户/实例名额预留和幂等释放。
4. `knowledge/src/main/java/com/zhku/knowledge/campusagent/CampusExecutionBudget.java`：每 Run 调用次数与单调时钟期限，不使用 ThreadLocal。
5. `knowledge/src/main/java/com/zhku/knowledge/controlled/ControlledCampusSubmissionController.java`：按键只读查询。
6. `knowledge/src/main/java/com/zhku/knowledge/controlled/ControlledRunRequestLimitFilter.java`：仅限制受控 POST /runs 的实际请求字节数，包括无 Content-Length 请求。
7. `knowledge/src/main/resources/db/migration/V21__create_campus_run_submission.sql`：新提交表；实施前重新核对编号，若已占用则改用下一个空闲编号并同步本计划引用，不覆盖历史迁移。
8. `agent/frontend/src/state/controlled-submission.js`：一次提交的键、内存快照和少量可恢复元数据。
9. `knowledge/scripts/verify-campus-submission-temp-db.ps1` 与 `.cmd`：3C 非供应商白名单验收入口；包装器沿用本机 PowerShell 查找方式。

不另建独立 JDBC 提交执行器或会自行 commit 的第二 Store；避免“两张表各自成功”的半提交。新 submission 包保存值对象，SQL仍由现有归档 Store 实现。

## 3. 冻结提交接口与数据契约

### 3.1 HTTP

浏览器前缀仍为 `/api/zhku/controlled-preview/v1`；下表省略 `/api` Servlet context。所有响应 `Cache-Control: no-store`。

| 请求 | 契约 |
| --- | --- |
| `POST /runs` | 新增必填 `Idempotency-Key`，取 UUID v4 小写规范形式；body仍为 question/courses。缺键、格式错误 400，不进入准入 |
| `GET /submissions/{submissionKey}` | 本人按键查询；只读取提交和当前可见的 Run，不构造任务，不预留名额，不调用模型 |
| `GET /runs/{runId}` | 保留现有在线协议；本轮不要求所有旧在线查询都变成历史 DTO |
| `POST /runs/{runId}/cancel` | 保留现有归属检查；已终止/已中断的历史任务不因取消而创建 Context |
| `GET /history`、`GET /history/{runId}` | 保持分页和当前来源许可；提交确认查询复用此投影规则 |

统一新响应外层：

```java
record SubmissionReply(
    UUID submissionKey, UUID runId, boolean replayed,
    Instant acceptedAt, Instant expiresAt,
    String location, // LIVE、ARCHIVED、UNCONFIRMED
    CampusRunAccessService.ControlledRunReply live,
    CampusRunHistoryService.Detail archived) {}
```

- 首次确认受理：202、`replayed=false`；命中原提交：200、`replayed=true`；GET成功200、`replayed=false`，其含义仅为非重复 POST。
- `LIVE` 只有 live 非 null，`ARCHIVED` 只有 archived 非 null；`UNCONFIRMED` 两者为 null，但已确认存在提交时保留 runId/acceptedAt。
- `expiresAt` 活动阶段为 null，终结后使用归档 retainedUntil。前端不可把 null 解释为永久保留。
- 同键不同指纹：409 / `SUBMISSION_CONTENT_CONFLICT`，不返回其他任务正文。
- 每用户超额：429 / `USER_IN_FLIGHT_LIMIT`；实例无法预留：503 / `INSTANCE_CAPACITY_EXCEEDED`。仅在明确没有创建提交时 `acceptance=NOT_ACCEPTED`。
- 数据库提交结果未知：503 / `SUBMISSION_UNCONFIRMED`，`acceptance=UNKNOWN`、原 submissionKey；不得给 `accepted=false`。只能保留已确认可信的字段，不把本地候选 runId 声称为持久受理事实。
- 提交存在但投影读取失败：503 / `RESULT_READ_UNAVAILABLE`，`acceptance=ACCEPTED`、submissionKey、runId，并兼容既有 accepted=true。
- GET 他人/不存在统一404 / `SUBMISSION_NOT_FOUND`。它只表示当前未找到本人可见记录，不证明原 POST 永未受理；数据库不可读503，不能转换404。
- 401/403、CSRF失败、用户停用仍由现有安全链处理；新的 GET matcher 只允许 USER，不放行 `/submissions/**` 任意方法。
- 名额预留后执行器明确拒绝：仍返回已经受理的原 Run，显示既有 EXECUTOR_REJECTED 终态；同键重发复用该拒绝任务，不再次派发。受理与执行成功分开。

### 3.2 Java 存储边界

以下类型置于 `CampusRunSubmission`，保存的请求哈希使用小写十六进制 SHA-256：

```java
record Fingerprint(int version, String sha256) {}
record Command(UUID submissionKey, UUID proposedRunId, Fingerprint fingerprint) {}
enum Decision { CREATED, REPLAYED }
record Record(UUID userId, UUID submissionKey, UUID runId,
              Fingerprint fingerprint, Instant acceptedAt) {}
record Claim(Decision decision, Record record, CampusRunArchive.Admission admission) {}
// CREATED持有admission；REPLAYED的admission为null，不具有投递能力。
```

`CampusRunArchiveStore` 增加：

```java
Claim admitSubmissionOwned(TrustedActor actor, Command command);
Optional<Record> findSubmissionOwned(TrustedActor actor, UUID submissionKey);
```

默认实现安全失败，禁止调用匿名 admit/find 作为回退。同键不同指纹抛有限分类的提交冲突异常。

Manager增加：

```java
record Submitted(Claim claim, Snapshot live) {}
Submitted submitOwned(TrustedActor actor, UUID submissionKey,
                     CampusCreditAgentService.Request normalizedRequest,
                     Fingerprint fingerprint);
```

`REPLAYED` 不创建 AgentRunContext、定时器或任务；`live` 可以为 null，由 AccessService 查询归档。旧匿名 `start` 保持独立，受控新 POST 不可因缺键回退旧 startOwned。

### 3.3 规范化与边界

`CampusRequestFingerprint.normalize(Request)` 返回规范化 Request；`of(Request)` 返回 Fingerprint，版本固定1。

- question、courseId、category去除首尾空白，不改内部空白、不做 Unicode 折叠或模型语义比较。
- question仍最多500个 Java字符串字符；courses最多20项，courseId最多64、category最多32个字符。
- 学分采用 BigDecimal；非null时 stripTrailingZeros，规范化后的 precision≤16、scale在-6到6之间，禁止巨大指数展开。1.50与1.5等价。
- 保留课程数组顺序；本版重排视为不同内容，避免擅自改变输入表示。null credits等可澄清业务值仍保留，不把所有领域无效值变成400。
- 请求体实际UTF-8字节≤16 KiB；超额413 / REQUEST_TOO_LARGE。未知身份字段仍拒绝。
- 固定字段序列化 question/courses/courseId/category/credits，null显式编码；不使用调用方JSON属性顺序；规范化后的同一Request同时用于指纹与执行。
- 指纹不含登录会话、authRevision、当前政策版本、模型版本或服务端时间；资料变化通过展示许可治理，不使原提交变成新任务。
- 不存问题/课程原文、Prompt、工具自由文本或恢复输入；指纹也受用户隔离与保留治理。
- 日后修改规范化规则需新增版本并在保留期内保留v1比较能力；本轮只实现v1。

### 3.4 SQL、事务与保留

迁移核心DDL：

```sql
CREATE TABLE campus_agent_run_submission (
    owner_user_id uuid NOT NULL REFERENCES preview_identity(user_id) ON DELETE RESTRICT,
    task_kind varchar(32) NOT NULL CHECK (task_kind='CAMPUS_CREDIT_SUMMARY'),
    submission_key uuid NOT NULL,
    run_id uuid NOT NULL UNIQUE REFERENCES campus_agent_run_archive(run_id) ON DELETE RESTRICT,
    fingerprint_version integer NOT NULL CHECK (fingerprint_version=1),
    request_sha256 varchar(64) NOT NULL CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    accepted_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (owner_user_id,task_kind,submission_key)
);
```

同一事务必须校验归档 run_id 的 owner_user_id/task_kind与提交一致；增加相应INSERT校验及禁止UPDATE改写关联/指纹的数据库触发器。FK只防悬空，不替代归属一致性校验。删除仅在明确过期清理路径执行。

事务锁序沿用 `owner → identity → submission/archive`：取得现有 owner行锁，校验当前身份；重新按键读取。若存在则比较指纹并返回REPLAYED，不再做新准入。若不存在才检查归档容量并在同一个Connection插入archive和submission，最后commit；只有确认返回才为CREATED。不跨连接调用自提交方法。

唯一约束是最终防线；现有owner锁已经序列化写入，不需要增加SERIALIZABLE和事务重试。写入确认未知沿用pause/关准入；只读按键查询不依赖isAccepting。读成功不能重新授权派发。

保留期冻结为**归档确认终结或重启对账关闭后7天**，与既有retainedUntil一致；活动ADMITTED不按年龄删除。清理选中一批已关闭且到期的runId，在同事务先删submission，再删archive；同步修改Store.prune与Owner启动清理，禁止ON DELETE CASCADE提前失去去重证据。

每条Run至多一条submission，因此提交表容量随既有归档maxRecords收敛；旧匿名/旧受控Run无提交记录，不自动补键、不认领。可查询但尚未FINALIZED的记录返回UNCONFIRMED或当前LIVE事实，不能从ADMITTED推断正在执行。

保留期后删除的键不再有服务端去重保证；404不提供历史否定证明，也不永久返回410。页面显示期限并不复用已过期键；用户明确新建才生成新键。若以后要求永久拒绝旧键，必须另立键年龄/墓碑协议，不在本轮暗示已有此保证。

## 4. 冻结资源与模型预算

| 参数 | 本版固定默认值 / 语义 |
| --- | --- |
| 每用户在途 | 2；包含准入预留、排队、执行、停止收尾；完成缓存不计 |
| 实例准入预留 | 最多18；对应2执行+16排队，实际execute拒绝仍按已受理终态处理 |
| 执行池 | 2个工作线程、队列16、AbortPolicy，不CallerRuns |
| 排队期限 | 从提交commit确认后本地投递准备开始10秒；事务等待另受既有存储operation预算限制 |
| 任务总期限 | 同一本地单调时钟起点30秒触发停止并禁止新外发，包含排队；出队不重新计时，不等于物理调用此刻已退出 |
| 模型请求 | 每Run最多3次；在外发前原子预留，异常/未知不退还 |
| 输出token | 每次显式max_tokens=2048；保持当前非推理兼容模型路径，不同时设置max_completion_tokens |
| 模型输入 | 每次发送的系统/用户文本合计最多12000个Java字符串字符；工具schema为现有固定白名单，不接受用户扩展 |
| 单次传输 | 复用现有profile时限与有界I/O池；发起前剩余总期限必须为正且调用预算可预留；正在执行的请求仍受其既有传输时限约束 |
| 自动重试 | 0；保留RetryTemplate maxAttempts(1)，前端不自动重发POST |
| 累计费用 | 本轮不做日/月账本，不承诺全天费用封顶；调用/输入/output上限约束单任务 |

参数由受控配置显式装配，前端不能覆盖。匿名旧入口保持原契约；受控请求必须经过3C预算。

30秒是本地主动停止与禁止继续调用的截止点，不能宣称此刻远端停止计费或所有线程已退出。物理调用收尾允许延续至已发请求的既有传输期限，归档另受operation预算限制；验收分别记录停止发起和实际收敛时间。不得用Future取消证明硬30秒完成。如果后续要求单次HTTP时限严格缩短到Run剩余时长，需另行核对1.0.9传输适配，本轮不重建每Run HttpClient或暗改SDK。

名额预留在Manager短临界区内原子进行，数据库/模型/定时器执行在锁外。查到已存在提交直接读取；同进程尚未确认的同键请求使用Manager内`(userId,submissionKey)`有界pending登记合并，仅首个请求预留名额，其他请求在锁外等待其结果并转换为REPLAYED。pending最多18个不同键，原请求结束后移除；等待最多5秒，超时返回UNKNOWN，不接管、不派发、不自动重试。等待者取得的结果不能携带CREATED许可，异内容在指纹比较后409。数据库唯一约束仍是持久最终防线，内存登记不是去重证据。

若额度已满，在拒绝前再次按键读取并检查pending，已存在或本机正在受理的同键不消耗额外名额；不同键明确拒绝。20并发正常同键验收必须最终指向原Run，不能用“19个因配额被拒绝”冒充去重闭环。

只有任务实际收尾并发布完成后释放用户逻辑名额；取消标志、Future.isDone或HTTP断开不能释放运行中的物理资源。准入确认未知时关闭新准入，原进程不自动恢复准入；未知预留仅在关闭并确认owner写入已封锁后丢弃。新进程先完成旧ADMITTED的INTERRUPTED对账再接收新任务，不在未知窗口乐观释放并继续接受其他工作。

### 排队停止实现边界

受控Manager保存实际FutureTask与ThreadPoolExecutor句柄。到期/取消先requestStop；定时器尝试安全移除未开始任务。只有`worker.remove(task)`成功者才能执行未启动停止收尾；失败表示可能已取走，交由原工作线程处理。先移除再cancel(false)，不以cancel成功推断remove成功。

```java
context.requestStop(reason);
if (worker.remove(managed.task)) {
    managed.task.cancel(false);
    // 停止已确认且任务不在队列：复用service.run的停止分支，禁止进入decide/model。
    completeStoppedBeforeStart(managed);
}
```

`completeStoppedBeforeStart`调用既有`service.run(context,request)`停止分支、归档和完整结果发布；通过ManagedRun一次性完成守卫与工作线程收尾互斥。不要新增清理假结果。取消HTTP只请求停止并在已有定时器安排移除，不能等待数据库收尾或在准入锁中归档。

排队物理槽在remove成功时回收；逻辑名额在完整停止收尾后回收。定时器单线程上的每次归档使用既有operation预算；批量队列收尾验收上界为`待处理数×归档operation预算+2秒本机调度余量`，不用无依据的“所有取消2秒内完成”。所有原任务动作开始前再检查单调期限；即使定时器迟到，过期任务也不允许开始模型/工具。

`CampusExecutionBudget`由每Run独立创建：`reserveModelCall(String prompt)`、`checkDeadline()`、`attemptedCalls()`。业务Service在模型外发前、返回后、每个工具前检查停止/总期限；预算失败输出固定分类MODEL_CALL_BUDGET_EXCEEDED、MODEL_INPUT_LIMIT或真实RUN_TIMEOUT，不并入无法解释的MODEL_FAILURE。

预算在内存原子预留即可：本轮无恢复，同一个持久Run禁止在新进程重新执行；不新增逐请求数据库事务或跨进程补偿退款。重启读取预算信息不产生新执行额度。

## 5. 网页提交生命周期

- 新增状态模块以`crypto.randomUUID()`生成键；缺少可用生成能力时阻止发送并给出有限提示，不使用Math.random。
- 同一次提交：键+规范化后冻结的内存请求快照；用户明确“重新发送本次提交”才用原键/原快照，不重新从已编辑表单取值。
- 网络、5xx未知或响应解析失败：保存键，显示“提交结果暂未确认”，只进行GET确认；禁止自动POST。
- sessionStorage只保存当前用户的submissionKey、runId（已确认时）、本地创建时间和确认过的expiresAt，最多5条；不保存请求原文、哈希、Cookie、CSRF或答案。
- 刷新后可按键查询；请求快照已丢失时不提供原POST重发，只提供确认查询与明确新建。没有原内容就不能伪称重发同一次提交。
- `replayed`表示任务复用，不代表答案允许展示；使用LIVE/ARCHIVED各自现有投影规则，UNCONFIRMED不展示缓存正文。
- 显式退出/切换账号清除旧账号可见内容与本地提交元数据；401只隐藏并停止请求，保留原账号的键提示，重新认证到同一userId后才恢复查询。不同userId登录立即删除旧提示。
- GET轮询1秒、单次最多60秒；离页/身份换代停止。客户端轮询结束不制造后端超时。
- “新建任务”明确说明将创建新提交；未知原提交旁不把它称为“重试”。不改整体布局，不增加完整聊天或任务恢复入口。

## 6. Review Focus与验收证据

五个容易漏测的边界均在后续任务中绑定测试：

1. 实例/用户名额满时，同键已受理请求仍应能确认原Run，不再次预留或调度。
2. commit成功但确认丢失、或派发前退出：可找到原记录，但读取不得成为补派发入口。
3. 排队取消与工作线程取走任务竞态：只有一个收尾者，超期无模型/工具外发，名额不双重释放。
4. 页面刷新/重新登录/换账号和迟到响应：保留可确认的键，不泄露旧答案，不把缺少请求快照当成可重发。
5. 资料撤回/归档清理/去重期结束：任务身份与答案许可分开，保留期内不因内存或归档缺失创建新Run。

每批证据目录：`docs/evidence/milestone-3c/2026-10-07/{baseline,3c-1,3c-2,3c-3}`。真实执行日期不同则创建实际日期目录，不伪造日期。每批包含文件清单/哈希、增量diff、RED/GREEN原始结果、选类、模型/派发计数、临时库标识、监听/PID、实际关闭状态和验收结论；只存合成数据及有限错误码。

## 7. 实施前置检查点（不计为第四批）

**交付：** 可恢复当前完整源码状态的检查点、基线结果、冻结契约。

- [ ] 保存HEAD、分支、Git状态；包含已跟踪修改与未跟踪源码/配置/文档，使用路径清单和SHA-256核对归档副本。排除.git、target、node_modules、密钥文件、真实数据卷与浏览器会话。
- [ ] 在证据目录中保存源码副本或补丁+未跟踪文件副本；进行只读抽样恢复比对。仅Git标签或普通git diff不足以覆盖当前版本。
- [ ] 检查同仓Maven/演示writer与lease，先停止需要占用target的活动；保存该阶段真实基线，不复用历史“通过”数。
- [ ] 检查本机Java/Maven/PowerShell/Node实际路径；不把Codex PATH当普通用户PATH。用户入口沿用.cmd定位PowerShell模式；新runner不依赖rg。
- [ ] 运行现有相关非供应商基线；专用临时库基线按当前V20核验。前端使用`Resolve-ZhkuNode.ps1`返回的实际可执行文件。所有命令/结果记录实际退出码和选中测试数。
- [ ] 对照本文第3–5节检查无冲突；同步原路线3C中Session作用域、有限重试和“实例在途2”的旧表述，仅补注明本计划优先，不改历史验收事实。

本轮写计划不执行上述步骤。实施时检查点失败就先修复检查点，不直接开始迁移或产品修改。

## 8. 3C-1：提交去重与持久确认

### Task C1.1：指纹、请求大小与数据迁移

**文件：** 新增第2节submission两个值对象文件、请求限制Filter、V21；修改受控配置和`CampusRunArchiveOwner`/Store清理。新增测试：

- `knowledge/src/test/java/com/zhku/knowledge/campusagent/submission/CampusRequestFingerprintTest.java`
- `knowledge/src/test/java/com/zhku/knowledge/campusagent/submission/CampusRunSubmissionSchemaIntegrationTest.java`
- `knowledge/src/test/java/com/zhku/knowledge/controlled/ControlledRunRequestLimitTest.java`

**接口：** 产出Fingerprint/Command/Record/Claim、normalize/of及第3节DDL；后续存储任务只接收这些值。

- [ ] 写RED：数字规范化与字段顺序、课程顺序变化冲突、内部空白不折叠、边界长度/数字精度、实际body大小和无Content-Length请求。代表断言：

```java
var normalized = CampusRequestFingerprint.normalize(request(" 问题 ", "1.50"));
assertThat(normalized.question()).isEqualTo("问题");
assertThat(CampusRequestFingerprint.of(normalized))
    .isEqualTo(CampusRequestFingerprint.of(
        CampusRequestFingerprint.normalize(request("问题", "1.5"))));
assertThat(CampusRequestFingerprint.of(reorder(normalized)))
    .isNotEqualTo(CampusRequestFingerprint.of(normalized));
```

`request`/`reorder`是该测试类的合成Request构造辅助方法，分别创建两门不同courseId课程及交换其顺序，不引用真实个人数据。

- [ ] 写真库RED：重复唯一键失败、跨用户同键成功、关联归属不一致失败、改写关联失败、过期清理按submission→archive顺序成功；活动行不可清理。
- [ ] 运行选类，确认RED由新增行为缺失导致；编译未通过的结果单独标注，不当成行为RED。
- [ ] 实现固定规范化、SQL触发器、仅POST /runs的实际body限制；Filter在身份/CSRF许可之后读取业务body，413不进入Manager。
- [ ] body限制Filter仅注册在受控安全链、当前身份与请求授权之后；禁止同时作为Servlet全局Filter重复执行。测试匿名/错误CSRF/MAINTAINER仍先返回原401/403，不因请求大小改变权限错误或读取原文。
- [ ] 在两个清理位置使用同一过期批次与同一事务；旧无submission行照常清理，保留期内不能单独删除关联。
- [ ] 更新`knowledge/src/test/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveSchemaIntegrationTest.java`中最高版本20的活动断言及测试名为21；逐一核对新测试选中的其他最高版本断言，既有target18/19迁移起点保持原含义，历史报告不修改。
- [ ] GREEN：通过本任务测试与既有请求校验、Schema、归档清理回归；保存增量证据，不自动提交。

### Task C1.2：原子受理、重复分支与一次派发

**文件：** 修改ArchiveStore、JdbcStore、Manager、Service、AgentRunContext；新增：

- `knowledge/src/test/java/com/zhku/knowledge/campusagent/submission/CampusRunSubmissionStoreIntegrationTest.java`
- `knowledge/src/test/java/com/zhku/knowledge/campusagent/CampusCreditAgentSubmissionTest.java`
- 在`agent-runtime/src/test/java/com/zhku/agent/agent/runtime/AgentRunContextTest.java`补指定ID构造测试。

**接口：** 消费C1.1类型，产出admitSubmissionOwned/findSubmissionOwned/submitOwned。指定ID构造重载冻结为：

```java
AgentRunContext.create(UUID runId, String prompt);
AgentRunContext.create(UUID runId, String prompt, Consumer<AgentRunEvent> observer);
AgentRunContext.create(UUID runId, String prompt, Consumer<AgentRunEvent> observer,
                       AgentRunObservationDispatcher dispatcher);
// Service：createContext(UUID runId, Request request)，保持三种观察模式。
```

- [ ] 写RED：同键20线程、同键异内容、跨用户同键、重复不创建Context/定时器/模型、原runId一致，旧create入口仍生成不同ID。代表断言：

```java
assertThat(claims).extracting(c -> c.record().runId()).containsOnly(originalRunId);
assertThat(claims.stream().filter(c -> c.decision() == Decision.CREATED)).hasSize(1);
assertThat(taskDispatches.get()).isEqualTo(1);
assertThat(submissionRows(userA, key)).isEqualTo(1);
assertThat(context.runId()).isEqualTo(originalRunId);
```

`claims`来自屏障同时启动的20个真库调用；taskDispatches统计执行器execute实际调用；submissionRows使用参数化SQL按user/key读取。模型请求计数另统计，不误断言正常轨迹只有一次模型请求。

- [ ] 真库注入commit前失败/commit后ACK丢失：前者无提交且无派发，后者有原关联且原调用派发0；按键读取和重复请求不能补派发。
- [ ] 实现第3.4节同连接准入事务；确认后才创建带原runId的Context与绑定任务。重复分支不调用原startOwned。
- [ ] 在Manager实现第4节pending登记：登记/查找只占短锁，等待使用有界Future且在锁外；结果明确后移除登记。等待者只取得Record和REPLAYED，不能拿到原调用的Admission；网络超时不撤销原调用，原调用的未知异常也不能由等待者接管。
- [ ] task提交拒绝、定时器拒绝、创建Context失败：对已受理Run尝试有界终结/保留未确认事实；任何补写失败都不再次execute。
- [ ] 指定ID重载只增加构造能力；保留旧随机入口、观察隔离、cleanup/termination语义。若出现共享生命周期修改需求，先报告最小差距，不统一两个循环。
- [ ] GREEN：真库并发、确认丢失、旧Manager和Runtime契约通过；保存数据库行与派发计数证据。

### Task C1.3：HTTP提交协议与持久按键查询

**文件：** 新SubmissionController；修改RunController、AccessService、异常处理、安全matcher、ControlledCampusTestFixtures；新增：

- `knowledge/src/test/java/com/zhku/knowledge/controlled/ControlledCampusSubmissionControllerTest.java`
- `knowledge/src/test/java/com/zhku/knowledge/controlled/ControlledCampusSubmissionHttpTest.java`

**接口：** 消费Submitted/Record，产出SubmissionReply及第3.1节HTTP。现有HTTP夹具submit方法生成显式键；增加submitWithKey，不能让产品自动补键。

- [ ] 写RED：缺键400、首发202、复用200、异内容409、按键GET200、他人404、维护者403、未登录401、CSRF拒绝零派发。响应断言：

```java
assertThat(first.statusCode()).isEqualTo(202);
assertThat(replay.statusCode()).isEqualTo(200);
assertThat(json(replay).path("runId").asText())
    .isEqualTo(json(first).path("runId").asText());
assertThat(json(replay).path("replayed").asBoolean()).isTrue();
assertThat(json(unknown).path("acceptance").asText()).isEqualTo("UNKNOWN");
```

`json`使用夹具现有JSON mapper，first/replay/unknown是实际HTTP客户端响应；UNKNOWN夹具只注入提交确认丢失，不模拟供应商。

- [ ] 写RED：Manager无记录但归档存在、重启INTERRUPTED、资料撤回、投影失败、DB不可读；LIVE/ARCHIVED/UNCONFIRMED互斥，GET无taskDispatch增长。
- [ ] 实现按键→当前身份和归属→在线或归档投影；投影不能绕过当前资料许可，不回放原HTTP答案。
- [ ] 保留旧HTTP权限/撤回测试，适配新的外层响应；旧匿名入口与重修接口不因新增matcher开放。
- [ ] GREEN：本任务真实回环HTTP与4A/3B权限回归通过；3C-1结束记录“网页尚未接线”。不把后端完成当整个3C完成。

**3C-1门禁：** 原子关联、并发只一次派发、未知不重放、本人持久查询、权限与保留清理全部通过。

## 9. 3C-2：资源约束与模型预算

### Task C2.1：原子用户准入与队列停止

**文件：** 新CampusAdmissionBudget；修改Manager和受控配置；新增：

- `knowledge/src/test/java/com/zhku/knowledge/campusagent/CampusAdmissionBudgetTest.java`
- `knowledge/src/test/java/com/zhku/knowledge/campusagent/CampusCreditAgentQueueDeadlineTest.java`
- 修改`knowledge/src/test/java/com/zhku/knowledge/web/CampusAgentSaturationContractTest.java`，区分旧匿名保留语义与新受控安全移除语义。

**接口：** `CampusAdmissionBudget.reserve(UUID userId)`返回唯一Reservation；`Reservation.release()`幂等。只在Manager admissionLock内计数；QUEUED/RUNNING/REMOVED由ManagedRun记录，终结发布使用一次性守卫。

- [ ] 写RED：同用户不同键并发不超过2、不同用户隔离、实例18上限、满额同键复用、REPLAYED释放临时预留、缓存不占在途、确认未知不乐观开放新准入。
- [ ] 写同键等待RED：20请求只有一个准入预留；原请求成功后其余返回同Run；原请求确认未知时所有等待者均无投递能力；等待5秒超时不创建新Run；同用户不同键仍受2个在途限制。
- [ ] 使用屏障堵住两个模型调用，排入任务后触发10秒期限/用户取消；验证remove成功、停止原因正确、零新模型调用、逻辑名额仅释放一次。代表断言：

```java
assertThat(worker.getQueue()).doesNotContain(queuedTask);
assertThat(providerCalls.get()).isEqualTo(callsBeforeExpiry);
assertThat(manager.findOwned(actor, queuedRunId).terminated()).isTrue();
assertThat(reservationReleased.get()).isEqualTo(1);
```

`queuedTask`由夹具捕获实际execute对象；到期使用可控单调时钟/定时器，不靠长sleep；另用真实定时器覆盖接线。

- [ ] 写取走/移除竞态RED：工作线程和定时器同步屏障，循环运行足够样本；零双重publish、零双重finalize、零超期外发，remove失败不在timer运行业务。
- [ ] 实现第4节短锁预留、安全队列移除与真实停止收尾；运行任务停止后保持物理占用直至实际调用返回，保留传输active保护。
- [ ] GREEN：10秒/30秒边界、队列真实占用、用户拒绝和既有cancel/cleanup/observer回归通过；记录取消收尾上界与实测。

### Task C2.2：每Run调用预算与有效供应商选项

**文件：** 新CampusExecutionBudget；修改Service、模型工厂、受控配置；新增：

- `knowledge/src/test/java/com/zhku/knowledge/campusagent/CampusExecutionBudgetTest.java`
- `knowledge/src/test/java/com/zhku/knowledge/tools/modelcall/CourseCreditModelBudgetProtocolTest.java`
- 扩展既有`CourseCreditSpringAiProtocolContractTest`、`CampusModelTransportContractTest`。

**接口：** 消费第4节默认值；预算在每Run创建，不跨Run共享；factory默认输出限制与Prompt最终有效限制都需协议验证。

- [ ] 写RED：第4次reserve失败；前3次未知/超时不退还；超长输入零外发；取消/超时后下一次reserve失败；不同Run预算隔离。
- [ ] 通过本地HTTP协议替身观察真实工厂发出的JSON与请求次数：

```java
assertThat(requestBody.path("max_tokens").asInt()).isEqualTo(2048);
assertThat(requestBody.hasNonNull("max_completion_tokens")).isFalse();
assertThat(requestBody.path("parallel_tool_calls").asBoolean()).isFalse();
assertThat(receivedRequests.get()).isEqualTo(1); // 单次故障model.call不自动重试
```

`requestBody`为协议替身实际接收并解析的JSON；分别返回429、500、坏JSON、空响应、length截断和慢正文，不以mock掉工厂的ChatModel证明传输行为。

- [ ] 对安装的Spring AI1.0.9核对defaultOptions与Prompt选项合并；固定maxTokens且禁止请求上调；保留internalToolExecutionEnabled(false)和现有工具参数裁决。不以新版本文档替代实际编译/报文证据。
- [ ] 实现reserve/checkDeadline守卫及有限错误分类；任务期限到达优先真实RUN_TIMEOUT，取消优先已记录的停止事实，不制造远端停止结论。
- [ ] GREEN：协议请求数、有效output上限、输入边界、慢流时限、取消后资源实际收敛和既有工具/领域测试通过；无真实供应商请求。

**3C-2门禁：** 用户与实例不越限、重复不增配额、队列期限内停止进入执行、停止名额不提前释放、模型请求和token选项有协议证据；总期限与实际资源收敛分别核验，不混为硬30秒退出。

## 10. 3C-3：网页接线与整体验收

### Task C3.1：提交状态模块与局部网页交互

**文件：** 新controlled-submission.js；修改controlled.js、controlled-results.js、CampusRun.vue；新增`agent/frontend/tests/controlled-submission.test.mjs`，扩展既有API/session/results测试。

**接口：** 状态模块提供`begin(userId, request)`、`confirm(reply)`、`restore(userId)`、`forget(userId)`；begin返回`{submissionKey, requestSnapshot}`。API `startRun(body,{submissionKey,signal})`必填键，新增`findSubmission(key,{signal})`。

- [ ] 写RED：断网键不变、自动POST次数1、手动同次重发使用冻结body、刷新只恢复键不恢复原文、缺少快照禁止重发、重新登录同账号查询、账号切换清空、迟到响应不能覆盖新身份。

```javascript
assert.equal(posts.length, 1)
assert.equal(posts[0].headers['Idempotency-Key'], originalKey)
assert.equal(restored.submissionKey, originalKey)
assert.equal(restored.requestSnapshot, undefined)
assert.equal(sessionStorageText.includes('合成课程问题'), false)
```

测试使用可注入UUID生成器、内存Storage和fetch替身，不实际保存登录凭据；posts捕获实际client请求。

- [ ] 实现metadata最多5条、响应字段校验、身份换代、LIVE/ARCHIVED投影和60秒GET轮询；网络未知不自动发POST。
- [ ] 页面增加“确认本次提交”“重新发送本次提交”（仅快照存在）和明确新建说明，保留现有视觉/出处组件；401后同账号认证再查询，显式logout清除。
- [ ] 对归档INTERRUPTED显示“本次处理已中断，未自动继续”；它是归档事实，不伪造Runtime失败/取消状态。ADMITTED且缺少本进程live状态显示“已记录，执行情况暂未确认”，不继续标为正在运行。
- [ ] GREEN：全部现有前端测试+新增测试、生产构建通过；验证404文案不声称未受理，UNCONFIRMED不显示旧答案。

### Task C3.2：真实HTTP故障矩阵、不同PID与代理入口

**文件：** 新非供应商runner及.cmd；新增：

- `knowledge/src/test/java/com/zhku/knowledge/controlled/SubmissionHttpRestartTest.java`
- `knowledge/src/test/java/com/zhku/knowledge/controlled/SubmissionHttpRestartProbe.java`
- `knowledge/src/test/java/com/zhku/knowledge/controlled/SubmissionResponseLossHttpTest.java`
- 扩展真实HTTP夹具与既有Nginx验收脚本；不加产品故障注入开关。

**接口：** runner仅接受上述3C测试和明确列出的既有回归；显式校验选类存在、测试实际执行数>0、迁移版本及provider=false。遵循普通用户.cmd启动、凭据检查和容器实际退出规则，不使用rg作为脚本依赖。

- [ ] 写RED：真实socket中转丢弃POST响应，但后端完成受理；独立HTTP客户端按键找到原Run，再POST同键，派发计数不增长。
- [ ] 双JVM/PID测试：进程A在commit后派发前退出；进程B登录同一持久userId后查原键，archiveStatus=INTERRUPTED，模型/工具计数0。另测已FINALIZED任务重启可查，结果受当前来源许可控制。
- [ ] 真实HTTP跑20并发同键、A/B相同键、满额同键查询、确认丢失、DB不可读、撤回、停用、取消取走竞态；记录提交数/Run数/taskDispatches/模型外发数四组事实。
- [ ] 在实际Nginx同源入口确认Idempotency-Key转发、200/202/409/429/503原样传递、no-store、body限额和旧路径拒绝；生产代理不重试POST。无需修改代理布局。
- [ ] 用浏览器人工走网络断开→刷新→重新登录→确认原任务，完成退出/切换用户、撤回后隐藏答案和新建键变化；浏览器替身证据与真实HTTP证据分别标记。
- [ ] 验证q/STOP/EOF关闭后PID、端口、worker/timer、模型传输和owner资源实际退出；普通Windows PowerShell5.1设置本次会话UTF-8编码后验中文，不改全局编码。
- [ ] GREEN后生成本轮验收矩阵；未验证项明确写未验证，不用历史报告补齐，不额外调用真实模型。

### Task C3.3：收口、文档与回退演练

**文件：** 更新knowledge/README.md、agent/frontend/README.md、原路线3C注记、当批证据与验收说明。

- [ ] 写操作说明：键与新任务区别、保留期、UNCONFIRMED/INTERRUPTED解释、刷新后不能无原文重发、配额与期限、零重试和无费用封顶。
- [ ] 检查真实源码/迁移/选类与证据哈希对应；把完成数、跳过数、失败数分开，基线与本轮结果不重复累计。
- [ ] 回退演练使用独立临时库：先停新准入，保留submission/archive；回退源码只恢复检查点白名单文件，不整体覆盖仓库dirty。旧版本受控POST不得重新开放，读取可以保留；不DROP新表、不删除未知记录、不修改已应用Flyway历史。
- [ ] 如果旧二进制不能识别新迁移或新查询，保持受控服务关闭并报告限制；不得通过关闭校验或接回匿名接口绕过。
- [ ] 完成以下总门禁，记录3C完成并停止；不生成3D执行任务、不启用恢复扫描、不自动发布。

## 11. 执行命令与验证顺序

以下是**实施时**的入口要求；新增runner/测试完成之前不可声称命令已可用。本计划未运行任何下列验收。

现有普通用户环境检查入口：

```powershell
.\knowledge\scripts\start-controlled-manual-demo.cmd -CheckEnvironment
```

新增runner创建并通过自测后：

```powershell
.\knowledge\scripts\verify-campus-submission-temp-db.cmd -Batch 3C-1 -Port 55437
.\knowledge\scripts\verify-campus-submission-temp-db.cmd -Batch 3C-2 -Port 55437
.\knowledge\scripts\verify-campus-submission-temp-db.cmd -Batch 3C-3 -Port 55437
```

`Batch`冻结映射：3C-1选C1.1–C1.3新测试及相关Schema/权限；3C-2选C2.1–C2.2及C1门禁回归；3C-3选C3.2及上述全部相关门禁、4A/3B重启/撤回回归。每次独立临时容器、全迁移到V21；不得运行旧脚本的V19/V20断言后忽略失败。既有runner若需要支持V21，显式扩展最高版本参数/断言并新增脚本验证，保留历史报告。

前端在`agent/frontend`目录，通过已存在工具解析器确定实际可执行文件：

```powershell
. ..\..\scripts\Resolve-ZhkuNode.ps1
$zhkuNodeTools = Get-ZhkuNodeToolchain
$env:PATH = $zhkuNodeTools.Directory + ';' + $env:PATH
$zhkuFrontendTests = @(Get-ChildItem -LiteralPath '.\tests' -Filter '*.test.mjs' |
    ForEach-Object { $_.FullName })
& $zhkuNodeTools.Node --test @zhkuFrontendTests
if ($LASTEXITCODE -ne 0) { throw '前端测试失败' }
& $zhkuNodeTools.Npm run build
if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
```

runner内部复用已有Maven解析与临时库机制，测试源码先创建再选类；不得使用空测试成功、静默skip或真实供应商类通配符。Maven按本批选类运行，保存新XML；失败定位后只重跑受影响范围，最后一次相关回归通过即可，不无故重复全仓测试。

## 12. 总门禁与停止条件

| 编号 | 场景 | 通过事实 |
| --- | --- | --- |
| A1 | 同用户同键同内容20并发 | 提交1、Run1、派发1；模型请求符合正常轨迹及≤3预算 |
| A2 | 同键不同内容 / 跨用户同键 | 前者409且不派发；后者各自独立且不可互查 |
| A3 | 用户/实例满额重发原键 | 原记录可返回，不新增Run或名额 |
| A4 | commit确认未知 | 原调用派发0；记录按实际提交结果存在或不存在，查询不补投递 |
| A5 | 派发后HTTP响应丢失 | 按键找回原Run，再POST不增加任务派发 |
| A6 | commit后派发前退出、不同PID重启 | 原关联保持、INTERRUPTED、零自动恢复 |
| A7 | 排队期限/取消与取走竞态 | 过期无外发、单次收尾、正确停止原因、物理/逻辑名额证据分开 |
| A8 | 模型429/500/慢正文/解析失败/截断 | 单次调用无重试、总次数≤3、有效max_tokens=2048、资源实际收敛 |
| A9 | 撤回/停用/账号切换/迟到响应 | 无旧正文回放、无跨账号结果、无权限旁路 |
| A10 | 缓存回收、TTL边界、清理事务失败 | 保留期内仍关联原任务；清理不半删，不把404当否定证明 |
| A11 | 实际Nginx与普通终端 | 键与状态正确转发，中文可读，关闭后PID/监听/owner收敛 |
| A12 | 4A/3B/领域回归与回退 | 权限、资料撤回、Session重启、工具守卫保持；回退不开放旧受控POST |

**立即停止并报告差距的条件：** 需要新增恢复输入/自动派发、必须合并两个循环、无法区分确认未知与明确拒绝、真实资源释放无法验证、临时库与原演示库隔离失败、出现权限旁路、测试runner误调用真实供应商、或需要存真实个人原文才能实现确认查询。

**收口输出：** 三批实际状态、变更文件和迁移、总门禁结果、未验证限制、回退检查点位置、资源退出事实。计划本身不代表这些结果已经产生。

## 13. 文档依据与版本边界

最终执行状态：用户批准的V21/3B有限兼容修复已完成，迁移契约RED→GREEN、3B实际恢复及普通终端完整产品启动通过。最终3C-3默认明确选取前两批及相关回归55类371项，全通过、无skip/漂移/资源残留；前端46项和构建通过，供应商0。本节下方初期阻塞注记是历史时点，失败保留，当前以[最终验收](../../evidence/milestone-3c/2026-10-07/3c-3/acceptance.md)为准。阶段结束，不继续3D。

范围修订（二，已授权）：用户回复“批准”，允许仅修改`ControlledConversationConfiguration.java`的同库/V20应用版本守卫，新增`ControlledConversationMigrationContractTest.java`及必要测试白名单/证据。保留同DataSource、V20成功应用、当前版本不低于20，未迁移/缺V20/失败V20/跨库均拒绝；不改Session领域、SQL历史或授权事务。修复前增量和原守卫保存于`3c-3/v21-authorized-checkpoint/`，按RED→最小修复→3B/产品真实启动→整体3C验证执行，供应商0。

2026-10-07本轮执行注记：用户授权的requireCurrent有界等待修订已完成，物理2/原2秒/无许可缓存保持；3C-1和3C-2已验收。3C-3本轮357项相关后端及46项前端通过，但完整产品启动被既有3B守卫`current==20`拒绝V21。该生产文件不在原白名单，新增有限兼容修订仅为待授权方案，尚未实施；[精确STOP与候选范围](../../evidence/milestone-3c/2026-10-07/3c-3/STOP-v21-conversation-assembly.md)及[本轮验收](../../evidence/milestone-3c/2026-10-07/3c-3/acceptance-partial.md)记录事实。不得用关闭3B/更改鉴权或历史结果宣布全部完成；本轮不启动3D。

- PostgreSQL16：[事务隔离](https://www.postgresql.org/docs/16/transaction-iso.html)、[唯一约束](https://www.postgresql.org/docs/16/ddl-constraints.html)、[INSERT](https://www.postgresql.org/docs/16/sql-insert.html)。Context7核对了ON CONFLICT与Read Committed并发语义；本实现优先复用既有owner锁，不新增盲目事务重试。
- Spring AI通过Context7取得的[1.0.3选项参考](https://github.com/spring-projects/spring-ai/blob/v1.0.3/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/openai-chat.adoc)及[手动工具参考](https://github.com/spring-projects/spring-ai/blob/v1.0.3/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools.adoc)仅作为1.0.x设计依据；项目锁定1.0.9，不能把1.0.3资料当作该版本API已经验证。实施必须以安装依赖编译和真实协议替身报文闭环。
- 既有模型工厂已显式maxAttempts(1)，受控配置已2/2/16和30秒；本计划是在此基础上补齐缺口，未实施新功能或运行测试。
