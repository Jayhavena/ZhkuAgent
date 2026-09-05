# AgentRunContext 证据账本

## 记录 01：BaseAgent 同步运行状态迁移

### 基本信息

| 字段 | 记录值 |
| --- | --- |
| 验证日期 | 2026-09-05 |
| 被验证代码提交 | `ccc20d8 fix(agent): isolate concurrent run state` |
| 基础上下文提交 | `c08df45 feat(agent): add isolated run context` |
| 验证时当前 HEAD | `87e47af docs(agent): document run-context migration workflow` |
| 当前分支 | `codex/agent-run-context` |
| 仓库工作区 | 不干净；存在与本阶段无关的未跟踪文件 |
| Agent 代码范围 | 干净；提交后的生产代码和测试没有额外修改 |
| 交付阶段 | 个人项目中的原型/技术验证 |

### 执行命令

聚焦测试：

```powershell
.\mvnw.cmd "-Dtest=BaseAgentFailureLifecycleTest,ReActAgentDecisionIsolationTest" test
```

阶段回归：

```powershell
.\mvnw.cmd "-Dtest=BaseAgentFailureLifecycleTest,ReActAgentDecisionIsolationTest,BaseAgentLifecycleTest,AgentRunContextTest" test
```

### 测试结果

| 测试类 | 测试数量 | 成功 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `AgentRunContextTest` | 6 | 6 | 0 | 0 | 0 |
| `BaseAgentLifecycleTest` | 2 | 2 | 0 | 0 | 0 |
| `ReActAgentDecisionIsolationTest` | 1 | 1 | 0 | 0 | 0 |
| `BaseAgentFailureLifecycleTest` | 2 | 2 | 0 | 0 | 0 |
| **阶段回归合计** | **11** | **11** | **0** | **0** | **0** |

- 本次阶段回归测试成功率：`11 / 11 = 100%`；
- 本次测试逻辑报告的超时数量：`0`；
- 本次正式证据采集轮次：聚焦测试 1 轮，阶段回归 1 轮；
- 上述成功率是 JUnit 测试用例成功率，不是并发任务压力测试成功率；
- 当前没有吞吐量、响应延迟、CPU 或内存性能测量。

### 并发验证口径

| 场景 | 测试内部并发任务数 | 重复轮次 | 观察到的消息污染 | 观察到的步骤错配 | 观察到的 decision 错配 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 同一 Agent 的 context 隔离 | 2 | 1 | 0 | 0 | 不适用 |
| ReAct decision 配对 | 2 | 1 | 不适用 | 不适用 | 0 |

两个场景分别执行，不能合并描述为 4 个任务同时并发。该结果属于确定性双任务
技术验证，不是压力测试，也不能证明系统支持高并发。

### RED → GREEN 与变异证据

1. 顺序复用 RED：旧实现的第二次运行被上一次 `FINISHED` 状态拒绝；
2. 顺序复用 GREEN：迁移后同一实例连续运行两次，消息分别隔离，步骤均从 1 开始；
3. decision 当前 GREEN：局部 decision 传递测试通过；
4. mutation RED：临时改为实例共享 `currentDecision` 后，配对测试按预期失败；
5. 恢复 GREEN：删除临时字段并恢复局部变量后，11 项阶段回归通过；
6. 不稳定测试修正：定位首次 `UUID.randomUUID()` 冷启动导致的超时误报，
   在测试同步计时窗口前预热后恢复稳定。

RED、GREEN 和 mutation 的详细过程位于 `docs/tutorials/04` 至
`docs/tutorials/08` 对应教程。mutation 使用的错误代码已经恢复，生产代码中
不存在 `currentDecision`。

### 当前可以证明的结论

1. 每次同步 `run` 创建独立 `AgentRunContext`；
2. 同一个 Agent 实例能够顺序复用，运行间消息和步骤不继承；
3. 在确定性的两个并发任务场景中，runId、消息和步骤相互隔离；
4. `ReActAgent` 使用当前方法局部 decision 完成 think-act 配对；
5. think 或 act 抛出运行时异常时，原异常继续传播，当前 context 进入 `FAILED`；
6. 同步失败路径的 cleanup 每次只执行一次；
7. cleanup 执行期间 termination 尚未完成，cleanup 返回后 termination 以
   `FAILED` 完成；
