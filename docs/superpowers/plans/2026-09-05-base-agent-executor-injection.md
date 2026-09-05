# BaseAgent Executor Injection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `BaseAgent.runStream()` 建立可注入的 `Executor` 接缝，并用确定性行为测试证明后台任务由注入执行器控制，同时保持现有生产策略和 12 项生命周期回归不变。

**Architecture:** 本阶段只修改 `BaseAgent`：新增受保护的执行器构造器，并临时保留委托给 `ForkJoinPool.commonPool()` 的无参构造器，避免提前改变 `YuManus @Component` 的 Spring 装配。先通过现有回归保护无行为变化的构造接缝，再用手动推进的真实 `Executor` 形成有效 RED，最后把流式任务提交点从硬编码公共池切换到实例持有的接口依赖。

**Tech Stack:** Java 21、Spring Boot 3.4.4、Spring MVC `SseEmitter`、JUnit 5、AssertJ、Maven Surefire、JDK `Executor`/`FutureTask`。

**Spec:** `docs/decisions/2026-09-05-agent-executor-lifecycle.md`

## Global Constraints

- 以分支 `codex/agent-run-context`、提交 `d815263` 和重新执行的 12 项相关测试作为基线；若事实不一致，停止并更新计划。
- 本阶段不得修改 `YuManus`、`AiController` 或 Spring Bean 装配；这些属于阶段 10B。
- 无参构造器委托公共池只是明确的过渡兼容入口，阶段 10B 必须删除。
- 任务句柄仍必须在提交前绑定；工作线程仍独占最终 cleanup 和 termination 职责。
- 测试验证真实任务控制流，不读取私有字段、不扫描源码、不只断言 mock 调用。
- 测试失败必须按“现象—假设—验证—根因—修正—回归”处理，不先延长超时时间。
- 原始输出写入 `target/evidence/agent-run-context/2026-09-05-d815263/`，不进入 Git。
- 不使用 `git add .`；未经用户审阅和明确确认，不执行暂存、提交、amend、推送或创建 PR。
- 12/13 表示测试数量，不表示并发任务数、重复轮次、吞吐量或性能提升。

---

## File Map

| 文件 | 职责 | 本阶段动作 |
| --- | --- | --- |
| `src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java` | Agent 同步/流式运行边界和任务提交 | 新增执行器依赖与兼容构造器；GREEN 时替换唯一提交点 |
| `src/test/java/com/yupi/yuaiagent/agent/BaseAgentExecutorSelectionTest.java` | 验证注入执行器真实控制流式任务 | 新增确定性 RED → GREEN 测试 |
| `docs/evidence/AgentRunContext-证据账本.md` | 保存结构化、可核验结论 | 完成测试后新增记录 03 |
| `docs/decisions/README.md` | 决策状态索引 | 10A 完成后将总决策标记为“实施中” |
| `docs/decisions/2026-09-05-agent-executor-lifecycle.md` | 总体设计、代价和边界 | 追加 10A 实际结果，不重复教程 |
| `docs/superpowers/plans/2026-09-05-base-agent-executor-injection.md` | 本阶段执行契约 | 勾选实际完成步骤并保留审阅依据 |

`ReActAgent.java`、`ToolCallAgent.java`、`YuManus.java`、`AiController.java` 以及现有测试夹具在本阶段不修改。这样可以把构造链和 Spring 所有权变化留给 10B，避免一个 RED 同时对应多个原因。

---

### Task 1: 重新核验基线并保存原始输出

**Files:**
- Read: `src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java`
- Read: `docs/evidence/AgentRunContext-证据账本.md`
- Create locally: `target/evidence/agent-run-context/2026-09-05-d815263/step10a-baseline.log`

**Interfaces:**
- Consumes: 当前 `BaseAgent.runStream(String)` 和已有五个相关测试类。
- Produces: 可与 GREEN 对比的 12 项测试基线；不产生代码差异。

- [x] **Step 1: 核验分支、提交和任务范围**

```powershell
git branch --show-current
git log -1 --oneline
git status --short -- `
  "src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java" `
  "src/test/java/com/yupi/yuaiagent/agent" `
  "docs/evidence/AgentRunContext-证据账本.md" `
  "docs/decisions" `
  "docs/superpowers/plans/2026-09-05-base-agent-executor-injection.md"
