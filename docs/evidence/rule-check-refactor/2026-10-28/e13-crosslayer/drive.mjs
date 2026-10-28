// E13 跨层验收驱动：真实 HTTP 夹具宿主 → 保存响应 → 用前端契约模块解析。
// 仅回环 127.0.0.1，无模型/数据库调用；样例结果由夹具显式构造，不冒充真实模型输出。
import { writeFile } from 'node:fs/promises'
import { validateRuleAgentSnapshot } from '../../../agent/frontend/src/api/rule-agent-contract.js'

const P = '/api/zhku/controlled-preview/v2'
const base = `http://127.0.0.1:${process.argv[2] ?? '18089'}${P}`
const outDir = new URL('./', import.meta.url)
let cookie = ''

async function call(method, path, body, csrf) {
  const headers = { Accept: 'application/json' }
  if (cookie) headers.Cookie = cookie
  if (body !== undefined) headers['Content-Type'] = 'application/json; charset=UTF-8'
  if (csrf) headers[csrf.headerName] = csrf.token
  const res = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual' })
  const set = res.headers.getSetCookie?.() ?? []
  if (set.length) cookie = set.map(x => x.split(';')[0]).join('; ')
  const text = await res.text()
  let json = null
  try { json = text ? JSON.parse(text) : null } catch { /* 表单/空响应 */ }
  return { status: res.status, json, text }
}

async function login(user) {
  const csrf = (await call('GET', '/auth/csrf')).json
  const res = await fetch(base + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token, Cookie: cookie },
    body: `username=${user}&password=synthetic-only-password`,
  })
  const set = res.headers.getSetCookie?.() ?? []
  if (set.length) cookie = set.map(x => x.split(';')[0]).join('; ')
  if (res.status !== 200) throw new Error(`login ${user} -> ${res.status}: ${await res.text()}`)
  return (await call('GET', '/auth/csrf')).json
}

async function ask(question, csrf) {
  const accepted = await call('POST', '/tasks', { question, clientRequestId: crypto.randomUUID() }, csrf)
  if (accepted.status !== 202) throw new Error(`accepted ${accepted.status}: ${accepted.text}`)
  const taskId = accepted.json.taskId
  await new Promise(r => setTimeout(r, 2600)) // 夹具工作线程在入队后固定延迟 1.8s
  const snapshot = await call('GET', `/tasks/${taskId}`)
  if (snapshot.status !== 200) throw new Error(`snapshot ${snapshot.status}: ${snapshot.text}`)
  return snapshot.json
}

const csrf = await login('student-a')
const results = {}

const gap = await ask('今晚补选几点关？帮我补上。', csrf)
validateRuleAgentSnapshot(gap) // 前端契约：解析失败会抛 RuleAgentContractError
const issue = gap.result.items[0].issues[0]
await writeFile(new URL('http-e13-insufficient.json', outDir), JSON.stringify(gap, null, 2))
results.insufficient = {
  httpStatus: 200,
  frontendContract: 'PARSED',
  taskStatus: gap.taskStatus,
  runState: gap.runState,
  coverage: gap.result.coverage,
  reasonCode: issue.reasonCode,
  kind: issue.kind,
  claims: gap.result.items[0].claims.length,
  hasFabricatedDeadline: /\d{1,2}\s*[:：]\s*\d{2}|\d{1,2}\s*点/.test(`${issue.message}${issue.nextStep}`),
  nextStep: issue.nextStep,
  message: issue.message,
}

const failed = await ask('故障', csrf)
validateRuleAgentSnapshot(failed)
await writeFile(new URL('http-e13-failed.json', outDir), JSON.stringify(failed, null, 2))
const failedIssue = failed.result.items[0].issues[0]
results.technicalFailure = {
  taskStatus: failed.taskStatus,
  runState: failed.runState,
  coverage: failed.result.coverage,
  reasonCode: failedIssue.reasonCode,
  kind: failedIssue.kind,
}

await writeFile(new URL('http-e13-summary.json', outDir), JSON.stringify(results, null, 2))
console.log(JSON.stringify(results, null, 2))
