<script setup>
import { ref, computed, watch, nextTick, inject, onMounted, onUnmounted } from 'vue'
import RuleHeader from '../components/rule-check/RuleHeader.vue'
import RuleLogin from '../components/rule-check/RuleLogin.vue'
import RuleWelcome from '../components/rule-check/RuleWelcome.vue'
import RuleComposer from '../components/rule-check/RuleComposer.vue'
import RuleAnswer from '../components/rule-check/RuleAnswer.vue'
import RuleOriginal from '../components/rule-check/RuleOriginal.vue'
import { ruleTaskClient } from '../api/rule-task.js'
import { ruleCheckSession } from '../state/rule-check-session.js'
import { createRuleTaskWorkflow } from '../state/rule-task-workflow.js'
import { ruleCheckConditionLabel } from '../state/rule-check-labels.js'

const runtime = inject('rule-task-runtime', null)
const hostInfo = ref(null)
const modeError = ref('')
const simulated = computed(() => hostInfo.value?.mode === 'SIMULATED' || (runtime?.simulated ?? import.meta.env.VITE_DEMO_MODE === 'simulated'))
const httpFixture = runtime?.httpFixture ?? import.meta.env.VITE_DEMO_MODE === 'http-fixture'
const session = runtime?.session || ruleCheckSession
const api = runtime?.client || ruleTaskClient
const flow = runtime?.workflow || createRuleTaskWorkflow({ client: api, session })
const state = flow.state

const username = ref('')
const password = ref('')
const rememberMe = ref(true)
const question = ref('')
const choice = ref('')
const loginError = ref('')
const loginBusy = ref(false)
const authChecking = ref(!runtime)
const originalOpen = ref(false)
let alive = true

const student = computed(() => session.state.actor?.role === 'USER')
const result = computed(() => state.evidenceUnavailable ? null : state.snapshot?.result)
const nextCondition = computed(() => result.value?.items.flatMap(item => item.clarification)[0])
const active = computed(() => !!state.ticket && (!state.snapshot || state.snapshot.taskStatus === 'ACTIVE'))
const hasConversation = computed(() => !!state.question || !!state.ticket || !!state.pending)
const waitingForClarification = computed(() => state.snapshot?.taskStatus === 'WAITING_USER')
const terminal = computed(() => ['RESOLVED', 'STOPPED', 'FAILED'].includes(state.snapshot?.taskStatus))
const composerDisabled = computed(() => authChecking.value || session.state.identityBusy || state.busy || !!state.pending || active.value || waitingForClarification.value)

const hasProcessingFailure = computed(() => !!result.value?.items.some(item => item.issues.some(issue => issue.kind === 'PROCESSING_FAILURE')))
const answerKind = computed(() => {
  if (state.snapshot?.taskStatus === 'WAITING_USER') return 'clarification'
  if (state.snapshot?.taskStatus === 'STOPPED') return 'stopped'
  if (state.snapshot?.taskStatus === 'FAILED' || hasProcessingFailure.value) return 'failure'
  if (result.value?.coverage === 'COMPLETE') return 'complete'
  if (result.value?.coverage === 'PARTIAL') return 'partial'
  return 'unknown'
})
// 结论优先：有已发布 claim 时，标题直接用第一条 claim 的原文，而不是“查证结果”这类状态词。
// 标题只是把已发布内容提前展示，不生成新结论；没有 claim 时仍回到描述状态的中性标题。
const leadClaim = computed(() => {
  if (!result.value) return null
  for (const item of result.value.items) if (item.claims.length) return { item, claim: item.claims[0] }
  return null
})
const usesConclusionTitle = computed(() =>
  !!leadClaim.value && (answerKind.value === 'complete' || answerKind.value === 'partial'))
const answerTitle = computed(() => usesConclusionTitle.value
  ? leadClaim.value.claim.text
  : ({
      clarification: '需要确认一个条件',
      stopped: '本次查证已停止',
      failure: '这次查证没有完成',
      complete: '查证结果',
      partial: '目前只能确认部分内容',
      unknown: '暂时无法确认'
    })[answerKind.value])
