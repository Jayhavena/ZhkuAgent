#!/usr/bin/env node
/**
 * T08 证据校验工具（只读检查，不修改任何证据文件）。
 *
 * 存在理由：T08 四组证据（旧回放回归/已曝光开发问题/新报告集/异常注入）一旦混用、
 * 冒充在线或漏计失败样本，整个验收结论就不可信。本工具在归档前对这些模式做机器可检断言。
 *
 * 设计约束：
 * - 只读取并报告，绝不改写、不回填、不"修复"证据；发现问题的唯一输出是 finding。
 * - 未知值规则：usage/费用等未知必须为 null；把未知记为 0 按违规处理（T05 收口已确立该口径）。
 * - 合成记录必须显式标记 synthetic；本工具自身也只用合成夹具测试，不生成任何真实证据。
 *
 * 用法：node validate-evidence.mjs <文件...> [--batch] [--expect-group G1|G2|G3|G4]
 *       [--expect-source-sha <sha>] [--expect-contract-version <v>] [--expect-budget-revision <id>]
 *       [--max-attempts <n>] [--strict] [--json]
 */

import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const GROUPS = new Set(['G1', 'G2', 'G3', 'G4']);
const CATEGORIES = new Set(['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']);
const MODES = new Set(['ONLINE', 'ENGINEERING_REPLAY', 'SEALED_REPLAY']);
const REPLAY_COVERAGE = new Set(['NOT_APPLICABLE', 'SEALED_REPLAY', 'MISS']);
const OUTCOMES = new Set(['SUCCESS', 'TASK_FAILURE', 'REFUSAL', 'UNKNOWN', 'INDETERMINATE']);
const DIMENSIONS = ['D1', 'D2', 'D3', 'D4', 'D5', 'D6', 'D7', 'D8'];
const TERMINAL_TASK_STATUS = new Set(['RESOLVED', 'STOPPED', 'FAILED']);

/** 生成一条 finding；severity: ERROR=必须修正后才可归档，WARN=须人工确认，INFO=提示。 */
function finding(severity, code, path, message) {
  return { severity, code, path, message };
}

