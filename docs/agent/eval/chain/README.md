# Agent 评测链路（执行 → 证据 → Grader → 报告）

这份文档同时说明两条相关但不能混用的评测路径：历史 Trace 回归链，以及基于冻结 Gold 的真实效果评测批次。它们的输入、分母和报告口径不同。

## 当前状态与入口（截至 2026-11-20）

| 评测路径 | 核心入口 | 当前状态 |
| --- | --- | --- |
| Trace 回归链 | `AgentEvalChainMain collect/run` + `suite-v1` + `RuleAgentTraceEvaluator` | 2026-11-20 吸收 2026-11-10 G3 批块后 8/8 命中预期，其中 6 PASS、2 NOT_EVALUABLE（归档回放，非新模型执行） |
| Gold / M2 效果评测 | `RuleFactBatchHost` + `RuleFactBatchReport`，复用既有 scorer 与 Trace Grader | 2026-11-17 首批真实批次已交付：10 题 / 11 run / 42 次调用；1 PASS、9 FAIL；5/10 题的全部适用维度可评估 |
| Gold / M3 全量基线 | 同一宿主/Grader，`-FullDataset` | 全部20题 / 21run / 79次调用；1 PASS、19 FAIL；3/20题全部适用维度可评估，授权额度已关闭 |

**当前全量质量结论：FAIL（1/20，5%）。** M3已建立全部20题真实开发集基线，交付验收PASS：20任务/21run/79次调用，1 PASS、19 FAIL，全部适用维度可评估3/20。17题执行FAILED/STOPPED，局部语义维度仍未知。未用26次额度关闭、剩余授权0，临时容器已清理；标准Execute实际通过。语义复核由Codex离线独立完成，不是用户人工终审。[M3实际记录](../../../evidence/rule-agent-provider/2026-11-17-frozen-m3/README.md) · [质量报告与修复优先级](../../../evidence/rule-agent-provider/2026-11-17-frozen-m3/live-5e4325f591f441b4a33d08add4151382/quality-report.md)。下列M2条目保留为历史首批结果与限制，不能覆盖M3当前状态。

- [M2 批次执行记录](ten-case-batch.md) · [真实质量报告](../../../evidence/rule-agent-provider/2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/quality-report.md) · [逐题逐维报告](../../../evidence/rule-agent-provider/2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/report-reviewed.md) · [机器可读报告](../../../evidence/rule-agent-provider/2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/report-reviewed.json)。
- Gold v1.0 共20题；M2当时只执行固定子集001/002/003/004/005/006/007/013/018/020，其余10题当时未执行，不能将M2外推为全量成绩。M3随后已实际执行全部20题。题集SHA-256：`36e2827d7855a8b1ddcacbde013aa60dd59e4b99f215ec35b3b9c7470f86f280`。
- 本批 42 次调用已核对，13 次未使用额度已关闭，`remainingAuthorizedCalls=0`。任何新批次都必须重新生成请求包并取得绑定包 SHA、额度、预算和有效期的新授权；不得复用本批包或额度。
- M2交付验收PASS、回答质量FAIL；其当时标准Execute长预算入口未完成验收，使用等价1350秒专用监督器。该限制随后在M3修复并实际验收标准入口；不能倒推为M2当时标准启动器已通过。

## Trace 回归链：执行 → 证据 → Grader → 报告

| 阶段 | 谁来跑 | 产物 |
| --- | --- | --- |
| ① 真实执行 | 人工/受控环境，`RuleAgentOnlineProbeHost` + `knowledge/scripts/start-rule-agent-probe.ps1` | `docs/evidence/rule-agent-provider/...` 下的 provider trace、交换报文、`task-results.json`、`probe-result.json` |
| ② 采集 | `AgentEvalChainMain collect`（离线） | 白名单 Trace 夹具 + `collection.json`（含来源 SHA、丢弃项） |
| ③ 判定 | `RuleAgentTraceEvaluator`（现有 Grader） | `PASS` / `FAIL` / `NOT_EVALUABLE` + 明细码 |
| ④ 报告 | `AgentEvalChainMain run` | `report.json` + `report.md` |

采集只保留白名单字段：动作类型、step、modelAttempt、观察状态/数据级别/原因码、终态、issue 原因码、证据与关系标识、经 SHA-256 脱敏的 factId。检索候选原文（`excerpt`）、用户可见文案（`message`/`nextStep`）、请求/响应原文一律丢弃，丢弃项记在 `collection.json` 的 `losses` 里。

