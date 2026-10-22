# OBS-02E 生产 Sink 与宿主生命周期合同门禁

日期：2026-10-22
状态：**PLANNED / DESIGN_ONLY / NO_PRODUCTION_WIRING**

上游评审：docs/reviews/2026-10-22-obs-02d-rules-wiring-safety-review.md
通过的输入证据：docs/evidence/observability/2026-10-22-obs-02c-combined-offline-acceptance.md

## 目标

在修改真实 /rules HTTP 装配前，先解决 OBS-02D 的两个阻塞：
1. 当前没有生产级 ObservationSink；noop() 是明确禁用路径，BoundedObservationBuffer 仅为离线测试容量界限。
2. 未来 Spring 装配必须证明 TaskStore/业务执行器的收尾先于自有 dispatcher 的关闭，且检查物理线程退出，不以逻辑 CLOSED 代替。

本阶段先定合同与可验收方案，不立即新增 exporter、日志 sink、Spring Bean 或业务代码。

## 决策一：观测语义与字段边界

- 当前 ObservationEventV1 v1 只作为匿名聚合事件，不提供 per-task/per-run 可关联 Trace。
- 生产 Sink 只接收 ObservationProjector.anonymous 输出；任何原始 AgentRunEvent、RuleAgentRunTrace、taskId/runId/turnId/Owner/sessionNonce、原始 prompt/reply、工具参数/结果、证据 sourceId/chunkId/hash、异常原文或凭证均不得传出。
- 未知指标必须保持 null，不可将缺失 token、耗时或成本伪造为 0。
- 必须定义目标用途：哪些团队/角色能看、保存多久、是否跨进程、怎样删除、如何审计访问、Schema v1 如何兼容升级、采样与丢弃如何计量。
- 不能只因为 BoundedObservationBuffer 有 drain() 就把它升级为生产 Sink；也不能把 noop() 的 DISABLED 计数宣称为已采集。

## 决策二：生产 Sink 必须满足的接口合同

在实现前形成 ADR，明确下列条件：
- offer() 有界、尽力而为、非阻塞；不得让业务线程等待遥测后端，也不得反向回调 TaskStore。
- 对 FULL/DISABLED/FAILED 及 Sink 异常有清晰且可观测的聚合计数；不缓存原始事件、不把异常消息带入日志。
- 目的地与访问控制由服务端显式配置；若生产启用但缺少目的地、权限或保留配置，应在启动时失败，不能默默使用 noop 或测试 buffer。
- 目标后端不可用时，只丢弃遥测，不改变 Agent 结果、TaskSnapshot、Trace 落账或 mandatory provider audit 的 fail-closed 行为。
- 不引入新增外部依赖/遥测出口，除非 ADR 明确说明必要性、数据流、威胁边界和回滚。

## 决策三：Spring 开关与 Bean 装配合同

未来接线需同时满足：
- zhku.rule-agent-http.enabled 及 zhku.rule-agent-launch.enabled 的现有显式准入条件保持不变；ONLINE 还需原有 online-enabled 与真实 Factory。
- 新 OBS 开关缺省关闭。
- OBS 关闭时，HTTP TaskStore 仍走现有四参数构造器，不创建 dispatcher、不登记订阅。
- OBS 开启时，必须注入显式生产 Sink、AnonymousRuntimeObserver 和自有 AgentRunObservationDispatcher；任一依赖缺失应启动失败，不允许隐式 fallback。
- 仅对一个 TaskStore Bean 生效；不能让四参数和六参数两个 Bean 因条件重叠而争用名称，也不能使用 bean overriding 消除冲突。
- 不改前端 /rules、Controller 路径、请求 Schema、CSRF、Owner/session 校验或 202 语义。

## 决策四：停机协调合同

必须先画出并测试明确顺序：
1. 关闭新请求准入。
2. 对 TaskStore 发起关闭/停止请求。
3. 让已接受的业务包装器完成其真实清理与终态发布；等待业务 Executor 的 activeJobs/物理线程达到合同要求。
4. 业务任务不再发布 Runtime 事件后，按固定预算关闭自有 dispatcher。
5. 在关闭预算内验证 dispatcher 的 liveThreadCount=0、registeredCount=0、ticketCount=0、inFlightCount=0；逻辑 CLOSED/DETACHED 不算物理退出证据。
6. 若超时，保留明确的 shutdown-incomplete 证据，不无限等待，不假称已释放资源。不得关闭借用的 processDefault()。

只写 destroyMethod 不构成上述顺序的验收证明。应决定是否使用专用生命周期协调器或等价的、可测试的宿主机制。

## 后续实现前必须通过的测试

| 测试 | 验收条件 |
|---|---|
| 默认关闭上下文 | OBS 开关缺省时只使用四参 TaskStore；无 dispatcher 线程、无 Observer 注册、无新外发 |
| 缺 Sink 启动 | OBS 显式开启但缺少生产 Sink/配置时，Spring 刷新失败；不能静默降级 |
| 显式开启合成上下文 | 六参 TaskStore 只接收 AnonymousRuntimeObserver；只有 schema v1 白名单字段进入测试 Sink |
| HTTP 合成回归 | 使用本地回环、假身份仓储和合成 Factory；验证 Owner/Session 隔离、重复请求、澄清、取消、202 与终态区别 |
| 故障/饱和 | Sink throw/FULL/DISABLED/关闭不改写业务结果；mandatory audit 仍 fail-closed |
| 生命周期退出 | 在队列任务、阻塞 Observer、终态排空边界下验证执行器与 dispatcher 退出；有超时就判失败而不是只看逻辑状态 |
| 隐私负例 | 注入合成 prompt、工具参数、runId、actionId、证据 ID/hash；任何 Sink、日志和回执均不回显 |
| 回滚 | 关闭 OBS 开关即可恢复既有四参路径；恢复补丁不修改已有 T08 Trace/业务状态语义 |

## 范围限制

- 允许：ADR、数据流/生命周期图、测试矩阵、合成测试设计。
- 本计划本身不授权：实现生产 Sink、修改 RuleAgentHttpConfiguration、Controller、前端、数据库、Provider、真实 HTTP、push、PR、部署。
- 不把匿名事件包装成评测 Trace；真实 Java-to-evaluator 来源认证仍是独立工作流。
- 生产接线需在合同评审和实现测试分别通过后再单独审批。

**进入下一阶段的准入条件：生产 Sink 的用途/目的地/保留/访问合同与关闭协调方案均有可审查决议。未满足前继续 NO-GO。**
