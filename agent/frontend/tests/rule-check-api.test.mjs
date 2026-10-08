import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRuleCheckClient, PREFIX } from '../src/api/rule-check.js'
import { createControlledSession } from '../src/state/controlled-session.js'
const ID='22222222-2222-4222-8222-222222222222', HANDLE='33333333-3333-4333-8333-333333333333'
const json=(body,status=200)=>new Response(JSON.stringify(body),{status})
const actor=(revision=0)=>({userId:ID,role:'USER',authRevision:revision})
const session=()=>{const s=createControlledSession();s.state.actor=actor();s.csrf={headerName:'X-CSRF-TOKEN',token:'synthetic'};return s}
export const answered=()=>({status:'ANSWERED',referenceRevision:'2025.9.2',knowledgeScope:'SYNTHETIC',conditionSource:'USER_DECLARED',confirmedConditions:{},
  confirmedFacts:[{id:'limit',text:'合成事实',when:{},citations:[{citationId:'c',sourceId:ID,chunkId:ID,revision:'2025.9.2',startInclusive:2,endExclusive:4,text:'原文',originalToken:HANDLE}]}],
  clarificationQuestions:[],unknownItems:[],conflicts:[],scope:{ruleId:'fixture',questionScope:'合成范围',declaredConditions:[],factPreconditions:{limit:{}}},clarificationToken:null})
const original=()=>({citationId:'c',sourceId:ID,chunkId:ID,revision:'2025.9.2',title:'合成标题',sourceLocator:'合成定位',recordedPageNumber:1,pageNumberKind:'RECORDED_SOURCE_PAGE',sectionTitle:null,safeSourceUrl:null,
  contextText:'前文原文后文',contextStartInclusive:0,quoteStartInclusive:2,quoteEndExclusive:4,unknownItems:['映射未知']})
const tick=()=>new Promise(resolve=>setImmediate(resolve))

