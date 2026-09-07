# 开源项目研究与可恢复执行 Agent：架构设计

## 1. 文档状态

| 项目 | 内容 |
| --- | --- |
| 设计日期 | 2026-09-07 |
| 当前代码基线 | `8ed4707 fix(agent): harden stream execution lifecycle` |
| 当前分支 | `codex/agent-run-context` |
| 设计类型 | 个人二次开发、分层模块化单体 |
| 实施状态 | 设计已讨论，尚未开始实现 |
| 部署边界 | 单应用实例、单 PostgreSQL、公开 GitHub 仓库 |

本文将现有教学项目重构为“开源项目研究与可恢复执行 Agent”。设计保留已经验证的
Agent 运行时生命周期能力，替换恋爱业务，新增持久任务、GitHub 调研、版本化项目
知识库、可重连事件流和服务重启恢复。

本文是实现约束，不是完成证明。除运行时生命周期治理外，GitHub 调研、PGVector
知识库评测和断点续跑目前都不能作为已交付能力写入简历。

---

## 2. 当前仓库事实

### 2.1 已经存在并有证据的能力

- 每次 Agent 运行拥有独立 `AgentRunContext` 和 `runId`；
- `state`、`currentStep` 和消息历史不再作为 Agent 实例共享运行态；
- think/act 通过步骤级不可变 `Decision` 传递结果；
- 流式任务句柄在提交执行器前绑定；
- 停止请求、工作线程清理和 termination 已分离；
- Spring 管理专用有界执行器，每次请求创建独立 Agent；
- 拒绝、取消、SSE 失败、cleanup 幂等和终止顺序具有行为测试；
- 当前相关回归包含 25 项测试，并有 5 轮关键生命周期重复验证。

这些事实只能证明当前测试范围内的单 JVM 生命周期行为，不能证明持久任务恢复、
生产吞吐量、高并发或分布式调度。

现有证据入口：

- [AgentRunContext 证据账本](../../evidence/AgentRunContext-证据账本.md)；
- [Agent 与执行器生命周期决策](../../decisions/2026-09-05-agent-executor-lifecycle.md)；
- [运行时生命周期完整复盘](../../retrospectives/02-Agent运行时生命周期问题-完整复盘与学习指南.md)。

### 2.2 尚未形成的能力

- 仓库中不存在可用的 `GitHubIssueTool`；
- PGVector 配置未启用，并且仍依赖恋爱文档加载器；
- 没有项目文档快照、固定 RAG 评测集和检索质量数据；
- `task` 包中的未跟踪文件是空骨架，不构成任务持久化；
- 没有独立进程重启恢复证据；
- 原生 `EventSource` 当前把参数拼入 URL，不适合传递 API Key；
- 前端、Controller、RAG Bean、测试和运行资源仍包含恋爱业务。

### 2.3 仓库治理前置条件

正式实施前必须先审计当前未跟踪文件，区分正式资产、个人笔记和实验草稿。任何阶段
都不得使用 `git add .`。根目录当前未发现许可证文件，公开发布二次开发成果前必须
核验上游许可并保留来源说明。

---

## 3. 产品目标与非目标

### 3.1 产品目标

用户通过 API Key 提交公开 GitHub 仓库和研究问题，系统完成：

1. 持久接纳任务并返回 `taskId`；
2. 生成有限、可校验、可版本化的研究计划；
3. 查询 GitHub Issue 并采集白名单项目文档；
4. 按 commit SHA 构建或复用项目知识库快照；
5. 分步骤执行研究任务并持续记录事件；
6. 允许客户端通过 SSE 断线重连和补收事件；
7. 支持用户显式取消、执行期限和调用配额；
8. 服务重启后从最近确认成功的步骤继续；
9. 生成带仓库版本和原始链接的研究报告。

### 3.2 第一版非目标

- 私有仓库和 GitHub OAuth；
- 多应用实例、分布式租约和跨节点取消；
- 外部副作用操作的 exactly-once；
- 任意终端命令、代码执行和仓库写操作；
- 完整源代码符号分析；
- 完整 Event Sourcing；
- 用户注册、密码、角色和管理后台；
- 极限 QPS、生产 SLA 和生产故障率承诺；
- 将图片搜索 MCP 包装成项目研究能力。