```

预期：分支为 `codex/agent-run-context`，HEAD 为 `d815263`；Agent 生产代码和测试没有本阶段开始前的未提交修改。设计文档和本计划属于已知的新文件，不与用户的其他未跟踪内容混合。

- [x] **Step 2: 创建不进入 Git 的证据目录**

```powershell
New-Item `
  -ItemType Directory `
  -Force `
  -Path "target/evidence/agent-run-context/2026-09-05-d815263" | Out-Null
```

`-Force` 在目录已存在时复用目录，不删除或覆盖其中其他文件；`Out-Null` 只隐藏目录对象输出。

- [x] **Step 3: 运行 12 项基线回归并保存输出**

```powershell
./mvnw.cmd `
  "-Dtest=AgentRunContextTest,BaseAgentLifecycleTest,BaseAgentFailureLifecycleTest,ReActAgentDecisionIsolationTest,BaseAgentStreamCancellationTest" `
  test 2>&1 | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-baseline.log"

$baselineExitCode = $LASTEXITCODE
"MAVEN_EXIT_CODE=$baselineExitCode" | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-baseline.log" `
  -Append

if ($baselineExitCode -ne 0) {
    throw "阶段 10A 基线失败，停止实施并先完成故障分类"
}
```

`-Dtest` 只选择本次迁移的相关测试；`2>&1` 合并标准错误与标准输出；`Tee-Object` 同时在终端显示并保存原始日志。预期为 12 tests、0 failures、0 errors、0 skipped、退出码 0。

---

### Task 2: 添加无行为变化的构造注入接缝

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java:14-46`
- Test: 现有五个相关测试类，共 12 项测试

**Interfaces:**
- Consumes: JDK `Executor`、现有 `ForkJoinPool.commonPool()` 行为。
- Produces: `protected BaseAgent(Executor streamExecutor)` 和临时 `protected BaseAgent()`；`runStream()` 此时仍未使用新字段。

这个任务是受既有测试保护的结构性重构，不改变任务实际提交位置。先建立可编译接缝，才能让下一任务的行为测试以断言失败而不是测试编译错误进入 RED。

- [x] **Step 1: 增加 import**

在 `BaseAgent.java` 的并发 import 中加入：

```java
import java.util.concurrent.Executor;
```

保留当前 `ForkJoinPool` import，因为兼容构造器仍然使用它。

- [x] **Step 2: 增加执行器字段和两个受保护构造器**

在 `chatClient` 字段之后加入以下完整代码：

```java
    /**
     * 执行流式 Agent 任务的资源入口。
     *
     * <p>字段属于 Agent 配置，不保存任何一次运行的状态。本阶段先建立注入
     * 接缝；下一阶段再由 Spring 专用有界执行器接管其生命周期。</p>
     */
    private final Executor streamExecutor;

    /**
     * 兼容现有 Agent 构造链的临时入口。
     *
     * <p>它保持当前公共池行为，避免本阶段提前改变 YuManus 的 Spring 装配。
     * 阶段 10B 贯通显式构造器后必须删除，不能长期隐藏执行器所有权。</p>
     */
    protected BaseAgent() {
        this(ForkJoinPool.commonPool());
    }

    /**
     * 使用调用方明确提供的执行器创建 Agent 定义。
     *
     * @param streamExecutor 执行流式任务的执行器
     */
    protected BaseAgent(Executor streamExecutor) {
        this.streamExecutor = streamExecutor;
    }
```

这里不使用 Lombok 生成构造器，因为生命周期依赖需要显式表达；也不增加本阶段
不需要、没有测试驱动的校验分支。

- [x] **Step 3: 确认任务提交点暂时未改变**

`runStream()` 在这个任务结束时必须仍是：

```java
            // 当前仍使用公共池作为过渡；后续再替换为专用有界执行器。
            ForkJoinPool.commonPool().execute(streamTask);
```

这不是最终实现，而是为了让下一项测试能够证明硬编码行为确实存在。

- [x] **Step 4: 运行 12 项结构重构回归**

```powershell
./mvnw.cmd `
  "-Dtest=AgentRunContextTest,BaseAgentLifecycleTest,BaseAgentFailureLifecycleTest,ReActAgentDecisionIsolationTest,BaseAgentStreamCancellationTest" `
  test 2>&1 | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-constructor-seam-green.log"

$seamExitCode = $LASTEXITCODE
"MAVEN_EXIT_CODE=$seamExitCode" | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-constructor-seam-green.log" `
  -Append

