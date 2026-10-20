/** Sprint02 仅做离线工程验证；不导入网络库、不读凭据，也不生成模型质量成绩。 */
import {createHash} from 'node:crypto';
import {lstatSync, readFileSync, realpathSync} from 'node:fs';
import {isAbsolute, join, relative, resolve, sep} from 'node:path';
import {performance} from 'node:perf_hooks';

const STATUS = ['PASS','FAIL','NOT_EXECUTED','INSUFFICIENT_EVIDENCE','NOT_EVALUABLE'];
const str = v => typeof v === 'string' && v.trim().length > 0;
const obj = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const strings = v => Array.isArray(v) && v.every(str);
const hash = v => typeof v === 'string' && /^[a-f0-9]{64}$/.test(v);
const add = (f,code,path) => f.push({severity:'ERROR',code,path});
export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

export function validateDataset(d) {
  const f=[];
  if (!obj(d)) return [{severity:'ERROR',code:'DATASET_OBJECT',path:'$'}];
  if (d.recordType!=='T08_S02_DATASET'||d.schemaVersion!==1||!str(d.datasetId)||!str(d.revision)) add(f,'DATASET_HEADER','$');
  if (!['development','regression','holdout'].includes(d.split)) add(f,'DATASET_SPLIT','split');
  if (!Array.isArray(d.cases)||!d.cases.length) {add(f,'EMPTY_DATASET','cases');return f;}
  const ids=new Set();
  for (const [i,c] of d.cases.entries()) {
    const p=`cases[${i}]`;
    if (!obj(c)) {add(f,'CASE_OBJECT',p);continue;}
    if (!str(c.caseId)) add(f,'CASE_ID',p+'.caseId');
    else if(ids.has(c.caseId)) add(f,'DUPLICATE_CASE',p+'.caseId');
    ids.add(c.caseId);
    if (!['G2','G3','G4'].includes(c.group)||! /^[A-H]$/.test(c.category)) add(f,'CASE_GROUP_CATEGORY',p);
    if (typeof c.synthetic!=='boolean') add(f,'CASE_SYNTHETIC',p+'.synthetic');
    const expectedExposure={development:'DEVELOPMENT',regression:'REGRESSION',holdout:'HOLDOUT'}[d.split];
    if (c.provenance?.exposure!==expectedExposure || (d.split==='holdout' && (c.group!=='G3'||c.synthetic!==false)) || (d.split!=='holdout'&&c.group==='G3')) add(f,'SPLIT_GROUP',p+'.provenance');
    if (!['DESIGNED_STUDENT_QUESTION','EXPOSED_DEVELOPMENT','SYNTHETIC_FAULT'].includes(c.provenance?.kind)||!str(c.provenance?.sourceCommit)) add(f,'CASE_PROVENANCE',p+'.provenance');
    if (!str(c.input?.question)||!strings(c.input?.supplements)) add(f,'CASE_INPUT',p+'.input');
    const r=c.reference;
    if (!obj(r)||!['answerPoints','forbiddenConclusions','necessaryConditions','relevantEvidence','allowedGaps'].every(k=>strings(r[k]))||typeof r.verifiedBeforeRun!=='boolean'||!['HISTORICAL_SOURCE_CHECKED','SAFETY_EXPECTATION_ONLY','PENDING_OFFICIAL_SOURCE'].includes(r.reviewStatus)) add(f,'CASE_REFERENCE',p+'.reference');
    const e=c.expected;
    const turns=1+(Array.isArray(c.input?.supplements)?c.input.supplements.length:0);
    if (!obj(e)||!strings(e.taskStatus)||!strings(e.coverage)||e.taskStatus.length!==turns||e.coverage.length!==turns||!strings(e.requiredToolTypes)||typeof e.evidenceRequired!=='boolean') add(f,'CASE_EXPECTED',p+'.expected');
    if (Array.isArray(e?.taskStatus)&&!e.taskStatus.every(s=>['CREATED','RUNNING','WAITING_USER','RESOLVED','STOPPED','FAILED'].includes(s))) add(f,'CASE_TASK_STATUS',p+'.expected.taskStatus');
    if (Array.isArray(e?.coverage)&&!e.coverage.every(s=>['NONE','PARTIAL','COMPLETE'].includes(s))) add(f,'CASE_COVERAGE',p+'.expected.coverage');
    if (c.policyScope?.revision!=='2025.9.2'||!strings(c.policyScope?.audience)||!c.policyScope.audience.length||c.policyScope?.currentApplicability!=='UNKNOWN') add(f,'CASE_POLICY_SCOPE',p+'.policyScope');
    if (c.rubricRevision!=='t08-s02-rubric-v1') add(f,'CASE_RUBRIC',p+'.rubricRevision');
    if (c.riskTags!==undefined&&!strings(c.riskTags)) add(f,'CASE_RISK_TAGS',p+'.riskTags');
  }
  return f;
}

