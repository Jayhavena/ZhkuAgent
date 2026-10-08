<script setup>
import { computed, ref, watch, nextTick, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CampusSource from '../components/CampusSource.vue'
import { controlledClient as api } from '../api/controlled.js'
import { controlledSession as session } from '../state/controlled-session.js'
import { createConversationPage } from '../state/controlled-conversation.js'

const route = useRoute(), router = useRouter(), model = createConversationPage({ api }), state = model.state
const input = ref(null), feedback = ref(null), newButton = ref(null)
const user = computed(() => session.state.actor?.role === 'USER')
const canWrite = computed(() => user.value && state.phase === 'ready' && state.snapshot?.status === 'ACTIVE')
const pending = computed(() => state.snapshot?.pendingField === 'COURSE_TYPE')
const status = computed(() => {
  if (state.busy) return state.phase === 'loading' ? '正在加载咨询…' : '正在处理，请稍候…'
  return {
    idle: '开始一次重修咨询，按提示补充课程类型。', ready: pending.value ? '请补充：公共选修课，还是专业选修课？' : '可以填写新的重修问题。',
    answered: '本次答复已完成。', conflict: '咨询有新的变化。请先刷新，再确认是否继续。',
    confirm: '咨询已刷新。确认后请重新填写问题或补充内容。',
    expired: '这次咨询已过期，请开始新的咨询。', cancelled: '这次咨询已取消，可以开始新的咨询。',
    createUnknown: '暂时无法确认是否创建成功。再次创建可能产生重复咨询，请谨慎操作。',
    writeUnknown: '暂时无法确认是否提交成功。请先刷新咨询，不要重复提交。',
    readError: '暂时无法查看这次咨询，可以刷新或开始新的咨询。'
  }[state.phase] || '正在加载咨询。'
})
function time(value) { return value ? new Date(value).toLocaleString('zh-CN') : '尚未取得' }
watch(() => route.query.sessionId, id => {
  if (user.value) model.load(id == null ? null : typeof id === 'string' ? id : 'invalid')
  else model.dispose()
}, { immediate: true })
onUnmounted(() => model.dispose())
async function focusNext() {
  await nextTick()
  if (canWrite.value && !state.busy) input.value?.focus()
  else if (['answered', 'expired', 'cancelled'].includes(state.phase)) newButton.value?.focus()
  else feedback.value?.focus()
}
async function create(confirmUnknown = false) {
  if (!user.value) return
  const task = model.create(confirmUnknown)
  await nextTick(); feedback.value?.focus()
  const id = await task
  if (id) await router.replace({ path: '/campus/conversation', query: { sessionId: id } })
  await nextTick()
  // 路由watch负责最终GET；若还在读取，不抢到禁用输入，完成后用户可继续键盘操作。
  if (!state.busy) await focusNext()
}
async function send() {
  if (!user.value) return
  const task = model.send(); await nextTick(); feedback.value?.focus()
  await task; await focusNext()
}
async function cancel() {
  if (!user.value) return
  const task = model.cancel(); await nextTick(); feedback.value?.focus()
  await task; await focusNext()
}
async function read() { if (user.value) { await model.load(state.id); await focusNext() } }
async function confirm() { model.confirmLatest(); await focusNext() }
async function fresh() { model.dispose(); await router.replace('/campus/conversation'); await nextTick(); newButton.value?.focus() }
</script>

<template>
  <header class="page-heading"><h1>重修咨询</h1><p>说明课程类型，查看适用的重修规则与出处。</p></header>
  <div class="conversation-columns"><section class="conversation-main">
    <div ref="feedback" tabindex="-1" class="conversation-status" role="status" aria-live="polite"><p>{{ status }}</p>
      <p v-if="state.issue" class="form-feedback">{{ state.issue }}</p></div>
    <div v-if="state.question" class="question-text"><strong>本次问题</strong><p>{{ state.question }}</p></div>
    <section v-if="state.reply" class="conversation-reply" aria-label="本轮回复">
      <template v-if="state.reply.answer"><h2>答复</h2><p class="answer-text">{{ state.reply.answer.conclusion }}</p>
        <p class="muted">{{ state.reply.answer.basis }}</p><CampusSource :source="state.reply.answer" />
        <p class="verification-note">请核对适用范围；可申请不代表一定获批或开课，个人办理结果应向教务部门确认。</p></template>
      <p v-else class="answer-text">{{ state.reply.message }}</p>
    </section>
    <form v-if="state.id && state.phase !== 'answered' && !['cancelled', 'expired'].includes(state.phase)" @submit.prevent="send">
      <label for="conversation-input">{{ pending ? '补充课程类型' : '你的重修问题' }}</label>
      <textarea id="conversation-input" ref="input" v-model="state.draft" rows="3" required maxlength="500" :disabled="!canWrite || state.busy"
        :placeholder="pending ? '例如：专业选修课' : '例如：选修课挂科了可以重修吗？'" />
      <div class="inline-actions"><button type="submit" class="button primary" :disabled="!canWrite || state.busy">{{ pending ? '提交补答' : '提交问题' }}</button>
        <button type="button" class="text-button" :disabled="!canWrite || state.busy" @click="cancel">取消咨询</button></div>
    </form>
    <div class="inline-actions">
      <button v-if="state.id" type="button" class="button secondary" :disabled="state.busy || !user" @click="read">刷新咨询</button>
      <button v-if="state.phase === 'confirm'" type="button" class="button primary" :disabled="state.busy || !user" @click="confirm">确认继续</button>
      <button v-if="!state.id" ref="newButton" type="button" class="button primary" :disabled="state.busy || !user" @click="create(state.phase === 'createUnknown')">
        {{ state.phase === 'createUnknown' ? '仍要创建新的咨询' : '开始咨询' }}</button>
      <button v-else ref="newButton" type="button" class="text-button" :disabled="state.busy || !user" @click="fresh">开始新的咨询</button>
    </div>
  </section><aside class="conversation-aside" aria-label="咨询提示">
    <h2>咨询提示</h2><p class="muted">离开页面后，可在有效期内通过原链接继续补充信息。这里只保留待补充的信息，不保存完整聊天记录。</p>
    <dl v-if="state.snapshot" class="metadata"><dt>需要补充</dt><dd>{{ pending ? '课程类型' : '暂无' }}</dd>
      <dt>有效期至</dt><dd>{{ time(state.snapshot.expiresAt) }}</dd></dl>
  </aside></div>
</template>
