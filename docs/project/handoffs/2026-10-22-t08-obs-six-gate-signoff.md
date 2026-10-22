# T08 → OBS-02 六项交接条件：范围限定技术签收

日期：2026-10-22
签收身份：**T08 WORKFLOW 技术审查（Codex 实施 + 项目协调方本机独立复验）**。本记录并非任何具名人类维护者的签名，也不代替真实生产变更审批。

**裁定：SCOPED_T08_OBSERVER_ABI_TECHNICALLY_SIGNED / OBS02A_SYNTHETIC_GO / REAL_RULES_WIRING_NO_GO / EVALUATOR_ADAPTER_UNVERIFIED**

## 0. 签收绑定的唯一源码身份

- T08 Observer 新接口代码提交：`9d68854139f21f0b843e1fe9d81fa311c6e5fe14`
- 对应 Git tree：`1b88caaa6be0c54da83fac88eddbab0c0a741fe9`
- 父检查点：`3cad324f036057c6ca7e8528ee5f4c2c9cf1e5d0`；其上游 T08 已验证源码：`f3414eee62025ef026d5eccc059b3aa4cd644553`
- 工作区：`D:/alldesk/Projects/ZhkuAgent-T08ObsHost`，分支 `codex/t08-observer-host-20261022`。
- 此代码 SHA 含 TaskStore 六参可选装配接口、新增 9 项契约测试以及 [宿主合同](../../decisions/2026-10-22-t08-observer-host-contract.md)。本签收文档随后单独提交，以避免在源码身份中循环记录自身 SHA。
- 测试源码对应以上代码提交。未读取 holdout、生产隐私内容、密钥和其他工作树未提交改动。

## 六门禁逐项记录

| 门禁 | 技术签收 | 源码/实测边界 |
|---|---|---|
| 1. 源码身份 | **PASS（仅本次 Observer ABI）** | 上述 commit + tree 已在本机 Git 核对，后续 OBS-02A 必须以此作为候选依赖；不把旧 T08 适配分支未提交改动视为当前成果 |
| 2. 消费接口和版本 | **PASS（只读与观察接口分离）** | T08 只读评测：`RuleAgentTaskStore.traceHistory(Owner, UUID)`，仅 Owner 授权、不可变副本；OBS 输入：`AgentRunEvent` 的异步可选观察器。原 HTTP schema v3，OBS 匿名事件 schema v1，T08 Trace 的 `version` 是快照版本。严禁复制完整 `RuleAgentRunTrace` 到匿名遥测 |
| 3. 装配所有权与生命周期 | **PASS（合同冻结，生产仍默认关闭）** | 新稳定签名见下；T08 拥有 TaskStore 每次 RunContext 的创建与任务发布；以后获批的 OBS 宿主提供匿名化 Consumer、管理自有 dispatcher 生命周期；TaskStore 不关闭 dispatcher。原四参及 `RuleAgentHttpConfiguration` 不变 |
| 4. 幂等、取消、终态 | **PASS（离线限定）** | 新增测试核对首轮、续答/重复消息、匹配 Accepted.runId、排队取消先 STOP_REQUESTED 后真正终止、观察器抛错/阻塞与关闭。202 Accepted 不是执行完成，RUN_TERMINATED 是 Context 事实，不保证 TaskStore 已发布最终快照或观察线程已退出 |
| 5. 验收及来源回执 | **PASS：本接口离线验证；PENDING：真实 Java-to-T08 评测适配器** | 已授权本机 Windows 离线 Maven：57/57 PASS、0 failures、0 errors、0 skipped、BUILD SUCCESS、exit 0（9 项新 Observer + 48 项现有）；Node `sprint02.test.mjs`：30/30 PASS，exit 0。受限 Codex 沙箱 Maven JAR `AccessDeniedException` 是另一个执行环境失败，不能替代本机结果。真实 Java-to-evaluator adapter 溯源、旧冻结数据重新绑定、在线质量判定、模型供应商/DB 均未通过 |
| 6. 安全、隐私、合并 | **PASS（限制条件接受，不授权真实接线）** | Observer 仅隔离性旁路，不参与模型或审计决策；`AUDIT_UNAVAILABLE` 保持 fail-closed（新增合成测试，未校验真实供应商）。不得记录原始 `AgentRunEvent` 的正文、工具参数、run UUID 或 T08 证据 ID/哈希。新建生产 exporter、权限/保留配置及跨分支集成需另行审批；未 push/merge/deploy |

