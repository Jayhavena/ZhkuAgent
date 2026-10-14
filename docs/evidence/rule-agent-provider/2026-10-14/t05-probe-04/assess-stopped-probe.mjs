import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {validateRuleAgentSnapshot} from '../../../../../agent/frontend/src/api/rule-agent-contract.js';
const dir=new URL('../t05-live-48509ea06b12415daee4f1e842fe774c/',import.meta.url);
const traceRaw=fs.readFileSync(new URL('provider-traces.jsonl',dir));
const traces=traceRaw.toString('utf8').trim().split(/\r?\n/).map(JSON.parse);
const file=fs.readdirSync(dir).find(x=>x.endsWith('-outcome.json'));
const outcomeRaw=fs.readFileSync(new URL(file,dir));
const outcome=JSON.parse(outcomeRaw);
const result=JSON.parse(fs.readFileSync(new URL('probe-result.json',dir)));
const exit=JSON.parse(fs.readFileSync(new URL('resource-exit-check.json',import.meta.url)));
validateRuleAgentSnapshot(outcome.snapshot);
const begins=traces.filter(t=>t.phase==='BEGIN');
const received=traces.filter(t=>t.phase==='RECEIVED');
assert.equal(begins.length,2);
assert.equal(received.length,2);
assert.ok(received.every(t=>t.status===200));
assert.equal(traces.at(-1).validation.stage,'ACTION_PARAMETERS');
assert.equal(traces.at(-1).validation.code,'READ_HANDLE_UUID');
assert.equal(outcome.snapshot.taskStatus,'FAILED');
assert.equal(outcome.snapshot.execution.modelAttemptCount,2);
assert.equal(outcome.snapshot.execution.toolStepCount,1);
assert.equal(outcome.snapshot.execution.providerResponseReceived,true);
assert.equal(outcome.snapshot.result.evidence.length,0);
assert.equal(outcome.snapshot.result.items.flatMap(i=>i.claims).length,0);
assert.ok(outcome.snapshot.result.items.flatMap(i=>i.issues).every(i=>i.kind==='PROCESSING_FAILURE'));
assert.equal(outcome.events.length,1);
assert.equal(outcome.events[0].action,'SEARCH_REVIEWED_RULES');
const candidates=outcome.events[0].observation.candidates;
assert.equal(candidates.length,10);
assert.ok(candidates.every(c=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(c.handle)));
assert.ok(candidates.some(c=>c.excerpt.startsWith('第四条')));
assert.equal(result.passed,false);
assert.equal(result.allowanceConsumed,2);
assert.equal(result.resourcesClosed,true);
assert.equal(exit.resourcesAbsent,true);
const report={t05Passed:false,probePassed:false,safetyChecksPassed:true,actualSnapshotAcceptedByFrontend:true,
  authorizationMaximum:12,actualProviderCalls:2,unusedAllowance:10,retryPerformed:false,
  purposes:{decision:2,publicationReview:0},cases:{P01:'FAIL_BEFORE_READ',P02:'NOT_EXECUTED',P02Continuation:'NOT_EXECUTED'},
  failure:{stage:'ACTION_PARAMETERS',code:'READ_HANDLE_UUID',exactInvalidValue:'NOT_RETAINED'},
  retrieval:{candidateCount:10,allIssuedHandlesCanonicalUuid:true,article4CandidatePresent:true},
  usage:{inputTokens:received.reduce((sum,t)=>sum+t.inputTokens,0),outputTokens:received.reduce((sum,t)=>sum+t.outputTokens,0),source:'RECEIVED once per call',currencyCost:'UNKNOWN'},
  readToolsExecuted:0,claimsPublished:0,resourcesClosed:true,
  hashes:{trace:crypto.createHash('sha256').update(traceRaw).digest('hex'),outcome:crypto.createHash('sha256').update(outcomeRaw).digest('hex')},
  limits:['No full free response saved; cannot identify exact malformed value or infer why it was emitted',
    'Publication/independent review and P02 not reached','No task quality or user benefit success claimed']};
fs.writeFileSync(new URL('failure-assessment.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
const sanitized=JSON.parse(JSON.stringify(outcome,(key,value)=>['originalToken','continuationToken'].includes(key)?undefined:value));
fs.writeFileSync(new URL('actual-outcome-sanitized.json',import.meta.url),JSON.stringify(sanitized,null,2)+'\n');
console.log(JSON.stringify(report));
