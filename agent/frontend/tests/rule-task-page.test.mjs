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
async function render(snapshot=null,actor={role:'USER'},extra={},session={},simulated=false,httpFixture=false){const app=createSSRApp(Page),router=createRouter({history:createMemoryHistory(),routes:[{path:'/rules',component:{render:()=>null}}]});app.use(router);await router.push('/rules');await router.isReady();app.provide('rule-task-runtime',{simulated,httpFixture,session:{state:{actor,epoch:0,identityBusy:false,...session}},client:{},workflow:{state:{snapshot,ticket:snapshot?{taskId:snapshot.taskId}:null,question:'合成问题',busy:false,polling:false,error:'',...extra},reset(){},dispose(){}}});return renderToString(app)}
test('fixed simulation scope is visible before login and does not claim general answers',async()=>{
  const html=await render(null,null,{},{},true)
  assert.match(html,/工程模拟：仅支持下方固定问题，不调用在线模型/)
  assert.match(html,/我这门选修课没过，能不能重修？/)
  assert.doesNotMatch(await render(null,null),/工程模拟：仅支持下方固定问题/)
})
test('HTTP fixture is disclosed before login and never represented as live model success',async()=>{
  assert.match(await render(null,null,{},{},false,true),/本机 HTTP FIXTURE 验收：身份、规则和答案均为合成测试数据，模型调用为零/)
  assert.doesNotMatch(await render(null,null),/本机 HTTP FIXTURE 验收/)
})
test('student login and navigation expose rules only',async()=>{const html=await render(null,null);assert.match(html,/登录规则查证/);assert.match(html,/href="\/rules"/);assert.doesNotMatch(html,/学分实验|通用助手实验|href="\/campus/)} )
for(const row of data.snapshots)test(`student presents shared ${row.name}`,async()=>{const html=await render(row.value);for(const q of row.value.result?.items||[]){for(const c of q.claims)assert.ok(html.includes(c.text));for(const i of q.issues){assert.ok(html.includes(i.message));assert.ok(html.includes(i.nextStep))}}assert.doesNotMatch(html,/<details[^>]*open/);if(row.value.result?.claims?.length)assert.ok(html.indexOf('能确认')<html.indexOf('依据'))})
test('partial answers, processing failure, missing data and replay have separate labels',async()=>{const labels={'partial-and-processing-failure':'处理未完成','honest-data-gap':'资料缺口','honest-unsupported-demand':'资料缺口','replay-not-covered':'回放未覆盖','partial-and-clarification':'需要补充条件'};for(const [name,label]of Object.entries(labels))assert.match(await render(data.snapshots.find(x=>x.name===name).value),new RegExp(label))})
test('identity uncertainty and task receipt uncertainty provide explicit recovery',async()=>{assert.match(await render(null,{role:'USER'},{pending:{action:'CREATE'},error:'受理结果尚未确认'}),/查询原请求/);assert.match(await render(null,{role:'USER'},{ticket:{taskId:'x'},error:'任务查询未确认'}),/继续查询/);assert.doesNotMatch(await render(null,{role:'MAINTAINER'}),/<textarea/)} )
test('rules are escaped and only related evidence is rendered after explanations',async()=>{const s=structuredClone(data.snapshots[0].value);s.result.items[0].claims[0].text='<script>evil()</script>';const html=await render(s);assert.match(html,/&lt;script&gt;/);assert.doesNotMatch(html,/<script>/);assert.ok(html.indexOf('能确认')<html.indexOf('证据详情'))})

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


test('minimal student home centers one prompt and avoids a permanent evidence panel', async () => {
  const html = await render(null, { role: 'USER' }, { question: '' })
  // 首页按 V3：一句话主张 + 直接提问的输入框 + 输入框内的自然语言提示 + 三条例题。
  assert.match(html, /校园规则，直接问。/)
  assert.match(html, /自然语言提问即可/)
  assert.match(html, /试试这样问/)
  assert.match(html, /class="composer composer--hero"/)
  assert.doesNotMatch(html, /original-panel/)
  assert.doesNotMatch(html, /Trace|toolStepCount|modelAttemptCount|PROCESSING_FAILURE/)
})

test('answer keeps the three detail sections collapsed and original context hidden until requested', async () => {
  const snapshot = data.snapshots.find(row => row.name === 'complete').value
  const html = await render(snapshot, { role: 'USER' })
  assert.match(html, /能确认的内容/)
  // 三个默认收起的详情区必须同时存在，不能合并成两个。
  assert.match(html, /data-testid="fold-explanation"/)
  assert.match(html, /data-testid="fold-sources"/)
  assert.match(html, /data-testid="fold-evidence"/)
  assert.match(html, /查看具体解释/)
  assert.match(html, /本次使用的资料/)
  assert.match(html, /证据详情/)
  assert.doesNotMatch(html, /<details[^>]*\sopen/)
  assert.doesNotMatch(html, /class="original-panel"/)
})

test('header exposes new-question only in conversation and keeps the identity panel closed by default', async () => {
  const home = await render(null, { role: 'USER' }, { question: '' })
  assert.doesNotMatch(home, />新提问</)
  assert.match(home, /aria-label="账号信息：学生查证身份"/)
  const conversation = await render(data.snapshots.find(x => x.name === 'complete').value)
  assert.match(conversation, />新提问<\/button>/)
  // 身份入口默认收起：退出动作只应在用户主动打开面板后出现，不常驻顶部。
  assert.doesNotMatch(conversation, /退出登录/)
  const busy = await render(null, { role: 'USER' }, { pending: { action: 'CREATE' } })
  assert.match(busy, /<button[^>]*disabled[^>]*>新提问<\/button>/)
})

test('conversation keeps composer in a bottom dock and scrolls only the answer area', async () => {
  const complete = await render(data.snapshots.find(x => x.name === 'complete').value)
  assert.match(complete, /class="conversation-scroll"/)
  assert.match(complete, /class="composer-bottom"/)
  assert.match(complete, /class="composer composer--bottom"/)
  assert.match(complete, /class="composer-note"/)
  assert.match(complete, /新问题将独立查证，不会自动沿用上一题条件。/)
  const waiting = await render(data.snapshots.find(x => x.name === 'partial-and-clarification').value)
  assert.match(waiting, /请先完成上方澄清，或改问其他问题。/)
  assert.match(waiting, /<textarea[^>]*id="question-next"[^>]*disabled/)
})

test('evidence gaps carry a warm next-step accent while processing failure stays distinct', async () => {
  const gap = await render(data.snapshots.find(x => x.name === 'honest-data-gap').value)
  assert.match(gap, /class="[^"]*issue--gap[^"]*"/)
  assert.match(gap, /class="issue-next-step"/)
  const failure = await render(data.snapshots.find(x => x.name === 'partial-and-processing-failure').value)
  assert.match(failure, /class="[^"]*issue--failure[^"]*"/)
  assert.doesNotMatch(failure, /issue--gap/)
  assert.match(failure, /处理未完成/)
  assert.doesNotMatch(failure, /资料缺口/)
})

test('the evidence fold carries revision, span and checksum but never claims verification', async () => {
  const html = await render(data.snapshots.find(x => x.name === 'complete').value)
  const fold = html.indexOf('data-testid="fold-evidence"')
  assert.ok(fold > -1, '缺少证据详情折叠区')
  // 位置信息由服务端字符跨度给出；不出现后端没有的“段落”“已核验”“适用范围”“原件地址”。
  assert.match(html, /原文位置/)
  assert.match(html, /字符/)
  assert.doesNotMatch(html, /已核验/)
  assert.doesNotMatch(html, /第\d+段|第 \d+ 段/)
  assert.doesNotMatch(html, /适用范围[:：]/)
  assert.doesNotMatch(html, /查看原件/)
})

test('inline citations only reference CLAIM evidence and open the matching detail', async () => {
  const snapshot = data.snapshots.find(x => x.name === 'complete').value
  const html = await render(snapshot, { role: 'USER' })
  const citations = html.match(/data-testid="inline-ref"/g) || []
  const claimEvidence = snapshot.result.evidenceRelations.filter(relation => relation.role === 'CLAIM').length
  // 行内引用数量必须等于 CLAIM 用途关系的数量；SCOPE/CONFLICT 证据不得伪装成“支持本结论”。
  assert.equal(citations.length, claimEvidence)
  assert.match(html, /依据 1/)
})

test('conclusion-first: the published claim doubles as the answer title and is not repeated', async () => {
  const snapshot = data.snapshots.find(x => x.name === 'complete').value
  const claim = snapshot.result.items.find(item => item.claims.length).claims[0]
  const html = await render(snapshot, { role: 'USER' })
  const title = html.indexOf('data-testid="answer-title"')
  assert.ok(title > -1)
  assert.ok(html.indexOf(claim.text) > title, '结论句应出现在标题位置')
  // 同一句话只出现一次：标题已承载首条结论。
  assert.equal(html.split(claim.text).length - 1, 1)
})

test('explanation fold states explicitly when no explanation is available', async () => {
  // 造一个既无 claim.when 也无 issue 的快照：后端没有独立 explanation 字段，前端不得编造解释。
  const snapshot = structuredClone(data.snapshots.find(x => x.name === 'complete').value)
  for (const item of snapshot.result.items) {
    item.issues = []
    for (const claim of item.claims) claim.when = {}
  }
  const html = await render(snapshot, { role: 'USER' })
  assert.match(html, /data-testid="explanation-empty"/)
  assert.match(html, /没有可展示的额外解释/)
})

test('clarification renders its confirm action inside the scrolling message area', async () => {
  // 澄清面板必须落在消息滚动区内、且未选择前确认按钮是禁用的，
  // 否则窄屏首屏只剩选项（按钮被裁到滚动区外），用户会以为流程走不下去。
  const html = await render(data.snapshots.find(x => x.name === 'partial-and-clarification').value)
  const scroller = html.indexOf('class="conversation-scroll"')
  const form = html.indexOf('class="clarification-form"')
  const confirm = html.search(/<button[^>]*disabled[^>]*>确认并继续<\/button>/)
  assert.ok(scroller > -1, '缺少消息滚动区')
  assert.ok(form > scroller, '澄清面板应位于消息滚动区内')
  assert.ok(confirm > form, '未选择条件时确认按钮应存在且禁用，并位于澄清面板内')
})
