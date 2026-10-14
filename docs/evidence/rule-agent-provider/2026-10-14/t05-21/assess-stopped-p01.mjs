import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {validateRuleAgentSnapshot} from '../../../../../agent/frontend/src/api/rule-agent-contract.js';

const base=new URL('./',import.meta.url);
const launch=JSON.parse(fs.readFileSync(new URL('approved-launcher-result.json',base)));
assert.equal(launch.evidenceDirectories.length,1);
const dir=new URL(`../${launch.evidenceDirectories[0]}/`,base);
const rawTrace=fs.readFileSync(new URL('provider-traces.jsonl',dir));
const traces=rawTrace.toString('utf8').trim().split(/\r?\n/).map(JSON.parse);
const files=fs.readdirSync(dir).filter(name=>name.endsWith('-outcome.json'));
assert.equal(files.length,1);
const rawOutcome=fs.readFileSync(new URL(files[0],dir));
const outcome=JSON.parse(rawOutcome);
const result=JSON.parse(fs.readFileSync(new URL('probe-result.json',dir)));
const exit=JSON.parse(fs.readFileSync(new URL('resource-exit-check.json',base)));
validateRuleAgentSnapshot(outcome.snapshot);
const begins=traces.filter(row=>row.phase==='BEGIN');
const received=traces.filter(row=>row.phase==='RECEIVED');
assert.equal(begins.length,2);
assert.equal(received.length,2);
assert.equal(new Set(begins.map(row=>row.attempt)).size,2);
assert.ok(received.every(row=>row.purpose==='DECISION'&&row.status===200));
assert.equal(traces.at(-1).validation.stage,'ACTION_PARAMETERS');
assert.equal(traces.at(-1).validation.code,'READ_HANDLE_COUNT');
assert.deepEqual(traces.at(-1).validation.readSelection,{elementIndex:null,jsonType:'ARRAY',stringLength:null,failureCategory:'READ_HANDLE_COUNT',currentCandidate:null});
assert.equal(outcome.snapshot.taskStatus,'FAILED');
assert.equal(outcome.snapshot.runState,'FAILED');
assert.equal(outcome.snapshot.terminated,true);
assert.equal(outcome.snapshot.execution.modelAttemptCount,2);
assert.equal(outcome.snapshot.execution.toolStepCount,1);
assert.equal(outcome.snapshot.execution.providerResponseReceived,true);
assert.equal(outcome.snapshot.execution.answerSource,'NONE');
assert.equal(outcome.snapshot.result.evidence.length,0);
assert.equal(outcome.snapshot.result.items.flatMap(item=>item.claims).length,0);
assert.ok(outcome.snapshot.result.items.flatMap(item=>item.issues).every(issue=>issue.kind==='PROCESSING_FAILURE'));
assert.equal(outcome.events.length,1);
assert.equal(outcome.events[0].action,'SEARCH_REVIEWED_RULES');
const candidates=outcome.events[0].observation.candidates;
assert.equal(candidates.length,10);
assert.ok(candidates.some(candidate=>candidate.excerpt.startsWith('第四条')));
assert.equal(result.passed,false);
assert.equal(result.allowanceConsumed,2);
assert.equal(result.maximumProviderCalls,4);
assert.equal(result.caseSelection,'P01_READ_REFERENCE_03');
assert.equal(result.resourcesClosed,true);
assert.equal(launch.exitCode,1);
assert.equal(launch.timedOut,false);
assert.equal(launch.launcherResourcesClosed,true);
assert.equal(exit.resourcesAbsent,true);
assert.equal(launch.retryPerformed,false);
const sumKnown=key=>received.every(row=>Number.isSafeInteger(row[key]))?received.reduce((sum,row)=>sum+row[key],0):null;
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const report={probePassed:false,t05Complete:false,safetyChecksPassed:true,actualSnapshotAcceptedByFrontend:true,
  actualProviderCalls:begins.length,authorizedMaximum:4,unusedAllowance:2,unusedAllowanceClosed:true,retryPerformed:false,
  phasesReached:{search:true,read:false,finish:false,independentReview:false,publication:false},P02:'NOT_EXECUTED',
  failure:{stage:'ACTION_PARAMETERS',code:'READ_HANDLE_COUNT',jsonType:'ARRAY',
    knownConstraint:'array size outside 1..3',emptyOrTooMany:'UNKNOWN_NOT_RETAINED',candidateReferenceLegality:'NOT_REACHED'},
  retrieval:{candidateCount:candidates.length,article4CandidatePresent:true},
  usage:{inputTokens:sumKnown('inputTokens'),outputTokens:sumKnown('outputTokens'),source:'RECEIVED once per call',currencyCost:'UNKNOWN'},
  readToolsExecuted:0,claimsPublished:0,resourcesClosed:true,
  hashes:{trace:hash(rawTrace),outcome:hash(rawOutcome)},
  limits:['No free model response saved; do not infer empty vs excess or why selected',
    'Count check precedes set/ref validation; real reference mapping remains unverified',
    'No publication/P02 quality or overall natural-language/user-benefit success']};
fs.writeFileSync(new URL('failure-assessment.json',base),JSON.stringify(report,null,2)+'\n');
const sanitized=JSON.parse(JSON.stringify(outcome,(key,value)=>['originalToken','continuationToken'].includes(key)?undefined:key==='handle'?'[HOST_CANDIDATE_HANDLE_REDACTED]':value));
fs.writeFileSync(new URL('actual-outcome-sanitized.json',base),JSON.stringify(sanitized,null,2)+'\n');
console.log(JSON.stringify(report));
