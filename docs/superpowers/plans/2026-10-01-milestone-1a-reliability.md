# Milestone 1A 可靠性修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本计划由当前 Agent 顺序实施；不创建子 Agent，不自动提交或推送。

**Goal:** 修复校园运行记录永久满额、错误成功终态和收尾发布竞态，并强制单人本机预览的网络绑定边界。

**Architecture:** 保留两条 Agent 主循环、共享 AgentRunContext 和既有校园证据链。用有界内存记录、明确结束结果及小范围的错误分类修复当前路径；不建设统一编排框架。三个补丁依次为快照与收尾、终态与错误、本机启动限制。

**Tech Stack:** 当前仓库 Java 21、Spring Boot、既有 Spring AI 依赖、JUnit/AssertJ/MockMvc；不增加依赖或数据库。

**Spec:** 本文件“冻结设计”汇总用户确认的 Milestone 1A 范围及随后修订。原方案附件：`C:/Users/nangua/.codex/attachments/c07b8d64-32c7-4605-a90d-3ec79924a483/已粘贴的文本.txt`；历史事实依据：`docs/evidence/2026-09-28-campus-agent-v1-验收记录.md`、`docs/evidence/2026-09-28-campus-agent-temp-db-v2-验收记录.md`。实施者读取本文件后不依赖原聊天才能执行。

## Global Constraints

- 不删除 agent/ 或 knowledge/，不重写两个 Agent 主循环。
- 不引入多 Agent、长期记忆、RAG、MCP、新数据库、新认证系统或大量抽象接口。
- 不修改校园政策证据链、审核资料、数据库迁移、前端视觉和临时数据库验收核心行为。
- 已有成功校园任务的 HTTP 状态、JSON 字段及字段意义保持不变；非成功结果新增 INCOMPLETE，必须记录兼容性变化。
- 当前阶段仅支持单人本机预览；身份、资源归属及非本机多用户权限验收属于 Milestone 1B，完成前不开放多人试用。
- 修改前重新运行相关测试并保存基线；测试替身结果不能标作真实模型验证。
- 遇到需要越过文件范围或修改证据链的解决方案时，先报告并停止依赖该变更的工作。
- 按 AGENTS.md 在状态约束、锁边界、结果发布、工具失败和网络暴露边界添加简明中文注释。
- 每次数据库或 DashScope 操作前重新检查进程、Windows 用户级和系统级凭证；不打印值。数据库写入只允许专用临时测试库。
- 回滚仅处理本阶段差异，不覆盖原有 .learnings/、career/ 和其他未跟踪文件。

## Review Focus

1. 取消与成功提交同时发生：原子提交结果，先获胜的结果不被错误改写；停止请求先获胜时不得 SUCCEEDED。Task 1、2 使用可控屏障覆盖。
2. 清理阻塞或异常：开始清理不等于结束；异常收尾不得保留成功、不得永久丢失终止信号。Task 1 覆盖。
3. 快速完成、调度拒绝与查询重叠：terminated=true 的快照必须包含完整结果或明确的调度失败类别。Task 1 覆盖。
4. 终止工具与失败工具同批出现：任何失败不得因 doTerminate 响应而变为成功。Task 2 覆盖。
5. 地址被命令参数或外部配置覆盖：仅在启用预览入口时检查有效配置，拒绝任意网卡绑定；不得把 loopback 当成身份隔离。Task 3 覆盖。

## 冻结设计

### 运行状态与结束原因

`AgentRunState` 增加 `INCOMPLETE`。它表示本次执行结束但用户请求尚未完成，不是可恢复的等待状态。用户补充信息仍需按既有接口发起请求，本阶段不增加续跑能力。

