# OBS-02E：宿主关闭协调合同与验收矩阵

- 状态：**DESIGN DRAFT COMPLETE / IMPLEMENTATION BLOCKED**
- 日期：2026-10-22
- 适用范围：未来受控 HTTP TaskStore 的 OBS 可选装配。
- 不授权：修改真实 /rules 链路、生产 Sink 实现、数据库迁移、外部遥测出口或部署。

## 1. 当前源码约束

1. `RuleAgentTaskStore.close()` 只设置 closed、清理已发布任务/请求键并对未完成 Context 调用 requestStop；它不会等待业务执行器的所有工作线程退出。
2. `RuleAgentTaskStore.activeJobs()` 不能单独作为“业务任务已完全结束”的证明：当前执行路径可能在最终快照发布前先递减 jobs。应等待拥有的 `ExecutorService.awaitTermination`，而不是只轮询 activeJobs。
3. `RuleAgentHttpConfiguration` 当前的 ExecutorService Bean 使用 `destroyMethod="shutdown"`，TaskStore 使用 `destroyMethod="close"`；仅有这两个 destroyMethod 不能证明停止顺序或物理线程退出。
4. 六参数 TaskStore 不拥有 dispatcher；宿主必须负责 dispatcher 与 Sink 的创建、关闭和物理退出核验。
5. `AgentRunObservationDispatcher.close(Duration)` 接受的显式预算最大为 1000 ms。逻辑 CLOSED/DETACHED 不等于回调线程已物理退出。
6. 当前配置方法的注入类型是 `Executor`，虽然默认 Bean 实际是 `ExecutorService`。启用 OBS 的生产装配必须明确要求可被宿主管理和等待的 `ExecutorService`；不能把任意 Executor 假定为可关闭。

## 2. 推荐关闭顺序

```text
Spring Boot 开始 graceful shutdown
        |
        v
停止接受新的 HTTP 连接/请求，排空在途 HTTP handler
        |
        v
OBS Shutdown Coordinator 开始单一截止时间预算
        |
        +--> RuleAgentTaskStore.close()
        |      closed=true；拒绝后续任务操作；请求未完成 Context 停止
        |
        +--> ExecutorService.shutdown()
        |      在宽限窗口内等待所有 execute() 包装器完整返回
        |      若超时：shutdownNow() 请求中断，再限时等待
        |
        +--> AgentRunObservationDispatcher.close(<= 1000 ms)
        |      只在业务执行器正常退出后进行完整 drain；
        |      若业务执行器超时，则明确记录 incomplete，关闭后期事件会被丢弃
        |
        +--> AsyncStructuredLogObservationSink.close(<= 1000 ms)
        |      停止新 offer；限时排空队列；检查 writer 线程是否真正退出
        |
        v
生成 shutdown result：COMPLETE 或 INCOMPLETE
        |
        +--> COMPLETE：业务 executor、dispatcher、sink writer 均已退出
        |
        +--> INCOMPLETE：记录超时阶段/计数；不宣称已完成；不无限等待
```

### 顺序规则

- 首先使用 Spring Boot 的 graceful web-server shutdown 停止准入；Coordinator 的生命周期 phase/依赖必须通过真实 Spring Context 测试证明其执行位置，不能凭 Bean 声明顺序推断。
- 随后调用 TaskStore.close()，再 shutdown 业务 ExecutorService。正常路径须等 `awaitTermination` 成功，确保执行包装器包含最终 `publish()` 的整个方法体已经返回。
- 业务 Executor 正常退出后，才关闭 dispatcher；dispatcher 的终态事件可在其有界 drain 窗口内投递给 Observer/Sink。
- dispatcher 关闭后才关闭 Sink；Sink 关闭后任何迟到的 offer 都返回 DISABLED，不能重新启动 writer 或悄悄换用 noop。
- 如果业务 Executor 在截止时间内没有退出，可请求 `shutdownNow()` 并再次限时等待；仍未退出则进入 INCOMPLETE，继续在剩余预算内关闭 dispatcher/Sink。晚到事件可能丢失，这是明确记录的降级结果，不是成功 drain。
- `processDefault()` 等非本宿主所有的 dispatcher 禁止关闭。
- 每一个被宿主创建的线程资源都必须能检查 `isAlive`/终态；计数归零、状态 CLOSED、subscription detached 都不能代替物理退出。

## 3. 建议的固定预算

建议将 OBS 自有资源的关闭预算固定为最多 8 秒，所有阶段以 `System.nanoTime()` 的单调时钟计算剩余时间，避免各阶段分别重置超时：

