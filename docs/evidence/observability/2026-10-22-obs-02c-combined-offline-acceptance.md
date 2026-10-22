# OBS-02C 联合离线编译与合成集成验收回执

日期：2026-10-22
状态：**OBS02C_COMBINED_SYNTHETIC_PASS / REAL_RULES_WIRING_NO_GO**
工作区：D:/alldesk/Projects/ZhkuAgent-OBS02C
分支：codex/obs02c-combined-20261022

## 来源身份

- T08 六参数接口代码：commit 9d68854139f21f0b843e1fe9d81fa311c6e5fe14，tree 1b88caaa6be0c54da83fac88eddbab0c0a741fe9。
- T08 工作树基线：commit f0cbedf6de85f9360a2ccf904b85d4655bebb096，tree ce59e1e90a85ad7f04a8535000d61a257310b469。
- OBS 来源：commit f78899e97d0c6caafbd6b5b77e49bcfc9d770cbf，tree b10c519fe8e562b4fa0337a17049392a7e873645。
- 从 OBS 来源导入的 11 个文件均逐个执行 Git Blob SHA 对比，全部与源对象相同：

| 文件 | Source Blob SHA |
|---|---|
| knowledge/src/main/java/com/zhku/knowledge/observability/AnonymousRuntimeObserver.java | 5ad79fd7d61d6a3e8ec08f757caabb3b7b0f1c2f |
| knowledge/src/main/java/com/zhku/knowledge/observability/BoundedObservationBuffer.java | a9eeea349e27e1b7b23d4065ce421346a0572e83 |
| knowledge/src/main/java/com/zhku/knowledge/observability/ObservationEventV1.java | c2401eba9918d6bba8348fd39eea0bed2b44eae4 |
| knowledge/src/main/java/com/zhku/knowledge/observability/ObservationProjector.java | 726706e6c5b050ee83f09e60b64a1a2777f11f68 |
| knowledge/src/main/java/com/zhku/knowledge/observability/ObservationSink.java | 0f2bd58b3c550c2ffc04013eeca893cce346df73 |
| knowledge/src/main/java/com/zhku/knowledge/observability/RunDiagnosticEventV1.java | 9c42ce396009797950a85946229aa5d7b431ba6c |
| knowledge/src/main/java/com/zhku/knowledge/observability/RunObservationSession.java | 854c54d957e0258465d6f82f44b21594ec9d850c |
| knowledge/src/test/java/com/zhku/knowledge/observability/AnonymousRuntimeObserverContractTest.java | edf932a16ec512c623c45b9422599e6ebf15e7fa |
| knowledge/src/test/java/com/zhku/knowledge/observability/ObservationDeliveryTest.java | b98479aa1303edac92473ea5a69e5055d37a8c4f |
| knowledge/src/test/java/com/zhku/knowledge/observability/ObservationProjectionTest.java | c2a717f6442cba1ca44533b21048f73ad435e05e |
| knowledge/src/test/resources/observability/metadata-v1-synthetic.json | 8cc99d2426c9e7d04bbe6d11a62b23de3b3b3a30 |

除上述 11 个导入文件外，本轮只新增一个联合测试文件和本回执；未修改 T08/Runtime/Runner/TaskStore/Trace/HTTP/Controller/前端/POM/provider audit 等生产代码。构建产物留在 Git 忽略的 target 目录。既有 TaskStore 测试写出的合成快照指向专用 %TEMP%/zhku-obs02c-evidence，不写入仓库。

## 主联合 Maven 测试

授权 Windows PowerShell 执行；Maven 离线模式，无真实供应商、数据库、HTTP 或学生请求。先设置临时证据目录，再执行：

    $e = Join-Path $env:TEMP 'zhku-obs02c-evidence'
    .\mvnw.cmd -o -pl knowledge -am '-Dtest=AnonymousRuntimeObserverContractTest,ObservationProjectionTest,ObservationDeliveryTest,RuleAgentTaskObserverContractTest,RuleAgentTaskStoreTest,RuleAgentRunnerTest,RuleAgentRunTraceTest,T08AnonymousObserverCombinedContractTest,AgentRunObservationDispatcherTest' '-Dsurefire.failIfNoSpecifiedTests=false' ("-Dzhku.rulecheck.evidenceDir=" + $e) test

结果：**BUILD SUCCESS，退出码 0，122 tests / 0 failures / 0 errors / 0 skipped**。

