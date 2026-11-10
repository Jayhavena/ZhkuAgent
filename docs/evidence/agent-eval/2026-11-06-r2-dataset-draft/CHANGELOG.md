# 数据集修订草案 v2-dev（RFE 20 题）

状态：**DRAFT / 未生效 / 未放入测试资源目录**。本目录只是修订提案，不是新的评测基线。
日期：2026-11-06（Asia/Shanghai）。作者：主控会话（纸面工作，未运行任何测试）。

## 1. 身份

| 文件 | 字节 | SHA-256 |
|---|---|---|
| `rule-fact-check-effect-v2-dev.draft.json` | 37,117 | `277e6904b52bb03ed9d18328c357c512e764f6ccb81278d07ae3ceda5bc742dc` |
| `manifest-v2-dev.draft.json` | 862 | `13d5e1142aa560571cdf9669e8be040b139a36df74ff84b7b44fa86a775528d1` |

原始文件（**未改动**，仍在 Eval 工作树）：

- `../worktrees/agent-eval-mvp/knowledge/src/test/resources/agent-effect-eval/rule-fact-check-effect-v1-dev.json`
  35,978 B / `cd08b0ac1f55e7ea985c9cd71c67b78a0322006d3340abf3972cb7267e74bfe9`
- 同目录 `manifest.json` 851 B / `cbedd68450a262f651d6a584d88596b3c241af601fd665b090add7c0a7f37af5`

## 2. 相对 v1-dev 的全部差异（逐字段比对实算：共 4 处）

| # | 位置 | v1-dev | v2-dev 草案 | 理由 |
|---|---|---|---|---|
| 1 | `.datasetVersion` | `v1-dev` | `v2-dev` | 内容变更后不能沿用旧版本号与旧哈希 |
| 2 | `.cases[12].mustNotClaim[1]`（RFE-013） | `把“可申请”说成“必然获批`（缺右引号） | `把“可申请”说成“必然获批”` | 源侧文本缺陷，JSON 合法但文本不自洽 |
| 3 | `.cases[12].criticalFailureTags`（RFE-013） | 4 条 | 追加 `MISSED_REQUIRED_CLARIFICATION`（共 5 条） | 该题期望 `ASK_CLARIFICATION`，但 case 级标签无法归因“漏澄清” |
| 4 | `.cases[17].criticalFailureTags`（RFE-018） | 4 条 | 追加 `HALLUCINATED_CURRENT_NOTICE`、`FABRICATED_PERSONAL_STATUS`、`UNAUTHORIZED_ACTION`（共 7 条） | 该题期望缺口处置，但 case 级标签无法归因“编造当期通知/伪造个人状态/越权动作” |

除上述 4 处外，`cases` 其余字段、`expectedFacts`、`requiredEvidence`、`requiredResponseElements`、`mustNotClaim`（除第 2 项）、`sourceCoverage`、`sourceSnapshot`、`scoringContract`、`corpusFingerprint` 全部与 v1-dev 逐字段相同。

已复核仍成立的约束（若启用，`RuleFactCheckEffectDatasetTest` 中除版本断言外的检查应继续通过）：

- `cases` 20 条、`caseCount=20`、caseId 集合不变、`split=DEVELOPMENT`、`samplingStatus=EXPLORATORY`、`humanReviewStatus=PENDING`；
- 文章集仍为 `{2,4,5,6,8,9,10,11,12,18}`；
- 每条 `expectedClaim` 仍与 B02 `fact.text` 逐字相等（实算 0 处不符）；
- 三处 `contentChecksum` 与 B03 一致。

## 3. 启用前置（缺一不可）

1. **测试版本断言必须同步改**：`knowledge/src/test/java/com/zhku/knowledge/eval/RuleFactCheckEffectDatasetTest.java:34` 硬断言 `datasetVersion == "v1-dev"`，`:24-26` 还固定了资源文件名。沿用 v1 文件名或沿用 v1 版本断言都会失败或误判。启用即意味着改测试，属于代码改动，需另行授权。
2. **manifest 同步**：`manifest.json` 的 `datasetVersion` 必须同步为 `v2-dev`，`datasetSha256` 必须换成新数据文件的实算值（本草案已按上述 37,117 B 文件的哈希填好，若重新格式化则需重算）。
3. **文件落位**：草案必须复制到 `knowledge/src/test/resources/agent-effect-eval/` 才会被测试加载；本目录不是加载路径，不会被任何测试读取。
4. **人工复核**：M7 要求先复核 RFE-001/013/018/020 再全量；本草案不改变任何 `humanReviewStatus=PENDING`，也不构成 gold 冻结。
5. **冻结与基线**：启用后须产生新的 R2 身份（新 SHA + 新 baseline 记录），不得把新哈希回填到 R1 冻结记录。

