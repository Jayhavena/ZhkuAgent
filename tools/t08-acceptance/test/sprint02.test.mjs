import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, writeFileSync, mkdirSync, symlinkSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {validateDataset, validateRubric, createManifest, verifyManifest, runDataset, aggregate, redact} from '../sprint02/engine.mjs';

const makeCase = (id='synthetic-1') => ({caseId:id,group:'G2',category:'A',synthetic:true,
  provenance:{kind:'SYNTHETIC_FAULT',sourceCommit:'synthetic-source',exposure:'DEVELOPMENT'},
  input:{question:'仅用于工程测试的合成问题',supplements:[]},
  reference:{answerPoints:[],forbiddenConclusions:[],necessaryConditions:[],relevantEvidence:[],allowedGaps:[],verifiedBeforeRun:true,reviewStatus:'SAFETY_EXPECTATION_ONLY'},
  expected:{taskStatus:['RESOLVED'],coverage:['COMPLETE'],requiredToolTypes:['SYNTHETIC_READ'],evidenceRequired:true},
  policyScope:{revision:'2025.9.2',audience:['SYNTHETIC'],currentApplicability:'UNKNOWN'},rubricRevision:'t08-s02-rubric-v1'});
const dataset = () => ({recordType:'T08_S02_DATASET',schemaVersion:1,datasetId:'synthetic',revision:'1',split:'development',cases:[makeCase()]});
const rubric = () => ({recordType:'T08_S02_RUBRIC',schemaVersion:1,revision:'t08-s02-rubric-v1',status:'PROPOSED_NOT_APPROVED',
  dimensions:Array.from({length:9},(_,i)=>({id:`D${i+1}`,name:'合成维度',checkType:'DETERMINISTIC',anchors:{0:'缺失',1:'部分',2:'完整'},evidence:['trace'],counterexample:'遗漏'})),
  qualityStatuses:['PASS','FAIL','NOT_EXECUTED','INSUFFICIENT_EVIDENCE','NOT_EVALUABLE'],criticalErrors:[{code:'CE-SYN',definition:'合成错误'}],proposedThresholds:{}});
const identity = {candidateId:'synthetic-candidate',sourceSha:'synthetic-source',datasetSha256:'a'.repeat(64),rubricSha256:'b'.repeat(64)};
const execution = c => ({taskId:`task-${c.caseId}`,turnId:'turn-1',runId:`run-${c.caseId}`,taskStatus:'RESOLVED',runState:'SUCCEEDED',
  actions:[{actionId:'a-1',type:'SYNTHETIC_READ'}],observations:[{actionId:'a-1',status:'SUCCEEDED'}],
  result:{coverage:'COMPLETE',items:[],issues:[],evidence:[{id:'e-1'}],evidenceRelations:[]}});

