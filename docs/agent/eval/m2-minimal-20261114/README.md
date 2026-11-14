# M2 两轮最小评测套件（预冻结模板 + 绑定器）

本目录只准备 **一个合成任务、两个 run** 的评测输入：

- 题面：`我这门选修课没过，能不能重修？`（不出现在套件里，运行器不读题面）
- 首轮：模型必须检索、读取、追问必要条件（`courseType`），停在 `WAITING_USER / INCOMPLETE / NONE`。
- 续答：只回复 `PUBLIC`（公共选修课），同一个 task 的第二个 run 必须给出带证据引用的结论，达到 `RESOLVED / SUCCEEDED / COMPLETE`。

**当前没有任何真实调用授权**：本目录和绑定器都没有连接数据库、模型或浏览器，没有产生本批证据，也没有生成新的 Grader Evaluation Report。旧 P02/20 题报告一律不能当作本批结果。

## 文件

| 文件 | 作用 | 是否可改 |
| --- | --- | --- |
| `suite-template.json` | 预冻结的两个 case 期望，`runId` 全为 `null`，`evidenceDir` 是占位符 | **冻结**。失败时不得为了让输出好看而修改 |
| `knowledge/scripts/bind_m2_suite.py` | 离线绑定器：填入两个真实 `runId` 和 `evidenceDir`，并做 Grader 之外的结构守卫 | 代码变更须单独评审 |
| `knowledge/scripts/tests/test_bind_m2_suite.py` | 合成快照单元测试，23 项 | 随绑定器维护 |

旧文件 `knowledge/src/test/resources/agent-eval-chain/suite-v1.json` 和 `docs/agent/eval/chain/2026-11-08/report.md` 是历史回放资产，**不得修改、不得拿它们的 runId/结果充当本批结果**。

## 为什么先冻结期望

模板的 expectation 对齐 `suite-v1.json` 第一个真实两轮块，但只保留 M2 的两个 case：

| case | 期望终态 | 关键动作 | claim/证据 |
| --- | --- | --- | --- |
| `M2-CLARIFY` | `WAITING_USER / INCOMPLETE / NONE`，`MISSING_CONDITION` | `SEARCH_REVIEWED_RULES` → `READ_VERIFIED_RULE` → `ASK_NECESSARY_CONDITION` → `PUBLICATION_REVIEW` | 不允许 claim |
| `M2-RESOLVE` | `RESOLVED / SUCCEEDED / COMPLETE` | `SEARCH_REVIEWED_RULES` → `READ_VERIFIED_RULE` → `FINISH_CHECK` → `PUBLICATION_REVIEW` | `expectClaims: true`、`requireEvidenceLinks: true` |

`expectedVerdict: PASS` 的含义是“现有 Grader 应当确认上述契约”，不是“模型已经跑出该结果”，也不是人工给出的参考答案。模板里的 `runId: null` 表示真实 run 尚不存在；绑定器只能用新证据里的真实 UUID 填入。

`bundleCase` 是证据块级断言：`expectedVerdict: NOT_EVALUABLE`、`expectedDetails: ["COLLECTION_INCOMPLETE"]`。它只在 Collector 判定 `INCOMPLETE`（完全没有终态快照）时由 `EvalChainRunner` 产出，用来把“整块缺证据”标成可解释的不可判定，而不是执行异常。

需要知道的边界（按现有 `EvalChainRunner` 实际行为）：

- `COMPLETE`：`bundleCase` 不参与，两个 case 正常判定。
- `PARTIAL_TERMINAL_FALLBACK`（只有 `*-outcome.json`、没有发布终态）：`bundleCase` 也不参与；非 `FAILED` 的 run 被强制记为 `NOT_EVALUABLE / TERMINAL_SNAPSHOT_UNPUBLISHED`，与模板的 PASS 期望不符，因此回归失败。**部分证据暴露为 case 未命中，而不是 bundleCase。**
- `INCOMPLETE`：`bundleCase` 命中 `NOT_EVALUABLE / COLLECTION_INCOMPLETE`；两个 case 仍会各记一条 `NOT_EVALUABLE` 并因期望是 PASS 而未命中，所以整份报告依然失败。bundleCase 命中不等于本批通过。