8. 11 个聚焦单元测试在提交 `ccc20d8` 上通过。

### 尚未测量或不能得出的结论

1. 尚未进行多任务、多轮次压力测试；
2. 尚不能声称支持高并发；
3. 尚未测量吞吐量、响应时间、CPU、内存和 GC；
4. 尚不能声称性能得到提升；
5. 尚未验证 SSE timeout、断连和发送失败后后台任务真实退出；
6. 尚未测量停止请求到 termination 的 p50、p95 或最大延迟；
7. 尚未检查专用线程池的队列上限、拒绝策略和任务残留；
8. 尚未获得真实模型、真实工具或生产环境数据；
9. 尚不能声称降低线上故障率或产生生产收益。

### 原始输出位置

本地原始输出，不进入 Git：

```text
target/evidence/agent-run-context/2026-09-04-ccc20d8/focused-tests.log
target/evidence/agent-run-context/2026-09-04-ccc20d8/phase-regression.log
target/surefire-reports/
```

目录名保留最初计划使用的 `2026-09-04`，文件实际采集时间为 2026-09-05；
后续新证据目录应使用真实执行日期。

进入 Git 的证据摘要：

```text
docs/evidence/AgentRunContext-证据账本.md
docs/tutorials/04-BaseAgent迁移-步骤1-顺序复用RED测试.md
docs/tutorials/05-BaseAgent迁移-步骤2-RunContext接入GREEN.md
docs/tutorials/06-BaseAgent迁移-步骤3-并发隔离验证.md
docs/tutorials/07-BaseAgent迁移-步骤4-think-act决策配对验证.md
docs/tutorials/08-BaseAgent迁移-步骤5-失败生命周期验证.md
```

### 简历表述边界

原始证据：提交 `ccc20d8`，11 个单元测试通过，包含双任务并发隔离、decision
变异检查和同步失败收尾验证。

可得出的技术结论：完成请求级运行状态隔离，并在单 JVM、内存测试替身范围内
验证复用、并发配对和失败生命周期。

保守简历表述：

> 将 Agent 请求级状态迁移至独立 `AgentRunContext`，以局部不可变 decision
> 消除步骤响应共享，并通过 11 个确定性单元测试验证同一实例复用、双任务
> 并发隔离及异常后的 `FAILED → cleanup → termination` 收尾顺序。

禁止使用：

- “支持高并发”；
- “性能提升 `[比例]`”；
- “吞吐量提升 `[比例]`”；
- “生产故障率下降 `[比例]`”；
- “线上消息污染降为 0”。

只有完成后续压力测试或同环境性能基准后，才能使用
`[并发任务数]`、`[成功率]`、`[p95 终止延迟]` 等实际测量值。

## 记录 02：流式任务句柄 RED → GREEN

### 基本信息

| 字段 | 记录值 |
| --- | --- |
| 验证日期 | 2026-09-05 |
| 起点提交 | `3fcaf93 docs(agent): add run-context evidence ledger` |
| 被验证代码 | 本记录所在的后续修复提交；提交前尚无哈希 |
| 测试环境 | 单 JVM、内存测试 Agent、Java 21、Maven Surefire |
| 当前执行器 | `ForkJoinPool.commonPool()` 过渡实现，尚非专用有界执行器 |

### RED → GREEN 结果

| 阶段 | 测试数量 | 成功 | 失败 | 错误 | 跳过 | 退出码 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 修复前聚焦 RED | 1 | 0 | 1 | 0 | 0 | 1 |
| 修复后聚焦 GREEN | 1 | 1 | 0 | 0 | 0 | 0 |
| 最终阶段回归 | 12 | 12 | 0 | 0 | 0 | 0 |

