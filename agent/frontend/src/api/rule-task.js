import {createRuleCheckClient,RuleCheckError} from './rule-check.js'
import {validateRuleAgentSnapshot} from './rule-agent-contract.js'
const uuid=x=>typeof x==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(x)
const requireId=x=>{if(!uuid(x))throw new RuleCheckError('INVALID_TASK_REQUEST');return x}
export function validateAccepted(x){
  const keys=['taskId','turnId','runId','version','duplicate']
  if(!x||Object.keys(x).length!==keys.length||Object.keys(x).some(k=>!keys.includes(k))||!uuid(x.taskId)||!uuid(x.turnId)||!uuid(x.runId)||!Number.isSafeInteger(x.version)||x.version<1||typeof x.duplicate!=='boolean')throw new RuleCheckError('BAD_RESPONSE')
  return x
}
export function createRuleTaskClient(options={}){
  const auth=createRuleCheckClient(options),read=path=>auth.taskRequest('GET',path),write=async(path,body)=>validateAccepted(await auth.taskRequest('POST',path,body))
  return {
    ...auth,
    capabilities:async()=>{const x=await read('/tasks/capabilities');if(x.schemaVersion!==3||x.asynchronous!==true||Object.keys(x).length!==2)throw new RuleCheckError('BAD_RESPONSE');return x},
    create:(question,clientRequestId)=>write('/tasks',{question,clientRequestId:requireId(clientRequestId)}),
    snapshot:async task=>validateRuleAgentSnapshot(await read(`/tasks/${requireId(task)}`)),
    message:(task,body)=>write(`/tasks/${requireId(task)}/messages`,body),
    cancel:(task,body)=>write(`/tasks/${requireId(task)}/cancel`,body),
    lookup:async(action,key)=>{if(!['CREATE','MESSAGE','CANCEL'].includes(action))throw new RuleCheckError('INVALID_TASK_REQUEST');return validateAccepted(await read(`/tasks/requests/${action}/${requireId(key)}`))},
    original:(task,token)=>auth.taskOriginal(task,token)
  }
}
export const ruleTaskClient=createRuleTaskClient()