| 结果 | state | 校园现有字段 / 通用路径 |
| --- | --- | --- |
| 校园答案通过政策和计算守卫 | SUCCEEDED | ANSWERED / NONE |
| 通用模型明确声明完成 | SUCCEEDED | 有效 doTerminate(COMPLETED)；仅证明模型声明，不证明答案质量 |
| 缺输入 / 证据不足 | INCOMPLETE | NEED_CLARIFICATION / HANDOFF + INSUFFICIENT_EVIDENCE |
| 步骤上限耗尽 / 无法继续 | INCOMPLETE | STEP_LIMIT / doTerminate(BLOCKED) |
| 模型异常或无有效输出 | FAILED | MODEL_FAILURE |
| 工具异常 / 无效工具调用 | FAILED | TOOL_FAILURE / 已有 INVALID_TOOL_ARGUMENT、UNKNOWN_TOOL 等细分类 |
| 查询失败 | FAILED | 保留 RETRIEVAL_FAILURE |
| 执行器或定时器拒绝 | FAILED | EXECUTOR_REJECTED |
| 用户取消 / 断连 | CANCELLED | USER_REQUEST / CLIENT_DISCONNECTED |
| 整体运行或 SSE 超时 | CANCELLED | RUN_TIMEOUT / SSE_TIMEOUT |
| 清理异常 | FAILED | CLEANUP_FAILURE；同时保留最早 stopReason 和原失败的诊断类别 |

超时与用户取消保持同一个 CANCELLED 状态，通过 stopReason 区分，避免增加 TIMED_OUT 等更多枚举。停止尚未被工作逻辑响应时仍是 STOP_REQUESTED；不保证忽略中断的供应商在截止时间立即退出。

`state.isTerminal()` 表示业务循环不再运行；`terminated` 表示业务与清理收尾结束，且 Manager 结果已发布。它不表示线程池线程被销毁，也不保证清理成功：清理失败应以 FAILED 和分类表达。普通结束结果在收尾前不可被另一普通结果覆盖；仅清理异常可在 termination 发布前将已有结果降级为 FAILED，发布后状态冻结。

`finish(SUCCEEDED/INCOMPLETE)` 与 requestStop 竞争时，若已经 STOP_REQUESTED，返回未提交结果，由调用方按 stopReason 收尾，不把竞争抛成 MODEL_FAILURE。CREATED 直接成功、非终态传入 finish 等真正非法迁移仍应拒绝。

### 记录保留与发布

- 总记录容量保持 128，包含已接纳、排队、运行、停止中和已完成记录。
- 只有结果已发布且 context 的 termination 已完成的记录可以清理。
- 完成后 TTL 为 10 分钟，从完成时间计算，查询不续期。
- start、find、cancel 时执行惰性到期清理；容量满时淘汰最早完成的可回收记录。
- 10 分钟是最长正常保留时间，容量压力会提前淘汰；不保证最短保留期。
- 容量检查、淘汰和新记录插入处于同一个短临界区；模型、工具、worker.execute 和 timer.schedule 都在锁外。
- 记录全部未完成时拒绝接纳，仍是现有繁忙行为；不能移除活动或 STOP_REQUESTED 记录。
- 已清理记录的 find/cancel 返回现有 404 RUN_NOT_FOUND，不新增 410 或永久墓碑。
- 不新增清理线程；无请求时可能暂不移除过期记录，但总量仍有界。

建议 Manager 保留原公开构造器，并增加包内构造器注入容量、TTL 和 Clock，便于小容量、可控时间测试：

```java
CampusCreditAgentManager(CampusCreditAgentService service, Executor worker,
        ScheduledExecutorService timer, Duration timeout,
        int capacity, Duration retention, Clock clock)
```

原构造器委托至 128、Duration.ofMinutes(10)、Clock.systemUTC()。完成数据用一次 volatile 发布的不可变 `PublishedCompletion` 保存结果、结束时间、调度拒绝信息，避免多个标志分别发布产生不一致快照。Manager 的 terminated 由已发布完成数据和 context.termination 共同决定，不使用 Future.isDone 判断，因为 cancel(true) 不代表业务退出。

### 工具与错误契约

保持工具名 doTerminate，改为必需参数 `String outcome`，只接受精确值 COMPLETED 或 BLOCKED，返回固定 JSON：

```java
// 计划中的签名与结果；业务代码尚未修改。
public String doTerminate(String outcome)
// COMPLETED -> {"outcome":"COMPLETED"}
// BLOCKED   -> {"outcome":"BLOCKED"}
```