---

## 4. 已确认的核心决策

1. SSE 连接只是任务观察通道，不拥有业务任务；
2. API Key 映射服务端用户身份，不建设完整账号系统；
3. 第一版只保证单实例重启恢复；
4. 恢复粒度为完整步骤，执行语义为至少一次；
5. 可恢复 Agent 只使用只读或具备幂等能力的工具；
6. 先生成并持久化有限计划，计划调整必须版本化；
7. 使用关系型当前状态表和追加式任务事件表；
8. 第一版只研究公开仓库，使用服务端 GitHub Token；
9. 通过 GitHub REST API 采集白名单文档，不执行 `git clone`；
10. 总体架构采用分层模块化单体；
11. PostgreSQL 是持久任务队列，线程池只承载实际运行任务；
12. GitHub 限流和暂时故障进入 `WAITING_RETRY`，worker 不睡眠；
13. 公开仓库知识库按 commit 构建可复用的不可变快照；
14. 新前端使用支持认证头和游标重连的 Fetch SSE 客户端；
15. 恋爱领域采用旁路替换，研究主链路稳定后再删除。

---

## 5. 总体架构

```text
浏览器 / API Client
        │ X-API-Key
        ▼
┌─────────────────────────────────────────────┐
│ 接口层                                      │
│ TaskController / TaskEventController        │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│ 任务应用层                                  │
│ TaskApplicationService / Quota / Recovery   │
└──────────────┬────────────────┬─────────────┘
               │                │
               ▼                ▼
┌──────────────────────┐  ┌───────────────────┐
│ 研究执行层           │  │ PostgreSQL        │
│ Plan / Step / Agent  │  │ Task/Step/Event   │
└──────────┬───────────┘  │ Snapshot/Metadata │
           │              └─────────┬─────────┘
           ├────────────────────────┤
           ▼                        ▼
┌──────────────────────┐  ┌───────────────────┐
│ GitHub REST Adapter  │  │ PGVector          │
│ Issue / Tree / Blob  │  │ Document Chunks   │
└──────────────────────┘  └───────────────────┘
```

建议的包边界：

```text
com.yupi.yuaiagent
├── research
│   ├── agent
│   ├── application
│   ├── domain
│   └── web
├── task
│   ├── application
│   ├── domain
│   ├── infrastructure
│   └── recovery
├── github
│   ├── tool
│   ├── application
│   └── infrastructure
└── knowledge
    ├── ingestion
    ├── retrieval
    ├── evaluation
    └── infrastructure
```

第一版不同时迁移 Java 根包名。产品品牌和根包机械重命名在功能稳定后单独完成。

---

## 6. 生命周期模型

### 6.1 四种不同寿命

```text
AgentTask
= 跨请求和服务重启存在的持久业务任务

ExecutionAttempt
= 某个进程对任务的一次执行尝试

AgentRunContext
= 当前 JVM 中一次 attempt 的临时运行上下文

SSE Subscription
= 某个客户端观察任务事件的临时连接
```

数据库不保存线程、`FutureTask`、`SseEmitter`、`ChatClient` 或完整运行时对象。

### 6.2 任务状态

```text
CREATED → PLANNING → READY → RUNNING → SUCCEEDED
                         ▲       ├→ CANCEL_REQUESTED → CANCELLED
                         │       ├→ FAILED
                         │       ├→ TIMED_OUT
                         │       └→ WAITING_RETRY
                         │                │
                         └────────────────┘

遗留 RUNNING / STARTING → 恢复扫描 → READY
```

终态为 `SUCCEEDED`、`FAILED`、`CANCELLED` 和 `TIMED_OUT`，终态不可再次迁移。

`CANCEL_REQUESTED` 只表示用户取消已被持久接受。deadline 到达时记录
`deadline_exceeded_at` 和 attempt stop reason；在 worker 尚未完成收尾期间，任务仍保持
非终态并通过查询字段暴露 `terminationPending=true`，不能提前宣称已经 `TIMED_OUT`。

### 6.3 步骤状态

```text
PENDING → RUNNING → SUCCEEDED
                  ├→ WAITING_RETRY → RUNNING
                  ├→ FAILED
                  └→ INTERRUPTED

PENDING → SKIPPED
```