## 冻结的 Java 方法签名

```java
// 默认入口：不创建观察资源，不启用 OBS
public RuleAgentTaskStore(Executor executor, Factory factory, Clock clock, Limits limits);

// T08 归属的可选入口：只有未来批准的宿主才可调用
public RuleAgentTaskStore(
    Executor executor, Factory factory, Clock clock, Limits limits,
    Consumer<AgentRunEvent> observer, AgentRunObservationDispatcher dispatcher);
```

- 六参入口要求 observer/dispatcher 同时非空，沿用 Runtime `AgentRunContext.create(UUID.randomUUID(), question, observer, dispatcher)`；任务 create 和 message 各自产生新 RunContext。重复 clientRequestId 不新建 Run。
- Runtime dispatcher 在独立线程、固定有界队列中投递；Callback `RuntimeException` 被隔离并计数，不必然立即 DETACH。阻塞 callback 超时可被逻辑脱离，但**脱离不等于物理线程结束**。宿主需要释放自己的 latch、等待实际线程退出；拥有 explicit dispatcher 的宿主可关闭它，不能关闭 JVM 默认实例。
- Observer 接收的**原始** Runtime event 可能带原文、工具内容和 UUID，消费者必须立即做 OBS-01 白名单匿名投影。禁止输出原始 event 或把 T08 `RuleAgentRunTrace` 直接转为匿名数据。
- T08 的 `publish` / cancel / TaskStore 锁和 Runtime 事实序列保持不变；不得让观察失败改变 mandatory provider audit。异步 RUN_TERMINATED 不能当作最终已持久化 TaskSnapshot 的证明。

## 实际测试记录

执行环境：已授权的本机 Windows PowerShell，离线 Maven，使用已有本地依赖，无网络/模型/API/DB。

```powershell
.\mvnw.cmd -o -pl knowledge -am '-Dtest=RuleAgentTaskObserverContractTest,RuleAgentTaskStoreTest,RuleAgentRunnerTest,RuleAgentRunTraceTest,RuleAgentTaskHttpTest' '-Dsurefire.failIfNoSpecifiedTests=false' ('-Dzhku.rulecheck.evidenceDir='+$tempEvidenceDir) test
node --test tools/t08-acceptance/test/sprint02.test.mjs
```

本轮先发现 57 项中的两项新测试断言失败：它们将 RuntimeException 回调的累计异常计数强行等于 1，并误认为必然触发 DETACH。Runtime 源码表明回调可能在事件序列中多次抛错但仍继续交付；仅超时或致命错误等条件才会 detach。已校正测试语义后，**完整 57/57 复跑通过**；不是通过改变业务生产代码来使测试变绿。上次 T08 技术基线的 Java 48/48、Node 30/30 也在此次新源码上分别回归通过。

## 签收范围与下一步

**本次技术签收对象仅为 T08 工作流维护的可选 Observer 宿主 ABI、所有权及上述离线行为约束。**

- **GO：** OBS-02A 在自己的独立工作区基于此签名准备纯合成适配、故障隔离/匿名化/线程验证；必要时另行评审将此提交受控整合进 OBS 分支。
- **NO-GO：** 未经独立集成评审就实际更改 `RuleAgentHttpConfiguration`、`/rules`、Runner、TaskStore 的其他业务状态机；不调用真实提供商、DB、生产 exporter，不启用受限诊断，不把 OBS-02A 当线上效果或质量评测 PASS。
- **尚未签收：** T08 真实 Java-to-evaluator 导入/控制器完整来源证明、正式在线评测候选授权、OBS/T08 合并冲突处理与生产宿主装配验收、任何具名人类维护者审批。

本文件记录 T08 WORKFLOW **范围限定的技术签收**，不可解读为所有历史 T08 项目整体完工或真实 `/rules` 的生产接线签收。