ToolCallAgent 校验终止响应、无效参数和批次中所有失败后才提交状态。缺参数、未知值、非约定结果不能成功；DefaultAgent 的提示使用实际工具名 doTerminate，解释两个结果。该 schema 变化需由可控 ChatModel 验证，真实模型工具选择仍未验证，不启用兼容回退把无参数调用当成功。

新增 agent 内部的小型 `AgentExecutionFailure`，只含固定 Category（MODEL_FAILURE、TOOL_FAILURE）及安全信息，不包含供应商原始文本；不是共享工具抽象。ToolCallAgent 在模型调用/输出解析和工具执行各自边界转换异常。BaseAgent 在失败出口记录 runId 与类别，SSE 失败发送一个现有字符串格式的固定失败提示后结束；发送本身失败时走现有 SEND_FAILURE 路径，不递归发送。成功 SSE 输出格式不变。

WebSearchTool 明确纳入范围：异常抛受控工具失败，禁止将原始异常文字作为成功字符串。增加包内 transport 构造器，用现成 BiFunction 注入测试响应，不新增网络客户端接口。0—4 条结果按实际长度读取，空数组是合法空结果；缺失或类型错误的结果结构、网络异常为工具失败。

校园 service 按局部边界捕获模型、查询、计算工具异常，保留既有细分类，禁止所有 RuntimeException 都冒充 MODEL_FAILURE。不改 policyGateway 查询与裁决规则。

### HTTP 与本机边界

成功路径仍为 POST 202 + 原 Snapshot，GET 200 + 原 Snapshot。保留字段 state、stopReason、terminated、status、answer、failureCategory、steps、source。INCOMPLETE 是非成功结果枚举的新增值，应在 README 告知；不增加成功响应字段。

POST 的 503 只表示没有接纳执行（容量/调度拒绝）；已接纳而快速执行失败也返回 202，由 Snapshot 表达 FAILED，避免使用同步/异步执行器造成不同 HTTP 状态。调度拒绝既有 Snapshot 结构可保留，由 EXECUTOR_REJECTED 判定，而非只看 state==FAILED。

预览启用时检查最终解析的 server.address，只允许 127.0.0.1、::1 或等价完整 IPv6 loopback 字面值；不通过 DNS 判断 localhost。空值、0.0.0.0、::、局域网地址均拒绝启动。关闭预览的普通配置装配不受影响；现有启动脚本已指定 127.0.0.1，无需修改。

此检查只防误开放网络。反向代理和本机其他用户/进程不属于本阶段可隔离身份；禁止将该结果宣传为权限验证。Milestone 1B 负责用户身份、run/session 归属、管理员权限及非本机测试，1B 前不开放多人服务。

## 文件边界

以下路径相对仓库根目录 `D:/alldesk/Projects/ZhkuAgent`。

### Patch A：快照容量与收尾

- 修改 `agent-runtime/src/main/java/com/zhku/agent/agent/runtime/AgentRunContext.java`：清理开始/结束/失败与停止竞争。
- 修改 `agent-runtime/src/test/java/com/zhku/agent/agent/runtime/AgentRunContextTest.java`：清理和竞争行为。
- 修改 `agent/src/main/java/com/zhku/agent/agent/BaseAgent.java`：清理异常收尾与终止信号兜底。
- 修改 `knowledge/src/main/java/com/zhku/knowledge/campusagent/CampusCreditAgentManager.java`：锁、保留与一次完成发布。
- 修改 `knowledge/src/main/java/com/zhku/knowledge/campusagent/CampusCreditAgentService.java`：保证收尾异常不逃逸为错误类别。
- 修改 `knowledge/src/test/java/com/zhku/knowledge/campusagent/CampusCreditAgentManagerTest.java` 和 `knowledge/src/test/java/com/zhku/knowledge/web/CampusAgentPreviewControllerTest.java`：容量、发布、清理后 404。
- 新增 `agent/src/test/java/com/zhku/agent/agent/BaseAgentCleanupLifecycleTest.java`。

### Patch B：终态与错误

