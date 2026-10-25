import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, writeFileSync, readFileSync, mkdirSync, symlinkSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {validateDataset, validateRubric, createManifest, verifyManifest, runDataset, aggregate, redact, sha256} from '../sprint02/engine.mjs';

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
test('safe execution carries event step and modelAttempt but never raw action payload',async()=>{
  const report=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:c=>({
    ...execution(c),actions:[{actionId:'a-1',type:'SYNTHETIC_READ',step:2,
      modelAttempt:1,rawActionJson:'SENSITIVE-PROVIDER-PAYLOAD'}]})});
  assert.equal(report.records[0].engineeringStatus,'PASS');
  assert.deepEqual(report.records[0].execution.actions,[{
    actionId:'a-1',type:'SYNTHETIC_READ',step:2,modelAttempt:1}]);
  assert.ok(!JSON.stringify(report).includes('SENSITIVE-PROVIDER-PAYLOAD'));
  const invalid=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:c=>({
    ...execution(c),actions:[{actionId:'a-1',type:'SYNTHETIC_READ',
      modelAttempt:'SENSITIVE-INSTEAD-OF-ATTEMPT'}]})});
  assert.equal(invalid.records[0].engineeringStatus,'PASS');
  assert.equal(invalid.records[0].execution.actions[0].modelAttempt,undefined);
  assert.ok(!JSON.stringify(invalid).includes('SENSITIVE-INSTEAD-OF-ATTEMPT'));
});

test('safe execution copies only boolean coverage and removes private source identifiers',async()=>{
  const report=await runDataset(dataset(),rubric(),{...identity,mode:'synthetic',adapter:c=>({
    ...execution(c),traceCoverage:{truncated:false,exceptionalFailure:false,question:'SECRET_TEST_MARKER',nested:{secret:'SECRET_TEST_MARKER'}},
    result:{...execution(c).result,evidence:[{id:'e-1',sourceId:'SECRET_TEST_MARKER',chunkId:'SECRET_TEST_MARKER'}]},
  })});
  assert.deepEqual(report.records[0].execution.traceCoverage,{truncated:false,exceptionalFailure:false});
  assert.deepEqual(report.records[0].execution.result.evidence,[{id:'e-1'}]);
  assert.ok(!JSON.stringify(report).includes('SECRET_TEST_MARKER'));
});
const javaIdentity={candidateId:'candidate-1',sourceSha:'a'.repeat(40),caseId:'synthetic-1',datasetSha256:'b'.repeat(64)};
const javaExport=()=>({recordType:'T08_JAVA_TRACE_EXPORT',schemaVersion:1,
  sourceLayer:'ACTUAL_JAVA_RUNNER_OFFLINE_SIMULATED',executionMode:'OFFLINE_SIMULATED',providerCalls:0,...javaIdentity,
  traces:[{taskId:'task-1',turnId:'turn-1',runId:'run-1',version:2,exceptionalFailure:false,truncated:false,
    taskStatus:'RESOLVED',runState:'SUCCEEDED',actions:[{actionId:'action-1',type:'READ_VERIFIED_RULE',step:1,modelAttempt:1},
      {actionId:'action-2',type:'FINISH_CHECK',step:2,modelAttempt:2}],
    observations:[{actionId:'action-1',status:'SUCCEEDED',dataLevel:'VERIFIED_FACT'},
      {actionId:'action-2',status:'NOT_OBSERVED',dataLevel:null}],
    result:{coverage:'COMPLETE',evidence:[{id:'evidence-1',revision:'2025.9.2'}],
      evidenceRelations:[{targetId:'claim-1',evidenceId:'evidence-1',role:'CLAIM'}],
      items:[{claims:[{id:'claim-1',factId:'sha256:'+'b'.repeat(64)}]}],issues:[]}}]});
