import test, { after } from 'node:test'
import assert from 'node:assert/strict'
import { createSSRApp, createRenderer, reactive, nextTick, ssrContextKey } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'
const root=fileURLToPath(new URL('..',import.meta.url))
const vite=await createServer({root,server:{middlewareMode:true,hmr:false},appType:'custom',logLevel:'silent'})
after(()=>vite.close())
const component=(await vite.ssrLoadModule('/src/views/RuleCheckReplayView.vue')).default
const ID='22222222-2222-4222-8222-222222222222'
const quote=()=>({citationId:'c',sourceId:ID,chunkId:ID,revision:'2025.9.2',startInclusive:2,endExclusive:4,text:'原文',originalToken:ID})
const fact=()=>({id:'limit',text:'合成确认事实',when:{year:'A'},citations:[quote()]})
const decision=(status='ANSWERED')=>({status,referenceRevision:'2025.9.2',conditionSource:'USER_DECLARED',confirmedConditions:{year:'A'},confirmedFacts:[fact()],clarificationQuestions:[],unknownItems:[],conflicts:[],clarificationToken:null,
  scope:{ruleId:'fixture',questionScope:'合成规则覆盖范围',conditions:[{key:'year',question:'合成年级',allowedValues:['A','B']}],factPreconditions:{limit:{year:'A'}}}})