if ($seamExitCode -ne 0) {
    throw "构造接缝改变了既有行为，停止进入 RED 测试"
}
```

预期仍为 12 tests、0 failures、0 errors、0 skipped。该结果只证明添加字段和构造器没有破坏既有行为，不能证明 `runStream()` 已使用注入执行器。

---

### Task 3: 添加注入执行器控制流的有效 RED 测试

**Files:**
- Create: `src/test/java/com/yupi/yuaiagent/agent/BaseAgentExecutorSelectionTest.java`
- Read: `src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java:87-145`

**Interfaces:**
- Consumes: Task 2 产生的 `BaseAgent(Executor)` 构造器。
- Produces: 一个不依赖网络、模型、工具或 Spring 容器的确定性行为测试；旧提交点应得到 1 项失败、0 项错误。

- [x] **Step 1: 创建完整测试类**

```java
package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.runtime.AgentRunContext;
import com.yupi.yuaiagent.agent.runtime.AgentRunState;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BaseAgent 流式任务执行器选择测试。
 *
 * <p>测试使用一个由测试线程手动推进的真实 Executor。它验证的是任务是否
 * 服从构造器注入的执行入口，而不是读取 BaseAgent 私有字段或检查源码文本。</p>
 */
class BaseAgentExecutorSelectionTest {

    /**
     * 验证流式任务只有在注入执行器明确推进后才开始执行。
     */
    @Test
    void shouldRunStreamTaskOnlyWhenInjectedExecutorAdvances()
            throws Exception {
        ManualExecutor executor = new ManualExecutor();
        ExecutorControlledAgent agent =
                new ExecutorControlledAgent(executor);

        // runStream 只应创建并提交任务，不应绕过注入执行器立即执行 step。
        agent.runStream("executor-controlled-run");

        // 旧实现硬编码 commonPool，因此这里稳定得到 0 并形成 RED。
        assertThat(executor.queuedTaskCount())
                .as("流式 FutureTask 必须提交给构造器注入的执行器")
                .isEqualTo(1);
        assertThat(agent.stepCalls())
                .as("手动执行器推进前不应开始 Agent 步骤")
                .isZero();

        // 由测试线程执行队列中的真实 FutureTask，避免依赖调度和 sleep。
        executor.runNext();

        AgentRunContext context = agent.capturedContext();
        assertThat(context.termination().get(1, TimeUnit.SECONDS))
                .isEqualTo(AgentRunState.SUCCEEDED);
        assertThat(agent.stepCalls()).isEqualTo(1);
        assertThat(agent.cleanupCalls()).isEqualTo(1);
        assertThat(executor.queuedTaskCount()).isZero();
    }

    /**
     * 只保存已提交命令，并由测试显式决定执行时点的执行器。
     */
    private static final class ManualExecutor implements Executor {

        /** 测试线程是唯一读写者，无需引入额外并发噪声。 */
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            if (command == null) {
                throw new IllegalArgumentException(
                        "command must not be null"
                );
            }
            tasks.add(command);
        }

        /** 返回尚未被测试推进的任务数量。 */
        private int queuedTaskCount() {
            return tasks.size();
        }

        /** 执行下一项真实任务；没有任务表示生产代码没有使用该执行器。 */
        private void runNext() {
            Runnable task = tasks.poll();
            if (task == null) {
                throw new IllegalStateException(
                        "没有可执行的流式任务"
                );
            }
            task.run();
        }
    }

    /**
     * 记录真实 step、cleanup 和 RunContext 的最小流式 Agent。
     */
    private static final class ExecutorControlledAgent
            extends BaseAgent {

        /** 保存工作任务实际收到的运行上下文。 */
        private final AtomicReference<AgentRunContext> capturedContext =
                new AtomicReference<>();

        /** 记录真实步骤执行次数。 */
        private final AtomicInteger stepCalls = new AtomicInteger();

        /** 记录最终清理次数。 */
        private final AtomicInteger cleanupCalls = new AtomicInteger();

        private ExecutorControlledAgent(Executor executor) {
            super(executor);
            setMaxSteps(1);
        }

        @Override
        protected String step(AgentRunContext context) {
            if (!capturedContext.compareAndSet(null, context)) {
                throw new IllegalStateException(
                        "测试 Agent 不应执行多个运行上下文"
                );
            }
            stepCalls.incrementAndGet();
            return "executor-controlled";
        }

        @Override
        protected void cleanup(AgentRunContext context) {
            cleanupCalls.incrementAndGet();
        }

        /** 返回工作任务捕获的真实运行上下文。 */
        private AgentRunContext capturedContext() {
            AgentRunContext context = capturedContext.get();
            if (context == null) {
                throw new IllegalStateException(
                        "注入执行器尚未推进流式任务"
                );
            }
            return context;
        }

        private int stepCalls() {
            return stepCalls.get();
        }

        private int cleanupCalls() {
            return cleanupCalls.get();
        }
    }
}
```

- [x] **Step 2: 运行聚焦测试并保存 RED**

```powershell
./mvnw.cmd `
  "-Dtest=BaseAgentExecutorSelectionTest" `
  test 2>&1 | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-executor-selection-red.log"