只有成功结果已经提交数据库的 `SUCCEEDED` 步骤才是恢复检查点。

### 6.4 生命周期不变量

1. 一个任务同一时刻最多只有一个活动执行尝试；
2. 同一任务最多只有一个活动计划版本；
3. 已成功步骤不能被静默覆盖或重复执行；
4. 停止请求不能冒充真实终止；
5. SSE 断开、超时和发送失败不能修改业务任务状态；
6. 外部调用不在数据库事务中执行；
7. 状态变化和对应任务事件在同一事务中提交；
8. worker cleanup 完成后，持久任务才提交最终终态；
9. `AgentRunContext.termination` 晚于 worker cleanup 和持久终态提交；
10. 应用关闭中断当前 attempt，但不把任务标记为用户取消；
11. 超过任务 deadline 后不能启动新的业务步骤；
12. 恢复只能跳过已确认成功的步骤；
13. cleanup 必须幂等；cleanup 抛出异常时不能把任务发布为成功。

如果业务步骤和报告已经完成，但 cleanup 失败，执行尝试记录 `CLEANUP_FAILED`，任务进入
`FAILED`，已生成报告只能作为未完成交付物查看。termination 必须在 cleanup 异常已经
记录后发出，不能吞掉清理失败并伪装成成功。

---

## 7. 持久化模型

### 7.1 API 调用方

`api_consumer` 保存：

- 调用方标识和展示名；
- API Key 哈希与非敏感前缀；
- 是否启用；
- 每用户并发任务上限；
- 每任务模型调用上限；
- 每任务 GitHub 调用上限；
- 创建与更新时间。

数据库、日志和事件中不保存完整 API Key。

### 7.2 任务与计划

`agent_task` 保存任务所有者、目标仓库、研究问题、状态、活动计划版本、deadline、
最后活动时间、最后订阅时间、调用计数、下一个事件序号、乐观锁版本和生命周期时间。

`research_plan` 保存任务、计划版本、状态、调整原因以及激活和废弃时间。同一任务只能
存在一个活动版本。

`task_step` 保存任务、计划版本、步骤序号、受控步骤类型、结构化输入、状态、尝试数、
结果摘要、错误分类、重试时间和生命周期时间。

### 7.3 执行尝试

`task_execution_attempt` 保存：

- `task_id`、`run_id` 和执行器实例标识；
- `STARTING/RUNNING/SUCCEEDED/FAILED/INTERRUPTED/REJECTED`；
- stop reason；
- heartbeat；
- 开始和结束时间；
- 裁剪后的错误摘要。

`task_step_attempt` 保存步骤每次尝试的编号、幂等键、请求摘要、结果摘要、错误分类和
时间。完整 GitHub 响应、完整模型上下文和敏感头不进入该表。

### 7.4 事件

`task_event` 保存 `task_id`、单调递增的 `event_seq`、事件类型、版本化载荷和时间。

关键唯一约束：

```text
unique(task_id, event_seq)
unique(task_id, plan_version, step_no)
unique(task_step_id, attempt_no)
unique(idempotency_key)
```

事件序号通过锁定任务行并递增 `next_event_seq` 分配，禁止使用 `MAX(seq) + 1`。

### 7.5 事务边界

步骤执行分为三个阶段：

```text
事务 A：标记步骤运行、创建 attempt、追加开始事件
事务外：调用模型、GitHub 或 PGVector
事务 B：保存结果、更新状态、追加结果事件
```

用户取消必须先提交 `CANCEL_REQUESTED` 和事件，再通知内存 `AgentRunContext`。外部调用
配额在调用前原子预留，进程崩溃造成的保守计数不予退回。

同一用户并发接纳通过锁定 `api_consumer` 行后统计非终态任务完成，避免“先查询再插入”
突破配额。

---

## 8. 任务规划、执行与恢复

### 8.1 有限计划

第一版只支持顺序步骤。模型输出必须经过结构化校验：

- 步骤数量不超过配置上限；
- 步骤类型来自白名单；
- 输入符合对应 Schema；
- 不包含任意 URL、任意工具名或终端命令；
- 预计调用次数不超过任务预算。

计划调整创建 `planVersion + 1`，保留成功步骤审计，旧计划未运行步骤进入 `SKIPPED`，
并受到调整次数和总调用预算限制。

