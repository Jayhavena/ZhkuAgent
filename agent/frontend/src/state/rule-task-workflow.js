import {reactive,watch} from 'vue'
import {ruleTaskClient,validateAccepted} from '../api/rule-task.js'
import {validateRuleAgentSnapshot} from '../api/rule-agent-contract.js'
import {ruleCheckSession} from './rule-check-session.js'
import {ruleTaskHistory} from './rule-task-history.js'

/** 本地只管理请求和显示；任务成功、资料缺失、取消退出均取服务端快照。 */
export function createRuleTaskWorkflow({client=ruleTaskClient,session=ruleCheckSession,history=ruleTaskHistory,keyFactory=()=>crypto.randomUUID(),schedule=(fn,ms)=>setTimeout(fn,ms),unschedule=id=>clearTimeout(id),now=()=>Date.now()}={}){
  const state=reactive({question:'',ticket:null,snapshot:null,pending:null,busy:false,polling:false,cancelRequested:false,error:'',original:null,originalBusy:false,evidenceUnavailable:false,recent:[]})
  let generation=0,originalGeneration=0,timer=null,alive=true,deadline=0
  const usable=()=>alive&&!session.state.identityBusy&&session.state.actor?.role==='USER'
  const guard=()=>{const g=generation,e=session.state.epoch;return()=>alive&&g===generation&&e===session.state.epoch}
  function stopPolling(){if(timer!==null)unschedule(timer);timer=null;state.polling=false}
  function reset(){generation++;originalGeneration++;stopPolling();client.cancelResults();Object.assign(state,{question:'',ticket:null,snapshot:null,pending:null,busy:false,cancelRequested:false,error:'',original:null,originalBusy:false,evidenceUnavailable:false})}
  // clear先递增epoch，再写reason；此处只撤回内容，不能用上一轮reason误删恢复元数据。
  const stop=watch(()=>session.state.epoch,()=>{reset();state.recent=[]},{flush:'sync'})
  const stopReason=watch(()=>session.state.reason,reason=>{
    if(['LOGOUT','LOGOUT_UNCONFIRMED','IDENTITY_CHANGED','IDENTITY_CHANGED_ELSEWHERE'].includes(reason))history.clear()
  },{flush:'sync'})
  function recent(){state.recent=usable()?history.list(session.state.actor.userId):[]}
  function remember(ticket){if(!usable())return;history.remember(session.state.actor.userId,ticket);recent()}
  async function resume(taskId){
    if(!usable()||state.busy||state.pending||state.snapshot?.taskStatus==='ACTIVE')return
    recent();if(!state.recent.some(x=>x.taskId===taskId))return
    reset();const current=guard(),owner=session.state.actor.userId;state.busy=true
    try{
      // 本地ID不授予访问权；只有本次会话取得的真实快照才能恢复澄清/原文权限。
      const value=validateRuleAgentSnapshot(await client.snapshot(taskId));if(!current())return
      if(value.taskId!==taskId)throw new Error('BAD_RESPONSE')
      state.question='已恢复的查证任务';state.ticket={taskId:value.taskId,runId:value.runId,turnId:value.turnId,version:value.version,duplicate:false};state.snapshot=value;remember(state.ticket)
      deadline=now()+900000;if(value.taskStatus==='ACTIVE')queuePoll(0)
    }catch(error){if(current()){
      if([404,409].includes(error.status)){history.forget(owner,taskId);recent();state.error='这条任务已过期或不可访问，请新建查证。'}
      else state.error='任务恢复尚未确认，请稍后重试；不会自动重新提交。'
    }}finally{if(current())state.busy=false}
  }
  function queuePoll(delay=800){stopPolling();if(!alive||!state.ticket)return;state.polling=true;timer=schedule(()=>{timer=null;return refresh()},delay)}
  async function refresh(){
    if(!usable()||!state.ticket||state.busy)return
    const current=guard(),ticket={...state.ticket};stopPolling()
    try{
      const value=validateRuleAgentSnapshot(await client.snapshot(ticket.taskId));if(!current())return
      const prior=state.snapshot
      if(value.taskId!==ticket.taskId||value.runId!==ticket.runId||value.turnId!==ticket.turnId||value.version<ticket.version||prior&&value.version<prior.version||prior?.terminated&&value.version===prior.version&&!value.terminated)throw new Error('BAD_RESPONSE')
      state.snapshot=value;state.error='';remember({taskId:value.taskId,runId:value.runId,turnId:value.turnId,version:value.version})
      if(value.taskStatus==='ACTIVE'){
        if(now()>=deadline){state.error='本地查询等待已到时限，任务状态仍未确认；可主动继续查询。';return}
        queuePoll()
      }
    }catch(error){if(current()&&error.code!=='STALE_RESPONSE'){
      if([404,409].includes(error.status)){
        // 后端资格拒绝是撤回信号，不能按临时网络故障继续保留旧结论/原文。
        history.forget(session.state.actor.userId,ticket.taskId);reset();recent()
        state.evidenceUnavailable=true;state.error='任务或依据已过期或不可访问，旧结果已撤回，请重新查证。'
      }else state.error='任务查询未确认，已保留任务；请继续查询，不必重复提交问题。'
    }}
  }
  function install(ticket,action){
    validateAccepted(ticket)
    if(action!=='CREATE'&&state.ticket&&ticket.taskId!==state.ticket.taskId)throw new Error('BAD_RESPONSE')
    if(action==='MESSAGE'){state.snapshot=null;state.original=null;state.cancelRequested=false}
    state.ticket=ticket;remember(ticket);state.pending=null;state.error='';if(action==='CANCEL')state.cancelRequested=true
    deadline=now()+900000;queuePoll(0)
  }
  async function mutate(action,body){
    if(!usable()||state.busy||state.pending)return
    const current=guard(),task=state.ticket?.taskId;stopPolling();state.busy=true;state.error='';state.pending={action,clientRequestId:body.clientRequestId}
    try{
      const ticket=await(action==='CREATE'?client.create(body.question,body.clientRequestId):action==='MESSAGE'?client.message(task,body):client.cancel(task,body));if(current())install(ticket,action)
    }catch(error){if(current()&&error.code!=='STALE_RESPONSE'){
      const unknown=error.code==='NETWORK_UNAVAILABLE'||error.code==='BAD_RESPONSE'||!error.status
      if(unknown)state.error='受理结果尚未确认，请按原请求查询；不会自动重新提交。'
      else{state.pending=null;state.error=error.status===409?'任务版本或条件已更新，请继续查询最新状态。':error.status===422?'补充未获确认，请只回答本轮必要条件。':'请求未获确认，请保留任务并稍后查询。'}
    }}finally{if(current())state.busy=false}
  }
  return {
    state,reset,refresh,resume,
    async restoreLatest(){recent();if(!state.ticket&&state.recent.length)await resume(state.recent[0].taskId)},
    async start(question){if(!usable()||state.busy||state.pending||state.ticket&&state.snapshot?.taskStatus==='ACTIVE')return;if(typeof question!=='string'||!question.trim()||question.length>2048)return;reset();state.question=question;await mutate('CREATE',{question,clientRequestId:keyFactory()})},
    async recover(){if(!usable()||state.busy||!state.pending)return;const current=guard(),pending={...state.pending};state.busy=true;try{const ticket=await client.lookup(pending.action,pending.clientRequestId);if(current())install(ticket,pending.action)}catch(error){if(current()&&error.code!=='STALE_RESPONSE')state.error='原请求受理情况仍未确认，请稍后按原请求查询。'}finally{if(current())state.busy=false}},
    async clarify(value){const s=state.snapshot,c=s?.result?.items.flatMap(q=>q.clarification)[0];if(s?.taskStatus!=='WAITING_USER'||!c||!c.allowedValues.includes(value)){state.error='请只确认本轮要求的条件。';return}await mutate('MESSAGE',{message:value,continuationToken:s.continuationToken,expectedVersion:s.version,clientRequestId:keyFactory()})},
    async cancel(){const s=state.snapshot,t=state.ticket;if(!t||s&&s.taskStatus!=='ACTIVE'||state.cancelRequested)return;await mutate('CANCEL',{runId:t.runId,expectedVersion:s?.version??t.version,clientRequestId:keyFactory()})},
    async openOriginal(token){if(!usable()||!state.snapshot?.result||state.evidenceUnavailable||!state.snapshot.result.evidence.some(e=>e.originalToken===token))return;const current=guard(),originalRound=++originalGeneration;state.original=null;state.originalBusy=true;try{const value=await client.original(state.ticket.taskId,token);if(current()&&originalRound===originalGeneration)state.original=value}catch(error){if(current()&&originalRound===originalGeneration&&error.code!=='STALE_RESPONSE'){if([404,409].includes(error.status))state.evidenceUnavailable=true;state.error=[404,409].includes(error.status)?'依据资格或版本已变化，旧结果暂停展示；请重新查证。':'原文查询未确认，请稍后重试。'}}finally{if(current()&&originalRound===originalGeneration)state.originalBusy=false}},
    dispose(){alive=false;stop();stopReason();reset()}
  }
}