function isPlainObject(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

// ---------------------------------------------------------------------------
// 单样本记录检查
// ---------------------------------------------------------------------------

/** R1 身份缺失：身份字段是跨批次对照的唯一凭据，缺失即无法证明"验的是哪一版"。 */
function checkIdentity(findings, record) {
  const identity = isPlainObject(record.identity) ? record.identity : {};
  const requiredStrings = [
    'candidateId',
    'sourceSha',
    'contractVersion',
    'catalogueVersion',
    'referenceRevision',
    'budgetRevision',
  ];
  for (const key of requiredStrings) {
    if (!isNonEmptyString(identity[key])) {
      findings.push(
        finding('ERROR', 'E-ID-001', `identity.${key}`, `身份字段缺失或为空：${key}（候选身份必须完整，未知也要显式登记，不得省略）`),
      );
    }
  }
  if (typeof identity.schemaVersion !== 'number' || !Number.isInteger(identity.schemaVersion)) {
    findings.push(finding('ERROR', 'E-ID-001', 'identity.schemaVersion', '身份字段缺失或类型错误：schemaVersion 必须为整数'));
  } else if (identity.schemaVersion !== 3) {
    findings.push(finding('ERROR', 'E-ID-005', 'identity.schemaVersion', `schemaVersion 期望 3，实际 ${identity.schemaVersion}`));
  }
}

/** R1b 基本字段：组/类别/模式/问题标识。 */
function checkBasicFields(findings, record) {
  if (!GROUPS.has(record.group)) {
    findings.push(finding('ERROR', 'E-ID-002', 'group', `group 必须属 G1/G2/G3/G4，实际 ${JSON.stringify(record.group)}`));
  }
  if (!CATEGORIES.has(record.category)) {
    findings.push(finding('ERROR', 'E-ID-003', 'category', `category 必须属 A-H，实际 ${JSON.stringify(record.category)}`));
  }
  if (!MODES.has(record.mode)) {
    findings.push(finding('ERROR', 'E-ID-004', 'mode', `mode 必须属 ONLINE/ENGINEERING_REPLAY/SEALED_REPLAY，实际 ${JSON.stringify(record.mode)}`));
  }
  const question = isPlainObject(record.question) ? record.question : {};
  if (!isNonEmptyString(question.questionId)) {
    findings.push(finding('ERROR', 'E-ID-006', 'question.questionId', '问题标识缺失'));
  }
  if (!isNonEmptyString(question.text)) {
    findings.push(finding('ERROR', 'E-ID-007', 'question.text', '问题原文缺失'));
  }
  if (question.reference !== undefined && question.reference?.verifiedBeforeRun !== true) {
    findings.push(finding('WARN', 'W-ID-008', 'question.reference.verifiedBeforeRun', '参考答案未声明运行前核对（G3/G4 必须为 true）'));
  }
}

/** R3 模拟冒充在线：模式与 Execution/metrics 字段互相矛盾，或模拟记录冒充分 ONLINE。 */
function checkModeConsistency(findings, record) {
  const mode = record.mode;
  if (!MODES.has(mode)) return;
  const metrics = isPlainObject(record.metrics) ? record.metrics : {};
  const execution = isPlainObject(record.execution) ? record.execution : {};

  // synthetic=true 是显式声明的测试形状（批次层另有 containsSynthetic 与 INFO 提示）；
  // 这里只拦截"未声明的模拟冒充在线"：simulated 记录不得声称 ONLINE。
  if (record.simulated === true && mode === 'ONLINE') {
    findings.push(finding('ERROR', 'E-MODE-006', 'mode', '受控模拟记录不得标记为 ONLINE，应使用 ENGINEERING_REPLAY 并显式标注模拟'));
  }

  if (mode === 'ONLINE') {
    if (metrics.providerResponseReceived !== true) {
      findings.push(finding('ERROR', 'E-MODE-001', 'metrics.providerResponseReceived', 'mode=ONLINE 但未记录供应商响应收到（不得把未收到响应记为在线成功）'));
    }
    if (typeof metrics.modelAttemptCount !== 'number' || metrics.modelAttemptCount <= 0) {
      findings.push(finding('ERROR', 'E-MODE-002', 'metrics.modelAttemptCount', 'mode=ONLINE 但模型尝试计数缺失或为 0（无法证明当次真实调用）'));
    }
    if (metrics.replayCoverage !== undefined && !REPLAY_COVERAGE.has(metrics.replayCoverage)) {
      findings.push(finding('ERROR', 'E-MODE-004', 'metrics.replayCoverage', `replayCoverage 非法：${JSON.stringify(metrics.replayCoverage)}`));
    } else if (metrics.replayCoverage !== undefined && metrics.replayCoverage !== 'NOT_APPLICABLE') {
      findings.push(finding('ERROR', 'E-MODE-004', 'metrics.replayCoverage', 'mode=ONLINE 时 replayCoverage 只能为 NOT_APPLICABLE（合同第 6 节）'));
    }
    if (metrics.answerSource === 'SEALED_REPLAY') {
      findings.push(finding('ERROR', 'E-MODE-005', 'metrics.answerSource', 'mode=ONLINE 但 answerSource=SEALED_REPLAY（回放冒充在线）'));
    }
    if (metrics.answerSource === 'VERIFIED_RULE_ONLY' && metrics.modelAttemptCount > 0) {
      findings.push(finding('ERROR', 'E-MODE-005', 'metrics.answerSource', 'answerSource=VERIFIED_RULE_ONLY 与模型尝试>0 矛盾'));
    }
  } else {
    if (typeof metrics.modelAttemptCount === 'number' && metrics.modelAttemptCount > 0) {
      findings.push(finding('ERROR', 'E-MODE-003', 'metrics.modelAttemptCount', `${mode} 模式模型尝试必须为 0，实际 ${metrics.modelAttemptCount}`));
    }
    if (metrics.providerResponseReceived === true) {
      findings.push(finding('ERROR', 'E-MODE-003', 'metrics.providerResponseReceived', `${mode} 模式不得记录供应商响应`));
    }
    if (!isPlainObject(record.replay) || !isNonEmptyString(record.replay.inputFingerprint)) {
      findings.push(finding('WARN', 'W-REP-001', 'replay.inputFingerprint', '回放样本应记录完整输入指纹，用于证明确定版本重现'));
    }
  }
}

/** R4 缺失轨迹：声称执行过却没有动作/观察/结果/核验记录。 */
function checkTrajectory(findings, record) {
  const execution = isPlainObject(record.execution) ? record.execution : {};
  const metrics = isPlainObject(record.metrics) ? record.metrics : {};
  const actions = Array.isArray(execution.actions) ? execution.actions : null;
  const observations = Array.isArray(execution.observations) ? execution.observations : null;

  if (record.mode === 'ONLINE' && typeof metrics.modelAttemptCount === 'number' && metrics.modelAttemptCount > 0) {
    if (!actions || actions.length === 0) {
      findings.push(finding('ERROR', 'E-TRJ-001', 'execution.actions', 'ONLINE 且有模型尝试，但动作轨迹缺失'));
    }
    if (!observations || observations.length === 0) {
      findings.push(finding('ERROR', 'E-TRJ-004', 'execution.observations', 'ONLINE 且有模型尝试，但工具观察记录缺失'));
    }
    if (actions && observations && actions.length !== observations.length) {
      findings.push(finding('WARN', 'W-TRJ-005', 'execution.observations', '动作与观察数量不一致：拒绝类动作也应有观察，请补充说明'));
    }
  }

  const result = isPlainObject(execution.result) ? execution.result : null;
  if (TERMINAL_TASK_STATUS.has(execution.taskStatus)) {
    if (!result || (!Array.isArray(result.items) && !Array.isArray(result.issues))) {
      findings.push(finding('ERROR', 'E-TRJ-002', 'execution.result', `taskStatus=${execution.taskStatus} 但结果封套缺失 items/issues`));
    }
  }

  // 每个主张必须至少有一条 evidenceRelation（合同第 3 节；T08 额外检查语义关联存在性）。
  const relations = result && Array.isArray(result.evidenceRelations) ? result.evidenceRelations : [];
  const relationTargets = new Set(relations.map((rel) => rel?.targetId).filter((id) => typeof id === 'string'));
  const items = result && Array.isArray(result.items) ? result.items : [];
  for (const item of items) {
    const claims = Array.isArray(item?.claims) ? item.claims : [];
    for (const claim of claims) {
      if (isNonEmptyString(claim?.id) && !relationTargets.has(claim.id)) {
        findings.push(finding('ERROR', 'E-TRJ-003', `execution.result.items[].claims[${claim.id}]`, '主张没有对应 evidenceRelation（依据关系缺失）'));
      }
    }
  }

  if (execution.review?.performed === true && !isNonEmptyString(execution.review.runId)) {
    findings.push(finding('ERROR', 'E-TRJ-005', 'execution.review.runId', '声明执行了独立核验但缺少核验 runId'));
  }
  if (execution.taskStatus === 'RESOLVED' && result?.publishedVersion !== undefined && execution.review?.performed !== true) {
    findings.push(finding('WARN', 'W-TRJ-006', 'execution.review', '已发布结果但无独立核验记录（规则路径须显式标注 answerSource）'));
  }
}

/** R5 重复计数：同一 run 重复出现、同键不同载荷、批次声明数与实际记录数不符。 */
function checkDuplicates(findings, records, batch) {
  const seenRuns = new Set();
  const seenRequests = new Map();
  const runsByQuestion = new Map();
  for (const record of records) {
    const execution = isPlainObject(record.execution) ? record.execution : {};
    const runKey = `${execution.taskId}|${execution.turnId}|${execution.runId}`;
    if (seenRuns.has(runKey)) {
      findings.push(finding('ERROR', 'E-DUP-001', 'execution.runId', `重复计数的 run：${runKey}（同一 task/turn/run 只能计一次）`));
    }
    seenRuns.add(runKey);

    const requestKey = execution.clientRequestId;
    const questionId = record.question?.questionId;
    if (isNonEmptyString(requestKey)) {
      if (seenRequests.has(requestKey) && seenRequests.get(requestKey) !== questionId) {
        findings.push(finding('ERROR', 'E-DUP-003', 'execution.clientRequestId', `clientRequestId ${requestKey} 对应不同问题（同键异载荷应按 409 处理，不得双计）`));
      }
      seenRequests.set(requestKey, questionId);
    }

    if (isNonEmptyString(questionId)) {
      const runs = runsByQuestion.get(questionId) ?? [];
      runs.push(execution.runId);
      runsByQuestion.set(questionId, runs);
    }
  }
  for (const [questionId, runs] of runsByQuestion) {
    if (runs.length > 1) {
      findings.push(finding('WARN', 'W-DUP-002', 'question.questionId', `同题多次运行（${questionId} × ${runs.length}）：有意重复须分别记录并按题聚合，无意重复请核查`));
    }
  }
  const declared = batch?.declared?.sampleCount;
  if (declared !== undefined && declared !== records.length) {
    findings.push(finding('ERROR', 'E-DUP-004', 'declared.sampleCount', `批次声明样本数 ${declared} 与实际记录数 ${records.length} 不符`));
  }
}

/** R6 失败样本被遗漏：期望清单缺失、汇总与记录不符、失败样本无归类、剔除无理由。 */
function checkOmissions(findings, records, batch) {
  const expected = batch?.declared?.expectedQuestionIds;
  if (Array.isArray(expected)) {
    const present = new Set(records.map((r) => r.question?.questionId).filter(isNonEmptyString));
    for (const questionId of expected) {
      if (!present.has(questionId)) {
        findings.push(finding('ERROR', 'E-OMIT-001', 'declared.expectedQuestionIds', `期望样本缺失：${questionId}（失败样本也必须出现在批次中，不得静默剔除）`));
      }
    }
  }
  const summary = isPlainObject(batch?.summary) ? batch.summary : null;
  if (summary) {
    const byOutcome = isPlainObject(summary.byOutcome) ? summary.byOutcome : {};
    if (summary.total !== records.length) {
      findings.push(finding('ERROR', 'E-OMIT-002', 'summary.total', `汇总 total=${summary.total} 与记录数 ${records.length} 不符`));
    }
    const summed = Object.values(byOutcome).reduce((acc, value) => acc + (typeof value === 'number' ? value : 0), 0);
    if (summary.total === records.length && summed !== records.length) {
      findings.push(finding('ERROR', 'E-OMIT-003', 'summary.byOutcome', `byOutcome 合计 ${summed} 与记录数 ${records.length} 不符`));
    }
    const counted = {};
    for (const record of records) {
      const outcome = record.scoring?.outcome;
      if (!OUTCOMES.has(outcome)) {
        findings.push(finding('ERROR', 'E-OMIT-004', 'records[].scoring.outcome', `记录缺少合法 outcome：${JSON.stringify(outcome)}`));
        continue;
      }
      counted[outcome] = (counted[outcome] ?? 0) + 1;
    }
    for (const [outcome, count] of Object.entries(counted)) {
      if (byOutcome[outcome] !== undefined && byOutcome[outcome] !== count) {
        findings.push(finding('ERROR', 'E-OMIT-005', 'summary.byOutcome', `byOutcome.${outcome}=${byOutcome[outcome]} 与记录分布 ${count} 不符`));
      }
    }
  }
  for (const exclusion of batch?.exclusions ?? []) {
    if (!isNonEmptyString(exclusion?.reason)) {
      findings.push(finding('ERROR', 'E-OMIT-006', 'exclusions', '样本被剔除但未登记理由'));
    }
  }
  for (const record of records) {
    const status = record.execution?.taskStatus;
    if ((status === 'FAILED' || status === 'STOPPED') && !OUTCOMES.has(record.scoring?.outcome)) {
      findings.push(finding('ERROR', 'E-OMIT-007', 'records[].scoring.outcome', `taskStatus=${status} 的样本必须评分归类，不得遗漏`));
    }
  }
}

/** R7 未知值不得记为 0。 */
function checkUnknowns(findings, record) {
  const metrics = isPlainObject(record.metrics) ? record.metrics : {};
  const usage = isPlainObject(metrics.usage) ? metrics.usage : {};
  const usageKeys = ['promptTokens', 'completionTokens', 'totalTokens'];
  if (metrics.providerResponseReceived === true) {
    const allZero = usageKeys.every((key) => usage[key] === 0);
    const anyZero = usageKeys.some((key) => usage[key] === 0);
    if (allZero) {
      findings.push(finding('ERROR', 'E-UNK-001', 'metrics.usage', '供应商已响应但 usage 全为 0：疑似把未知记为 0，应改 null 并附说明'));
    } else if (anyZero) {
      findings.push(finding('WARN', 'W-UNK-002', 'metrics.usage', 'usage 分量出现 0 而其他分量未知：请确认是真实值'));
    }
    if (metrics.costCny === 0) {
      findings.push(finding('WARN', 'W-UNK-003', 'metrics.costCny', '费用记为 0 而用量未知：疑似把未知记为 0，应改 null'));
    }
  } else {
    for (const key of usageKeys) {
      if (usage[key] !== undefined && usage[key] !== null) {
        findings.push(finding('ERROR', 'E-UNK-004', `metrics.usage.${key}`, '未收到供应商响应时 usage 必须为 null，不得记录数值'));
      }
    }
    if (metrics.costCny !== undefined && metrics.costCny !== null) {
      findings.push(finding('WARN', 'W-UNK-005', 'metrics.costCny', '未收到响应但记录了费用：超时可能计费，须附计费依据'));
    }
  }
}

/** R2b 组别语义：G3 必须在线；G1 不得计在线；G2/G4 应有附加登记。 */
function checkGroupSemantics(findings, record) {
  if (record.group === 'G3' && record.mode !== 'ONLINE') {
    findings.push(finding('ERROR', 'E-GRP-001', 'mode', 'G3 新报告集必须 ONLINE 执行，回放/模拟不得计入'));
  }
  if (record.group === 'G1' && record.mode === 'ONLINE') {
    findings.push(finding('ERROR', 'E-GRP-002', 'mode', 'G1 旧回放回归不得标记 ONLINE'));
  }
  if (record.group === 'G2' && !isNonEmptyString(record.exposureReference)) {
    findings.push(finding('WARN', 'W-GRP-003', 'exposureReference', 'G2 已曝光问题应引用原暴露记录（批次/提交）'));
  }
  if (record.group === 'G4' && !isPlainObject(record.injection)) {
    findings.push(finding('WARN', 'W-GRP-004', 'injection', 'G4 异常注入样本应记录注入点与预期观察'));
  }
}

/**
 * R10 语义反例的确定性部分（对应 docs/preparation/t08/scoring-counterexamples.md）。
 * 只检查"字段层面可判定"的部分；语义正确性仍由人工/模型裁判评价（W-SEM-001/003/004/005 即为此设）。
 */
function checkSemanticCounterexamples(findings, record) {
  const execution = isPlainObject(record.execution) ? record.execution : {};
  const scoring = isPlainObject(record.scoring) ? record.scoring : null;
  const result = isPlainObject(execution.result) ? execution.result : null;
  const items = result && Array.isArray(result.items) ? result.items : [];
  const claims = items.flatMap((item) => (Array.isArray(item?.claims) ? item.claims : []));
  const claimsWithFact = claims.filter((claim) => isNonEmptyString(claim?.factId));
  const observedFacts = Array.isArray(execution.observedFacts) ? execution.observedFacts : null;

  // CE1 引用身份正确但事实映射错误：主张引用的 factId 必须来自本次运行观察到的事实集合。
  if (claimsWithFact.length > 0) {
    if (observedFacts === null) {
      findings.push(finding('WARN', 'W-SEM-001', 'execution.observedFacts', '主张引用 factId 但未记录 observedFacts 快照：无法机器核对事实映射，须人工核对'));
    } else {
      const observed = new Set(observedFacts.map((fact) => fact?.id).filter(isNonEmptyString));
      for (const claim of claimsWithFact) {
        if (!observed.has(claim.factId)) {
          findings.push(finding('ERROR', 'E-SEM-001', `execution.result.items[].claims[${claim.factId}]`, `主张引用的 factId 不在本次运行观察到的事实集合中：${claim.factId}（引用身份存在不等于映射正确）`));
        }
      }
    }
  }

  // CE2 条件遗漏：事实声明的 when 条件键必须在主张中原样保留。
  if (observedFacts !== null) {
    const whenByFact = new Map();
    for (const fact of observedFacts) {
      if (isNonEmptyString(fact?.id) && isPlainObject(fact.when)) whenByFact.set(fact.id, fact.when);
    }
    for (const claim of claimsWithFact) {
      const factWhen = whenByFact.get(claim.factId);
      if (!factWhen) continue;
      const claimWhen = isPlainObject(claim.when) ? claim.when : {};
      const missing = Object.keys(factWhen).filter((key) => !(key in claimWhen));
      if (missing.length > 0) {
        findings.push(finding('ERROR', 'E-SEM-002', `execution.result.items[].claims[${claim.factId}].when`, `主张遗漏事实条件：${missing.join(',')}（条件缺失会改变结论适用面）`));
      }
    }
  }

  // CE3 问题未覆盖：参考答案要点必须逐条映射到子问题；未覆盖也要显式登记，不得静默省略。
  const reference = isPlainObject(record.question?.reference) ? record.question.reference : null;
  const answerPoints = reference && Array.isArray(reference.answerPoints) ? reference.answerPoints : null;
  if (answerPoints && answerPoints.length > 0) {
    const coverageMap = scoring && isPlainObject(scoring.coverageMap) ? scoring.coverageMap : null;
    if (!coverageMap) {
      findings.push(finding('WARN', 'W-SEM-003', 'scoring.coverageMap', '有 answerPoints 但未登记 coverageMap：无法机器核对问题覆盖，须人工核对'));
    } else {
      for (const point of answerPoints) {
        const id = isPlainObject(point) ? point.id : point;
        if (isNonEmptyString(id) && !(id in coverageMap)) {
          findings.push(finding('ERROR', 'E-SEM-003', 'scoring.coverageMap', `答案要点未登记覆盖映射：${id}（未覆盖也必须显式登记，不得静默省略）`));
        }
      }
    }
  }

  // CE4 范围越界待确认：有禁止结论的样本记为成功前，必须显式声明核对过禁止结论。
  const forbidden = reference && Array.isArray(reference.forbiddenConclusions) ? reference.forbiddenConclusions : [];
  if (scoring && forbidden.length > 0 && scoring.outcome === 'SUCCESS' && scoring.forbiddenConclusionsChecked !== true) {
    findings.push(finding('WARN', 'W-SEM-004', 'scoring.forbiddenConclusionsChecked', '存在禁止结论但成功样本未声明已核对：范围越界须人工确认'));
  }

  // CE5 误拒绝待确认：拒答若仅依据零召回/无观察，须人工确认真实缺证而非漏检索或漏映射。
  if (scoring && scoring.outcome === 'REFUSAL') {
    const observations = Array.isArray(execution.observations) ? execution.observations : [];
    const anyVerified = observations.some((o) => o?.dataLevel === 'VERIFIED_FACT' || o?.dataLevel === 'VERIFIED_EVIDENCE');
    const allNoMatch = observations.length > 0 && observations.every((o) => o?.status === 'NO_MATCH');
    if (!anyVerified && (allNoMatch || observations.length === 0)) {
      findings.push(finding('WARN', 'W-SEM-005', 'scoring.outcome', '拒答仅依据零召回/无观察：须人工确认真实缺证，零召回不等于资料没有规定'));
    }
  }

  // 反例样本必须登记拦截方式（谁发现的、哪个 finding 码）；用于分别统计漏拦截与误拒绝。
  const counterexample = scoring && isPlainObject(scoring.counterexample) ? scoring.counterexample : null;
  if (counterexample) {
    if (!isNonEmptyString(counterexample.category)) {
      findings.push(finding('ERROR', 'E-SEM-006', 'scoring.counterexample.category', '反例样本缺少类别（CE1-CE5）'));
    }
    const interception = isPlainObject(scoring.interception) ? scoring.interception : null;
    if (!interception || !isNonEmptyString(interception.caughtBy)) {
      findings.push(finding('ERROR', 'E-SEM-006', 'scoring.interception', '反例样本必须登记拦截方式（tool/human/model）'));
    } else if (interception.caughtBy === 'tool' && !isNonEmptyString(interception.findingCode)) {
      findings.push(finding('ERROR', 'E-SEM-006', 'scoring.interception.findingCode', '工具拦截必须登记 finding 码'));
    }
    if (!['G2', 'G4'].includes(record.group)) {
      findings.push(finding('WARN', 'W-SEM-008', 'group', '反例样本应归入 G2（已曝光）或 G4（异常注入）组'));
    }
  }

  // 校验工具自身的误判登记：FALSE_POSITIVE 必须附理由，用于校准工具（不放任工具权威）。
  const dispositions = Array.isArray(record.validatorDisposition) ? record.validatorDisposition : [];
  for (const disposition of dispositions) {
    if (disposition?.disposition === 'FALSE_POSITIVE' && !isNonEmptyString(disposition.reason)) {
      findings.push(finding('ERROR', 'E-SEM-007', 'validatorDisposition', '校验误判（FALSE_POSITIVE）必须附理由'));
    }
  }
}

/** R8 评分与关键错误。 */
function checkScoring(findings, record) {
  const scoring = isPlainObject(record.scoring) ? record.scoring : null;
  if (!scoring) {
    findings.push(finding('WARN', 'W-SCR-000', 'scoring', '验收批次样本缺少评分记录'));
    return;
  }
  if (!OUTCOMES.has(scoring.outcome)) {
    findings.push(finding('ERROR', 'E-SCR-001', 'scoring.outcome', `outcome 必须属五类结果，实际 ${JSON.stringify(scoring.outcome)}`));
  }
  const dimensions = isPlainObject(scoring.dimensions) ? scoring.dimensions : {};
  for (const dimension of DIMENSIONS) {
    if (![0, 1, 2].includes(dimensions[dimension])) {
      findings.push(finding('ERROR', 'E-SCR-002', `scoring.dimensions.${dimension}`, `维度 ${dimension} 必须为 0/1/2`));
    }
  }
  if (isNonEmptyString(scoring.criticalError) && scoring.outcome === 'SUCCESS') {
    findings.push(finding('ERROR', 'E-SCR-003', 'scoring.criticalError', '存在关键错误不得记为 SUCCESS'));
  }
  const coverage = record.execution?.result?.coverage;
  if (scoring.outcome === 'SUCCESS' && coverage === 'NONE') {
    findings.push(finding('WARN', 'W-SCR-001', 'scoring.outcome', 'SUCCESS 但 coverage=NONE：须说明是诚实缺证且参考答案认可，防止把可答题拒答记为成功'));
  }
  if (scoring.outcome === 'SUCCESS' && (record.execution?.taskStatus === 'FAILED' || record.execution?.taskStatus === 'STOPPED')) {
    findings.push(finding('WARN', 'W-SCR-002', 'scoring.outcome', 'SUCCESS 与 FAILED/STOPPED 任务状态矛盾，须说明'));
  }
  if (scoring.outcome === 'REFUSAL' && isPlainObject(record.question?.reference)) {
    findings.push(finding('WARN', 'W-SCR-004', 'scoring.outcome', 'REFUSAL 样本：若参考答案判定可答，按关键错误第 6 条（评分舞弊）处理'));
  }
}

/** R9 资源退出与预算/延迟。 */
function checkResourcesAndBudget(findings, record, options) {
  const exit = isPlainObject(record.resourceExit) ? record.resourceExit : null;
  if (!exit) {
    findings.push(finding('WARN', 'W-EXIT-001', 'resourceExit', '缺少资源退出记录'));
  } else {
    if (TERMINAL_TASK_STATUS.has(record.execution?.taskStatus) && exit.terminated !== true) {
      findings.push(finding('WARN', 'W-EXIT-002', 'resourceExit.terminated', '终态任务未确认资源退出'));
    }
    if (typeof exit.databaseOperations === 'number' && exit.databaseOperations > 0) {
      findings.push(finding('WARN', 'W-EXIT-003', 'resourceExit.databaseOperations', '记录了数据库写操作：T08 只应使用专用临时库，须说明'));
    }
  }
  const metrics = isPlainObject(record.metrics) ? record.metrics : {};
  const maxAttempts = options.maxAttempts ?? 4;
  if (typeof metrics.modelAttemptCount === 'number' && metrics.modelAttemptCount > maxAttempts) {
    findings.push(finding('ERROR', 'E-BUD-001', 'metrics.modelAttemptCount', `模型尝试 ${metrics.modelAttemptCount} 超过次数上限 ${maxAttempts}（预算变更须有授权与新 budgetRevision）`));
  }
  const latency = isPlainObject(metrics.latencyMillis) ? metrics.latencyMillis : null;
  if (latency && Array.isArray(latency.perAttempt) && typeof metrics.modelAttemptCount === 'number') {
    if (latency.perAttempt.length !== metrics.modelAttemptCount) {
      findings.push(finding('WARN', 'W-LAT-001', 'metrics.latencyMillis.perAttempt', 'perAttempt 数量与模型尝试数不一致'));
    }
  }
  if (record.mode === 'ONLINE' && !latency) {
    findings.push(finding('WARN', 'W-LAT-002', 'metrics.latencyMillis', 'ONLINE 样本缺少延迟记录'));
  }
}

// ---------------------------------------------------------------------------
// 单样本入口
// ---------------------------------------------------------------------------

export function validateRecord(record, options = {}) {
  const findings = [];
  if (!isPlainObject(record)) {
    findings.push(finding('ERROR', 'E-DOC-001', '$', '记录不是 JSON 对象'));
    return findings;
  }
  if (record.recordType !== 'T08_SAMPLE') {
    findings.push(finding('ERROR', 'E-DOC-002', 'recordType', `recordType 必须为 T08_SAMPLE，实际 ${JSON.stringify(record.recordType)}`));
  }
  if (record.synthetic === true) {
    findings.push(finding('INFO', 'I-SYN-001', 'synthetic', '合成记录：不得作为真实证据统计'));
  }
  checkIdentity(findings, record);
  checkBasicFields(findings, record);
  checkModeConsistency(findings, record);
  checkTrajectory(findings, record);
  checkGroupSemantics(findings, record);
  checkUnknowns(findings, record);
  checkScoring(findings, record);
  checkSemanticCounterexamples(findings, record);
  checkResourcesAndBudget(findings, record, options);
  if (options.expectSourceSha && record.identity?.sourceSha !== options.expectSourceSha) {
    findings.push(finding('ERROR', 'E-VER-003', 'identity.sourceSha', `sourceSha 与预期 ${options.expectSourceSha} 不符`));
  }
  if (options.expectContractVersion && record.identity?.contractVersion !== options.expectContractVersion) {
    findings.push(finding('ERROR', 'E-VER-007', 'identity.contractVersion', `contractVersion 与预期 ${options.expectContractVersion} 不符`));
  }
  if (options.expectBudgetRevision && record.identity?.budgetRevision !== options.expectBudgetRevision) {
    findings.push(finding('ERROR', 'E-VER-008', 'identity.budgetRevision', `budgetRevision 与预期 ${options.expectBudgetRevision} 不符`));
  }
  if (options.expectGroup && record.group !== options.expectGroup) {
    findings.push(finding('ERROR', 'E-VER-006', 'group', `group 与预期 ${options.expectGroup} 不符`));
  }
  return findings;
}

// ---------------------------------------------------------------------------
// 批次入口
// ---------------------------------------------------------------------------

export function validateBatch(batch, options = {}) {
  const findings = [];
  if (!isPlainObject(batch)) {
    findings.push(finding('ERROR', 'E-DOC-001', '$', '批次不是 JSON 对象'));
    return findings;
  }
  if (batch.recordType !== 'T08_BATCH') {
    findings.push(finding('ERROR', 'E-DOC-002', 'recordType', `recordType 必须为 T08_BATCH，实际 ${JSON.stringify(batch.recordType)}`));
  }
  const records = Array.isArray(batch.records) ? batch.records : [];
  if (records.length === 0) {
    findings.push(finding('ERROR', 'E-BAT-002', 'records', '批次没有记录'));
    return findings;
  }

  // R2 版本/身份混用：一个批次只允许单一候选身份（T05 已确立"不同配置不得混为可靠性样本"）。
  const identities = new Set();
  for (const record of records) {
    const identity = isPlainObject(record.identity) ? record.identity : {};
    identities.add(
      [identity.candidateId, identity.sourceSha, identity.contractVersion, identity.schemaVersion, identity.catalogueVersion, identity.referenceRevision, identity.budgetRevision].join('|'),
    );
  }
  if (identities.size > 1) {
    findings.push(finding('ERROR', 'E-VER-001', 'records[].identity', `批次内存在 ${identities.size} 种不同身份（候选/源码SHA/合同/目录/预算必须单一）`));
  }
  for (const record of records) {
    const identity = isPlainObject(record.identity) ? record.identity : {};
    if (isNonEmptyString(batch.candidateId) && identity.candidateId !== batch.candidateId) {
      findings.push(finding('ERROR', 'E-VER-002', 'records[].identity.candidateId', '批次头 candidateId 与记录不一致'));
    }
    if (isNonEmptyString(batch.sourceSha) && identity.sourceSha !== batch.sourceSha) {
      findings.push(finding('ERROR', 'E-VER-002', 'records[].identity.sourceSha', '批次头 sourceSha 与记录不一致'));
    }
  }

  // 组别纯度：四组必须分开统计，一个批次不得混组。
  const groups = new Set(records.map((record) => record.group));
  if (groups.size > 1) {
    findings.push(finding('ERROR', 'E-GRP-005', 'records[].group', `批次内混用多个组（${[...groups].join(',')}）：四组证据必须分开归档统计`));
  }
  const modes = new Set(records.map((record) => record.mode));
  if (modes.size > 1 && batch.declared?.mixedModes !== true) {
    findings.push(finding('WARN', 'W-MODE-002', 'records[].mode', `批次内混合模式（${[...modes].join(',')}）且未声明 declared.mixedModes`));
  }

  // 合成标记：含合成记录的批次必须显式声明，防止合成数据混入真实统计。
  const hasSynthetic = records.some((record) => record.synthetic === true);
  if (hasSynthetic) {
    if (batch.containsSynthetic !== true) {
      findings.push(finding('ERROR', 'E-SYN-001', 'containsSynthetic', '批次含合成记录但未声明 containsSynthetic'));
    }
    findings.push(finding('INFO', 'I-SYN-002', 'containsSynthetic', '本批次含合成记录：仅供工具测试，不得作为正式验收证据'));
  }

  if (options.expectGroup && batch.group !== options.expectGroup) {
    findings.push(finding('ERROR', 'E-VER-006', 'group', `批次 group 与预期 ${options.expectGroup} 不符`));
  }
  if (options.expectSourceSha && batch.sourceSha !== options.expectSourceSha) {
    findings.push(finding('ERROR', 'E-VER-003', 'sourceSha', `批次 sourceSha 与预期 ${options.expectSourceSha} 不符`));
  }
  if (options.expectContractVersion && records.some((r) => r.identity?.contractVersion !== options.expectContractVersion)) {
    findings.push(finding('ERROR', 'E-VER-007', 'records[].identity.contractVersion', `记录 contractVersion 与预期 ${options.expectContractVersion} 不符`));
  }
  if (options.expectBudgetRevision && records.some((r) => r.identity?.budgetRevision !== options.expectBudgetRevision)) {
    findings.push(finding('ERROR', 'E-VER-008', 'records[].identity.budgetRevision', `记录 budgetRevision 与预期 ${options.expectBudgetRevision} 不符`));
  }

  for (const record of records) {
    findings.push(...validateRecord(record, options));
  }
  checkDuplicates(findings, records, batch);
  checkOmissions(findings, records, batch);
  return findings;
}

/** 按 recordType 自动分派；同时兼容无 recordType 的裸数组批次。 */
export function validateDocument(document, options = {}) {
  if (Array.isArray(document)) {
    return validateBatch({ recordType: 'T08_BATCH', records: document }, options);
  }
  if (isPlainObject(document) && document.recordType === 'T08_BATCH') {
    return validateBatch(document, options);
  }
  return validateRecord(document, options);
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

function parseArgs(argv) {
  const options = { files: [], strict: false, json: false };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    const next = () => {
      index += 1;
      if (index >= argv.length) throw new Error(`参数 ${arg} 缺少值`);
      return argv[index];
    };
    switch (arg) {
      case '--batch':
        options.forceBatch = true;
        break;
      case '--expect-group':
        options.expectGroup = next();
        break;
      case '--expect-source-sha':
        options.expectSourceSha = next();
        break;
      case '--expect-contract-version':
        options.expectContractVersion = next();
        break;
      case '--expect-budget-revision':
        options.expectBudgetRevision = next();
        break;
      case '--max-attempts':
        options.maxAttempts = Number.parseInt(next(), 10);
        break;
      case '--strict':
        options.strict = true;
        break;
      case '--json':
        options.json = true;
        break;
      case '--help':
      case '-h':
        options.help = true;
        break;
      default:
        if (arg.startsWith('--')) throw new Error(`未知参数：${arg}`);
        options.files.push(arg);
    }
  }
  return options;
}

const USAGE = `用法：node validate-evidence.mjs <文件...> [选项]
  --batch                      强制按批次解析（默认按 recordType 自动识别）
  --expect-group G1|G2|G3|G4   期望组别，不符即报错
  --expect-source-sha <sha>    期望候选源码 SHA
  --expect-contract-version <v>
  --expect-budget-revision <id>
  --max-attempts <n>           每 run 模型尝试次数上限（默认 4）
  --strict                     将 WARN 也视为不通过
  --json                       机器可读输出
退出码：0=通过；1=存在 ERROR（--strict 时含 WARN）；2=用法或文件错误。`;

export function runCli(argv) {
  let options;
  try {
    options = parseArgs(argv);
  } catch (error) {
    process.stderr.write(`${error.message}\n${USAGE}\n`);
    return 2;
  }
  if (options.help || options.files.length === 0) {
    process.stdout.write(`${USAGE}\n`);
    return options.help ? 0 : 2;
  }
  const allFindings = [];
  for (const file of options.files) {
    let document;
    try {
      document = JSON.parse(readFileSync(file, 'utf8'));
    } catch (error) {
      allFindings.push(finding('ERROR', 'E-FILE-001', file, `无法读取或解析 JSON：${error.message}`));
      continue;
    }
    const findings = options.forceBatch && !Array.isArray(document) && document?.recordType !== 'T08_BATCH'
      ? validateBatch({ recordType: 'T08_BATCH', records: [document] }, options)
      : validateDocument(document, options);
    for (const item of findings) allFindings.push({ ...item, file });
  }
  const hasError = allFindings.some((item) => item.severity === 'ERROR');
  const hasWarn = allFindings.some((item) => item.severity === 'WARN');
  if (options.json) {
    process.stdout.write(`${JSON.stringify({ ok: !hasError && !(options.strict && hasWarn), findings: allFindings }, null, 2)}\n`);
  } else {
    for (const item of allFindings) {
      process.stdout.write(`[${item.severity}] ${item.code} ${item.file ? `${item.file} ` : ''}${item.path}: ${item.message}\n`);
    }
    process.stdout.write(`\n合计：ERROR ${allFindings.filter((i) => i.severity === 'ERROR').length}，WARN ${allFindings.filter((i) => i.severity === 'WARN').length}，INFO ${allFindings.filter((i) => i.severity === 'INFO').length}\n`);
  }
  if (hasError) return 1;
  if (options.strict && hasWarn) return 1;
  return 0;
}

// 直接执行时才启动 CLI；被 import（如测试）时不触发。Windows 盘符路径必须用 pathToFileURL 规范化。
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = runCli(process.argv.slice(2));
}