### 8.2 PostgreSQL 排队与执行器

PostgreSQL 是 `READY`、`WAITING_RETRY` 和待恢复任务的事实来源。研究线程池只承载
正在执行的任务，不使用大容量内存队列。

调度器原子执行：

```text
READY → RUNNING
→ 创建 STARTING ExecutionAttempt 和 runId
→ 提交 researchTaskExecutor
```

提交被拒绝时，只有匹配当前 attempt 和版本的事务才能把任务退回 `READY`。进程在领取
后、提交前崩溃时，启动恢复扫描通过过期 `STARTING` attempt 释放任务。

### 8.3 步骤重试

可重试错误进入 `WAITING_RETRY` 并写入 `next_retry_at`，worker 立即退出。调度扫描器
到期后把任务恢复为 `READY`，不允许工作线程通过 `Thread.sleep()` 等待限流恢复。

重试必须同时满足：错误可重试、操作只读或幂等、未超过 deadline、未超过尝试次数且
调用配额仍有剩余。

### 8.4 应用关闭与启动恢复

应用关闭时停止接纳和调度，对活动 attempt 发出 `APPLICATION_SHUTDOWN`，等待安全
检查点和 cleanup，将 attempt 标记为 `INTERRUPTED`，任务保留为可恢复非终态。

应用被强杀时，恢复扫描通过 `executor_instance_id`、heartbeat 和状态判断遗留 attempt：

```text
旧 attempt → INTERRUPTED
当前 RUNNING step → INTERRUPTED
检查取消、deadline 和重试时间
找到第一个非 SUCCEEDED 步骤
task → READY
由普通调度入口重新领取
```

恢复不使用第二套执行器或业务逻辑。

### 8.5 至少一次语义

进程可能在外部 GET 成功、结果落库前崩溃，因此未确认步骤允许重新执行。第一版不
承诺 exactly-once。已确认 `SUCCEEDED` 的步骤不得重复执行，报告生成使用 taskId 和
步骤 attempt 组成幂等键。

---

## 9. SSE 与 API

### 9.1 API 契约

```text
POST /api/research/tasks
GET  /api/research/tasks/{taskId}
GET  /api/research/tasks/{taskId}/events
POST /api/research/tasks/{taskId}/cancel
GET  /api/research/tasks/{taskId}/report
GET  /api/research/tasks
```

创建请求使用 `X-API-Key` 和 `Idempotency-Key`，成功持久接纳返回 `202 Accepted` 和
`taskId`。相同调用方、相同幂等键和相同请求返回原任务；相同键对应不同内容返回冲突。

非任务所有者统一返回 404，避免暴露任务是否存在。取消接口幂等；已成功、失败或超时
的任务返回终态冲突。

### 9.2 事件流

客户端通过 `Last-Event-ID` 传递游标。服务端发送带 `id`、`event` 和版本化 `data` 的
SSE。连接顺序为：

```text
校验归属
→ 注册有界实时缓冲
→ 查询数据库历史事件
→ 发送历史
→ 合并、排序和去重缓冲事件
→ 切换实时
```

数据库事件是事实来源，事务提交后的内存通知只是低延迟优化。活跃订阅周期性按
`lastDeliveredSeq` 补查数据库，以找回“数据库已提交、内存通知前崩溃”的事件。

慢客户端缓冲区满时只断开该订阅，客户端稍后按游标补收，不取消任务。

### 9.3 前端

新前端围绕任务组织：

- 创建研究任务；
- 查看任务状态与配额；
- 查看计划和步骤时间线；
- 查看实时事件；
- 显式取消；
- 查看带引用的最终报告；
- 查询历史任务。

前端使用支持认证请求头、游标和 `AbortController` 的 Fetch SSE 客户端，不把 API Key
放进 URL 或默认写入 `localStorage`。刷新后重新认证，再以 taskId 和 eventId 恢复观察。

### 9.4 错误响应

统一错误载荷包含稳定 code、用户可读 message、requestId、retryable 和可选的
nextRetryAt。后台 GitHub 限流表现为任务事件和 `WAITING_RETRY`，不直接变成任务创建
请求的 HTTP 错误。

---

## 10. GitHub 调研适配器

### 10.1 边界

