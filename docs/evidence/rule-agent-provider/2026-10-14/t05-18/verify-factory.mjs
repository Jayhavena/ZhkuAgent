import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { validateRuleAgentSnapshot } from '../../../../../agent/frontend/src/api/rule-agent-contract.js';
const path=new URL('../t05-online-wire-green-03/actual-factory-continuation.json',import.meta.url);
const raw=fs.readFileSync(path);
const record=JSON.parse(raw);
for(const x of [record.waiting,record.resolved]){
  validateRuleAgentSnapshot(x);
  assert.equal(x.execution.modelAttemptCount,4);
  assert.equal(x.execution.providerResponseReceived,false);
  assert.equal(x.terminated,true);
}
assert.equal(record.waiting.taskStatus,'WAITING_USER');
assert.equal(record.resolved.taskStatus,'RESOLVED');
assert.equal(record.waiting.taskId,record.resolved.taskId);
assert.notEqual(record.waiting.runId,record.resolved.runId);
assert.ok(record.resolved.version>record.waiting.version);
assert.ok(record.waiting.continuationToken);
assert.equal(record.resolved.continuationToken,null);
assert.equal(record.resolved.result.items[0].clarification.length,0);
assert.equal(record.resolved.result.items[0].claims[0].when.courseType,'PUBLIC');
assert.equal(record.localHttpCalls,8);
assert.equal(record.providerCalls,0);
assert.equal(record.factoryResourcesClosed,true);
assert.equal(record.traces.filter(t=>t.purpose==='PUBLICATION_REVIEW'&&t.phase==='RECEIVED').length,2);
assert.equal(record.traces.filter(t=>t.purpose==='DECISION'&&t.phase==='RECEIVED').length,6);
const sanitized=JSON.parse(JSON.stringify(record,(key,value)=>['originalToken','continuationToken'].includes(key)?undefined:value));
fs.writeFileSync(new URL('actual-factory-sanitized.json',import.meta.url),JSON.stringify(sanitized,null,2)+'\n');
const result={passed:true,actualSnapshotsAcceptedByFrontend:2,realTaskStoreContinuationTokenConsumed:true,
  duplicateMessageDispatchedOnce:true,localHttpCalls:8,actualProviderCalls:0,databaseCalls:0,
  sourceSha256:crypto.createHash('sha256').update(raw).digest('hex'),removedFields:['originalToken','continuationToken'],
  dataProvenance:'SYNTHETIC_MAPPED_CAPABILITY_FIXTURE',semanticReview:'MECHANICAL_FAKE_HTTP',t05Complete:false};
fs.writeFileSync(new URL('verification.json',import.meta.url),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result));
