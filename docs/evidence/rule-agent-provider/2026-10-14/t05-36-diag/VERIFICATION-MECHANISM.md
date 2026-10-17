# 核验机制说明（确定性检查／模型语义判断／程序发布约束，三层分开）

本文件描述**已经实现并本地验证**的核验机制。三层职责严格分开，**不得互相代替或混为一谈**。
所有条目为宿主侧实现与本地测试证据；**不构成在线语义正确性承诺**。

---

## 一、确定性检查（可判定、可复现，不含模型判断）

| 检查 | 内容 | 实现入口 |
| --- | --- | --- |
| 来源 | `sourceId` / `chunkId` / `profileId` / `requiredScope` / `chunkingVersion` 必须与已审核资产一致 | `RuleAgentEvidenceTools`、`JdbcRuleCheckEvidenceReader`、`Reviewed2025ControlledCatalogue` |
| 版本 | 仅解释 **2025.9.2** 历史本科文本；审核目录与参考版本固定 | `RuleAgentContract.REVISION`、审核目录装配 |
| 指纹 | 审核片段内容校验和、事实能力权威 SHA、核验输入 `inputFingerprint` 必须**原样回传**并逐字相等 | `RuleAgentProtocol`、`RulePublicationProtocol`（第 47 行 `inputFingerprint` 相等） |
| 跨度 | 需求/缺口跨度取自**原 `questions.text`** 的 UTF-16 字符坐标（左闭右开），越界或不等于原文即拒绝；声明值必须等于问题原文切片 | `RulePublicationProtocol.checkSpan`、`RuleResultPublisher`（第 142 行切片比对） |
| 证据句柄 | `originalToken` 必须仍可解析；证据被替换即 `EVIDENCE_CHANGED` | `RuleActionBroker`（第 60 行）、`RuleAgentOnlineFactory.revalidate` |
| 结构 | 动作/核验 JSON 的字段、枚举、必填项、数量上限（如 `maxRequirements`）严格校验 | `RuleAgentProtocol`、`RulePublicationProtocol`、`RuleAgentProvider` |
| 资源/预算 | 每 run 4 次决策 + 1 次核验，共 5 次/110 秒，单次 20 秒、1024 输出 token，无自动重试 | `RuleAgentContract.DEFAULT_BUDGET`、`RulePublicationContract.MAX_DECISION_CALLS` |

这些检查**不依赖模型判断**：给定相同输入与资产，结果确定。

## 二、模型执行的“需求—事实”语义判断（概率性，不是判据）

- 由**独立核验调用**产出 `SemanticAssessment`：分别列出问题侧需求与事实侧支持、给出 `unresolvedDemands`。
  提示词要求“独立识别每个原子问题的完整需求，比较两侧分别列需求；不得省略未支持或未理解的部分”
  （`RuleAgentProvider.REVIEW_SYSTEM`）。
- 程序只对这份判断做**结构 + 指纹 + 跨度**校验（第一节）；**判断内容本身仍是模型输出**，
  结构合法的错误语义判断无法被自动发现。因此**不得**声称“语义正确性已被保证”。
- 反例覆盖：错指纹、未声明字段、非法枚举、坏 JSON、`{}`、`null`、重复键均被拒绝且重试为 0
  （`RuleAgentProviderTest.wrongFingerprintUnknownFieldsAndInvalidEnumAreRejected`、`invalidReviewRetainsUsageAndNeverRetries`）。

## 三、“独立”的确切含义（必须准确引用）

**独立 = 同一次运行内的“另一次调用”，使用同一模型、同一端点、同一客户端端口；不是不同模型。**

依据（`RuleAgentOnlineFactory` 第 25–26 行注释与实现）：
`同一run端口兼任决策/独立核验；不能另开客户端、额度或漏掉核验后回退旧门禁。`

独立性体现在四处，而非模型切换：

1. **另一次调用**：核验占用共享额度的另 1 次（`purpose=PUBLICATION_REVIEW`，`RuleAgentProviderTest.reviewUsesIndependentInputAndSharedAttemptsWithPurposeAudit`）；
2. **独立提示**：`REVIEW_SYSTEM` 与决策侧 `SYSTEM` 不同；
3. **独立输入**：核验载荷由 `inputFingerprint` 绑定，**不接收**提议者自报的相关性/完整性（防自我批准）；
4. **独立合同**：`RulePublicationProtocol` 与动作合同分离，`REVISION` 独立。

**禁止表述**：把同模型的另一次核验写成“独立模型核验”“第二模型交叉验证”。**同模型不构成模型多样性保证。**

## 四、程序发布约束（模型无法绕过）

| 约束 | 内容 | 入口 |
| --- | --- | --- |
| 同 run 核验 | 缺 `PUBLICATION_REVIEW` 或核验未通过即不发布；核验后不得回退旧门禁 | `RuleAgentRunner`、`RulePublicationGate` |
| 事实资格 | 只发布 `independentlyAnswerable` 且 `when` 条件与用户条件**逐项相等**的事实 | `RuleResultPublisher`（第 98 行） |
| 主张—证据一致 | 主张的 `factId` 必须来自本次已核验事实；跨度与操作数须与问题原文一致 | `RuleResultPublisher`、`RulePublicationProtocol` |
| 不得把故障当资料缺失 | 执行故障与资料缺口使用不同原因码，不得互相冒充 | `RuleAgentRunner`、`ReasonCode` |
| 发布前置 | 发布由独立门禁判定，模型自报“结束/通过”不构成核验通过 | `RulePublicationGate` |

## 五、边界

- 以上均为**宿主侧、本地可复现**的机制说明；**真实在线**的核验是否通过、发布是否发生，**从未执行过**
  （`T05-CLOSEOUT.md` 第 10.1 节：独立核验“仅尝试过”、发布“从未执行”）。
- 不保证普适语义正确；不保证供应商一定返回合规结构（`t05-34` 连响应头都未取得）。