```text
GitHubIssueTool
→ GitHubResearchService
→ GitHubRepositoryGateway
→ 固定 https://api.github.com
```

Tool 只暴露受控仓库、关键词、状态、标签、时间范围和最大结果数。Service 构造查询、
排除 Pull Request、控制分页、裁剪正文并形成稳定 DTO。Gateway 负责 HTTP、认证、API
版本、超时、响应头和错误映射。

模型不能提供任意 URL 或原始 GitHub 查询表达式。服务器 Token 只存在于安全配置中，
不进入 Tool 参数、日志、事件和模型上下文。

### 10.2 错误分类

内部错误至少区分：

- `INVALID_INPUT`；
- `AUTHENTICATION_FAILED`；
- `NOT_FOUND`；
- `FORBIDDEN`；
- `RATE_LIMITED`；
- `VALIDATION_FAILED`；
- `NETWORK_TIMEOUT`；
- `UPSTREAM_UNAVAILABLE`；
- `MALFORMED_RESPONSE`；
- `RESPONSE_TOO_LARGE`；
- `TREE_TRUNCATED`。

只对幂等 GET 的暂时故障做有限重试。GitHub 的 403/429 结合响应头区分限流，不在
worker 内等待恢复时间。

### 10.3 Issue 查询

服务端构造包含 `repo:owner/name` 和 `is:issue` 的受控搜索，过滤 Issue API 中可能出现
的 Pull Request。结果限制页数、总数、正文长度和 Tool 总字符数，并明确返回
`truncated`。

### 10.4 文档采集

采集顺序：仓库元数据、默认分支 commit SHA、文件树、白名单筛选、预算检查、按 blob
SHA 下载、内容哈希和后续索引。

白名单包括 README、CONTRIBUTING、SECURITY、CHANGELOG、`docs/**/*.md` 和 GitHub
模板。第一版不处理二进制、图片、压缩包、生成文件和任意源码。

文件树截断时只做有界降级；仍不完整则快照进入 `PARTIAL`，报告必须暴露资料不完整。

### 10.5 不可信内容

Issue 和仓库文档全部是不可信数据。系统提示必须明确它们是待分析资料而非指令，不能
允许仓库文本覆盖工具白名单、访问其他仓库、泄露密钥或触发终端执行。

---

## 11. 项目知识库

### 11.1 不可变快照

公开仓库使用全局可复用的 `repository_snapshot`，以仓库和 commit SHA 唯一标识。
任务创建后固定引用 snapshot，新 commit 的导入不能改变运行中任务的检索集合。

主要对象：

- `repository`；
- `repository_snapshot`；
- `project_document`；
- `project_document_chunk`；
- `ingestion_run`。

快照状态为 `DISCOVERED → INGESTING → READY/PARTIAL/FAILED`。导入和应用启动解耦，
禁止在 Spring Bean 创建过程中自动完成远程下载和全量向量化。

### 11.2 导入流水线

```text
文档发现与预算
→ 下载
→ 标准化
→ Markdown 结构化切分
→ 元数据补充
→ Embedding
→ PGVector
→ 完整性检查
→ 激活快照
```

快照完整构建后才激活，避免检索半成品。`PARTIAL` 是否可用由任务显式记录并展示。

幂等身份包含仓库、commit、路径、内容哈希、splitter 版本和 Embedding 模型版本。

### 11.3 检索

研究步骤显式调用 `ProjectKnowledgeRetriever.search(snapshotId, query, topK)`，记录原始
查询、查询改写、topK、返回 chunk、排名和引用。RAG 不作为所有 Agent 对话的默认
Advisor，避免不可审计的隐式检索。

### 11.4 评测

版本化评测集进入 Git，每条包含问题、仓库、commit、相关路径、相关章节和预期事实。
评测记录 Hit@K、Recall@K、MRR、引用正确率和必要的人工抽检。

不同切分、Top-K 和查询改写的比较必须固定仓库快照、Embedding 模型和评测集，每次只
改变一个主要变量。Hit@K 不能替代最终回答正确率，也不能被描述为性能提升。

---

## 12. 资源、安全与数据治理

### 12.1 资源上限

所有具体数值通过受控配置提供，实施时根据本地测量确定默认值：