$redExitCode = $LASTEXITCODE
"MAVEN_EXIT_CODE=$redExitCode" | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-executor-selection-red.log" `
  -Append

if ($redExitCode -eq 0) {
    throw "RED 测试意外通过，测试没有捕获硬编码公共池缺陷"
}
```

预期是 1 test、0 successes、1 failure、0 errors、0 skipped、退出码非 0，核心断言信息包含：

```text
流式 FutureTask 必须提交给构造器注入的执行器
expected: 1
but was: 0
```

若出现测试编译错误、超时或其他异常，不得把它登记为有效 RED；先按故障分类查明原因。

---

### Task 4: 最小 GREEN——使用注入执行器提交任务

**Files:**
- Modify: `src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java:132-135`
- Test: `src/test/java/com/yupi/yuaiagent/agent/BaseAgentExecutorSelectionTest.java`

**Interfaces:**
- Consumes: `private final Executor streamExecutor` 和提交前已绑定的 `FutureTask<Void>`。
- Produces: `runStream()` 的任务提交受注入执行器控制；其余生命周期逻辑不变。

- [x] **Step 1: 替换唯一任务提交点**

把：

```java
            // 当前仍使用公共池作为过渡；后续再替换为专用有界执行器。
            ForkJoinPool.commonPool().execute(streamTask);
```

替换为：

```java
            // 任务句柄已经绑定，具体线程和容量策略由注入执行器决定。
            streamExecutor.execute(streamTask);
```

不得修改 `AgentRunTask.create(...)` 与现有 catch/finally 顺序。提交失败仍由调用线程执行 `cancel(false) → FAILED → cleanup → termination → completeWithError`。

- [x] **Step 2: 运行聚焦 GREEN 并保存输出**

```powershell
./mvnw.cmd `
  "-Dtest=BaseAgentExecutorSelectionTest" `
  test 2>&1 | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-executor-selection-green.log"

$greenExitCode = $LASTEXITCODE
"MAVEN_EXIT_CODE=$greenExitCode" | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-executor-selection-green.log" `
  -Append

if ($greenExitCode -ne 0) {
    throw "阶段 10A 聚焦 GREEN 失败，进入系统化调试"
}
```

预期为 1 test、1 success、0 failures、0 errors、0 skipped、退出码 0。

- [x] **Step 3: 确认没有残留硬编码提交点**

```powershell
rg -n "ForkJoinPool\.commonPool\(\)\.execute" `
  src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java
```

预期无输出。`ForkJoinPool.commonPool()` 仍会出现在临时无参构造器中，这是 10A 明确保留、10B 必须删除的兼容路径。

---

### Task 5: 阶段回归与证据账本

**Files:**
- Modify: `docs/evidence/AgentRunContext-证据账本.md`
- Modify: `docs/decisions/README.md`
- Modify: `docs/decisions/2026-09-05-agent-executor-lifecycle.md`
- Create locally: `target/evidence/agent-run-context/2026-09-05-d815263/step10a-phase-regression.log`

**Interfaces:**
- Consumes: 12 项既有测试和 1 项新增执行器选择测试。
- Produces: 13 项阶段回归和记录 03；不产生性能、压力或线上收益结论。

- [x] **Step 1: 运行 13 项阶段回归**

```powershell
./mvnw.cmd `
  "-Dtest=AgentRunContextTest,BaseAgentLifecycleTest,BaseAgentFailureLifecycleTest,ReActAgentDecisionIsolationTest,BaseAgentStreamCancellationTest,BaseAgentExecutorSelectionTest" `
  test 2>&1 | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-phase-regression.log"

$regressionExitCode = $LASTEXITCODE
"MAVEN_EXIT_CODE=$regressionExitCode" | Tee-Object `
  -FilePath "target/evidence/agent-run-context/2026-09-05-d815263/step10a-phase-regression.log" `
  -Append

if ($regressionExitCode -ne 0) {
    throw "阶段 10A 回归失败，不得更新为已验证结论"
}
```