async function render({actor={role:'USER',userId:ID,authRevision:0},reason='',result=null,original=null,busy=false,error='',originalBusy=false}={}) {
  const app=createSSRApp(component)
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/rules',component:{template:'<div />'}},{path:'/',component:{template:'<div />'}}]})
  app.use(router);await router.push('/rules');await router.isReady()
  app.provide('rule-check-runtime',{session:{state:{actor,epoch:0,identityBusy:false,reason}},client:{},workflow:{state:{question:'合成问题',result,original,busy,originalBusy,error},dispose(){},reset(){}}})
  return renderToString(app)
}
test('student page has independent login and explicit first reference boundary',async()=>{
  const html=await render({actor:null});assert.match(html,/登录规则查证/);assert.match(html,/2025/);assert.match(html,/现行/)
  assert.match(html,/autocomplete="username"/);assert.match(html,/autocomplete="current-password"/)
})
test('clarification and refusal show separate original controls without confirmed facts',async()=>{
  for(const status of ['NEED_CLARIFICATION','INSUFFICIENT_EVIDENCE']) {
    const result=decision(status);result.confirmedFacts=[];result.supportingReferences=[quote()];result.unknownItems=['适用条件尚不能确认']
    if(status==='NEED_CLARIFICATION'){result.clarificationQuestions=[{key:'type',question:'哪类课程',allowedValues:['普通','其他']}];result.clarificationToken=ID}
    else result.scope=null
    const html=await render({result});assert.match(html,/条件与适用范围依据/);assert.match(html,/不代表已作出结论/);assert.match(html,/核对原文/)
    assert.doesNotMatch(html,/合成确认事实/)
  }
})
test('all five decisions have distinct visible outcomes',async()=>{
  for(const [status,label] of Object.entries({ANSWERED:'有原文支持',PARTIALLY_ANSWERED:'有部分依据',NEED_CLARIFICATION:'需要确认条件',INSUFFICIENT_EVIDENCE:'暂无充分依据',CONFLICTING_EVIDENCE:'依据存在冲突'})) {
    const result=decision(status)
    if(status==='PARTIALLY_ANSWERED')result.unknownItems=['合成未知项']
    if(status==='NEED_CLARIFICATION'){result.confirmedFacts=[];result.clarificationQuestions=[{key:'type',question:'合成类型是什么',allowedValues:['普通','其他']}];result.clarificationToken=ID}
    if(status==='INSUFFICIENT_EVIDENCE'){result.confirmedFacts=[];result.scope=null;result.unknownItems=['无审核依据']}
    if(status==='CONFLICTING_EVIDENCE'){result.confirmedFacts=[];result.conflicts=[{issue:'合成相互矛盾',sides:[fact(),{...fact(),id:'other',text:'合成另一侧'}]}]}
    const html=await render({result});assert.match(html,new RegExp(label))
    if(status==='CONFLICTING_EVIDENCE'){assert.match(html,/合成相互矛盾/);assert.match(html,/合成另一侧/)}
  }
})
test('confirmed facts disclose declared conditions and original action',async()=>{
  const html=await render({result:decision()});assert.match(html,/合成规则覆盖范围/);assert.match(html,/合成年级/)
  assert.match(html,/你已确认/);assert.match(html,/核对原文/);assert.match(html,/2025.9.2/);assert.match(html,/仅在这些条件下确认/)
})
test('necessary clarification displays only allowed choices and no invented fact',async()=>{
  const result={...decision('NEED_CLARIFICATION'),confirmedFacts:[],clarificationQuestions:[{key:'type',question:'合成类型是什么',allowedValues:['普通','其他']}],clarificationToken:ID}
  const html=await render({result});assert.match(html,/合成类型是什么/);assert.match(html,/>普通</);assert.match(html,/>其他</)
  assert.match(html,/确认条件并继续/);assert.doesNotMatch(html,/合成确认事实/)
})
test('reviewed course type choices and confirmed facts use student labels while preserving submitted codes',async()=>{
  const condition={key:'courseType',question:'这门课属于公共选修还是专业选修？',allowedValues:['PUBLIC','PROFESSIONAL']}
  const clarified={...decision('NEED_CLARIFICATION'),confirmedFacts:[],confirmedConditions:{},clarificationQuestions:[condition],clarificationToken:ID}
  clarified.scope.conditions=[condition]
  const choices=await render({result:clarified})
  assert.match(choices,/value="PUBLIC"[^>]*>公共选修课/);assert.match(choices,/value="PROFESSIONAL"[^>]*>专业选修课/)
  const answered=decision();answered.confirmedConditions={courseType:'PUBLIC'};answered.scope.conditions=[condition];answered.confirmedFacts[0].when={courseType:'PUBLIC'}
  const html=await render({result:answered});assert.doesNotMatch(html,/>PUBLIC</);assert.match(html,/<dd[^>]*>公共选修课<\/dd>/)
})
test('original highlight uses relative span and recorded page mapping remains unknown',async()=>{
  const original={title:'合成手册',sourceLocator:'合成定位',revision:'2025.9.2',recordedPageNumber:1,pageNumberKind:'RECORDED_SOURCE_PAGE',contextText:'前文原文后文',contextStartInclusive:10,quoteStartInclusive:12,quoteEndExclusive:14,safeSourceUrl:'https://official.example/a.pdf',unknownItems:['物理页与印刷页映射尚未核实']}
  const html=await render({result:decision(),original});assert.match(html,/<mark[^>]*>原文<\/mark>/);assert.match(html,/记录页码/);assert.match(html,/映射尚未核实/)
  assert.match(html,/rel="noopener noreferrer"/);assert.match(html,/https:\/\/official.example\/a.pdf/)
})
test('policy and original are escaped text and javascript links are never emitted',async()=>{
  const result=decision();result.confirmedFacts[0].text='<script>evil()</script>';result.confirmedFacts[0].citations[0].text='<img src=x onerror=evil>'
  const original={title:'<script>title()</script>',revision:'2025.9.2',pageNumberKind:'UNKNOWN',contextText:'<script>body()</script>',contextStartInclusive:0,quoteStartInclusive:0,quoteEndExclusive:8,safeSourceUrl:'javascript:evil()',unknownItems:['未知页码']}
  const html=await render({result,original});assert.match(html,/&lt;script&gt;/);assert.doesNotMatch(html,/<script>|<img src=x|href="javascript:/)
})
test('pending, missing evidence, unconfirmed outcome and logout uncertainty stay distinct',async()=>{
  const pending=await render({busy:true});assert.match(pending,/正在查证/)
  const failed=await render({error:'本次查证结果未确认，请主动重新查证。'});assert.match(failed,/结果未确认/);assert.doesNotMatch(failed,/未受理|已受理/)
  const loggedOut=await render({actor:null,reason:'LOGOUT_UNCONFIRMED'});assert.match(loggedOut,/退出尚未确认/);assert.match(loggedOut,/重试退出/)
})
test('maintainer cannot use student question form',async()=>{
  const html=await render({actor:{role:'MAINTAINER',userId:ID,authRevision:0}});assert.match(html,/学生查证身份/);assert.doesNotMatch(html,/<textarea/)
})
test('unconfirmed login requires identity read before another login attempt',async()=>{
  const html=await render({actor:null,reason:'LOGIN_UNCONFIRMED'});assert.match(html,/登录结果尚未确认/);assert.match(html,/核对登录状态/)
  assert.match(html,/<button[^>]*type="submit"[^>]*disabled/)
})

// 用Vue实际setup/mount/unmount执行生命周期；虚拟host不伪称浏览器DOM或Cookie验收。
function mountLifecycle(runtime) {
  const host = createRenderer({
    createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
    insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {}, parentNode: () => null, nextSibling: () => null
  })
  const app = host.createApp({ ...component, render: () => null })
  app.provide(ssrContextKey, { modules: new Set() })
  app.provide('rule-check-runtime', runtime); app.mount({})
  return { app, setup: app._instance.setupState }
}
test('login pending then unmount and return preserves uncertainty until identity read', async () => {
  const session = { state: reactive({ actor: null, epoch: 0, identityBusy: false, reason: '' }) }
  let rejectLogin, posts = 0, reads = 0
  const runtime = { session, client: {
    async login() { posts++; session.state.epoch++; session.state.reason = ''; session.state.identityBusy = true
      try { await new Promise((_,reject) => { rejectLogin = reject }) }
      finally { session.state.identityBusy = false }
    },
    async me() { reads++; session.state.actor = { role: 'USER', userId: ID, authRevision: 0 } }
  }, workflow: { state: reactive({ result: null, question: '' }), dispose() {}, reset() {} } }
  const first = mountLifecycle(runtime), pending = first.setup.login()
  first.app.unmount()
  const returned = mountLifecycle(runtime)
  rejectLogin({ code: 'NETWORK_UNAVAILABLE' }); await pending; await nextTick()
  assert.equal(session.state.reason, 'LOGIN_UNCONFIRMED')
  await returned.setup.login(); assert.equal(posts, 1, 'unknown identity must block another POST')
  await returned.setup.checkLogin(); assert.equal(reads, 1); assert.equal(session.state.reason, '')
  returned.app.unmount()
})
test('unmounted login failure cannot mark a later identity uncertain', async () => {
  const session = { state: reactive({ actor: null, epoch: 0, identityBusy: false, reason: '' }) }
  let rejectLogin
  const runtime = { session, client: { async login() { session.state.epoch++; await new Promise((_,reject) => { rejectLogin = reject }) } },
    workflow: { state: reactive({ result: null, question: '' }), dispose() {}, reset() {} } }
  const first = mountLifecycle(runtime), pending = first.setup.login(); first.app.unmount()
  session.state.epoch++; session.state.reason = ''; session.state.actor = { role: 'USER', userId: ID, authRevision: 1 }
  rejectLogin({ code: 'NETWORK_UNAVAILABLE' }); await pending
  assert.equal(session.state.reason, ''); assert.equal(session.state.actor.authRevision, 1)
})