export function validateRubric(r) {
  const f=[];
  if (!obj(r)) return [{severity:'ERROR',code:'RUBRIC_OBJECT',path:'$'}];
  if (r.recordType!=='T08_S02_RUBRIC'||r.schemaVersion!==1||r.revision!=='t08-s02-rubric-v1'||r.status!=='PROPOSED_NOT_APPROVED') add(f,'RUBRIC_HEADER','$');
  const dimensions=Array.isArray(r.dimensions)?r.dimensions:[];
  if (dimensions.length!==9||new Set(dimensions.map(d=>d?.id)).size!==9) add(f,'RUBRIC_DIMENSIONS','dimensions');
  for(const [i,d] of dimensions.entries()) {
    if(!obj(d)||!/^D[1-9]$/.test(d.id)||!str(d.name)||!['DETERMINISTIC','REVIEW_REQUIRED','MIXED'].includes(d.checkType)||!['0','1','2'].every(k=>str(d.anchors?.[k]))||!strings(d.evidence)||!d.evidence.length||!str(d.counterexample)) add(f,'RUBRIC_DIMENSION',`dimensions[${i}]`);
  }
  if(!Array.isArray(r.qualityStatuses)||r.qualityStatuses.length!==5||!STATUS.every(s=>r.qualityStatuses.includes(s))) add(f,'RUBRIC_STATUSES','qualityStatuses');
  if(!Array.isArray(r.criticalErrors)||!r.criticalErrors.length||!r.criticalErrors.every(e=>str(e?.code)&&str(e?.definition))||new Set(r.criticalErrors.map(e=>e.code)).size!==r.criticalErrors.length) add(f,'RUBRIC_CRITICAL_ERRORS','criticalErrors');
  if(!obj(r.proposedThresholds)) add(f,'RUBRIC_THRESHOLDS','proposedThresholds');
  return f;
}

/** 检查每个路径分量，不能只 realpath 后检查最终落点，否则树内软链接仍可改变冻结身份。 */
function boundedFile(root, name) {
  if(!str(name)||isAbsolute(name)||name.includes('\\')||name.split('/').some(v=>!v||v==='.'||v==='..')||name.includes(':')) throw new Error('UNSAFE_PATH');
  const base=resolve(root);
  if(lstatSync(base).isSymbolicLink()||realpathSync(base).toLowerCase()!==base.toLowerCase()) throw new Error('LINKED_ROOT');
  let current=base;
  for(const part of name.split('/')) {current=join(current,part);if(lstatSync(current).isSymbolicLink())throw new Error('SYMLINK');}
  if(relative(base,current).startsWith('..'+sep)||!lstatSync(current).isFile())throw new Error('UNSAFE_FILE');
  return current;
}
export function createManifest(root, files) {
  if(!Array.isArray(files)||!files.length||new Set(files.map(v=>String(v).toLowerCase())).size!==files.length)throw new Error('INVALID_FILES');
  return {recordType:'T08_S02_FREEZE',schemaVersion:1,algorithm:'SHA-256',files:files.map(path=>{const bytes=readFileSync(boundedFile(root,path));return {path,sha256:sha256(bytes),bytes:bytes.length};})};
}
export function verifyManifest(root,m) {
  const f=[];
  if(!obj(m)||m.recordType!=='T08_S02_FREEZE'||m.schemaVersion!==1||m.algorithm!=='SHA-256'||!Array.isArray(m.files)||!m.files.length)return [{severity:'ERROR',code:'FREEZE_HEADER',path:'$'}];
  const seen=new Set();
  for(const [i,e] of m.files.entries()) {
    if(!obj(e)||!str(e.path)||!hash(e.sha256)||!Number.isSafeInteger(e.bytes)||e.bytes<0){add(f,'FREEZE_ENTRY',`files[${i}]`);continue;}
    const key=e.path.toLowerCase();if(seen.has(key))add(f,'FREEZE_DUPLICATE',`files[${i}]`);seen.add(key);
    try {const bytes=readFileSync(boundedFile(root,e.path));if(bytes.length!==e.bytes||sha256(bytes)!==e.sha256)add(f,'FREEZE_CHANGED',`files[${i}]`);}
    catch {add(f,'FREEZE_UNREADABLE',`files[${i}]`);}
  }
  return f;
}

