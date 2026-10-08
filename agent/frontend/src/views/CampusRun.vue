<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CampusSource from '../components/CampusSource.vue'
import { controlledClient as api, errorMessage, isResourceId } from '../api/controlled.js'
import { controlledSession as session } from '../state/controlled-session.js'
import { controlledSubmissions as submissions, isSubmissionKey } from '../state/controlled-submission.js'
import { runFacts, submissionFacts, stateLabel, visibilityMessage } from '../state/controlled-results.js'
const route = useRoute(), router = useRouter()
const question = ref(''), courses = ref([{ courseId: '', category: '公共选修课', credits: '' }])
const facts = ref(null), error = ref(''), notice = ref(''), busy = ref(false), unknown = ref(false), timedOut = ref(false), pending = ref(null)
const id = computed(() => facts.value?.runId || pending.value?.runId || (isResourceId(route.query.runId) ? route.query.runId : null))
const user = computed(() => session.state.actor?.role === 'USER')
const key = computed(() => pending.value?.submissionKey || (isSubmissionKey(route.query.submissionKey) ? route.query.submissionKey : null))
const active = computed(() => !!id.value || !!key.value || unknown.value)
const canCancel = computed(() => !!id.value && facts.value?.location !== 'ARCHIVED' && facts.value?.location !== 'UNCONFIRMED' && facts.value?.terminated === false)
let controller = new AbortController(), timer, version = 0, deadline = 0
function resetReader() { clearTimeout(timer); controller.abort(); controller = new AbortController(); version++ }
onUnmounted(resetReader)
function fill() {
  if (active.value || busy.value) return
  facts.value = null
  question.value = '这两门虚构公共选修课学分合计以及每学期上限是多少？'
  courses.value = [{ courseId: 'ART01', category: '公共选修课', credits: 1.5 }, { courseId: 'HIS02', category: '公共选修课', credits: 2 }]
}
async function sendOriginal() {
  if (busy.value || !user.value || !pending.value?.requestSnapshot) return
  resetReader(); const expected = version, original = pending.value
  busy.value = true; facts.value = null; error.value = ''; notice.value = ''
  try {
    const reply = await api.startRun(original.requestSnapshot, { submissionKey: original.submissionKey, signal: controller.signal })
    if (expected !== version) return
    pending.value = submissions.confirm(reply); facts.value = submissionFacts(reply); unknown.value = false
    notice.value = reply.acceptedReadUnavailable ? '任务已受理，结果暂不可读，请确认本次提交。' : reply.replayed ? '已找到本次提交的原任务。' : '任务已受理。'
    await router.replace({ path: '/campus/run', query: { submissionKey: original.submissionKey, runId: reply.runId } })
    if (expected === version) query()
  } catch (failure) {
    if (expected === version && failure.code !== 'STALE_RESPONSE') {
      error.value = errorMessage(failure); unknown.value = failure.submissionUnknown === true
      if (unknown.value) facts.value = submissionFacts({ location: 'UNCONFIRMED', runId: original.runId })
      // 明确拒绝也保留同次请求；用户可手动再发，不能由网络层暗中重试。
      notice.value = failure.code === 'SUBMISSION_CONTENT_CONFLICT'
        ? '请先查看原任务。修改内容时请明确新建另一任务。'
        : '先确认本次提交；重新发送只使用本页保留的原课程。新建会产生新的提交。'
    }
  } finally { if (expected === version) busy.value = false }
}
function submit() {
  if (busy.value || active.value || !user.value) return
  const values = courses.value.map(c => ({ courseId: c.courseId.trim(), category: c.category.trim(), credits: Number(c.credits) }))
  if (values.some((c, i) => !c.courseId || !c.category || courses.value[i].credits === '' || !Number.isFinite(c.credits) || c.credits < 0)) { error.value = '请填写有效课程编号、类别和非负学分。'; return }
  try { pending.value = submissions.begin(session.state.actor.userId, { question: question.value.trim(), courses: values }) }
  catch { error.value = '当前浏览器无法安全创建提交，请更新浏览器后再试。'; return }
  sendOriginal()
}
async function read(expected = version) {
  if ((!key.value && !id.value) || !user.value) return
  const submissionKey = key.value, runId = id.value
  facts.value = null; error.value = ''
  try {
    const reply = submissionKey ? await api.findSubmission(submissionKey, { signal: controller.signal }) : await api.findRun(runId, { signal: controller.signal })
    if (expected !== version) return
    facts.value = submissionKey ? submissionFacts(reply) : { ...runFacts(reply), location: 'LIVE' }
    if (submissionKey && pending.value) pending.value = submissions.confirm(reply)
    unknown.value = false
    if (facts.value.location === 'LIVE' && facts.value.terminated === false) {
      if (Date.now() >= deadline) { timedOut.value = true; notice.value = '暂时停止自动查询，点击“继续查询”查看进度。'; return }
      timer = setTimeout(() => read(expected), 1000)
    }
  } catch (failure) { if (expected === version && failure.code !== 'STALE_RESPONSE') error.value = errorMessage(failure) }
}
function query() { resetReader(); busy.value = false; timedOut.value = false; deadline = Date.now() + 60000; read(version) }
watch(() => [route.query.runId, route.query.submissionKey, session.state.actor?.userId, session.state.epoch], () => {
  resetReader(); facts.value = null; busy.value = false; error.value = ''; notice.value = ''
  if (!user.value) { pending.value = null; question.value = ''; courses.value = [{ courseId: '', category: '公共选修课', credits: '' }]; return }
  pending.value = isResourceId(route.query.runId) && !isSubmissionKey(route.query.submissionKey) ? null
    : submissions.restore(session.state.actor.userId, isSubmissionKey(route.query.submissionKey) ? route.query.submissionKey : undefined)
  if (key.value || id.value) query()
}, { immediate: true, flush: 'sync' })
async function cancel() {
  if (!canCancel.value || busy.value || !user.value) return
  resetReader(); const expected = version, runId = id.value
  busy.value = true; facts.value = null
  try {
    const reply = await api.cancelRun(runId, { signal: controller.signal })
    if (expected !== version) return
    facts.value = { ...runFacts(reply), location: 'LIVE' }; notice.value = facts.value.terminated ? '取消操作已处理，请查看下方结果。' : '正在停止，请稍候；页面会继续更新。'
    if (!facts.value.terminated) query()
  } catch (failure) { if (expected === version && failure.code !== 'STALE_RESPONSE') error.value = errorMessage(failure) }
  finally { if (expected === version) busy.value = false }
}
async function fresh() {
  // 新建是明确的用户选择，不把确认失败/404当成原任务未受理的证明。
  resetReader(); if (session.state.actor) submissions.forget(session.state.actor.userId)
  pending.value = null; facts.value = null; busy.value = false; unknown.value = false; error.value = ''; notice.value = ''
  question.value = ''; courses.value = [{ courseId: '', category: '公共选修课', credits: '' }]
  await router.replace('/campus/run')
}
</script>
<template>
  <header class="page-heading"><h1>学分任务</h1><p>填写课程，查看有出处的学分结果。</p></header>
  <div class="task-columns"><section class="input-area"><div class="section-heading"><h2>{{ active && !question ? '当前任务' : '课程信息' }}</h2>
    <button v-if="!active" class="text-button" type="button" @click="fill" :disabled="busy || active || !user">使用示例课程</button></div>
    <p v-if="active && !question" class="empty-state">{{ facts?.state || facts?.archiveStatus === 'FINALIZED' ? '已找到本次任务记录。刷新不会恢复原课程。' : '正在确认这次提交。刷新不会恢复原课程，暂未找到记录也不代表从未受理。' }}</p>
    <form v-else @submit.prevent="submit"><fieldset :disabled="busy || active || !user"><label for="question">你想核对的问题</label>
      <textarea id="question" v-model="question" rows="3" required maxlength="500" />
      <div class="course-heading"><h3>本次课程</h3></div><div class="course-table-wrap"><table class="course-table"><thead><tr><th>课程编号</th><th>类别</th><th>学分</th><th><span class="sr-only">操作</span></th></tr></thead>
        <tbody><tr v-for="(course, index) in courses" :key="index"><td><input v-model="course.courseId" :aria-label="`课程${index + 1}编号`" required /></td>
          <td><input v-model="course.category" :aria-label="`课程${index + 1}类别`" required /></td><td><input v-model="course.credits" type="number" min="0" step="any" :aria-label="`课程${index + 1}学分`" required /></td>
          <td><button class="remove-button" type="button" :disabled="courses.length === 1" :aria-label="`删除课程${index + 1}`" @click="courses.splice(index, 1)">×</button></td></tr></tbody></table></div>
      <button class="add-course" type="button" :disabled="courses.length >= 20" @click="courses.push({ courseId: '', category: '公共选修课', credits: '' })">＋ 添加课程</button>
      <div class="submit-area"><button class="button primary" type="submit">{{ busy ? '提交中…' : '提交学分任务' }}</button></div>
    </fieldset></form>
    <p class="form-feedback" role="status" aria-live="polite">{{ error }}</p><p class="muted" role="status">{{ notice }}</p>
    <div v-if="active" class="run-status"><strong>{{ stateLabel(facts) }}</strong></div>
    <div class="inline-actions"><button v-if="active" type="button" class="button secondary" :disabled="busy || !user" @click="query">{{ timedOut ? '继续查询' : key ? '确认本次提交' : '刷新进度' }}</button>
      <button v-if="key && pending?.requestSnapshot" type="button" class="button secondary" :disabled="busy || !user" @click="sendOriginal">重新发送本次提交</button>
      <button v-if="canCancel" type="button" class="button secondary" :disabled="busy || !user" @click="cancel">取消任务</button>
      <RouterLink v-if="active" class="text-button" :to="{ path: '/campus/history', query: id ? { runId: id } : {} }">查看我的历史</RouterLink>
      <button v-if="active" type="button" class="text-button" @click="fresh">新建另一任务</button></div>
  </section><section class="result-area"><div class="section-heading"><h2>结果与出处</h2></div>
    <p v-if="facts?.answer" class="answer-text">{{ facts.answer }}</p><p v-else class="empty-state">{{ visibilityMessage(facts) }}</p>
    <CampusSource :source="facts?.source" /><p v-if="facts?.answer" class="verification-note">计算与政策说明不能确认个人选课资格或办理结果，请按适用范围向教务部门核实。</p>
  </section></div>
</template>
