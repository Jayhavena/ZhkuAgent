# ADR-OBS-001：匿名 Runtime 事件的生产 Sink 候选

- 状态：**PROPOSED / NOT APPROVED FOR PRODUCTION**
- 日期：2026-10-22
- 范围：OBS-02E 设计阶段；不包含生产实现或 /rules 接线。
- 依据：OBS-02C 合成联合验收回执、OBS-02D 安全评审、当前 OBS v1 源码静态检查。

## 1. 背景与当前事实

项目已有 `ObservationSink` 接口、匿名白名单投影和有界 Runtime dispatcher，但尚无生产 Sink。

当前源码事实：
- `ObservationEventV1` 只有 schemaVersion、kind、outcome、toolClass、failureClass 与四个可空数值指标；没有自由文本字段或关联 ID。
- `ObservationProjector.anonymous` 会把 Runtime 事件映射到上述白名单，并把 tool name、参数、结果、错误原文、runId 与 sequence 留在投影之外。
- 当前投影将 inputTokens、outputTokens、elapsedMillis、estimatedCostMicros 全部置为 `null`。因此现阶段只能观察事件类别/结果/失败类别，不能宣称已采集 token、耗时或成本。
- `ObservationSink.noop()` 会明确返回 DISABLED；`BoundedObservationBuffer` 的源码定位是离线测试容量界限。
- 本仓库没有发现项目自有的日志保留配置、结构化日志配置、Metrics exporter、OpenTelemetry exporter 或 Prometheus 端点配置。Spring Boot Web starter 提供基础日志设施，但这不证明部署侧已有采集、权限和保留政策。

## 2. 推荐候选：异步、有界的结构化应用日志

**建议优先评估“结构化匿名事件写入应用 stdout 日志”，不引入数据库表、外部遥测 SDK 或新的网络出口。此项仍是候选，不是生产批准。**

建议数据流：

```text
AgentRunContext
  -> AgentRunObservationDispatcher (existing bounded async isolation)
  -> AnonymousRuntimeObserver
  -> ObservationProjector.anonymous (strict allowlist)
  -> AsyncStructuredLogObservationSink (new bounded queue; non-blocking offer)
  -> one writer thread
  -> SLF4J application logger / stdout
  -> deployment-managed log collector (if configured)
```

Sink worker 将 `ObservationEventV1` 编码为单行、固定 schema 的 JSON 消息，并加固定前缀 `OBS_V1` 便于日志检索。消息只允许下列字段：
- `schema_version`（固定为 1）
- `kind`、`outcome`、`tool_class`、`failure_class`（固定枚举）
- `input_tokens`、`output_tokens`、`elapsed_ms`、`estimated_cost_micros`（Long 或 JSON null）
- 不增加 task、run、turn、owner、session、请求、证据或工具调用关联字段。

日志行的时间戳由日志框架/采集端提供；不得为了排序而补入 Run ID、sequence 或其他稳定关联键。由于 Schema v1 不带关联身份，数据只用于总体事件类别与失败类型观察，不支持逐请求诊断。

### 交付语义

- Sink 使用有界队列；`offer()` 只调用非阻塞 `queue.offer`，不得执行日志 I/O、等待锁、等待网络或回调 TaskStore。
- `ACCEPTED` 的准确含义是“事件已进入 Sink 队列”，**不是**“已写入日志、更不是已持久化/已被外部采集”。Sink 状态必须区分 queued、written、dropped、writeFailure、closed、writerAlive。
- 队列满时丢弃新事件并返回 FULL；关闭后返回 DISABLED；写线程不可恢复失败后返回 FAILED。禁止自动退回 noop 或测试内存 buffer。
- 写线程只序列化投影后的固定字段；不调用原始事件的 `toString()`，不记录异常原文，不在失败时递归写同一个 Sink。
- 队列容量须有明确上限和配置校验；候选默认容量 256、允许范围 32–2048。容量只限制进程内排队，不承诺持久性或完整性。
- 生产日志后端阻塞时，只允许阻塞 Sink 自己的 writer；必须有物理线程退出检测。业务 Runtime 线程与 HTTP 请求线程不得直接执行日志 I/O。