/** 只保留白名单字段，适配器异常不落原始message，避免凭据混入失败日志。 */
export function redact(value) {
  if(Array.isArray(value))return value.map(redact);
  if(obj(value))return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,/(?:password|secret|cookie|authorization|api.?key|(?:continuation|original|access|refresh)?token)$/i.test(k)?'[REDACTED]':redact(v)]));
  return value;
}
function safeExecution(e) {
  if(!obj(e))return null;
  // 任意文本与完整返回载荷不进入工程回执；字段形状足以追溯状态与证据关系。
  const pick=(v,keys)=>Object.fromEntries(keys.filter(k=>v?.[k]!==undefined).map(k=>[k,redact(v[k])]));
  return {
    ...pick(e,['taskId','turnId','runId','taskStatus','runState']),
    actions:Array.isArray(e.actions)?e.actions.map(a=>pick(a,['actionId','type','step'])):[],
    observations:Array.isArray(e.observations)?e.observations.map(o=>pick(o,['actionId','status','dataLevel','reasonCode'])):[],
    result:obj(e.result)?{coverage:e.result.coverage,evidence:Array.isArray(e.result.evidence)?e.result.evidence.map(v=>pick(v,['id','evidenceId','sourceId','chunkId','revision'])):[],evidenceRelations:Array.isArray(e.result.evidenceRelations)?e.result.evidenceRelations.map(v=>pick(v,['targetId','evidenceId','role'])):[],items:Array.isArray(e.result.items)?e.result.items.map(v=>({claims:Array.isArray(v?.claims)?v.claims.map(c=>pick(c,['id','factId'])):[]})):[],issues:Array.isArray(e.result.issues)?e.result.issues.map(v=>pick(v,['kind','reasonCode'])):[]}:null,
  };
}
function safeFaultReceipt(receipt) {
  if(!obj(receipt))return null;
  return {type:['EMPTY_RETRIEVAL','INVALID_EVIDENCE_CHECKSUM','TOOL_EXCEPTION'].includes(receipt.type)?receipt.type:null,
    point:['SEARCH','READ'].includes(receipt.point)?receipt.point:null,reached:receipt.reached===true,applied:receipt.applied===true};
}
function faultSatisfied(injection,receipt,turns) {
  const tool={SEARCH:'SEARCH_REVIEWED_RULES',READ:'READ_VERIFIED_RULE'}[injection?.point];
  return !!tool&&receipt?.type===injection.type&&receipt?.point===injection.point&&receipt?.reached===true&&receipt?.applied===true&&
    turns.some(e=>e?.actions?.some(a=>a.type===tool&&str(a.actionId)&&e.observations?.some(o=>o.actionId===a.actionId&&str(o.status))));
}
function evaluate(c,turns,faultReceipt) {
  const findings=[];
  let failed=false,missing=false;
  if(turns.length!==c.expected.taskStatus.length){add(findings,'TURN_COUNT','execution');missing=true;}
  const ids=new Set();
  for(const [i,e] of turns.entries()) {
    if(!e||!['taskId','turnId','runId','taskStatus','runState'].every(k=>str(e[k]))){add(findings,'EXECUTION_IDENTITY',`turns[${i}]`);missing=true;continue;}
    const key=[e.taskId,e.turnId,e.runId].join('|');if(ids.has(key)){add(findings,'DUPLICATE_RUN',`turns[${i}]`);failed=true;}ids.add(key);
    if(i>0&&e.taskId!==turns[0]?.taskId){add(findings,'TASK_LINKAGE',`turns[${i}]`);failed=true;}
    if(e.taskStatus!==c.expected.taskStatus[i]||e.result?.coverage!==c.expected.coverage[i]||(e.runState==='FAILED'&&c.expected.taskStatus[i]!=='FAILED')){add(findings,'EXPECTED_STATE',`turns[${i}]`);failed=true;}
    if(!e.result||!e.actions.length||!e.observations.length||e.actions.some(a=>!str(a.actionId)||!e.observations.some(o=>o.actionId===a.actionId))){add(findings,'MISSING_TRAJECTORY',`turns[${i}]`);missing=true;}
    if(e.result?.items.some(item=>item.claims.some(claim=>!e.result.evidenceRelations.some(rel=>rel.targetId===claim.id)))){add(findings,'MISSING_CLAIM_EVIDENCE',`turns[${i}]`);missing=true;}
  }
  const types=new Set(turns.flatMap(e=>e?.actions?.map(a=>a.type)||[]));
  if(c.expected.requiredToolTypes.some(t=>!types.has(t))){add(findings,'MISSING_TOOL','execution');missing=true;}
  if(c.expected.evidenceRequired&&!turns.some(e=>e?.result?.evidence?.some(v=>str(v.id)||str(v.evidenceId)))){add(findings,'MISSING_EVIDENCE','execution');missing=true;}
  // 声明了故障不等于实际注入；普通失败不能替代注入点触达、生效及对应工具观察的证据。
  if(c.faultInjection!==undefined&&!faultSatisfied(c.faultInjection,faultReceipt,turns)) {
    add(findings,'FAULT_INJECTION_UNPROVEN','faultReceipt');
    return {engineeringStatus:'INSUFFICIENT_EVIDENCE',findings};
  }
  return {engineeringStatus:failed?'FAIL':missing?'INSUFFICIENT_EVIDENCE':'PASS',findings};
}

