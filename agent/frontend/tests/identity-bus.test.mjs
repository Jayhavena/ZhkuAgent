import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {createControlledSession} from '../src/state/controlled-session.js'
import {createIdentityBus} from '../src/state/identity-bus.js'
import {bindIdentityBus} from '../src/state/rule-check-session.js'
import {createRuleTaskWorkflow} from '../src/state/rule-task-workflow.js'

const ID='22222222-2222-4222-8222-222222222222'
const ticket={taskId:ID,turnId:ID,runId:ID,version:1,duplicate:false}
const data=JSON.parse(await readFile(new URL('../../../knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json',import.meta.url),'utf8'))
const sample=name=>structuredClone(data.snapshots.find(x=>x.name===name).value)
const actor={userId:ID,role:'USER',authRevision:0}

// 两个窗口=同源两个独立上下文：各自一份会话，只有广播通道相连。
function windows(){
  const channels=[]
  const factory=()=>{const channel={onmessage:null,sent:[],postMessage(value){channel.sent.push(value);for(const other of channels)if(other!==channel)other.onmessage?.({data:value})},close(){}};channels.push(channel);return channel}
  const firstSession=createControlledSession(),secondSession=createControlledSession()
  const first=bindIdentityBus(firstSession,createIdentityBus({factory}))
  const second=bindIdentityBus(secondSession,createIdentityBus({factory}))
  return {channels,firstSession,secondSession,first,second}
}

test('local identity change is broadcast once and the receiving window never echoes',()=>{
  const {channels,firstSession,secondSession,first,second}=windows()
  firstSession.state.actor={...actor};secondSession.state.actor={...actor}
  const epoch=secondSession.state.epoch
  firstSession.clear('AUTH_EXPIRED')
  assert.equal(firstSession.state.actor,null)
  assert.equal(secondSession.state.actor,null)
  assert.equal(secondSession.state.epoch,epoch+1)
  assert.equal(secondSession.state.reason,'IDENTITY_CHANGED_ELSEWHERE')
  assert.equal(channels[0].sent.length,1)
  assert.equal(channels[1].sent.length,0,'接收端不得回broadcast，否则两窗口会互相换代')
  firstSession.clear()
  assert.equal(channels[0].sent.length,1,'本来就没有身份时不必广播')
  first.dispose();second.dispose()
})

test('the other window drops published results and a late response cannot restore them',async()=>{
  const {firstSession,secondSession,first,second}=windows()
  firstSession.state.actor={...actor};secondSession.state.actor={...actor}
  let resolveSnapshot
  const timers=[]
  const client={cancelResults(){},create:async()=>ticket,snapshot:()=>new Promise(resolve=>{resolveSnapshot=resolve}),message:async()=>ticket,cancel:async()=>ticket,original:async()=>({}),lookup:async()=>ticket}
  const flow=createRuleTaskWorkflow({client,session:secondSession,keyFactory:()=>ID,schedule:fn=>{timers.push(fn);return timers.length},unschedule(){},now:()=>0})
  await flow.start('旧问题')
  const poll=timers.shift()()
  firstSession.clear()
  resolveSnapshot(sample('complete'))
  await poll
  assert.equal(flow.state.snapshot,null)
  assert.equal(flow.state.ticket,null)
  assert.equal(secondSession.state.actor,null)
  assert.equal(secondSession.state.reason,'IDENTITY_CHANGED_ELSEWHERE')
  flow.dispose();first.dispose();second.dispose()
})

test('a session without a document degrades to single-window behaviour instead of failing',()=>{
  const bus=createIdentityBus()
  assert.equal(bus.available,false)
  assert.doesNotThrow(()=>bus.publish())
  assert.equal(typeof bus.subscribe(()=>{}),'function')
  bus.close()
})
