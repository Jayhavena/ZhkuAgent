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

## 记录 04：专用执行器与 Agent 所有权（阶段 10B）

### 基本信息与测试结果

| 字段 | 记录值 |
| --- | --- |
| 验证日期 | 2026-09-05 |
| 起点提交 | `74b7fd9 refactor(agent): inject stream task executor` |
| 被验证代码 | 当前工作区，尚未提交 |
| 运行环境 | Windows 11 amd64、Zulu JDK 21.0.11、Maven 3.9.9、单 JVM |
| 阶段回归 | 9 个测试类、17 项测试，17 成功、0 失败、0 错误、0 跳过 |

RED/GREEN 证据：

| 场景 | RED | GREEN | 能证明 |
| --- | --- | --- | --- |
| 执行器配置 | 1 项失败：容器中不存在 `agentRunExecutor` | 1/1 通过 | 专用 Bean 的容量、线程名和拒绝策略符合配置 |
| Agent 所有权 | 1 项失败：容器持有 `yuManus` 单例 | 2/2 通过（最终回归） | 容器不再持有 YuManus；工厂连续创建不同实例 |
| 饱和拒绝 | 正确实现首次 1/1 通过，不伪造 RED | mutation 后 1 项失败，恢复后 1/1 通过 | core=1、queue=1 下第 3 个任务拒绝，并遵守 FAILED→cleanup→termination |

拒绝用例含 3 个任务：1 个运行、1 个排队、1 个拒绝；这是一次确定性饱和故障
验证，不是 3 个任务的吞吐量测试，也不能证明高并发能力。mutation 只临时把
termination 移到 cleanup 前，`AgentRunContext` 立即拒绝非法时序；错误代码已恢复。

调试记录：所有权 GREEN 首次命令观察窗口先返回“仍在运行”，继续读取同一 Maven
进程后得到 1 项通过和 BUILD SUCCESS。根因是 Maven 冷编译超过工具的 30 秒观察
窗口，不是测试超时；因此没有延长断言等待或修改生产代码。

原始输出：

```text
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10b-executor-config-red.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10b-executor-config-green.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10b-agent-ownership-red.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10b-agent-ownership-green.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10b-rejection-order-mutation-red.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10b-executor-rejection-green-after-mutation.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10b-phase-regression.log
```

## 记录 05：协作式终止延迟与残留（阶段 10C）

### 指标口径与结果

| 指标 | 实测值 |
| --- | ---: |
| JUnit 测试数量 | 2（重复取消 1、优雅关闭 1） |
| 顺序取消样本 | 20 |
| 同时并发任务数 | 1 |
| 成功完成生命周期断言 | 20/20 |
| p95 停止请求→termination | 0.213 ms |
| 最大停止请求→termination | 6.098 ms |
| 最终活动任务 | 0 |
| 最终排队任务 | 0 |

优雅关闭用例还在关闭开始前确定性构造 1 个运行中任务和 1 个排队任务，确认 shutdown
不会提前返回，两个已接受任务依次完成 cleanup 和 termination 后，线程池才终止。

每个样本验证工作线程观察中断、step 先退出、cleanup 恰好一次、cleanup 期间
termination 未完成，以及 termination 后线程池恢复空闲。所有样本结束后执行器
成功关闭。原始输出：

```text
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10c-termination-characterization.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10c-graceful-shutdown-green.log
```

该结果属于可响应中断的内存测试基线，不能推导真实模型/工具终止延迟，不能表示
接口响应时间或性能提升。`awaitTerminationSeconds` 也只是关闭等待上限，不会强制
停止忽略中断的外部调用。

## 记录 06：SSE 失败语义 RED → GREEN（阶段 10D）

### 结果

| 阶段 | 测试数量 | 成功 | 失败 | 错误 | 跳过 | 超时 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| emitter 接缝行为保持 | 2 | 2 | 0 | 0 | 0 | 0 |
| SSE 生命周期 RED | 5 | 4 | 1 | 0 | 0 | 0 |
| SSE 生命周期 GREEN | 5 | 5 | 0 | 0 | 0 | 0 |

RED 的唯一失败为 emitter error：期望 `FAILED`，实际 `CANCELLED`。按照
“现象—假设—验证—根因—修正—回归”定位到 `finishAfterLoop()` 无差别地把所有
停止原因映射为取消；修正为 `SEND_FAILURE → FAILED` 后 5 项通过，没有放宽断言、
延长等待或吞掉异常。

可证明 timeout 和 completion/断连进入 `CANCELLED`，error 和 send failure 进入
`FAILED`，重复回调保留首个停止原因；5 条路径均执行一次 cleanup，termination
晚于 step 退出和 cleanup。不能证明真实 Servlet 容器、代理或浏览器的网络回调顺序。

原始输出：

```text
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10d-emitter-seam-regression.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10d-sse-lifecycle-red.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10d-sse-lifecycle-green.log
```

## 记录 07：封装、总回归与稳定性核验（阶段 10E）

### RED → GREEN 与总回归

| 阶段 | 测试数量 | 成功 | 失败 | 错误 | 跳过 | 超时 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 公共 setter RED | 1 | 0 | 1 | 0 | 0 | 0 |
| 公共 setter GREEN | 1 | 1 | 0 | 0 | 0 | 0 |
| 最终阶段回归 | 25 | 25 | 0 | 0 | 0 | 0 |

RED 通过真实公共 API 反射观察到 `setChatClient`、`setMaxSteps`、`setName`、
`setNextStepPrompt` 和 `setSystemPrompt` 共 5 个公共 setter。移除 Lombok `@Data`
并仅向子类保留 protected 配置访问后，公共 setter 数量降为 0。

### 稳定性核验

- 关键子集：拒绝 1 项 + 终止/关闭 2 项 + SSE 5 项 = 每轮 8 项测试；
- Maven 重复轮次：5；成功轮次：5/5；测试执行数：40/40；
- 每轮终止测试包含 20 个顺序取消样本，5 轮合计 100 个样本；
- 同时并发任务数仍为 1，不能把 100 个样本称为 100 并发；
- 五轮 p95：0.198、0.255、0.210、0.283、0.199 ms；
- 五轮各自 active=0、queue=0，所有 Maven 轮次 BUILD SUCCESS。

原始输出：

```text
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10e-encapsulation-red.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10e-encapsulation-green.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10e-final-phase-regression.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10e-final-stability-round-[1-5].log
target/evidence/agent-run-context/2026-09-05-74b7fd9/step10e-final-stability-summary.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/environment-and-revision.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/final-audit.log
target/evidence/agent-run-context/2026-09-05-74b7fd9/untracked-whitespace-check.log
```

### 当前可用于简历的保守表述

> 将流式 Agent 后台任务迁移到 Spring 管理的专用有界执行器，统一每请求 Agent
> 工厂所有权；通过 25 项单元测试和 5 轮关键生命周期回归验证任务拒绝、SSE
> 失败分类、幂等 cleanup 及“工作线程退出和清理完成后才 termination”的时序。

禁止写“支持高并发”“吞吐量提升”“响应时间降低”“彻底消除资源泄漏”或“降低
线上故障率”。这些说法分别需要压力测试、同环境前后基准、真实外部调用残留验证
和生产数据，当前均尚未测量。
