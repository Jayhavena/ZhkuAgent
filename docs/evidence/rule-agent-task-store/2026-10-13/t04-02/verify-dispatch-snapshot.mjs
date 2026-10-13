import assert from 'node:assert/strict';
import fs from 'node:fs';
import {validateRuleAgentSnapshot} from '../../../../../agent/frontend/src/api/rule-agent-contract.js';

// 使用Java实际生成的失败快照，防止两端仅各自接受自己的样例。
const input = new URL('../t04-final-unit-03/dispatch-failure-snapshot.json', import.meta.url);
const snapshot = JSON.parse(fs.readFileSync(input, 'utf8'));
validateRuleAgentSnapshot(snapshot);
assert.equal(snapshot.taskStatus, 'FAILED');
assert.equal(snapshot.result.items[0].issues[0].reasonCode, 'TASK_EXECUTION_UNAVAILABLE');
assert.equal(snapshot.result.items[0].issues[0].kind, 'PROCESSING_FAILURE');
assert.equal(snapshot.execution.modelAttemptCount, 0);
const record = {passed: true, input: input.pathname, taskStatus: snapshot.taskStatus,
  reasonCode: snapshot.result.items[0].issues[0].reasonCode, modelAttemptCount: 0,
  checkedAt: new Date().toISOString()};
fs.writeFileSync(new URL('dispatch-contract-result.json', import.meta.url), JSON.stringify(record, null, 2));
console.log(JSON.stringify(record));
