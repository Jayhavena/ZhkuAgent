import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {createTaskHistory} from '../src/state/rule-task-history.js'
import {createRuleTaskWorkflow} from '../src/state/rule-task-workflow.js'
import {createControlledSession} from '../src/state/controlled-session.js'

const owner='22222222-2222-4222-8222-222222222222'
const other='33333333-3333-4333-8333-333333333333'
const receipt={taskId:owner,runId:owner,turnId:owner,version:1,duplicate:false}
const fixtures=JSON.parse(await readFile(new URL('../../../knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json',import.meta.url),'utf8'))
const complete=()=>structuredClone(fixtures.snapshots.find(x=>x.name==='complete').value)
function storage(){const values=new Map();return{values,getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)}}

test('tab history bounds five metadata-only receipts and expires them',()=>{
  const store=storage();let time=1000;const history=createTaskHistory({storage:()=>store,now:()=>time})
  for(let n=1;n<=7;n++)history.remember(owner,{...receipt,taskId:`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`,question:'private',originalToken:'private',continuationToken:'private'})
  assert.equal(history.list(owner).length,5)
  assert.equal(history.list(owner)[0].taskId.endsWith('000000000007'),true)
  assert.equal([...store.values.values()].join('').includes('private'),false)
  assert.deepEqual(Object.keys(history.list(owner)[0]).sort(),['recordedAt','runId','taskId','turnId','version'])
  time+=900001;assert.deepEqual(history.list(owner),[])
})
test('different account and corrupt cache cannot recover prior receipts',()=>{
  const store=storage(),history=createTaskHistory({storage:()=>store,now:()=>0})
  history.remember(owner,receipt);assert.deepEqual(history.list(other),[]);assert.deepEqual(history.list(owner),[])
  store.setItem('zhku-rule-task-history-v1','{"owner":"'+owner+'","entries":[{"token":"secret"}]}')
  assert.deepEqual(history.list(owner),[]);assert.equal(store.values.size,0)
})
test('blocked browser storage degrades without breaking task creation',()=>{
  const history=createTaskHistory({storage:()=>{throw new Error('blocked')}})
  assert.doesNotThrow(()=>history.remember(owner,receipt));assert.deepEqual(history.list(owner),[])
})
test('reload restores latest authorized snapshot using GET only and never replays POST',async()=>{
  const store=storage(),history=createTaskHistory({storage:()=>store,now:()=>0});history.remember(owner,receipt)
  const session=createControlledSession();session.state.actor={userId:owner,role:'USER'}
  let reads=0,posts=0
  const client={cancelResults(){},snapshot:async id=>{reads++;assert.equal(id,owner);return complete()},create:async()=>{posts++;return receipt}}
  const first=createRuleTaskWorkflow({client,session,history});await first.restoreLatest()
  assert.equal(first.state.snapshot.taskStatus,'RESOLVED');first.dispose()
  const second=createRuleTaskWorkflow({client,session,history});await second.restoreLatest()
  assert.equal(second.state.snapshot.taskStatus,'RESOLVED');assert.equal(reads,2);assert.equal(posts,0)
  assert.equal([...store.values.values()].join('').includes('continuationToken'),false);second.dispose()
})
test('expired server task is removed and delayed restore cannot cross identity change',async()=>{
  const store=storage(),history=createTaskHistory({storage:()=>store,now:()=>0});history.remember(owner,receipt)
  const session=createControlledSession();session.state.actor={userId:owner,role:'USER'}
  let resolve;const client={cancelResults(){},snapshot:()=>new Promise(r=>{resolve=r})}
  const flow=createRuleTaskWorkflow({client,session,history});const pending=flow.restoreLatest();session.clear('AUTH_EXPIRED');resolve(complete());await pending
  assert.equal(flow.state.snapshot,null);assert.equal(flow.state.ticket,null);flow.dispose()
  session.state.actor={userId:owner,role:'USER'}
  const expired=createRuleTaskWorkflow({client:{cancelResults(){},snapshot:async()=>{throw{status:409}}},session,history})
  await expired.restoreLatest();assert.equal(expired.state.snapshot,null);assert.deepEqual(history.list(owner),[]);expired.dispose()
})
