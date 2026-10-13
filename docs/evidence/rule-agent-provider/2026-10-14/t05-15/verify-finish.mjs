import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { validateRuleAgentSnapshot } from '../../../../../agent/frontend/src/api/rule-agent-contract.js';

const rawPath = new URL('../t05-finish-wire-db-green-04/publication-reproduction.json', import.meta.url);
const raw = fs.readFileSync(rawPath);
const cases = JSON.parse(raw);
const statuses = ['RESOLVED', 'RESOLVED', 'RESOLVED', 'FAILED', 'RESOLVED', 'FAILED'];
const coverage = ['COMPLETE', 'COMPLETE', 'COMPLETE', 'PARTIAL', 'COMPLETE', 'NONE'];
const claims = [2, 2, 2, 1, 3, 0];
assert.equal(cases.length, 6);
for (const [i, item] of cases.entries()) {
  const snapshot = item.outcome.snapshot;
  validateRuleAgentSnapshot(snapshot);
  assert.equal(snapshot.taskStatus, statuses[i]);
  assert.equal(snapshot.result.coverage, coverage[i]);
  assert.equal(snapshot.result.items.flatMap(x => x.claims).length, claims[i]);
  assert.equal(snapshot.result.referenceRevision, '2025.9.2');
  assert.equal(snapshot.execution.modelAttemptCount, 4);
  assert.equal(snapshot.execution.toolStepCount, 3);
  assert.equal(snapshot.execution.providerResponseReceived, false);
  assert.equal(snapshot.terminated, true);
  assert.equal(item.outcome.events.at(-1).action, 'PUBLICATION_REVIEW');
}
assert.equal(cases[3].outcome.snapshot.result.items[0].issues[0].reasonCode, 'ANSWER_COVERAGE_INCOMPLETE');
assert.equal(cases[3].outcome.snapshot.result.items[0].issues[0].kind, 'PROCESSING_FAILURE');
assert.equal(cases[5].outcome.events.at(-1).publicationReason, 'FACT_NOT_RELEVANT');
assert.equal(cases[5].outcome.snapshot.result.evidence.length, 0);

// 只对副本移除访问令牌；原始验收文件保留，摘要用于复核其身份。
const sanitized = JSON.parse(JSON.stringify(cases, (key, value) => key === 'originalToken' ? undefined : value));
fs.writeFileSync(new URL('actual-cases-sanitized.json', import.meta.url), JSON.stringify(sanitized, null, 2) + '\n');
const result = {
  passed: true, cases: 6, frontendContractAccepted: 6,
  actualProviderCalls: 0, decisionAndReview: 'SIMULATED', databaseTools: 'REAL_TEMPORARY_DATABASE',
  sourceSha256: crypto.createHash('sha256').update(raw).digest('hex'),
  rawEvidence: '../t05-finish-wire-db-green-04/publication-reproduction.json',
  sanitizedRemovedFields: ['originalToken'],
  statuses, coverage, claims,
  limitations: ['不证明自然语言核验质量', '未接入在线工厂', '未实现新核验路径ASK', '不代表T05完成']
};
fs.writeFileSync(new URL('verification.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result));