test('dataset missing and duplicate IDs or split/group contradiction cannot pass',()=>{
  assert.deepEqual(validateDataset(dataset()),[]);
  const d=dataset();d.cases.push(makeCase());delete d.cases[0].caseId;
  assert.ok(validateDataset(d).some(f=>f.code==='CASE_ID'));
  d.cases[0]=makeCase();assert.ok(validateDataset(d).some(f=>f.code==='DUPLICATE_CASE'));
  d.split='holdout';assert.ok(validateDataset(d).some(f=>f.code==='SPLIT_GROUP'));
});
test('rubric missing ninth dimension and proposed status cannot be silently approved',()=>{
  assert.deepEqual(validateRubric(rubric()),[]);
  const r=rubric();r.dimensions.pop();assert.ok(validateRubric(r).length);
  r.dimensions.push({...r.dimensions[0],id:'D9'});r.status='APPROVED';assert.ok(validateRubric(r).length);
});
test('freeze detects byte tampering, missing file, traversal and duplicate file',()=>{
  const root=mkdtempSync(join(tmpdir(),'t08-s02-'));
  try {writeFileSync(join(root,'data.json'),'{}');const m=createManifest(root,['data.json']);
    assert.deepEqual(verifyManifest(root,m),[]);writeFileSync(join(root,'data.json'),'{ }');assert.ok(verifyManifest(root,m).length);
    rmSync(join(root,'data.json'));assert.ok(verifyManifest(root,m).length);
    assert.throws(()=>createManifest(root,['../escape.json']));
    writeFileSync(join(root,'data.json'),'{}');assert.throws(()=>createManifest(root,['data.json','data.json']));
  } finally {rmSync(root,{recursive:true,force:true});}
});
test('freeze rejects symbolic links including linked parent directory',()=>{
  const root=mkdtempSync(join(tmpdir(),'t08-s02-'));
  try {mkdirSync(join(root,'real'));writeFileSync(join(root,'real','data.json'),'{}');
    symlinkSync(join(root,'real'),join(root,'linked'),'junction');assert.throws(()=>createManifest(root,['linked/data.json']));
  } finally {rmSync(root,{recursive:true,force:true});}
});
test('plan preserves every real holdout case without exposing question/reference or default PASS',async()=>{
  const d=dataset();d.split='holdout';Object.assign(d.cases[0],{group:'G3',synthetic:false});d.cases[0].provenance={kind:'DESIGNED_STUDENT_QUESTION',sourceCommit:'synthetic-source',exposure:'HOLDOUT'};
  const result=await runDataset(d,rubric(),{...identity,mode:'plan'});
  assert.equal(result.records[0].engineeringStatus,'NOT_EXECUTED');assert.equal(result.records[0].qualityStatus,'NOT_EVALUABLE');
  assert.equal(result.records[0].metrics.costCny,null);assert.equal(result.providerCalls,0);
  assert.ok(!JSON.stringify(result).includes(d.cases[0].input.question));assert.equal(result.summary.modelQualityEvaluable,false);
});
test('synthetic engineering execution preserves task linkage and unknown cost without model quality grade',async()=>{
  const result=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:execution});
  const r=result.records[0];assert.equal(r.engineeringStatus,'PASS');assert.equal(r.qualityStatus,'NOT_EVALUABLE');
  assert.equal(r.execution.taskId,'task-synthetic-1');assert.equal(r.metrics.usage.totalTokens,null);assert.equal(r.metrics.costCny,null);
  assert.equal(result.summary.byEngineeringStatus.PASS,1);
});
test('missing trajectory and false success state become insufficient evidence and FAIL',async()=>{
  const missing=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:c=>({...execution(c),observations:[]})});
  assert.equal(missing.records[0].engineeringStatus,'INSUFFICIENT_EVIDENCE');
  const failed=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:c=>({...execution(c),taskStatus:'FAILED',runState:'FAILED'})});
  assert.equal(failed.records[0].engineeringStatus,'FAIL');
  const invalidEvidence=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:c=>({...execution(c),result:{...execution(c).result,evidence:[{}]}})});
  assert.equal(invalidEvidence.records[0].engineeringStatus,'INSUFFICIENT_EVIDENCE');
});
test('adapter exception is retained and later cases still run; thrown credentials are not recorded',async()=>{
  const d=dataset();d.cases.push(makeCase('synthetic-2'));
  const result=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:c=>{if(c.caseId==='synthetic-1')throw new Error('api_key=secret credential');return execution(c);}});
  assert.deepEqual(result.records.map(r=>r.engineeringStatus),['FAIL','PASS']);assert.equal(result.records[0].error.code,'ADAPTER_EXCEPTION');
  assert.ok(!JSON.stringify(result).includes('secret'));
});
test('real cases, unknown execution mode and missing adapter never execute synthetic path',async()=>{
  const d=dataset();d.cases[0].synthetic=false;
  await assert.rejects(runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:execution}));
  await assert.rejects(runDataset(dataset(),rubric(),{...identity,mode:'online'}));
  const result=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic'});assert.equal(result.records[0].engineeringStatus,'NOT_EXECUTED');
});
test('aggregate rejects omitted/duplicate cases, mixed group and changed identity/hash',async()=>{
  const result=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:execution});
  assert.throws(()=>aggregate({...result,records:[]}));
  assert.throws(()=>aggregate({...result,records:[...result.records,...result.records]}));
  assert.throws(()=>aggregate({...result,records:[{...result.records[0],group:'G4'}]}));
  assert.throws(()=>aggregate({...result,records:[{...result.records[0],datasetSha256:'c'.repeat(64)}]}));
});
test('redaction removes credential fields and replayable tokens recursively',()=>{
  assert.deepEqual(redact({apiKey:'hidden',nested:{continuationToken:'hidden',password:'hidden'},ok:'visible'}),{apiKey:'[REDACTED]',nested:{continuationToken:'[REDACTED]',password:'[REDACTED]'},ok:'visible'});
});