const javaDataset=()=>{const d=dataset();d.cases[0].expected.requiredToolTypes=['READ_VERIFIED_RULE','FINISH_CHECK'];return d;};
const javaOptions=()=>({...identity,...javaIdentity,rubricSha256:identity.rubricSha256,mode:'java-offline',javaCaseId:'synthetic-1'});
const javaNegatives=[
  ['UUID composite',t=>t.result.evidence[0].id='prefix-123e4567-e89b-12d3-a456-426614174000:0-20'],
  ['raw field',t=>t.question='SECRET_TEST_MARKER'],
  ['duplicate action',t=>t.actions[1].actionId='action-1'],
  ['missing control observation',t=>t.observations.pop()],
  ['contradictory state',t=>t.runState='RUNNING'],
  ['failed state pair',t=>t.taskStatus='FAILED'],
  ['dangling evidence',t=>t.result.evidenceRelations[0].evidenceId='evidence-2'],
  ['omitted items',t=>delete t.result.items],
  ['omitted evidence',t=>delete t.result.evidence],
  ['omitted relations',t=>delete t.result.evidenceRelations],
  ['omitted issues',t=>delete t.result.issues],
  ['omitted claims',t=>delete t.result.items[0].claims],
  ['missing result',t=>delete t.result],
  ['empty claims',t=>t.result.items[0].claims=[]],
  ['duplicate claim',t=>t.result.items[0].claims.push({...t.result.items[0].claims[0]})],
  ['duplicate evidence',t=>t.result.evidence.push({...t.result.evidence[0]})],
  ['wrong role',t=>t.result.evidenceRelations[0].role='SCOPE'],
  ['unreferenced evidence',t=>t.result.evidence.push({id:'evidence-2',revision:'2025.9.2'})],
  ['source ID',t=>t.result.evidence[0].sourceId='SECRET_TEST_MARKER'],
  ['chunk ID',t=>t.result.evidence[0].chunkId='SECRET_TEST_MARKER'],
  ['unsafe revision',t=>t.result.evidence[0].revision='sk-test-marker'],
  ['arbitrary reason',t=>t.observations[0].reasonCode='SECRET_TEST_MARKER'],
  ['contradictory successful reason',t=>t.observations[0].reasonCode='RETRIEVAL_NO_MATCH'],
  ['contradictory failed observation',t=>Object.assign(t.observations[0],{status:'NO_MATCH',reasonCode:'RETRIEVAL_NO_MATCH'})],
  ['arbitrary issue',t=>t.result.issues.push({kind:'SECRET_TEST_MARKER',reasonCode:'SECRET_TEST_MARKER'})],
  ['arbitrary status',t=>t.observations[0].status='SECRET_TEST_MARKER'],
  ['arbitrary data level',t=>t.observations[0].dataLevel='SECRET_TEST_MARKER'],
  ['arbitrary action',t=>t.actions[0].type='SECRET_TEST_MARKER'],
  ['nested coverage',t=>t.traceCoverage={question:'SECRET_TEST_MARKER',truncated:false}],
  ['nonboolean coverage',t=>t.truncated='SECRET_TEST_MARKER'],
  ['step gap',t=>t.actions[1].step=3],
  ['attempt decreases',t=>t.actions[1].modelAttempt=0],
  ['spurious observation',t=>t.observations.push({actionId:'action-3',status:'NOT_OBSERVED',dataLevel:null})],
  ['duplicate observation',t=>t.observations[1]={...t.observations[0]}],
  ['invented control success',t=>t.observations[1]={actionId:'action-2',status:'SUCCEEDED',dataLevel:'VERIFIED_FACT'}],
  ['tool not observed',t=>t.observations[0]={actionId:'action-1',status:'NOT_OBSERVED',dataLevel:null}],
  ['failed read cannot prove claims',t=>t.observations[0]={actionId:'action-1',status:'UNAVAILABLE',dataLevel:'NONE',reasonCode:'RETRIEVAL_UNAVAILABLE'}],
  ['bad fact hash',t=>t.result.items[0].claims[0].factId='SECRET_TEST_MARKER'],
  ['hidden UUID action',t=>t.actions[0].actionId='action-123e4567-e89b-12d3-a456-426614174000'],
  ['wrong turn ID',t=>t.turnId='turn-2'],
  ['wrong run ID',t=>t.runId='run-2'],
  ['wrong version',t=>t.version=3],
];
test('Java imports remain unverified even when hand-written JSON has matching identity',async()=>{
  const {importJavaTraceExport}=await import('../sprint02/java-trace-adapter.mjs');
  const [turn]=importJavaTraceExport(javaExport(),javaIdentity);
  assert.deepEqual(turn.traceCoverage,{truncated:false,exceptionalFailure:false});
  assert.equal(turn.observations[1].status,'NOT_OBSERVED');
  const report=await runDataset(javaDataset(),rubric(),{...javaOptions(),javaExport:javaExport()});
  assert.equal(report.mode,'UNVERIFIED_IMPORTED_JAVA_TRACE');
  assert.equal(report.sourceLayer,'UNVERIFIED_IMPORTED_JAVA_TRACE');
  assert.equal(report.javaExecutionVerified,false);assert.equal(report.summary.verifiedJavaCandidate,false);
  assert.equal(report.records[0].engineeringStatus,'PASS');
  assert.equal(report.records[0].qualityStatus,'NOT_EVALUABLE');assert.equal(report.providerCalls,0);
  assert.equal(report.rubricStatus,'PROPOSED_NOT_APPROVED');
  const adapterReport=await runDataset(javaDataset(),rubric(),{...javaOptions(),adapter:()=>javaExport()});
  assert.equal(adapterReport.records[0].engineeringStatus,'PASS');assert.equal(adapterReport.javaExecutionVerified,false);
  const bypass=await runDataset(javaDataset(),rubric(),{...javaOptions(),javaSourceVerified:true,adapter:()=>({turns:[turn]})});
  assert.notEqual(bypass.records[0].engineeringStatus,'PASS');assert.equal(bypass.javaExecutionVerified,false);
  assert.ok(!JSON.stringify(bypass).includes('ACTUAL_JAVA_RUNNER_VERIFIED'));
  assert.throws(()=>aggregate({...report,mode:'ACTUAL_JAVA_RUNNER_OFFLINE_SIMULATED',javaExecutionVerified:true}));
  assert.throws(()=>aggregate({...report,javaExecutionVerified:true}));
  const forged=structuredClone(report);forged.records[0].turns[0].traceCoverage.question='SECRET_TEST_MARKER';
  assert.throws(()=>aggregate(forged));
  await assert.rejects(runDataset(javaDataset(),rubric(),{...javaOptions(),sourceSha:'not-a-sha',javaSourceVerified:true,javaExport:javaExport()}),/JAVA_EXPORT_IDENTITY/);
  const multi=javaDataset();multi.cases.push({...makeCase('other')});
  await assert.rejects(runDataset(multi,rubric(),{...javaOptions(),javaExport:javaExport()}),/JAVA_EXPORT_REQUIRED_SINGLE_CASE/);
  const turns=javaExport();turns.traces.push(structuredClone(turns.traces[0]));
  assert.throws(()=>importJavaTraceExport(turns,javaIdentity));
  const mult=javaDataset();mult.cases[0].input.supplements=['extra'];mult.cases[0].expected.taskStatus.push('RESOLVED');mult.cases[0].expected.coverage.push('COMPLETE');
  await assert.rejects(runDataset(mult,rubric(),{...javaOptions(),javaExport:javaExport()}),/JAVA_EXPORT_REQUIRED_SINGLE_CASE/);
  for(const key of ['truncated','exceptionalFailure']) {
    const doc=javaExport();doc.traces[0][key]=true;
    const r=await runDataset(javaDataset(),rubric(),{...javaOptions(),javaExport:doc});
    assert.notEqual(r.records[0].engineeringStatus,'PASS');
    assert.ok(r.records[0].findings.some(f=>f.code===(key==='truncated'?'TRACE_TRUNCATED':'TRACE_EXCEPTIONAL_FAILURE')));
  }
});
for(const [name,mutate] of javaNegatives)test(`Java import and direct API reject ${name} without leaking raw data`,async()=>{
  const {importJavaTraceExport}=await import('../sprint02/java-trace-adapter.mjs');
  const doc=javaExport();mutate(doc.traces[0]);
  assert.throws(()=>importJavaTraceExport(doc,javaIdentity),/JAVA_EXPORT_/);
  for(const input of [{javaExport:doc},{adapter:()=>doc}]) {
    const report=await runDataset(javaDataset(),rubric(),{...javaOptions(),...input});
    assert.ok(['FAIL','INSUFFICIENT_EVIDENCE'].includes(report.records[0].engineeringStatus));
    assert.equal(report.records[0].execution,null);
    assert.ok(!JSON.stringify(report).includes('SECRET_TEST_MARKER'));
    assert.ok(!JSON.stringify(report).includes('123e4567'));
  }
});
test('CLI saves rejected Java imports on fresh outputs and never verifies fake Java source',async()=>{
  const {runCli}=await import('../sprint02/cli.mjs');
  const root=mkdtempSync(join(tmpdir(),'t08-java-import-'));const messages=[];
  const io={out:v=>messages.push(v),err:v=>messages.push(v)};
  try {
    const d=javaDataset();writeFileSync(join(root,'dataset.json'),JSON.stringify(d));writeFileSync(join(root,'rubric.json'),JSON.stringify(rubric()));
    const digest=sha256(readFileSync(join(root,'dataset.json')));
    assert.equal(await runCli(['create-manifest','--root',root,'--out',join(root,'freeze.json'),'dataset.json','rubric.json'],io),0);
    const pin=sha256(readFileSync(join(root,'freeze.json')));
    let index=0;
    for(const [name,mutate] of [['valid self-attestation',()=>{}],...javaNegatives,
      ['fake source',(_,doc)=>doc.sourceLayer='ACTUAL_JAVA_RUNNER_VERIFIED'],
      ['unknown commit',(_,doc)=>doc.sourceSha='c'.repeat(40)],
      ['parent commit',(_,doc)=>doc.sourceSha='f3414eee62025ef026d5eccc059b3aa4cd644553'],
      ['non-SHA source',(_,doc)=>doc.sourceSha='SECRET_TEST_MARKER']]) {
      const doc=javaExport();doc.datasetSha256=digest;mutate(doc.traces[0],doc);
      writeFileSync(join(root,'java-export.json'),JSON.stringify(doc));
      const out=join(root,`result-${index++}.json`);
      const code=await runCli(['run','--root',root,'--manifest','freeze.json','--expect-manifest-sha256',pin,'--dataset','dataset.json','--rubric','rubric.json',
        '--mode','java-offline','--candidate-id','candidate-1','--source-sha','a'.repeat(40),'--case-id','synthetic-1','--java-export','java-export.json','--out',out],io);
      assert.equal(code,name==='valid self-attestation'?0:1,name);
      const report=JSON.parse(readFileSync(out));assert.equal(report.javaExecutionVerified,false);
      assert.equal(report.mode,'UNVERIFIED_IMPORTED_JAVA_TRACE');
      if(name!=='valid self-attestation')assert.notEqual(report.records[0].engineeringStatus,'PASS',name);
      assert.ok(!JSON.stringify(report).includes('SECRET_TEST_MARKER'),name);
      assert.ok(!JSON.stringify(report).includes('123e4567'),name);
    }
    assert.ok(!messages.join('').includes('SECRET_TEST_MARKER'));
  } finally {rmSync(root,{recursive:true,force:true});}
});

