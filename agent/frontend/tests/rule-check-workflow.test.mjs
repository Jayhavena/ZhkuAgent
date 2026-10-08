import test from 'node:test'
import assert from 'node:assert/strict'
import { createControlledSession } from '../src/state/controlled-session.js'
import { createRuleCheckWorkflow } from '../src/state/rule-check-workflow.js'
const ID='22222222-2222-4222-8222-222222222222'
const setup=client=>{const session=createControlledSession();session.state.actor={userId:ID,role:'USER',authRevision:0};return {session,flow:createRuleCheckWorkflow({client,session})}}
const need=()=>({status:'NEED_CLARIFICATION',clarificationToken:ID,clarificationQuestions:[{key:'year',question:'哪一年级',allowedValues:['A','B']}],confirmedConditions:{}})
test('busy execution is separate from business decision and no retry on unknown outcome',async()=>{
  let resolve,calls=0;const {flow}=setup({check:()=>{calls++;return new Promise(r=>resolve=r)},cancelResults(){}})
  const promise=flow.start('q');assert.equal(flow.state.busy,true);assert.equal(flow.state.result,null)
  resolve({status:'INSUFFICIENT_EVIDENCE'});await promise;assert.equal(flow.state.busy,false);assert.equal(flow.state.result.status,'INSUFFICIENT_EVIDENCE');assert.equal(calls,1);flow.dispose()
})
test('clarification sends only current allowed delta and never guesses unknown conditions',async()=>{
  const seen=[];const {flow}=setup({check:async(...args)=>{seen.push(args);return need()},cancelResults(){}})
  await flow.start('q');await flow.clarify('year','C');assert.equal(seen.length,1)
  await flow.clarify('year','A');assert.deepEqual(seen[1],['q',{year:'A'},ID]);flow.dispose()
})
test('new question clears previous answer and original before pending query completes',async()=>{
  let resolve;const {flow}=setup({check:async()=>({status:'ANSWERED'}),original:async()=>({contextText:'旧原文'}),cancelResults(){}})
  await flow.start('old');await flow.openOriginal(ID);assert.equal(flow.state.original.contextText,'旧原文')
  flow.reset();assert.equal(flow.state.result,null);assert.equal(flow.state.original,null);flow.dispose()
})
test('identity epoch change immediately clears displayed content and old async response',async()=>{
  let resolve;const {flow,session}=setup({check:()=>new Promise(r=>resolve=r),cancelResults(){}})
  const first=flow.start('q');assert.equal(flow.state.busy,true);session.clear();resolve({status:'ANSWERED'});await first
  assert.equal(flow.state.result,null);assert.equal(flow.state.question,'');assert.equal(flow.state.busy,false);flow.dispose()
})
test('reset and disposal reject late original response even if transport resolves',async()=>{
  let resolve;const {flow}=setup({check:async()=>({status:'ANSWERED'}),original:()=>new Promise(r=>resolve=r),cancelResults(){}})
  await flow.start('q');const promise=flow.openOriginal(ID);assert.equal(flow.state.originalBusy,true);flow.reset();resolve({contextText:'旧原文'});await promise
  assert.equal(flow.state.original,null);flow.dispose()
})
test('failed original retrieval clears old original and outcome remains explicit',async()=>{
  let fail=false;const {flow}=setup({check:async()=>({status:'ANSWERED'}),original:async()=>{if(fail)throw {status:409,code:'ORIGINAL_CHANGED'};return {contextText:'旧原文'}},cancelResults(){}})
  await flow.start('q');await flow.openOriginal(ID);fail=true;await flow.openOriginal(ID)
  assert.equal(flow.state.original,null);assert.match(flow.state.error,/变化|重新查证/);flow.dispose()
})
test('connection loss records unconfirmed result and never automatically resubmits',async()=>{
  let calls=0;const {flow}=setup({check:async()=>{calls++;throw {code:'NETWORK_UNAVAILABLE'}},cancelResults(){}})
  await flow.start('q');assert.equal(flow.state.result,null);assert.equal(calls,1);assert.match(flow.state.error,/未确认/);assert.doesNotMatch(flow.state.error,/未受理|已受理/);flow.dispose()
})