预期为 13 tests、13 successes、0 failures、0 errors、0 skipped、退出码 0。其中 13 是测试数量，不是同时运行的任务数量。

- [x] **Step 2: 从 Surefire 报告交叉核对测试数量**

```powershell
Get-ChildItem -LiteralPath "target/surefire-reports" -Filter "TEST-*.xml" |
  Where-Object {
      $_.Name -match "AgentRunContextTest|BaseAgentLifecycleTest|BaseAgentFailureLifecycleTest|ReActAgentDecisionIsolationTest|BaseAgentStreamCancellationTest|BaseAgentExecutorSelectionTest"
  } |
  ForEach-Object {
      $report = [xml](Get-Content -LiteralPath $_.FullName)
      [pscustomobject]@{
          Suite = $report.testsuite.name
          Tests = [int]$report.testsuite.tests
          Failures = [int]$report.testsuite.failures
          Errors = [int]$report.testsuite.errors
          Skipped = [int]$report.testsuite.skipped
      }
  } |
  Sort-Object Suite |
  Format-Table -AutoSize
```

预期六个测试类合计 13 项。若 Maven 汇总与 XML 不一致，以重新执行和逐份 XML 核验为准，不选择更有利的数字。

- [x] **Step 3: 向证据账本追加记录 03**

只有前述输出符合预期时，记录以下事实：

````markdown
## 记录 03：流式执行器注入接缝 RED → GREEN

### 基本信息

| 字段 | 记录值 |
| --- | --- |
| 验证日期 | 2026-09-05 |
| 起点提交 | `d815263 fix(agent): bind cancellable stream task handle` |
| 被验证代码 | 尚未提交，等待用户审阅 |
| 测试环境 | 单 JVM、Java 21、Maven Surefire、内存测试 Agent |
| 生产执行策略 | 无参构造器仍委托 `ForkJoinPool.commonPool()`；专用有界执行器尚未实施 |

### RED → GREEN 结果

| 阶段 | 测试数量 | 成功 | 失败 | 错误 | 跳过 | 超时 | 退出码 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 修改前阶段基线 | 12 | 12 | 0 | 0 | 0 | 0 | 0 |
| 构造接缝回归 | 12 | 12 | 0 | 0 | 0 | 0 | 0 |
| 执行器选择 RED | 1 | 0 | 1 | 0 | 0 | 0 | 非 0 |
| 执行器选择 GREEN | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| 最终阶段回归 | 13 | 13 | 0 | 0 | 0 | 0 | 0 |

RED 证明旧提交点绕过了构造器注入的执行器；GREEN 证明流式 FutureTask 只有在
注入的手动执行器推进后才开始执行，并最终进入 SUCCEEDED、cleanup 一次且
termination 完成。

### 当前可以证明

1. `BaseAgent.runStream()` 的任务提交由构造器注入的 `Executor` 控制；
2. 测试无需 sleep、网络、模型和工具即可确定性控制任务开始时点；
3. 替换提交入口后，既有状态隔离、异常、取消和终止相关回归未退化；
4. 本阶段总计 13 项相关单元测试通过。

### 尚不能证明

1. 生产环境仍使用公共池兼容入口，尚未形成专用资源隔离；
2. 尚未验证线程数、队列容量和拒绝策略；
3. 没有并发压力测试、重复轮次或高并发能力证据；
4. 没有吞吐量、响应时间、CPU、内存或终止延迟数据；
5. 尚未解决 Spring 单例标注与每请求手动创建的所有权冲突。

### 原始输出

原始测试输出保存在：

```text
target/evidence/agent-run-context/2026-09-05-d815263/step10a-baseline.log
target/evidence/agent-run-context/2026-09-05-d815263/step10a-constructor-seam-green.log
target/evidence/agent-run-context/2026-09-05-d815263/step10a-executor-selection-red.log
target/evidence/agent-run-context/2026-09-05-d815263/step10a-executor-selection-green.log
target/evidence/agent-run-context/2026-09-05-d815263/step10a-phase-regression.log
```
````

如果任何实际计数与上述预期不同，不复制预期表；先完成故障分类，再把真实结果写入账本。

- [x] **Step 4: 更新决策状态但不提前宣布整体完成**

把 `docs/decisions/README.md` 中该决策的状态从 `待审阅` 改为 `实施中`。把总设计文档顶部状态也改为 `实施中`，并追加一个简短的“阶段 10A 实际结果”小节，引用账本记录 03，不重复粘贴完整日志。

---

### Task 6: 完成提交前审阅包，但不操作 Git 历史