export async function runDataset(d,r,options={}) {
  const f=[...validateDataset(d),...validateRubric(r)];
  if(f.length)throw Object.assign(new Error('INVALID_INPUT'),{findings:f});
  if(!['plan','synthetic'].includes(options.mode))throw new Error('OFFLINE_MODES_ONLY');
  if(!str(options.candidateId)||!str(options.sourceSha)||!hash(options.datasetSha256)||!hash(options.rubricSha256))throw new Error('IDENTITY_REQUIRED');
  if(options.group!==undefined&&!['G2','G3','G4'].includes(options.group))throw new Error('INVALID_GROUP');
  const selected=options.group?d.cases.filter(c=>c.group===options.group):d.cases;
  const groups=new Set(selected.map(c=>c.group));if(groups.size!==1)throw new Error('MIXED_OR_EMPTY_GROUP');
  if(options.mode==='synthetic'&&(d.split==='holdout'||d.cases.some(c=>c.synthetic!==true||c.provenance.kind!=='SYNTHETIC_FAULT')))throw new Error('SYNTHETIC_ONLY');
  const report={recordType:'T08_S02_RESULT',schemaVersion:1,datasetId:d.datasetId,datasetRevision:d.revision,rubricRevision:r.revision,rubricStatus:r.status,group:[...groups][0],split:d.split,candidateId:options.candidateId,sourceSha:options.sourceSha,datasetSha256:options.datasetSha256,rubricSha256:options.rubricSha256,mode:options.mode==='plan'?'PLAN_ONLY':'SYNTHETIC_OFFLINE',providerCalls:0,scope:'OFFLINE_ENGINEERING_ONLY_NOT_MODEL_QUALITY',datasetTotalCaseIds:d.cases.map(c=>c.caseId),excludedCaseIds:d.cases.filter(c=>!selected.includes(c)).map(c=>c.caseId),expectedCaseIds:selected.map(c=>c.caseId),records:[]};
  for(const c of selected) {
    const row={caseId:c.caseId,group:c.group,split:d.split,candidateId:report.candidateId,sourceSha:report.sourceSha,datasetSha256:report.datasetSha256,rubricSha256:report.rubricSha256,synthetic:c.synthetic,engineeringStatus:'NOT_EXECUTED',qualityStatus:'NOT_EVALUABLE',execution:null,turns:[],faultInjection:c.faultInjection===undefined?null:{type:c.faultInjection?.type,point:c.faultInjection?.point},faultReceipt:null,metrics:{elapsedMillis:null,modelAttemptCount:0,providerResponseReceived:false,usage:{promptTokens:null,completionTokens:null,totalTokens:null},costCny:null,unknown:true},error:null,findings:[]};
    if(options.mode==='synthetic'&&typeof options.adapter==='function') {
      const start=performance.now();
      try {
        // 参考答案和expected不传给adapter，防止通过抄写评分条件伪造执行成绩。
        const raw=await options.adapter(structuredClone({caseId:c.caseId,group:c.group,input:c.input,synthetic:true,...(c.faultInjection===undefined?{}:{faultInjection:c.faultInjection})}));
        const rawTurns=Array.isArray(raw?.turns)?raw.turns:[raw];
        if([raw,...rawTurns].some(e=>e?.mode==='ONLINE'||e?.providerCalls>0||e?.providerResponseReceived===true||e?.modelAttemptCount>0||e?.metrics?.providerResponseReceived===true||e?.metrics?.modelAttemptCount>0))throw new Error('ONLINE_ADAPTER_FORBIDDEN');
        row.turns=rawTurns.map(safeExecution);row.execution=row.turns.at(-1)??null;
        row.faultReceipt=safeFaultReceipt(raw?.faultReceipt);
        Object.assign(row,evaluate(c,row.turns,row.faultReceipt));
      } catch {row.engineeringStatus=c.faultInjection===undefined?'FAIL':'INSUFFICIENT_EVIDENCE';if(c.faultInjection!==undefined)add(row.findings,'FAULT_INJECTION_UNPROVEN','faultReceipt');row.error={code:'ADAPTER_EXCEPTION',message:'Adapter failed; raw error omitted to avoid credential disclosure'};}
      finally {row.metrics.elapsedMillis=Math.max(0,performance.now()-start);}
    }
    report.records.push(row);
  }
  report.summary=aggregate(report);
  return report;
}

