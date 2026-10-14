# T06 任务管理与身份边界说明（与实际源码一致）

适用范围：`knowledge/src/main/java/com/zhku/knowledge/ruleagent/**`、`web/RuleAgentTaskController.java`、
`security/PreviewIdentityRepository.java`、`agent-runtime/AgentRunContext.java`。
所有结论均标注源码位置；**未经执行的场景一律标"未验证"**。

| 项 | 值 |
| --- | --- |
| 基线 HEAD | `9d254f199f437a020d3bc2f13f94759a9e6264e0` |
| schemaVersion | `3`（`RuleAgentContract.SCHEMA_VERSION`） |
| 共享合同 | v3，**本轮未改**（本文档是说明，不是合同） |

---

## 0. 本轮身份复验结果（同一基线）

| 项 | 结果 |
| --- | --- |
| 夹具 | `correctPassword="t06-tempdb-correct-pass"`(23)、`wrongPassword="t06-tempdb-wrong-password"`(26)、`unknownAccountPassword="t06-tempdb-unknown-pass"`(25)，全部 16–64 位可打印 ASCII |
| 专用临时库 | `zhku-t06-tmpdb-2059`，库 `zhkuagent_rulecheck_e2e`，127.0.0.1:55686，无挂载卷；迁移前表计数 **0** |
| 测试 | `RuleAgentReviewedIdentityTempDbTest` → **1 run / 0 fail / 0 error**（11.28s） |
| 覆盖 | 合规正确口令返回主体（userId 相等、`role=USER`、`authRevision=0`）；合规但内容错误被拒；合规未知账号被拒；传入 `char[]` 被清空；`status()`→`closed=true,inFlight=0,liveThreads=0`；已审核资料安装仍通过（revision=`2025.9.2`） |
| 未执行 | SIMULATED 模式下的真实身份／资料 **HTTP 整链**、浏览器、任何在线调用（按任务要求不提前进入） |
| 结论 | 上一轮失败确为夹具问题；**生产代码零改动**，认证规则未放宽 |

---

## 1. task / turn / run 三层

| 层 | 源码载体 | 含义 |
| --- | --- | --- |
| **task** | `RuleAgentTaskStore.Slot`（`id` + `owner`） | 一次持续的学生查证会话，跨轮保持稳定。`Slot.question`／`questions` 原样保留，`expires = 创建时刻 + 15 分钟` |
| **turn** | `Slot.turn` 与 `Slot.history` 中的 `Turn(turnId, runId, message, conditions, status, attempts)` | 一轮"提问或补充"。创建时即有一条 `Turn`（ACTIVE/attempts=0） |
| **run** | `Slot.context`（`AgentRunContext`，`runId()`） | 一次真实执行窗口。`execute()` 用当前 `Ticket.context` 调用 `Work.run` |

### 澄清续答：为什么保留 task 却新建 run

`message()` 在通过校验后（`RuleAgentTaskStore.message`，锁内第二段）：

```java
slot.previous=previous; slot.published=null; slot.version++; slot.runs++;
slot.turn=UUID.randomUUID(); slot.context=AgentRunContext.create(slot.question); slot.waitingExpires=null;
slot.history.add(new Turn(slot.turn, slot.context.runId(), request.message(), slot.conditions, TaskStatus.ACTIVE, 0));
```

- **保留 task**：`Slot.id`、`Slot.owner`、`Slot.question`、`Slot.conditions` 不变 → 归属与已确认条件延续，历史可追溯。
- **新建 run**：`slot.context = AgentRunContext.create(...)` → 新 `runId`；`slot.turn` 换新 → 新 turn。
  动机是"等待时结束本轮线程；补充后相同 task、新 turn/run"（合同 v3 §1）：**不能让原线程长期等待学生**，
  也便于 `cancel` 用 `runId` 精确定位当前窗口。

## 2. 已知条件与"最小历史"在哪里，什么真正进入后续运行

| 内容 | 存放位置 | 是否进入下一次运行 |
| --- | --- | --- |
| 已确认条件（如 `courseType=PUBLIC`） | `Slot.conditions`（`Map<String,String>`，逐轮合并） | **是**。`execute()` 构造 `new RuleAgentRunner.Input(owner, taskId, version+1, question, questions, ticket.conditions())` |
| 原始问题与拆分后的子问题 | `Slot.question` / `Slot.questions`（按 `[；;]` 拆分，1–4 项，否则 `NEEDS_CONFIRMATION`） | **是**（同 `Input`） |
| 逐轮消息、状态、模型尝试次数 | `Slot.history`（`List<Turn>`） | **否**。仅经 `history(owner,task)` 供读取/审计，**不进入 `Input`** |
| 上一轮已发布结果 | `Slot.previous`（续答期间保留），`Slot.published`（已发布快照） | 仅用于 `work.revalidate(previous)` 重验证据；`publish` 成功后 `slot.previous=null` |

