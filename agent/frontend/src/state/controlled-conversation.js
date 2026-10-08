import { reactive } from 'vue'
import { ControlledError, isResourceId, errorMessage } from '../api/controlled.js'

export function conversationSnapshot(value, expectedId = value?.sessionId) {
  if (!value || !isResourceId(value.sessionId) || value.sessionId !== expectedId
    || !['ACTIVE', 'CANCELLED'].includes(value.status) || !Number.isSafeInteger(value.revision) || value.revision < 0
    || typeof value.expiresAt !== 'string' || !Number.isFinite(Date.parse(value.expiresAt))) throw new ControlledError('BAD_RESPONSE')
  const pending = value.pendingIntent === 'ELECTIVE_RETAKE_AFTER_FAILURE' && value.pendingField === 'COURSE_TYPE'
  const empty = value.pendingIntent === null && value.pendingField === null
  if (!pending && !empty || value.status === 'CANCELLED' && pending) throw new ControlledError('BAD_RESPONSE')
  // GET只恢复服务端六项控制字段，不能把扩展字段或本地旧问答当成持久历史。
  return { sessionId: value.sessionId, status: value.status, pendingIntent: value.pendingIntent,
    pendingField: value.pendingField, expiresAt: value.expiresAt, revision: value.revision }
}
export function conversationReply(value) {
  if (!value || !['ANSWER', 'NEED_CLARIFICATION', 'EVIDENCE_UNAVAILABLE', 'OUT_OF_SCOPE',
    'SESSION_EXPIRED', 'SESSION_CANCELLED', 'SESSION_INVALID'].includes(value.type) || typeof value.message !== 'string') throw new ControlledError('BAD_RESPONSE')
  let answer = null
  if (value.type === 'ANSWER') {
    if (value.handoffRequired === true || !value.answer || ['conclusion', 'basis', 'sourceTitle', 'sourceVersion', 'sourceLocator', 'applicableScope']
      .some(key => typeof value.answer[key] !== 'string')) throw new ControlledError('BAD_RESPONSE')
    answer = Object.fromEntries(['conclusion', 'basis', 'sourceId', 'sourceTitle', 'sourceVersion', 'sourceLocator', 'applicableScope']
      .map(key => [key, value.answer[key] ?? null]))
  }
  return { type: value.type, message: value.message, answer, handoffRequired: value.handoffRequired === true }
}

const initial = () => ({ id: null, snapshot: null, reply: null, question: '', draft: '', phase: 'idle', busy: false, issue: '', confirmationRequired: false })