/** 单报告单身份；不允许把异组、异冻结输入或遗漏的失败记录合并为通过分母。 */
export function aggregate(report) {
  if(report.recordType!=='T08_S02_RESULT'||report.schemaVersion!==1||!['PLAN_ONLY','SYNTHETIC_OFFLINE'].includes(report.mode)||report.providerCalls!==0||!['G2','G3','G4'].includes(report.group)||!['development','regression','holdout'].includes(report.split)||!str(report.candidateId)||!str(report.sourceSha)||!hash(report.datasetSha256)||!hash(report.rubricSha256))throw new Error('RESULT_HEADER');
  const expected=report.expectedCaseIds;
  if(!Array.isArray(expected)||!expected.length||new Set(expected).size!==expected.length||!Array.isArray(report.records)||report.records.length!==expected.length)throw new Error('CASE_COVERAGE');
  const all=report.datasetTotalCaseIds,excluded=report.excludedCaseIds;
  if(!strings(all)||!strings(excluded)||new Set(all).size!==all.length||new Set([...expected,...excluded]).size!==all.length||expected.length+excluded.length!==all.length||![...expected,...excluded].every(id=>all.includes(id)))throw new Error('EXCLUDED_CASE_ACCOUNTING');
  const seen=new Set();const byEngineeringStatus=Object.fromEntries(STATUS.map(s=>[s,0]));
  for(const row of report.records) {
    if(seen.has(row.caseId)||!expected.includes(row.caseId))throw new Error('CASE_COVERAGE');seen.add(row.caseId);
    if(['group','split','candidateId','sourceSha','datasetSha256','rubricSha256'].some(k=>row[k]!==report[k]))throw new Error('MIXED_IDENTITY');
    if(!STATUS.includes(row.engineeringStatus)||row.qualityStatus!=='NOT_EVALUABLE')throw new Error('INVALID_STATUS');
    if(row.metrics?.costCny!==null||!['promptTokens','completionTokens','totalTokens'].every(k=>row.metrics?.usage?.[k]===null)||row.metrics?.modelAttemptCount!==0||row.metrics?.providerResponseReceived!==false)throw new Error('OFFLINE_UNKNOWN_METRICS');
    if(report.mode==='PLAN_ONLY'&&row.engineeringStatus!=='NOT_EXECUTED')throw new Error('PLAN_NOT_EXECUTED');
    if(row.engineeringStatus==='PASS'&&(!row.execution||!Array.isArray(row.turns)||!row.turns.length||row.turns.some(e=>!e||!str(e.taskId)||!str(e.turnId)||!str(e.runId)||!e.actions?.length||!e.observations?.length||!e.result)))throw new Error('PASS_WITHOUT_EXECUTION');
    if(row.engineeringStatus==='PASS'&&row.faultInjection&&!faultSatisfied(row.faultInjection,row.faultReceipt,row.turns))throw new Error('PASS_WITHOUT_FAULT_EVIDENCE');
    if(row.engineeringStatus==='NOT_EXECUTED'&&(row.execution!==null||row.turns?.length||row.metrics.elapsedMillis!==null))throw new Error('NOT_EXECUTED_TRACE');
    byEngineeringStatus[row.engineeringStatus]++;
  }
  return {total:report.records.length,datasetTotal:all.length,excludedCount:excluded.length,byEngineeringStatus,modelQualityEvaluable:false,qualityStatus:'NOT_EVALUABLE'};
}
