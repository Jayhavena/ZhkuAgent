export class RuleAgentContractError extends Error {
  constructor(){super('INVALID_RULE_AGENT_CONTRACT');this.name='RuleAgentContractError'}
}
const check=value=>{if(!value)throw new RuleAgentContractError()}
const fields=(x,names)=>{
  check(x!==null&&typeof x==='object'&&!Array.isArray(x))
  const keys=Object.keys(x),expected=names.split(' ')
  check(keys.length===expected.length&&keys.every(k=>expected.includes(k)))
}
const text=(x,max=2048)=>{
  check(typeof x==='string'&&x.trim().length>0&&x.length<=max)
  for(let i=0;i<x.length;i++){
    const c=x.charCodeAt(i)
    if(c>=0xd800&&c<=0xdbff){const n=x.charCodeAt(++i);check(n>=0xdc00&&n<=0xdfff)}
    else check(c<0xdc00||c>0xdfff)
  }
}
const id=x=>{text(x,128);check(/^[A-Za-z0-9][A-Za-z0-9_./:-]{0,127}$/.test(x))}
const uuid=x=>check(typeof x==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(x))
const integer=(x,min,max=Number.MAX_SAFE_INTEGER)=>check(Number.isSafeInteger(x)&&x>=min&&x<=max)
const list=(x,min,max)=>{check(Array.isArray(x)&&x.length>=min&&x.length<=max)}
const unique=x=>check(new Set(x).size===x.length)
const reasons={
  RETRIEVAL_NO_MATCH:'DATA_GAP',DETAIL_NOT_IN_REVIEWED_TEXT:'DATA_GAP',CURRENT_NOTICE_MISSING:'DATA_GAP',
  PERSONAL_RECORD_NOT_AVAILABLE:'DATA_GAP',REVIEWED_FACT_COVERAGE_GAP:'DATA_GAP',
  MATERIAL_SCOPE_MISMATCH:'SCOPE_LIMIT',CURRENT_APPLICABILITY_UNKNOWN:'SCOPE_LIMIT',
  MISSING_CONDITION:'CLARIFICATION',UNRESOLVED_CONFLICT:'CONFLICT',REPLAY_NOT_COVERED:'REPLAY_MISS',
  RETRIEVAL_UNAVAILABLE:'PROCESSING_FAILURE',MODEL_TIMEOUT:'PROCESSING_FAILURE',MODEL_RATE_LIMIT:'PROCESSING_FAILURE',
  INVALID_MODEL_RESPONSE:'PROCESSING_FAILURE',UNKNOWN_TOOL:'PROCESSING_FAILURE',EVIDENCE_CHANGED:'PROCESSING_FAILURE',
  AUDIT_UNAVAILABLE:'PROCESSING_FAILURE',BUDGET_EXCEEDED:'PROCESSING_FAILURE',CLARIFICATION_EXPIRED:'PROCESSING_FAILURE',
  CANCELLED:'PROCESSING_FAILURE',NO_PROGRESS:'PROCESSING_FAILURE',TASK_EXECUTION_UNAVAILABLE:'PROCESSING_FAILURE',
  MODEL_AUTH_FAILURE:'PROCESSING_FAILURE',MODEL_UNAVAILABLE:'PROCESSING_FAILURE',MODEL_CONNECTION_FAILURE:'PROCESSING_FAILURE',
  QUESTION_UNDERSTANDING_UNRESOLVED:'PROCESSING_FAILURE',ANSWER_COVERAGE_INCOMPLETE:'PROCESSING_FAILURE'
}
const coverage=items=>items.every(x=>x.coverage==='COMPLETE')?'COMPLETE':items.some(x=>x.claims.length)?'PARTIAL':'NONE'
function result(r){
  fields(r,'publishedVersion originatingRunId catalogueVersion referenceRevision coverage items evidence evidenceRelations')
  integer(r.publishedVersion,1);uuid(r.originatingRunId);text(r.catalogueVersion,128);text(r.referenceRevision,128)
  list(r.items,1,4);unique(r.items.map(x=>x.id));list(r.evidence,0,8);unique(r.evidence.map(x=>x.id));list(r.evidenceRelations,0,64)
  const targets=new Map(),relationKeys=[]
  let questionCount=0
  for(const q of r.items){
    fields(q,'id coverage claims issues clarification');id(q.id)
    list(q.claims,0,16);list(q.issues,0,8);list(q.clarification,0,1)
    const register=(target,role)=>{const key=`${q.id}|${target.id??target.conditionId}`;check(!targets.has(key));targets.set(key,{q,target,role,count:0})}
    for(const c of q.claims){
      fields(c,'id factId text when conditionSource');id(c.id);id(c.factId);text(c.text,8192)
      check(c.conditionSource==='USER_DECLARED');check(c.when!==null&&typeof c.when==='object'&&!Array.isArray(c.when));check(Object.keys(c.when).length<=16)
      for(const [k,v]of Object.entries(c.when)){id(k);text(v,128)}
      register(c,'CLAIM')
    }
    for(const i of q.issues){
      fields(i,'id kind reasonCode message nextStep');id(i.id);check(Object.hasOwn(reasons,i.reasonCode)&&reasons[i.reasonCode]===i.kind);text(i.message);text(i.nextStep)
      register(i,i.kind==='CONFLICT'?'CONFLICT':'SCOPE')
    }
    for(const c of q.clarification){
      fields(c,'conditionId question allowedValues');id(c.conditionId);text(c.question);list(c.allowedValues,2,32);unique(c.allowedValues);c.allowedValues.forEach(x=>text(x,128));register(c,'CLARIFICATION');questionCount++
    }
    check((q.clarification.length>0)===q.issues.some(i=>i.kind==='CLARIFICATION'))
    const expected=q.claims.length?(q.issues.length||q.clarification.length?'PARTIAL':'COMPLETE'):'NONE'
    check(q.coverage===expected)
  }
  check(questionCount<=1&&r.coverage===coverage(r.items))
  const evidenceMap=new Map(r.evidence.map(e=>[e.id,e])),used=new Set()
  for(const e of r.evidence){
    fields(e,'id sourceId chunkId revision checksum startInclusive endExclusive quote originalToken')
    id(e.id);uuid(e.sourceId);uuid(e.chunkId);uuid(e.originalToken);check(e.revision===r.referenceRevision)
    check(typeof e.checksum==='string'&&/^[a-f0-9]{64}$/.test(e.checksum));text(e.quote,4096)
    integer(e.startInclusive,0);integer(e.endExclusive,1);check(e.endExclusive-e.startInclusive===e.quote.length)
  }
  check(r.evidence.reduce((bytes,e)=>bytes+new TextEncoder().encode(e.quote).byteLength,0)<=32768)
  for(const relation of r.evidenceRelations){
    fields(relation,'evidenceId subquestionId role targetId')
    id(relation.evidenceId);id(relation.subquestionId);id(relation.targetId)
    const target=targets.get(`${relation.subquestionId}|${relation.targetId}`)
    check(evidenceMap.has(relation.evidenceId)&&target&&target.role===relation.role)
    const key=JSON.stringify([relation.evidenceId,relation.subquestionId,relation.role,relation.targetId]);check(!relationKeys.includes(key));relationKeys.push(key)
    target.count++;used.add(relation.evidenceId)
  }
  // 只接收对应主张/必要条件/冲突的证据；来源合格本身不等于值得展示。
  check(used.size===r.evidence.length)
  for(const x of targets.values()){if(x.role==='CLAIM')check(x.count>=1);if(x.role==='CONFLICT')check(x.count>=2)}
  return questionCount
}
export function validateRuleAgentSnapshot(x){
  fields(x,'schemaVersion taskId turnId runId version taskStatus runState terminated result continuationToken execution')
  check(x.schemaVersion===3);uuid(x.taskId);uuid(x.turnId);uuid(x.runId);integer(x.version,1)
  check(['ACTIVE','WAITING_USER','RESOLVED','STOPPED','FAILED'].includes(x.taskStatus))
  check(['CREATED','RUNNING','STOP_REQUESTED','SUCCEEDED','INCOMPLETE','FAILED','CANCELLED'].includes(x.runState))
  check(typeof x.terminated==='boolean')
  const terminal=['SUCCEEDED','INCOMPLETE','FAILED','CANCELLED'].includes(x.runState)
  check(!x.terminated||terminal)
  fields(x.execution,'mode modelAttemptCount toolStepCount providerResponseReceived answerSource replayCoverage')
  const e=x.execution
  integer(e.modelAttemptCount,0,4);integer(e.toolStepCount,0,6);check(typeof e.providerResponseReceived==='boolean')
  check(['ONLINE','ENGINEERING_REPLAY'].includes(e.mode));check(['NONE','LIVE_AGENT','SEALED_REPLAY','VERIFIED_RULE_ONLY'].includes(e.answerSource))
  check(['NOT_APPLICABLE','HIT','MISS'].includes(e.replayCoverage))
  check(!e.providerResponseReceived||e.modelAttemptCount>0)
  if(e.mode==='ONLINE'){check(e.replayCoverage==='NOT_APPLICABLE'&&e.answerSource!=='SEALED_REPLAY')}
  else check(e.modelAttemptCount===0&&!e.providerResponseReceived&&e.replayCoverage!=='NOT_APPLICABLE'&&e.answerSource!=='LIVE_AGENT')
  if(e.answerSource==='LIVE_AGENT')check(e.providerResponseReceived&&e.modelAttemptCount>0)
  if(e.answerSource==='SEALED_REPLAY')check(e.replayCoverage==='HIT')
  const questions=x.result===null?0:result(x.result)
  if(x.continuationToken!==null)uuid(x.continuationToken)
  if(x.result!==null)check(x.result.publishedVersion<=x.version)
  if(x.taskStatus==='ACTIVE'){
    check(!x.terminated&&x.continuationToken===null)
    if(x.result!==null)check(x.result.publishedVersion<x.version)
  }else{
    check(x.terminated&&x.result!==null&&x.result.publishedVersion===x.version&&x.result.originatingRunId.toLowerCase()===x.runId.toLowerCase())
    if(x.taskStatus==='WAITING_USER')check(x.runState==='INCOMPLETE'&&questions===1&&x.continuationToken!==null)
    else{
      check(questions===0&&x.continuationToken===null)
      if(x.taskStatus==='RESOLVED')check(['SUCCEEDED','INCOMPLETE'].includes(x.runState))
      if(x.taskStatus==='FAILED')check(x.runState==='FAILED'&&x.result.items.some(q=>q.issues.some(i=>i.kind==='PROCESSING_FAILURE')))
      if(x.taskStatus==='STOPPED')check(['CANCELLED','INCOMPLETE'].includes(x.runState)&&x.result.items.some(q=>q.issues.some(i=>['CANCELLED','BUDGET_EXCEEDED','NO_PROGRESS'].includes(i.reasonCode))))
    }
  }
  if(e.replayCoverage==='MISS'&&x.taskStatus!=='ACTIVE')check(x.result.items.some(q=>q.issues.some(i=>i.reasonCode==='REPLAY_NOT_COVERED')))
  return x
}
