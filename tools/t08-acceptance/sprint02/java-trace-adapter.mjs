const HEADER_FIELDS=['recordType','schemaVersion','sourceLayer','executionMode','providerCalls','candidateId','sourceSha','caseId','datasetSha256','traces'];
const TRACE_FIELDS=['taskId','turnId','runId','version','taskStatus','runState','exceptionalFailure','truncated','actions','observations','result'];
const ERROR_CODES=new Set(['FIELD','UNSAFE_VALUE','ENUM','MISSING_RESULT','DUPLICATE_ID','VERSION_OR_SOURCE',
  'IDENTITY','REQUIRED_SINGLE_CASE','LOCAL_ID','COVERAGE','MISSING_TRACE','STEP','OBSERVATION_LINK','NOT_OBSERVED',
  'OBSERVATION_STATE','REVISION','RESULT_LINK','ISSUE_STATE','RESULT_STATE','UNPROVEN_RESULT','STATE',
  'FORMAT','LIMIT','DUPLICATE_KEY','UNSAFE_KEY','MISSING'].map(code=>`JAVA_EXPORT_${code}`));
// 前缀相同不等于已知错误；只允许审查过的固定码跨过日志/报告边界。
export const javaExportErrorCode=error=>ERROR_CODES.has(error?.message)?error.message:null;
// 不接受“看起来像枚举”的任意字符串，只承认已审查的 Java 契约值。
const ACTION_TYPES=['SEARCH_REVIEWED_RULES','READ_VERIFIED_RULE','ASK_NECESSARY_CONDITION','FINISH_CHECK','PUBLICATION_REVIEW','INVALID'];
const REASONS={
  RETRIEVAL_NO_MATCH:'DATA_GAP',DETAIL_NOT_IN_REVIEWED_TEXT:'DATA_GAP',CURRENT_NOTICE_MISSING:'DATA_GAP',
  PERSONAL_RECORD_NOT_AVAILABLE:'DATA_GAP',REVIEWED_FACT_COVERAGE_GAP:'DATA_GAP',MATERIAL_SCOPE_MISMATCH:'SCOPE_LIMIT',
  CURRENT_APPLICABILITY_UNKNOWN:'SCOPE_LIMIT',MISSING_CONDITION:'CLARIFICATION',UNRESOLVED_CONFLICT:'CONFLICT',REPLAY_NOT_COVERED:'REPLAY_MISS',
  ...Object.fromEntries(['RETRIEVAL_UNAVAILABLE','MODEL_TIMEOUT','MODEL_RATE_LIMIT','INVALID_MODEL_RESPONSE','UNKNOWN_TOOL',
    'EVIDENCE_CHANGED','AUDIT_UNAVAILABLE','BUDGET_EXCEEDED','CLARIFICATION_EXPIRED','CANCELLED','NO_PROGRESS','TASK_EXECUTION_UNAVAILABLE',
    'MODEL_AUTH_FAILURE','MODEL_UNAVAILABLE','MODEL_CONNECTION_FAILURE','QUESTION_UNDERSTANDING_UNRESOLVED','ANSWER_COVERAGE_INCOMPLETE'].map(k=>[k,'PROCESSING_FAILURE'])),
};
const UUID=/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
const object=v=>v!==null&&typeof v==='object'&&!Array.isArray(v);
const fail=code=>{throw new Error(`JAVA_EXPORT_${code}`);};
function fields(value,allowed) {
  if(!object(value)||Object.keys(value).some(k=>!allowed.includes(k)))fail('FIELD');
}
function safeString(value,pattern) {
  if(typeof value!=='string'||UUID.test(value)||!pattern.test(value))fail('UNSAFE_VALUE');
  return value;
}
const local=(value,kind)=>safeString(value,new RegExp(`^${kind}-[1-9][0-9]{0,3}$`));
function oneOf(value,allowed) {if(!allowed.includes(value))fail('ENUM');return value;}
function array(value) {if(!Array.isArray(value))fail('MISSING_RESULT');return value;}
function unique(values) {if(new Set(values).size!==values.length)fail('DUPLICATE_ID');}