// 标题已经承载第一条结论，正文只列其余 claim，避免同一句话在页面上出现两次。
const remainingClaims = computed(() => {
  const lead = usesConclusionTitle.value ? leadClaim.value.claim.id : null
  return (result.value?.items || []).flatMap(item =>
    item.claims.filter(claim => claim.id !== lead).map(claim => ({ item, claim })))
})
const evidenceFor = (subquestionId, targetId, role) => result.value
  ? result.value.evidenceRelations
      .filter(relation => relation.subquestionId === subquestionId && relation.targetId === targetId && relation.role === role)
      .map(relation => result.value.evidence.find(evidence => evidence.id === relation.evidenceId))
      .filter(Boolean)
  : []
// 行内引用只取 CLAIM 用途的已发布证据；其他用途（澄清/冲突/范围）不是“支持该结论”的依据。
const claimCitations = (item, claim) => evidenceFor(item.id, claim.id, 'CLAIM')
const answerStatus = computed(() => ({
  clarification: '需要补充',
  stopped: '已停止',
  failure: '处理未完成',
  complete: '已找到依据',
  partial: '部分确认',
  unknown: '尚无法确认'
})[answerKind.value])

const identityMessage = computed(() => session.state.reason === 'LOGOUT_UNCONFIRMED' ? '退出尚未确认，请重试退出。'
  : session.state.reason === 'LOGIN_UNCONFIRMED' ? '登录结果尚未确认，请先核对登录状态。'
  : session.state.reason === 'IDENTITY_CHANGED_ELSEWHERE' ? '登录状态已在其他窗口变更，请重新登录。'
  : session.state.reason === 'AUTH_EXPIRED' ? '登录已失效，请重新登录。' : '')

const originalParts = computed(() => {
  const original = state.original
  if (!original) return { before: '', quote: '', after: '' }
  const value = original.contextText
  const start = original.quoteStartInclusive - original.contextStartInclusive
  const end = original.quoteEndExclusive - original.contextStartInclusive
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || end <= start || end > value.length) {
    return { before: value, quote: '', after: '' }
  }
  return { before: value.slice(0, start), quote: value.slice(start, end), after: value.slice(end) }
})

const originalLink = computed(() => {
  const value = state.original?.safeSourceUrl
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !url.username && !url.password && (!url.port || url.port === '443') ? value : null
  } catch {
    return null
  }
})

const issueLabels = {
  DATA_GAP: '资料缺口',
  CLARIFICATION: '需要补充条件',
  PROCESSING_FAILURE: '处理未完成',
  SCOPE_LIMIT: '适用范围待确认',
  CONFLICT: '依据存在冲突',
  REPLAY_MISS: '回放未覆盖'
}
const sampleQuestions = computed(() => modeError.value ? [] : simulated.value
  ? [hostInfo.value?.simulatedQuestion || '我这门选修课没过，能不能重修？']
  : ['公共选修课有什么学分要求？', '课程没通过后怎么办？'])

const relatedEvidence = item => result.value.evidence.filter(evidence =>
  result.value.evidenceRelations.some(relation => relation.subquestionId === item.id && relation.evidenceId === evidence.id))

// 「本次使用的资料」与「证据详情」都按用途关系列出，但用途必须如实展示：
// CLAIM 支撑结论、SCOPE 限定范围、CONFLICT 表示冲突、CLARIFICATION 说明澄清依据。
const roleLabels = { CLAIM: '支持本条结论', CLARIFICATION: '用于澄清条件', SCOPE: '限定适用范围', CONFLICT: '与其他依据冲突' }
const evidenceRows = computed(() => {
  if (!result.value) return []
  const roles = new Map()
  for (const relation of result.value.evidenceRelations) {
    if (!roles.has(relation.evidenceId)) roles.set(relation.evidenceId, new Set())
    roles.get(relation.evidenceId).add(relation.role)
  }
  return result.value.evidence.map((evidence, index) => ({
    evidence,
    index: index + 1,
    roles: [...(roles.get(evidence.id) || [])]
  }))
})
// 选中某条依据时，证据详情只展示它；行内引用点击即定位到这里。
const selectedEvidenceId = ref('')
const selectedEvidence = computed(() =>
  evidenceRows.value.find(row => row.evidence.id === selectedEvidenceId.value) || evidenceRows.value[0] || null)