- 每用户非终态任务数；
- 单任务最大执行时间；
- 单任务计划步骤数和调整次数；
- 模型、Embedding 和 GitHub 尝试调用数；
- GitHub 页数、Issue 数、文件数、单文件与总字节数；
- 文档 chunk 数和 Tool 返回字符数；
- 单用户和单任务 SSE 连接数；
- 单订阅缓冲事件数和单次补发数；
- 任务、事件、报告和旧快照保留时间。

第一版不因短暂无人订阅立即取消任务。deadline 和调用配额保证任务不会无限执行，
`lastSubscriberAt` 为未来的无人订阅宽限策略保留事实依据。

### 12.2 密钥

- API Key 只保存哈希；
- GitHub Token 和模型 Key 只来自安全配置；
- 认证信息不进入 URL、日志、事件和模型上下文；
- 客户端 API Key 默认只保存在当前页面内存；
- 生产代理不记录敏感认证头。

### 12.3 数据保留

事件只保存客户端和审计需要的裁剪信息。研究问题和报告可能包含用户数据，必须提供
保留周期。旧快照只有在没有任务引用后才能清理。

---

## 13. 恋爱领域迁移

迁移采用旁路替换：

```text
建立研究领域空壳
→ 持久任务基础设施
→ GitHub 调研
→ 知识库
→ 计划执行与恢复
→ 新前端
→ 切换默认入口
→ 删除 Love 领域
→ 独立品牌重命名
```

应保留 Agent 抽象、运行上下文和通用组件。新研究 Agent 使用独立只读工具 Bean，不
注入包含终端和文件写操作的现有 `allTools`。

新主链路稳定后，删除 `LoveApp`、Love RAG 配置、恋爱语料、Love Controller 接口、
Love 测试、前端 Love 路由和文案。`PgVectorVectorStoreConfig` 由新的知识库配置替代，
不是随 Love 文件一起直接删除。

`YuManus` 作为旧演示 Agent，先由 `ProjectResearchAgent` 替代并迁移生命周期所有权
测试，再决定删除或移至示例。图片搜索 MCP 第一版不进入研究工具集，也不能作为
GitHub MCP 能力写入简历。

迁移完成的含义是新主链路、后端启动、前端构建和生命周期回归保持正常；历史复盘和
Git 历史可以保留恋爱问题描述，不应为了残留扫描篡改证据。

---

## 14. 测试与证据

### 14.1 五层验证

1. 纯 JVM 领域单元测试；
2. 真实线程和同步原语的确定性并发测试；
3. 真实 PostgreSQL 的事务和约束集成测试；
4. Mock HTTP 与模型替身的外部适配器测试；
5. 独立进程退出、重启和恢复测试。

RAG 质量使用单独的版本化评测集。H2 和 Mock Repository 不能代替 PostgreSQL 对锁、
部分唯一约束和事务竞态的验证。

### 14.2 关键故障注入

- 两个调度器同时领取同一任务；
- 同一用户在配额边界同时创建任务；
- 取消与步骤成功竞争；
- deadline 与任务完成竞争；
- 并发分配事件序号；
- 补发期间产生实时 SSE 事件；
- 事务提交后丢失内存事件通知；
- GitHub 限流、超时、5xx、格式异常和文件树截断；
- 计划保存前后退出；
- 外部调用前后退出；
- 步骤结果提交前后退出；
- `WAITING_RETRY`、`CANCEL_REQUESTED` 和报告提交点退出；
- 应用关闭和强制进程终止。

### 14.3 证据口径

必须分别记录测试方法数、故障场景数、每场景重复轮次、并发任务数、恢复成功率、已
成功步骤重复次数、事件缺失数、去重后重复数、调用配额突破次数和任务残留数。

现有 25 项生命周期测试继续作为阶段回归。测试通过不能被描述为性能提升；顺序样本
不能被描述为并发量；Mock HTTP 不能被描述为生产 GitHub 可用性。

### 14.4 证据存放

进入 Git：测试、故障注入脚本、数据库迁移、评测集、决策文档和结构化证据账本。

不进入 Git：`target/evidence` 原始输出、密钥、数据库密码、完整模型响应和用户任务
数据。

