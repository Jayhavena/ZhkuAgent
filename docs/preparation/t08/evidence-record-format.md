# T08 证据记录格式：T08_SAMPLE / T08_BATCH

日期：2026-10-14。性质：**T08 正式验收前的准备材料**。定义单样本与批次的 JSON 记录格式，供 [校验工具](../../../tools/t08-acceptance/README.md) 检查。本格式复用 [v3 共享合同](../design/rule-agent-contract-v3.md) 的 TaskSnapshot/ResultEnvelope/Execution 定义，并新增 T08 证据层字段（见第 4 节增量建议，**未修改合同本身**）。

## 0. 总则

1. **未知保留未知**：usage、费用、延迟分量等未知时记 `null` 并附 `"unknown": true` 语义说明；**严禁把未知记为 0**（T05 收口已确立"usage 和实际扣费未知，不记为 0"）。
2. 所有记录去凭证：不含 API Key、Cookie、完整原文 PDF、学生个人信息；owner 用哈希。
3. 模式声明优先于叙事：mode 为 `ONLINE` / `ENGINEERING_REPLAY` / `SEALED_REPLAY` 之一，与 Execution 字段互相约束（见 3.2）。
4. 合成记录必须带 `"synthetic": true`；真实证据不得带该标记。

## 1. T08_SAMPLE（单样本记录）

```json
{
  "schemaVersion": 3,
  "recordType": "T08_SAMPLE",
  "synthetic": false,
  "identity": {
    "candidateId": "t08-candidate-01",
    "sourceSha": "<候选源码全量 SHA>",
    "contractVersion": "rule-agent-contract-v3",
    "schemaVersion": 3,
    "catalogueVersion": "<审核目录版本>",
    "referenceRevision": "<事实增量版本>",
    "budgetRevision": "<预算标识，如 T05_REVIEW_BUDGET_03>",
    "promptRevision": "<提示词版本>"
  },
  "group": "G3",
  "category": "A",
  "mode": "ONLINE",
  "question": {
    "questionId": "G3-A-01",
    "text": "<问题原文>",
    "supplements": [
      { "turn": 2, "text": "<学生补充>", "continuationToken": "<继续凭据标识>" }
    ],
    "reference": {
      "answerPoints": ["<必要回答要点>"],
      "forbiddenConclusions": ["<禁止结论>"],
      "necessaryConditions": ["<必要条件>"],
      "relevantEvidence": ["<相关原文定位>"],
      "allowedGaps": ["<允许缺口>"],
      "verifiedBeforeRun": true
    }
  },
  "execution": {
    "taskId": "...", "turnId": "...", "runId": "...", "version": 3,
    "clientRequestId": "...",
    "ownerHash": "<owner(actorId,sessionNonce) 哈希，不存原文>",
    "taskStatus": "RESOLVED",
    "runState": "SUCCEEDED",
    "terminated": true,
    "startedAt": "2026-10-14T10:00:00+08:00",
    "endedAt": "2026-10-14T10:00:12+08:00",
    "actions": [
      { "step": 1, "actionId": "...", "type": "SEARCH_REVIEWED_RULES", "paramsDigest": "sha256:..." }
    ],
    "observations": [
      { "step": 1, "actionId": "...", "status": "SUCCEEDED", "reasonCode": null, "dataLevel": "CANDIDATE" }
    ],
    "result": { "<ResultEnvelope：coverage/items/claims/issues/clarification/evidenceRelations>" : null },
    "review": { "performed": true, "runId": "...", "verdict": "AGREED", "usage": { "promptTokens": null, "completionTokens": null } }
  },
  "metrics": {
    "modelAttemptCount": 4,
    "toolStepCount": 2,
    "providerResponseReceived": true,
    "replayCoverage": "NOT_APPLICABLE",
    "answerSource": "LIVE_AGENT",
    "latencyMillis": { "total": 12000, "perAttempt": [3000, 3000, 3000, 3000] },
    "usage": { "promptTokens": null, "completionTokens": null, "totalTokens": null },
    "costCny": null
  },
  "scoring": {
    "scoredBy": "<评分员标识>",
    "scoredAt": "2026-10-14T11:00:00+08:00",
    "dimensions": { "D1": 2, "D2": 2, "D3": 2, "D4": 2, "D5": 1, "D6": 2, "D7": 1, "D8": 1 },
    "outcome": "SUCCESS",
    "criticalError": null,
    "notes": "<判定理由，含未知项说明>"
  },
  "resourceExit": {
    "terminated": true,
    "terminationReason": "<真实结束原因>",
    "ownedProcessesCleaned": true,
    "databaseOperations": 0,
    "notes": "<临时容器/端口/进程清理证据指针>"
  }
}
```

### 1.1 必填与约束

| 字段 | 必填 | 约束 |
| --- | --- | --- |
| identity.sourceSha / contractVersion / schemaVersion / candidateId | 是 | 批次内必须一致（见第 3 节版本混用检查） |
| identity.catalogueVersion / referenceRevision / budgetRevision | 是 | 与 ResultEnvelope 及实际装配一致 |
| group / category | 是 | 取值见[验收矩阵](acceptance-matrix.md) |
| mode | 是 | 三选一；ONLINE 时 replayCoverage 必须 NOT_APPLICABLE、answerSource 必须 LIVE_AGENT |
| question.questionId / text | 是 | G3/G4 题目来自候选冻结后的独立报告集 |
| question.reference.verifiedBeforeRun | 是 | 运行前核对参考答案的声明 |
| execution.taskId/turnId/runId/version | 是 | 与快照一致；version 用于 CAS 追溯 |
| execution.actions / observations | ONLINE 且计数>0 时必填 | 轨迹缺失即证据不合格 |
| execution.result | taskStatus 终态时必填 | claims 必须有 evidenceRelations |
| metrics | 是 | 未知记 null；providerResponseReceived=false 时 usage/cost 必须为 null |
| scoring | 验收批次必填 | outcome 五类之一；criticalError 与 SUCCESS 互斥 |
| resourceExit | 是 | terminated 与 taskStatus 终态一致 |

