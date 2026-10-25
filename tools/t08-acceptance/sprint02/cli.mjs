#!/usr/bin/env node
import {readFileSync, writeFileSync} from 'node:fs';
import {resolve, relative} from 'node:path';
import {pathToFileURL} from 'node:url';
import {validateDataset,validateRubric,createManifest,verifyManifest,runDataset,aggregate,sha256} from './engine.mjs';
import {syntheticAdapter} from './synthetic-adapter.mjs';
import {parseJavaExportJson} from './java-export-json.mjs';
import {javaExportErrorCode} from './java-trace-adapter.mjs';
import {ReceiptError,JAVA_TEST_PATH,parseStrictJson,MAX_EXPORT_BYTES} from '../receipt/receipt.mjs';
import {evaluateReceipted,aggregateReceipted,RECEIPTED_RESULT} from '../receipt/join-java-export.mjs';

const USAGE='Commands: validate | create-manifest | verify | run | aggregate. See sprint02/README.md. Offline only.';
function parse(argv) {
  const [command,...args]=argv;const options={command,files:[]};
  const allowed=new Set(['root','out','dataset','rubric','manifest','expect-manifest-sha256','candidate-id','source-sha','mode','result','group','java-export','case-id','receipt-policy','receipts','challenge','surefire-report']);
  for(let i=0;i<args.length;i++) {
    if(args[i].startsWith('--')) {
      const k=args[i].slice(2);if(!allowed.has(k)||options[k]!==undefined||!args[i+1]||args[i+1].startsWith('--'))throw new Error('INVALID_ARGUMENT');options[k]=args[++i];
    } else options.files.push(args[i]);
  }
  return options;
}
const json=path=>JSON.parse(readFileSync(path,'utf8').replace(/^\uFEFF/,''));
function save(path,value) {if(!path)throw new Error('OUTPUT_REQUIRED');writeFileSync(path,JSON.stringify(value,null,2)+'\n',{encoding:'utf8',flag:'wx'});}
function requireOptions(o,keys){if(keys.some(k=>!o[k]))throw new Error('MISSING_ARGUMENT');}
function checkedFile(root,name) {createManifest(root,[name]);return resolve(root,name);}
function loadPinnedManifest(o) {
  requireOptions(o,['root','manifest','expect-manifest-sha256']);
  const bytes=readFileSync(checkedFile(o.root,o.manifest));
  if(sha256(bytes)!==o['expect-manifest-sha256'])return {findings:[{severity:'ERROR',code:'MANIFEST_PIN_MISMATCH',path:'manifest'}]};
  const manifest=JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/,''));
  return {manifest,bytes,findings:verifyManifest(o.root,manifest)};
}
function optionalArtifact(o,name){
  if(!name)return null;
  try{return readFileSync(checkedFile(o.root,name));}catch(error){if(error.code==='ENOENT')return null;throw error;}
}
function frozenReader(o,pinned){
  return name=>{
    const entry=pinned.manifest.files.find(f=>f.path===name);if(!entry)throw new Error('INPUT_NOT_FROZEN');
    const bytes=readFileSync(checkedFile(o.root,name));
    if(sha256(bytes)!==entry.sha256||bytes.length!==entry.bytes)throw new Error('INPUT_CHANGED_AFTER_VERIFY');
    return bytes;
  };
}
function receiptInput(o,pinned){
  requireOptions(o,['dataset','rubric','candidate-id','source-sha','case-id']);
  const read=frozenReader(o,pinned),entry=name=>pinned.manifest.files.find(f=>f.path===name);
  return {manifest:pinned.bytes,dataset:read(o.dataset),rubric:read(o.rubric),javaTest:optionalArtifact(o,JAVA_TEST_PATH),
    receipts:optionalArtifact(o,o.receipts),challenge:optionalArtifact(o,o.challenge),surefire:optionalArtifact(o,o['surefire-report']),javaExport:optionalArtifact(o,o['java-export']),
    paths:{dataset:o.dataset,rubric:o.rubric},identity:{candidateId:o['candidate-id'],sourceSha:o['source-sha'],caseId:o['case-id'],
      manifestSha256:o['expect-manifest-sha256'],datasetSha256:entry(o.dataset).sha256,rubricSha256:entry(o.rubric).sha256}};
}

