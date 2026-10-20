<script setup>
import { ref, computed, watch, inject, onMounted, onUnmounted } from 'vue'
import PasswordInput from '../components/PasswordInput.vue'
import RuleCheckFact from '../components/RuleCheckFact.vue'
import { ruleTaskClient } from '../api/rule-task.js'
import { ruleCheckSession } from '../state/rule-check-session.js'
import { createRuleTaskWorkflow } from '../state/rule-task-workflow.js'
import { ruleCheckConditionLabel } from '../state/rule-check-labels.js'

// 测试可注入渲染状态；正式入口仍只从独立v2客户端取得结果，不提供URL资料自证参数。
const runtime = inject('rule-task-runtime', null)
// 模拟范围在构建时固定，登录前也应告知；不能等待首个结果才暴露它不具备通用问答能力。
const simulated = runtime?.simulated ?? import.meta.env.VITE_DEMO_MODE === 'simulated'
const session = runtime?.session || ruleCheckSession, api = runtime?.client || ruleTaskClient
const flow = runtime?.workflow || createRuleTaskWorkflow({client:api,session}), state = flow.state
const username = ref(''), password = ref(''), question = ref(''), choice = ref(''), loginError = ref('')
const loginBusy = ref(false), authChecking = ref(!runtime)
let alive = true
const student = computed(() => session.state.actor?.role === 'USER')
const result = computed(() => state.evidenceUnavailable ? null : state.snapshot?.result)
const nextCondition = computed(() => result.value?.items.flatMap(q => q.clarification)[0])
const active = computed(() => !!state.ticket && (!state.snapshot || state.snapshot.taskStatus === 'ACTIVE'))
const title = computed(() => state.snapshot?.taskStatus === 'WAITING_USER' ? '需要补充条件' : state.snapshot?.taskStatus === 'STOPPED' ? '本轮已停止' : state.snapshot?.taskStatus === 'FAILED' ? '本轮处理失败' : result.value?.coverage === 'COMPLETE' ? '有依据可确认' : result.value?.coverage === 'PARTIAL' ? '有部分依据' : '仍有未确认项')
const identityMessage = computed(() => session.state.reason === 'LOGOUT_UNCONFIRMED' ? '退出尚未确认，请重试退出。'
  : session.state.reason === 'LOGIN_UNCONFIRMED' ? '登录结果尚未确认，请先核对登录状态。'
  : session.state.reason === 'IDENTITY_CHANGED_ELSEWHERE' ? '登录状态已在其他窗口变更，请重新登录。'
  : session.state.reason === 'AUTH_EXPIRED' ? '登录已失效，请重新登录。' : '')
