import test, { after } from 'node:test'
import assert from 'node:assert/strict'
import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'
import { errorMessage, ControlledError } from '../src/api/controlled.js'
const vite=await createServer({root:fileURLToPath(new URL('..',import.meta.url)),server:{middlewareMode:true,hmr:false},appType:'custom',logLevel:'silent'})
after(()=>vite.close())
const component=(await vite.ssrLoadModule('/src/views/CampusRun.vue')).default
const ID='22222222-2222-4222-8222-222222222222'
async function render(facts) {
  // 实际setup和SSR模板；仅在已执行setup后装入合成展示状态，不运行网络或浏览器点击。
  const app=createSSRApp({...component,setup(props,context){const state=component.setup(props,context);state.facts.value=facts;return state}})
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/campus/run',component:{template:'<div />'}}]})
  app.use(router);await router.push('/campus/run?runId='+ID);await router.isReady();return renderToString(app)
}
test('same submission key with different content explains conflict and deliberate new task',()=>{
  const message=errorMessage(new ControlledError('SUBMISSION_CONTENT_CONFLICT',409))
  assert.match(message,/内容|课程或问题/);assert.match(message,/不一致/);assert.match(message,/原任务/);assert.match(message,/新建/)
  assert.doesNotMatch(message,/未受理|重新发送/)
  assert.match(errorMessage(new ControlledError('NETWORK_UNAVAILABLE',0,{submissionUnknown:true})),/暂未确认/)
})
test('completed live and finalized archived tasks never claim still confirming submission',async()=>{
  for(const facts of [{runId:ID,state:'SUCCEEDED',terminated:true,visibility:'HIDDEN',location:'LIVE'},
    {runId:ID,state:'SUCCEEDED',archiveStatus:'FINALIZED',visibility:'VISIBLE',location:'ARCHIVED'},
    {runId:ID,archiveStatus:'FINALIZED',visibility:'HIDDEN',location:'ARCHIVED'}]) {
    const html=await render(facts);assert.doesNotMatch(html,/正在确认这次提交/);assert.match(html,/已找到本次任务记录/)
  }
})
test('missing or unconfirmed record retains uncertainty without asserting never accepted',async()=>{
  for(const facts of [null,{runId:ID,location:'UNCONFIRMED',visibility:'UNCONFIRMED'}]) {
    const html=await render(facts);assert.match(html,/正在确认这次提交/);assert.match(html,/不代表从未受理/)
  }
})
test('known running task is found but not called completed',async()=>{
  const html=await render({runId:ID,state:'RUNNING',terminated:false,location:'LIVE',visibility:'RUNNING'})
  assert.match(html,/已找到本次任务记录/);assert.match(html,/正在核对/);assert.doesNotMatch(html,/正在确认这次提交|已完成/)
})