## Trace 回归链命令

以下命令针对归档 Trace 采集与回归，不会启动真实模型调用。

```bash
# 采集一个真实证据目录（也可由 run 自动触发）
java -cp "knowledge/target/classes:$(cat knowledge/target/cp.txt)" \
  com.zhku.knowledge.eval.chain.AgentEvalChainMain collect \
  --evidence docs/evidence/rule-agent-provider/2026-10-16/t05-69-review-condition-request/live-04994adbb93a4932bed3aa7b1b8b9047 \
  --out target/eval-collected --repo-root .

# 跑整条链并出报告
java -cp "knowledge/target/classes:$(cat knowledge/target/cp.txt)" \
  com.zhku.knowledge.eval.chain.AgentEvalChainMain run \
  --suite knowledge/src/test/resources/agent-eval-chain/suite-v1.json \
  --out target/agent-eval-chain-report --repo-root .
```

Windows 下 `-cp` 分隔符是 `;`。`cp.txt` 由
`mvn -q -pl knowledge -am -Dmaven.test.skip=true install`（先让同仓库模块进入本地仓库）后
`mvn -q -pl knowledge dependency:build-classpath -Dmdep.outputFile=target/cp.txt` 生成。

## Trace 回归链：历史套件与报告（2026-11-08）

`knowledge/src/test/resources/agent-eval-chain/suite-v1.json`（suiteId `agent-eval-chain-v1`）同时覆盖真实证据块与归档夹具块：

| 块 | 来源 | 用例 | 期望 |
| --- | --- | --- | --- |
| `live-t05-69` | 2026-10-16 真实两轮探针 | `T05-69-CLARIFY` | PASS（首轮必须 `ASK_NECESSARY_CONDITION` 追问） |
| | | `T05-69-RESOLVE` | PASS（续答结论 `COMPLETE` 且 claim 有证据链） |
| `g3-20261110-p02` | 2026-11-10 G3 批真实两轮（live-5ecc89a5） | `G3-P02-CLARIFY` | PASS（首轮必要追问，同 t05-69 合同） |
| | | `G3-P02-RESOLVE` | PASS（续答结论 `COMPLETE` 且 claim 有证据链） |
| `e13-failure-fixtures` | R1 归档夹具 | `E13-0842-FAIL-CLOSED` | PASS（非法模型响应必须 `FAILED`，不得伪装成资料不存在） |
| | | `E13-1016-NO-OBSERVATION` | NOT_EVALUABLE（缺工具观察只能是 `TOOL_OBSERVATION_MISSING`） |
| `failed-probe-t05-46` | 2026-10-15 失败探针 | `T05-46-FAILED-RUN` | PASS（失败关闭终态 + 动作有观察） |
| `incomplete-probe-t05` | 2026-10-14 只有 trace、无终态 | `T05-INCOMPLETE-EVIDENCE` | NOT_EVALUABLE（`COLLECTION_INCOMPLETE`） |

历史报告：[2026-11-08/report.md](2026-11-08/report.md)记录当时 6/6 命中（4 PASS / 0 FAIL / 2 NOT_EVALUABLE）。2026-11-20 吸收 `g3-20261110-p02` 块（G3 批已验证期望值原样并入）后为 8/8 命中（6 PASS / 0 FAIL / 2 NOT_EVALUABLE）。这里的 `regressionPassed=true` 表示实际判定与预设期望一致，不表示全部用例质量通过；块内包含 2026-10-14 至 10-16 的历史证据、2026-11-10 G3 批与 R1 归档夹具，均为归档回放，不是新执行结果。

本会话（2026-11-08）的完整工作记录见
[`2026-11-08-agent-eval-chain-delivery.md`](../../../collaboration/2026-11-08-agent-eval-chain-delivery.md)（交付物、验证、提交、排障与边界）。

真实证据时间线（2026-11-20 全部入库 docs/evidence）：2026-10-16 t05-69 两轮探针 → 2026-11-10 G3 批（g3-authorization-review 与 live-5ecc89a5，已吸收进主链套件）→ 2026-11-17/18 M2/M3 冻结、全量与修复及 rfe008 各批（走 Gold 效果评测路径，非 Trace 套件）→ 2026-11-20 DashScope 配额 403 关闭（由用户自行处理；403 探针现场归档于 freeze/probe-2026-11-09 分支）。

## Gold v1.0 与 M2 真实效果评测

### 冻结题集