const originalParts = computed(() => {
  const original = state.original
  if (!original) return { before: '', quote: '', after: '' }
  const value = original.contextText, start = original.quoteStartInclusive - original.contextStartInclusive, end = original.quoteEndExclusive - original.contextStartInclusive
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || end <= start || end > value.length) return { before: value, quote: '', after: '' }
  return { before: value.slice(0, start), quote: value.slice(start, end), after: value.slice(end) }
})
const originalLink = computed(() => {
  const value = state.original?.safeSourceUrl
  if (!value) return null
  try { const url = new URL(value); return url.protocol === 'https:' && !url.username && !url.password && (!url.port || url.port === '443') ? value : null }
  catch { return null }
})
watch(() => state.snapshot, () => { choice.value = '' })
watch(() => session.state.epoch, () => { question.value = ''; username.value = ''; password.value = ''; choice.value = ''; loginError.value = '' }, { flush: 'sync' })
watch(question, value => {
  // 编辑问题后旧依据不再对应当前输入；不能让用户以新题的视角误读上轮回答。
  if ((state.snapshot || state.ticket) && value !== state.question) flow.reset()
})
onMounted(async () => {
  if (runtime || session.state.identityBusy) { authChecking.value = false; return }
  await checkLogin()
})
onUnmounted(() => { alive = false; password.value = ''; flow.dispose() })
async function login() {
  if (loginBusy.value || session.state.identityBusy || session.state.reason === 'LOGIN_UNCONFIRMED') return
  loginBusy.value = true; loginError.value = ''
  const pending = api.login(username.value.trim(), password.value), epoch = session.state.epoch
  // 身份请求可能跨页面存活；先保留未确认状态，不能随组件销毁丢失，也不能污染下一代身份。
  session.state.reason = 'LOGIN_UNCONFIRMED'
  try { await pending; if (session.state.epoch === epoch) session.state.reason = '' }
  catch (error) {
    if (error.code !== 'STALE_RESPONSE') {
      const unknown = error.code === 'NETWORK_UNAVAILABLE' || error.code === 'BAD_RESPONSE'
      if (!unknown && session.state.epoch === epoch) session.state.reason = ''
      if (alive && !unknown) loginError.value = error.status === 401 ? '登录未成功，请检查账号和密码。' : '登录暂不可用，请稍后再试。'
    }
  }
  finally { password.value = ''; if (alive) loginBusy.value = false }
}
async function checkLogin() {
  if (session.state.identityBusy) return
  authChecking.value = true; loginError.value = ''
  try { await api.me(); if (alive) session.state.reason = '' }
  catch (error) { if (alive && error.code !== 'STALE_RESPONSE' && error.status !== 401) loginError.value = '暂时无法核对登录状态，请稍后重试。' }
  finally { if (alive) authChecking.value = false }
}
async function logout() {
  loginError.value = ''; flow.reset()
  try { await api.logout() }
  catch (error) { if (alive && error.code !== 'STALE_RESPONSE') loginError.value = '退出尚未确认，请重试退出。' }
}
function newQuestion() { if (active.value || state.pending || state.busy) return; flow.reset(); question.value = ''; choice.value = '' }
const issueLabels = { DATA_GAP: '资料缺口', CLARIFICATION: '需要补充条件', PROCESSING_FAILURE: '处理未完成', SCOPE_LIMIT: '适用范围待确认', CONFLICT: '依据存在冲突', REPLAY_MISS: '回放未覆盖' }
const relatedEvidence = q => result.value.evidence.filter(e => result.value.evidenceRelations.some(r => r.subquestionId === q.id && r.evidenceId === e.id))
</script>
<template>
  <main class="rule-page">
    <header class="rule-header"><RouterLink to="/rules" class="rule-brand">仲园规则查证</RouterLink>
      <nav aria-label="查证导航"><button v-if="session.state.actor" type="button" :disabled="session.state.identityBusy" @click="logout">退出登录</button></nav>
    </header>
    <section class="rule-intro"><h1>先找到依据，再理解规则。</h1><p>用你的话提问，确认必要条件，逐条核对原文。</p><p class="reference-note">首轮以2025版文本为依据；现行适用性仍需核实。</p>
      <p v-if="simulated" class="rule-notice">工程模拟：仅支持下方固定问题，不调用在线模型。<br />我这门选修课没过，能不能重修？<br />可补充公共选修课或专业选修课；仅验证历史资料的查证流程。</p>
      <p v-if="$route.query.entry === 'rules'" class="rule-hint">学生入口提供规则查证，请在这里提交问题。</p></section>
    <section v-if="!session.state.actor" class="rule-login" aria-labelledby="rule-login-title">
      <h2 id="rule-login-title">登录规则查证</h2><p>使用受控演示账号，查看当前可查证的规则。</p><p v-if="authChecking" role="status">正在核对登录状态…</p>
      <form @submit.prevent="login"><label for="username">账号</label><input id="username" v-model="username" name="username" autocomplete="username" required maxlength="32" :disabled="authChecking || loginBusy || session.state.identityBusy" />
        <label for="password">密码</label><PasswordInput v-model="password" :disabled="authChecking || loginBusy || session.state.identityBusy" />
        <p id="password-hint" class="rule-hint">请输入演示账号的密码（16～64位英文字符）。</p><p id="login-feedback" role="status" aria-live="polite">{{ loginError || identityMessage }}</p>
        <button class="rule-primary" type="submit" :disabled="authChecking || loginBusy || session.state.identityBusy || session.state.reason === 'LOGIN_UNCONFIRMED'">{{ loginBusy || session.state.identityBusy ? '正在切换登录状态…' : '登录' }}</button>
      </form>
      <button v-if="session.state.reason === 'LOGOUT_UNCONFIRMED'" type="button" :disabled="session.state.identityBusy" @click="logout">重试退出</button>
      <button v-if="session.state.reason === 'LOGIN_UNCONFIRMED'" type="button" :disabled="authChecking || session.state.identityBusy" @click="checkLogin">核对登录状态</button>
    </section>
    <p v-else-if="!student" class="rule-notice">当前账号不是学生查证身份，请退出后使用学生查证身份登录。</p>
    <div v-else class="rule-desk">
      <section class="question-column" aria-label="问题与查证结果">
        <form class="question-form" @submit.prevent="flow.start(question)"><label for="rule-question">你想查证什么？</label>
          <textarea id="rule-question" v-model="question" required maxlength="2048" rows="4" :disabled="authChecking || state.busy || active || !!state.pending || session.state.identityBusy" placeholder="例如：公共选修课有什么学分限制？"></textarea>
          <div class="question-actions"><span>{{ question.length }}/2048</span><button type="button" :disabled="active || !!state.pending || state.busy" @click="newQuestion">新问题</button><button class="rule-primary" type="submit" :disabled="!question.trim() || authChecking || state.busy || active || !!state.pending || session.state.identityBusy">{{ state.busy ? '正在提交…' : '查证规则' }}</button></div>
        </form>
        <p v-if="state.error" class="rule-notice" role="alert">{{ state.error }}</p>
        <button v-if="state.pending" :disabled="state.busy || session.state.identityBusy" @click="flow.recover">查询原请求</button>
        <button v-else-if="state.ticket && !state.polling && !state.busy" :disabled="session.state.identityBusy" @click="flow.refresh">继续查询</button>
        <div v-if="active" class="query-progress" role="status" aria-live="polite"><p>{{ state.cancelRequested ? '停止请求已受理，等待本轮结束。' : '问题已受理，正在查证。' }}</p>
          <button type="button" :disabled="state.busy || !!state.pending || state.cancelRequested || session.state.identityBusy" @click="flow.cancel">停止本次查证</button></div>
        <section v-if="result" class="decision" aria-labelledby="decision-title" aria-live="polite">
          <header><p class="decision-question">本次问题：{{ state.question }}</p><h2 id="decision-title">{{ title }}</h2><p>依据版本 {{ result.referenceRevision }}</p></header>
          <p v-if="result.publishedVersion < state.snapshot.version" class="rule-hint">以下为上轮已确认内容，本轮补充仍在处理。</p>
          <p v-if="state.snapshot.execution.mode === 'ENGINEERING_REPLAY'" class="rule-notice">工程回放：本次没有调用在线模型；回放覆盖情况不能代表资料或在线能力。</p>
          <section class="confirmed-summary"><h3>当前能确认什么</h3>
            <template v-for="q in result.items" :key="q.id"><article v-for="claim in q.claims" :key="claim.id" class="rule-fact"><h3>{{ claim.text }}</h3>
              <div v-if="Object.keys(claim.when).length" class="declared-conditions"><p>仅在以下声明条件下解释，不代表个人资格认定</p><dl><template v-for="(value,key) in claim.when" :key="key"><dt>{{ key }}</dt><dd>{{ ruleCheckConditionLabel(key,value) }}</dd></template></dl></div>
            </article></template>
            <p v-if="!result.items.some(q => q.claims.length)">本次尚无可确认的规则内容。</p>
          </section>
          <section v-if="result.items.some(q => q.issues.length)" class="rule-unknown"><h3>还缺什么，下一步怎么办</h3>
            <template v-for="q in result.items" :key="q.id"><article v-for="issue in q.issues" :key="issue.id" class="result-issue"><h4>{{ issueLabels[issue.kind] }}</h4><p>{{ issue.message }}</p><p class="next-step">下一步：{{ issue.nextStep }}</p></article></template>
          </section>
          <form v-if="state.snapshot.taskStatus === 'WAITING_USER' && nextCondition" class="clarification" @submit.prevent="flow.clarify(choice)"><label for="rule-condition">{{ nextCondition.question }}</label>
            <select id="rule-condition" v-model="choice" required :disabled="state.busy || !!state.pending"><option disabled value="">请选择你能确认的情况</option><option v-for="value in nextCondition.allowedValues" :key="value" :value="value">{{ ruleCheckConditionLabel(nextCondition.conditionId,value) }}</option></select>
            <button class="rule-primary" type="submit" :disabled="!choice || state.busy || !!state.pending">确认条件并继续</button><p class="rule-hint">无法确认时保留未知；补充会继续原任务。</p></form>
          <section v-if="result.evidence.length" class="related-evidence"><h3>相关依据</h3><p class="rule-hint">按本次结果关联展示，原文默认折叠。</p>
            <template v-for="q in result.items" :key="q.id"><details v-for="evidence in relatedEvidence(q)" :key="evidence.id"><summary>查看相关段落 · {{ evidence.revision }}</summary>
              <blockquote>{{ evidence.quote }}</blockquote><button type="button" :disabled="state.originalBusy" @click="flow.openOriginal(evidence.originalToken)">核对原文</button></details></template>
          </section>
        </section>
        <div v-else-if="!state.ticket && !state.error" class="empty-result"><h2>从一个具体问题开始</h2><p>有依据时解释规则，需要条件时追问，未完成时说明原因。</p></div>
      </section>
      <aside id="original-panel" class="original-column" aria-label="原文对照" aria-live="polite"><h2>原文对照</h2><p v-if="state.originalBusy" role="status">正在核对当前原文…</p>
        <template v-else-if="state.original"><h3>{{ state.original.title || '来源标题未记录' }}</h3><p>版本 {{ state.original.revision }}</p><p>{{ state.original.sourceLocator || '条款定位尚未记录' }}</p>
          <p v-if="state.original.pageNumberKind === 'RECORDED_SOURCE_PAGE'">记录页码 {{ state.original.recordedPageNumber }}</p><p v-else>页码尚未记录</p>
          <details><summary>展开本条原文与必要上下文</summary><blockquote class="original-text">{{ originalParts.before }}<mark>{{ originalParts.quote }}</mark>{{ originalParts.after }}</blockquote></details>
          <ul class="original-unknown"><li v-for="(unknown,i) in state.original.unknownItems" :key="i">{{ unknown }}</li></ul>
          <a v-if="originalLink" class="original-link" :href="originalLink" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer">打开原件</a></template>
        <p v-else class="original-placeholder">展开结果中的相关段落，再点击“核对原文”。</p>
      </aside>
    </div>
    <footer class="rule-footer">个人项目，非学校官方系统。规则查证不能代替个人修读资格认定或教务办理。</footer>
  </main>
