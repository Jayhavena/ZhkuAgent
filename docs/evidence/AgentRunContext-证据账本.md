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
