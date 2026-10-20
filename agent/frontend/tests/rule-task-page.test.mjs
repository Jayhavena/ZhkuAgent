import test,{after} from 'node:test'
import assert from 'node:assert/strict'
import {createSSRApp} from 'vue'
import {renderToString} from '@vue/server-renderer'
import {createRouter,createMemoryHistory} from 'vue-router'
import {createServer} from 'vite'
import {readFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
const server=await createServer({root:fileURLToPath(new URL('..',import.meta.url)),server:{middlewareMode:true},appType:'custom'})
after(()=>server.close())
const {default:Page}=await server.ssrLoadModule('/src/views/RuleCheckView.vue')
const data=JSON.parse(await readFile(new URL('../../../knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json',import.meta.url),'utf8'))
async function render(snapshot=null,actor={role:'USER'},extra={},session={},simulated=false){const app=createSSRApp(Page),router=createRouter({history:createMemoryHistory(),routes:[{path:'/rules',component:{render:()=>null}}]});app.use(router);await router.push('/rules');await router.isReady();app.provide('rule-task-runtime',{simulated,session:{state:{actor,epoch:0,identityBusy:false,...session}},client:{},workflow:{state:{snapshot,ticket:snapshot?{taskId:snapshot.taskId}:null,question:'合成问题',busy:false,polling:false,error:'',...extra},reset(){},dispose(){}}});return renderToString(app)}
test('fixed simulation scope is visible before login and does not claim general answers',async()=>{
  const html=await render(null,null,{},{},true)
  assert.match(html,/工程模拟：仅支持下方固定问题，不调用在线模型/)
  assert.match(html,/我这门选修课没过，能不能重修？/)
  assert.doesNotMatch(await render(null,null),/工程模拟：仅支持下方固定问题/)
})
test('student login and navigation expose rules only',async()=>{const html=await render(null,null);assert.match(html,/登录规则查证/);assert.match(html,/href="\/rules"/);assert.doesNotMatch(html,/学分实验|通用助手实验|href="\/campus/)} )
for(const row of data.snapshots)test(`student presents shared ${row.name}`,async()=>{const html=await render(row.value);for(const q of row.value.result?.items||[]){for(const c of q.claims)assert.ok(html.includes(c.text));for(const i of q.issues){assert.ok(html.includes(i.message));assert.ok(html.includes(i.nextStep))}}assert.doesNotMatch(html,/<details[^>]*open/);if(row.value.result?.claims?.length)assert.ok(html.indexOf('能确认')<html.indexOf('依据'))})
test('partial answers, processing failure, missing data and replay have separate labels',async()=>{const labels={'partial-and-processing-failure':'处理未完成','honest-data-gap':'资料缺口','replay-not-covered':'回放未覆盖','partial-and-clarification':'需要补充条件'};for(const [name,label]of Object.entries(labels))assert.match(await render(data.snapshots.find(x=>x.name===name).value),new RegExp(label))})
test('identity uncertainty and task receipt uncertainty provide explicit recovery',async()=>{assert.match(await render(null,{role:'USER'},{pending:{action:'CREATE'},error:'受理结果尚未确认'}),/查询原请求/);assert.match(await render(null,{role:'USER'},{ticket:{taskId:'x'},error:'任务查询未确认'}),/继续查询/);assert.doesNotMatch(await render(null,{role:'MAINTAINER'}),/<textarea/)} )
test('rules are escaped and only related evidence is rendered after explanations',async()=>{const s=structuredClone(data.snapshots[0].value);s.result.items[0].claims[0].text='<script>evil()</script>';const html=await render(s);assert.match(html,/&lt;script&gt;/);assert.doesNotMatch(html,/<script>/);assert.ok(html.indexOf('能确认')<html.indexOf('相关依据'))})

test('another window changing identity is explained and restores no old result',async()=>{
  // 其他窗口退出/换身份后本窗口只剩登录态与说明，不能停留在此前的任务或已发布结论上。
  const html=await render(null,null,{},{reason:'IDENTITY_CHANGED_ELSEWHERE'})
  assert.match(html,/登录状态已在其他窗口变更，请重新登录。/)
  assert.doesNotMatch(html,/本次问题：/)
  assert.doesNotMatch(html,/decision-title/)
})

test('frozen T05 processing reasons and five simulated attempts preserve safe partial content',async()=>{
  const {validateRuleAgentSnapshot}=await import('../src/api/rule-agent-contract.js')
  for(const reason of ['QUESTION_UNDERSTANDING_UNRESOLVED','ANSWER_COVERAGE_INCOMPLETE']){
    const value=structuredClone(data.snapshots.find(x=>x.name==='partial-and-processing-failure').value)
    const issue=value.result.items.flatMap(q=>q.issues).find(i=>i.kind==='PROCESSING_FAILURE')
    issue.reasonCode=reason;issue.message='合成处理未完成';issue.nextStep='核对缺口后继续查证'
    value.execution.modelAttemptCount=5
    validateRuleAgentSnapshot(value)
    const html=await render(value)
    assert.match(html,/处理未完成/);assert.match(html,/核对缺口后继续查证/);assert.doesNotMatch(html,/资料缺口/)
    for(const claim of value.result.items.flatMap(q=>q.claims))assert.ok(html.includes(claim.text))
  }
})
