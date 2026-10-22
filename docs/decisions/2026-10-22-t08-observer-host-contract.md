# T08 可选 Observer 宿主契约

日期：2026-10-22。状态：IMPLEMENTED / OFFLINE_57_OF_57_VERIFIED / SCOPED_TECHNICAL_SIGNOFF。

本记录由执行 T08 WORKFLOW 实现任务的 Codex 编写，不代表人类维护者签名，不授权生产接线。Codex 受限沙箱的 Maven JAR 访问失败仅属于构建环境问题；编排方随后在已授权本机终端离线独立运行 Maven，57/57 项通过、BUILD SUCCESS、退出码 0。可对本接口出具范围受限的 T08 工作流技术签收，但不代表人类维护者签字或批准生产接线。六项门禁见 [交接记录](../project/handoffs/2026-10-22-t08-obs-six-gate-signoff.md)。

## 公开入口与装配责任

```java
public RuleAgentTaskStore(
    Executor executor,
    Factory factory,
    Clock clock,
    Limits limits,
    Consumer<AgentRunEvent> observer,
    AgentRunObservationDispatcher dispatcher)
```

`Factory`、`Limits` 是 `RuleAgentTaskStore` 的嵌套类型；`AgentRunEvent` 是 `com.zhku.agent.agent.runtime.contract.AgentRunEvent`；dispatcher 是 `com.zhku.agent.agent.runtime.AgentRunObservationDispatcher`。新增的两个参数均必须非 null，构造时拒绝缺项。

原四参入口保留默认关闭路径，只调用既有 `AgentRunContext.create(question)`，不创建 dispatcher、不初始化 `processDefault`、不建立订阅。默认 `RuleAgentHttpConfiguration` Bean 仍调用四参构造器，本次没有改动 HTTP 或真实 `/rules` 装配。

T08 宿主仅在 OBS 集成另行获批后才能选择六参入口，提供经隐私审查的 `Consumer<AgentRunEvent>` 和显式 dispatcher。OBS 负责纯事件投影和匿名 sink；T08 宿主负责装配选择、dispatcher 来源、停机顺序和物理线程检查。TaskStore 只管理每轮 Context 及其停止请求，绝不关闭注入的 dispatcher，更不关闭 `processDefault()`。显式 `createOwned()` 资源由创建它的宿主关闭；若注入 JVM 默认实例，宿主也没有关闭权。

## 生命周期与并发约束

- 新 Slot 与每次成功接受的澄清续轮使用 `AgentRunContext.create(UUID.randomUUID(), question, observer, dispatcher)`。同一业务 task 保留，Context、runId、turnId 更新；Accepted 的 runId 来自该 Context。重复 create/message 返回已保存的受理身份，不创建新 Context 或调度新工作。
- 订阅创建只保存 Consumer 引用，首次事件才登记；它不执行 Consumer。现有 Runtime 的事件 offer 可能在 TaskStore 取消锁内发生，但 offer 只提交有界数据；回调在 dispatcher 线程执行，既不在任务执行器同步运行，也不在 publish 锁内调用。publish 的业务版本检查、终态检查和 Trace 同锁落账没有改动。
- Consumer 可以与 TaskStore 发布并发执行；不得等待 TaskStore 终态作为同步屏障、持有应用锁再等待业务线程、或者向业务线程反向索取资源。否则可能形成应用层等待环。异步回调可读快照，但 `RUN_TERMINATED` 到达不保证 TaskStore 的最终快照已保存：Runner 发布 Context 终态后才返回给 TaskStore.publish。不得改成回调内阻塞轮询等待发布。
- cancel 受理只确认停止请求。排队任务仍须执行包装器和 `publishFailure` 才有真实清理、termination 和最终事件；STOP_REQUESTED、HTTP 202、Future 取消都不代表物理工作线程退出。排队取消不要求产生 RUN_STARTED。
- Runner/失败收尾负责 finish、cleanup、signalTermination、publishTermination；Context 只发布一次终止事实。publish 拒绝过期 ticket、跨 Run 或晚到成功覆盖，不通过 Observer 改写业务结果。业务 Trace 保留与旁路事件投递互不替代。
- TaskStore.close 幂等地停止未发布 Context、删除已发布任务、清空请求索引；它不等待任务/观察器退出，不关闭业务 Executor，也不保证从不启动的外部执行器队列会自行清空。宿主须确保已受理任务有机会执行收尾，等待业务包装器返回，再在独立预算内关闭自有 dispatcher。
- TTL 和等待期约束保持原行为；TTL 不是新增后台计时器，也不保证到期自动杀死运行线程。已发布且过期的 Slot 在后续准入时淘汰；等待期过期拒绝续答；观察器不创建新的保留入口。
- dispatcher 保持现有有界、尽力交付语义：队列饱和、异常、关闭、超时可能丢事件或脱离订阅。当前 Runtime 的回调预算 200ms、最终排空预算 1s；脱离不 interrupt/替换阻塞观察线程。宿主关闭预算最多 1s；逻辑 CLOSED/DETACHED 不证明物理退出，应另查 `status().liveThreadCount()`。业务不得等待 observer 成功，更不得把它当 mandatory audit。

## 数据与权限边界

`traceHistory(Owner, UUID)` / `RuleAgentRunTrace` 是受 Owner 约束的离线评测视图，含关联 UUID、证据和哈希；最多保留最近 4 Run，每条最多 24 Action。它不能输入匿名 sink。Task HTTP `SCHEMA_VERSION=3`，Trace `version` 是业务快照版本，OBS `ObservationEventV1.schemaVersion=1`；三者不互换。

OBS-02A 的输入只能是 Runtime `AgentRunEvent`，由 OBS 的匿名白名单投影生成 v1。原生事件仍可能包含工具参数/结果、runId，不能直接日志化或外发。安全 Consumer 及其访问/保留策略需要单独集成审查。本次不引入 OBS schema、生产 exporter 或受限诊断查询。

mandatory provider audit 的 `AUDIT_UNAVAILABLE` 必须保持 fail-closed。新增测试仅以合成 typed failure 检查 Runner/TaskStore 在 Observer 抛错时不把该失败改成成功；没有调用真实供应商或证明真实审计写入链。

## 变更、回退与后续验收

仅修改 TaskStore、新增独立测试及本次两份文档。未改 Runtime、Runner、Trace、HTTP、工具、评测器、holdout 或 POM。回退时由编排方撤销上述精确文件的本次差异；既有四参路径继续可用。

新增 9 项测试覆盖默认关闭、匹配受理 runId 的始末事件、重复 create、跨轮/重复 message、排队取消、抛错隔离、阻塞与物理退出、合成审计失败隔离、双参数非空约束。测试持有的自有 dispatcher 在 AfterEach 关闭并限时检查物理线程；阻塞回调在 finally 显式释放。本机独立离线运行结果为新增测试 9/9 PASS，连同 Runner 22、Trace 11、TaskStore 14、HTTP 1 共 57/57 PASS、0 fail/error/skipped；本次没有执行真实供应商、数据库或端到端 /rules 联调。

**GO：OBS-02A 独立工作树中的纯合成事件适配器开发/测试（延续既有范围，不代表本接口验收）。NO GO：真实 `/rules` 装配、T08 在线评测、生产、合并、push 或部署。** Java-to-T08 adapter 与最终来源认证仍需单独验收。
