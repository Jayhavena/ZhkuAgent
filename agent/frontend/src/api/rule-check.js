import { ruleCheckSession } from '../state/rule-check-session.js'
export const PREFIX = '/api/zhku/controlled-preview/v2'
export class RuleCheckError extends Error {
  constructor(code, status = 0) { super(code); this.code = code; this.status = status }
}
const id = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value)
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)
const unicode = value => {
  for (let i = 0; i < value.length; i++) {
    const unit = value.charCodeAt(i)
    if (unit >= 0xd800 && unit <= 0xdbff) {
      const next = value.charCodeAt(++i); if (!(next >= 0xdc00 && next <= 0xdfff)) return false
    } else if (unit >= 0xdc00 && unit <= 0xdfff) return false
  }
  return true
}
const text = (value, limit = 2048, blank = false) => typeof value === 'string' && value.length <= limit && (blank || value.trim()) && unicode(value)
const splits = (value, offset) => offset > 0 && offset < value.length && /[\ud800-\udbff]/.test(value[offset - 1]) && /[\udc00-\udfff]/.test(value[offset])
const list = (value, check, limit = 64) => Array.isArray(value) && value.length <= limit && value.every(check)
const claims = value => object(value) && Object.keys(value).length <= 16 && Object.entries(value).every(([key, value]) => text(key, 64) && text(value, 128, true))
const span = (start, end, value) => Number.isSafeInteger(start) && start >= 0 && Number.isSafeInteger(end) && end > start && end - start === value.length
const condition = value => object(value) && text(value.key, 64) && text(value.question) && list(value.allowedValues, v => text(v, 128), 32) && value.allowedValues.length > 0
const citation = value => object(value) && text(value.citationId, 128) && id(value.sourceId) && id(value.chunkId) && value.revision === '2025.9.2'
  && text(value.text, 4096) && span(value.startInclusive, value.endExclusive, value.text) && id(value.originalToken)
const fact = value => object(value) && text(value.id, 128) && text(value.text, 8192) && claims(value.when)
  && list(value.citations, citation, 32) && value.citations.length > 0
