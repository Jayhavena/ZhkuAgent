import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,rmSync,readFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fixture,bytes,hash,sign,xml} from './receipt-fixture.mjs';

test('Java CLI does not silently treat a missing receipt as a closed-loop pass',async()=>{
  const {runCli}=await import('../sprint02/cli.mjs');const f=fixture(),root=mkdtempSync(join(tmpdir(),'receipt-required-'));
  try{
    for(const [name,b] of [['dataset.json',f.dataset],['rubric.json',f.rubric],['java.json',f.javaExport]])writeFileSync(join(root,name),b);
    const manifest={recordType:'T08_S02_FREEZE',schemaVersion:1,algorithm:'SHA-256',files:[['dataset.json',f.dataset],['rubric.json',f.rubric]].map(([path,b])=>({path,bytes:b.length,sha256:hash(b)}))};
    writeFileSync(join(root,'manifest.json'),bytes(manifest));
    const out=join(root,'result.json');
    const code=await runCli(['run','--root',root,'--manifest','manifest.json','--expect-manifest-sha256',hash(bytes(manifest)),
      '--dataset','dataset.json','--rubric','rubric.json','--mode','java-offline','--candidate-id',f.identity.candidateId,'--source-sha',f.identity.sourceSha,
      '--case-id',f.identity.caseId,'--java-export','java.json','--out',out],{out:()=>{},err:()=>{}});
    assert.equal(code,1);const result=JSON.parse(readFileSync(out));assert.equal(result.receiptStatus,'MISSING');assert.equal(result.summary.accepted,false);
  }finally{rmSync(root,{recursive:true,force:true});}
});

test('receipt join validates the whole evidence binding and remains SELF_REPORTED',async()=>{
  const {evaluateReceipted,aggregateReceipted}=await import('../receipt/join-java-export.mjs');const f=fixture();
  const result=await evaluateReceipted(f);
  assert.equal(result.status,'UNVERIFIED');assert.equal(result.receiptStatus,'VALID');assert.equal(result.summary.accepted,true);
  assert.equal(result.summary.trustLevel,'SELF_REPORTED');assert.equal(result.traceResult.javaExecutionVerified,false);
  assert.equal((await aggregateReceipted(result,f)).accepted,true);
  const tampered=structuredClone(result);tampered.binding.receiptId='0'.repeat(64);
  await assert.rejects(aggregateReceipted(tampered,f),/RECEIPT_RESULT_MISMATCH/);
});

const negatives=[
  ['missing',f=>f.receipts=null,'MISSING'],
  ['empty',f=>f.receipts=bytes({recordType:'T08_RECEIPT_BUNDLE',schemaVersion:1,receipts:[]}),'MISSING'],
  ['duplicate',f=>f.receipts=bytes({recordType:'T08_RECEIPT_BUNDLE',schemaVersion:1,receipts:[f.receipt,f.receipt]}),'INVALID'],
  ['corrupt',f=>{f.receipt.execution.export.sha256='0'.repeat(64);f.receipts=bytes({recordType:'T08_RECEIPT_BUNDLE',schemaVersion:1,receipts:[f.receipt]});},'INVALID'],
  ['export changed',f=>f.javaExport=Buffer.concat([f.javaExport,Buffer.from('\n')]),'INVALID'],
  ['challenge changed',f=>f.challenge=Buffer.concat([f.challenge,Buffer.from('\n')]),'INVALID'],
  ['report changed',f=>f.surefire=xml(2),'INVALID'],
  ['dataset changed',f=>f.dataset=Buffer.concat([f.dataset,Buffer.from('\n')]),'INVALID'],
  ['test source changed',f=>f.javaTest=Buffer.from('// not the pinned test'),'INVALID'],
  ['export missing',f=>f.javaExport=null,'INVALID'],
  ['bad JSON',f=>f.receipts=Buffer.from('{"private":"RAW_PRIVATE_MARKER"'),'INVALID'],
  ['duplicate JSON',f=>f.receipts=Buffer.from('{"receipts":[],"receipts":[]}'),'INVALID'],
];
for(const [name,mutate,want] of negatives)test(`receipt ${name} cannot become success during evaluation or aggregation`,async()=>{
  const {evaluateReceipted,aggregateReceipted}=await import('../receipt/join-java-export.mjs');const f=fixture();mutate(f);
  const r=await evaluateReceipted(f);assert.equal(r.receiptStatus,want);assert.equal(r.summary.accepted,false);assert.equal(r.traceResult,null);
  assert.equal((await aggregateReceipted(r,f)).accepted,false);assert.ok(!JSON.stringify(r).includes('RAW_PRIVATE_MARKER'));
});

test('failed and not-executed receipts remain distinct from invalid receipts',async()=>{
  const {evaluateReceipted}=await import('../receipt/join-java-export.mjs');
  for(const status of ['FAILED','NOT_EXECUTED']){
    const f=fixture(),r=f.receipt;r.execution.status=status;r.observationStatus='NOT_EXECUTED';
    r.reasonCode=status==='FAILED'?'MAVEN_EXIT_NONZERO':'MAVEN_EXECUTION_NOT_ENABLED';
    r.execution.maven=null;r.execution.export=null;
    if(status==='FAILED')r.execution.exit.code=17;else{r.execution.exit=null;r.execution.command=null;}
    f.receipts=bytes({recordType:'T08_RECEIPT_BUNDLE',schemaVersion:1,receipts:[sign(r)]});
    const result=await evaluateReceipted(f);assert.equal(result.receiptStatus,status==='FAILED'?'EXECUTION_FAILED':'NOT_EXECUTED');assert.equal(result.summary.accepted,false);
  }
});

test('legacy 2-test receipts cannot replace the current 3-test contract',async()=>{
  const {evaluateReceipted}=await import('../receipt/join-java-export.mjs');const f=fixture();
  f.receipt.controller.version='t08-controller-v1a';f.receipt.execution.maven.tests=2;f.surefire=xml(2);f.receipt.execution.maven.reportSha256=hash(f.surefire);
  f.receipts=bytes({recordType:'T08_RECEIPT_BUNDLE',schemaVersion:1,receipts:[sign(f.receipt)]});
  const r=await evaluateReceipted(f);assert.equal(r.receiptStatus,'INVALID');assert.equal(r.error.code,'LEGACY_RECEIPT_NOT_CURRENT');
});

test('evaluator exceptions never become a successful receipted result or expose their message',async()=>{
  const {evaluateReceipted}=await import('../receipt/join-java-export.mjs');
  const r=await evaluateReceipted(fixture(),{evaluate:()=>{throw new Error('RAW_PRIVATE_MARKER');}});
  assert.equal(r.receiptStatus,'VALID');assert.equal(r.evaluationStatus,'EVALUATOR_FAILED');assert.equal(r.summary.accepted,false);
  assert.equal(r.error.code,'EVALUATOR_FAILED');assert.ok(!JSON.stringify(r).includes('RAW_PRIVATE_MARKER'));
});
