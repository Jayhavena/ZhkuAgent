# OBS-02D：/rules 真实接线安全评审

- 日期：2026-10-22
- 状态：**REVIEW_COMPLETE / REAL_RULES_WIRING_NO_GO**
- 评审基线：OBS-02C commit 37dddf3ffa805ea733e9e66b32bc509056c93622，tree fb01e6d895099cfe6367da97febde6449a9cac4f。
- 方法：只读源码与既有离线回执检查。本轮没有修改业务源码、运行真实 HTTP、连接数据库/模型供应商或触碰学生请求。

## 1. 评审结论

OBS-02C 已证明：在同一份 T08 + OBS 源码树中，六参数 TaskStore 构造器可以接收 AnonymousRuntimeObserver 与显式 dispatcher，匿名投影及现有合成合同通过。但这只证明离线合成兼容性，不能直接放行真实 HTTP 接线。

**当前结论：NO-GO 真实 /rules 接线。** 阻塞点不是 Runtime ABI，而是尚无生产级 ObservationSink、尚无明确的 Spring 可选装配方案和有证据的关闭协调顺序。此时将测试内存缓冲或 noop 接入真实链路会制造“已埋点”的假象，却没有可审计的生产观测结果。

## 2. 已核实事实

### 2.1 前端 /rules 与后端 API 不是同一个路径

- 学生页面路由是前端 SPA 的 /rules。
- 前端 API 模块 agent/frontend/src/api/rule-check.js 的 PREFIX 为 /api/zhku/controlled-preview/v2；rule-task.js 通过 /tasks、/tasks/capabilities、/tasks/{taskId}/messages、/cancel、/originals 发起请求。
- 后端 RuleAgentTaskController 的类级映射为 /zhku/controlled-preview/v2/tasks；在既有 /api servlet context-path 下，对应完整 API 路径 /api/zhku/controlled-preview/v2/tasks。
- 因此“接入 /rules”实际是为现有受控任务 API 的 TaskStore 增加观察器装配，不应新增或改写前端路由，也不应把 /rules 当成后端 Controller endpoint。

### 2.2 当前 Spring 装配默认保持关闭

- RuleAgentHttpConfiguration 只有在 zhku.rule-agent-http.enabled=true 时注册 Controller 与 TaskStore。
- 当前 ruleAgentTaskStore Bean 仍调用四参数构造器；它没有 Observer 或 dispatcher 注入。
- RuleAgentOnlineHttpConfiguration 还要求 HTTP 和 online-enabled 双开关。
- RuleAgentControlledLaunchConfiguration 还要求 HTTP 与 launch.enabled 双开关，并强制显式声明 SIMULATED 或 ONLINE；ONLINE 必须有在线开关与真实 Factory，不允许隐式降级到模拟执行。
- 任何 OBS 配置都不能放宽现有受控启动门禁或默认打开真实请求入口。

### 2.3 当前没有生产 ObservationSink

在 knowledge/src/main/java 范围内，ObservationSink 的实际实现只有 BoundedObservationBuffer；另有 ObservationSink.noop() 工厂：

- noop() 返回 DISABLED，AnonymousRuntimeObserver 只增加 drop 计数，不产生可用观测。
- BoundedObservationBuffer 源码注释明确其为离线测试用容量界限，不代表持久化或数据保留政策；目前没有生产 exporter、读取端、保留期限、访问控制或可靠交付协议。
- 因此把 noop() 当生产实现会导致零可用数据；把测试 buffer 当生产实现则会形成不可审计的进程内短期数据，不满足生产观测要求。

### 2.4 生命周期不能只靠两个 destroyMethod

T08 明确规定：六参 TaskStore 不拥有也不关闭注入的 dispatcher；宿主负责 dispatcher 创建、停机顺序和物理线程检查。

当前 HTTP executor Bean 使用 destroyMethod=shutdown，TaskStore 使用 destroyMethod=close。TaskStore.close() 会对未发布 Context 请求停止并清理任务索引，但不等待业务包装器全部退出；ExecutorService.shutdown() 本身也不证明所有任务或线程已结束。dispatcher 的 close 预算最多 1 秒，逻辑 CLOSED/DETACHED 不等于物理线程已退出。