- 修改上述 Context、BaseAgent、校园 Service 及相关测试。
- 修改 `agent-runtime/src/main/java/com/zhku/agent/agent/runtime/AgentRunState.java`。
- 修改 `agent/src/main/java/com/zhku/agent/agent/ToolCallAgent.java`、`DefaultAgent.java`、`agent/src/main/java/com/zhku/agent/tools/TerminateTool.java`、`WebSearchTool.java`。
- 新增 `agent/src/main/java/com/zhku/agent/agent/model/AgentExecutionFailure.java`。
- 新增 `agent/src/test/java/com/zhku/agent/agent/BaseAgentStepLimitTest.java`、`ToolCallAgentOutcomeTest.java`、`agent/src/test/java/com/zhku/agent/tools/TerminateToolContractTest.java`、`WebSearchToolContractTest.java`。
- 修改 `knowledge/src/main/java/com/zhku/knowledge/web/CampusAgentPreviewController.java` 及现有 ControllerTest：HTTP 接纳语义。
- 修改 `knowledge/src/test/java/com/zhku/knowledge/campusagent/CampusCreditAgentServiceTest.java`。
- 调整 `agent/src/test/java/com/zhku/agent/agent/BaseAgentExecutorSelectionTest.java`、`BaseAgentExecutorRejectionTest.java`、`BaseAgentExecutorTerminationTest.java` 的成功假 Agent，使其明确提交成功；不机械更改预期以掩盖回归。

### Patch C：本机暴露限制与交付

- 修改 `knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfiguration.java`：预览地址守卫 Bean。
- 新增 `knowledge/src/main/java/com/zhku/knowledge/web/LocalPreviewBindingGuard.java` 及 `knowledge/src/test/java/com/zhku/knowledge/web/LocalPreviewBindingGuardTest.java`。
- 修改 `knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewApplicationTest.java`：在启用预览的装配测试中明确设置绑定地址，验证不可绕过守卫。
- 更新 `agent/README.md`、`knowledge/README.md`，新增 `docs/evidence/2026-10-01-milestone-1a-验收记录.md`：实际日期若改变，以交付当天日期命名。
- 不预先增加其他文件；需要增加时先说明原因和范围。

## Task 0：新基线与记录

**输入：** 当前源码、历史基线和已有未跟踪文件。**输出：** 变更前测试结果和版本信息。

- [ ] 记录 git HEAD、git status，确认任务不覆盖用户改动。
- [ ] 将下面两个基线命令的标准输出写入仓库的测试产物目录或临时目录，汇总到验收记录；不复制 Surefire XML 中可能包含的环境属性。

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/nangua/.m2/repository' -pl agent,knowledge -am '-Dtest=AgentRunContextTest,BaseAgent*Test,CampusCreditAgentManagerTest,CampusCreditAgentServiceTest,CampusAgentPreviewControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/nangua/.m2/repository' -pl agent,knowledge -am '-Dtest=DefaultAgentLifecycleOwnershipTest,ReActAgentDecisionIsolationTest,ZhkuLocalPreviewApplicationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- [ ] 读取仅选定测试的运行数、失败、错误、跳过、耗时。2026-09-30 历史为 35+8=43 项通过，41.045+40.453 秒；本轮计划整理没有重跑，不能写成当前通过。
- [ ] 新基线有失败时先判断既有失败还是环境问题；不通过改动预期消除失败。确属超出范围的问题时报告。

## Task 1：Patch A，快照与收尾

**输入：** 原公开 Manager 构造器、run/start/find/cancel 接口。**输出：** 同格式 Snapshot、有界记录与明确完成发布。

- [ ] 在现有 ManagerTest 加入连续 256 个同步完成请求的用例，保留最后一个可查，最早被容量淘汰的 ID 查询失败。模型只返回无工具响应，课程为空，得到追问；本用例验证生命周期，不依赖业务答案。