**Files:**
- Inspect: 本计划 File Map 中所有本阶段文件
- Do not stage: `target/evidence/**`

**Interfaces:**
- Consumes: 13 项通过结果、RED 原始日志、结构化账本。
- Produces: 供用户确认的差异摘要、暂存范围建议和推荐提交信息；不执行提交。

- [x] **Step 1: 检查空白、临时痕迹和硬编码提交点**

```powershell
# git diff --check 负责检查已经被 Git 跟踪的修改。
git diff --check -- `
  "src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java" `
  "docs/evidence/AgentRunContext-证据账本.md" `
  "docs/decisions/README.md"

# 新文件在暂存前不会出现在 git diff 中，因此逐行检查全部目标文件。
$stageFiles = @(
    "src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java",
    "src/test/java/com/yupi/yuaiagent/agent/BaseAgentExecutorSelectionTest.java",
    "docs/evidence/AgentRunContext-证据账本.md",
    "docs/decisions/README.md",
    "docs/decisions/2026-09-05-agent-executor-lifecycle.md",
    "docs/superpowers/plans/2026-09-05-base-agent-executor-injection.md"
)

foreach ($file in $stageFiles) {
    $lineNumber = 0
    Get-Content -LiteralPath $file | ForEach-Object {
        $lineNumber++
        if ($_ -match "[ `t]+$") {
            throw "检测到行尾空白：${file}:${lineNumber}"
        }
    }
}

rg -n "TODO|TBD|temporary mutation|ForkJoinPool\.commonPool\(\)\.execute" `
  "src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java" `
  "src/test/java/com/yupi/yuaiagent/agent/BaseAgentExecutorSelectionTest.java" `
  "docs/evidence/AgentRunContext-证据账本.md"
```

预期 `git diff --check` 和逐行检查都无错误；源码中没有临时 mutation；硬编码 `.execute` 搜索无结果。文档若合理出现“尚未测量”不属于占位错误。

- [x] **Step 2: 核对本阶段差异和未跟踪边界**

```powershell
git status --short
git diff --stat -- `
  "src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java" `
  "src/test/java/com/yupi/yuaiagent/agent/BaseAgentExecutorSelectionTest.java" `
  "docs/evidence/AgentRunContext-证据账本.md" `
  "docs/decisions/README.md" `
  "docs/decisions/2026-09-05-agent-executor-lifecycle.md" `
  "docs/superpowers/plans/2026-09-05-base-agent-executor-injection.md"
```

不得把 `.cursor/`、`.learnings/`、`prompts/`、`skills/`、其他文档或任务目录加入本阶段建议范围。

- [x] **Step 3: 向用户提交阶段审阅报告**

报告必须包含：实际修改、核心差异、RED/GREEN 因果、六个测试类共 13 项的结果、原始日志路径、Git 状态、可证明与不可证明结论、重点代码位置，以及不超过 5 个理解检查问题。

- [x] **Step 4: 提供暂存范围和推荐提交信息，等待明确授权**

建议暂存范围只能包含：

```text
src/main/java/com/yupi/yuaiagent/agent/BaseAgent.java
src/test/java/com/yupi/yuaiagent/agent/BaseAgentExecutorSelectionTest.java
docs/evidence/AgentRunContext-证据账本.md
docs/decisions/README.md
docs/decisions/2026-09-05-agent-executor-lifecycle.md
docs/superpowers/plans/2026-09-05-base-agent-executor-injection.md
```

推荐提交信息：

```text
refactor(agent): inject stream task executor

Route BaseAgent stream tasks through an injectable Executor while preserving
the common-pool compatibility path for the next lifecycle migration stage.
```

只有用户明确确认后，才可使用逐文件 `git add -- <paths>` 和 `git commit`。阶段 10A 完成前不执行中间提交。

---

## Stage 10A Evidence Boundary

成功完成本计划后，可以安全得出：

> `BaseAgent.runStream()` 已从硬编码提交点改为服从构造器注入的执行器，并通过 1 项确定性 RED → GREEN 行为测试及 13 项阶段回归验证既有运行隔离、取消与终止契约未退化。

仍禁止表述为：

- “已使用专用线程池”；
- “支持高并发”；
- “线程池吞吐量提升”；
- “消除了资源泄漏”；
- “SSE 断连均能可靠终止”；
- “生产故障率下降”。

阶段 10B 才负责删除公共池兼容构造器、统一 Agent 所有权、装配专用有界执行器并验证拒绝生命周期。