/** 仅校验脱敏结构；JSON 自报的 sourceLayer/SHA 不能证明执行过 Java。 */
export function importJavaTraceExport(doc,identity) {
  fields(doc,HEADER_FIELDS);
  if(doc.recordType!=='T08_JAVA_TRACE_EXPORT'||doc.schemaVersion!==1||
      doc.sourceLayer!=='ACTUAL_JAVA_RUNNER_OFFLINE_SIMULATED'||doc.executionMode!=='OFFLINE_SIMULATED'||
      doc.providerCalls!==0)fail('VERSION_OR_SOURCE');
  for(const key of ['candidateId','caseId'])safeString(doc[key],/^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$/);
  if(!identity||['candidateId','caseId','sourceSha','datasetSha256'].some(k=>doc[k]!==identity[k])||
      !/^[a-f0-9]{40}(?:[a-f0-9]{24})?$/.test(doc.sourceSha)||!/^[a-f0-9]{64}$/.test(doc.datasetSha256))fail('IDENTITY');
  // 只有一个单轮开发案例，禁止把局部标签当作跨轮身份或版本递增证明。
  if(!Array.isArray(doc.traces)||doc.traces.length!==1)fail('REQUIRED_SINGLE_CASE');
  return doc.traces.map(validateTrace);
}
function validateTrace(trace) {
  fields(trace,TRACE_FIELDS);
  if(trace.taskId!=='task-1'||trace.turnId!=='turn-1'||trace.runId!=='run-1')fail('LOCAL_ID');
  if(trace.version!==2||typeof trace.exceptionalFailure!=='boolean'||typeof trace.truncated!=='boolean')fail('COVERAGE');
  oneOf(trace.taskStatus,['WAITING_USER','RESOLVED','STOPPED','FAILED']);
  oneOf(trace.runState,['SUCCEEDED','INCOMPLETE','CANCELLED','FAILED']);
  if(!Array.isArray(trace.actions)||!Array.isArray(trace.observations))fail('MISSING_TRACE');
  if(trace.actions.length>24)fail('STEP');
  const actions=trace.actions.map((a,i)=>{
    fields(a,['actionId','type','step','modelAttempt']);local(a.actionId,'action');oneOf(a.type,ACTION_TYPES);
    if(a.actionId!==`action-${i+1}`||a.step!==i+1||!Number.isSafeInteger(a.modelAttempt)||a.modelAttempt<1||a.modelAttempt>10000||
        (i&&a.modelAttempt<trace.actions[i-1].modelAttempt))fail('STEP');
    return {...a};
  });
  unique(actions.map(a=>a.actionId));
  if(trace.observations.length!==actions.length)fail('OBSERVATION_LINK');
  const observations=trace.observations.map(o=>{
    fields(o,['actionId','status','dataLevel','reasonCode']);local(o.actionId,'action');
    const action=actions.find(a=>a.actionId===o.actionId);if(!action)fail('OBSERVATION_LINK');
    oneOf(o.status,['SUCCEEDED','NO_MATCH','REJECTED','UNAVAILABLE','NOT_OBSERVED']);
    if(o.reasonCode!==undefined)oneOf(o.reasonCode,Object.keys(REASONS));
    const tool=['SEARCH_REVIEWED_RULES','READ_VERIFIED_RULE'].includes(action.type);
    if(!tool) {
      if(o.status!=='NOT_OBSERVED'||o.dataLevel!==null)fail('NOT_OBSERVED');
    } else if(o.status==='SUCCEEDED') {
      if(o.reasonCode!==undefined)fail('OBSERVATION_STATE');
      oneOf(o.dataLevel,action.type==='SEARCH_REVIEWED_RULES'?['CANDIDATE']:['VERIFIED_EVIDENCE','VERIFIED_FACT']);
    } else {
      const reasons={NO_MATCH:['RETRIEVAL_NO_MATCH'],UNAVAILABLE:['RETRIEVAL_UNAVAILABLE','AUDIT_UNAVAILABLE'],
        REJECTED:['UNKNOWN_TOOL','EVIDENCE_CHANGED','MATERIAL_SCOPE_MISMATCH','BUDGET_EXCEEDED']};
      if(o.dataLevel!=='NONE'||!reasons[o.status]?.includes(o.reasonCode))fail('OBSERVATION_STATE');
    }
    return {...o};
  });
  unique(observations.map(o=>o.actionId));
  const result=trace.result;fields(result,['coverage','evidence','evidenceRelations','items','issues']);
  oneOf(result.coverage,['COMPLETE','PARTIAL','NONE']);
  const evidence=array(result.evidence).map(e=>{
    fields(e,['id','revision']);local(e.id,'evidence');
    if(e.revision!=='2025.9.2')fail('REVISION');return {...e};
  });
  const items=array(result.items).map(item=>{
    fields(item,['claims']);return {claims:array(item.claims).map(c=>{
      fields(c,['id','factId']);local(c.id,'claim');safeString(c.factId,/^sha256:[a-f0-9]{64}$/);return {...c};
    })};
  });
  const claims=items.flatMap(i=>i.claims);unique(evidence.map(e=>e.id));unique(claims.map(c=>c.id));
  const evidenceRelations=array(result.evidenceRelations).map(r=>{
    fields(r,['targetId','evidenceId','role']);local(r.targetId,'claim');local(r.evidenceId,'evidence');
    if(r.role!=='CLAIM'||!claims.some(c=>c.id===r.targetId)||!evidence.some(e=>e.id===r.evidenceId))fail('RESULT_LINK');
    return {...r};
  });
  unique(evidenceRelations.map(r=>`${r.targetId}|${r.evidenceId}`));
  if(claims.some(c=>!evidenceRelations.some(r=>r.targetId===c.id))||evidence.some(e=>!evidenceRelations.some(r=>r.evidenceId===e.id)))fail('RESULT_LINK');
  const issues=array(result.issues).map(i=>{
    fields(i,['kind','reasonCode']);oneOf(i.reasonCode,Object.keys(REASONS));
    if(i.kind!==REASONS[i.reasonCode])fail('ISSUE_STATE');return {...i};
  });
  const verifiedRead=actions.some(a=>a.type==='READ_VERIFIED_RULE'&&observations.some(o=>
    o.actionId===a.actionId&&o.status==='SUCCEEDED'&&['VERIFIED_EVIDENCE','VERIFIED_FACT'].includes(o.dataLevel)));
  // 失败观察能证明执行尝试，不能为仍然保留的claims/evidence证明读取成功。
  if((claims.length||evidence.length)&&!verifiedRead)fail('UNPROVEN_RESULT');
  if(result.coverage==='COMPLETE'&&(!items.length||items.some(i=>!i.claims.length)||!evidence.length||!evidenceRelations.length||issues.length))fail('MISSING_RESULT');
  if(result.coverage==='NONE'&&claims.length||result.coverage==='PARTIAL'&&!claims.length)fail('RESULT_STATE');
  const pairs={RESOLVED:result.coverage==='COMPLETE'?['SUCCEEDED']:['INCOMPLETE'],WAITING_USER:['INCOMPLETE'],STOPPED:['CANCELLED','INCOMPLETE'],FAILED:['FAILED']};
  if(!pairs[trace.taskStatus].includes(trace.runState)||
      (trace.taskStatus!=='RESOLVED'&&result.coverage==='COMPLETE')||
      (trace.taskStatus==='FAILED'&&!issues.some(i=>i.kind==='PROCESSING_FAILURE'))||
      (trace.taskStatus==='STOPPED'&&!issues.some(i=>['CANCELLED','BUDGET_EXCEEDED','NO_PROGRESS'].includes(i.reasonCode))))fail('STATE');
  return {taskId:trace.taskId,turnId:trace.turnId,runId:trace.runId,version:trace.version,taskStatus:trace.taskStatus,runState:trace.runState,
    traceCoverage:{truncated:trace.truncated,exceptionalFailure:trace.exceptionalFailure},actions,observations,
    result:{coverage:result.coverage,evidence,evidenceRelations,items,issues}};
}
/** 聚合复验结构与内部一致性；不持有数据集expected，不替代数据集相对的评测。 */
export function validateJavaExecution(execution) {
  fields(execution,['taskId','turnId','runId','version','taskStatus','runState','traceCoverage','actions','observations','result']);
  fields(execution.traceCoverage,['truncated','exceptionalFailure']);
  const {traceCoverage,...trace}=execution;
  return validateTrace({...trace,...traceCoverage});
}
