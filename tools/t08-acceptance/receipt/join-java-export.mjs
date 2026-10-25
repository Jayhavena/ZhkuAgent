// 回执只证明本地材料自洽；评测仍使用已有引擎，不授予可信执行等级。
import {runDataset,aggregate,validateDataset,validateRubric} from '../sprint02/engine.mjs';
import {importJavaTraceExport} from '../sprint02/java-trace-adapter.mjs';
import {ReceiptError,CONTROLLER_VERSION,JAVA_TEST_PATH,MAX_EXPORT_BYTES,parseStrictJson,
  parseSurefireReport,validateReceipt,validateChallenge,validateFixtureManifest,sha256Hex,stableStringify} from './receipt.mjs';

export const RECEIPTED_RESULT='T08_RECEIPTED_RESULT';
const fail=code=>{throw new ReceiptError(code);};
const object=v=>v!==null&&typeof v==='object'&&!Array.isArray(v);
const hash=v=>typeof v==='string'&&/^[a-f0-9]{64}$/.test(v);
const identityKeys=['candidateId','sourceSha','caseId','manifestSha256','datasetSha256','rubricSha256'];
function identity(input){
  const i=input.identity;
  if(!object(i)||!['candidateId','caseId'].every(k=>typeof i[k]==='string'&&/^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$/.test(i[k]))||
      typeof i.sourceSha!=='string'||!/^[a-f0-9]{40}(?:[a-f0-9]{24})?$/.test(i.sourceSha)||
      !['manifestSha256','datasetSha256','rubricSha256'].every(k=>hash(i[k])))fail('RECEIPT_INPUT_IDENTITY');
  return Object.fromEntries(identityKeys.map(k=>[k,i[k]]));
}
function required(bytes){if(!Buffer.isBuffer(bytes)||!bytes.length)fail('RECEIPT_ARTIFACT_MISSING');return bytes;}
function view(i){return {recordType:RECEIPTED_RESULT,schemaVersion:1,status:'UNVERIFIED',identity:i,
  receiptStatus:'INVALID',evaluationStatus:'NOT_EXECUTED',binding:null,traceResult:null,error:null,summary:null};}
function finish(result){
  const accepted=result.receiptStatus==='VALID'&&result.evaluationStatus==='PASS';
  result.summary={accepted,receiptStatus:result.receiptStatus,evaluationStatus:result.evaluationStatus,
    trustLevel:'SELF_REPORTED',trustedExecution:false,qualityStatus:'NOT_EVALUABLE',providerCalls:0};
  return result;
}
function verify(input,i,result){
  if(!input.receipts){result.receiptStatus='MISSING';fail('RECEIPT_REQUIRED');}
  const bundle=parseStrictJson(required(input.receipts));
  if(!object(bundle)||Object.keys(bundle).sort().join('|')!=='receipts|recordType|schemaVersion'||
      bundle.recordType!=='T08_RECEIPT_BUNDLE'||bundle.schemaVersion!==1||!Array.isArray(bundle.receipts))fail('RECEIPT_BUNDLE_FIELDS');
  if(!bundle.receipts.length){result.receiptStatus='MISSING';fail('RECEIPT_REQUIRED');}
  if(new Set(bundle.receipts.map(r=>r?.receiptId)).size!==bundle.receipts.length)fail('RECEIPT_DUPLICATE');
  if(bundle.receipts.length!==1)fail('RECEIPT_COUNT');
  const r=validateReceipt(bundle.receipts[0]);
  if(r.controller.version!==CONTROLLER_VERSION)fail('LEGACY_RECEIPT_NOT_CURRENT');
  const manifest=required(input.manifest),dataset=required(input.dataset),rubric=required(input.rubric),javaTest=required(input.javaTest);
  if(sha256Hex(manifest)!==i.manifestSha256||sha256Hex(dataset)!==i.datasetSha256||sha256Hex(rubric)!==i.rubricSha256)fail('RECEIPT_FIXTURE_MISMATCH');
  const entries=validateFixtureManifest(parseStrictJson(manifest));
  const paths=input.paths??{dataset:'tools/t08-acceptance/sprint02/java-offline-runner-dataset.json',rubric:'docs/preparation/t08/sprint02/rubric.json'};
  for(const [path,bytes] of [[paths.dataset,dataset],[paths.rubric,rubric],[JAVA_TEST_PATH,javaTest]]){
    const entry=entries.get(path);if(!entry||entry.sha256!==sha256Hex(bytes)||entry.bytes!==bytes.length)fail('RECEIPT_FIXTURE_MISMATCH');
  }
  if(r.fixtures.manifestSha256!==i.manifestSha256||r.fixtures.datasetSha256!==i.datasetSha256||r.fixtures.rubricSha256!==i.rubricSha256||
      r.fixtures.javaTestSha256!==sha256Hex(javaTest)||r.fixtures.caseId!==i.caseId||r.source.commit!==i.sourceSha)fail('RECEIPT_FIXTURE_MISMATCH');
  const challengeBytes=required(input.challenge),challenge=parseStrictJson(challengeBytes);
  validateChallenge(challenge,{expected:{candidateCommit:i.sourceSha,candidateTree:r.source.tree,caseId:i.caseId,
    fixtureManifestSha256:i.manifestSha256,datasetSha256:i.datasetSha256,rubricSha256:i.rubricSha256}});
  const {recordType,schemaVersion,...body}=challenge;const {sha256,...saved}=r.challenge;
  if(sha256Hex(challengeBytes)!==sha256||stableStringify(body)!==stableStringify(saved))fail('RECEIPT_CHALLENGE_MISMATCH');
  result.binding={receiptId:r.receiptId,challengeId:challenge.challengeId,caseId:i.caseId,sourceCommit:r.source.commit,sourceTree:r.source.tree,
    fixtureManifestSha256:i.manifestSha256,challengeSha256:sha256,exportSha256:r.execution.export?.sha256??null,
    surefireReportSha256:r.execution.maven?.reportSha256??null,controllerVersion:r.controller.version};
  if(r.execution.status==='FAILED'){result.receiptStatus='EXECUTION_FAILED';fail('RECEIPT_EXECUTION_FAILED');}
  if(r.execution.status==='NOT_EXECUTED'){result.receiptStatus='NOT_EXECUTED';fail('RECEIPT_NOT_EXECUTED');}
  const exported=required(input.javaExport),surefire=required(input.surefire);
  if(exported.length>MAX_EXPORT_BYTES||r.execution.export.sha256!==sha256Hex(exported)||r.execution.export.bytes!==exported.length)fail('RECEIPT_EXPORT_MISMATCH');
  if(r.execution.maven.reportSha256!==sha256Hex(surefire))fail('RECEIPT_REPORT_MISMATCH');
  let xml;try{xml=new TextDecoder('utf-8',{fatal:true}).decode(surefire);}catch{fail('SUREFIRE_MALFORMED');}
  const counts=parseSurefireReport(xml,{controllerVersion:r.controller.version});
  if(['tests','failures','errors','skipped'].some(k=>counts[k]!==r.execution.maven[k]))fail('RECEIPT_REPORT_MISMATCH');
  const argv=r.execution.command.argv;
  for(const [key,value] of [['candidateId',i.candidateId],['sourceSha',i.sourceSha],['caseId',i.caseId]]){
    const args=argv.filter(a=>a.startsWith(`-Dzhku.t08.${key}=`));if(args.length!==1||args[0]!==`-Dzhku.t08.${key}=${value}`)fail('RECEIPT_COMMAND_MISMATCH');
  }
  if(!argv.includes('-o')||argv.filter(a=>a==='-Dtest=RuleAgentOfflineTraceExportTest').length!==1)fail('RECEIPT_COMMAND_MISMATCH');
  const d=parseStrictJson(dataset,{maxBytes:MAX_EXPORT_BYTES}),rub=parseStrictJson(rubric);
  if(validateDataset(d).length||validateRubric(rub).length)fail('RECEIPT_INPUT_INVALID');
  const doc=parseStrictJson(exported,{maxBytes:MAX_EXPORT_BYTES});
  try{importJavaTraceExport(doc,i);}catch{fail('EXPORT_INVALID');}
  result.receiptStatus='VALID';return {dataset:d,rubric:rub,javaExport:doc};
}