// 页面请求协调器仅管前端读取/展示；许可、TTL、revision写入与重修规则继续由knowledge决定。
export function createConversationPage({ api }) {
  const state = reactive(initial())
  let generation = 0, controller = new AbortController()
  function reset(id = null) {
    generation++; controller.abort(); controller = new AbortController()
    Object.assign(state, initial(), { id })
  }
  function current(ticket) { return ticket === generation && !controller.signal.aborted }
  function failure(error, operation) {
    state.reply = null; state.question = ''; state.snapshot = null; state.draft = ''
    state.issue = errorMessage(error)
    if (error.status === 401) { reset(); return }
    if (error.status === 410 || error.code === 'SESSION_EXPIRED') state.phase = 'expired'
    else if (error.code === 'SESSION_CANCELLED') state.phase = 'cancelled'
    else if (error.status === 409) { state.phase = 'conflict'; state.confirmationRequired = true }
    else if (operation === 'create' && (error.conversationUnknown || error.code === 'BAD_RESPONSE')) state.phase = 'createUnknown'
    else if (operation === 'write') { state.phase = 'writeUnknown'; state.confirmationRequired = true }
    else state.phase = 'readError'
  }
  async function load(id) {
    // 中间GET失败不能消除写入确认门禁；换ID/离页才丢弃这一页面内标记。
    const confirm = state.id === id && state.confirmationRequired
    reset(id)
    state.confirmationRequired = confirm
    if (!id) return
    if (!isResourceId(id)) { state.phase = 'readError'; state.issue = '无法打开这次咨询，请开始新的咨询。'; return }
    const ticket = generation; state.busy = true; state.phase = 'loading'
    try {
      const value = await api.findConversation(id, { signal: controller.signal })
      if (!current(ticket)) return
      state.snapshot = conversationSnapshot(value, id)
      state.phase = state.snapshot.status === 'CANCELLED' ? 'cancelled' : confirm ? 'confirm' : 'ready'
    } catch (error) { if (current(ticket) && error.code !== 'STALE_RESPONSE') failure(error, 'read') }
    finally { if (current(ticket)) state.busy = false }
  }
  async function create(confirmUnknown = false) {
    if (state.busy || state.id || state.phase === 'createUnknown' && !confirmUnknown) return null
    const ticket = generation; state.busy = true; state.issue = ''; state.reply = null
    try {
      const value = await api.createConversation({ signal: controller.signal })
      if (!current(ticket)) return null
      const snapshot = conversationSnapshot(value)
      if (snapshot.status !== 'ACTIVE') throw new ControlledError('BAD_RESPONSE')
      state.id = snapshot.sessionId; state.snapshot = snapshot; state.phase = 'ready'
      return state.id
    } catch (error) { if (current(ticket) && error.code !== 'STALE_RESPONSE') failure(error, 'create'); return null }
    finally { if (current(ticket)) state.busy = false }
  }
  async function send() {
    if (state.busy || state.phase !== 'ready' || state.snapshot?.status !== 'ACTIVE') return
    const message = state.draft.trim()
    if (!message || message.length > 500) { state.issue = '请填写1至500字的问题或补答。'; return }
    const ticket = generation, id = state.id, revision = state.snapshot.revision
    state.busy = true; state.reply = null; state.question = ''; state.issue = ''; state.draft = ''
    try {
      const reply = conversationReply(await api.sendConversationMessage(id, message, revision, { signal: controller.signal }))
      if (!current(ticket)) return
      // 消息响应不携带revision；只有重新读取成功才允许下一次写入。读取失败也不展示暂存答复。
      const snapshot = conversationSnapshot(await api.findConversation(id, { signal: controller.signal }), id)
      if (!current(ticket)) return
      if (snapshot.revision < revision || reply.type === 'ANSWER' && snapshot.pendingField !== null
        || reply.type === 'NEED_CLARIFICATION' && snapshot.pendingField !== 'COURSE_TYPE') throw new ControlledError('BAD_RESPONSE')
      state.snapshot = snapshot
      if (snapshot.status === 'CANCELLED') { state.phase = 'cancelled'; return }
      state.reply = reply; state.question = message
      state.phase = reply.type === 'ANSWER' ? 'answered' : reply.type === 'SESSION_EXPIRED' ? 'expired'
        : reply.type === 'SESSION_CANCELLED' ? 'cancelled' : reply.type === 'SESSION_INVALID' ? 'readError' : 'ready'
    } catch (error) { if (current(ticket) && error.code !== 'STALE_RESPONSE') failure(error, 'write') }
    finally { if (current(ticket)) state.busy = false }
  }
  async function cancel() {
    if (state.busy || state.phase !== 'ready' || state.snapshot?.status !== 'ACTIVE') return
    const ticket = generation, id = state.id, revision = state.snapshot.revision
    state.busy = true; state.reply = null; state.question = ''; state.draft = ''; state.issue = ''
    try {
      const snapshot = conversationSnapshot(await api.cancelConversation(id, revision, { signal: controller.signal }), id)
      if (!current(ticket)) return
      if (snapshot.status !== 'CANCELLED') throw new ControlledError('BAD_RESPONSE')
      state.snapshot = snapshot; state.phase = 'cancelled'
    } catch (error) { if (current(ticket) && error.code !== 'STALE_RESPONSE') failure(error, 'write') }
    finally { if (current(ticket)) state.busy = false }
  }
  return { state, load, create, send, cancel, dispose: () => reset(),
    confirmLatest() { if (state.phase === 'confirm' && !state.busy && state.snapshot?.status === 'ACTIVE') { state.phase = 'ready'; state.issue = ''; state.confirmationRequired = false } } }
}