## 3. 目的地、保留与访问策略：尚未通过的硬门禁

stdout 只是应用侧输出目的地，不代表已经存在安全的生产日志系统。本仓库目前没有证据证明部署环境具备日志采集、访问控制、保留或删除能力。

在实现或启用之前，运行环境负责人必须确认并记录：
1. **接收位置**：stdout 是否被采集；如果没有采集，明确该方案只提供本地进程日志，不能宣称可运营监控。
2. **可访问角色**：哪些运维/开发角色可以读取日志；禁止学生、普通业务用户或不必要的第三方访问。
3. **保留与删除**：明确保留期限、轮转/清理责任和删除验证。当前不预设天数，也不以“平台默认”代替决议。
4. **外发边界**：日志是否离开应用主机、进入哪个系统、由谁管理；不得默默接入新的第三方。
5. **容量与告警**：日志采集丢失、磁盘/ stdout 背压、Sink 饱和和写线程异常如何被运维发现。
6. **用途限制**：禁止将该匿名事件流用于还原个人行为、构建逐请求轨迹或评测模型回答正确性。

以上未确认前，状态保持 `NOT APPROVED FOR PRODUCTION`，OBS 开关不得打开。

## 4. 替代方案比较

| 方案 | 优点 | 当前问题 | 结论 |
|---|---|---|---|
| 结构化 stdout 日志 | 可复用 Boot/SLF4J，不需新网络出口或数据库 Schema | 依赖部署侧采集、访问和保留政策；日志 I/O 可能阻塞 | **首选候选，待运行环境确认** |
| PostgreSQL 遥测表 | 可查询、可制定数据库保留规则 | 把旁路观测耦合到业务数据库；需要迁移、权限、事务/故障政策；现有 DB 不等于获准用于遥测 | 不作为当前 MVP |
| Micrometer/Prometheus/OTel exporter | 适合指标/观测生态 | 当前没有项目 exporter/endpoint 证据；需依赖、配置、暴露面和权限评审；当前事件 Schema 也不是完整指标模型 | 后续独立评估 |
| 进程内有界 Buffer | 实现简单、可做测试 | 重启即丢、无保留/权限/读取协议；现有类明确是测试用 | 仅测试 |
| noop | 没有资源和隐私暴露 | 没有可用观测 | 仅用于明确关闭状态 |

## 5. 配置与失败合同（未来实现要求）

- 新开关候选：`zhku.rule-agent-observation.enabled=false`，缺省关闭。
- 开关关闭：维持现有四参数 `RuleAgentTaskStore` 构造器；不创建 dispatcher、Sink writer 或订阅。
- 开关开启：必须有显式、校验通过的 Sink 与生命周期协调器；缺失配置或依赖时 Spring Context 启动失败，不得默默降级为 noop。
- 日志输出失败、队列满或 Sink 关闭只影响观测交付，不得改变 TaskSnapshot、RunTrace、用户响应、任务状态或 mandatory provider audit。
- mandatory audit 仍保持原有 fail-closed；匿名观测失败不能代替、覆盖或“修复”审计失败。
- 只允许 `ObservationProjector.anonymous` 的结果进入 Sink。禁止 `RunDiagnosticEventV1`、`RuleAgentRunTrace`、原始 `AgentRunEvent` 或任何请求/身份上下文进入日志。

## 6. 决策状态与准入条件

当前建议是架构候选，而非不可逆决定。进入实现前必须：
- 由运行环境负责人确认 stdout 的采集位置、访问角色、保留期限和删除责任；
- 完成数据流/威胁边界审查；
- 对异步队列容量、写失败、背压、关闭与物理线程退出形成可执行测试；
- 单独批准默认关闭的 Spring 装配补丁。

**结论：推荐候选为有界异步匿名结构化日志；生产目的地/保留/访问尚未确认，故当前仍 NO-GO。**
