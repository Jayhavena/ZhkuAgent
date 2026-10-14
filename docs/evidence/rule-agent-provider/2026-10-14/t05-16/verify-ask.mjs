import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { validateRuleAgentSnapshot } from '../../../../../agent/frontend/src/api/rule-agent-contract.js';

const phase='../t05-ask-wire-db-green-05/';
const read=name=>{
  const raw=fs.readFileSync(new URL(phase+name,import.meta.url));
  return {cases:JSON.parse(raw),sha256:crypto.createHash('sha256').update(raw).digest('hex')};
};
const ask=read('actual-ask-flow.json');
const finish=read('publication-reproduction.json');
const frontendRaw=fs.readFileSync(new URL('frontend-green-t05-ask-wire-front-green-06.log',import.meta.url));
const frontendLog=frontendRaw.toString('utf8');
const frontendExecution=JSON.parse(fs.readFileSync(new URL('frontend-green-t05-ask-wire-front-green-06.json',import.meta.url)));
assert.equal(frontendExecution.exitCode,0);
assert.equal(frontendExecution.resourcesClosed,true);
assert.match(frontendLog,/ℹ tests 84/);
assert.match(frontendLog,/ℹ pass 84/);
assert.match(frontendLog,/ℹ fail 0/);
assert.equal(ask.cases.length,3);
assert.equal(finish.cases.length,6);
for(const row of [...ask.cases,...finish.cases]){
  const snapshot=row.outcome.snapshot;
  validateRuleAgentSnapshot(snapshot);
  assert.equal(snapshot.execution.modelAttemptCount,4);
  assert.equal(snapshot.execution.providerResponseReceived,false);
  assert.equal(snapshot.terminated,true);
  assert.equal(snapshot.result.referenceRevision,'2025.9.2');
  assert.equal(row.providerCalls,0);
}
for(const [index,row] of ask.cases.entries()){
  const x=row.outcome.snapshot;
  const q=x.result.items[0];
  assert.equal(x.taskStatus,index===2?'RESOLVED':'WAITING_USER');
  assert.equal(q.claims.length,index===0?0:1);
  assert.equal(row.decisionClosed,true);
  assert.equal(row.reviewClosed,true);
  assert.equal(x.execution.toolStepCount,3);
  assert.equal(row.outcome.events.at(-1).action,'PUBLICATION_REVIEW');
  if(index<2){
    assert.equal(q.clarification.length,1);
    assert.equal(q.clarification[0].conditionId,'courseType');
    assert.ok(q.issues.some(i=>i.reasonCode==='MISSING_CONDITION'));
    assert.ok(x.continuationToken);
    assert.ok(x.result.evidenceRelations.some(r=>r.role==='CLARIFICATION'));
  }else{
    assert.equal(x.version,2);
    assert.equal(q.clarification.length,0);
    assert.equal(x.continuationToken,null);
    assert.equal(x.result.coverage,'COMPLETE');
    assert.equal(q.claims[0].when.courseType,'PUBLIC');
  }
}
assert.equal(ask.cases[0].outcome.snapshot.taskId,ask.cases[2].outcome.snapshot.taskId);
assert.equal(ask.cases[1].outcome.snapshot.result.coverage,'PARTIAL');
assert.equal(ask.cases[1].outcome.snapshot.result.items[0].claims[0].factId,'T02/ARTICLE4:professional-elective-study-arrangement');
assert.ok(!ask.cases[1].outcome.snapshot.result.items[0].claims.some(c=>c.factId.endsWith('public-elective-self-selection')));
assert.equal(finish.cases[3].outcome.snapshot.result.items[0].issues[0].reasonCode,'ANSWER_COVERAGE_INCOMPLETE');
assert.equal(finish.cases[5].outcome.events.at(-1).publicationReason,'FACT_NOT_RELEVANT');
// 副本去除访问令牌；不修改原始验收记录，SHA绑定原始文件。
const sanitized=JSON.parse(JSON.stringify({ask:ask.cases,finish:finish.cases},(key,value)=>
  ['originalToken','continuationToken'].includes(key)?undefined:value));
fs.writeFileSync(new URL('actual-flows-sanitized.json',import.meta.url),JSON.stringify(sanitized,null,2)+'\n');
const report={passed:true,actualJavaSnapshotsAcceptedByFrontend:9,askCases:3,finishCases:6,
  frontendRegression:{tests:84,pass:84,fail:0,sourceLogSha256:crypto.createHash('sha256').update(frontendRaw).digest('hex')},
  originalHashes:{'actual-ask-flow.json':ask.sha256,'publication-reproduction.json':finish.sha256},
  removedFields:['originalToken','continuationToken'],actualProviderCalls:0,
  inputs:'REAL_REVIEWED_2025_TEXT',decisionAndReview:'SIMULATED',
  continuationScope:'Same task/version2 Input manually constructed; no TaskStore continuation-token test',
  assertions:['necessary clarification','relevant known claim retained','unrelated read background excluded',
    'provided condition leads to FINISH without repeated ASK','ports closed','within four logical calls'],
  t05Complete:false};
fs.writeFileSync(new URL('verification.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report));
