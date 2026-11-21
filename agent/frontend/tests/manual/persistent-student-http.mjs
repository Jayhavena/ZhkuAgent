// 只用于具名隔离实例；不读真实账号或供应商密钥，报告不保存Cookie、CSRF、口令或原文。
import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {writeFile,readFile,mkdir} from 'node:fs/promises'
import {resolve} from 'node:path'

const base=process.env.ZHKU_STUDENT_TEST_BASE_URL
assert.match(base||'',/^http:\/\/127\.0\.0\.1:\d{4,5}$/)
assert.notEqual(new URL(base).port,'54329')
const output=resolve(process.env.ZHKU_STUDENT_TEST_OUTPUT||'tmp/persistent-student-http')
const phase=process.argv[2]||'initial'
assert.ok(['initial','restart'].includes(phase))
await mkdir(output,{recursive:true})
const password='Synthetic-Student-Only-20261111!'
const question='我这门选修课没过，能不能重修？'
const checks=[]
class User {
  cookies=new Map(); csrf=null
  async request(method,path,body,token=true,form=false){
    const headers={Accept:'application/json',Cookie:[...this.cookies].map(([k,v])=>`${k}=${v}`).join('; ')}
    if(body!==undefined)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json'
    if(token&&this.csrf)headers[this.csrf.headerName]=this.csrf.token
    const r=await fetch(base+'/api/zhku/controlled-preview/v2'+path,{method,headers,
      body:body===undefined?undefined:form?new URLSearchParams(body).toString():JSON.stringify(body),signal:AbortSignal.timeout(10000)})
    for(const cookie of r.headers.getSetCookie()){const pair=cookie.split(';')[0];const n=pair.indexOf('=');this.cookies.set(pair.slice(0,n),pair.slice(n+1))}
    const text=await r.text();return {status:r.status,data:text?JSON.parse(text):null}
  }
  async token(){this.csrf=(await this.request('GET','/auth/csrf',undefined,false)).data}
  async register(name){await this.token();const r=await this.request('POST','/auth/register',{username:name,password,confirmPassword:password});assert.equal(r.status,201)}
  async login(name){await this.token();const r=await this.request('POST','/auth/login',{username:name,password,'remember-me':'false'},true,true);assert.equal(r.status,200);await this.token()}
}
async function terminal(user,id){
  const until=Date.now()+20000
  while(Date.now()<until){const r=await user.request('GET','/tasks/'+id);assert.equal(r.status,200);if(r.data.taskStatus!=='ACTIVE')return r.data;await new Promise(r=>setTimeout(r,100))}
  throw new Error('task did not terminate within local test deadline')
}
async function check(name,fn){await fn();checks.push({name,passed:true})}
const a=new User(),b=new User(),anonymous=new User()
let oldTask
try {
  await check('anonymous task access denied',async()=>assert.equal((await anonymous.request('GET','/tasks/capabilities')).status,401))
  if(phase==='initial'){
    await check('register isolated synthetic accounts',async()=>{await a.register('persistent-student-a');await b.register('persistent-student-b')})
  }
  await check('persistent accounts login',async()=>{await a.login('persistent-student-a');await b.login('persistent-student-b')})
  await check('task API and explicit simulated mode available',async()=>{
    assert.equal((await a.request('GET','/tasks/capabilities')).status,200)
    const r=await a.request('GET','/tasks/runtime');assert.equal(r.status,200);assert.equal(r.data.mode,'SIMULATED');assert.equal(r.data.simulatedQuestion,question)
  })
  if(phase==='restart'){
    const receipt=JSON.parse(await readFile(resolve(output,'initial.json'),'utf8'));oldTask=receipt.taskId
    await check('old task cannot cross restart/session boundary',async()=>assert.equal((await a.request('GET','/tasks/'+oldTask)).status,404))
  }
  await check('CSRF missing request rejected',async()=>assert.equal((await a.request('POST','/tasks',{question,clientRequestId:randomUUID()},false)).status,403))
  const request={question,clientRequestId:randomUUID()};let accepted
  await check('create accepted and idempotent duplicate has one run',async()=>{
    let r=await a.request('POST','/tasks',request);assert.equal(r.status,202);accepted=r.data
    r=await a.request('POST','/tasks',request);assert.equal(r.status,202);assert.equal(r.data.runId,accepted.runId);assert.equal(r.data.duplicate,true)
  })
  const waiting=await terminal(a,accepted.taskId)
  await check('reviewed evidence produces necessary clarification without model',async()=>{
    assert.equal(waiting.taskStatus,'WAITING_USER');assert.equal(waiting.execution.mode,'ENGINEERING_REPLAY');assert.equal(waiting.execution.modelAttemptCount,0);assert.equal(waiting.execution.providerResponseReceived,false)
  })
  const proof=waiting.result.evidence[0];assert.ok(proof)
  await check('same-owner original readable and other account isolated',async()=>{
    assert.equal((await a.request('GET',`/tasks/${accepted.taskId}/originals/${proof.originalToken}`)).status,200)
    assert.equal((await b.request('GET','/tasks/'+accepted.taskId)).status,404)
    assert.equal((await b.request('GET',`/tasks/${accepted.taskId}/originals/${proof.originalToken}`)).status,404)
  })
  const reply={message:'公共选修课',continuationToken:waiting.continuationToken,expectedVersion:waiting.version,clientRequestId:randomUUID()}
  await check('clarification continuation accepted and idempotent',async()=>{
    const one=await a.request('POST',`/tasks/${accepted.taskId}/messages`,reply);assert.equal(one.status,202)
    const two=await a.request('POST',`/tasks/${accepted.taskId}/messages`,reply);assert.equal(two.status,202);assert.equal(two.data.runId,one.data.runId);assert.equal(two.data.duplicate,true)
  })
  const done=await terminal(a,accepted.taskId)
  await check('resolved answer uses published claims and zero model metadata',async()=>{
    assert.equal(done.taskStatus,'RESOLVED');assert.ok(done.result.items.some(i=>i.claims.length));assert.equal(done.execution.modelAttemptCount,0)
    assert.equal((await a.request('GET','/tasks/'+accepted.taskId)).data.runId,done.runId)
  })
  await check('uncovered input is disclosed as replay miss',async()=>{
    const r=await a.request('POST','/tasks',{question:'合成未覆盖问题',clientRequestId:randomUUID()});assert.equal(r.status,202)
    const miss=await terminal(a,r.data.taskId);assert.equal(miss.execution.replayCoverage,'MISS');assert.equal(miss.execution.modelAttemptCount,0)
    assert.ok(miss.result.items.some(i=>i.issues.some(x=>x.reasonCode==='REPLAY_NOT_COVERED')))
  })
  await check('cancel endpoint accepts active work or reports completed-state conflict',async()=>{
    const r=await a.request('POST','/tasks',{question,clientRequestId:randomUUID()});assert.equal(r.status,202)
    const cancel=await a.request('POST',`/tasks/${r.data.taskId}/cancel`,{runId:r.data.runId,expectedVersion:r.data.version,clientRequestId:randomUUID()})
    assert.ok([202,409].includes(cancel.status));const stopped=await terminal(a,r.data.taskId)
    if(cancel.status===202){assert.equal(stopped.taskStatus,'STOPPED');assert.equal(stopped.runState,'CANCELLED')}
    checks.push({name:'observed cancellation outcome',passed:true,httpStatus:cancel.status,terminalStatus:stopped.taskStatus})
  })
  await check('logout invalidates task access',async()=>{
    assert.equal((await a.request('POST','/auth/logout')).status,204)
    assert.equal((await a.request('GET','/tasks/'+accepted.taskId)).status,401)
  })
  await writeFile(resolve(output,phase+'.json'),JSON.stringify({phase,passed:true,taskId:accepted.taskId,executionMode:'ENGINEERING_REPLAY',providerCalls:0,checks},null,2))
  console.log(JSON.stringify({phase,passed:true,checks:checks.length,providerCalls:0}))
} catch(error){
  await writeFile(resolve(output,phase+'-failed.json'),JSON.stringify({phase,passed:false,completedChecks:checks,errorType:error.constructor.name},null,2))
  throw error
}
