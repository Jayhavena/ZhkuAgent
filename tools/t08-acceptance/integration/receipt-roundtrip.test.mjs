import {test} from 'node:test';
import assert from 'node:assert/strict';
import {existsSync,mkdirSync,readFileSync,writeFileSync,realpathSync,rmSync} from 'node:fs';
import {resolve,relative,dirname,basename,isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createHash,randomBytes} from 'node:crypto';
import {createManifest} from '../sprint02/engine.mjs';
import {runController} from '../receipt/observe.mjs';
import {stableStringify,JAVA_TEST_PATH} from '../receipt/receipt.mjs';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../..');
const hash=b=>createHash('sha256').update(b).digest('hex');
const save=(p,v)=>writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const resign=r=>{const {receiptId,...body}=r;return {...body,receiptId:hash(stableStringify(body))};};

test('real Maven receipt joins Java export, existing evaluator and aggregate without trust upgrade',async t=>{
  for(const name of ['T08_RECEIPT_REPORT_ROOT','T08_RECEIPT_RUN_DIR','T08_RECEIPT_CANDIDATE_SOURCE'])assert.ok(process.env[name],`${name} required`);
  const reportRoot=resolve(process.env.T08_RECEIPT_REPORT_ROOT),out=resolve(process.env.T08_RECEIPT_RUN_DIR);
  assert.equal(realpathSync(reportRoot),reportRoot);assert.equal(resolve(process.env.T08_RECEIPT_CANDIDATE_SOURCE),root);
  const relativeOut=relative(root,out);assert.ok(relativeOut&&!relativeOut.startsWith('..')&&!isAbsolute(relativeOut));
  assert.equal(realpathSync(dirname(out)),dirname(out));assert.ok(!existsSync(out));mkdirSync(out);
  const label=basename(out);assert.match(label,/^[A-Za-z0-9-]+$/);
  const candidate=resolve(reportRoot,'candidate-'+label),controllerOut=resolve(reportRoot,'controller-'+label);
  assert.ok(!existsSync(candidate)&&!existsSync(controllerOut));
  let callIndex=0;
  function call(exe,args,cwd=root){
    const p=spawnSync(exe,args,{cwd,encoding:'utf8',timeout:60000,windowsHide:true,maxBuffer:4*1024*1024});
    save(resolve(out,`command-${++callIndex}.json`),{executable:exe,args,cwd,status:p.status,signal:p.signal,errorCode:p.error?.code??null,stdout:p.stdout,stderr:p.stderr});
    assert.equal(p.error?.code,undefined);assert.equal(p.signal,null);
    assert.ok(!`${p.stdout}${p.stderr}`.includes('RAW_PRIVATE_MARKER'));return p;
  }
  const git=(args,cwd=root)=>{
    const p=call('git',['--no-optional-locks','-c','core.fsmonitor=false','-c','core.longpaths=true',...args],cwd);assert.equal(p.status,0,'local read/temporary checkout failed');return p.stdout.trim();
  };
  const commit=git(['rev-parse','HEAD']),tree=git(['rev-parse','HEAD^{tree}']);
  const dataset='tools/t08-acceptance/sprint02/java-offline-runner-dataset.json',rubric='docs/preparation/t08/sprint02/rubric.json';
  try{
    // 新临时clone只用于执行固定提交，不注册Worktree、不切换原树，原树的Node改动不冒充干净候选。
    git(['clone','--shared','--no-checkout','--quiet',root,candidate]);
    git(['sparse-checkout','set','--cone','agent-runtime','agent','knowledge','tools/t08-acceptance','docs/preparation/t08/sprint02'],candidate);
    git(['checkout','--detach','--quiet',commit],candidate);
    const manifest=createManifest(candidate,[dataset,rubric,JAVA_TEST_PATH]);const manifestPath=resolve(out,'fixtures.json');save(manifestPath,manifest);
    const pin=hash(readFileSync(manifestPath)),entry=p=>manifest.files.find(f=>f.path===p);
    const now=Date.now(),challenge={recordType:'T08_EXECUTION_CHALLENGE',schemaVersion:1,challengeId:'p2-c-'+randomBytes(8).toString('hex'),nonce:randomBytes(24).toString('hex'),
      issuedAt:new Date(now).toISOString(),expiresAt:new Date(now+29*60000).toISOString(),candidateCommit:commit,candidateTree:tree,
      caseId:'java-offline-runner-pass',fixtureManifestSha256:pin,datasetSha256:entry(dataset).sha256,rubricSha256:entry(rubric).sha256};
    const challengePath=resolve(out,'challenge.json');save(challengePath,challenge);
    const observation=await runController({root:candidate,candidateCommit:commit,candidateTree:tree,
      challengePath,challengeSha256:hash(readFileSync(challengePath)),fixtureManifestPath:manifestPath,fixtureManifestSha256:pin,
      datasetPath:dataset,rubricPath:rubric,caseId:challenge.caseId,candidateId:'p2-c1',outDir:controllerOut,
      mavenExecutable:'D:/Software/Maven/apache-maven-3.9.15/bin/mvn.cmd',allowMaven:true,timeoutMs:600000});
    save(resolve(out,'controller-result.json'),{ok:observation.ok,reasonCode:observation.reasonCode,receiptPath:observation.receiptPath});
    assert.equal(observation.ok,true,'actual offline Maven observation failed; see controller-result/receipt');
    const receipt=observation.receipt;assert.equal(receipt.execution.maven.tests,3);
    assert.equal(receipt.source.clean,true);assert.equal(receipt.controller.version,'t08-controller-v1b');
    for(const n of ['java-export.json','surefire.xml'])writeFileSync(resolve(out,n),readFileSync(resolve(controllerOut,n)),{flag:'wx'});
    const receiptPath=resolve(out,'receipts.json');save(receiptPath,{recordType:'T08_RECEIPT_BUNDLE',schemaVersion:1,receipts:[receipt]});
    const exportPath=resolve(out,'java-export.json'),xmlPath=resolve(out,'surefire.xml');
    const rel=p=>relative(root,p).replaceAll('\\','/');
    const common=['--root',root,'--manifest',rel(manifestPath),'--expect-manifest-sha256',pin,'--dataset',dataset,'--rubric',rubric,
      '--candidate-id','p2-c1','--source-sha',commit,'--case-id',challenge.caseId,'--java-export',rel(exportPath),
      '--receipts',rel(receiptPath),'--challenge',rel(challengePath),'--surefire-report',rel(xmlPath)];
    const cli=resolve(root,'tools/t08-acceptance/sprint02/cli.mjs');
    const invoke=args=>call(process.execPath,[cli,...args]);
    const resultPath=resolve(out,'valid-result.json');
    await t.test('fresh real Java + 3 named tests + receipt + evaluation + aggregation succeed as UNVERIFIED',()=>{
      assert.equal(invoke(['run',...common,'--mode','java-offline','--out',resultPath]).status,0);
      const r=JSON.parse(readFileSync(resultPath));assert.equal(r.status,'UNVERIFIED');assert.equal(r.summary.accepted,true);
      assert.equal(r.receiptStatus,'VALID');assert.equal(r.summary.trustLevel,'SELF_REPORTED');assert.equal(r.summary.trustedExecution,false);
      assert.equal(r.traceResult.javaExecutionVerified,false);assert.equal(r.traceResult.summary.qualityStatus,'NOT_EVALUABLE');
      assert.equal(invoke(['aggregate',...common,'--result',resultPath]).status,0);
    });
    const cases=[
      ['missing',()=>[], 'MISSING'],
      ['duplicate',()=>[receipt,receipt],'INVALID'],
      ['damaged',r=>{r.execution.export.sha256='0'.repeat(64);return [r];},'INVALID'],
      ['wrong-case',r=>{r.fixtures.caseId='wrong-case';return [resign(r)];},'INVALID'],
      ['wrong-count',r=>{r.execution.maven.tests=2;return [resign(r)];},'INVALID'],
      ['old-contract',r=>{r.controller.version='t08-controller-v1a';r.execution.maven.tests=2;return [resign(r)];},'INVALID'],
      ['failed-execution',r=>{r.execution.status='FAILED';r.execution.exit.code=17;r.execution.maven=null;r.execution.export=null;r.reasonCode='MAVEN_EXIT_NONZERO';r.observationStatus='NOT_EXECUTED';return [resign(r)];},'EXECUTION_FAILED'],
    ];
    for(const [name,mutate,want] of cases)await t.test(`${name} receipt remains rejected by run and aggregate`,()=>{
      const file=resolve(out,`${name}-receipts.json`);save(file,{recordType:'T08_RECEIPT_BUNDLE',schemaVersion:1,receipts:mutate(structuredClone(receipt))});
      const args=[...common];args[args.indexOf('--receipts')+1]=rel(file);const dest=resolve(out,`${name}-result.json`);
      assert.equal(invoke(['run',...args,'--mode','java-offline','--out',dest]).status,1);
      const r=JSON.parse(readFileSync(dest));assert.equal(r.receiptStatus,want);assert.equal(r.summary.accepted,false);
      assert.equal(invoke(['aggregate',...args,'--result',dest]).status,1);
    });
    await t.test('omitting receipt argument cannot fall back to the old trace-only pass',()=>{
      const args=[...common];args.splice(args.indexOf('--receipts'),2);const dest=resolve(out,'omitted-receipt-result.json');
      assert.equal(invoke(['run',...args,'--mode','java-offline','--out',dest]).status,1);
      assert.equal(JSON.parse(readFileSync(dest)).receiptStatus,'MISSING');
    });
    await t.test('business failure with valid evidence bindings is not a successful evaluation',()=>{
      const doc=JSON.parse(readFileSync(exportPath));const tr=doc.traces[0];tr.taskStatus='FAILED';tr.runState='FAILED';tr.exceptionalFailure=true;
      tr.result={coverage:'NONE',items:[],evidence:[],evidenceRelations:[],issues:[{kind:'PROCESSING_FAILURE',reasonCode:'RETRIEVAL_UNAVAILABLE'}]};
      const p=resolve(out,'failed-business-export.json');save(p,doc);const b=readFileSync(p),r=structuredClone(receipt);
      r.execution.export={sha256:hash(b),bytes:b.length};const rp=resolve(out,'failed-business-receipts.json');save(rp,{recordType:'T08_RECEIPT_BUNDLE',schemaVersion:1,receipts:[resign(r)]});
      const args=[...common];args[args.indexOf('--java-export')+1]=rel(p);args[args.indexOf('--receipts')+1]=rel(rp);
      const dest=resolve(out,'failed-business-result.json');assert.equal(invoke(['run',...args,'--mode','java-offline','--out',dest]).status,1);
      const result=JSON.parse(readFileSync(dest));assert.equal(result.receiptStatus,'VALID');assert.equal(result.evaluationStatus,'FAIL');
      assert.equal(invoke(['aggregate',...args,'--result',dest]).status,1);
    });
    await t.test('malformed receipt never leaks its body or yields success',()=>{
      const p=resolve(out,'malformed-receipts.json');writeFileSync(p,'{"private":"RAW_PRIVATE_MARKER"',{flag:'wx'});
      const args=[...common];args[args.indexOf('--receipts')+1]=rel(p);const dest=resolve(out,'malformed-result.json');
      assert.equal(invoke(['run',...args,'--mode','java-offline','--out',dest]).status,1);
      assert.equal(JSON.parse(readFileSync(dest)).summary.accepted,false);
    });
    await t.test('aggregation revalidates evidence instead of trusting a forged successful result',()=>{
      const result=JSON.parse(readFileSync(resultPath));result.binding.receiptId='0'.repeat(64);const p=resolve(out,'forged-result.json');save(p,result);
      assert.equal(invoke(['aggregate',...common,'--result',p]).status,2);
    });
    save(resolve(out,'receipt-roundtrip.json'),{status:'UNVERIFIED',candidateCommit:commit,candidateTree:tree,receiptId:receipt.receiptId,
      javaExportSha256:hash(readFileSync(exportPath)),surefireSha256:hash(readFileSync(xmlPath)),actualJavaTests:3,
      controllerMavenCommand:receipt.execution.command,controllerExit:receipt.execution.exit,nodeInvocations:callIndex,trustLevel:'SELF_REPORTED',trustedExecution:false});
  } finally {
    if(existsSync(candidate)&&dirname(candidate)===reportRoot&&realpathSync(candidate)===candidate)rmSync(candidate,{recursive:true,force:true,maxRetries:2});
  }
});