条件必填/可选附加字段：

| 字段 | 何时需要 | 说明 |
| --- | --- | --- |
| simulated | 受控模拟运行 | 为 true 时 mode 不得为 ONLINE（工具报 E-MODE-006） |
| replay.inputFingerprint | mode 非 ONLINE | 回放样本须记录完整输入指纹 |
| injection | group=G4 | 注入点与预期观察，如 `{ "point": "...", "expected": "..." }` |
| exposureReference | group=G2 | 原暴露记录（批次/提交）指针 |

语义反例核对字段（第二轮新增，均为证据层扩展，见[反例文档](scoring-counterexamples.md)第 4 节）：

| 字段 | 位置 | 用途 |
| --- | --- | --- |
| observedFacts | execution | `[{id, when}]`：READ 观察到的事实快照，供 CE1/CE2 机器核对 |
| reference.answerPoints | question.reference | 运行前核对的答案要点（字符串或 `{id}`） |
| scoring.coverageMap | scoring | `{"ap-1":"sq-1"\|null}`：要点→子问题映射，null=显式未覆盖 |
| scoring.forbiddenConclusionsChecked | scoring | boolean：CE4 禁止结论人工核对声明 |
| scoring.counterexample | scoring | `{category:"CE1".."CE5", note}`：反例样本声明 |
| scoring.interception | scoring | `{caughtBy:"tool"\|"human"\|"model", findingCode?, note}`：拦截登记 |
| validatorDisposition | 记录根 | `[{code, disposition:"CONFIRMED"\|"FALSE_POSITIVE", reason}]`：工具误判登记 |

## 2. T08_BATCH（批次记录）

```json
{
  "schemaVersion": 3,
  "recordType": "T08_BATCH",
  "batchId": "t08-g3-batch-01",
  "group": "G3",
  "candidateId": "t08-candidate-01",
  "sourceSha": "<与记录一致>",
  "declared": { "sampleCount": 4, "mode": "ONLINE", "expectedQuestionIds": ["G3-A-01", "G3-A-02", "G3-A-03", "G3-A-04"] },
  "containsSynthetic": false,
  "records": [ "<T08_SAMPLE>", "..." ],
  "summary": { "total": 4, "byOutcome": { "SUCCESS": 3, "TASK_FAILURE": 1 }, "criticalErrors": [], "usageKnown": false }
}
```

批次约束：单组、单候选、单模式（除非 declared.mixed 显式说明并逐记录标注）；`expectedQuestionIds` 用于"失败样本被遗漏"检查——凡列入期望清单的题，无论成败都必须出现在 records 中；`summary.byOutcome` 合计必须等于 `records.length`。

## 3. 与合同的关系与增量建议（不自行修改合同）

合同 v3 已定义：TaskStatus/RunState、ResultEnvelope、issue.kind/reasonCode、EvidenceRelation、NextAction/ToolObservation、RequestKey/幂等、Execution（mode/modelAttemptCount/toolStepCount/providerResponseReceived/answerSource/replayCoverage）、防御预算。**以下为 T08 证据层需要而合同尚未定义的字段**，按约定记录待用户协调：

| 现有定义 | 具体问题 | 建议增量 | 影响模块 | 对应测试 |
| --- | --- | --- | --- | --- |
| Execution 无费用/令牌字段 | T08 需记录调用与用量，但供应商 usage 常缺失 | Execution 增加可选 `usage`/`cost` 字段，未知为 null，禁止默认 0 | knowledge ruleagent Execution 序列化、前端展示（不展示学生） | RuleAgentContractTest 增补用例 |
| Execution 无延迟分量 | T08 D7 维度需要 per-attempt 延迟 | 证据层 `metrics.latencyMillis`（宿主记录，非合同字段）或 Execution 增加可选 latency | 宿主证据记录器 | 校验工具 fixtures |
| 无批次/分组概念 | 四组分离统计需要组标识与期望清单 | 证据层 batch.group + expectedQuestionIds（宿主约定，非模型合同） | 证据目录规范 | 校验工具 fixtures |
| 无人工评分结构 | T08 需把五类结果与八维度落到机器可检记录 | 合同外新增 T08 evidence schema（本文件），校验工具消费 | tools/t08-acceptance | node --test 合成用例 |
| 无事实快照/覆盖映射 | CE1/CE2/CE3 需要 observedFacts 与 coverageMap 才能机器核对 | 证据层 execution.observedFacts + scoring.coverageMap（第二轮新增，见第 1.1 节） | 证据记录器、校验工具 | node --test 合成用例 |
| 预算为可变量 | 不同 budgetRevision 混用会污染统计 | identity.budgetRevision 必填；批次单预算标识 | 预算常量与测试字面量（T05 既有约定） | RuleAgentContractTest 既有断言 |

上述增量**均未实施**；在用户协调前，T08 证据按本文件记录，校验工具只检查不修改。

## 4. 目录与命名规范

- 每组独立目录：`docs/evidence/t08-acceptance/<候选ID>/<组>/<批次>/`，与[准备证据](../evidence/t08-preparation/)分开。
- 文件名：`<批次>-<组>-sample-<序号>.json`；批次含 `batch.json` 汇总。
- 原始供应商响应只保留本机脱敏摘要；轨迹与原始 outcome 不进入提交（沿用 T05 冻结声明第九节）。