所以未来装配若仅添加一个 dispatcher Bean 并标记 destroyMethod=close，尚不足以证明：
1. 停止新请求后，已受理任务有机会执行收尾；
2. 业务工作线程返回后，观察器才进入关闭阶段；
3. dispatcher 在限时内关闭，且 liveThreadCount 实际归零；
4. 队列饱和、阻塞回调或关闭竞态不会被报告成成功交付。

这需要明确的宿主关闭协调与真实 Spring 生命周期测试，不可只依赖 Bean 声明顺序推断。

### 2.5 隐私和身份边界

- Runtime AgentRunEvent 原始事件可能包含 runId、工具参数/结果及错误文本；必须先通过 ObservationProjector.anonymous 白名单投影，不能直接日志化或传给 Sink。
- ObservationEventV1 仅是匿名元数据，不带可关联的 Task/Run/Owner 标识，不能被当成逐请求 Trace。
- T08 RuleAgentRunTrace 含 Owner 约束下的 UUID、动作、证据标识及哈希；不能送入匿名 Sink。
- Controller 现有身份仓储复核、会话绑定、Owner/Session 隔离、CSRF、Cache-Control: no-store 与 202 受理语义均应保持不变；禁止把 actorId、sessionNonce、taskId 或 runId 加为遥测标签。
- mandatory provider audit 的 AUDIT_UNAVAILABLE 必须继续 fail-closed。匿名投递失败只能影响观测计数，不能改写业务/审计结果。

## 3. 六项接线安全门禁

| 门禁 | 当前状态 | 放行条件 |
|---|---|---|
| 固定源码与接口 | PASS（OBS-02C） | 保持 T08 六参 ABI 与 OBS v1 投影不变 |
| API 与身份边界 | 静态 PASS，真实 Spring 组合未测 | 合成身份/会话隔离、Owner 访问、幂等与续答的 HTTP 回归 |
| 生产 Sink | **BLOCKED** | 先决定并实现生产目的地、字段白名单、保留/访问/故障政策；不能用 noop 或测试 buffer 代替 |
| 可选装配与默认关闭 | **BLOCKED** | OBS 开关缺省关闭；关闭时仍走四参路径；启用时必须显式提供有效 Sink，不得静默回退 |
| 生命周期与资源退出 | **BLOCKED** | 关闭协调器证明业务执行退出先于 dispatcher 关闭，且检查物理线程归零 |
| 审计、隐私和评测来源 | 部分已有合成证据 | mandatory audit fail-closed 回归；不混用匿名事件与 T08 Trace；真实 Java-to-evaluator 来源验证另行完成 |

## 4. 下一步建议

先进入 OBS-02E：生产 Sink 与宿主生命周期合同设计，不改 Controller、前端或在线工厂。

最小交付应是：
1. 明确观测用途是匿名聚合指标还是逐请求 Trace；本项目当前 v1 只支持前者。
2. 决定生产 Sink 的目的地、非阻塞/有界策略、drop/failure 可见性、字段保留期限、访问权限、版本演进和停机行为。
3. 明确独立开关及 Bean 装配规则：OBS 缺省关闭；关闭时保留四参 TaskStore；启用时若 Sink/dispatcher 缺失必须启动失败，不能默默切换到 noop。
4. 定义宿主停机协调：停止准入、请求 TaskStore 收尾、等待业务执行器退出，再关闭自有 dispatcher，并在固定预算内验证物理线程。
5. 为新配置编写纯合成 Spring 上下文测试和本地回环 HTTP 测试，使用测试身份与假 Factory；不得调用真实供应商/数据库，也不发送真实学生请求。
6. 只有该合同、实现和测试另行通过评审后，才修改 RuleAgentHttpConfiguration 的最小范围 Bean 装配。

## 5. 本轮边界

本轮只做只读审查，没有运行新的测试，没有修改业务代码，没有添加 Sink/Bean，没有 push、PR、部署或合并。

**最终裁定：OBS-02C 合成集成证据有效；OBS-02D 真实 /rules 接线 NO-GO。** 先解决生产 Sink 与关闭生命周期的可审计性，再进入接线实现。