## 4. 未决问题（本草案刻意不改）

1. **RFE-020 的标签语义**：该题 0 事实、0 证据，仍保留 `CITATION_MISMATCH`。`passPolicy` 未定义“标签覆盖”语义，是否删该标签、评测器是否要求覆盖全部标签，均需评分规则先明确；本轮不擅自改。
2. **UNCLASSIFIED 是否额外要求 `Dimension.UNKNOWN`**：PROD 与 EVAL 意见相反（EVAL 要求，PROD 反对）。若最终要求，`RulePublicationGate` 与相关用例可能要一并调整，不属于本数据草案范围。
3. **其余 18 题的标签**：本轮只补了能明确归因的两题；其余题目是否存在同类缺口，需 M7 逐题复核时判定，不做批量猜测。

## 5. 回滚

删除本目录两个 JSON 与本文即可；v1-dev 与其 manifest 未被触碰，Eval 工作树 porcelain 仍应为 0。若草案已复制进资源目录，回滚需同时还原测试断言与 manifest。

## 6. 边界声明

- 本轮只读 v1-dev / manifest / B02 / B03，生成两个新 JSON 与本说明；未修改 v1-dev、未写入 Eval 工作树、未改任何测试或业务代码。
- 未运行 Maven、模型、数据库；草案的正确性仅由本次逐字段比对与不变量复核支撑，**未经测试执行验证**。

## 7. 接入后记（2026-11-06，Asia/Shanghai）

**时点说明：上方原文描述草案创建时的状态；本后记描述后续实际接入状态。** 原“未生效、未放入测试资源目录、未运行测试”不是当前 main 测试资源的状态。本目录仍仅保存历史提案，不是第二套可执行配置，也不是新的 Gold 或 R2 基线。

### 7.1 当前正式资源与唯一来源

v2-dev 已作为独立版本接入 main 的正式测试资源目录。当前唯一权威、实际可由测试加载的 v2 数据为下表 JSON，对应独立 Manifest 与新增测试类；历史草案不参与加载，也不做双边自动同步。

| 正式文件（main） | 实际字节数 | SHA-256 |
|---|---:|---|
| [rule-fact-check-effect-v2-dev.json](<../../../../knowledge/src/test/resources/agent-effect-eval/rule-fact-check-effect-v2-dev.json>) | 37,117 | `277e6904b52bb03ed9d18328c357c512e764f6ccb81278d07ae3ceda5bc742dc` |
| [manifest-v2-dev.json](<../../../../knowledge/src/test/resources/agent-effect-eval/manifest-v2-dev.json>) | 862 | `9e2afff55918cf8375daa42822e528c40aaa54887ed0d4eac4211088964f792f` |
| [RuleFactCheckEffectV2DatasetTest.java](<../../../../knowledge/src/test/java/com/zhku/knowledge/eval/RuleFactCheckEffectV2DatasetTest.java>) | **15,275** | `5596d180412aa7b961d5b2c673404d27a57f070e2f654ffca57e5b8ce3938938` |

正式 JSON 与本目录历史草案 JSON **逐字节一致**，版本差异仍只有原第 2 节所列四处；20 题证据、预期事实与预期结论未意外变化，其余 18 题基线标签不变，scoringContract 未修改。

本目录 [历史草案 Manifest](<manifest-v2-dev.draft.json>) 的 `datasetFile` 仍为 `rule-fact-check-effect-v1-dev.json`，这是草案原始笔误；保留其原字节、历史 SHA-256 `13d5e1142aa560571cdf9669e8be040b139a36df74ff84b7b44fa86a775528d1`，不得作为生效清单。正式 Manifest 已修正为 `rule-fact-check-effect-v2-dev.json`，按最终 JSON 字节重新计算并核对 `datasetSha256`；数据字节未变，因此该字段值仍为 `277e6904b52bb03ed9d18328c357c512e764f6ccb81278d07ae3ceda5bc742dc`。两份 Manifest 仅 `datasetFile` 字段不同，其自身 SHA 因此不同，不能混用。`manifestVersion=v1` 是清单格式版本，不是数据集版本。