function reply(value) {
  if (!object(value) || value.referenceRevision !== '2025.9.2' || !text(value.knowledgeScope, 128) || value.conditionSource !== 'USER_DECLARED'
    || !claims(value.confirmedConditions) || !list(value.confirmedFacts, fact, 32) || !list(value.clarificationQuestions, condition, 16)
    || !list(value.unknownItems, v => text(v)) || !list(value.conflicts, c => object(c) && text(c.issue) && list(c.sides, fact, 32) && c.sides.length >= 2, 16)) return false
  const { confirmedFacts: facts, unknownItems: unknown, clarificationQuestions: questions, conflicts } = value
  if (value.status !== 'INSUFFICIENT_EVIDENCE' && (!object(value.scope) || !text(value.scope.ruleId, 128)
    || !text(value.scope.questionScope, 8192) || !list(value.scope.declaredConditions, condition, 16) || !object(value.scope.factPreconditions))) return false
  if (value.status === 'NEED_CLARIFICATION') {
    // 当前协议逐轮只问一个会影响判断的条件；不把多项歧义问题自动猜成声明。
    return questions.length === 1 && conflicts.length === 0 && id(value.clarificationToken)
  }
  if (value.clarificationToken != null || questions.length) return false
  switch (value.status) {
    case 'ANSWERED': return facts.length > 0 && !unknown.length && !conflicts.length
    case 'PARTIALLY_ANSWERED': return facts.length > 0 && unknown.length > 0 && !conflicts.length
    case 'INSUFFICIENT_EVIDENCE': return !facts.length && unknown.length > 0 && !conflicts.length && value.scope == null
    case 'CONFLICTING_EVIDENCE': return !facts.length && conflicts.length > 0
    default: return false
  }
}
function located(value) {
  if (!object(value) || !text(value.citationId, 128) || !id(value.sourceId) || !id(value.chunkId) || value.revision !== '2025.9.2'
    || !text(value.contextText, 8192) || !Number.isSafeInteger(value.contextStartInclusive) || value.contextStartInclusive < 0
    || !Number.isSafeInteger(value.quoteStartInclusive) || !Number.isSafeInteger(value.quoteEndExclusive)
    || value.quoteStartInclusive < value.contextStartInclusive || value.quoteEndExclusive <= value.quoteStartInclusive
    || value.quoteEndExclusive - value.contextStartInclusive > value.contextText.length || !list(value.unknownItems, v => text(v))) return false
  if (splits(value.contextText, value.quoteStartInclusive - value.contextStartInclusive)
    || splits(value.contextText, value.quoteEndExclusive - value.contextStartInclusive)) return false
  if (![ ['title', 300], ['sourceLocator', 500], ['sectionTitle', 2048] ].every(([key, limit]) => value[key] == null || text(value[key], limit))) return false
  if (value.pageNumberKind === 'UNKNOWN' ? value.recordedPageNumber != null
    : value.pageNumberKind !== 'RECORDED_SOURCE_PAGE' || !Number.isSafeInteger(value.recordedPageNumber) || value.recordedPageNumber < 1) return false
  if (value.safeSourceUrl != null) {
    try {
      if (!text(value.safeSourceUrl, 2048)) return false
      const url = new URL(value.safeSourceUrl)
      if (url.protocol !== 'https:' || url.username || url.password || url.port && url.port !== '443') return false
    } catch { return false }
  }
  return true
}
export function createRuleCheckClient({ session = ruleCheckSession, fetchImpl = (...args) => fetch(...args), timeoutMs = 10000 } = {}) {
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0 || timeoutMs > 60000) throw new RuleCheckError('INVALID_TIMEOUT')
  let round = 0
  const resultRequests = new Set()
  const current = (epoch, expectedRound) => {
    if (session.state.epoch !== epoch || expectedRound != null && expectedRound !== round) throw new RuleCheckError('STALE_RESPONSE')
  }
  const cancelResults = () => { round++; for (const controller of resultRequests) controller.abort(); resultRequests.clear() }
  async function request(method, path, body, { token = session.csrf, form = false, expectedRound } = {}) {
    const scope = session.acquire()
    if (expectedRound != null) resultRequests.add(scope.controller)
    const timer = setTimeout(() => scope.controller.abort(), timeoutMs)
    let ownRevocationEpoch = null
    try {
      current(scope.epoch, expectedRound)
      const headers = { Accept: 'application/json' }
      if (method !== 'GET') {
        if (!text(token?.headerName, 128) || !text(token?.token, 512)) throw new RuleCheckError('CSRF_UNAVAILABLE')
        headers[token.headerName] = token.token
        headers['Content-Type'] = form ? 'application/x-www-form-urlencoded;charset=UTF-8' : 'application/json; charset=UTF-8'
      }
      const response = await fetchImpl(PREFIX + path, { method, headers, credentials: 'same-origin', cache: 'no-store', signal: scope.controller.signal,
        ...(body === undefined ? {} : { body: form ? body : JSON.stringify(body) }) })
      current(scope.epoch, expectedRound)
      if (scope.controller.signal.aborted) throw new RuleCheckError('NETWORK_UNAVAILABLE')
      if (response.status === 401) {
        // HTTP身份失效不依赖错误正文可解析；先排除旧轮，空正文/代理HTML也要清当前主体。
        session.clear('AUTH_EXPIRED'); ownRevocationEpoch = session.state.epoch
        throw new RuleCheckError('AUTH_REVOKED', 401)
      }
      let data = null
      if (response.status !== 204) {
        try { data = await response.json() } catch { current(scope.epoch, expectedRound); throw new RuleCheckError('BAD_RESPONSE', response.status) }
      }
      // Abort并不保证物理请求结束；旧身份/旧问题的成功和401都必须在改状态前丢弃。
      current(scope.epoch, expectedRound)
      if (scope.controller.signal.aborted) throw new RuleCheckError('NETWORK_UNAVAILABLE')
      if (!response.ok) {
        const code = typeof data?.code === 'string' && /^[A-Z_]{1,80}$/.test(data.code) ? data.code : `HTTP_${response.status}`
        throw new RuleCheckError(code, response.status)
      }
      if (response.status !== 204 && !object(data)) throw new RuleCheckError('BAD_RESPONSE', response.status)
      return data
    } catch (error) {
      // 当前401主动清身份会换代，不应把自己的登录提示误判成迟到响应；新身份仍不接收旧错误。
      if (error instanceof RuleCheckError && error.status === 401 && ownRevocationEpoch === session.state.epoch) throw error
      current(scope.epoch, expectedRound)
      if (error instanceof RuleCheckError) throw error
      throw new RuleCheckError('NETWORK_UNAVAILABLE')
    } finally { clearTimeout(timer); resultRequests.delete(scope.controller); scope.release() }
  }
  async function fetchCsrf(expectedRound) {
    const epoch = session.state.epoch, value = await request('GET', '/auth/csrf', undefined, { expectedRound })
    // 查证的前置CSRF也属于本题；不能在轮次检查前写token或清新问题的身份。
    current(epoch, expectedRound)
    if (!text(value?.headerName, 128) || !text(value?.token, 512)) throw new RuleCheckError('BAD_RESPONSE')
    session.csrf = value; return value
  }
  const csrf = () => fetchCsrf()
  async function write(path, body, options = {}) {
    const epoch = session.state.epoch, token = session.csrf || await fetchCsrf(options.expectedRound)
    current(epoch, options.expectedRound); return request('POST', path, body, { ...options, token })
  }
  async function readActor() {
    const epoch = session.state.epoch, actor = await request('GET', '/auth/me')
    current(epoch)
    if (!id(actor?.userId) || !['USER', 'MAINTAINER'].includes(actor.role) || !Number.isSafeInteger(actor.authRevision) || actor.authRevision < 0) throw new RuleCheckError('BAD_RESPONSE')
    const old = session.state.actor
    if (old && (old.userId !== actor.userId || old.role !== actor.role || old.authRevision !== actor.authRevision)) session.clear('IDENTITY_CHANGED')
    session.state.actor = actor; session.state.checked = true; return actor
  }
  async function me() {
    // 退出后同epoch的迟到身份查询会复活旧显示；公开读与登录/退出转换不能交叠。
    if (session.state.identityBusy) throw new RuleCheckError('AUTH_TRANSITION')
    return readActor()
  }
  return {
    csrf, me, cancelResults,
    async login(username, password) {
      if (session.state.identityBusy) throw new RuleCheckError('AUTH_TRANSITION')
      session.state.identityBusy = true
      try {
        session.clear(); cancelResults(); const epoch = session.state.epoch
        const token = await csrf(); current(epoch)
        await request('POST', '/auth/login', new URLSearchParams({ username, password }).toString(), { form: true, token })
        current(epoch); session.csrf = null; await csrf(); return await readActor()
      } finally { session.state.identityBusy = false }
    },
    async logout() {
      if (session.state.identityBusy) throw new RuleCheckError('AUTH_TRANSITION')
      session.state.identityBusy = true
      try {
        const token = session.csrf; session.clear(); cancelResults()
        await request('POST', '/auth/logout', undefined, { token: token || await csrf() })
      } catch (error) { session.clear('LOGOUT_UNCONFIRMED'); throw error }
      finally { session.state.identityBusy = false }
    },
    async check(question, confirmedConditions, clarificationToken) {
      if (!text(question, 2048) || !claims(confirmedConditions) || clarificationToken != null && !id(clarificationToken)) throw new RuleCheckError('INVALID_RULE_CHECK_REQUEST')
      if (session.state.identityBusy) throw new RuleCheckError('AUTH_TRANSITION')
      cancelResults(); const expectedRound = round
      const body = { question, confirmedConditions, ...(clarificationToken == null ? {} : { clarificationToken }) }
      const value = await write('/rule-check', body, { expectedRound })
      if (!reply(value)) throw new RuleCheckError('BAD_RESPONSE')
      // HTTP字段按后端合同核对，再投影为页面条件列表；不能用自行造的前端夹具替代接口对接。
      return { ...value, scope: value.scope == null ? null : { ...value.scope, conditions: value.scope.declaredConditions } }
    },
    async original(token) {
      if (!id(token)) throw new RuleCheckError('INVALID_ORIGINAL_TOKEN')
      if (session.state.identityBusy) throw new RuleCheckError('AUTH_TRANSITION')
      const value = await request('GET', `/rule-check/originals/${token}`, undefined, { expectedRound: round })
      if (!located(value)) throw new RuleCheckError('BAD_RESPONSE')
      return value
    }
  }
}
export const ruleCheckClient = createRuleCheckClient()
export function ruleCheckErrorMessage(error) {
  if (error.code === 'NETWORK_UNAVAILABLE' || error.code === 'BAD_RESPONSE') return '本次查证结果未确认，请主动重新查证。'
  if (error.code === 'AUTH_TRANSITION') return '正在切换登录状态，请稍后操作。'
  if (error.status === 401) return '登录已失效，请重新登录。'
  if (error.status === 403) return '当前账号或登录状态无法查证，请重新登录。'
  if (error.status === 404 || error.status === 409) return '引用或资料已变化、过期或被新问题替代，请重新查证。'
  if (error.status === 400 || error.code === 'INVALID_RULE_CHECK_REQUEST') return '请填写问题，并只确认本轮要求的条件。'
  if (error.status === 429) return '查证过于频繁，请稍后再试。'
  return '暂时无法查证，请稍后再试。'
}
