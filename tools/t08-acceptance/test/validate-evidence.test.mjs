/**
 * T08 证据校验工具测试。
 *
 * 所有夹具均为显式标记 synthetic 的合成记录（test/fixtures/*.json），
 * 不构成任何真实证据；测试只验证校验逻辑本身，不生成、不回填真实数据。
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { validateDocument, validateRecord, runCli } from '../validate-evidence.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const fixtures = join(here, 'fixtures');

function load(name) {
  return JSON.parse(readFileSync(join(fixtures, name), 'utf8'));
}

function codes(findings, severity) {
  return findings.filter((item) => item.severity === severity).map((item) => item.code);
}

function errorCodes(findings) {
  return new Set(codes(findings, 'ERROR'));
}

test('合法 ONLINE 单样本：无 ERROR 无 WARN', () => {
  const findings = validateDocument(load('valid-online-sample.json'));
  assert.deepEqual([...errorCodes(findings)], []);
  assert.deepEqual(codes(findings, 'WARN'), []);
});

test('合法批次（含诚实缺证 UNKNOWN）：无 ERROR 无 WARN', () => {
  const findings = validateDocument(load('valid-batch.json'));
  assert.deepEqual([...errorCodes(findings)], []);
  assert.deepEqual(codes(findings, 'WARN'), []);
});

test('身份缺失：E-ID-001', () => {
  const findings = validateDocument(load('missing-identity.json'));
  assert.ok(errorCodes(findings).has('E-ID-001'));
  assert.ok(findings.filter((i) => i.code === 'E-ID-001').length >= 2, 'sourceSha 与 budgetRevision 各报一次');
});

test('版本混用：批次内多身份 E-VER-001', () => {
  const findings = validateDocument(load('version-mixing-batch.json'));
  assert.ok(errorCodes(findings).has('E-VER-001'));
});

test('模拟冒充在线：E-MODE-001/002/004/005', () => {
  const findings = validateDocument(load('replay-posing-online.json'));
  const errors = errorCodes(findings);
  assert.ok(errors.has('E-MODE-001'));
  assert.ok(errors.has('E-MODE-002'));
  assert.ok(errors.has('E-MODE-004'));
  assert.ok(errors.has('E-MODE-005'));
});

test('缺失轨迹：E-TRJ-001 与 E-TRJ-004', () => {
  const findings = validateDocument(load('missing-trajectory.json'));
  const errors = errorCodes(findings);
  assert.ok(errors.has('E-TRJ-001'));
  assert.ok(errors.has('E-TRJ-004'));
});

test('重复计数：E-DUP-001、E-DUP-004 与汇总不符', () => {
  const findings = validateDocument(load('duplicate-count-batch.json'));
  const errors = errorCodes(findings);
  assert.ok(errors.has('E-DUP-001'));
  assert.ok(errors.has('E-DUP-004'));
  assert.ok(errors.has('E-OMIT-002'));
});

test('失败样本被遗漏：E-OMIT-001 与 E-OMIT-002', () => {
  const findings = validateDocument(load('omitted-failure-batch.json'));
  const errors = errorCodes(findings);
  assert.ok(errors.has('E-OMIT-001'), '期望清单中的失败样本缺失必须报错');
  assert.ok(errors.has('E-OMIT-002'));
});

test('未知记为 0：E-UNK-001 与 W-UNK-003', () => {
  const findings = validateDocument(load('unknown-as-zero.json'));
  assert.ok(errorCodes(findings).has('E-UNK-001'));
  assert.ok(codes(findings, 'WARN').includes('W-UNK-003'));
});

test('拒答记成功：E-SCR-003 与 W-SCR-001', () => {
  const findings = validateDocument(load('success-on-refusal.json'));
  assert.ok(errorCodes(findings).has('E-SCR-003'));
  assert.ok(codes(findings, 'WARN').includes('W-SCR-001'));
});

test('合成记录未声明：E-SYN-001', () => {
  const findings = validateDocument(load('synthetic-unmarked-batch.json'));
  assert.ok(errorCodes(findings).has('E-SYN-001'));
  assert.ok(codes(findings, 'INFO').includes('I-SYN-002'));
});

test('四组混批：E-GRP-005', () => {
  const findings = validateDocument(load('mixed-group-batch.json'));
  assert.ok(errorCodes(findings).has('E-GRP-005'));
});

// ---- 第二轮：语义反例（CE1-CE5）的确定性检查 ----

test('合法语义样本（observedFacts/coverageMap/禁止结论核对齐全）：无 ERROR 无 WARN', () => {
  const findings = validateDocument(load('valid-semantic-sample.json'));
  assert.deepEqual([...errorCodes(findings)], []);
  assert.deepEqual(codes(findings, 'WARN'), []);
});

test('CE1 事实映射错误：E-SEM-001', () => {
  const findings = validateDocument(load('semantic-fact-mapping.json'));
  const errors = errorCodes(findings);
  assert.ok(errors.has('E-SEM-001'));
  assert.equal(errors.size, 1, '仅应触发目标检查');
});

test('CE2 条件遗漏：E-SEM-002', () => {
  const findings = validateDocument(load('semantic-condition-omission.json'));
  const errors = errorCodes(findings);
  assert.ok(errors.has('E-SEM-002'));
  assert.equal(errors.size, 1, '仅应触发目标检查');
});

test('CE3 问题未覆盖：E-SEM-003', () => {
  const findings = validateDocument(load('semantic-question-uncovered.json'));
  const errors = errorCodes(findings);
  assert.ok(errors.has('E-SEM-003'));
  assert.equal(errors.size, 1, '仅应触发目标检查');
});

test('CE4 范围越界待确认：W-SEM-004', () => {
  const findings = validateDocument(load('semantic-scope-overreach.json'));
  assert.deepEqual(codes(findings, 'WARN'), ['W-SEM-004']);
  assert.deepEqual([...errorCodes(findings)], []);
});

test('CE5 误拒绝待确认：W-SEM-005（零召回拒答须人工确认）', () => {
  const findings = validateDocument(load('semantic-false-refusal.json'));
  const warns = codes(findings, 'WARN');
  assert.ok(warns.includes('W-SEM-005'));
  assert.ok(warns.includes('W-SCR-004'), 'REFUSAL 样本同时触发参考答案核对提示');
  assert.deepEqual([...errorCodes(findings)], []);
});

test('反例未登记拦截方式：E-SEM-006', () => {
  const findings = validateDocument(load('semantic-counterexample-unregistered.json'));
  const errors = errorCodes(findings);
  assert.ok(errors.has('E-SEM-006'));
  assert.equal(errors.size, 1, '仅应触发目标检查');
});

test('选项参数：预算超限 E-BUD-001、SHA 不符 E-VER-003、组别不符 E-VER-006', () => {
  const base = load('valid-online-sample.json');
  const overBudget = structuredClone(base);
  overBudget.metrics.modelAttemptCount = 5;
  overBudget.metrics.latencyMillis.perAttempt = [2000, 2000, 2000, 2000, 2000];
  assert.ok(errorCodes(validateRecord(overBudget)).has('E-BUD-001'));

  assert.ok(errorCodes(validateRecord(base, { expectSourceSha: 'ffffffff' })).has('E-VER-003'));
  assert.ok(errorCodes(validateRecord(base, { expectGroup: 'G1' })).has('E-VER-006'));
  assert.ok(errorCodes(validateRecord(base, { expectBudgetRevision: 'OTHER' })).has('E-VER-008'));
});

test('G3 非在线：E-GRP-001；未声明模拟冒充在线：E-MODE-006', () => {
  const base = load('valid-online-sample.json');
  const replayInG3 = structuredClone(base);
  replayInG3.mode = 'ENGINEERING_REPLAY';
  replayInG3.metrics.modelAttemptCount = 0;
  replayInG3.metrics.providerResponseReceived = false;
  replayInG3.metrics.answerSource = 'SEALED_REPLAY';
  replayInG3.replay = { inputFingerprint: 'sha256:synthetic' };
  const g3Errors = errorCodes(validateRecord(replayInG3));
  assert.ok(g3Errors.has('E-GRP-001'));

  const simulatedOnline = structuredClone(base);
  simulatedOnline.simulated = true;
  assert.ok(errorCodes(validateRecord(simulatedOnline)).has('E-MODE-006'));
});

test('CLI（进程内）：合法批次退出 0，问题样本退出 1，--json 可解析', () => {
  // 本机禁止 spawn 子进程（EBUSY），因此直接进程内调用 runCli 并捕获 stdout。
  const chunks = [];
  const originalWrite = process.stdout.write.bind(process.stdout);
  process.stdout.write = (chunk, ...rest) => {
    chunks.push(typeof chunk === 'string' ? chunk : chunk.toString());
    return true;
  };
  try {
    const okExit = runCli([join(fixtures, 'valid-batch.json'), '--json']);
    const okOut = chunks.join('');
    chunks.length = 0;
    assert.equal(okExit, 0);
    assert.equal(JSON.parse(okOut).ok, true);

    const badExit = runCli([join(fixtures, 'missing-identity.json')]);
    const badOut = chunks.join('');
    assert.equal(badExit, 1);
    assert.match(badOut, /E-ID-001/);

    chunks.length = 0;
    const strictExit = runCli([join(fixtures, 'valid-online-sample.json'), '--strict']);
    assert.equal(strictExit, 0, '合法样本 --strict 也应通过');
  } finally {
    process.stdout.write = originalWrite;
  }
});