## 前置条件

1. 已获用户明确授权，并按 M2 准备方案冻结了请求包 SHA、代码 SHA、额度和外发资料边界。
2. 真实执行入口已产出新证据目录，目录内有 `task-results.json`，且严格满足：
   - 恰好 2 条 run；
   - 两条 `taskId` 相同；
   - 两个 `runId` 是不同且合法的 UUID；
   - 第 1 条是首轮 `WAITING_USER / INCOMPLETE / NONE`，第 2 条是续答 `RESOLVED / SUCCEEDED / COMPLETE`；
   - 第 2 条至少有 1 个 claim，且至少有 1 个 claim 通过 `role: CLAIM` 的关系指向 `result.evidence` 中已列出的证据。
3. 从仓库根运行命令；只安装 Python 标准库依赖。

## 绑定新证据（生成 suite.json / binding.json）

```bash
python knowledge/scripts/bind_m2_suite.py \
  --template docs/agent/eval/m2-minimal-20261114/suite-template.json \
  --evidence docs/evidence/rule-agent-provider/<日期>/<批次>/live-<sha> \
  --out docs/agent/eval/m2-minimal-20261114/out \
  --repo-root .
```

Windows 终端同样可用 `python knowledge\scripts\bind_m2_suite.py ...`。`--evidence` 必须在仓库根内，绑定器会把仓库相对路径（`/` 分隔）写进套件。模板中的 `evidenceDir` 只是占位符，**不能**把 `suite-template.json` 直接交给 `run`；必须先绑定。

输出：

- `out/suite.json`：只把两个 `case.runId` 和 `block.evidenceDir` 换成真实值；其余内容与模板语义一致。
- `out/binding.json`：结构守卫结果、失败枚举、绑定前后的 SHA。**这不是 Grader 报告。**

两个输出都用独占创建（`CREATE_NEW`）：只要 `suite.json` 或 `binding.json` 已存在，绑定器立即以非 0 退出，不覆盖、不写入一半。要重试请换新的输出目录，不要删除旧失败记录。

### binding.json 里的 SHA

- `templateSha256`：模板文件字节的 SHA-256。
- `suiteSha256`：生成的 `suite.json` 字节的 SHA-256。
- `bindingSha256`：`binding.json` 去掉自身 `bindingSha256` 字段后，按 UTF-8 + 键排序 + 紧凑分隔符序列化所得的 SHA-256，用于自校验。

### 失败枚举与退出码

| 退出码 | 含义 | 典型原因 |
| --- | --- | --- |
| `0` | 绑定 PASS | 两 run 结构满足、套件已生成 |
| `2` | FAIL | 异 task、runId 重复、轮次状态不符、无 claim/证据关系、模板期望被改、模板 `runId` 非空 |
| `3` | NOT_EVALUABLE | 模板/证据/`task-results.json` 缺失或损坏、run 数量不是 2、run 身份不可读 |
| `4` | 拒绝覆盖 | `out/suite.json` 或 `out/binding.json` 已存在 |

失败时仍尽量生成 `suite.json`：无法证明身份的 `runId` 留 `null`，让现有 Grader 输出 `CASE_RUN_ID_MISSING` / `NOT_EVALUABLE`，**绝不伪造 runId 或 PASS**。异 task 或 runId 重复时，第二个 run 不会被写进续答 case（它不能被证明是同 task 的续答）；模板损坏或被改动时不生成套件，避免把错误期望带进报告。

`binding.json` 只保存固定枚举和计数，不保存原始 JSON、异常正文、题面或用户可见文案。

## 生成现有 Grader Evaluation Report

本批直接使用请求包已绑定的`controlled.jar`内置Grader，不需要重新构建类路径。在Check/Probe之前禁止运行Maven install/package或改写冻结jar；如丢失，从本机私有prepared副本按SHA恢复原字节。需要重新打包则必须冻结新包并重新授权。其他批次单独维护Grader时使用仓库mvnw.cmd，而非假设用户终端存在全局mvn。