```java
UUID first = null;
UUID last = null;
for (int i = 0; i < 256; i++) {
    var snapshot = manager.start(new CampusCreditAgentService.Request("公选课学分", List.of()));
    if (first == null) first = snapshot.runId();
    last = snapshot.runId();
    assertThat(snapshot.terminated()).isTrue();
}
assertThat(manager.find(last).terminated()).isTrue();
UUID evicted = first;
assertThatThrownBy(() -> manager.find(evicted)).isInstanceOf(IllegalArgumentException.class);
```

- [ ] 增加小容量=2、手动排队 Executor 的并发接纳测试：三次 start 只有两次接纳；排队任务未完成时不得淘汰。用屏障形成竞争，不靠长时间 sleep。
- [ ] 用 Clock.fixed 的初始时间及可推进 Clock 验证 TTL：9 分 59 秒仍可查询、10 分钟时 404；查询不刷新时间。容量压力提前淘汰另做独立断言。
- [ ] 复用现有阻塞模型测试：取消后 STOP_REQUESTED/terminated=false；新增若干已完成请求施加容量压力，阻塞记录仍可查询/取消；释放后才可回收。
- [ ] 在 ContextTest 用 CountDownLatch 阻塞 cleanupAction，断言 signalTermination 在清理未结束时被拒绝；释放后可成功发信号。

```java
assertThat(context.cleanupOnce(() -> { })).isTrue();
assertThat(context.cleanupOnce(() -> { throw new AssertionError("不能重复清理"); })).isFalse();
assertThat(context.signalTermination()).isTrue();
```

- [ ] 新增 cleanupAction 抛异常测试：只执行一次，状态降级 FAILED，终止在异常收尾完成后发布；已发布终止的状态不能再修改。BaseAgentCleanupLifecycleTest 验证运行边界也能结束，不只是 Context 直接调用。
- [ ] 先运行聚焦测试确认新增行为失败，再实现最小修改：Context 增加 cleanupFinished/cleanupFailure，cleanupOnce 的结束标志写入 finally；收尾调用方在捕获清理失败后仍发终止信号，不能把错误当成功。
- [ ] Manager 用小锁串行清理与插入，锁外调度；publish 完成信息在 service 返回且定时器撤销收尾后进行。拒绝路径也发布明确的完成记录，不留下 result=null 却 terminated=true 的普通成功快照。
- [ ] ControllerTest 补淘汰 ID 的 GET/cancel 均为 404 RUN_NOT_FOUND；保持 JSON 字段不变。
- [ ] 执行以下聚焦回归，审查锁范围与状态发布顺序，保存 Patch A 差异。可独立回退，不自动提交。

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/nangua/.m2/repository' -pl agent,knowledge -am '-Dtest=AgentRunContextTest,BaseAgent*Test,CampusCreditAgentManagerTest,CampusAgentPreviewControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## Task 2：Patch B，终态与失败分类

**输入：** Patch A 的完整收尾与发布保障。**输出：** INCOMPLETE 终态、有效结束工具及分类失败；成功 HTTP 格式保留。

- [ ] BaseAgentStepLimitTest 使用一个只返回步骤文本、从不 finish 的假 Agent，设置 maxSteps=2；实际运行并在 cleanup 观察 Context。

```java
assertThat(result).contains("Reached max steps (2)");
assertThat(captured.state()).isEqualTo(AgentRunState.INCOMPLETE);
assertThat(captured.currentStep()).isEqualTo(2);
assertThat(captured.termination().join()).isEqualTo(AgentRunState.INCOMPLETE);
```

