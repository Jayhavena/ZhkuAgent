import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {validateRuleAgentSnapshot} from '../../../../../agent/frontend/src/api/rule-agent-contract.js';

const base = new URL('../t05-publication-reproduction-02/', import.meta.url);
const read = name => JSON.parse(fs.readFileSync(new URL(name, base), 'utf8'));
const results = read('results.json');
const database = read('database.json');
const records = read('publication-reproduction.json');
assert.equal(results.passed, true);
assert.equal(results.testCount, 2);
assert.equal(results.sourceDrift, 0);
assert.equal(results.resourcesVerified, true);
assert.equal(results.containerExited, true);
assert.equal(results.providerAllowed, false);
assert.equal(database.connectionsAfterTests, 0);
assert.equal(database.verified, true);
assert.equal(records.length, 6);
for (const record of records) {
    validateRuleAgentSnapshot(record.outcome.snapshot);
    assert.equal(record.providerCalls, 0);
    assert.equal(record.decisionPortClosed, true);
    assert.equal(record.runtimeTerminated, true);
    assert.equal(record.outcome.snapshot.execution.providerResponseReceived, false);
    assert.equal(record.outcome.events[0].observation.status, 'SUCCEEDED');
    assert.equal(record.outcome.events[1].observation.dataLevel, 'VERIFIED_FACT');
    if (record.outcome.snapshot.taskStatus === 'STOPPED') {
        assert.equal(record.outcome.snapshot.result.items[0].claims.length, 0);
        assert.equal(record.outcome.snapshot.result.evidence.length, 0);
    }
}
const byId = Object.fromEntries(records.map(record => [record.id, record]));
assert.deepEqual(byId.C01.selectedFactIds, byId.C02.selectedFactIds);
assert.deepEqual(byId.C03.selectedFactIds, byId.C02.selectedFactIds);
assert.equal(byId.C01.topicAssessments[0].assessment.requestedTopics.length, 0);
assert.equal(byId.C03.topicAssessments[0].assessment.requestedTopics.length, 0);
assert.equal(byId.C02.outcome.snapshot.result.coverage, 'COMPLETE');
assert.equal(byId.C04.selectedFactIds.length, 1);
assert.equal(byId.C04.outcome.snapshot.result.coverage, 'COMPLETE');
assert.equal(byId.C05.selectedFactIds.length, 3);
assert.equal(byId.C05.topicAssessments.length, 2);
const union = new Set(byId.C05.topicAssessments.flatMap(item => item.declaredTopics));
assert.deepEqual([...union].sort(), ['HOURS', 'SEMESTER_CREDITS']);
for (const rule of byId.C05.topicAssessments) {
    assert.ok(rule.assessment.requestedTopics.every(topic => union.has(topic)));
    assert.ok(rule.assessment.blockingReasons.length > 0);
}
assert.deepEqual(byId.C06.selectedFactIds, ['S10/RETAKE_ONLY:public-failed-no-retake']);
const dispositions = {C01: 'GAP', C02: 'CONTROL_PASS', C03: 'GAP', C04: 'GAP', C05: 'GAP', C06: 'CONTROL_PASS'};
const summary = records.map(record => ({
    id: record.id,
    question: record.question,
    requiredBehavior: record.requiredBehavior,
    businessDisposition: dispositions[record.id],
    actualTaskStatus: record.outcome.snapshot.taskStatus,
    actualCoverage: record.outcome.snapshot.result.coverage,
    claimCount: record.outcome.snapshot.result.items[0].claims.length,
    selectedFactIds: record.selectedFactIds,
    topicAssessments: record.topicAssessments,
    actionSequence: record.outcome.events.map(event => event.action),
    rejectionReasons: record.outcome.events.filter(event => event.reason).map(event => event.reason),
    runtimeTerminated: true,
    providerCalls: 0
}));
fs.writeFileSync(new URL('case-matrix.json', import.meta.url), JSON.stringify(summary, null, 2));
// 派生副本删去只在本机有效的原文令牌；原始证据保持不变，不冒充原始输出。
const sanitized = JSON.parse(JSON.stringify(records));
function omitTokens(value) {
    if (!value || typeof value !== 'object') return;
    if ('originalToken' in value) delete value.originalToken;
    for (const child of Object.values(value)) omitTokens(child);
}
omitTokens(sanitized);
fs.writeFileSync(new URL('sanitized-reproduction.json', import.meta.url), JSON.stringify({
    derivedFrom: 't05-publication-reproduction-02/publication-reproduction.json',
    originalSha256: crypto.createHash('sha256').update(fs.readFileSync(new URL('publication-reproduction.json', base))).digest('hex'),
    removedFields: ['originalToken'],
    snapshotContractsValidatedBeforeSanitizing: true,
    records: sanitized
}, null, 2));
const verification = {
    reproductionAcceptancePassed: true,
    freshJUnitMethods: 2,
    diagnosticCases: 6,
    businessGaps: ['C01', 'C03', 'C04', 'C05'],
    controlsPassed: ['C02', 'C06'],
    knownGapKinds: ['UNKNOWN_TOPIC_AS_MISMATCH', 'PER_RULE_FULL_COVERAGE', 'ONE_SIDE_AS_COMPLETE'],
    T05Complete: false,
    productionModified: false,
    providerCalls: 0,
    providerUsage: null,
    resourcesVerified: true,
    sourceDrift: 0,
    snapshotContractsChecked: 6,
    checkedAt: new Date().toISOString()
};
fs.writeFileSync(new URL('verification.json', import.meta.url), JSON.stringify(verification, null, 2));
console.log(JSON.stringify(verification));
