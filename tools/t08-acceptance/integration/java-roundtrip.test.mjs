import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync,realpathSync,statSync,existsSync} from 'node:fs';
import {dirname,resolve,relative,isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../..');
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const inside=path=>{const r=relative(root,path);return r!==''&&!r.startsWith('..')&&!isAbsolute(r);};
const save=(path,value)=>writeFileSync(path,JSON.stringify(value,null,2)+'\n',{flag:'wx'});

// 必须显式使用本次 Maven 的实际产物；不生成成功夹具，也不在缺少验收输入时跳过。
test('actual Java export traverses the existing Node CLI with fail-closed negative cases',async t=>{
  for(const key of ['T08_JAVA_EXPORT_FILE','T08_BRIDGE_RUN_DIR','T08_JAVA_EXPECTED_SOURCE',
      'T08_JAVA_EXPECTED_CANDIDATE','T08_JAVA_EXPORT_SHA256','T08_JAVA_NOT_BEFORE'])assert.ok(process.env[key],`${key} required`);
  const input=resolve(process.env.T08_JAVA_EXPORT_FILE),out=resolve(process.env.T08_BRIDGE_RUN_DIR);
  assert.ok(inside(input)&&inside(out));assert.equal(realpathSync(input),input);
  assert.equal(realpathSync(dirname(out)),dirname(out));assert.ok(!existsSync(out));mkdirSync(out);
  const raw=readFileSync(input),doc=JSON.parse(raw.toString('utf8'));
  assert.equal(sha(raw),process.env.T08_JAVA_EXPORT_SHA256);
  assert.ok(Number.isFinite(Number(process.env.T08_JAVA_NOT_BEFORE)));
  assert.ok(statSync(input).mtimeMs>=Number(process.env.T08_JAVA_NOT_BEFORE));
  assert.equal(doc.candidateId,process.env.T08_JAVA_EXPECTED_CANDIDATE);
  assert.equal(doc.sourceSha,process.env.T08_JAVA_EXPECTED_SOURCE);
  assert.equal(doc.caseId,'java-offline-runner-pass');assert.equal(doc.providerCalls,0);
  const dataset='tools/t08-acceptance/sprint02/java-offline-runner-dataset.json';
  const rubric='docs/preparation/t08/sprint02/rubric.json';
  assert.equal(doc.datasetSha256,sha(readFileSync(resolve(root,dataset))));
  assert.ok(!raw.toString('utf8').includes(JSON.parse(readFileSync(resolve(root,dataset))).cases[0].input.question));
  assert.ok(!/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i.test(raw.toString('utf8')));
  let invocation=0;
  const cli=resolve(root,'tools/t08-acceptance/sprint02/cli.mjs');
  function call(args){
    const child=spawnSync(process.execPath,[cli,...args],{cwd:root,encoding:'utf8',timeout:30000});
    save(resolve(out,`command-${++invocation}.json`),{args:[process.execPath,cli,...args],cwd:root,status:child.status,
      signal:child.signal,errorCode:child.error?.code??null,stdout:child.stdout,stderr:child.stderr});
    assert.equal(child.error?.code,undefined);assert.equal(child.signal,null);
    assert.ok(!`${child.stdout}${child.stderr}`.includes('RAW_PRIVATE_MARKER'));
    return child;
  }
  const manifest=resolve(out,'bridge-manifest.json');
  const created=call(['create-manifest','--root',root,'--out',manifest,dataset,rubric]);
  assert.equal(created.status,0);
  const pin=sha(readFileSync(manifest));
  const common=['--root',root,'--manifest',relative(root,manifest).replaceAll('\\','/'),'--expect-manifest-sha256',pin];
  const run=(file,destination,extra=[])=>call(['run',...common,'--dataset',dataset,'--rubric',rubric,'--mode','java-offline',
    '--candidate-id',process.env.T08_JAVA_EXPECTED_CANDIDATE,'--source-sha',process.env.T08_JAVA_EXPECTED_SOURCE,
    '--case-id','java-offline-runner-pass','--java-export',relative(root,file).replaceAll('\\','/'),'--out',destination,...extra]);
  const result=resolve(out,'valid-result.json');
  await t.test('fresh Maven bytes pass validation, evaluation and aggregation but stay UNVERIFIED',()=>{
    assert.equal(call(['verify',...common]).status,0);
    const child=run(input,result);assert.equal(child.status,0);assert.equal(JSON.parse(child.stdout).ok,true);
    const report=JSON.parse(readFileSync(result));
    assert.equal(report.mode,'UNVERIFIED_IMPORTED_JAVA_TRACE');assert.equal(report.javaExecutionVerified,false);
    assert.equal(report.summary.verifiedJavaCandidate,false);assert.equal(report.summary.modelQualityEvaluable,false);
    assert.equal(report.summary.qualityStatus,'NOT_EVALUABLE');assert.equal(report.providerCalls,0);
    assert.equal(report.summary.total,1);assert.equal(report.summary.byEngineeringStatus.PASS,1);
    assert.equal(report.records[0].execution.taskStatus,'RESOLVED');
    assert.equal(report.records[0].execution.actions[0].modelAttempt,1);
    const aggregated=call(['aggregate','--result',result]);assert.equal(aggregated.status,0);
    assert.equal(JSON.parse(aggregated.stdout).byEngineeringStatus.PASS,1);
  });
  const negatives=[
    ['missing-field',d=>delete d.traces[0].observations],
    ['wrong-type',d=>d.traces[0].version='2'],
    ['unknown-field',d=>d.traces[0].question='RAW_PRIVATE_MARKER'],
    ['wrong-source',d=>d.sourceSha='0'.repeat(40)],
    ['failed-read-with-claims',d=>{
      for(const a of d.traces[0].actions.filter(a=>a.type==='READ_VERIFIED_RULE')){
        Object.assign(d.traces[0].observations.find(o=>o.actionId===a.actionId),{status:'UNAVAILABLE',dataLevel:'NONE',reasonCode:'RETRIEVAL_UNAVAILABLE'});
      }
    }],
    ['truncated',d=>d.traces[0].truncated=true],
    ['failed-run',d=>{
      const trace=d.traces[0];trace.taskStatus='FAILED';trace.runState='FAILED';trace.exceptionalFailure=true;
      trace.result={coverage:'NONE',items:[],evidence:[],evidenceRelations:[],issues:[{kind:'PROCESSING_FAILURE',reasonCode:'RETRIEVAL_UNAVAILABLE'}]};
    }],
  ];
  for(const [name,mutate] of negatives)await t.test(`${name} produces nonzero exit and no successful record`,()=>{
    const changed=structuredClone(doc);mutate(changed);
    const file=resolve(out,`${name}-export.json`),destination=resolve(out,`${name}-result.json`);save(file,changed);
    const child=run(file,destination);assert.equal(child.status,1);assert.equal(JSON.parse(child.stdout).ok,false);
    const report=JSON.parse(readFileSync(destination));assert.equal(report.summary.byEngineeringStatus.PASS,0);
    assert.notEqual(report.records[0].engineeringStatus,'PASS');assert.equal(report.javaExecutionVerified,false);
    assert.ok(!readFileSync(destination,'utf8').includes('RAW_PRIVATE_MARKER'));
    // aggregate 的退出0只表示报告结构可汇总，不表示失败case变为成功。
    const aggregate=call(['aggregate','--result',destination]);assert.equal(aggregate.status,0);
    assert.equal(JSON.parse(aggregate.stdout).byEngineeringStatus.PASS,0);
  });
  for(const [name,bytes] of [['missing-file',null],['malformed',Buffer.from('{')],
      ['duplicate-key',Buffer.from('{"traces":[],"traces":[]}')]])await t.test(`${name} fails before producing a result`,()=>{
    const file=resolve(out,`${name}-export.json`),destination=resolve(out,`${name}-result.json`);
    if(bytes!==null)writeFileSync(file,bytes,{flag:'wx'});
    const child=run(file,destination);assert.equal(child.status,2);assert.equal(JSON.parse(child.stderr).ok,false);
    assert.ok(!existsSync(destination));
  });
  await t.test('existing output is never overwritten',()=>{
    const before=sha(readFileSync(result));assert.equal(run(input,result).status,2);assert.equal(sha(readFileSync(result)),before);
  });
  await t.test('wrong manifest pin cannot produce a successful result',()=>{
    const destination=resolve(out,'wrong-pin-result.json');
    const args=['run',...common];args[args.indexOf('--expect-manifest-sha256')+1]='0'.repeat(64);
    const child=call([...args,'--dataset',dataset,'--rubric',rubric,'--mode','java-offline','--candidate-id',doc.candidateId,
      '--source-sha',doc.sourceSha,'--case-id',doc.caseId,'--java-export',relative(root,input).replaceAll('\\','/'),'--out',destination]);
    assert.equal(child.status,1);assert.equal(JSON.parse(child.stdout).ok,false);assert.ok(!existsSync(destination));
  });
  save(resolve(out,'roundtrip-receipt.json'),{status:'UNVERIFIED',actualJavaExportSha256:sha(raw),manifestSha256:pin,
    candidateId:doc.candidateId,parentSourceSha:doc.sourceSha,nodeInvocations:invocation,providerCalls:0,
    scope:'SINGLE_CASE_OFFLINE_FUNCTIONAL_BRIDGE_ONLY',trustedExecution:false});
});
