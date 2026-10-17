# T06 真实库上的取消 / 容量 / 并发 / 终态矩阵（本轮通过）

> 时间 2026-10-16 21:16。对应交接文档 [T06-HANDOFF.md](../t06-handoff/T06-HANDOFF.md) 第 3 节第 1 项
> 的收尾扩展：把合成层验证过的取消、413 容量、终态矩阵搬到**真实一次性临时库 + 真实已审核资料层**
> （`RuleAgentSimulatedChainTempDbHttpTest` 新方法 `realTempDbCancelCapacityAndTerminalStatesOverActualHttp`）。
> 全部为本地提交，未 push／PR／标签／部署／合并。

## 0. 一句话状态

**新方法在真实临时库 + 真实已审核资料 + 真实回环 HTTP 上通过（1 项，12.66s，0 失败 0 错误 0 跳过）**：
413 容量不产生任务；取消后迟到执行结果保持 STOPPED 且无证据、无结论；4 线程同键并行受理只产生
一个任务、一次执行；三条终态（部分覆盖／检索未命中／处理失败）逐条带真实证据或真实检索路径。
**决策段仍是宿主显式模拟执行器（模型尝试 0、供应商调用 0）**，不得读作在线可用。

同轮用统一受控验收脚本（`-Scenario chain`）复跑**两个方法**：2 项 15.62s 0 失败，退出码 0，
双证据落盘（[`../t06-unified-20261016-211949/README.md`](../t06-unified-20261016-211949/README.md)）。
整类运行确认**原主链方法无回归**（代理方法本轮按假设跳过，**跳过不算通过**）。

## 1. 身份与执行范围

| 项 | 值 |
| --- | --- |
| worktree / 分支 | `C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent` ／ `codex/t06-controlled-launch` |
| 改动文件 | `knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentSimulatedChainTempDbHttpTest.java`（**仅测试，未改主源码**）；`scripts/t06-controlled-acceptance.ps1`（chain 场景收两个方法、证据核对支持多文件） |
| 装配 | 同主链方法：`RuleAgentHttpConfiguration` + `RuleAgentControlledLaunchConfiguration(SIMULATED)` + `RuleAgentReviewedToolsConfiguration` + `RuleCheckPreviewSecurityConfiguration` |
| 共享合同 | v3 未改；`RuleAgentOnlineHttpConfiguration.java` 与基线零差异 |
| 资料根 | `D:/alldesk/Projects/ZhkuAgent`（主工作目录，只读；`-Dzhku.t06.root` 指定） |
| 外发 | 供应商调用 0、模型密钥未读取；`injectedServerPort` 记录为 `null`（子进程已摘除注入端口） |

## 2. 临时库（一次性，每轮重建）

| 项 | 值 |
| --- | --- |
| 容器（绿轮） | `zhku-t06-tmpdb-20261016-211949-1`（脚本轮，127.0.0.1:55399）；手动绿轮 127.0.0.1:56235 |
| 镜像 | `pgvector/pgvector:0.8.6-pg16-bookworm`（本机已有，未拉新镜像） |
| 库名 / 用户 | `zhkuagent_rulecheck_e2e` ／ `zhkuagent` |
| 空库证明 | 每轮迁移前 `information_schema.tables`（排除系统 schema）计数 = 0 |
| 迁移 | `ZhkuFlywayTestSupport.migrate`，18 个迁移 applied，至 v22 |

## 3. 实际通过项（逐条对应测试断言，22 条交换：POST 202×9／GET 200×6／GET 404×6／POST 413×1）