| 阶段 | 上限 | 超时处理 |
|---|---:|---|
| TaskStore.close + 请求停止 | 立即返回的同步阶段；单独记录耗时 | 若违反快速返回合同，视为 Coordinator 异常 |
| ExecutorService 优雅等待 | 5 秒 | 超时后调用 shutdownNow，并进入下一阶段前再等待最多 1 秒 |
| ExecutorService 中断后等待 | 1 秒 | 仍有活线程则记录 business-executor-incomplete |
| Dispatcher close | 1 秒（API 上限） | 检查 liveThreadCount、registeredCount、ticketCount、inFlightCount |
| Sink queue drain / writer close | 1 秒 | 队列未排空或 writer 存活则记录 sink-incomplete |
| **OBS 自有资源预算合计** | **最多约 8 秒** | 不含 Spring Web Server 自己的 graceful shutdown 阶段 |

这是设计建议，最终值需通过合成的慢工具/阻塞观察器压力测试校准。所有超时均是上限，不可无限等待；若多个阶段消耗共享截止时间，后续阶段只能使用剩余时间，不能逐项重新获得完整 8 秒。

### 关闭结果字段（只用于宿主运维状态，不进入匿名事件 Schema v1）

- `result`: COMPLETE / INCOMPLETE
- `businessExecutorTerminated`: boolean
- `dispatcherPhase`: CLOSED / other
- `dispatcherLiveThreads`, `registeredCount`, `ticketCount`, `inFlightCount`
- `sinkQueueRemaining`, `sinkWriterAlive`, `sinkWrittenCount`, `sinkDropCount`, `sinkWriteFailureCount`
- `timedOutStage`: 固定枚举或 null

不得包含 taskId、runId、owner、sessionNonce、原始事件、异常原文、提示词或工具参数。该关闭结果不应伪装成学生请求的逐 Run Trace。

## 4. 测试矩阵（未来实现阶段的硬门禁）

| ID | 场景 | 通过条件 |
|---|---|---|
| LC-01 | OBS 缺省关闭的 Spring Context | TaskStore 仍用四参数构造器；无 Sink writer、无 dispatcher 注册/线程；既有启动条件不变 |
| LC-02 | OBS 开启但没有 Sink/日志策略 | Context refresh 明确失败；不得回退 noop/测试 Buffer |
| LC-03 | OBS 显式开启的合成 Context | 六参数 TaskStore 接收白名单 AnonymousRuntimeObserver 与自有 dispatcher；只有 schema v1 字段进入 Sink |
| LC-04 | 关闭顺序 | 观测顺序可证：web server 停止准入 → TaskStore.close → executor shutdown/terminated → dispatcher close → Sink close |
| LC-05 | 慢业务工具 | 在宽限窗口内收到 stop；正常结束时最终状态/轨迹先完成，dispatcher 再 drain |
| LC-06 | 永不返回的合成业务任务 | 触发超时；有限时间返回 INCOMPLETE；不假称 executor terminated；dispatcher/Sink 仍被限时关闭 |
| LC-07 | Sink 队列饱和 | offer 非阻塞并返回 FULL；丢弃计数增加；业务结果、TaskSnapshot 与 Trace 不变 |
| LC-08 | 日志写线程阻塞 | Runtime Observer 回调不直接阻塞在日志 I/O；关闭预算到期后报告 writer alive/incomplete |
| LC-09 | Sink writer 异常 | 后续 offer 返回 FAILED；异常文本不外泄；不递归上报；不切换为 noop |
| LC-10 | 敏感字段负例 | 注入合成 prompt、reply、runId、tool arguments、evidence ID/hash；日志 Sink 与普通日志无任何这些标记 |
| LC-11 | 强制审计故障 | mandatory provider audit 的 AUDIT_UNAVAILABLE 仍 fail-closed；OBS Sink 故障不得使其放行 |
| LC-12 | 身份/会话回归 | 合成 HTTP 继续验证 Owner/Session 隔离、幂等、续答、取消与 202 受理语义 |
| LC-13 | 关闭后迟到事件 | Sink 关闭后 offer 返回 DISABLED；不创建线程、不恢复队列、不复活资源 |
| LC-14 | 物理资源泄漏 | 正常关闭时 executor、dispatcher 与 sink writer 均物理退出；阻塞负例必须明确判 INCOMPLETE |

## 5. 进入实现前必须确认

- [ ] 运维负责人书面确认 stdout 是否被采集、日志可访问角色、保留/删除政策和外发范围。
- [ ] ADR-OBS-001 从 PROPOSED 变为 ACCEPTED，或明确选用其他目的地并记录替代 ADR。
- [ ] 确认新配置开关缺省关闭；打开时缺少 Sink/策略必须启动失败。
- [ ] 确认 Spring graceful shutdown 与 Coordinator 的实际生命周期 phase/依赖，并由测试证明顺序。
- [ ] 确认业务 ExecutorService 的可关闭所有权；不接受仅有 Executor 接口而无法 awaitTermination 的替代实现。
- [ ] 通过 LC-01 至 LC-14 的合成测试后，再单独申请真实 /rules 接线评审。

**结论：本设计为后续实现提供了可执行合同，但生产日志目的地的采集、访问和保留政策尚无证据，因此仍不得实现或启用生产接线。**