// 「查看具体解释」没有独立 explanation 字段，只能用已发布的真实内容：
// claim.when（适用条件）与 issue 的种类（适用边界）。两者都没有时明确说明没有额外解释。
// 限制说明的正文已经在上方「需要注意」里完整出现过，这里只汇总种类与条数，不复制一遍。
const explanationLines = computed(() => {
  if (!result.value) return []
  const lines = []
  const boundaries = []
  for (const item of result.value.items) {
    for (const claim of item.claims) {
      if (Object.keys(claim.when).length) {
        lines.push({ kind: 'condition', label: '适用条件', text: conditionSummary(claim.when) })
      }
    }
    for (const issue of item.issues) boundaries.push(issueLabels[issue.kind] || '限制')
  }
  if (boundaries.length) {
    lines.push({ kind: 'boundary', label: '适用边界', text: [...new Set(boundaries)].join('、') + '（详见上方限制与提醒）' })
  }
  return lines
})

function conditionName(key) {
  if (key === 'courseType') return '课程类型'
  if (key === 'grade') return '年级'
  if (key === 'semester') return '学期'
  return '适用条件'
}
function conditionSummary(conditions) {
  return Object.entries(conditions || {})
    .map(([key, value]) => conditionName(key) + '：' + ruleCheckConditionLabel(key, value))
    .join('；')
}

watch(() => state.snapshot, () => {
  choice.value = ''
  // 新一轮结果可能不再包含上一轮选中的依据，必须清掉，否则引用定位会指向已消失的证据。
  selectedEvidenceId.value = ''
})
watch(() => session.state.epoch, () => {
  hostInfo.value = null
  modeError.value = ''
  question.value = ''
  username.value = ''
  password.value = ''
  choice.value = ''
  loginError.value = ''
  originalOpen.value = false
}, { flush: 'sync' })

onMounted(async () => {
  if (runtime || session.state.identityBusy) {
    authChecking.value = false
    return
  }
  await checkLogin()
  if (alive) await flow.restoreLatest()
})
onUnmounted(() => {
  alive = false
  password.value = ''
  flow.dispose()
})