| # | 能力 | 实际观察 |
| --- | --- | --- |
| 1 | 容量 | 70000 字节请求体 `POST /tasks` → **413** `REQUEST_TOO_LARGE`；按 `clientRequestId` 查询 → **404**（**未产生任务**，不是先受理再拒） |
| 2 | 取消（受理后执行前） | `POST /tasks` → 202 → `POST /tasks/{id}/cancel`（带 runId + expectedVersion）→ 202 |
| 3 | 取消（终止前不得虚称） | 执行未完成时 `GET` → `taskStatus=ACTIVE`、`runState=STOP_REQUESTED`、**`terminated=false`** |
| 4 | 取消（迟到结果不得改写） | 投递执行后再 `GET` → `taskStatus=STOPPED`、`runState=CANCELLED`、**`terminated=true`** |
| 5 | 取消（无证据无结论） | 结果 `coverage=NONE`、**`evidence` 空**、`issues[0].reasonCode=CANCELLED`、`execution.answerSource=NONE` |
| 6 | 取消（无原文） | `GET /tasks/{id}/originals/{随机令牌}` → **404** |
| 7 | 取消（跨主体） | 另一账号 `GET` 同一任务 → **404** `TASK_NOT_AVAILABLE` |
| 8 | 并发幂等（受理面） | 4 线程 CountDownLatch 同键并行 `POST /tasks` → **全部 202**、**同一 taskId**、`duplicate=true` ×3（只有首个非重复）、**队列 size=1** |
| 9 | 并发幂等（执行面） | 手动驱动执行一次 → 执行计数 **+1**（同键并发不产生第二次执行）；`GET` → `RESOLVED` 且 `evidence` **非空**（revision `2025.9.2`，quote「第二条　所有全日制普通本科学生都必须按人才培养方案要求参加选修课学习。」） |
| 10 | 终态：部分覆盖 | `RESOLVED`／`runState=INCOMPLETE`／`coverage=PARTIAL`，**带真实已审核证据**，`issues[0].reasonCode=ANSWER_COVERAGE_INCOMPLETE`；原文 404 |
| 11 | 终态：检索未命中 | `RESOLVED`／`INCOMPLETE`／`coverage=NONE`，`evidence` 空；reasonCode **恰好两个**：`RETRIEVAL_NO_MATCH`、`REPLAY_NOT_COVERED`（整表比对，不是只查第一条）；`execution.replayCoverage=MISS` |
| 12 | 终态：处理失败 | `FAILED`／`FAILED`／`coverage=NONE`；reasonCode **恰好两个**：`MODEL_UNAVAILABLE`、`REPLAY_NOT_COVERED`；`replayCoverage=MISS`、**`modelAttemptCount=0`**（模拟不得声称模型尝试）；原文 404 |
| 13 | 退出 | 上下文关闭后 `activeJobs=0`、执行队列空；关闭后绑定端口不可连接；证据 JSON `resourcesExited=true` |

原始交换见 [`cancel-capacity-terminal.json`](cancel-capacity-terminal.json)，构建日志见 [`maven.log`](maven.log)。

## 3.5 模拟执行器扩展与回放覆盖的诚实记账

`execute` 内按问题文本分流四条业务分支（等待澄清／部分覆盖／检索未命中／处理失败；**取消不需要分支——
取消发生在执行之前**），检索与读取仍走真实工具组件（`registry.open(...).search/read`），
未命中分支用**保证不存在的检索词**走真实检索证明"没命中"，而不是伪造空结果。

回放覆盖记账规则（`replayHit = waiting || !evidence.isEmpty()`）：

- 等待澄清与**有证据**都算 HIT（回放达成了它的产出）——保持主链方法无回归；
- **无证据必须标 MISS**：`Execution(ENGINEERING_REPLAY, 0, steps, false, NONE/VERIFIED_RULE_ONLY, HIT/MISS)`；
- MISS 的非 ACTIVE 快照**必须**追加 `Issue("replay-miss", REPLAY_MISS, REPLAY_NOT_COVERED, …)` 显式披露
  （合同硬约束，见 §5 红3）——不能把"回放没覆盖"伪装成"资料没有规定"或静默留空；
- `answerSource` 与证据联动：无证据 → `NONE`，有证据 → `VERIFIED_RULE_ONLY`。

## 4. 本轮失败轨迹（四条红，原样保留，不改写为通过）

| 轮次 | 日志 | 现象 | 根因 | 处理 |
| --- | --- | --- | --- | --- |
| 红1 | [`maven-red.log`](maven-red.log) | `expected: "NONE" but was: ""`（测试行 455） | 断言路径层级错：`execution` 在**快照层**不在 `result` 内 | 改 `stopped.path("execution").path("answerSource")` |
| 红2 | [`maven-red2.log`](maven-red2.log) | `expected: "RESOLVED" but was: "ACTIVE"`（行 509） | 给 `ENGINEERING_REPLAY` 传了 `ReplayCoverage.NOT_APPLICABLE`（**ONLINE 专属**），构造抛 `IllegalArgumentException: 非法规则查证合同`；异常死在 FutureTask 里，任务停留 ACTIVE——只看 `taskStatus` 会把合同违例误读成业务失败 | 无证据标 `MISS`；执行器内显式 `AtomicReference` 捕获并 `printStackTrace` |
| 红3 | [`maven-red3.log`](maven-red3.log) | 同样是 `expected: "RESOLVED" but was: "ACTIVE"`（行 511） | 合同：`replayCoverage==MISS && taskStatus!=ACTIVE` 的 `TaskSnapshot` **必须**带 `REPLAY_NOT_COVERED` 问题，否则构造即抛 | issues 增加 `replay-miss` 披露项；`WAITING_USER` 仍保持 HIT 避免主链回归 |
| 红4 | [`maven-red4.log`](maven-red4.log) | 断言全过但测试 ERROR：`FileNotFoundException`（行 548） | 证据目录路径拼错（拼成了**主工作区**前缀），Java 写盘不建父目录 | 改回本 worktree 证据目录；**非回归**——46 条断言全过后才在写盘失败 |