test('continuation expectations follow turn order and adapter never receives reference/expected',async()=>{
  const d=dataset();d.cases[0].input.supplements=['合成补充'];d.cases[0].expected.taskStatus=['WAITING_USER','RESOLVED'];d.cases[0].expected.coverage=['NONE','COMPLETE'];
  const result=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:c=>{
    assert.equal(c.reference,undefined);assert.equal(c.expected,undefined);
    return {turns:[{...execution(c),taskStatus:'WAITING_USER',result:{...execution(c).result,coverage:'NONE'}},{...execution(c),turnId:'turn-2',runId:'run-2'}]};
  }});
  assert.equal(result.records[0].engineeringStatus,'PASS');
  const missing=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:execution});assert.equal(missing.records[0].engineeringStatus,'FAIL');
});
test('aggregate refuses forged zero unknown cost and synthetic model quality PASS',async()=>{
  const result=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:execution});
  result.records[0].metrics.costCny=0;assert.throws(()=>aggregate(result));
  result.records[0].metrics.costCny=null;result.records[0].qualityStatus='PASS';assert.throws(()=>aggregate(result));
});
test('aggregate rejects PASS without execution and adapter cannot claim online calls',async()=>{
  const result=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:execution});
  result.records[0].turns=[];result.records[0].execution=null;assert.throws(()=>aggregate(result));
  const online=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:c=>({...execution(c),mode:'ONLINE',providerCalls:1})});
  assert.equal(online.records[0].engineeringStatus,'FAIL');
});
test('explicit group selection retains all dataset IDs and reports excluded IDs',async()=>{
  const d=dataset();d.cases.push({...makeCase('synthetic-g4'),group:'G4'});
  await assert.rejects(runDataset(d,rubric(),{...identity,mode:'plan'}));
  const result=await runDataset(d,rubric(),{...identity,mode:'plan',group:'G2'});
  assert.deepEqual(result.datasetTotalCaseIds,['synthetic-1','synthetic-g4']);assert.deepEqual(result.expectedCaseIds,['synthetic-1']);assert.deepEqual(result.excludedCaseIds,['synthetic-g4']);
  assert.deepEqual(result.summary.excludedCount,1);assert.equal(result.records.length,1);
  assert.throws(()=>aggregate({...result,excludedCaseIds:[]}));
});
test('legacy evidence validator remains compatible with existing explicit synthetic fixture',async()=>{
  const {validateDocument}=await import('../validate-evidence.mjs');
  const {readFileSync}=await import('node:fs');
  const fixture=JSON.parse(readFileSync(new URL('./fixtures/valid-online-sample.json',import.meta.url),'utf8'));
  assert.equal(fixture.synthetic,true);
  assert.deepEqual(validateDocument(fixture).filter(f=>f.severity==='ERROR'),[]);
});

for(const [name,receipt,tool,want] of [
  ['missing receipt',undefined,'SEARCH_REVIEWED_RULES','INSUFFICIENT_EVIDENCE'],
  ['wrong point',{type:'EMPTY_RETRIEVAL',point:'READ',reached:true,applied:true},'SEARCH_REVIEWED_RULES','INSUFFICIENT_EVIDENCE'],
  ['wrong type',{type:'TOOL_EXCEPTION',point:'SEARCH',reached:true,applied:true},'SEARCH_REVIEWED_RULES','INSUFFICIENT_EVIDENCE'],
  ['not reached',{type:'EMPTY_RETRIEVAL',point:'SEARCH',reached:false,applied:true},'SEARCH_REVIEWED_RULES','INSUFFICIENT_EVIDENCE'],
  ['not applied',{type:'EMPTY_RETRIEVAL',point:'SEARCH',reached:true,applied:false},'SEARCH_REVIEWED_RULES','INSUFFICIENT_EVIDENCE'],
  ['unrelated trace',{type:'EMPTY_RETRIEVAL',point:'SEARCH',reached:true,applied:true},'READ_VERIFIED_RULE','INSUFFICIENT_EVIDENCE'],
  ['matched fault and expected FAILED',{type:'EMPTY_RETRIEVAL',point:'SEARCH',reached:true,applied:true},'SEARCH_REVIEWED_RULES','PASS'],
])test(`G4 fault injection requires matching applied receipt and linked point: ${name}`,async()=>{
  const d=dataset(),c=d.cases[0];c.group='G4';c.faultInjection={type:'EMPTY_RETRIEVAL',point:'SEARCH',payload:'synthetic-empty-result'};
  c.expected={taskStatus:['FAILED'],coverage:['NONE'],requiredToolTypes:[],evidenceRequired:false};
  const result=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:input=>({...execution(input),
    taskStatus:'FAILED',runState:'FAILED',actions:[{actionId:'injected-action',type:tool}],observations:[{actionId:'injected-action',status:'NO_MATCH'}],
    result:{coverage:'NONE',items:[],issues:[],evidence:[],evidenceRelations:[]},faultReceipt:receipt})});
  assert.equal(result.records[0].engineeringStatus,want);assert.equal(result.records[0].qualityStatus,'NOT_EVALUABLE');
  if(want==='PASS')assert.deepEqual(result.records[0].faultReceipt,receipt);
});