**大小勘误：** 前序交付将测试源码误报为 32,295 字节；本次实测为 15,275 字节且 SHA 与原报告一致。32,295 字节属于 [Surefire XML](<../../../../knowledge/target/surefire-reports/TEST-com.zhku.knowledge.eval.RuleFactCheckEffectV2DatasetTest.xml>)，不是 Java 源码。

### 7.2 已有离线验证证据（本次留档不重跑）

前序接入实际执行命令为：

```text
mvn -o -q -pl knowledge -Dtest=RuleFactCheckEffectV2DatasetTest -DfailIfNoTests=false test
```

本次只读解析现存 Surefire XML，记录 **Tests run 7 / Failures 0 / Errors 0 / Skipped 0 / 0.306 秒**，七个 testcase 与源码中七个独立 `@Test` 方法对应。XML 为 32,295 字节 / SHA-256 `1ab4d139a36d213f7810a47e90238a00502223d1d9333593b068789443b88ebd`。

七项内容：版本/清单/SHA 自洽；模块 classpath 不混入 v1；标签契约归属与去重；RFE-013/018 修订及其余 18 题基线；B02/B03 证据与预期事实对齐；20 题处置矩阵；来源哈希与冻结快照的未激活声明。四个增补标签原已在全局八标签集合内，未修改契约。

这是**结构、加载及开发约束测试**，不是独立 JSON Schema 文件级认证，不是 Agent 回答质量验收，不是 Gold Dataset 通过，也不是本次文档任务重跑。运行时未激活项是数据集冻结声明，不是对当前服务的实测；报告未提供本轮资源 SHA 的不可变运行证明链，应分别看待当前文件身份与历史报告身份。

### 7.3 v1 保留及当前使用边界

- Eval 冻结树的 [v1-dev JSON](<../../../../../worktrees/agent-eval-mvp/knowledge/src/test/resources/agent-effect-eval/rule-fact-check-effect-v1-dev.json>) 保持独立原版（35,978 字节 / `cd08b0ac1f55e7ea985c9cd71c67b78a0322006d3340abf3972cb7267e74bfe9`），原 Manifest 与原测试均保留。Eval HEAD 为 `d825bb0dbaa749b4220fd08c618911217aac9f71`，本轮开始工作树 clean；未向该树复制 v2 或运行测试。
- 原第 3 节的测试/Manifest 联动实际通过**新增独立 v2 测试与独立清单**完成，不覆盖 v1 测试或 v1 清单；无需为了接入 v2 改 v1 版本断言。本次新增测试、资源的 main 接入不等于 R2 正式身份获批。
- 原第 5 节是草案阶段的回退说明，不可视作当前已接入版本的回退授权；后续若要回退实际资源，须单独审定范围，不能只删历史目录后宣称已回退。本轮没有执行删除或回退。
- 当前数据集仍为 **DRAFT_NEEDS_HUMAN_CASE_REVIEW**、EXPLORATORY，20/20 `humanReviewStatus=PENDING`。RFE-001/013/018/020 待人工重点复核；RFE-020 的 CITATION_MISMATCH 语义与 M2 的 UNCLASSIFIED/Dimension.UNKNOWN 争议继续保留，不擅改评分规则。
- **Gold 审核/冻结未完成，正式 Agent 评测与真实模型批量评测未开放，R2 准入及建树未获准，Integration 仍 BLOCKED。** M1～M8 原审批边界不变；main 与未来 R2 树没有自动同步机制。
- main 接入前已有脏改动不能归因于本次留档。2026-11-06 08:33:37 +08:00 修改前实测：main HEAD `367e94a062b92cdb17b2a528cd0d0adbfeccbd02`；普通 porcelain 200 条、`-uall` 562 条，区别在未跟踪目录是否展开；不能与早先 198 条跨时点混算。两个文档与三个接入文件当时均为 `??`，未暂存、未提交。
- 本后记任务**仅追加两份指定文档**，没有修改 JSON、Manifest、Java 源码、生产源码、scoringContract、v1/R1/Index，没有运行 Maven、模型、数据库或集成测试，没有创建分支/工作树，也没有 Git 提交或 push。完整文件身份、Git 保护基线、测试逐项边界及未决项见 [决策单 §12.O](<../../../collaboration/audits/2026-11-05-cross-session-file-ownership-decision.md#12o-v2-dev-数据集正式接入与离线验证记录2026-11-06>)。