[Gold v1.0 包说明](../../../evidence/agent-eval/2026-11-17-frozen-gold-v1/README.md)记录 20/20 题的预期契约已审核并冻结，数据集 SHA-256 为
`36e2827d7855a8b1ddcacbde013aa60dd59e4b99f215ec35b3b9c7470f86f280`。它基于 B02/B03 的 2025-09-02 归档文本，不是当前学年政策基准，也不是独立盲测集。

[Effect Eval v1 阶段报告](../../../evidence/agent-eval/2026-11-17-frozen-gold-v1/effect-eval-v1-report.md)和包内 `smoke-subset.json` 是冻结/执行前快照：其中“真实输出 0”及 `PLANNED_NOT_EXECUTED_NOT_AUTHORIZED` 只描述当时状态。随后 M2 已执行 10 题；不要修改冻结包内文件来追写执行状态，也不要把该快照当作当前批次结果。

### M2 首批真实批次

[批次实施与执行记录](ten-case-batch.md)描述宿主、固定样本、预算、复核与报告口径。真实证据位于
[`2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/`](../../../evidence/rule-agent-provider/2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/)。

| 指标 | 实际结果 |
| --- | ---: |
| 题目 / Task | 10 |
| Run | 11（RFE-013 含 ASK 与 PUBLIC 续答） |
| 模型 | DashScope `qwen3.8-flash` |
| Provider 调用 | 42（33 次决策 + 9 次独立发布核验） |
| 题目结论 | 1 PASS / 9 FAIL |
| 全部适用维度可评估 | 5/10（50%） |
| Token | 输入 64,363 / 输出 4,482 |
| 自动重试 | 0 |

本批请求包 SHA-256：`18143180bc208127846495b726e5f218a7f6140d9b4ed3e2b30f61c4cdbcff5c`；运行 JAR SHA-256：`4a97fcb0fdf64ce140ba34b360e30ad60afb9572382a53a81848f30d6cdd4c32`。交付核验记录确认原始 capture/annotation 与报告来源摘要均匹配，离线重放覆盖全部用例和维度。

M2 的“交付验收 PASS”表示批次证据、来源摘要和离线重放核验完成；“回答质量 FAIL”是实际质量结论。10/10 均有确定整题结论，不代表 10/10 全维度可评估。独立语义复核人为 Codex，不是用户人工终审。详细原因以 [质量报告](../../../evidence/rule-agent-provider/2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/quality-report.md) 和 [逐题逐维报告](../../../evidence/rule-agent-provider/2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/report-reviewed.md) 为准。

### 后续真实执行入口

下一批必须使用新的题目/请求包身份和新的授权，不得复用已执行的包 SHA 或已关闭额度。执行前至少核对：题集与选择 SHA、模型、调用上限、Token/时间/费用预算、有效期、停止条件、临时资源与凭证范围。按 [批次记录](ten-case-batch.md) 中的 `Prepare → Check → Assembly → Execute → 独立标注 → Report` 顺序进行；`Execute` 必须获得该具体包的明确授权。当前 `remainingAuthorizedCalls=0`。

[2026-11-17 M2 实施计划](../../../superpowers/plans/2026-11-17-frozen-m2-ten-case-batch.md)保留实施前计划及其完成勾选；勾选表示实现、准备和首批执行步骤已交付，不代表回答质量通过。具体范围仍以实际代码、[批次执行记录](ten-case-batch.md)、批次证据和交付核验为准。

## 采集状态与判定边界

- `COMPLETE`：`task-results.json`（或 `.jsonl`）里有终态快照，正常判定。
- `PARTIAL_TERMINAL_FALLBACK`：只有 `*-outcome.json` 的 snapshot。这类快照可能是中断时的中间态，**只有终态 `FAILED` 才允许判定**，否则强制 `NOT_EVALUABLE`（`TERMINAL_SNAPSHOT_UNPUBLISHED`）。
- `INCOMPLETE`：没有终态快照（例如早期探针只落 `probe-result.json`）。整块只出 `NOT_EVALUABLE`，不可能产出 PASS。

`NOT_EVALUABLE` 不等于通过：`RuleAgentTraceEvaluator.Report.passedGate()` 要求失败与不可判定同时为 0，报告里的 `regressionPassed` 也只在全部用例命中期望时为真。

## 旧版单题 P02 Probe 流程（与 M2 批次分开）

本节保留原有单题 `RuleAgentOnlineProbeHost` 路径，不能代替上面的 M2 批次入口；单题与批次使用不同的宿主、预算和报告。历史冻结包会被后续提示词/协议变更淘汰：`--CheckPackage` 会以「请求包预算、问题或协议身份变化」拒绝，不能靠重建 jar 绕过。正确顺序：