**要点**：模型侧实际只看到"问题 + 子问题 + 已确认条件"，**不是**完整对话历史；历史只作宿主侧留痕。
`RuleAgentRunner.Input` 的 `version` 传的是 `ticket.version()+1`（即本轮将要发布的版本号）。

## 3. 可信主体来自什么后端／仓储，Session 如何参与归属

- **后端来源**：数据库表 `preview_identity(user_id, login_name, password_hash, role, enabled, auth_revision)`
  （迁移 `V19__create_preview_identity_and_run_ownership.sql`）。口令为 BCrypt，且迁移带 CHECK 约束
  （`^[a-z][a-z0-9-]{2,31}$`、`^\$2[aby]\$(10|11|12)\$...$`、`role IN ('USER','MAINTAINER')`）。
- **校验**：`PreviewIdentityRepository.authenticate(loginName, char[])` → 先做 `loginName` 正则与
  `validPassword`（**16–64 位、全部可打印 ASCII**）预检，再查库并比对 BCrypt；成功返回
  `PreviewPrincipal(TrustedActor(userId, role, authRevision))`。**传入的 `char[]` 在 `finally` 中被清零**。
- **持续许可**：`requireCurrent(actor)` 每次按 `user_id` 重查 `role/auth_revision/enabled`，
  任何不匹配 → `AUTH_REVOKED`；注释明确"不在主体中缓存账号许可，停用再启用也不能复活旧 Cookie"。
  控制器在**操作前与操作后各校验一次**（`RuleAgentTaskController.invoke`）。
- **Session 参与归属**：Session 属性 `ZHKU_RULE_AGENT_OWNER` 存 `Binding(TrustedActor, Owner)`，其中
  `Owner = (actorId=userId, sessionNonce=UUID.randomUUID())`——**同一用户重新登录会得到新的 nonce**，
  因此旧任务查不到（既有 HTTP 测试断言重登后 `GET /tasks/{id}` → 404）。
  `requireActor` 比对 Session 中 Spring Security 的 principal 与当前 actor，不一致即 `AUTH_REVOKED`。
  归属判定**不使用客户端传入的 userId**；请求体多余字段会被 400 拒绝（`payload()` 严格字段集校验）。

## 4. 任务 version 与资料 revision／checksum 不是同一种版本

| 名称 | 载体 | 语义 | 用途 |
| --- | --- | --- | --- |
| 任务 **version** | `Slot.version`（`long`，≤ `MAX_SAFE_VERSION = 9007199254740991`） | 任务内单调递增的**状态版本** | 拒绝旧续答与并发更新 |
| 资料 **referenceRevision** | `ResultEnvelope.referenceRevision`（如 `"2025.9.2"`） | 已审核资料的版本标识 | 声明结论基于哪一版资料 |
| 正文 **checksum** | `Evidence.checksum` / `knowledge_chunk.content_checksum` | 单段正文的 SHA-256 | 原文读取时逐字段比对 |

- **续答必须携带 `expectedVersion` 且与已发布快照版本一致**，并同时匹配 `continuationToken`，否则 `409 TASK_CONFLICT`（`checkWaiting`）。
- **并发窗口**：`message()` 先在锁内取 `version`，在**锁外**做 `work.revalidate(previous)`（真实证据重验），
  再回锁内比对 `slot.version != version` → `409 CONFLICT`，避免两个请求拼接同一轮条件（TOCTOU）。
- **`snapshot()` 的 ACTIVE 快照用 `slot.version`**，且合同要求 `result.publishedVersion() < version`
  （新 run 可以带上一轮已核验解释，但不能冒充本轮新结论）。
- **取消**要求 `published==null`、`expectedVersion` 匹配、且 `runId` 匹配当前 run，三者缺一即 `409`。

## 5. 内存态任务：期限、容量、重启、多实例与恢复边界

源码类注释即定义：**"单机短期任务存储，主体与 Session 共同隔离；不承诺跨重启恢复。"**

| 维度 | 实际实现 | 边界说明 |
| --- | --- | --- |
| 存储 | `LinkedHashMap<UUID,Slot>` + `LinkedHashMap<RequestKey,StoredRequest>`，全内存 | **无数据库持久化** |
| 任务 TTL | `taskTtlMillis = 900000`（15 分钟），`Slot.expires`；`admit()` 剪除已发布且过期的任务 | 未发布任务在 `close()` 时转为停止请求 |
| 等待 TTL | `waitingTtlMillis = 300000`（5 分钟），`Slot.waitingExpires`；超时续答 → `409 TASK_EXPIRED` | 等待不占 run 执行时间，但受 TTL 限制 |
| 容量 | `DEFAULT_LIMITS = Limits(maxTasks=128, maxTasksPerActor=8, maxRequestKeys=512)` | 超限 → `429 TASK_CAPACITY`；**满容量仍先返回已有幂等记录** |
| 幂等键 | `RequestKey(owner, CREATE/MESSAGE/CANCEL, clientRequestId)`，含 payload 指纹 | 同键同载荷 → 返回原 `Accepted(duplicate=true)`；同键异载荷 → `409 CONFLICT` |
| 进程重启 | **不支持恢复**。重启后任务、幂等键、已发布结果全部丢失 | 旧 `clientRequestId` 重放在新进程里会变成**新建任务**，不会幂等去重 |
| 多实例 | **不支持**。没有共享存储或路由粘滞保证 | 跨实例续答/取消不可用（未实现） |
| 追加上限 | `maxTaskRuns=3`、`maxClarificationTurns=2`、`maxModelAttemptsPerTask=12` | 超限 → `429`；第三轮不再发出追问（会被改写成 `STOPPED/BUDGET_EXCEEDED`） |