async function login() {
  if (loginBusy.value || session.state.identityBusy || session.state.reason === 'LOGIN_UNCONFIRMED') return
  loginBusy.value = true
  loginError.value = ''
  const pending = api.login(username.value.trim(), password.value, rememberMe.value)
  const epoch = session.state.epoch
  session.state.reason = 'LOGIN_UNCONFIRMED'
  try {
    await pending
    if (session.state.epoch === epoch) session.state.reason = ''
    await loadRuntimeInfo()
    if (alive) await flow.restoreLatest()
  } catch (error) {
    if (error.code !== 'STALE_RESPONSE') {
      const unknown = error.code === 'NETWORK_UNAVAILABLE' || error.code === 'BAD_RESPONSE'
      if (!unknown && session.state.epoch === epoch) session.state.reason = ''
      if (alive && !unknown) loginError.value = error.status === 401
        ? '登录未成功，请检查账号和密码。'
        : '登录暂不可用，请稍后再试。'
    }
  } finally {
    password.value = ''
    if (alive) loginBusy.value = false
  }
}
async function checkLogin() {
  if (session.state.identityBusy) return
  authChecking.value = true
  loginError.value = ''
  try {
    await api.me()
    if (alive) session.state.reason = ''
    await loadRuntimeInfo()
  } catch (error) {
    if (alive && error.code !== 'STALE_RESPONSE' && error.status !== 401) {
      loginError.value = '暂时无法核对登录状态，请稍后重试。'
    }
  } finally {
    if (alive) authChecking.value = false
  }
}
async function loadRuntimeInfo() {
  if (!alive || !student.value || !api.runtimeInfo) return
  const epoch = session.state.epoch
  try {
    const info = await api.runtimeInfo()
    if (alive && epoch === session.state.epoch && student.value) {
      hostInfo.value = info
      modeError.value = ''
    }
  } catch {
    // 模式读取失败不能被呈现成在线；并且换账号后迟到的响应不得覆盖新会话。
    if (alive && epoch === session.state.epoch && student.value) {
      hostInfo.value = null
      modeError.value = '暂时无法确认当前服务的执行模式，请稍后刷新或重试。'
    }
  }
}
async function logout() {
  loginError.value = ''
  flow.reset()
  originalOpen.value = false
  try {
    await api.logout()
  } catch (error) {
    if (alive && error.code !== 'STALE_RESPONSE') loginError.value = '退出尚未确认，请重试退出。'
  }
}
async function submitQuestion() {
  if (composerDisabled.value) return
  const value = question.value.trim()
  if (!value) return
  const pending = flow.start(value)
  question.value = ''
  originalOpen.value = false
  await pending
}
function useExample(value) {
  if (!composerDisabled.value) question.value = value
}
function newQuestion() {
  if (active.value || state.pending || state.busy) return
  flow.reset()
  question.value = ''
  choice.value = ''
  originalOpen.value = false
}
async function showOriginal(token) {
  originalOpen.value = true
  await flow.openOriginal(token)
}
// 行内引用：先选中该条依据，让「证据详情」显示对应内容，再按需打开原文。
// 选中状态只影响展示，不改变任何业务状态，也不会自动发起原文请求（原文由用户显式点击触发）。
const evidenceFocusToken = ref(0)
function focusEvidence(evidenceId) {
  selectedEvidenceId.value = evidenceId
  // 计数器让子组件每次都响应“用户点了引用”，即使连续点同一条也能重新展开。
  evidenceFocusToken.value += 1
}
function closeOriginal() {
  originalOpen.value = false
}

const appEl = ref(null)
// 移动端消息区可能比整块澄清面板矮：进入澄清时把面板（含“确认并继续”）滚进可见范围。
// 只在操作区确实被裁掉时才动滚动位置，避免覆盖用户自己的滚动，也不改变 DOM 顺序与业务状态。
async function revealClarification() {
  if (typeof window === 'undefined') return
  await nextTick()
  const scroller = appEl.value?.querySelector('.conversation-scroll')
  const panel = appEl.value?.querySelector('.clarification-form')
  if (!scroller || !panel) return
  const box = scroller.getBoundingClientRect()
  const rect = panel.getBoundingClientRect()
  const margin = 8
  if (rect.top >= box.top - 1 && rect.bottom <= box.bottom + 1) return
  // 只补足“操作区被裁掉”的那一段：滚太多会让问题标题和状态行一起离开视野。
  const needed = rect.bottom - box.bottom + margin
  const maxScroll = rect.top - box.top - margin
  scroller.scrollTop += Math.max(0, Math.min(needed, maxScroll))
}
watch(
  () => waitingForClarification.value && !!nextCondition.value,
  active => { if (active) revealClarification() },
  { immediate: true, flush: 'post' }
)
</script>

