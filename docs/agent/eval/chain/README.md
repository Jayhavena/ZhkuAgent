# Agent 评测链路（执行 → 证据 → Grader → 报告）

这条链路把「真实 Agent 跑一次」和「可重复的自动判定」分开：

| 阶段 | 谁来跑 | 产物 |
| --- | --- | --- |
| ① 真实执行 | 人工/受控环境，`RuleAgentOnlineProbeHost` + `knowledge/scripts/start-rule-agent-probe.ps1` | `docs/evidence/rule-agent-provider/...` 下的 provider trace、交换报文、`task-results.json`、`probe-result.json` |
| ② 采集 | `AgentEvalChainMain collect`（离线） | 白名单 Trace 夹具 + `collection.json`（含来源 SHA、丢弃项） |
| ③ 判定 | `RuleAgentTraceEvaluator`（现有 Grader） | `PASS` / `FAIL` / `NOT_EVALUABLE` + 明细码 |
| ④ 报告 | `AgentEvalChainMain run` | `report.json` + `report.md` |

采集只保留白名单字段：动作类型、step、modelAttempt、观察状态/数据级别/原因码、终态、issue 原因码、证据与关系标识、经 SHA-256 脱敏的 factId。检索候选原文（`excerpt`）、用户可见文案（`message`/`nextStep`）、请求/响应原文一律丢弃，丢弃项记在 `collection.json` 的 `losses` 里。

## 命令

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

Windows 下 `-cp` 分隔符是 `;`，`cp.txt` 由
`mvn -q -pl knowledge dependency:build-classpath -Dmdep.outputFile=target/cp.txt` 生成。

## 套件与当前基线

`knowledge/src/test/resources/agent-eval-chain/suite-v1.json`（suiteId `agent-eval-chain-v1`）同时覆盖真实证据块与归档夹具块：

| 块 | 来源 | 用例 | 期望 |
| --- | --- | --- | --- |
| `live-t05-69` | 2026-10-16 真实两轮探针 | `T05-69-CLARIFY` | PASS（首轮必须 `ASK_NECESSARY_CONDITION` 追问） |
| | | `T05-69-RESOLVE` | PASS（续答结论 `COMPLETE` 且 claim 有证据链） |
| `e13-failure-fixtures` | R1 归档夹具 | `E13-0842-FAIL-CLOSED` | PASS（非法模型响应必须 `FAILED`，不得伪装成资料不存在） |
| | | `E13-1016-NO-OBSERVATION` | NOT_EVALUABLE（缺工具观察只能是 `TOOL_OBSERVATION_MISSING`） |
| `failed-probe-t05-46` | 2026-10-15 失败探针 | `T05-46-FAILED-RUN` | PASS（失败关闭终态 + 动作有观察） |
| `incomplete-probe-t05` | 2026-10-14 只有 trace、无终态 | `T05-INCOMPLETE-EVIDENCE` | NOT_EVALUABLE（`COLLECTION_INCOMPLETE`） |

当前基线报告：`docs/agent/eval/chain/2026-11-08/report.md`，6/6 命中，4 PASS / 2 NOT_EVALUABLE。

## 采集状态与判定边界

- `COMPLETE`：`task-results.json`（或 `.jsonl`）里有终态快照，正常判定。
- `PARTIAL_TERMINAL_FALLBACK`：只有 `*-outcome.json` 的 snapshot。这类快照可能是中断时的中间态，**只有终态 `FAILED` 才允许判定**，否则强制 `NOT_EVALUABLE`（`TERMINAL_SNAPSHOT_UNPUBLISHED`）。
- `INCOMPLETE`：没有终态快照（例如早期探针只落 `probe-result.json`）。整块只出 `NOT_EVALUABLE`，不可能产出 PASS。

`NOT_EVALUABLE` 不等于通过：`RuleAgentTraceEvaluator.Report.passedGate()` 要求失败与不可判定同时为 0，报告里的 `regressionPassed` 也只在全部用例命中期望时为真。

## 新增一次真实执行

1. 确认授权额度：`approval-state.json` 里 `remainingAuthorizedCalls` 为 0 时必须重新授权，不要复用旧额度。
2. 启动 Docker 并确认本地有 `pgvector/pgvector:0.8.6-pg16-bookworm`（脚本用 `--pull never`）。
3. 运行 `knowledge/scripts/start-rule-agent-probe.ps1 -Probe -ApprovedPackageSha256 <包 SHA>`，产物即新的证据目录。
4. 用 `collect` 生成夹具，把用例与期望写进套件，跑 `run` 复核，再提交报告。
5. 真实执行不要放进 CI：CI 没有凭证，也不应产生外部调用与费用。

## CI

`.github/workflows/agent-eval-chain.yml` 在 PR / 推送 / 手动触发时：

1. `mvn -pl knowledge -am -Dtest=AgentEvalChainRegressionTest test` —— 6 条用例含篡改终态、抹掉观察、截断 trace 等负例，任何一条回退都会让构建失败。
2. 用 `AgentEvalChainMain run` 重新生成报告并作为 artifact 上传；回归不通过时 CLI 以非零码退出。

## 已知缺口

- 2026-10-16 之后没有重新跑过 Live 探针；本轮验证的是「归档证据 → 判定」这一段，不代表当前模型行为。
- E13 1016 夹具只归档了决策序列，没有工具观察，因此该用例的正确期望是 `NOT_EVALUABLE` 而不是 PASS；要把它变成硬证据，需要在探针侧补录 `ToolObservation`。
- `docs/evidence/agent-eval/2026-10-29-e13/` 是从 `feat/agent-eval-mvp` 原样复制的归档（字节与 SHA 未变），用于让夹具的来源校验在本分支成立。