> 红2/红3 的共同教训：模拟执行器内部异常会被 `RuleAgentTaskStore.execute` 统一吞成 `FAILED`，
> 断言只看 `taskStatus` 会掩盖真实卡点——测试必须显式捕获执行器异常并打印。

## 5. 合同硬约束（本轮实测固化，供后续会话直接引用）

| # | 约束 | 违背后果 |
| --- | --- | --- |
| 1 | `Execution`：`ENGINEERING_REPLAY` 的 `replayCoverage` **只能是 HIT/MISS**；`NOT_APPLICABLE` 是 ONLINE 专属 | 构造抛 `IllegalArgumentException: 非法规则查证合同` |
| 2 | `TaskSnapshot`：`replayCoverage==MISS && taskStatus!=ACTIVE` **必须**带 `REPLAY_NOT_COVERED` 问题 | 同上 |
| 3 | `Subquestion` 覆盖度联动：claims 空 → `NONE`；claims 非空且无 issue 无澄清 → `COMPLETE`；否则 `PARTIAL` | 同上（红2 之前的方法 1 已隐式满足） |

## 6. 三层分开记账（不可互相替代）

| 层 | 本轮状态 |
| --- | --- |
| 真实数据库身份 + 真实已审核资料安装/检索/读取 + 真实回环 HTTP | **通过**（本轮范围） |
| 决策段（模型） | **工程模拟**：宿主显式 `ruleAgentSimulatedExecutor`，`Mode=ENGINEERING_REPLAY`、模型尝试 0、供应商调用 0，不得读作在线 |
| 真正在线链路 | **未执行**（T05 未通过、旧额度已关闭；在线装配未被本轮启用） |

## 7. 脚本级验收（统一受控验收脚本，chain 场景扩展为两方法）

| 项 | 值 |
| --- | --- |
| 运行 | `t06-unified-20261016-211949`，`-Scenario chain`，退出码 **0** |
| 结果 | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`（15.62s）——主链方法 + 新方法 |
| 脚本改动 | surefire 过滤器 `#方法1+方法2` 多方法；`Test-EvidenceLanded` 改收 `[string[]]$ExpectedFiles`（缺任一文件即警告"跳过不算通过"） |
| 证据 | [`chain/simulated-chain-tempdb.json`](../t06-unified-20261016-211949/chain/simulated-chain-tempdb.json)（8888 字节）+ [`chain/cancel-capacity-terminal.json`](../t06-unified-20261016-211949/chain/cancel-capacity-terminal.json)（14068 字节，与绿轮逐字节一致） |

复现：

```powershell
cd C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent
.\scripts\t06-controlled-acceptance.ps1 -Scenario chain *>&1 | Out-File run.log -Encoding utf8
```

## 8. 资源退出核对

| 项 | 结果 |
| --- | --- |
| 本轮创建容器 | 手动轮 + 脚本轮临时库容器，**按名称精确确认后**逐个 `docker rm -f`（含手动轮残留 `zhku-t06-tmpdb-b-1792156691`）；`docker ps -a --filter name=zhku-t06` 计数 **0** |
| 监听端口 | 各轮临时库端口（56235／55399 等）监听数 **0** |
| java 进程 | 无残留（本机常有其他会话并发 `mvn`，禁止按进程名批量终止） |
| 其他容器 | `zhkuagent-postgres`（Exited）等未触碰；原演示库未启动、未写入 |
| 临时目录 | `tmp/t06-b-fullclass`（整类运行用）已删除 |
| 写入范围 | 仅本轮一次性临时库 |

## 9. 未验证范围（明确标注，不隐含通过）

- 代理方法（`sameChainThroughRepoNginxProxyWithActualProxiedResponses`）本轮整类运行中**按假设跳过**——
  **跳过不算通过**；其通过记录以 [`../t06-simulated-chain-01/`](../t06-simulated-chain-01/) 与
  [`../t06-unified-20261016-204306/`](../t06-unified-20261016-204306/) 两轮为准，本轮未重跑。
- 在线分支真机、浏览器整体验收、多实例/重启运行时行为（源码明确不支持）。
- 本轮"通过"仅限 SIMULATED 工程模式；不构成产品质量、可靠性或学生收益结论（属 T08/T09/T10）。

## 10. 提交

| 项 | 值 |
| --- | --- |
| 范围 | `RuleAgentSimulatedChainTempDbHttpTest.java` + `scripts/t06-controlled-acceptance.ps1` + 本目录 + `../t06-unified-20261016-211949/` |
| 本地操作 | 仅本地提交；不 push／PR／标签／部署／改写历史 |