</template>
<style scoped>
.rule-fact, .result-issue { padding: 18px 0; } .result-issue h4 { color: #7b4b17; } .next-step { margin-top: 8px; } details { border-top: 1px solid #d8ddcf; padding: 14px 0; } summary { cursor: pointer; color: #28553f; } .related-evidence { margin-top: 28px; } .related-evidence blockquote { white-space: pre-wrap; margin: 14px 0; padding-left: 14px; border-left: 3px solid #a9bca5; }
.rule-page { min-height: 100vh; width: 100%; background: #faf8f2; color: #252a24; color-scheme: light; font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif; line-height: 1.7; padding: 0 36px; }
.rule-header { max-width: 1200px; margin: auto; padding: 24px 0; display: flex; align-items: center; justify-content: space-between; gap: 24px; border-bottom: 1px solid #d8ddcf; }
.rule-brand { color: #28553f; font-weight: 700; font-size: 20px; }
nav { display: flex; align-items: center; flex-wrap: wrap; gap: 22px; font-size: 14px; }
.rule-intro { max-width: 1200px; margin: 42px auto 34px; }
h1 { font-family: 'Songti SC', SimSun, serif; font-size: clamp(28px, 4vw, 42px); font-weight: 700; letter-spacing: .025em; margin-bottom: 12px; }
.reference-note, .rule-hint, .rule-footer { color: #647261; font-size: 13px; }
.reference-note { margin-top: 14px; }
.rule-desk { max-width: 1200px; margin: auto; display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr); gap: 40px; }
.question-column, .original-column { min-width: 0; }
.question-form label { display: block; font-size: 18px; font-weight: 600; margin-bottom: 12px; }
input, textarea, select { width: 100%; color: #252a24; border: 1px solid #a5b29e; border-radius: 2px; background: #fff; padding: 12px; font: inherit; }
textarea { resize: vertical; min-height: 130px; }
button { font: inherit; color: #28553f; padding: 9px 16px; border: 1px solid #a5b29e; border-radius: 2px; background: #fff; }
.rule-primary { color: #fff; background: #28553f; border-color: #28553f; font-weight: 600; }
button:disabled, input:disabled, textarea:disabled { opacity: .55; cursor: not-allowed; }
button:focus-visible, a:focus-visible, input:focus-visible, textarea:focus-visible, select:focus-visible { outline: 3px solid #28553f; outline-offset: 3px; }
.question-actions { display: flex; gap: 12px; align-items: center; margin-top: 12px; }
.question-actions span { margin-right: auto; font-size: 12px; color: #647261; }
.decision { margin-top: 36px; }
.decision header { border-top: 3px solid #28553f; padding-top: 20px; }
.decision-question, .decision header > p:last-child { font-size: 13px; color: #647261; overflow-wrap: anywhere; }
h2 { font-size: 23px; margin: 6px 0 14px; }
h3 { font-size: 17px; }
.question-scope { margin: 20px 0; padding-left: 14px; border-left: 3px solid #a9bca5; white-space: pre-wrap; overflow-wrap: anywhere; }
.declared-conditions { margin: 20px 0; font-size: 14px; }
dl { display: grid; grid-template-columns: minmax(80px, 1fr) 2fr; gap: 6px 16px; }
.clarification { padding: 24px 0; border-top: 1px solid #d8ddcf; }
.clarification label { display: block; margin-bottom: 12px; font-weight: 600; }
.clarification select { margin-bottom: 14px; }
.clarification .rule-hint { margin-top: 12px; }
.rule-unknown, .rule-conflict { padding: 22px 0; border-top: 1px solid #d8ddcf; }
ul { padding-left: 22px; margin-top: 10px; }
li { margin-bottom: 8px; overflow-wrap: anywhere; }
.original-column { background: #fff; padding: 26px; border-top: 3px solid #a9bca5; align-self: start; }
.original-column h2 { margin-top: 0; }
.original-column h3, .original-column p { overflow-wrap: anywhere; }
.original-version, .original-unknown, .original-placeholder { color: #647261; font-size: 13px; }
.original-text { margin: 24px 0; line-height: 2; white-space: pre-wrap; overflow-wrap: anywhere; }
mark { background: #f1e4ab; color: #252a24; padding: 2px 0; }
.original-link { display: inline-block; text-decoration: underline; color: #28553f; margin-top: 16px; }
.rule-notice { max-width: 1200px; margin: 24px auto; border-left: 3px solid #a8853a; padding: 12px 16px; background: #f5efdc; }
.query-progress, .empty-result { margin: 34px 0; color: #647261; }
.empty-result h2 { color: #465346; font-size: 20px; }
.rule-footer { max-width: 1200px; margin: 50px auto 0; padding: 24px 0; border-top: 1px solid #d8ddcf; }
.rule-login { max-width: 440px; margin: 0 0 0 max(0px, calc((100% - 1200px) / 2)); padding: 26px 0; }
.rule-login form { margin-top: 20px; }
.rule-login label { display: block; margin: 14px 0 7px; }
.rule-login #login-feedback { min-height: 28px; margin: 10px 0; color: #7b4b17; font-size: 14px; }
.rule-login > button { margin-top: 16px; }
.rule-login :deep(.password-field) { display: flex; border: 1px solid #a5b29e; background: #fff; }
.rule-login :deep(.password-field input) { border: 0; min-width: 0; flex: 1; }
.rule-login :deep(.password-field button) { border: 0; padding: 10px 12px; }
.rule-login :deep(.icon) { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 1.7; }
@media (max-width: 800px) { .rule-desk { grid-template-columns: 1fr; gap: 30px; } .rule-page { padding: 0 20px; } .rule-header { align-items: flex-start; flex-direction: column; gap: 12px; } .rule-intro { margin-top: 30px; } .original-column { padding: 22px; } }
@media (prefers-reduced-motion: reduce) { * { scroll-behavior: auto; } }
</style>
<style>
/* 直接打开/rules时旧校园CSS尚未加载，必须独立解除Vite模板的居中/宽度限制。 */
body.rule-check-page { display: block; min-width: 320px; }
body.rule-check-page #app { max-width: none; margin: 0; padding: 0; text-align: left; height: auto; }
</style>