| 测试类 | Tests | Failures / Errors / Skipped |
|---|---:|---|
| AgentRunObservationDispatcherTest（Runtime） | 11 | 0 / 0 / 0 |
| AnonymousRuntimeObserverContractTest | 16 | 0 / 0 / 0 |
| ObservationDeliveryTest | 10 | 0 / 0 / 0 |
| ObservationProjectionTest | 26 | 0 / 0 / 0 |
| RuleAgentRunnerTest | 22 | 0 / 0 / 0 |
| RuleAgentRunTraceTest | 11 | 0 / 0 / 0 |
| RuleAgentTaskObserverContractTest | 9 | 0 / 0 / 0 |
| RuleAgentTaskStoreTest | 14 | 0 / 0 / 0 |
| T08AnonymousObserverCombinedContractTest | 3 | 0 / 0 / 0 |
| **总计** | **122** | **0 / 0 / 0** |

原始 Surefire XML 在 agent-runtime/target/surefire-reports/ 和 knowledge/target/surefire-reports/。

## 补充 Runtime 与 Node 验收

- Runtime 隔离测试另行执行：./mvnw.cmd -o -pl agent-runtime -am '-Dtest=AgentRunObservationIsolationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test。结果 BUILD SUCCESS，9/9，0 failures/errors/skipped。该次为独立调用，不重复计入上面的 122 项。
- Node 版本 v24.19.0；执行 node --test tools/t08-acceptance/test/sprint02.test.mjs。结果 30 pass，0 fail/cancelled/skipped，退出码 0。
- Node 通过只证明既有离线合同保持通过，不代表真实 Java-to-evaluator 来源认证完成。

## 联合测试实际覆盖

T08AnonymousObserverCombinedContractTest 通过真实六参数 RuleAgentTaskStore(..., Consumer<AgentRunEvent>, AgentRunObservationDispatcher) 构造器，注入共享 AnonymousRuntimeObserver 和显式自有 dispatcher；使用确定性合成工具与任务执行器。

1. CREATE/MESSAGE 重复请求幂等；同一 Task 的两个 Run 使用不同 runId；任务从 WAITING_USER 到 RESOLVED，T08 traceHistory 保留两条；匿名 Sink 只见 schema v1 元数据，观察到两个 RUN_STARTED 与两个 RUN_TERMINATED，drop/failure 为零。
2. Sink 抛出合成异常时，AnonymousRuntimeObserver 记录固定失败计数，不改变 TaskStore 已接受的业务结果和 Trace 落账。
3. 原始合成 ToolCall 注入私有标记、哈希、工具/action 字段及参数 JSON；匿名投影不包含这些值、runId 或原始参数字段；未知指标保持 null。
4. 测试先关闭 TaskStore，再关闭自有 dispatcher，并限时断言物理线程、registeredCount、inFlightCount、ticketCount 归零。TaskStore 不负责关闭借用 dispatcher。

## 失败尝试与修正记录

- 第一次 testCompile 因新测试错误引用 RuleAgentTaskStore.ResultEnvelope 失败；已改为 RuleAgentContract.ResultEnvelope，之后完整测试源码编译成功。
- 一次行为试验将敏感标记直接用作问题文本，与既有 CATEGORY 合成 fixture 不匹配，两个预期状态断言失败。已改用 fixture 已覆盖的“课程类别是什么？”；私有标记留在专门构造的原始 Runtime 事件和 Sink 异常中。
- 同次试验中既有 RuleAgentTaskStoreTest 因缺少 zhku.rulecheck.evidenceDir 而在写临时快照时抛出 NullPointerException。未修改或跳过该测试；最终命令将属性指向专用 %TEMP% 目录，TaskStore 测试 14/14 通过。
- 上述失败均被如实保留，不计入通过数量；最终成功结果来自更正后的真实运行。

## 测试文件布局说明

按 OBS-02B 约定，测试文件放在 knowledge/src/test/java/com/zhku/knowledge/observability/T08AnonymousObserverCombinedContractTest.java，但 Java package 声明为 com.zhku.knowledge.ruleagent，以复用包私有的现有合成 fixture，避免修改 T08 测试源码。Maven 与 Surefire 已实际编译、发现并运行该类。这是测试布局的临时维护性折衷，不是生产包/API 变更；提升到长期主干前应在单独评审中调整路径白名单或共享 fixture，消除 package/path 不一致。

## 裁定与限制

**GO：OBS02C_COMBINED_SYNTHETIC_PASS。** 可进入下一轮真实 /rules 接线安全评审，使用本回执与 Surefire/Node 原始结果作为输入证据。

**NO-GO：真实 /rules 接线、生产启用、部署、push、PR、合并。** 本轮没有真实 HTTP/学生请求、Spring 六参 Bean 装配、provider audit 写入链、线上 Trace 或真实 Java-to-evaluator 来源认证证据。匿名聚合事件不能解释为可关联的逐请求 Trace，T08 RuleAgentRunTrace 不得输入匿名 Sink。T08 默认四参数 HTTP 装配保持原样。

回滚只针对本分支本轮新增的 13 个文件；T08 冻结基线及原 OBS 工作区均保持不变。