test('actual Java HTTP answer accepts declaredConditions and normalizes UI conditions',async()=>{
  const value=JSON.parse(await readFile(new URL('./fixtures/rule-check-answer-http.json',import.meta.url),'utf8'))
  const c=createRuleCheckClient({session:session(),fetchImpl:async()=>json(value)})
  const result=await c.check('合成条款',{})
  assert.equal(result.status,'ANSWERED');assert.deepEqual(result.scope.conditions,value.scope.declaredConditions)
  assert.equal(result.confirmedFacts[0].citations[0].text,'合成条款：上限4学分。')
})
test('v2 login renews CSRF and reads independent actor without v1 calls',async()=>{
  const seen=[],s=createControlledSession();let count=0
  const client=createRuleCheckClient({session:s,fetchImpl:async(url,options)=>{
    seen.push({url,options});return url.endsWith('/csrf')?json({headerName:'X-CSRF-TOKEN',token:`t${++count}`}):json(actor())
  }})
  await client.login('user-a','Synthetic-password')
  assert.deepEqual(seen.map(x=>x.url),['/auth/csrf','/auth/login','/auth/csrf','/auth/me'].map(x=>PREFIX+x))
  assert.equal(new URLSearchParams(seen[1].options.body).get('username'),'user-a')
  assert.equal(seen.every(x=>x.options.credentials==='same-origin'&&x.options.cache==='no-store'),true)
  assert.equal(s.state.actor.userId,ID)
})
test('check sends only explicit conditions and server continuation token',async()=>{
  const seen=[];const c=createRuleCheckClient({session:session(),fetchImpl:async(url,options)=>{seen.push({url,options});return json(answered())}})
  assert.equal((await c.check('合成问题',{year:'A'},HANDLE))?.status,'ANSWERED')
  assert.deepEqual(JSON.parse(seen[0].options.body),{question:'合成问题',confirmedConditions:{year:'A'},clarificationToken:HANDLE})
  assert.equal(seen[0].url,PREFIX+'/rule-check');assert.equal(seen[0].options.headers['X-CSRF-TOKEN'],'synthetic')
})
test('new question discards late successful answer even when abort ignored',async()=>{
  const pending=[];const c=createRuleCheckClient({session:session(),fetchImpl:()=>new Promise(r=>pending.push(r))})
  const first=c.check('first',{});const rejected=assert.rejects(first,e=>e.code==='STALE_RESPONSE');await tick();assert.equal(pending.length,1)
  const next=c.check('next',{});await tick();assert.equal(pending.length,2)
  pending[1](json(answered()));assert.equal((await next).status,'ANSWERED');pending[0](json(answered()));await rejected
})
test('late old epoch 401 cannot clear new logged in actor',async()=>{
  const s=session();let resolve;const c=createRuleCheckClient({session:s,fetchImpl:()=>new Promise(r=>resolve=r)})
  const first=c.check('first',{});const rejected=assert.rejects(first,e=>e.code==='STALE_RESPONSE');await tick();assert.equal(typeof resolve,'function')
  s.clear();s.state.actor=actor(1);resolve(json({code:'AUTH_REVOKED'},401));await rejected;assert.equal(s.state.actor.authRevision,1)
})
test('all five statuses pass contract but contradictory fact shapes are refused',async()=>{
  const fixtures=[answered()];const base=answered()
  fixtures.push({...base,status:'PARTIALLY_ANSWERED',unknownItems:['未知项']})
  fixtures.push({...base,status:'NEED_CLARIFICATION',confirmedFacts:[],clarificationQuestions:[{key:'year',question:'合成年级',allowedValues:['A','B']}],clarificationToken:HANDLE})
  fixtures.push({...base,status:'INSUFFICIENT_EVIDENCE',confirmedFacts:[],scope:null,unknownItems:['无证据']})
  fixtures.push({...base,status:'CONFLICTING_EVIDENCE',confirmedFacts:[],conflicts:[{issue:'合成冲突',sides:[base.confirmedFacts[0],{...base.confirmedFacts[0],id:'other'}]}]})
  for(const value of fixtures){const c=createRuleCheckClient({session:session(),fetchImpl:async()=>json(value)});assert.equal((await c.check('q',{}))?.status,value.status)}
  const bad=[{...base,status:'SUCCESS'}, {...base,referenceRevision:'2025'}, {...base,confirmedFacts:[]}, {...base,status:'INSUFFICIENT_EVIDENCE'},
    {...base,confirmedFacts:[{...base.confirmedFacts[0],citations:[]}]}, {...base,conditionSource:'INFERRED'}]
  for(const value of bad){const c=createRuleCheckClient({session:session(),fetchImpl:async()=>json(value)});await assert.rejects(c.check('q',{}),e=>e.code==='BAD_RESPONSE')}
})
test('original rejects invalid context spans and unsafe links without showing old text',async()=>{
  const c=createRuleCheckClient({session:session(),fetchImpl:async()=>json(original())});assert.equal((await c.original(HANDLE))?.contextText,'前文原文后文')
  for(const value of [{...original(),quoteEndExclusive:99},{...original(),contextStartInclusive:3},{...original(),safeSourceUrl:'javascript:alert(1)'},{...original(),safeSourceUrl:'https://user:pass@example.com/'}]) {
    const bad=createRuleCheckClient({session:session(),fetchImpl:async()=>json(value)});await assert.rejects(bad.original(HANDLE),e=>e.code==='BAD_RESPONSE')
  }
})
test('invalid request or original handle never invokes transport',async()=>{
  let calls=0;const c=createRuleCheckClient({session:session(),fetchImpl:async()=>{calls++;return json(answered())}})
  for(const input of [[' ',{}],['q',{year:3}],['q',{},'fake'],['x'.repeat(2049),{}]])await assert.rejects(c.check(...input),e=>e.code==='INVALID_RULE_CHECK_REQUEST')
  await assert.rejects(c.original('../private'),e=>e.code==='INVALID_ORIGINAL_TOKEN');assert.equal(calls,0)
})
test('network loss does not retry and does not claim not accepted',async()=>{
  let calls=0;const c=createRuleCheckClient({session:session(),fetchImpl:async()=>{calls++;throw new Error('do-not-echo')}})
  await assert.rejects(c.check('q',{}),e=>e.code==='NETWORK_UNAVAILABLE'&&!e.message.includes('do-not-echo'));assert.equal(calls,1)
})
test('new question discards original response from earlier round',async()=>{
  let resolve;const c=createRuleCheckClient({session:session(),fetchImpl:url=>url.includes('/originals/')?new Promise(r=>resolve=r):Promise.resolve(json(answered()))})
  const old=c.original(HANDLE);const rejected=assert.rejects(old,e=>e.code==='STALE_RESPONSE');await tick();assert.equal(typeof resolve,'function')
  await c.check('next',{});resolve(json(original()));await rejected
})
test('identity changes are serialized and me detects changed auth revision',async()=>{
  const s=createControlledSession();let resolve
  const c=createRuleCheckClient({session:s,fetchImpl:url=>url.endsWith('/csrf')&&!resolve?new Promise(r=>resolve=r):Promise.resolve(url.endsWith('/csrf')?json({headerName:'X-CSRF-TOKEN',token:'next'}):json(actor(1)))})
  const login=c.login('user-a','Synthetic-password');await tick();assert.equal(s.state.identityBusy,true)
  await assert.rejects(c.login('user-b','Synthetic-password'),e=>e.code==='AUTH_TRANSITION');resolve(json({headerName:'X-CSRF-TOKEN',token:'first'}));await login
  s.state.actor=actor(0);const epoch=s.state.epoch;await c.me();assert.ok(s.state.epoch>epoch);assert.equal(s.state.actor.authRevision,1)
})
test('current 401 clears its own identity but preserves actionable auth error',async()=>{
  const s=session(),c=createRuleCheckClient({session:s,fetchImpl:async()=>json({code:'AUTH_REVOKED'},401)})
  await assert.rejects(c.check('q',{}),e=>e.code==='AUTH_REVOKED'&&e.status===401)
  assert.equal(s.state.actor,null);assert.equal(s.state.reason,'AUTH_EXPIRED')
})
test('half surrogate quote and context boundary are rejected',async()=>{
  const broken=answered();broken.confirmedFacts[0].citations[0].text='\ud83d';broken.confirmedFacts[0].citations[0].endExclusive=3
  const c=createRuleCheckClient({session:session(),fetchImpl:async()=>json(broken)})
  await assert.rejects(c.check('q',{}),e=>e.code==='BAD_RESPONSE')
  const bad={...original(),contextText:'前😀后',quoteStartInclusive:2,quoteEndExclusive:3}
  const d=createRuleCheckClient({session:session(),fetchImpl:async()=>json(bad)})
  await assert.rejects(d.original(HANDLE),e=>e.code==='BAD_RESPONSE')
})
test('current empty or HTML 401 clears identity independently of error body parsing',async()=>{
  for(const body of ['', '<html>untrusted cause</html>']) {
    const s=session(),c=createRuleCheckClient({session:s,fetchImpl:async()=>new Response(body,{status:401})})
    await assert.rejects(c.me(),e=>e.status===401&&e.code==='AUTH_REVOKED')
    assert.equal(s.state.actor,null);assert.equal(s.csrf,null);assert.equal(s.state.reason,'AUTH_EXPIRED')
  }
})
test('old epoch non JSON 401 never clears new current actor',async()=>{
  const s=session();let resolve;const c=createRuleCheckClient({session:s,fetchImpl:()=>new Promise(r=>resolve=r)})
  const pending=c.check('q',{});const rejected=assert.rejects(pending,e=>e.code==='STALE_RESPONSE');await tick()
  s.clear();s.state.actor=actor(1);resolve(new Response('<html>old</html>',{status:401}));await rejected
  assert.equal(s.state.actor.authRevision,1)
})
test('public me during logout cannot refill actor with late success',async()=>{
  const s=session();let logoutResolve,meResolve;let meCalls=0
  const c=createRuleCheckClient({session:s,fetchImpl:url=>url.endsWith('/logout')?new Promise(r=>logoutResolve=r):
    url.endsWith('/me')?(meCalls++,new Promise(r=>meResolve=r)):Promise.resolve(json({headerName:'X-CSRF-TOKEN',token:'t'}))})
  const logout=c.logout();await tick();assert.equal(s.state.identityBusy,true)
  const outcome=c.me().then(value=>({value}),error=>({error}));await tick()
  logoutResolve(new Response(null,{status:204}));await logout
  if(meResolve)meResolve(json(actor()));const result=await outcome
  assert.equal(result.error?.code,'AUTH_TRANSITION');assert.equal(meCalls,0);assert.equal(s.state.actor,null)
})
test('old question CSRF preflight 401 cannot erase new successful question',async()=>{
  for(const body of [JSON.stringify({code:'AUTH_REVOKED'}),'<html>old preflight</html>']) {
    const s=session();s.csrf=null;let firstResolve,csrfCalls=0
    const c=createRuleCheckClient({session:s,fetchImpl:url=>url.endsWith('/csrf')?
      (++csrfCalls===1?new Promise(r=>firstResolve=r):Promise.resolve(json({headerName:'X-CSRF-TOKEN',token:'new'}))):Promise.resolve(json(answered()))})
    const old=c.check('old',{});const outcome=old.then(value=>({value}),error=>({error}));await tick();assert.equal(typeof firstResolve,'function')
    assert.equal((await c.check('new',{})).status,'ANSWERED')
    firstResolve(new Response(body,{status:401}));const result=await outcome
    assert.equal(result.error?.code,'STALE_RESPONSE');assert.equal(s.state.actor.userId,ID);assert.equal(s.csrf.token,'new')
  }
})