RED 的核心失败信息为：停止请求应当中断正在运行的流式工作线程，但在未绑定任务
句柄的旧实现上观察值为 `false`。GREEN 实现通过 `AgentRunTask` 在提交线程池前创建
并绑定 `FutureTask`，工作线程进入包装器时标记 `workerStarted`。

最终源码完成格式整理后，于 2026-09-05 13:53 再次执行 12 项阶段回归并通过。
异常生命周期测试主动输出的 `think failure` 和 `act failure` 堆栈属于预期测试数据。

### 并发口径

本用例包含 1 个流式后台任务和 1 个测试控制线程，只验证单个在途任务的停止链路，
并发压力测试任务数和重复轮次均尚未测量。因此不能把 12 个测试描述成 12 个并发任务。

### 当前可以证明

1. 新测试能在旧实现上稳定捕获“停止请求无法中断在途流式任务”的缺陷；
2. 任务句柄在提交给执行器前绑定，避免“先提交、后保存句柄”的窗口；
3. 在确定性测试中，停止请求能够使阻塞中的工作线程观察到中断；
4. 该取消路径最终进入 `CANCELLED`，cleanup 执行一次，termination 完成；
5. 包级 `bindTask` 和 `markWorkerStarted` 没有被扩大为公共生命周期 API；
6. 包含既有回归在内的 12 项单元测试通过。

### 尚不能证明

1. 尚未使用专用有界执行器，也未验证队列容量与拒绝策略；
2. 尚未验证 SSE timeout、断连和发送失败回调的端到端行为；
3. Java 中断是协作式机制，不能证明所有模型或工具 SDK 阻塞都能及时退出；
4. 尚未测量停止请求到 termination 的 p50、p95 和最大延迟；
5. 尚未进行多任务、多轮压力测试；
6. 尚无吞吐量、响应时间、CPU、内存或生产故障率数据。

### 原始输出

本地保存、不进入 Git：

```text
target/evidence/agent-run-context/2026-09-05-3fcaf93/step8-stream-task-handle-red.log
target/evidence/agent-run-context/2026-09-05-3fcaf93/step9-stream-task-handle-green.log
target/evidence/agent-run-context/2026-09-05-3fcaf93/step9-phase-regression.log
target/surefire-reports/
```

### 简历表述边界

本阶段完成提交后，可以保守描述为：

> 为 Agent 流式运行建立提交前绑定的可取消任务句柄，通过 RED → GREEN 回归复现并
> 修复停止请求无法触达在途工作线程的问题，结合 12 项单元测试验证取消状态、单次
> cleanup 与真实 termination 收尾。

不得描述为“支持高并发”“终止延迟降低某比例”或“彻底消除线程泄漏”；这些结论
必须等待专用有界执行器、重复压力测试和延迟测量完成后再补证据。

### 提交质量修正记录

首次本地提交 `fc7d153` 的功能和 6 个文件范围正确，但提交后使用
`git show --check fc7d153` 发现 `BaseAgent.java` 存在 6 行行尾空格、文件末尾缺少
换行，并因整文件格式化混入 67 行新增和 58 行删除。该问题不影响 12 项测试的功能
结论，但会降低代码审查质量。

处理策略是在确认分支尚未关联远端上游后，为原提交建立本地恢复分支，只恢复
`BaseAgent.java` 到父提交，再重新应用任务句柄所需的最小差异，复测后 amend。
最终修正提交号应通过以下命令查询：

```powershell
git log -1 --oneline
```

这段记录证明提交质量问题经过了“现象—定位—修正—回归”，但不能作为新增功能或
性能收益计算。

## 记录 03：流式执行器注入接缝 RED → GREEN

### 基本信息

| 字段 | 记录值 |
| --- | --- |
| 验证日期 | 2026-09-05 |
| 起点提交 | `d815263 fix(agent): bind cancellable stream task handle` |
| 被验证代码 | 本记录所在的阶段 10A 提交；提交前尚无哈希 |
| 工作区状态 | 全局非干净，存在与本阶段无关的未跟踪用户文件；Agent 目标范围只有本阶段修改 |
| 测试环境 | 单 JVM、Java 21、Maven Surefire、内存测试 Agent |
| 生产执行策略 | 无参构造器仍委托 `ForkJoinPool.commonPool()`；专用有界执行器尚未实施 |

