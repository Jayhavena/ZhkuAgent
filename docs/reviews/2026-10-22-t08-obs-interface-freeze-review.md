# T08 → OBS-02 接口冻结独立复核（技术裁定）

日期：2026-10-22
状态：**READ_ONLY_TRACE_ABI_TECHNICALLY_VERIFIED / OBS_HOST_OBSERVER_INTERFACE_NOT_FROZEN / OWNER_SIGNOFF_PENDING / REAL_RULES_WIRING_NO_GO**

本文件是根据用户要求进行的 **T08 技术冻结复核与负责人待签收清单**，不是伪造的 T08 负责人签名，也不批准生产接线、在线评测或合并。独立 Codex 审查会话已检查已提交源码但未完成自身文档交付，以下结论由项目协调方基于已提交源码、历史交接单和本次直接执行的离线测试整理。不能将审查过程等同于负责人正式接受所有变更。

## 来源身份和测试证据

- 检查来源：`D:/alldesk/Projects/ZhkuAgent-DSH-Review`，分支 `dsh/s02-t08-offline`，当次 HEAD `f3414eee62025ef026d5eccc059b3aa4cd644553`，tree `d0c0362b995a2b0f0d09b39664e4a55b7dcb8b7d`，工作区当时干净。
- 复核执行：隔离工作树 `D:/alldesk/Projects/ZhkuAgent-T08FreezeReview`，基于完全相同的 SHA/tree 创建；未改已有产品源码、T08 适配器或 Control。
- 独立 Java 测试：`mvnw.cmd -o -pl knowledge -am -Dtest=RuleAgentRunTraceTest,RuleAgentTaskStoreTest,RuleAgentRunnerTest,RuleAgentTaskHttpTest -Dsurefire.failIfNoSpecifiedTests=false -Dzhku.rulecheck.evidenceDir=<临时目录> test`；实际退出码 0，**48/48 PASS、0 fail、0 error、0 skipped，BUILD SUCCESS**。临时证据目录仅用于本轮离线测试。
- 独立 Node 测试：`node --test tools/t08-acceptance/test/sprint02.test.mjs`；实际退出码 0，**30/30 PASS、0 fail、0 skipped**。这是本轮选定测试，不等于历史回执中 Node 58 项整套回归的再次执行。
- 历史交接：`D:/alldesk/Projects/ZhkuAgent-Control/docs/project/handoffs/T08-2A-IMPLEMENT-local-acceptance-20261021.json` 标记以上源码提交为 `DONE_LOCAL_VERIFIED`，但 `evaluationCandidateAuthorized=false` 且声明 Java-to-T08 adapter 及其新源码候选绑定仍未完成。
- 本次无线上请求、供应商调用、数据库、部署、push、合并或读取 holdout。未对 OBS+T08 合并后的干净源码进行测试，且尚未得到拥有 T08 所有权的维护者签字或可引用的同意回执。

## 六项冻结条件逐一裁定

