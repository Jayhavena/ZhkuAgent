<script setup>
import { ref, computed, watch, inject, onMounted, onUnmounted } from 'vue'
import PasswordInput from '../components/PasswordInput.vue'
import RuleCheckFact from '../components/RuleCheckFact.vue'
import { ruleCheckClient } from '../api/rule-check.js'
import { ruleCheckSession } from '../state/rule-check-session.js'
import { createRuleCheckWorkflow } from '../state/rule-check-workflow.js'
import { ruleCheckConditionLabel } from '../state/rule-check-labels.js'

// 测试可注入渲染状态；正式入口仍只从独立v2客户端取得结果，不提供URL资料自证参数。
const runtime = inject('rule-check-runtime', null)
const session = runtime?.session || ruleCheckSession, api = runtime?.client || ruleCheckClient
const flow = runtime?.workflow || createRuleCheckWorkflow(), state = flow.state
const username = ref(''), password = ref(''), question = ref(''), choice = ref(''), loginError = ref('')
const loginBusy = ref(false), authChecking = ref(!runtime)
let alive = true
const student = computed(() => session.state.actor?.role === 'USER')
const nextCondition = computed(() => state.result?.clarificationQuestions?.[0])
const labels = { ANSWERED: '有原文支持', PARTIALLY_ANSWERED: '有部分依据', NEED_CLARIFICATION: '需要确认条件', INSUFFICIENT_EVIDENCE: '暂无充分依据', CONFLICTING_EVIDENCE: '依据存在冲突' }
const identityMessage = computed(() => session.state.reason === 'LOGOUT_UNCONFIRMED' ? '退出尚未确认，请重试退出。'
  : session.state.reason === 'LOGIN_UNCONFIRMED' ? '登录结果尚未确认，请先核对登录状态。'
  : session.state.reason === 'AUTH_EXPIRED' ? '登录已失效，请重新登录。' : '')