可选：先单独采集，确认 Collector 状态。

```powershell
java '-Dloader.main=com.zhku.knowledge.eval.chain.AgentEvalChainMain' -cp knowledge/target/knowledge-0.1.0-rc.1-controlled.jar org.springframework.boot.loader.launch.PropertiesLauncher collect --evidence docs/evidence/rule-agent-provider/<日期>/<批次>/live-<guid> --out target/m2-collected-<新名称> --repo-root .
```

运行绑定后的套件（`run` 会自动采集）：

```powershell
java '-Dloader.main=com.zhku.knowledge.eval.chain.AgentEvalChainMain' -cp knowledge/target/knowledge-0.1.0-rc.1-controlled.jar org.springframework.boot.loader.launch.PropertiesLauncher run --suite docs/agent/eval/m2-minimal-20261114/out/suite.json --out target/m2-report-<新名称> --repo-root .
```

报告为新输出目录内的`report.json`、`report.md`。现有报告写入会覆盖同名文件，因此每次必须使用全新的--out，不删除失败记录重跑。只有在下面两条同时成立时才可把报告当作 M2 验收材料：

1. `binding.json` 的 `status` 是 `PASS`，且 `suiteGenerated` 为 `true`；
2. Grader 报告 `regressionPassed: true`，两个 case 均为 `PASS`（`EXPECTATIONS_MET`），CLI 退出码为 `0`。

任一条不成立就保留失败报告，按失败原因排查真实执行，**不要**：

- 改模板的期望、动作或 claim/链接要求来迎合输出；
- 手工填 `runId`、复制历史 runId，或用 `suite-v1.json`/旧报告回放冒充本次结果；
- 把 `binding.json` 当作 Grader 判定，或把 `NOT_EVALUABLE` 当作通过。

执行前确认新live目录尚不存在，并记录本包SHA、执行开始时间及结束时间。执行后将用户级`probe-claims/<包SHA>.used`复制到该新live目录，核对其中包SHA与本次授权一致；核对原始task-results中的taskId/runId与新provider trace，不以绑定器对旧证据也能PASS来替代来源核验。Grader通过后仍须单独核对公共选修的factId/when及原始checksum/span；结构守卫不证明这些内容。

## 绑定器的职责边界

绑定器是 Grader 之外的**结构补充**，只检查 `task-results.json` 的身份、数量、轮次状态和 claim/证据关系是否存在。它要求续答至少有 1 个 claim 带 `CLAIM` 证据关系；**全部** claim 的链接完整性（重复 ID、孤儿关系、未支撑 claim）仍由现有 Grader 检查。它也**不**验证：

- `checksum`、`startInclusive/endExclusive`、`factId` 或引文语义；
- 答案是否正确、是否覆盖全部适用规则；
- 模型是否真的做了检索/读取（动作与观察仍由现有 Grader 从 Collector 投影中判断）。

因此 `binding status=PASS` 只表示“这份两 run 证据可以进入现有 Grader”，不代表模型行为正确，也不代表 M2 或整体上线通过。

## 测试

```bash
python -m unittest discover -s knowledge/scripts/tests -p "test_bind_m2_suite.py" -v
```

测试使用临时目录中的合成两轮快照，覆盖成功绑定、缺第二个 run、异 task、重复 runId、坏 JSON、无 claim、claim 无证据关系、轮次状态不符、模板漂移、缺失模板、拒绝覆盖、SHA 自校验和“除 runId/evidenceDir 外期望不变”。不会读取真实证据、旧 suite、旧报告、密钥或 target 下的私有产物。

## 当前状态

- 模板与绑定器已离线准备并通过单元测试。
- 尚未获得 M2 真实调用授权；尚无本批 `task-results.json`、`suite.json`、`binding.json` 或 Evaluation Report。
- 失败停止、费用、模型实际用量等仍以真实执行时的记录为准；本文不构成调用授权或费用结论。