每个开发阶段提交前必须完成聚焦测试、生命周期回归、必要的 PostgreSQL 集成测试、
mutation 恢复、空白检查、精确暂存检查和证据更新。

---

## 15. 主要代价和失效模式

| 设计 | 收益 | 代价或失效方式 |
| --- | --- | --- |
| 任务与 SSE 解耦 | 断线可继续和重连 | 后台资源在用户离开后继续消耗 |
| 数据库任务队列 | 重启不丢任务 | PostgreSQL 成为强依赖 |
| 步骤级恢复 | 容易审计和验证 | 半步骤不能恢复，可能重新调用 |
| 至少一次 | 实现可控 | 未确认步骤可能重复执行 |
| 持久有限计划 | 恢复位置明确 | 自主性弱于完全开放 ReAct |
| 有界执行器 | 资源有上限 | 配置过小会排队，过大会压垮外部服务 |
| `WAITING_RETRY` | 不占 worker 睡眠 | 调度与状态机更复杂 |
| GitHub REST 采集 | 不执行不可信仓库 | API 限流且不适合完整代码分析 |
| commit 快照 | 引用可复现 | 快照和向量存储增长 |
| 内存通知 + DB 补查 | 无需消息队列 | 大量订阅会增加数据库查询 |
| API Key | 范围小、易实现 | 不等于完整用户权限系统 |
| 单实例 | 适合个人项目 | 不具备节点容错和水平扩展 |

外部 SDK 永久忽略中断时，当前方案仍不能强杀 Java 线程。多实例部署、数据库不可用、
大规模租户公平性和跨节点实时取消均需要新的架构阶段。

---

## 16. 分阶段交付顺序

本节只定义里程碑，不替代后续逐文件实施计划。

1. 整理工作区和建立迁移基线；
2. 建立持久任务领域模型与真实 PostgreSQL 测试；
3. 建立任务 REST API、API Key 和幂等接纳；
4. 建立事件表、SSE 补发和显式取消；
5. 建立调度、ExecutionAttempt、deadline 和重启恢复；
6. 建立 GitHub Gateway、Issue Tool、错误分类与调用配额；
7. 建立文档采集、不可变快照和 PGVector；
8. 建立有限计划、研究步骤和最终报告；
9. 建立 RAG 评测与完整故障注入证据；
10. 建立新前端并切换默认入口；
11. 删除恋爱领域并执行完整回归；
12. 独立完成品牌、README 和可选根包迁移。

每个里程碑保持可编译、可测试、可回退，并单独形成证据和提交边界。

---

## 17. 最终验收标准

- 新研究任务创建、查询、订阅、取消和报告接口工作正常；
- SSE 断开不取消任务，重连能够补收已提交事件；
- 每用户任务配额和每任务调用预算不能被并发绕过；
- 同一任务不能产生两个活动执行尝试或两个终态；
- 服务进程重启后从第一个未成功步骤继续；
- 已确认成功步骤不重复执行；
- GitHub 限流不占用 worker 睡眠并能按策略恢复；
- 文档快照固定 commit，半成品快照不被静默检索；
- 最终报告引用能够定位仓库、commit、路径和原始 URL；
- 固定 RAG 评测集和原始结果具有可复现环境记录；
- 原有生命周期回归保持通过或迁移到等价新 Agent 测试；
- 后端启动、前端构建和新主链路验证通过；
- 生产代码、前端和运行资源中没有非预期恋爱领域残留；
- 上游归属和许可证边界经过核验；
- 简历中的每个数字都能够映射到仓库证据。

---

## 18. 简历证据边界

当前可以使用的事实仅限已经完成的 Agent 生命周期重构及其测试。其余内容只有完成
对应实现和测量后才能使用以下占位表述：

> 通过 `[故障注入场景数]` 类进程故障场景、每类 `[重复轮次]` 轮验证，任务恢复成功率
> 达到 `[成功率]`，已确认成功步骤重复执行次数为 `[次数]`。

> 建立 `[评测问题数]` 条固定 RAG 评测集，在相同仓库快照和 Embedding 模型下，
> Hit@K 达到 `[数值]`、引用正确率达到 `[数值]`。

在缺少压力测试、同环境性能基准和生产数据时，禁止使用“支持高并发”“性能提升”
“生产可用”“零数据丢失”或“降低线上故障率”等表述。