const conditionName = key => state.result?.scope?.conditions.find(c => c.key === key)?.question || key
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
watch(() => state.result, () => { choice.value = '' })
watch(() => session.state.epoch, () => { question.value = ''; username.value = ''; password.value = ''; choice.value = ''; loginError.value = '' }, { flush: 'sync' })
watch(question, value => {
  // 编辑问题后旧依据不再对应当前输入；不能让用户以新题的视角误读上轮回答。
  if (state.result && value !== state.question) flow.reset()
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
function newQuestion() { flow.reset(); question.value = ''; choice.value = '' }
</script>

<template>
  <main class="rule-page">
    <header class="rule-header"><RouterLink to="/" class="rule-brand">仲园规则查证</RouterLink>
      <nav aria-label="查证导航"><RouterLink to="/campus/run">学分实验</RouterLink><RouterLink to="/super-agent">通用助手实验</RouterLink>
        <button v-if="session.state.actor" type="button" :disabled="session.state.identityBusy" @click="logout">退出登录</button></nav>
    </header>
    <section class="rule-intro"><h1>先找到依据，再理解规则。</h1><p>用你的话提问，确认必要条件，逐条核对原文。</p>
      <p class="reference-note">首轮以2025版文本为依据；现行适用性仍需核实。</p></section>
    <section v-if="!session.state.actor" class="rule-login" aria-labelledby="rule-login-title">
      <h2 id="rule-login-title">登录规则查证</h2><p>使用受控演示账号，查看当前可查证的规则。</p>
      <p v-if="authChecking" role="status">正在核对登录状态…</p>
      <form @submit.prevent="login"><label for="username">账号</label><input id="username" v-model="username" name="username" autocomplete="username" required maxlength="32" :disabled="authChecking || loginBusy || session.state.identityBusy" />
        <label for="password">密码</label><PasswordInput v-model="password" :disabled="authChecking || loginBusy || session.state.identityBusy" />
        <p id="password-hint" class="rule-hint">请输入演示账号的密码（16～64位英文字符）。</p>
        <p id="login-feedback" role="status" aria-live="polite">{{ loginError || identityMessage }}</p>
        <button class="rule-primary" type="submit" :disabled="authChecking || loginBusy || session.state.identityBusy || session.state.reason === 'LOGIN_UNCONFIRMED'">{{ loginBusy || session.state.identityBusy ? '正在切换登录状态…' : '登录' }}</button>
      </form>
      <button v-if="session.state.reason === 'LOGOUT_UNCONFIRMED'" type="button" :disabled="session.state.identityBusy" @click="logout">重试退出</button>
      <button v-if="session.state.reason === 'LOGIN_UNCONFIRMED'" type="button" :disabled="authChecking || session.state.identityBusy" @click="checkLogin">核对登录状态</button>
    </section>
    <p v-else-if="!student" class="rule-notice">当前账号不是学生查证身份，请退出后使用学生查证身份登录。</p>
    <div v-else class="rule-desk">
      <section class="question-column" aria-label="问题与查证结果">
        <p v-if="authChecking" role="status">正在核对登录状态…</p>
        <form class="question-form" @submit.prevent="flow.start(question)"><label for="rule-question">你想查证什么？</label>
          <textarea id="rule-question" v-model="question" required maxlength="2048" rows="4" :disabled="authChecking || state.busy || session.state.identityBusy" placeholder="例如：公共选修课有什么学分限制？"></textarea>
          <div class="question-actions"><span>{{ question.length }}/2048</span><button type="button" @click="newQuestion">新问题</button><button class="rule-primary" type="submit" :disabled="!question.trim() || authChecking || state.busy || session.state.identityBusy">{{ state.busy ? '正在查证…' : '查证规则' }}</button></div>
        </form>
        <p v-if="state.error" class="rule-notice" role="alert">{{ state.error }}</p>
        <p v-if="state.busy" class="query-progress" role="status" aria-live="polite">正在查证，请等待本次结果。</p>
        <section v-else-if="state.result" class="decision" aria-labelledby="decision-title" aria-live="polite">
          <header><p class="decision-question">本次问题：{{ state.question }}</p><h2 id="decision-title">{{ labels[state.result.status] }}</h2><p>依据版本 {{ state.result.referenceRevision }}</p></header>
          <p v-if="state.result.scope" class="question-scope">本次覆盖：{{ state.result.scope.questionScope }}</p>
          <div v-if="Object.keys(state.result.confirmedConditions).length" class="declared-conditions"><h3>你已确认</h3><dl><template v-for="(value,key) in state.result.confirmedConditions" :key="key"><dt>{{ conditionName(key) }}</dt><dd>{{ ruleCheckConditionLabel(key,value) }}</dd></template></dl></div>
          <form v-if="state.result.status === 'NEED_CLARIFICATION' && nextCondition" class="clarification" @submit.prevent="flow.clarify(nextCondition.key,choice)">
            <label for="rule-condition">{{ nextCondition.question }}</label><select id="rule-condition" v-model="choice" required><option disabled value="">请选择你能确认的情况</option><option v-for="value in nextCondition.allowedValues" :key="value" :value="value">{{ ruleCheckConditionLabel(nextCondition.key,value) }}</option></select>
            <button class="rule-primary" type="submit" :disabled="!choice">确认条件并继续</button><p class="rule-hint">无法确认时保留未知，先核对相关资料。</p>
          </form>
          <RuleCheckFact v-for="fact in state.result.confirmedFacts" :key="fact.id" :fact="fact" :conditions="state.result.scope?.conditions || []" :disabled="state.originalBusy" @original="flow.openOriginal" />
          <section v-if="state.result.supportingReferences?.length" class="rule-supporting"><h3>条件与适用范围依据</h3>
            <p>这些原文用于核对条件或资料覆盖范围，不代表已作出结论。</p>
            <div v-for="reference in state.result.supportingReferences" :key="reference.citationId"><blockquote>{{ reference.text }}</blockquote>
              <button type="button" :disabled="state.originalBusy" @click="flow.openOriginal(reference.originalToken)">核对原文</button></div>
          </section>
          <section v-for="(conflict,index) in state.result.conflicts" :key="index" class="rule-conflict"><h3>{{ conflict.issue }}</h3><p>这些依据尚未形成一致结论。</p>
            <RuleCheckFact v-for="(side,i) in conflict.sides" :key="i" :fact="side" :conditions="state.result.scope?.conditions || []" :disabled="state.originalBusy" @original="flow.openOriginal" /></section>
          <section v-if="state.result.unknownItems.length" class="rule-unknown"><h3>仍无法确认</h3><ul><li v-for="(unknown,i) in state.result.unknownItems" :key="i">{{ unknown }}</li></ul></section>
        </section>
        <div v-else-if="!state.error" class="empty-result"><h2>从一个具体问题开始</h2><p>有充分依据时解释规则；需要条件时追问；缺证或冲突时说明未知。</p></div>
      </section>
      <aside id="original-panel" class="original-column" aria-label="原文对照" aria-live="polite"><h2>原文对照</h2>
        <p v-if="state.originalBusy" role="status">正在核对当前原文…</p>
        <template v-else-if="state.original"><h3>{{ state.original.title || '来源标题未记录' }}</h3><p class="original-version">版本 {{ state.original.revision }}</p><p>{{ state.original.sourceLocator || '条款定位尚未记录' }}</p>
          <p v-if="state.original.pageNumberKind === 'RECORDED_SOURCE_PAGE'">记录页码 {{ state.original.recordedPageNumber }}</p><p v-else>页码尚未记录</p>
          <p v-if="state.original.sectionTitle">{{ state.original.sectionTitle }}</p>
          <blockquote class="original-text">{{ originalParts.before }}<mark>{{ originalParts.quote }}</mark>{{ originalParts.after }}</blockquote>
          <ul class="original-unknown"><li v-for="(unknown,i) in state.original.unknownItems" :key="i">{{ unknown }}</li></ul>
          <a v-if="originalLink" class="original-link" :href="originalLink" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer">打开原件</a>
        </template>
        <p v-else class="original-placeholder">点击回答中的“核对原文”，对照摘录、条款和记录页码。当前没有可展示的原文。</p>
      </aside>
    </div>
    <footer class="rule-footer">个人项目，非学校官方系统。规则查证不能代替个人修读资格认定或教务办理。</footer>
  </main>
</template>

<style scoped>
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