- [ ] 新增显式完成与停止竞争：最后一步明确成功不被上限覆盖；requestStop 先提交则不能 SUCCEEDED；成功先提交后 cancel 不改已结束结果。Context 先验证 CAS 规则，再由真实执行边界验证收尾。
- [ ] 新增校园请求断言：正常 ANSWERED/SUCCEEDED；缺输入 INCOMPLETE；无合格证据 HANDOFF/INCOMPLETE；模型空输出和异常 FAILED/MODEL_FAILURE；查询异常保持 RETRIEVAL_FAILURE。
- [ ] 注入同名 summarize_course_credits 的失败 CampusTool（复用真实 descriptor，仅 execute 抛异常或返回 DATA_UNAVAILABLE），确认 FAILED/TOOL_FAILURE，无答案合计，无原始异常内容。非法参数仍保持 INVALID_TOOL_ARGUMENT。
- [ ] 先运行用例确认失败，再增加 INCOMPLETE、更新 isTerminal 与 finish 转换；把 BaseAgent 上限退出映射 INCOMPLETE，服务 status 映射按冻结设计执行。
- [ ] 校园现有受控路径通常两次模型选择就完成；重复和未知调用会提前失败，所以 STEP_LIMIT 防御分支不应靠修改白名单、降低 maxSteps 合法范围或重写循环制造覆盖。验收记录说明可达性限制；通用真实循环证明步数耗尽行为。
- [ ] TerminateToolContractTest 验证下面契约；ToolCallAgentOutcomeTest 用可控 ChatModel 发出实际工具调用，验证宿主执行后的状态，不能只测试 Prompt 字符串。

```java
var tool = new TerminateTool();
assertThat(tool.doTerminate("COMPLETED")).isEqualTo("{\"outcome\":\"COMPLETED\"}");
assertThat(tool.doTerminate("BLOCKED")).isEqualTo("{\"outcome\":\"BLOCKED\"}");
assertThatThrownBy(() -> tool.doTerminate("unknown")).isInstanceOf(IllegalArgumentException.class);
```

- [ ] 同批失败工具和 COMPLETED 终止调用不得成功；无参数/未知 outcome/伪造响应不得成功。非完成的无工具输出不因循环自然结束而自动成功；本阶段不新增直接回答完成推断。
- [ ] AgentExecutionFailure 与 ToolCallAgent 按调用边界分类，BaseAgent 失败出口发固定类别。WebSearchToolContractTest 用包内 BiFunction transport 构造器返回 0/1/4/5/6 条 organic_results、错误 JSON 或抛异常，确保短结果不误报故障，失败不包含 key、URL 或供应商异常原文。现有 WebSearchToolTest 依赖网络，不纳入快速回归。
- [ ] ControllerTest 使用 Runnable::run 形成快速失败，确认已接纳任务返回 202/FAILED；拒绝为 503/EXECUTOR_REJECTED。用排队执行器验证同一种业务失败不会因调度时序改变接纳状态。
- [ ] 修正旧测试假 Agent：若该测试旨在证明成功收尾，step 必须显式 context.finish(SUCCEEDED)；若旨在证明步数耗尽，保留假 Agent 无完成信号并断言 INCOMPLETE。保留原隔离、执行器、清理断言。
- [ ] 执行聚焦回归，保存 Patch B 差异并记录新增枚举和工具参数的兼容性变化。

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/nangua/.m2/repository' -pl agent,knowledge -am '-Dtest=AgentRunContextTest,BaseAgent*Test,ToolCallAgentOutcomeTest,TerminateToolContractTest,WebSearchToolContractTest,DefaultAgentLifecycleOwnershipTest,ReActAgentDecisionIsolationTest,CampusCreditAgentServiceTest,CampusCreditAgentManagerTest,CampusAgentPreviewControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## Task 3：Patch C，本机绑定与交付

**输入：** 预览有效配置。**输出：** 本机启动守卫与明确能力边界，不新增身份认证。

- [ ] 定义小型守卫类签名 `LocalPreviewBindingGuard(String configuredAddress)`，构造时检查固定字面值；不解析 DNS、不连接网络、不读取数据库。
- [ ] 新增参数化纯测试：127.0.0.1、::1、0:0:0:0:0:0:0:1 通过；null、空串、localhost、0.0.0.0、::、192.168.1.10 拒绝。不把所有 127.* 地址自动放宽为允许。

```java
assertThatCode(() -> new LocalPreviewBindingGuard("127.0.0.1")).doesNotThrowAnyException();
assertThatThrownBy(() -> new LocalPreviewBindingGuard("0.0.0.0"))
        .isInstanceOf(IllegalArgumentException.class);
```