test('Java export byte boundary distinguishes missing, malformed and unsafe JSON without raw output',async()=>{
  const {runCli}=await import('../sprint02/cli.mjs');
  const root=mkdtempSync(join(tmpdir(),'t08-java-bytes-'));
  try {
    writeFileSync(join(root,'dataset.json'),JSON.stringify(javaDataset()));
    writeFileSync(join(root,'rubric.json'),JSON.stringify(rubric()));
    writeFileSync(join(root,'freeze.json'),JSON.stringify(createManifest(root,['dataset.json','rubric.json'])));
    const pin=sha256(readFileSync(join(root,'freeze.json')));
    const cases=[
      ['missing',null,'JAVA_EXPORT_MISSING'],
      ['malformed',Buffer.from('{"private":"RAW_PRIVATE_MARKER"'),'JAVA_EXPORT_FORMAT'],
      ['invalid-utf8',Buffer.from([0xff]),'JAVA_EXPORT_FORMAT'],
      ['duplicate',Buffer.from('{"traces":[],"tr\\u0061ces":[]}'),'JAVA_EXPORT_DUPLICATE_KEY'],
      ['prototype',Buffer.from('{"__proto__":{}}'),'JAVA_EXPORT_UNSAFE_KEY'],
      ['nested-duplicate',Buffer.from('{"a":{"x":1,"x":2}}'),'JAVA_EXPORT_DUPLICATE_KEY'],
      ['depth',Buffer.from('['.repeat(25)+'0'+']'.repeat(25)),'JAVA_EXPORT_LIMIT'],
      ['size',Buffer.alloc(2*1024*1024+1,32),'JAVA_EXPORT_LIMIT'],
    ];
    for(const [name,bytes,want] of cases){
      const input=`${name}.json`,output=join(root,`${name}-result.json`),messages=[];
      if(bytes!==null)writeFileSync(join(root,input),bytes);
      const code=await runCli(['run','--root',root,'--manifest','freeze.json','--expect-manifest-sha256',pin,
        '--dataset','dataset.json','--rubric','rubric.json','--mode','java-offline','--candidate-id','candidate-1',
        '--source-sha','a'.repeat(40),'--case-id','synthetic-1','--java-export',input,'--out',output],
        {out:v=>messages.push(v),err:v=>messages.push(v)});
      assert.equal(code,2,name);assert.equal(JSON.parse(messages.at(-1)).ok,false,name);
      assert.equal(JSON.parse(messages.at(-1)).error,want,name);
      assert.ok(!messages.join('').includes('RAW_PRIVATE_MARKER'));
      assert.throws(()=>readFileSync(output),{code:'ENOENT'});
    }
  } finally {rmSync(root,{recursive:true,force:true});}
});