export async function runCli(argv,io={out:v=>process.stdout.write(v+'\n'),err:v=>process.stderr.write(v+'\n')}) {
  try {
    const o=parse(argv);
    if(o.command==='validate') {
      if(!o.dataset&&!o.rubric)throw new Error('INPUT_REQUIRED');
      const findings=[...(o.dataset?validateDataset(json(o.dataset)):[]),...(o.rubric?validateRubric(json(o.rubric)):[])];
      io.out(JSON.stringify({ok:!findings.length,findings}));return findings.length?1:0;
    }
    if(o.command==='create-manifest') {
      requireOptions(o,['root','out']);
      // manifest必须独立于被冻结文件；自身身份由Git记录或外部SHA固定，不能内嵌自哈希。
      const own=relative(resolve(o.root),resolve(o.out)).replaceAll('\\','/').toLowerCase();
      if(o.files.some(f=>f.toLowerCase()===own))throw new Error('SELF_FREEZE');
      const manifest=createManifest(o.root,o.files);save(o.out,manifest);
      io.out(JSON.stringify({ok:true,files:manifest.files.length,manifestSha256:sha256(readFileSync(o.out))}));return 0;
    }
    if(o.command==='verify'||o.command==='run') {
      const pinned=loadPinnedManifest(o);
      if(pinned.findings.length){io.out(JSON.stringify({ok:false,findings:pinned.findings}));return 1;}
      if(o.command==='verify'){io.out(JSON.stringify({ok:true,files:pinned.manifest.files.length}));return 0;}
      requireOptions(o,['dataset','rubric','mode','candidate-id','source-sha','out']);
      const receiptKeys=['receipts','challenge','surefire-report'];
      if(o.mode!=='java-offline'&&(['receipt-policy',...receiptKeys].some(k=>o[k])))throw new ReceiptError('RECEIPT_INPUT_INVALID');
      const policy=o['receipt-policy']??'required';
      if(!['required','trace-only'].includes(policy)||(policy==='trace-only'&&receiptKeys.some(k=>o[k])))throw new ReceiptError('RECEIPT_INPUT_INVALID');
      // 默认闭环必须有回执；旧桥接需显式选择trace-only，不能静默降级。
      if(o.mode==='java-offline'&&policy==='required'){
        const result=await evaluateReceipted(receiptInput(o,pinned));save(o.out,result);
        io.out(JSON.stringify({ok:result.summary.accepted,status:result.status,...result.summary}));return result.summary.accepted?0:1;
      }
      const entry=name=>pinned.manifest.files.find(f=>f.path===name);
      if(!entry(o.dataset)||!entry(o.rubric))throw new Error('INPUT_NOT_FROZEN');
      const frozenJSON=name=>{
        const bytes=readFileSync(checkedFile(o.root,name));
        // 用真正传给parser的同一份字节再次匹配冻结，避免verify与读取之间换文件。
        if(sha256(bytes)!==entry(name).sha256||bytes.length!==entry(name).bytes)throw new Error('INPUT_CHANGED_AFTER_VERIFY');
        return JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/,''));
      };
      let javaExport;
      if(o.mode==='java-offline') {
        requireOptions(o,['java-export']);
        requireOptions(o,['case-id']);
        try {javaExport=parseJavaExportJson(readFileSync(checkedFile(o.root,o['java-export'])));}
        catch(error){if(error.code==='ENOENT')throw new Error('JAVA_EXPORT_MISSING');throw error;}
      } else if(o['java-export']) throw new Error('JAVA_EXPORT_MODE_MISMATCH');
      const report=await runDataset(frozenJSON(o.dataset),frozenJSON(o.rubric),{
        mode:o.mode,group:o.group,candidateId:o['candidate-id'],sourceSha:o['source-sha'],datasetSha256:entry(o.dataset).sha256,rubricSha256:entry(o.rubric).sha256,
        adapter:o.mode==='synthetic'?syntheticAdapter:undefined,
        ...(o.mode==='java-offline'?{javaCaseId:o['case-id'],javaExport}:{}),
      });
      report.manifestSha256=o['expect-manifest-sha256'];
      if(o.mode==='java-offline')report.receiptStatus='NOT_REQUESTED';
      // 即使case失败也先保存全部记录，再以非零退出码报告；不可丢弃失败后只存绿例。
      save(o.out,report);io.out(JSON.stringify({ok:report.summary.byEngineeringStatus.FAIL===0&&report.summary.byEngineeringStatus.INSUFFICIENT_EVIDENCE===0,mode:report.mode,summary:report.summary,providerCalls:0}));
      return report.summary.byEngineeringStatus.FAIL||report.summary.byEngineeringStatus.INSUFFICIENT_EVIDENCE?1:0;
    }
    if(o.command==='aggregate') {
      requireOptions(o,['result']);const report=parseStrictJson(readFileSync(o.result),{maxBytes:MAX_EXPORT_BYTES});
      if(report.recordType===RECEIPTED_RESULT){
        const pinned=loadPinnedManifest(o);if(pinned.findings.length)throw new ReceiptError('RECEIPT_FIXTURE_MISMATCH');
        const summary=await aggregateReceipted(report,receiptInput(o,pinned));io.out(JSON.stringify({status:'UNVERIFIED',...summary}));return summary.accepted?0:1;
      }
      const summary=aggregate(report);io.out(JSON.stringify({...summary,...(report.mode==='UNVERIFIED_IMPORTED_JAVA_TRACE'?{receiptStatus:'NOT_REQUESTED'}:{})}));return 0;
    }
    io.err(USAGE);return 2;
  } catch(error) {
    // 文件路径、JSON解析片段与原始异常可能携带正文或密钥，不直接打印message。
    const safeCodes=new Set(['MISSING_ARGUMENT','JAVA_EXPORT_MODE_MISMATCH','JAVA_EXPORT_REQUIRED_SINGLE_CASE','OFFLINE_MODES_ONLY','JAVA_EXPORT_IDENTITY','JAVA_EXPORT_VERSION_OR_SOURCE','JAVA_EXPORT_EMPTY_TRACES','JAVA_EXPORT_COVERAGE','JAVA_EXPORT_LINKAGE','JAVA_EXPORT_STATUS','JAVA_EXPORT_MISSING_TRACE','JAVA_EXPORT_STEP','JAVA_EXPORT_NOT_OBSERVED','JAVA_EXPORT_OBSERVATION_LINK','JAVA_TRACE_COVERAGE']);
    const safeMessage=javaExportErrorCode(error)||error?.message==='JAVA_EVALUATION_FAILED';
    const safeCode=error instanceof ReceiptError?error.code:(safeMessage||([...safeCodes].some(code=>error?.message===code))?error.message:'CLI_INPUT_OR_OUTPUT_ERROR');
    io.err(JSON.stringify({ok:false,error:safeCode,findings:Array.isArray(error.findings)?error.findings:[]}));return 2;
  }
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)process.exitCode=await runCli(process.argv.slice(2));
