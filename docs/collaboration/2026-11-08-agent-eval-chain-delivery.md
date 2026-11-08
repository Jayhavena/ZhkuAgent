# agent-eval-chain 链路交付（会话记录）

> 会话：本会话（实现、测试、提交与推送）
> 时间窗：2026-11-08 17:33 – 18:07（Asia/Shanghai）
> 相关提交：`fa9f1efb`、`816dcc19`、`180885c7`、`e3e548ac`
> 相关运行：[42469319506（push，成功）](https://github.com/Jayhavena/ZhkuAgent/actions/runs/42469319506)、
> [42469324308（PR，成功）](https://github.com/Jayhavena/ZhkuAgent/actions/runs/42469324308)
> 相关记录：[agent-eval-chain CI 失败排查](2026-11-08-agent-eval-chain-ci-failure-triage.md)（并行会话，只做解读与复核）

## 为什么放在 `docs/collaboration/`

按 [`docs/README.md`](../README.md) 的约定，文件名以 `YYYY-MM-DD-` 开头、新文档优先英文短横线；
这份内容既不是设计论证，也不是执行产物，而是跨会话的工作过程记录，与同目录的
`2026-11-05-cross-session-file-ownership-decision.md`、本次并行会话的 CI 排查记录同族。
原草稿在 `docs/agent/eval/chain/2026-11-08/session-log.md`，按上述约定移到这里。

## 结论

「真实 Agent 执行 → 采集 Trace/Evidence → 现有 Grader 自动判定 → 输出 Report」四段已接成一条可复跑链路；
失败样本回归 6 条全部进 CI，push 与 PR 两个工作流运行均绿（各 1 分 11 秒）。
**但真实 `--Probe` 没有执行**：本轮完成的是零调用预检 + 归档证据重放，不是 2026-10-16 之后的真实模型行为样本。

## 起点

- 仓库 `D:\alldesk\Projects\ZhkuAgent\main`，分支 `feat/zhkuagent-v3-frontend`，起始 HEAD `367e94a0`。
- 工作区已有 200+ 条脏改动，其中约 80 条是他人已 staging 的改动；本任务只提交显式路径，未触碰它们。
- 评测历史在 `feat/agent-eval-mvp` 分支（R0/R1 基线），main 上没有 E13 归档证据与夹具，本次原样复制进来。
- 现有 Grader `RuleAgentTraceEvaluator` 只在本地未跟踪存在（见"排障复盘"）。

## 交付物

| 路径 | 作用 |
| --- | --- |
| `knowledge/src/main/java/com/zhku/knowledge/eval/chain/EvalTraceFixture.java` | Trace 夹具模型：加载、来源 SHA 校验、转 `RuleAgentRunTrace`、脱敏 factId |
| `knowledge/src/main/java/com/zhku/knowledge/eval/chain/EvalEvidenceCollector.java` | 从真实探针目录采集并投影为白名单夹具，产出 `collection.json`（含来源 SHA、丢弃项） |
| `knowledge/src/main/java/com/zhku/knowledge/eval/chain/EvalChainSuite.java` | 套件模型：证据块 / 夹具块、期望判定与期望明细 |
| `knowledge/src/main/java/com/zhku/knowledge/eval/chain/EvalChainRunner.java` | 编排：采集 → 调用现有 Grader → 比对期望 |
| `knowledge/src/main/java/com/zhku/knowledge/eval/chain/EvalChainReport.java` | 输出 `report.json` / `report.md` |
| `knowledge/src/main/java/com/zhku/knowledge/eval/chain/AgentEvalChainMain.java` | CLI：`run` / `collect`，不匹配时非零退出 |
| `knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentTraceEvaluator.java` | 现有 Grader，本次纳入版本库（内容不变） |
| `knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineProbeHost.java` | 探针包路径参数化（`ZHKU_RULE_AGENT_PROBE_PACKAGE`） |
| `knowledge/scripts/start-rule-agent-probe.ps1` | 新增 `-PackageRelativePath`，限制在证据根下 |
| `knowledge/src/test/java/com/zhku/knowledge/eval/chain/AgentEvalChainRegressionTest.java` | 6 条回归用例 + 负例 |
| `knowledge/src/test/resources/agent-eval-chain/suite-v1.json` | 套件定义（4 块 / 6 用例） |
| `knowledge/src/test/resources/agent-eval-chain/fixtures/e13-20261029T0842.json`、`e13-20261029T1016.json` | R1 冻结夹具（原样复制） |
| `docs/evidence/agent-eval/2026-10-29-e13/` | 5 个归档证据（task-results / provider-traces / usage-and-chain，SHA 已核对） |
| `docs/evidence/rule-agent-provider/2026-11-08/eval-chain-preflight/` | 新请求包（SHA `a7557cb4…`）与 `PREFLIGHT.md` |
| `.github/workflows/agent-eval-chain.yml` | CI：跑回归测试 + 重新生成报告并上传 artifact |
| `docs/agent/eval/chain/README.md`、`2026-11-08/report.{json,md}` | 使用说明与基线报告 |

## 链路要点

- **采集只留白名单字段**：动作类型、step、modelAttempt、观察状态/数据级别/原因码、终态、issue 原因码、
  证据与关系标识、经 SHA-256 脱敏的 factId。检索候选原文 `excerpt`、用户可见文案 `message`/`nextStep`、
  请求/响应原文一律丢弃，丢弃项记入 `collection.json` 的 `losses`。
- **判定复用现有 Grader**：不新增第二套判定逻辑，CI 跑的就是线上 `RuleAgentTraceEvaluator`。
- **采集状态决定判定上限**：
  - `COMPLETE`：有终态快照，正常判定。
  - `PARTIAL_TERMINAL_FALLBACK`：只有 `*-outcome.json` 的 snapshot，可能是中断中间态，
    **只有终态 `FAILED` 才允许判定**，否则强制 `NOT_EVALUABLE`（`TERMINAL_SNAPSHOT_UNPUBLISHED`）。
  - `INCOMPLETE`：没有终态快照，整块只出 `NOT_EVALUABLE`（`COLLECTION_INCOMPLETE`），不可能产出 PASS。
- **`NOT_EVALUABLE` 不等于通过**：`Report.passedGate()` 要求失败与不可判定同时为 0；报告里的
  `regressionPassed` 只在全部用例命中期望时为真。

## 回归套件（6 条）

| 用例 | 来源 | 期望 |
| --- | --- | --- |
| `T05-69-CLARIFY` | 2026-10-16 真实两轮探针（首轮） | PASS：必须 `ASK_NECESSARY_CONDITION` 追问，终态 `WAITING_USER` |
| `T05-69-RESOLVE` | 同一探针（续答） | PASS：`RESOLVED` + `COMPLETE` + claim 有证据链 |
| `E13-0842-FAIL-CLOSED` | R1 归档夹具 | PASS：非法模型响应必须 `FAILED`，不得伪装成"资料不存在" |
| `E13-1016-NO-OBSERVATION` | R1 归档夹具 | NOT_EVALUABLE：缺工具观察只能是 `TOOL_OBSERVATION_MISSING` |
| `T05-46-FAILED-RUN` | 2026-10-15 失败探针 | PASS：有终态的失败运行 + 动作有观察 |
| `T05-INCOMPLETE-EVIDENCE` | 2026-10-14 只有 trace、无终态 | NOT_EVALUABLE：`COLLECTION_INCOMPLETE` |

另有三类负例（不进套件，直接断言 Grader/采集器行为）：篡改终态 → FAIL、抹掉观察 → NOT_EVALUABLE、
截断 trace → NOT_EVALUABLE。

## 真实执行入口与零调用预检

- 历史冻结包（2026-10-16）已被后续提示词/协议变更淘汰，`--CheckPackage` 拒绝：
  `IllegalArgumentException: 请求包预算、问题或协议身份变化`（`RuleAgentOnlineProbeHost.java:51/69`）。
  重建 jar 不能绕过，因为漂移在逐字 payload。
- 本次改动：宿主与脚本支持仓库相对包路径，且限制在 `docs/evidence/rule-agent-provider` 下。
- 预检结果（2026-11-08）：
  - 新包：`docs/evidence/rule-agent-provider/2026-11-08/eval-chain-preflight/request-package.json`（89,904 字节）
  - `PROBE_PACKAGE_SHA256=a7557cb4063c5cf98ed9fc6185ca8ee516c056616a37f8bfd5c147de057d5329`
  - `--CheckPackage`：`passed=true`、`providerCalls=0`、`databaseOperations=0`、`maximumProviderCalls=10`
  - 本地已有 `pgvector/pgvector:0.8.6-pg16-bookworm`，`--Probe` 不需要拉镜像
- **未执行 `--Probe`**：需要新的外发授权（旧额度已耗尽：`remainingAuthorizedCalls=0`、已用 8 次）与可访问
  Docker/凭证的受控终端；本轮受限 sandbox 内 docker 管道被拒绝。

## 验证记录

本地（离线）：

```bash
mvn -o -pl knowledge -am -Dtest=AgentEvalChainRegressionTest \
  -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test   # 6/6
mvn -o -q -pl knowledge -am -DskipTests test-compile           # 通过
```

`-DforkCount=0` 只是本地沙箱限制（surefire fork 被拒绝），CI 不需要。

干净检出（模拟 CI）：`git archive HEAD` 解到临时目录后

```bash
mvn -o -B -q -pl knowledge -am -Dmaven.test.skip=true install
mvn -o -B -q -pl knowledge dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "knowledge/target/classes:$(cat knowledge/target/cp.txt)" \
  com.zhku.knowledge.eval.chain.AgentEvalChainMain run \
  --suite knowledge/src/test/resources/agent-eval-chain/suite-v1.json \
  --out target/agent-eval-chain-report --repo-root .
```

结果：编译通过、6/6 测试、6 条用例全部 `[MATCH]`、`regression=PASS`。

CI（GitHub Actions，工作流 `agent-eval-chain`）：

| 运行 | 提交 | 结果 | 原因 |
| --- | --- | --- | --- |
| 42468688830 / 42468713415 | `816dcc19` | 失败 | `RuleAgentTraceEvaluator` 未跟踪，干净检出编译失败 |
| 42468968938 / 42468972122 | `180885c7` | 失败 | 报告步骤 `dependency:build-classpath` 解析不到同仓库模块 |
| 42469319506（push）/ 42469324308（PR） | `e3e548ac` | **通过（各 1m11s）** | 6/6 测试、6 条 `[MATCH]`、报告上传 |

PR：[#1 feat(eval): 打通真实执行→证据→Grader→报告的评测链路](https://github.com/Jayhavena/ZhkuAgent/pull/1)
（base `main`，head `feat/zhkuagent-v3-frontend`，检查已全绿，未合并）。

## 提交

| 提交 | 说明 | 变更 |
| --- | --- | --- |
| `fa9f1efb` | feat(eval): 打通真实执行到评测报告的可复跑链路 | 19 files, +2364 |
| `816dcc19` | fix(probe): 参数化探针包路径并完成零调用预检 | 5 files, +1000 |
| `180885c7` | fix(eval): 纳入 Trace Grader 源码，修复干净检出编译失败 | 1 file, +165 |
| `e3e548ac` | fix(ci): 报告步骤先安装同仓库模块，修复干净检出依赖解析失败 | 2 files, +6/−2 |

均用显式路径提交，未混入工作区既有脏改动。分支相对 `origin/main` 领先 10 个提交（本任务 4 个 + 既有 6 个）。

## 与并行会话的关系

本次 CI 首次失败（`816dcc19`）后，另一会话 `session-0b9b5a3d` 在 17:55–18:05 独立排查同一封告警邮件，
结论与修复一致，记录见[同目录的 CI 排查文档](2026-11-08-agent-eval-chain-ci-failure-triage.md)。

两边出现过一次并发写同一 workflow 文件的情况：对方读到旧版后在 17:56:30 被版本守卫拦下，
我这边在 17:56:48 先提交了 `e3e548ac`；对方回读后确认已修复，没有产生重复提交。
排查文档里对"两版修法"（A：install + build-classpath；B：build-classpath 直接加 `-am`）留有对比，
当前落库的是 A，B 是否收敛待用户决定。

## 排障复盘

1. **夹具 coverage 位置**：E13/R1 夹具把 coverage 放在 `terminal.result.coverage`，不在 terminal 顶层；
   loader 兼容两种形状，issue 也从 `items[].issues` 扁平化。
2. **repoRoot 定位**：原 `getPath()` 在 Windows 得到 `/D:/...`；改用 `getProtectionDomain().getCodeSource()
   .getLocation().toURI()`，向上找含 `pom.xml` 且含 `knowledge` 目录的祖先（surefire 工作目录被改过，不能依赖
   `user.dir`）。
3. **未跟踪 Grader**：`RuleAgentTraceEvaluator.java` 本地存在但从未提交，CI 编译失败；纳入版本库后修复。
4. **同仓库模块依赖**：`dependency:build-classpath -pl knowledge` 需要在本地仓库解析 `com.zhku:agent-runtime`；
   干净检出上先 `install` 再取 classpath。
5. **本地 surefire fork**：受限沙箱下报 `CreatePipe error=5`，本地用 `-DforkCount=0`；CI 不受影响。

## 边界与未完成

- 未跑 `--Probe`，因此没有 2026-10-16 之后的真实模型行为样本；本轮验证的是「归档证据 → 判定」。
- E13 1016 只归档了决策序列、没有 `ToolObservation`，正确期望是 `NOT_EVALUABLE`；要变成硬证据需在探针侧补录观察。
- 预检时 docker 引擎可从普通终端访问，但受限 sandbox 内管道被拒绝；Live 批次需在能访问 Docker 与凭证的终端执行。
- PR #1 尚未合并；工作区仍有他人未提交改动，未做清理。
- 未跟踪的失效测试 `knowledge/src/test/java/com/zhku/knowledge/eval/RuleFactOfflineScorerTest.java`
  已移到 `.scratch/eval-chain-20261108/moved-tests/`（未提交），全量 `test-compile` 因此恢复。

## 后续建议

1. 合并 PR #1，确认合并后 main 上的 `agent-eval-chain` 仍为绿色。
2. 取得新的外发授权后，按 `docs/agent/eval/chain/README.md` 的 5 步跑一次 `--Probe`，用新证据补真实行为用例。
3. 在探针侧补录 `ToolObservation`，把 E13 1016 从 `NOT_EVALUABLE` 升级为可判定用例。
