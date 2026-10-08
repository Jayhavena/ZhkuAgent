// 仅显式启动的浏览器夹具；没有数据库、供应商或生产假模型开关，不能证明可信身份。
import http from 'node:http'
import { readFile } from 'node:fs/promises'
import { resolve, extname } from 'node:path'
import { fileURLToPath } from 'node:url'
const root = resolve(fileURLToPath(new URL('../dist', import.meta.url)))
const PREFIX = '/api/zhku/controlled-preview/v1'
const ID = '22222222-2222-4222-8222-222222222222'
const B_ID = '44444444-4444-4444-8444-444444444444'
const submissions = new Map()
const SOURCE = { sourceTitle: '浏览器夹具合成规则', sourceVersion: 'fixture-v1', sourceLocator: '合成第1条', applicableScope: '仅测试' }
const CONVERSATION_ID = '33333333-3333-4333-8333-333333333333'
let conversation = null
const state = { mode: 'visible', posts: 0, user: null, cancelled: false, terminated: true, conversationMode: 'normal', conversationPosts: 0 }
function controlState() { return conversation ? { ...conversation } : null }
function send(response, status, body) { response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }); response.end(body == null ? '' : JSON.stringify(body)) }
function run() {
  const hidden = state.mode === 'hidden'
  const stateName = state.cancelled ? 'CANCELLED' : state.mode === 'running' ? 'RUNNING' : 'SUCCEEDED'
  const visible = stateName === 'SUCCEEDED' && !hidden
  return { run: { runId: ID, state: stateName, status: stateName === 'SUCCEEDED' ? 'ANSWERED' : null,
    answer: visible ? '合成夹具：学分合计3.5，每学期上限4。<script>不是HTML</script>' : null,
    source: visible ? SOURCE : null, terminated: state.terminated, stopReason: state.cancelled ? 'USER_REQUEST' : 'NONE',
    failureCategory: 'NONE', steps: [] }, resultVisibility: visible ? 'VISIBLE' : hidden ? 'HIDDEN' : 'RUNNING' }
}
const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1')
  if (url.pathname === '/__fixture/control') {
    for (const key of ['mode', 'terminated', 'conversationMode']) if (url.searchParams.has(key)) state[key] = key === 'terminated' ? url.searchParams.get(key) === 'true' : url.searchParams.get(key)
    if (url.searchParams.has('reset')) { state.cancelled = false; state.posts = 0 }
    if (url.searchParams.has('expire')) state.user = null
    return send(response, 200, { ...state, scope: 'browser fixture only / no trusted auth or model' })
  }
  if (url.pathname.startsWith(PREFIX)) {
    const path = url.pathname.slice(PREFIX.length)
    let body = ''
    for await (const part of request) body += part
    if (path === '/auth/csrf') return send(response, 200, { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'fixture-public-token' })
    if (path === '/auth/login') { state.user = new URLSearchParams(body).get('username'); return send(response, 200, { userId: state.user === 'user-b' ? B_ID : ID, role: state.user === 'maintainer' ? 'MAINTAINER' : 'USER', authRevision: 0 }) }
    if (path === '/auth/logout') { state.user = null; return send(response, 204, null) }
    if (!state.user) return send(response, 401, { code: 'AUTH_REVOKED' })
    if (path === '/auth/me') return send(response, 200, { userId: state.user === 'user-b' ? B_ID : ID, role: state.user === 'maintainer' ? 'MAINTAINER' : 'USER', authRevision: 0 })
    if (state.user === 'maintainer') return send(response, 403, { code: 'ROLE_DENIED' })
    if (path === '/conversations' && request.method === 'POST') {
      state.conversationPosts++
      if (state.conversationMode === 'createUnknown') { request.socket.destroy(); return }
      conversation = { sessionId: CONVERSATION_ID, status: 'ACTIVE', pendingIntent: null, pendingField: null,
        revision: 0, expiresAt: new Date(Date.now() + 600000).toISOString() }
      return send(response, 201, controlState())
    }
    if (path.startsWith('/conversations/')) {
      if (!conversation || !path.startsWith(`/conversations/${CONVERSATION_ID}`)) return send(response, 404, { code: 'SESSION_NOT_FOUND' })
      if (state.conversationMode === 'expired') return send(response, 410, { code: 'SESSION_EXPIRED' })
      if (request.method === 'GET') return send(response, 200, controlState())
      state.conversationPosts++
      if (state.conversationMode === 'writeUnknown') { request.socket.destroy(); return }
      if (state.conversationMode === 'conflict') return send(response, 409, { code: 'SESSION_STATE_CHANGED' })
      const input = JSON.parse(body)
      if (conversation.status === 'CANCELLED') return send(response, 409, { code: 'SESSION_CANCELLED' })
      if (input.expectedRevision !== conversation.revision) return send(response, 409, { code: 'SESSION_STATE_CHANGED' })
      if (path.endsWith('/cancel')) {
        conversation.status = 'CANCELLED'; conversation.pendingIntent = null; conversation.pendingField = null; conversation.revision++
        return send(response, 200, controlState())
      }
      if (state.conversationMode === 'evidence') return send(response, 200, { type: 'EVIDENCE_UNAVAILABLE', message: '夹具资料已不可核验，请人工咨询。', answer: null })
      if (conversation.pendingField === 'COURSE_TYPE') {
        conversation.pendingIntent = null; conversation.pendingField = null; conversation.revision++
        return send(response, 200, { type: 'ANSWER', message: '合成夹具答复', handoffRequired: false,
          answer: { ...SOURCE, conclusion: '合成夹具：专业选修课可申请重修。', basis: '申请不代表一定获批。' } })
      }
      conversation.pendingIntent = 'ELECTIVE_RETAKE_AFTER_FAILURE'; conversation.pendingField = 'COURSE_TYPE'; conversation.revision++
      return send(response, 200, { type: 'NEED_CLARIFICATION', message: '你说的是公共选修课，还是专业选修课？', answer: null })
    }
    if (path === '/runs' && request.method === 'POST') {
      state.posts++
      const key = request.headers['idempotency-key']
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(key || '')) return send(response, 400, { code: 'INVALID_SUBMISSION_KEY' })
      const entryKey = state.user + ':' + key, replayed = submissions.has(entryKey)
      if (replayed && submissions.get(entryKey).body !== body) return send(response, 409, { code: 'SUBMISSION_KEY_CONFLICT' })
      if (!replayed) submissions.set(entryKey, { body, acceptedAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 604800000).toISOString() })
      if (state.mode === 'unknown') { request.socket.destroy(); return }
      if (state.mode === 'accepted') return send(response, 503, { accepted: true, acceptance: 'ACCEPTED', submissionKey: key, runId: ID, code: 'RESULT_READ_UNAVAILABLE' })
      const entry = submissions.get(entryKey)
      return send(response, replayed ? 200 : 202, { submissionKey: key, runId: ID, replayed, acceptedAt: entry.acceptedAt, expiresAt: entry.expiresAt, location: 'LIVE', live: run(), archived: null })
    }
    if (path.startsWith('/submissions/') && request.method === 'GET') {
      const key = path.slice('/submissions/'.length), entry = submissions.get(state.user + ':' + key)
      if (!entry) return send(response, 404, { code: 'SUBMISSION_NOT_FOUND' })
      return send(response, 200, { submissionKey: key, runId: ID, replayed: false, acceptedAt: entry.acceptedAt, expiresAt: entry.expiresAt,
        location: state.mode === 'accepted' ? 'UNCONFIRMED' : 'LIVE', live: state.mode === 'accepted' ? null : run(), archived: null })
    }
    if (path === `/runs/${ID}/cancel`) { state.cancelled = true; state.terminated = false; return send(response, 200, run()) }
    if (path === `/runs/${ID}`) return send(response, state.mode === 'online404' ? 404 : 200, state.mode === 'online404' ? { code: 'ARCHIVE_NOT_FOUND' } : run())
    if (path === '/history') return send(response, 200, { items: [{ runId: ID, schemaVersion: 1, archiveStatus: 'FINALIZED', admittedAt: '2026-10-06T08:00:00Z', closedAt: '2026-10-06T08:00:10Z', retainedUntil: '2026-10-13T08:00:10Z' }], nextCursor: null })
    if (path === `/history/${ID}`) {
      const reply = run()
      return send(response, 200, { runId: ID, archiveStatus: 'FINALIZED', execution: { state: reply.run.state, businessStatus: reply.run.status, stopReason: reply.run.stopReason, failureCategory: 'NONE' }, result: reply.run.answer ? { answer: reply.run.answer, source: SOURCE } : null, resultVisibility: { status: state.mode === 'hidden' ? 'HIDDEN' : 'VISIBLE' }, provenance: 'REFERENCE_ONLY' })
    }
    return send(response, 404, { code: 'NOT_FOUND' })
  }
  try {
    const path = resolve(root, '.' + decodeURIComponent(url.pathname))
    if (!path.startsWith(root + '/') && !path.startsWith(root + '\\') && path !== root) return send(response, 404, {})
    const resource = extname(path) ? path : resolve(root, 'index.html')
    const type = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' }[extname(resource)] || 'application/octet-stream'
    const content = await readFile(resource)
    response.writeHead(200, { 'content-type': type + '; charset=utf-8', 'cache-control': 'no-store' }); response.end(content)
  } catch { send(response, 404, {}) }
})
server.listen(58233, '127.0.0.1', () => console.log('FIXTURE_READY port=58233'))
process.stdin.resume()
process.stdin.on('data', () => server.close(() => process.exit(0)))
process.stdin.on('end', () => server.close(() => process.exit(0)))