/** 不可信的回执/缺证据只产安全拒绝记录；已校验执行失败与评测器失败分别记录。 */
export async function evaluateReceipted(input,deps={}){
  const i=identity(input),result=view(i);let admitted;
  try{admitted=verify(input,i,result);}catch(error){
    result.error={code:error instanceof ReceiptError?error.code:'RECEIPT_INTERNAL_ERROR'};return finish(result);
  }
  try{
    const evaluate=deps.evaluate??runDataset;
    const trace=await evaluate(admitted.dataset,admitted.rubric,{candidateId:i.candidateId,sourceSha:i.sourceSha,
      datasetSha256:i.datasetSha256,rubricSha256:i.rubricSha256,mode:'java-offline',javaCaseId:i.caseId,javaExport:admitted.javaExport});
    const summary=aggregate(trace);result.traceResult=trace;
    result.evaluationStatus=summary.total===1&&summary.byEngineeringStatus.PASS===1?'PASS':'FAIL';
  }catch{result.traceResult=null;result.evaluationStatus='EVALUATOR_FAILED';result.error={code:'EVALUATOR_FAILED'};}
  return finish(result);
}
function comparable(report){
  const copy=structuredClone(report);
  for(const r of copy.traceResult?.records??[]){
    if(!Number.isFinite(r.metrics?.elapsedMillis)||r.metrics.elapsedMillis<0)fail('RECEIPT_RESULT_MISMATCH');
    r.metrics.elapsedMillis=null;
  }
  return stableStringify(copy);
}
/** 汇总必须重新读取回执和实际证据；不能只相信报告中的receiptId或PASS。 */
export async function aggregateReceipted(report,input){
  const current=await evaluateReceipted(input);
  // 历史评测器失败只能维持失败，不因当前重放恢复而被自动升绿。
  if(report?.evaluationStatus==='EVALUATOR_FAILED'&&current.receiptStatus==='VALID'){
    current.evaluationStatus='EVALUATOR_FAILED';current.traceResult=null;current.error={code:'EVALUATOR_FAILED'};finish(current);
  }
  if(comparable(report)!==comparable(current))fail('RECEIPT_RESULT_MISMATCH');
  return current.summary;
}