test('unexpected Java adapter failure stops evaluation with a fixed safe error',async()=>{
  for(const raw of ['RAW_PRIVATE_MARKER','JAVA_EXPORT_RAW_PRIVATE_MARKER']){
    await assert.rejects(runDataset(javaDataset(),rubric(),{...javaOptions(),adapter:()=>{throw new Error(raw);}}),
      error=>error.message==='JAVA_EVALUATION_FAILED');
  }
});

test('Java aggregation rejects evidence supported only by a failed verified read',async()=>{
  const report=await runDataset(javaDataset(),rubric(),{...javaOptions(),javaExport:javaExport()});
  for(const turn of [report.records[0].execution,...report.records[0].turns]){
    turn.observations[0]={actionId:'action-1',status:'UNAVAILABLE',dataLevel:'NONE',reasonCode:'RETRIEVAL_UNAVAILABLE'};
  }
  assert.throws(()=>aggregate(report),/JAVA_EXPORT_UNPROVEN_RESULT/);
});

test('a failed read followed by a successful verified read can support complete claims',async()=>{
  const doc=javaExport();doc.traces[0].actions=[
    {actionId:'action-1',type:'READ_VERIFIED_RULE',step:1,modelAttempt:1},
    {actionId:'action-2',type:'READ_VERIFIED_RULE',step:2,modelAttempt:2},
    {actionId:'action-3',type:'FINISH_CHECK',step:3,modelAttempt:3}];
  doc.traces[0].observations=[
    {actionId:'action-1',status:'UNAVAILABLE',dataLevel:'NONE',reasonCode:'RETRIEVAL_UNAVAILABLE'},
    {actionId:'action-2',status:'SUCCEEDED',dataLevel:'VERIFIED_FACT'},
    {actionId:'action-3',status:'NOT_OBSERVED',dataLevel:null}];
  const report=await runDataset(javaDataset(),rubric(),{...javaOptions(),javaExport:doc});
  assert.equal(report.records[0].engineeringStatus,'PASS');
  assert.equal(aggregate(report).byEngineeringStatus.PASS,1);
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

test('notObservedCannotProveToolExecution for READ and SEARCH',async()=>{
  for(const tool of ['READ_VERIFIED_RULE','SEARCH_REVIEWED_RULES']) {
    const d=dataset();
    d.cases[0].expected.requiredToolTypes=[tool];
    const result=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:c=>({
      ...execution(c),actions:[{actionId:'a-1',type:tool}],
      observations:[{actionId:'a-1',status:'NOT_OBSERVED'}]})});
    assert.equal(result.records[0].engineeringStatus,'INSUFFICIENT_EVIDENCE');
    assert.ok(result.records[0].findings.some(f=>f.code==='MISSING_TRAJECTORY'));
  }
});

