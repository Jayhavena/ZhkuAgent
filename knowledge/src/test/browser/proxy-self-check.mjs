import assert from 'node:assert/strict'
import http from 'node:http'
import net from 'node:net'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))
// 自检产物不写入源码树，避免与并行验收的源码身份快照互相污染。
const batch = fs.mkdtempSync(path.join(os.tmpdir(), 'zhku-reviewed-proxy-self-check-'))
const dist = path.join(batch, 'dist')
fs.mkdirSync(dist); fs.writeFileSync(path.join(dist, 'index.html'), '<!doctype html><title>synthetic proxy self-check</title>')
fs.writeFileSync(path.join(dist, 'app.js'), '/* synthetic js */')
const prefix = '/api/zhku/controlled-preview/v2'
const task = `${prefix}/tasks/11111111-1111-4111-8111-111111111111`
const stub = http.createServer((req, res) => {
  res.writeHead(200, { 'content-type': 'application/json', 'set-cookie': `synthetic-cookie=value; Path=${prefix}; HttpOnly` })
  res.end(JSON.stringify(req.url === task ? { taskStatus: 'RESOLVED', result: { evidence: ['SYNTHETIC_ONLY'] } } : { synthetic: true }))
})
await new Promise(resolve => stub.listen(0, '127.0.0.1', resolve))
const backendPort = stub.address().port
fs.writeFileSync(path.join(batch, 'browser-ready.json'), JSON.stringify({ backendPort, executionMode: 'SIMULATED_ENGINEERING_REPLAY_REAL_IDENTITY_REVIEWED_ASSETS', providerCalls: 0 }))
const child = spawn(process.execPath, [path.join(root, 'reviewed-student-browser-proxy.mjs'), '--evidence', batch, '--dist', dist, '--explicit-test-lease'], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
let output = ''
child.stdout.on('data', data => { output += data }); child.stderr.on('data', data => { output += data })
const exited = new Promise(resolve => child.on('exit', code => resolve(code)))
const waitFile = async file => {
  const until = Date.now() + 5000
  while (!fs.existsSync(path.join(batch, file))) {
    if (Date.now() > until) throw new Error(`self-check timed out: ${file}; ${output}`)
    await new Promise(resolve => setTimeout(resolve, 20))
  }
  return JSON.parse(fs.readFileSync(path.join(batch, file), 'utf8'))
}
const report = { mode: 'SYNTHETIC_PROXY_SELF_CHECK_ONLY', actualDatabaseCalls: 0, actualProviderCalls: 0, checks: {} }
let frontendPort
try {
  const ready = await waitFile('browser-proxy-ready.json'); frontendPort = ready.frontendPort
  const origin = ready.origin
  const page = await fetch(origin + '/rules'); assert.equal(page.status, 200); assert.match(await page.text(), /synthetic proxy self-check/)
  const js = await fetch(origin + '/app.js'); assert.match(js.headers.get('content-type'), /javascript/)
  report.checks.actualStaticAssetsAndHistoryFallback = true
  const result = await fetch(origin + task)
  assert.equal(result.headers.get('set-cookie'), `synthetic-cookie=value; Path=${prefix}; HttpOnly`)
  assert.equal(result.headers.get('cache-control'), 'no-store')
  assert.deepEqual(await result.json(), { taskStatus: 'RESOLVED', result: { evidence: ['SYNTHETIC_ONLY'] } })
  report.checks.unmodifiedTaskBodyAndSessionCookieForwarded = true
  await fetch(origin + prefix + '/auth/login', { method: 'POST', headers: { cookie: 'synthetic-cookie=SECRET_SYNTHETIC' }, body: 'password=SECRET_SYNTHETIC' })
  const exchanges = JSON.parse(fs.readFileSync(path.join(batch, 'browser-proxy-exchanges.json'), 'utf8'))
  const auth = exchanges.events.find(event => event.path.endsWith('/auth/login'))
  assert.equal(auth.actual, undefined); assert.equal(JSON.stringify(exchanges).includes('SECRET_SYNTHETIC'), false)
  report.checks.authBodyAndCookieNotRecorded = true
  fs.writeFileSync(path.join(batch, 'HOLD_NEXT_TERMINAL'), '')
  let returned = false
  const late = fetch(origin + task).then(async response => { returned = true; return response.json() })
  const held = await waitFile('late-response-held.json'); assert.equal(JSON.parse(held.actual).taskStatus, 'RESOLVED')
  assert.equal(returned, false)
  fs.writeFileSync(path.join(batch, 'RELEASE_LATE'), '')
  assert.equal((await late).taskStatus, 'RESOLVED')
  report.checks.actualTerminalResponseHeldThenReleased = true
  fs.writeFileSync(path.join(batch, 'STOP'), '')
  assert.equal(await exited, 0)
  const exit = await waitFile('browser-proxy-exit.json'); assert.equal(exit.frontendListenerExited, true); assert.equal(exit.explicitStopReceived, true)
  const listenerClosed = await new Promise(resolve => { const socket = net.connect({ port: frontendPort, host: '127.0.0.1' }); socket.on('connect', () => { socket.destroy(); resolve(false) }); socket.on('error', error => resolve(error.code === 'ECONNREFUSED')) })
  assert.equal(listenerClosed, true)
  report.checks.explicitStopOwnedProcessAndListenerExit = true
} finally {
  fs.writeFileSync(path.join(batch, 'STOP'), '')
  if (child.exitCode === null) child.kill()
  await new Promise(resolve => stub.close(resolve))
  report.output = output
  fs.writeFileSync(path.join(batch, 'self-check.json'), JSON.stringify(report, null, 2) + '\n')
}
console.log(JSON.stringify({ ok: true, report: path.join(batch, 'self-check.json'), checks: report.checks }))