以上均为**内存态边界**，文档中不得表述为持久化或可恢复。

## 6. 取消受理 / 任务终止 / 资源实际退出（三层，不能互相替代）

| 层 | 判定依据（源码） | 含义 |
| --- | --- | --- |
| **① 取消受理** | `cancel()` 返回 `Accepted`（202），要求 `context.requestStop(USER_REQUEST) == true` | 仅表示**停止请求已记录**。此刻任务快照 `terminated` 仍为 `false`（既有 HTTP 测试断言取消后 `terminated=false`） |
| **② 任务终止** | `publish()` 成功写 `slot.published`：要求 `result.terminated()` **且** `context.termination().isDone()` **且** `version == ticket.version+1` | 任务状态变为终态（STOPPED/FAILED/RESOLVED）；`context.state()==CANCELLED` 时**迟到的成功产物不得覆盖任务** |
| **③ 资源实际退出** | `AgentRunContext.publishTermination()` 只证明**清理收敛**（源码注释：*"发布标记不证明观察器已返回或线程已退出"*）；`signalTermination()` 要求先完成 `cleanupOnce` | 是否真的退出须另查：`RuleAgentTaskStore.activeJobs()==0`、执行器已 `shutdown`、身份仓储 `status().liveThreads()==0` |

失败路径：`publishFailure` 把 `TASK_EXECUTION_UNAVAILABLE` 等落为 `FAILED/STOPPED`，
文案明确"不代表资料没有规定"，**与 HTTP 503（服务不可用）分离**。

## 7. 状态表（与源码一致）

状态集合与 `terminated`／`runState` 约束来自 `RuleAgentContract.TaskSnapshot` 紧凑构造器：

| TaskStatus | 允许的 RunState | terminated | 其他约束 |
| --- | --- | --- | --- |
| ACTIVE | 非终态 | `false` | `continuationToken == null`；若有 result，则 `publishedVersion < version` |
| WAITING_USER | INCOMPLETE | `true` | 恰好 1 项澄清；`continuationToken != null` |
| RESOLVED | SUCCEEDED 或 INCOMPLETE | `true` | 无待答澄清；`publishedVersion == version` 且 `originatingRunId == runId` |
| STOPPED | CANCELLED 或 INCOMPLETE | `true` | issue 必含 `CANCELLED`／`BUDGET_EXCEEDED`／`NO_PROGRESS` |
| FAILED | FAILED | `true` | issue 必含 `PROCESSING_FAILURE` |

允许的转换（`RuleAgentContract.canTransition`）：`a == b` 恒允许；`ACTIVE → 任意`；
`WAITING_USER → ACTIVE | STOPPED | FAILED`。**终态不能回到 ACTIVE**，`WAITING_USER` 也不能直接转 `RESOLVED`。

```text
（创建）──► ACTIVE ──► WAITING_USER ──(有效补充)──► ACTIVE
              │             │
              ├────────────►├──► STOPPED / FAILED
              └──► RESOLVED / STOPPED / FAILED（终态，不可回退）
```

> 说明：上表是**合同与存量的校验边界**；`RuleAgentTaskStore` 实际只发布
> ACTIVE/WAITING_USER/RESOLVED/STOPPED/FAILED 五种终态快照，不自行做"任意状态迁移"。

## 8. 未验证（明确标注，不得用其他层替代）

- **HTTP 层**（登录、归属隔离、原文读取与资格撤回、错误码分流）：本轮**未执行**（唯一项是身份/资料装配）。
  既有 `RuleAgentTaskHttpTest` 目前仍因宿主端口冲突记"**尚未完整验证**"。
- **真实数据库身份**：本轮已验证（见 §0）；**模拟执行器 HTTP**：未执行；**真正在线链路**：未执行（T05 未通过，额度已关闭）。
- 多实例／重启行为：源码明确不支持，**未做运行时验证**（无实现可验）。
- 未被既有测试覆盖的状态转换组合：未逐一验证。

**三层分开记账**：本轮的通过只属于"真实数据库身份 + 专用临时库资料安装"层，
**不能**替代模拟执行器 HTTP 层或在线链路层。