test('notObservedCannotProveFaultInjection even with a matched receipt',async()=>{
  const d=dataset(),c=d.cases[0];c.group='G4';
  c.faultInjection={type:'EMPTY_RETRIEVAL',point:'SEARCH',payload:'synthetic-fault'};
  c.expected={taskStatus:['FAILED'],coverage:['NONE'],requiredToolTypes:[],evidenceRequired:false};
  const receipt={type:'EMPTY_RETRIEVAL',point:'SEARCH',reached:true,applied:true};
  for(const status of ['NOT_OBSERVED','FAKE_SUCCEEDED']) {
    const result=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:input=>({
      ...execution(input),taskStatus:'FAILED',runState:'FAILED',
      actions:[{actionId:'search-1',type:'SEARCH_REVIEWED_RULES'}],
      observations:[{actionId:'search-1',status}],
      result:{coverage:'NONE',items:[],issues:[],evidence:[],evidenceRelations:[]},
      faultReceipt:receipt})});
    assert.equal(result.records[0].engineeringStatus,'INSUFFICIENT_EVIDENCE');
    assert.ok(result.records[0].findings.some(f=>f.code==='FAULT_INJECTION_UNPROVEN'));
  }
});

test('control events do not require invented ToolObservations',async()=>{
  const d=dataset();d.cases[0].expected.requiredToolTypes=['FINISH_CHECK'];
  const result=await runDataset(d,rubric(),{...identity,mode:'synthetic',adapter:c=>({
    ...execution(c),actions:[{actionId:'finish-1',type:'FINISH_CHECK'}],
    observations:[{actionId:'finish-1',status:'NOT_OBSERVED'}]})});
  assert.equal(result.records[0].engineeringStatus,'PASS');
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