| 项 | 技术检查结果 | 冻结裁定 |
|---|---|---|
| 1. 源码 commit/tree | clean 的 `f3414ee...` 与 `d0c036...` 完整明确；独立重跑测试与这份源码绑定 | **已验证，可作为只读接口的精确引用身份** |
| 2. 公开消费接口 | `RuleAgentTaskStore.traceHistory(Owner, UUID)` 是 Owner 约束的不可变轨迹副本；`RuleAgentRunTrace.project(TaskSnapshot, UUID, List<RuleAgentRunner.Event>, boolean)`、`executionActions()`、`executionObservations()` 是 T08 离线评测视图。Result/Action/Observation 已白名单投影；每次最多 24 Action、每 Task 最近 4 个 Run。Task HTTP `RuleAgentContract.SCHEMA_VERSION=3`，OBS 匿名事件 `ObservationEventV1.schemaVersion=1`，Trace `version` 是业务快照版本，不得混淆 | **现有 T08 离线只读评测 ABI 已按固定源码技术验收；正式对外冻结仍需负责人签收** |
| 3. Observer 装配所有权 | 原生 Runtime 支持 `AgentRunContext.create(UUID,String,Consumer<AgentRunEvent>,AgentRunObservationDispatcher)`；但已提交的 T08 `RuleAgentTaskStore.Slot` 及其续答使用不带 observer 的 `AgentRunContext.create(question)`。没有单一获批的宿主注入接口、管理者或扩展路径 | **未冻结，禁止 OBS 直接修改 TaskStore/Runner** |
| 4. 取消与终态时序 | 源码与定向测试区分 HTTP 202 受理、STOP_REQUESTED 和真实 terminated；TaskStore 的 publish 检查终态/版本及晚到结果，并在 publish 锁内保存 T08 Trace | **已有行为具备离线测试依据，但旁路 observer 失败、排队取消、重复受理和关闭时的端到端接线不变量尚未联合验收** |
| 5. 验收与来源链 | 本轮 Java 48、Node 30 PASS；历史 Node 58 PASS 的源码检查点可引用 | **只覆盖离线工程测试；真实 Java-to-T08 adapter 来源认证、最终控制器回执、在线质量评测仍不具备冻结证明** |
| 6. 安全边界及合并 | `RuleAgentRunTrace` 含 task/turn/run UUID、actionId、source/chunk 和 factId 哈希，非匿名数据；OBS-01 只允许 `AgentRunEvent -> ObservationProjector.anonymous -> ObservationEventV1`。强制供应商审计 `AUDIT_UNAVAILABLE` 必须维持 fail-closed。OBS-01 源码与 T08 开发进度不一致 | **不可直接把 Trace 输入匿名 Sink；未获精确允许修改文件、两树集成方案、资源生命周期与访问/保留策略批准** |

## 正式签收所需 T08 负责人回复

T08 负责人需要对以下内容作出可记录的独立回复，才能把整体状态从 `OWNER_SIGNOFF_PENDING` 改为 `FROZEN`：

1. 认领以上 commit/tree 和唯一消费接口版本；如果最新 T08 真实来源不是该 SHA，则提供新的 clean SHA/tree 和回归证明。
2. 确认 `traceHistory(Owner, UUID)` 仅作为受限离线评测入口，`AgentRunEvent` observer 为独立匿名观测输入，不允许复制 T08 Trace 的关联标识。
3. 确认由谁拥有 RuleAgentTaskStore 的 **可选 observer 注入点**、具体稳定方法签名、默认关闭与关闭/超时后的资源责任，以及精确允许修改文件。
4. 确认幂等 create/message/cancel、取消受理/实际终止、重复事件、终态发布、超时/异常、observer 投递失败及 TaskStore.close 的严格时序不变量。
5. 接受上述测试身份或补充最新离线/适配器来源回执；明确不授予在线模型质量 PASS。
6. 接受不改变 mandatory audit fail-closed、不污染匿名遥测、不新建受限数据保留/访问入口，以及受控合并/回滚约束。

## 下一步界限

- **GO：** OBS-02A 可在独立 OBS 目录实现纯 `AgentRunEvent` 适配器，使用合成 RunContext + noop/bounded sink、完全离线契约测试；不修改 T08 业务入口。
- **NO-GO：** 真实学生 `/rules` Runtime/HTTP 接线、生产 exporter、受限诊断查询、部署、跨工作树合并及把完整 `RuleAgentRunTrace` 送匿名 telemetry。
- 如需促成正式签收，必须由已认领 T08 工作流所有权的负责人对上述六项逐项明确答复并保存来源回执；协调方或旁路 Codex 审查不能代替其授权。

**最终结论：只读评测轨迹 ABI 的技术复核 PASS；T08 → OBS 真实接线消费接口未满足全部冻结门槛，负责人正式签收尚未取得。**