- [ ] 在现有 ApplicationContextRunner 测试中加启用预览且 server.address=0.0.0.0 的启动失败用例；有效 loopback 配置成功；关闭预览不装配守卫。测试不启动 HTTP 服务或连接数据库。
- [ ] 先运行确认新增测试失败，再在 ZhkuLocalPreviewConfiguration 注册守卫 Bean，使用 Environment 的最终 server.address 值。不能只检查 YAML 文本。
- [ ] README 记录容量/TTL/404、INCOMPLETE、结束工具参数、HTTP 接纳语义，以及 loopback 不是多用户权限。明确 1B 的验收门槛。
- [ ] 执行最终快速回归；读取选定 Surefire 报告记录实际数量，不假定新增测试后仍为43。

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/nangua/.m2/repository' -pl agent,knowledge -am '-Dtest=AgentRunContextTest,BaseAgent*Test,ToolCallAgentOutcomeTest,TerminateToolContractTest,WebSearchToolContractTest,DefaultAgentLifecycleOwnershipTest,ReActAgentDecisionIsolationTest,CampusCreditAgentServiceTest,CampusCreditAgentManagerTest,CampusAgentPreviewControllerTest,ZhkuLocalPreviewApplicationTest,LocalPreviewBindingGuardTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- [ ] 有可用 Docker 和已缓存镜像时运行原 `knowledge/scripts/verify-campus-temp-db.ps1`，不使用 -RunLiveProvider、不更改脚本和审核资料，仅专用临时库。不可用时记录阻塞原因，不声称真库回归通过；纯合同测试不替代原真库记录。
- [ ] 写验收记录：HEAD、实际改动文件、基线与回归命令、通过/失败/跳过、耗时、容量压力和竞态结果、真库是否重跑、真实模型未调用、剩余边界。
- [ ] 执行 git diff --check、检查仅有计划内改动并做一次全补丁自审。关键生命周期错误或证据链回归必须修复后再交付，禁止调整政策断言制造通过。

## 完成标准、停止条件与回滚

### 完成标准

1. 连续256个已完成运行能继续接纳；记录总容量受约束，活动与停止中任务不会被回收。
2. 清理和结果发布竞态测试通过；terminated=true 时能读到稳定结果或明确失败；清理异常不冒充成功。
3. 上限耗尽、BLOCKED、缺输入与证据不足为 INCOMPLETE；已校验校园答案仍为 SUCCEEDED。
4. 取消/超时通过 stopReason 区分，模型/工具/查询失败通过固定类别区分；不承诺硬停止供应商。
5. 预览误绑非 loopback 时拒绝启动，身份隔离未实现的限制明确。
6. 相关快速测试全部通过；成功校园 HTTP 结构、政策结论及引用不变；真库与真实模型证据分别记录。

### 停止条件

- 为修复失败必须改政策证据、审核资料、迁移、前端协议或新增数据库/身份系统。
- 新增 INCOMPLETE 使未纳入计划的严格客户端无法工作，需要额外协议迁移。
- 运行收尾需要替换两条主循环或引入新的后台任务框架。
- 真实供应商验证要求外发未获准的具体资料。

遇到上述情况只停依赖该变更的工作，报告具体文件、行为冲突和最小备选方案；不扩大补丁。

### 回滚

- 每个 Patch 保存独立 diff 或独立提交（仅在用户要求提交时提交），方便逐补丁回退。
- 全部回滚按 C、B、A 的逆序，只撤销本任务差异，保留已有用户改动和未跟踪目录；禁止 git reset --hard 或广泛 git clean。
- 不涉及数据库迁移回滚；内存快照在进程重启后消失，1A 不提供恢复功能。
- 回滚后重新运行 Task 0 命令，确认回到基线行为，并如实记录已知旧问题重新出现。

## 计划状态

本文件于2026-10-01完成修订整理，作为实施前冻结计划保留。2026-10-01—02 已由当前 Agent 顺序实施，没有创建子 Agent、提交或推送。最新相关快速回归 127 项通过；具体完成项、实施决策、真实数据库/供应商未验证项与回滚方式见 [2026-10-02 验收记录](../../evidence/2026-10-02-milestone-1a-验收记录.md)。上方原始步骤保持规划记录，执行结果以验收文档为准。
