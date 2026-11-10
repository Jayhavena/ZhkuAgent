import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile, mkdtemp, mkdir, writeFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {assertE13Terminal, createBrowserLease, acceptBrowserTicket, withBrowserLease} from './rule-agent-e02-page-live.mjs';
const fixtures = JSON.parse(await readFile('knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json','utf8'));
function sample() {
 const s = structuredClone(fixtures.snapshots.find(x=>x.name==='honest-unsupported-demand').value);
 s.execution={...s.execution,mode:'ONLINE',providerResponseReceived:true,modelAttemptCount:3};
 return s;
}
const visible='资料缺口 已审核资料没有覆盖当期需求，本轮不作结论，也不代替你办理。请以教务系统当期通知为准；不代办报名、补选。';
test('E13同一真实终态和可见边界通过，无事实或原文也合法',()=>assertE13Terminal(sample(),visible));
test('技术失败/追问/完整覆盖/假模型/错原因不能冒充E13通过',()=>{
 for(const mutate of [s=>s.taskStatus='FAILED',s=>s.taskStatus='WAITING_USER',s=>s.taskStatus='STOPPED',s=>s.result.coverage='COMPLETE',s=>s.execution.mode='FIXTURE',s=>s.execution.providerResponseReceived=false,s=>s.result.items[0].issues[0].reasonCode='RETRIEVAL_NO_MATCH',s=>s.result.items[0].issues[0].kind='PROCESSING_FAILURE']) {
  const s=sample();mutate(s);assert.throws(()=>assertE13Terminal(s,visible));
 }
 for(const text of ['资料缺口','资料缺口 当期 无法确认 教务 已替你办理成功',visible+' 本轮处理失败']) assert.throws(()=>assertE13Terminal(sample(),text));
});
test('E13票据拒绝混用E02，异常仍STOP且不得复用租约',async()=>{
 const root=await mkdtemp(path.join(tmpdir(),'e13-lease-'));const batch=path.join(root,'batch');await mkdir(batch);
 try {
  const sha='c'.repeat(64), lease=await createBrowserLease(batch,root,sha);
  const ticket={schemaVersion:4,driverLeaseSchemaVersion:1,driverLeaseNonce:lease.nonce,packageSha256:sha,profile:'E02',executionMode:'ONLINE',browserRequired:true,maximumTasks:1,maximumRuns:1,maximumProviderCalls:5,hardBudgetRmb:'4.02',javaPostAllowed:false,proxyPort:12345};
  await writeFile(path.join(batch,'browser-ready.json'),JSON.stringify(ticket));await assert.rejects(acceptBrowserTicket(lease,'E13'));
  ticket.profile='E13';await writeFile(path.join(batch,'browser-ready.json'),JSON.stringify(ticket));await acceptBrowserTicket(lease,'E13');
  await assert.rejects(withBrowserLease(lease,async()=>{throw Error('synthetic failure');}),/synthetic failure/);
  assert.match(await readFile(path.join(batch,'STOP'),'utf8'),/complete/);
  await assert.rejects(createBrowserLease(batch,root,sha));
 } finally {await rm(root,{recursive:true,force:true});}
});
