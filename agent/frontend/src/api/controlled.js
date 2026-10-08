import { controlledSession } from '../state/controlled-session.js'
import { controlledSubmissions, isSubmissionKey } from '../state/controlled-submission.js'

export const PREFIX = '/api/zhku/controlled-preview/v1'
export const isResourceId = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
export class ControlledError extends Error {
  constructor(code, status = 0, extra = {}) { super(code); this.code = code; this.status = status; Object.assign(this, extra) }
}

export function createControlledClient({ session = controlledSession, submissions = controlledSubmissions, fetchImpl = (...args) => fetch(...args), timeoutMs = 10000 } = {}) {
  function resource(value) { if (!isResourceId(value)) throw new ControlledError('INVALID_RESOURCE_ID'); return value }
  function current(epoch) { if (epoch !== session.state.epoch) throw new ControlledError('STALE_RESPONSE') }
  async function request(method, path, body, { form = false, token = session.csrf, signal, submissionKey } = {}) {
    const scope = session.acquire()
    const timer = setTimeout(() => scope.controller.abort(), timeoutMs)
    const abort = () => scope.controller.abort()
    signal?.addEventListener('abort', abort, { once: true })
    if (signal?.aborted) abort()
    try {
      const headers = { Accept: 'application/json' }
      if (submissionKey) headers['Idempotency-Key'] = submissionKey
      if (method !== 'GET') {
        if (!token?.headerName || !token?.token) throw new ControlledError('CSRF_UNAVAILABLE')
        headers[token.headerName] = token.token
        headers['Content-Type'] = form ? 'application/x-www-form-urlencoded;charset=UTF-8' : 'application/json'
      }
      const response = await fetchImpl(PREFIX + path, { method, credentials: 'same-origin', cache: 'no-store', headers,
        signal: scope.controller.signal, ...(body === undefined ? {} : { body: form ? body : JSON.stringify(body) }) })
      let data = null
      if (response.status !== 204) {
        try { data = await response.json() } catch { current(scope.epoch); throw new ControlledError('BAD_RESPONSE', response.status) }
      }
      // 某些传输在abort之后仍返回；在触碰状态或正文前再检查身份generation。
      current(scope.epoch)
      if (signal?.aborted) throw new ControlledError('STALE_RESPONSE')
      if (scope.controller.signal.aborted) throw new ControlledError('NETWORK_UNAVAILABLE')
      if (!response.ok) {
        const code = typeof data?.code === 'string' ? data.code : `HTTP_${response.status}`
        if (response.status === 401) session.clear('AUTH_EXPIRED')
        throw new ControlledError(code, response.status, {
          accepted: data?.accepted === true && isResourceId(data?.runId),
          runId: isResourceId(data?.runId) ? data.runId : null,
          acceptance: data?.acceptance,
          submissionKey: isSubmissionKey(data?.submissionKey) ? data.submissionKey : null
        })
      }
      if (response.status !== 204 && (!data || typeof data !== 'object' || Array.isArray(data))) throw new ControlledError('BAD_RESPONSE', response.status)
      return data
    } catch (error) {
      if (error instanceof ControlledError) throw error
      if (session.state.epoch !== scope.epoch || signal?.aborted) throw new ControlledError('STALE_RESPONSE')
      throw new ControlledError('NETWORK_UNAVAILABLE')
    } finally { clearTimeout(timer); signal?.removeEventListener('abort', abort); scope.release() }
  }
  async function csrf() {
    const epoch = session.state.epoch
    const value = await request('GET', '/auth/csrf')
    current(epoch)
    if (!value?.headerName || !value?.token) throw new ControlledError('BAD_RESPONSE')
    session.csrf = value
    return value
  }
  const ensureCsrf = () => session.csrf ? Promise.resolve(session.csrf) : csrf()
  async function write(path, body, options) {
    const epoch = session.state.epoch
    const token = await ensureCsrf()
    current(epoch)
    return request('POST', path, body, { ...options, token })
  }
  async function me() {
    const epoch = session.state.epoch
    const actor = await request('GET', '/auth/me')
    current(epoch)
    if (!isResourceId(actor?.userId) || !['USER', 'MAINTAINER'].includes(actor.role)) throw new ControlledError('BAD_RESPONSE')
    session.state.actor = actor; session.state.checked = true
    if (actor.role === 'USER') submissions.restore(actor.userId)
    else submissions.forget()
    return actor
  }
  async function conversationWrite(path, body, kind, options) {
    try {
      const value = await write(path, body, options)
      if (kind === 'create' && !isResourceId(value?.sessionId)) throw new ControlledError('BAD_RESPONSE')
      return value
    } catch (error) {
      // 创建丢失响应没有可查询ID；已知会话写入未知则先读取版本，不能自动重发。
      if (error.code === 'NETWORK_UNAVAILABLE' || error.code === 'BAD_RESPONSE' || error.status >= 500) error.conversationUnknown = kind
      throw error
    }
  }
  function revision(value) {
    if (!Number.isSafeInteger(value) || value < 0) throw new ControlledError('INVALID_REVISION')
    return value
  }
  function key(value) { if (!isSubmissionKey(value)) throw new ControlledError('INVALID_SUBMISSION_KEY'); return value }
  function submissionReply(reply, expectedKey) {
    if (reply?.submissionKey !== expectedKey || !isResourceId(reply.runId)
      || !['LIVE', 'ARCHIVED', 'UNCONFIRMED'].includes(reply.location)
      || !Number.isFinite(Date.parse(reply.acceptedAt)) || reply.expiresAt != null && !Number.isFinite(Date.parse(reply.expiresAt))
      || (reply.location === 'LIVE' ? !reply.live || reply.archived != null
        : reply.location === 'ARCHIVED' ? !reply.archived || reply.live != null
          : reply.live != null || reply.archived != null)
      || reply.live && reply.live.run?.runId !== reply.runId
      || reply.archived && reply.archived.runId !== reply.runId) throw new ControlledError('BAD_RESPONSE')
    return reply
  }
  return {
    csrf, me,
    async login(username, password) {
      if (session.state.identityBusy) throw new ControlledError('AUTH_TRANSITION')
      session.state.identityBusy = true
      try {
        session.clear()
        const epoch = session.state.epoch
        const token = await csrf()
        current(epoch)
        await request('POST', '/auth/login', new URLSearchParams({ username, password }).toString(), { form: true, token })
        current(epoch)
        session.csrf = null
        await csrf()
        return await me()
      } finally { session.state.identityBusy = false }
    },
    async logout() {
      // generation保护不了浏览器接收Set-Cookie的顺序；身份变更必须串行，拒绝重叠而非排队。
      if (session.state.identityBusy) throw new ControlledError('AUTH_TRANSITION')
      session.state.identityBusy = true
      try {
        const token = session.csrf
        submissions.forget()
        session.clear()
        const epoch = session.state.epoch
        try { await request('POST', '/auth/logout', undefined, { token: token || await csrf() }) }
        catch (error) { if (epoch === session.state.epoch) session.clear('LOGOUT_UNCONFIRMED'); throw error }
        if (epoch === session.state.epoch) session.clear()
      } finally { session.state.identityBusy = false }
    },
    async startRun(body, options = {}) {
      const submissionKey = key(options.submissionKey)
      try {
        const reply = await write('/runs', body, options)
        return submissionReply(reply, submissionKey)
      } catch (error) {
        if (error.accepted && error.runId && error.submissionKey === submissionKey) return {
          submissionKey, runId: error.runId, location: 'UNCONFIRMED', acceptedReadUnavailable: true,
          live: null, archived: null
        }
        // 响应丢失只留下原键，确认查询和重复POST都不能获得补派发许可。
        if (error.code === 'NETWORK_UNAVAILABLE' || error.code === 'BAD_RESPONSE'
          || error.status >= 500 && error.acceptance !== 'NOT_ACCEPTED') error.submissionUnknown = true
        throw error
      }
    },
    async findSubmission(submissionKey, options) {
      key(submissionKey)
      return submissionReply(await request('GET', `/submissions/${submissionKey}`, undefined, options), submissionKey)
    },
    findRun: (id, options) => request('GET', `/runs/${resource(id)}`, undefined, options),
    cancelRun: (id, options) => write(`/runs/${resource(id)}/cancel`, {}, options),
    history: (cursor = null, options) => request('GET', '/history?limit=20' + (cursor ? '&cursor=' + encodeURIComponent(cursor) : ''), undefined, options),
    historyDetail: (id, options) => request('GET', `/history/${resource(id)}`, undefined, options),
    createConversation: options => conversationWrite('/conversations', {}, 'create', options),
    findConversation: (id, options) => request('GET', `/conversations/${resource(id)}`, undefined, options),
    async sendConversationMessage(id, message, expectedRevision, options) {
      return conversationWrite(`/conversations/${resource(id)}/messages`, { message, expectedRevision: revision(expectedRevision) }, 'write', options)
    },
    async cancelConversation(id, expectedRevision, options) {
      return conversationWrite(`/conversations/${resource(id)}/cancel`, { expectedRevision: revision(expectedRevision) }, 'write', options)
    }
  }
}
export const controlledClient = createControlledClient()

export function errorMessage(error) {
  if (error.code === 'AUTH_TRANSITION') return '正在切换登录状态，请等待完成后再操作。'
  if (error.submissionUnknown) return '提交结果暂未确认，请先确认本次提交，不要新建重复任务。'
  if (error.code === 'SUBMISSION_CONTENT_CONFLICT') return '同一次提交的课程或问题与原提交不一致。请先查看原任务；修改内容时请明确新建任务。'
  if (error.status === 401) return '登录已失效，请重新登录。'
  if (error.status === 403) return '暂时无法进行此操作，请确认登录账号或刷新页面后再试。'
  if (error.status === 404) return '未找到这条记录，或当前账号无法查看。'
  if (error.code === 'USER_IN_FLIGHT_LIMIT') return '已有两个任务正在处理，请等待或先取消，再新建任务。'
  if (error.status === 429) return '请求过于频繁，请稍后再试。'
  if (error.code === 'BAD_RESPONSE') return '暂时无法读取结果，请稍后刷新。'
  if (error.code === 'NETWORK_UNAVAILABLE') return '连接中断，请稍后刷新。'
  return '暂时无法确认操作结果，请先刷新查看，不要重复提交。'
}
