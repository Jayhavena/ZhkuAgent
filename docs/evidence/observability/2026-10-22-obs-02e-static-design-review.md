# OBS-02E 静态设计核对记录

日期：2026-10-22
状态：**DESIGN_DRAFT_COMPLETE / PRODUCTION_GATE_BLOCKED**
工作区：D:/alldesk/Projects/ZhkuAgent-OBS02E
基线：a67fb39f3a87445fb3d76dc7623360344bc42491
本阶段类型：仅文档设计和源码静态核对；没有业务代码修改或测试执行。

## 核对范围

只检查本阶段直接相关的既有文件：
- `knowledge/src/main/java/com/zhku/knowledge/observability/ObservationSink.java`
- `BoundedObservationBuffer.java`
- `AnonymousRuntimeObserver.java`
- `ObservationEventV1.java`
- `ObservationProjector.java`
- `agent-runtime/src/main/java/com/zhku/agent/agent/runtime/AgentRunObservationDispatcher.java`
- `knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentTaskStore.java`
- `knowledge/src/main/java/com/zhku/knowledge/ruleagent/web/RuleAgentHttpConfiguration.java`
- `knowledge/pom.xml`、根 `pom.xml` 与受控预览配置。

## 源码事实与设计影响

| 核对项 | 静态事实 | 设计影响 |
|---|---|---|
| Sink 实现 | noop 工厂明确返回 DISABLED；BoundedObservationBuffer 注释定位为离线测试 | 两者都不能被称为生产遥测落点 |
| Schema | v1 仅包含固定枚举和可空 Long；无身份、自由文本或事件时间戳 | 可以安全候选化为匿名事件日志；日志时间戳来自 logger/采集端，不可新增关联 ID |
| 指标来源 | 当前 anonymous 投影把 token、耗时、成本均设为 null | 不能宣称已有性能/成本指标；未来增加来源需单独设计与验证 |
| Runtime 投递 | dispatcher 有界异步投递，关闭预算最多 1000 ms；逻辑 detach 不等于线程退出 | Sink offer 还应非阻塞；必须检查物理线程 |
| TaskStore 关闭 | close 只请求停止并清理索引，不等待业务执行器退出 | 不能只调用 TaskStore.close 就关闭 dispatcher |
| 活跃任务计数 | activeJobs 可能在最终 publish 之前递减 | awaitTermination 比 activeJobs=0 更强；必须等待包装器方法真正返回 |
| 执行器类型 | 默认 Bean 为 ThreadPoolExecutor，但 TaskStore 装配参数声明为 Executor | OBS 宿主必须明确管理 ExecutorService 所有权/关闭能力 |
| 现有 HTTP 装配 | 当前使用四参 TaskStore；HTTP 显式开关仍是入口门禁 | OBS 开关缺省关闭且不能改变既有开关语义 |
| 日志/遥测配置 | 仓库未找到项目自有日志保留配置、Metrics exporter、OTel exporter 或 Prometheus 端点配置 | stdout 仅是候选目的地；运维采集、访问、保留与删除仍需外部确认 |

## 本阶段交付物

1. `docs/decisions/ADR-OBS-001-anonymous-observation-sink.md`
   - 比较 stdout 结构化日志、数据库表、指标 exporter、进程内 Buffer 和 noop。
   - 推荐有界异步结构化日志作为候选，不引入新网络出口/数据库 Schema。
   - 明确候选尚未获生产批准，部署日志策略为硬门禁。

2. `docs/design/2026-10-22-obs-02e-shutdown-contract-and-test-matrix.md`
   - 给出 Spring graceful shutdown、TaskStore、业务 ExecutorService、dispatcher、Sink 的顺序。
   - 提供单调时钟的有限关闭预算与超时后 INCOMPLETE 语义。
   - 定义 LC-01 至 LC-14 合成验收矩阵。

3. 更新本阶段计划状态，明确设计草案已形成，但生产目的地政策尚未核准。

## 未完成项 / 不得误报

- 未实现 AsyncStructuredLogObservationSink。
- 未新增 Spring Bean、配置开关或生命周期协调器。
- 未执行 Maven、Node、Spring Context、HTTP 或压力测试；OBS-02C 的既有通过结果不应被当成本阶段实现测试。
- 未证明 stdout 在目标部署环境中被安全采集，也没有决定日志保留天数或访问角色。
- 未接触真实学生请求、真实模型供应商、数据库、外部遥测平台或生产 /rules。
- 没有 push、PR、部署或合并。

## 本阶段裁定

**设计草案完成；生产实现与真实接线继续 NO-GO。**

唯一关键外部决策是确认日志目的地的采集、访问、保留/删除和外发边界。确认后可把 ADR 状态升级为 ACCEPTED（或选用其他目的地），再进入 OBS-02F 的实现与合成测试。未确认之前，保留现有四参 TaskStore 默认路径。