### RED → GREEN 结果

| 阶段 | 测试数量 | 成功 | 失败 | 错误 | 跳过 | 超时 | 退出码 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 修改前阶段基线 | 12 | 12 | 0 | 0 | 0 | 0 | 0 |
| 构造接缝回归 | 12 | 12 | 0 | 0 | 0 | 0 | 0 |
| 执行器选择 RED | 1 | 0 | 1 | 0 | 0 | 0 | 1 |
| 执行器选择 GREEN | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| 最终阶段回归 | 13 | 13 | 0 | 0 | 0 | 0 | 0 |

RED 运行于 2026-09-05 18:16，核心失败是注入执行器的任务数期望为 1、实际为 0；
同一份日志同时显示任务运行在 `ForkJoinPool.commonPool-worker-1`。这证明失败来自
`runStream()` 绕过注入入口，而不是编译错误、等待超时或环境冷启动。

GREEN 只把提交语句替换为 `streamExecutor.execute(streamTask)`，没有修改
`AgentRunTask` 的提交前绑定，也没有修改异常收尾和 cleanup/termination 顺序。
最终阶段回归于 2026-09-05 18:22 完成，Maven 汇总和 6 份 Surefire XML 报告均为
13 项测试、0 失败、0 错误、0 跳过。

重新查看真实 diff 时还发现：起点提交 `d815263` 的 `BaseAgent.java` 末尾仍缺少
换行，`git show --check` 不会把这一点报告为空白错误。本阶段通过正常文件编辑补上了
末尾换行。因此记录 02 中“格式修正”的结论只能覆盖已验证的行尾空格和最小差异，
不能继续推断该提交已经修复文件末尾换行。

### 本阶段新增的可证明结论

1. `BaseAgent.runStream()` 的任务提交由构造器注入的 `Executor` 控制；
2. 测试无需 sleep、网络、模型和工具即可确定性控制任务开始时点；
3. 注入执行器推进真实 `FutureTask` 后，运行进入 `SUCCEEDED`，cleanup 执行一次，
   termination 完成；
4. 替换提交入口后，既有状态隔离、异常、取消和终止相关回归未退化；
5. 本阶段 6 个测试类共 13 项相关单元测试通过。

### 尚未测量或不能得出的结论

1. 生产环境仍使用公共池兼容入口，尚未形成专用资源隔离；
2. 尚未验证线程数、队列容量和拒绝策略；
3. 本阶段没有并发压力测试，测试数量不能当作并发任务数量；
4. 没有记录重复测试轮次，不能声称长期运行成功率；
5. 没有吞吐量、响应时间、CPU、内存或终止延迟数据；
6. 尚未解决 `YuManus @Component` 与控制器每请求手动创建的所有权冲突。

### 原始输出

本地保存、不进入 Git：

```text
target/evidence/agent-run-context/2026-09-05-d815263/step10a-baseline.log
target/evidence/agent-run-context/2026-09-05-d815263/step10a-constructor-seam-green.log
target/evidence/agent-run-context/2026-09-05-d815263/step10a-executor-selection-red.log
target/evidence/agent-run-context/2026-09-05-d815263/step10a-executor-selection-green.log
target/evidence/agent-run-context/2026-09-05-d815263/step10a-phase-regression.log
target/surefire-reports/
```

### 简历表述边界

本阶段完成提交后，可以保守描述为：

> 为 Agent 流式任务建立可注入执行器接缝，通过确定性 RED → GREEN 测试复现并
> 消除硬编码公共池提交行为，结合 13 项单元测试验证既有运行隔离、取消和终止契约
> 未发生回归。

不得描述为“使用专用有界线程池”“支持高并发”“提高吞吐量”或“消除线程泄漏”；
这些结论需要阶段 10B/10C 的拒绝、重复运行、终止延迟和资源残留证据。
