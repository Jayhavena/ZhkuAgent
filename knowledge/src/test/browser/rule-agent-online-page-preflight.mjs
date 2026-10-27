// 测试专用HTTP预检：执行真实页面API Client/严格校验器，不是浏览器DOM验收，不制造模型答案。
import assert from 'node:assert/strict'
import {readFile, writeFile} from 'node:fs/promises'
import {resolve} from 'node:path'
import {randomUUID} from 'node:crypto'

const port = Number(process.argv[2]), batch = resolve(process.argv[3])
assert.ok(Number.isInteger(port) && port >= 1024 && port <= 65535)
const ready = JSON.parse(await readFile(resolve(batch, 'page-preflight-ready.json'), 'utf8'))
assert.equal(ready.mode, 'ONLINE_ASSEMBLY_PAGE_PREFLIGHT_NO_MODEL')
assert.equal(ready.runnerEnabled, false); assert.equal(ready.proxyPort, port)
const pack = JSON.parse(await readFile(resolve(batch, 'request-package.json'), 'utf8'))
assert.equal(pack.authorizationStatus, 'NOT_AUTHORIZED')
const question = pack.plan.cases[0].question
assert.equal(pack.plan.cases[0].id, 'E02')
// Node没有页面跨窗口生命周期；不启动Node BroadcastChannel，网络/返回值未作mock。
globalThis.BroadcastChannel = undefined
const {createRuleTaskClient} = await import('../../../../agent/frontend/src/api/rule-task.js')
const {createControlledSession} = await import('../../../../agent/frontend/src/state/controlled-session.js')
const origin = `http://127.0.0.1:${port}`, prefix = '/api/zhku/controlled-preview/v2'
const exchanges = [], checks = []
const safeRoute = path => path.replace(/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}/gi, '{opaque}')
function user() {
  let cookie = '' // 只在内存保留Session，不写请求头、登录正文、CSRF或任何token。
  const fetchImpl = async (path, options = {}) => {
    assert.ok(path === '/rules' || path.startsWith(prefix + '/'))
    const headers = {...options.headers, ...(cookie ? {Cookie: cookie} : {})}
    const response = await fetch(origin + path, {...options, headers, redirect: 'error', signal: options.signal || AbortSignal.timeout(10000)})
    for (const value of response.headers.getSetCookie()) cookie = value.split(';', 1)[0]
    exchanges.push({method: options.method || 'GET', route: safeRoute(path), status: response.status})
    return response
  }
  const client = createRuleTaskClient({session: createControlledSession(), fetchImpl})
  return {client, raw: fetchImpl}
}
async function rejected(call, status) {
  try { await call(); assert.fail('request unexpectedly accepted') }
  catch (error) { assert.equal(error.status, status, 'expected HTTP rejection') }
}
let passed = false, failureType = null
try {
  const anonymous = user(), a = user(), b = user(), admin = user()
  const page = await anonymous.raw('/rules')
  assert.equal(page.status, 200); assert.match(await page.text(), /\/assets\//)
  checks.push('actual student build /rules 200')
  await rejected(() => anonymous.client.capabilities(), 401)
  await rejected(() => a.client.login('t06-chain-a', 't06-chain-wrong-pass'), 401)
  await a.client.login('t06-chain-a', 't06-chain-correct-pass')
  await b.client.login('t06-chain-a', 't06-chain-correct-pass')
  await admin.client.login('t06-chain-admin', 't06-chain-correct-pass')
  await rejected(() => admin.client.capabilities(), 403)
  assert.deepEqual(await a.client.capabilities(), {schemaVersion: 3, asynchronous: true})
  checks.push('real DB-backed identity/session: anonymous 401, wrong login 401, USER 200, maintainer 403')
  const key = randomUUID()
  const withoutCsrf = await a.raw(prefix + '/tasks', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({question, clientRequestId: key})})
  assert.equal(withoutCsrf.status, 403); await withoutCsrf.text()
  await rejected(() => a.client.lookup('CREATE', key), 404)
  await rejected(() => a.client.taskRequest('POST', '/tasks', {question, clientRequestId: key, owner: 'not-trusted'}), 400)
  const accepted = await a.client.create(question, key)
  assert.equal(accepted.duplicate, false)
  const duplicate = await a.client.create(question, key)
  assert.equal(duplicate.duplicate, true); assert.equal(duplicate.taskId, accepted.taskId)
  await rejected(() => a.client.create(question + ' ', key), 409)
  assert.equal((await a.client.lookup('CREATE', key)).taskId, accepted.taskId)
  await rejected(() => b.client.snapshot(accepted.taskId), 404)
  await rejected(() => b.client.lookup('CREATE', key), 404)
  const active = await a.client.snapshot(accepted.taskId)
  assert.equal(active.taskStatus, 'ACTIVE'); assert.equal(active.execution.mode, 'ONLINE')
  assert.equal(active.execution.modelAttemptCount, 0); assert.equal(active.execution.providerResponseReceived, false)
  assert.equal(active.result, null)
  checks.push('actual API Client accepted5/snapshot11/execution6 strict validators; ACTIVE without model')
  await rejected(() => a.client.original(accepted.taskId, randomUUID()), 404)
  await rejected(() => a.client.message(accepted.taskId, {message: 'PUBLIC', continuationToken: randomUUID(), expectedVersion: active.version, clientRequestId: randomUUID()}), 409)
  const cancel = {runId: accepted.runId, expectedVersion: active.version, clientRequestId: randomUUID()}
  await rejected(() => a.client.cancel(accepted.taskId, {...cancel, expectedVersion: active.version + 1}), 409)
  await rejected(() => b.client.cancel(accepted.taskId, cancel), 404)
  await a.client.cancel(accepted.taskId, cancel)
  assert.equal((await a.client.cancel(accepted.taskId, cancel)).duplicate, true)
  // 只向父测试发“取消已受理”布尔标记；不落盘task/run/token，再由原TaskStore发布真实STOPPED。
  await writeFile(resolve(batch, 'page-cancel-ready'), 'CANCEL_ACCEPTED_NO_MODEL\n', {flag: 'wx'})
  let stopped
  for (let i = 0; i < 100; i++) {
    stopped = await a.client.snapshot(accepted.taskId)
    if (stopped.terminated) break
    await new Promise(r => setTimeout(r, 50))
  }
  assert.equal(stopped.taskStatus, 'STOPPED'); assert.equal(stopped.terminated, true)
  assert.equal(stopped.execution.modelAttemptCount, 0); assert.equal(stopped.execution.providerResponseReceived, false)
  assert.equal(stopped.result.coverage, 'NONE'); assert.deepEqual(stopped.result.evidence, [])
  checks.push('CSRF 403; extra owner field 400; idempotency 202/409; same-student other-session 404')
  checks.push('messages while ACTIVE 409; stale cancel 409; owner cancel 202 duplicate; actual STOPPED with zero attempts')
  await a.client.logout(); await a.client.login('t06-chain-a', 't06-chain-correct-pass')
  await rejected(() => a.client.snapshot(accepted.taskId), 404)
  checks.push('logout/relogin cannot access prior-session task; unpublished original 404')
  passed = true
} catch (error) {
  failureType = error.name || 'Error'
  // 异常可能带完整输入/响应；只输出类型，不打印对象、stack或敏感字段。
  console.error('ONLINE_PAGE_PREFLIGHT_FAILED type=' + failureType)
} finally {
  await writeFile(resolve(batch, 'page-client-result.json'), JSON.stringify({mode: ready.mode, passed, browserExecuted: false,
    actualPageApiClientUsed: true, proxyPort: port, backendPort: ready.backendPort, failureType, checks, exchanges}, null, 2), {flag: 'wx'})
}
process.exit(passed ? 0 : 1)