1. 确认授权额度：`approval-state.json` 里 `remainingAuthorizedCalls` 为 0 时必须重新授权，不要复用旧额度。
2. 零调用预检（不产生 provider 调用、不建库、不跑容器）：

   ```powershell
   # 1) 用当前构建生成新包；新目录，绝不覆盖历史冻结包
   knowledge\scripts\start-rule-agent-probe.ps1 -PrepareProbe `
     -PackageRelativePath docs/evidence/rule-agent-provider/<日期>/<批次>/request-package.json

   # 2) 用上一步打印的 PROBE_PACKAGE_SHA256 复核包身份
   knowledge\scripts\start-rule-agent-probe.ps1 -CheckPackage -OnlyP02 `
     -ApprovedPackageSha256 <SHA> `
     -PackageRelativePath docs/evidence/rule-agent-provider/<日期>/<批次>/request-package.json
   ```

   宿主与启动脚本都通过 `ZHKU_RULE_AGENT_PROBE_PACKAGE` 读取仓库相对路径；路径越出 `docs/evidence/rule-agent-provider` 会被拒绝。
3. 启动 Docker 并确认本地有 `pgvector/pgvector:0.8.6-pg16-bookworm`（脚本用 `--pull never`）。
4. 运行 `... -Probe -OnlyP02 -ApprovedPackageSha256 <新包 SHA> -PackageRelativePath <同一相对路径>`，产物即新的证据目录。
5. 用 `collect` 生成夹具，把用例与期望写进套件，跑 `run` 复核，再提交报告。
6. 真实执行不要放进 CI：CI 没有凭证，也不应产生外部调用与费用。

2026-11-08 的零调用预检记录见
`docs/evidence/rule-agent-provider/2026-11-08/eval-chain-preflight/PREFLIGHT.md`。

## CI：只验证 Trace 回归链

`.github/workflows/agent-eval-chain.yml` 在 PR / 推送 / 手动触发时只验证归档 Trace 回归链与现有 Grader：

1. `mvn -pl knowledge -am -Dtest=AgentEvalChainRegressionTest test` —— 6 条用例含篡改终态、抹掉观察、截断 trace 等负例，任何一条回退都会让构建失败。
2. 先 `mvn -pl knowledge -am -Dmaven.test.skip=true install` 让同仓库模块可解析，再用 `AgentEvalChainMain run` 重新生成报告并作为 artifact 上传；回归不通过或报告不匹配时 CLI 以非零码退出。

## 当前缺口与边界

- `suite-v1` 于 2026-11-20 吸收 G3 批块，覆盖 2026-10-14 至 2026-11-10 的归档真实证据与 R1 夹具；吸收与复核均为离线回放，没有执行新的 `--Probe`。2026-11-17/18 M2/M3 批走 Gold 效果评测路径（`RuleFactBatchHost`），不并入本套件；M3 全量质量仍以 Gold 效果批报告为准（1/20）。
- M2 结果为 1/10 PASS、9/10 FAIL；5/10 题的全部适用维度可评估。优先排查项与证据见 [M2 质量报告](../../../evidence/rule-agent-provider/2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/quality-report.md)。本报告的语义复核是 Codex 离线独立复核，不是用户人工终审。
- 本次 M2 使用等价的 1350 秒专用进程监督器调用原获批 jar；标准 `start-rule-fact-eval.ps1 -Batch -Operation Execute` 长预算入口仍需后续修复/验收，不能把本次批次描述为标准 Execute 命令已通过。
- 本批未使用的 13 次调用额度已关闭，`remainingAuthorizedCalls=0`；下一次真实执行必须重新准备请求包并取得新的包级授权。不能复用旧包、旧额度或将零调用预检当作执行授权。
- M2 批次未做前端/E2E 验收，未做用户人工终审；Gold 题集来自 2025-09-02 归档文本，其当前学年适用性仍 UNKNOWN，且开发集不是盲测集。
- E13-1016 夹具只归档决策序列、没有工具观察，因此正确期望仍是 `NOT_EVALUABLE`；若要升级为可判定用例，需要补录真实 `ToolObservation`，不得把历史结果改写为 PASS。
- `docs/evidence/agent-eval/2026-10-29-e13/` 是从 `feat/agent-eval-mvp` 原样复制的归档（字节与 SHA 未变），用于让夹具的来源校验在本分支成立。