<template>
  <main ref="appEl" class="rule-app" :class="{ 'rule-app--conversation': hasConversation && student }">
    <RuleHeader :actor="session.state.actor" :identity-busy="session.state.identityBusy" :can-ask-new="hasConversation && student" :new-question-disabled="authChecking || state.busy || !!state.pending || active" @logout="logout" @new-question="newQuestion" />

    <p v-if="student && state.error && !hasConversation" class="mode-notice" role="alert">{{ state.error }}</p>
    <p v-if="student && modeError" class="mode-notice" role="status">{{ modeError }}</p>

    <details v-if="student && state.recent?.length" class="mode-notice" data-testid="recent-tasks">
      <summary>本标签页最近查证（最多 5 条）</summary>
      <p>仅恢复本标签页约 15 分钟内的任务；退出或换账号会清除记录，不支持服务器重启或跨设备历史。</p>
      <button v-for="(entry,index) in state.recent" :key="entry.taskId" type="button" class="text-button" :disabled="state.busy || !!state.pending || active" @click="flow.resume(entry.taskId)">恢复查证 {{ index + 1 }}</button>
    </details>

    <p v-if="httpFixture" class="mode-notice" role="note" data-testid="http-fixture-notice">
      本机 HTTP FIXTURE 验收：身份、规则和答案均为合成测试数据，模型调用为零；不代表真实在线问答能力。
    </p>
    <p v-if="simulated" class="mode-notice" role="note">
      工程模拟：仅支持下方固定问题，不调用在线模型。示例：我这门选修课没过，能不能重修？可补充公共选修课或专业选修课；仅验证历史资料的查证流程。
    </p>

    <RuleLogin v-if="!session.state.actor" v-model:username="username" v-model:password="password" v-model:remember-me="rememberMe" :auth-checking="authChecking" :login-busy="loginBusy" :identity-busy="session.state.identityBusy" :reason="session.state.reason" :login-error="loginError" :identity-message="identityMessage" @login="login" @logout="logout" @check-login="checkLogin" />

    <section v-else-if="!student" class="access-notice" role="status">
      当前账号不是学生查证身份，请退出后使用学生查证身份登录。
    </section>

    <RuleWelcome v-else-if="!hasConversation" v-model:question="question" :composer-disabled="composerDisabled" :busy="state.busy" :sample-questions="sampleQuestions" @submit="submitQuestion" @example="useExample" />

    <section v-else class="conversation-layout" :class="{ 'conversation-layout--with-original': originalOpen }" aria-label="规则查证对话">
      <div class="conversation-main">
        <!-- 只有对话内容进入滚动区，底部输入区留在滚动区之外，长回答才不会把输入框顶走。 -->
        <div class="conversation-scroll">
          <div class="user-turn">
            <p class="turn-label">你</p>
            <p class="user-question">{{ state.question || '校园规则问题' }}</p>
          </div>

          <RuleAnswer v-model:choice="choice" :state="state" :result="result" :active="active" :identity-busy="session.state.identityBusy" :answer-kind="answerKind" :answer-status="answerStatus" :answer-title="answerTitle" :lead-claim="usesConclusionTitle ? leadClaim : null" :lead-claim-id="usesConclusionTitle ? leadClaim.claim.id : ''" :claim-citations="claimCitations" :explanation-lines="explanationLines" :evidence-rows="evidenceRows" :selected-evidence="selectedEvidence" :role-labels="roleLabels" :terminal="terminal" :has-processing-failure="hasProcessingFailure" :issue-labels="issueLabels" :condition-summary="conditionSummary" :waiting-for-clarification="waitingForClarification" :next-condition="nextCondition" :related-evidence="relatedEvidence" :evidence-focus-token="evidenceFocusToken" @cancel="flow.cancel" @recover="flow.recover" @refresh="flow.refresh" @new-question="newQuestion" @clarify="flow.clarify" @show-original="showOriginal" @focus-evidence="focusEvidence" />
        </div>

        <RuleComposer v-model:question="question" :disabled="composerDisabled" :waiting-for-clarification="waitingForClarification" :terminal="terminal" @submit="submitQuestion" />
      </div>

      <RuleOriginal v-if="originalOpen" :state="state" :original-parts="originalParts" :original-link="originalLink" @close="closeOriginal" />
    </section>

    <footer class="app-footer">ZhkuAgent 是校园规则查证辅助工具，不替代学校部门的正式认定或办理。</footer>
  </main>
</template>

<style src="../styles/rule-check.css"></style>