test('G4 READ receipt cannot match an observation from another action; plan remains unexecuted',async()=>{
  const d=dataset(),c=d.cases[0];c.group='G4';c.faultInjection={type:'INVALID_EVIDENCE_CHECKSUM',point:'READ',payload:'synthetic-invalid'};
  const result=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:input=>({...execution(input),
    actions:[{actionId:'read-1',type:'READ_VERIFIED_RULE'}],observations:[{actionId:'unrelated',status:'FAILED'}],
    faultReceipt:{type:'INVALID_EVIDENCE_CHECKSUM',point:'READ',reached:true,applied:true}})});
  assert.equal(result.records[0].engineeringStatus,'INSUFFICIENT_EVIDENCE');
  const plan=await runDataset(d,rubric(),{...identity,mode:'plan'});assert.equal(plan.records[0].engineeringStatus,'NOT_EXECUTED');
});
test('G4 adapter exception cannot establish fault application; matched READ receipt survives aggregation',async()=>{
  const d=dataset(),c=d.cases[0];c.group='G4';c.expected.requiredToolTypes=[];c.faultInjection={type:'TOOL_EXCEPTION',point:'READ',payload:'synthetic-error'};
  const thrown=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:()=>{throw new Error('synthetic error');}});
  assert.equal(thrown.records[0].engineeringStatus,'INSUFFICIENT_EVIDENCE');assert.equal(thrown.records[0].error.code,'ADAPTER_EXCEPTION');
  const receipt={type:'TOOL_EXCEPTION',point:'READ',reached:true,applied:true};
  const result=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:input=>{
    assert.deepEqual(input.faultInjection,c.faultInjection);
    return {...execution(input),actions:[{actionId:'read-1',type:'READ_VERIFIED_RULE'}],observations:[{actionId:'read-1',status:'FAILED'}],faultReceipt:receipt};
  }});
  assert.equal(result.records[0].engineeringStatus,'PASS');assert.deepEqual(result.records[0].faultReceipt,receipt);
  result.records[0].faultReceipt=null;assert.throws(()=>aggregate(result));
});
test('CLI freeze/run retain failure, pin manifest, avoid overwrite and reject data mutation',async()=>{
  const {runCli}=await import('../sprint02/cli.mjs');
  const root=mkdtempSync(join(tmpdir(),'t08-s02-cli-'));const stdout=[];const io={out:v=>stdout.push(v),err:v=>stdout.push(v)};
  try {
    writeFileSync(join(root,'dataset.json'),JSON.stringify(dataset()));writeFileSync(join(root,'rubric.json'),JSON.stringify(rubric()));
    assert.equal(await runCli(['create-manifest','--root',root,'--out',join(root,'freeze.json'),'dataset.json','rubric.json'],io),0);
    const {readFileSync}=await import('node:fs');const {sha256}=await import('../sprint02/engine.mjs');
    const pin=sha256(readFileSync(join(root,'freeze.json')));
    const args=['run','--root',root,'--manifest','freeze.json','--expect-manifest-sha256',pin,'--dataset','dataset.json','--rubric','rubric.json','--mode','synthetic','--candidate-id','synthetic','--source-sha','synthetic-source','--out',join(root,'result.json')];
    assert.equal(await runCli(args,io),0);assert.equal(await runCli(args,io),2);
    const result=JSON.parse(readFileSync(join(root,'result.json')));assert.equal(result.records[0].engineeringStatus,'PASS');
    assert.ok(!stdout.join('').includes('仅用于工程测试'));
    const fault=dataset();fault.cases.push(makeCase('synthetic-throw'),makeCase('synthetic-missing-trajectory'));
    writeFileSync(join(root,'fault.json'),JSON.stringify(fault));
    assert.equal(await runCli(['create-manifest','--root',root,'--out',join(root,'fault-freeze.json'),'fault.json','rubric.json'],io),0);
    const faultPin=sha256(readFileSync(join(root,'fault-freeze.json')));
    assert.equal(await runCli(['run','--root',root,'--manifest','fault-freeze.json','--expect-manifest-sha256',faultPin,'--dataset','fault.json','--rubric','rubric.json','--mode','synthetic','--group','G2','--candidate-id','synthetic','--source-sha','synthetic-source','--out',join(root,'fault-result.json')],io),1);
    assert.deepEqual(JSON.parse(readFileSync(join(root,'fault-result.json'))).records.map(r=>r.engineeringStatus),['PASS','FAIL','INSUFFICIENT_EVIDENCE']);
    writeFileSync(join(root,'dataset.json'),JSON.stringify({...dataset(),revision:'2'}));
    assert.equal(await runCli([...args.slice(0,-1),join(root,'result2.json')],io),1);
    assert.equal(await runCli(['verify','--root',root,'--manifest','freeze.json','--expect-manifest-sha256','c'.repeat(64)],io),1);
  } finally {rmSync(root,{recursive:true,force:true});}
});
