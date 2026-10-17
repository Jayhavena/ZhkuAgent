// 仅验收宿主：学生实际构建与回环后端同源；不替换身份、资料、任务或结果。
// 显式目录租约和 STOP 属于测试生命周期，不能作为产品启动器使用。
import http from 'node:http'
import net from 'node:net'
import fs from 'node:fs'
import path from 'node:path'

const args = process.argv.slice(2)
const flag = key => { const i = args.indexOf(key); return i < 0 ? null : args[i + 1] }
const evidence = path.resolve(flag('--evidence') || '')
const dist = path.resolve(flag('--dist') || '')
if (!flag('--evidence') || !flag('--dist') || !args.includes('--explicit-test-lease')) throw new Error('须显式提供 --evidence、--dist、--explicit-test-lease')
if (!fs.existsSync(path.join(dist, 'index.html'))) throw new Error('学生构建不存在')
if (fs.existsSync(path.join(evidence, 'STOP'))) throw new Error('新租约不得复用旧 STOP')
const ready = JSON.parse(fs.readFileSync(path.join(evidence, 'browser-ready.json'), 'utf8'))
if (ready.executionMode !== 'SIMULATED_ENGINEERING_REPLAY_REAL_IDENTITY_REVIEWED_ASSETS' || ready.providerCalls !== 0 || !Number.isInteger(ready.backendPort) || ready.backendPort < 1024 || ready.backendPort > 65535) throw new Error('拒绝非本轮显式模拟/真实资料租约')
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon' }
const events = [], held = []
let heldOnce = false, stopping = false, port = 0
const save = (file, value) => fs.writeFileSync(path.join(evidence, file), JSON.stringify(value, null, 2) + '\n')
const snapshotPath = /^\/api\/zhku\/controlled-preview\/v2\/tasks\/[0-9a-f-]{36}$/i
const sockets = new Set()
const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1')
  if (url.pathname.startsWith('/api/zhku/controlled-preview/v2/')) {
    const startedAt = new Date().toISOString()
    const upstream = http.request({ hostname: '127.0.0.1', port: ready.backendPort, path: request.url, method: request.method, headers: { ...request.headers, host: `127.0.0.1:${ready.backendPort}` } }, reply => {
      const chunks = []
      reply.on('data', value => chunks.push(value))
      reply.on('end', () => {
        const body = Buffer.concat(chunks)
        const event = { method: request.method, path: url.pathname, status: reply.statusCode, startedAt, receivedAt: new Date().toISOString() }
        // 仅记录任务合同；登录表单、口令、Cookie 和 CSRF 不进入证据。
        if (url.pathname.includes('/tasks')) event.actual = body.toString('utf8')
        events.push(event)
        const deliver = () => {
          event.deliveryAttemptAt = new Date().toISOString(); event.clientDisconnected = response.destroyed
          if (!response.destroyed) { response.writeHead(reply.statusCode, { ...reply.headers, 'cache-control': 'no-store' }); response.end(body) }
          save('browser-proxy-exchanges.json', { executionMode: ready.executionMode, providerCalls: 0, events })
        }
        let terminal = false
        try { terminal = snapshotPath.test(url.pathname) && JSON.parse(body).taskStatus === 'RESOLVED' } catch { /* 非快照 */ }
        if (!heldOnce && terminal && fs.existsSync(path.join(evidence, 'HOLD_NEXT_TERMINAL'))) {
          heldOnce = true; event.heldForLateResponseCheck = true; held.push(deliver)
          save('late-response-held.json', { path: url.pathname, status: reply.statusCode, actual: body.toString('utf8'), receivedAt: event.receivedAt })
          save('browser-proxy-exchanges.json', { executionMode: ready.executionMode, providerCalls: 0, events })
        } else deliver()
      })
    })
    upstream.on('error', error => {
      if (!response.destroyed) { response.writeHead(502, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); response.end(JSON.stringify({ code: 'TEST_PROXY_UPSTREAM_UNAVAILABLE' })) }
      save('browser-proxy-upstream-error.json', { code: error.code })
    })
    request.pipe(upstream); return
  }
  if (url.pathname.startsWith('/api/')) { response.writeHead(404); response.end(); return }
  let file
  try { file = path.resolve(dist, '.' + decodeURIComponent(url.pathname)) } catch { response.writeHead(400); response.end(); return }
  if (file !== dist && !file.startsWith(dist + path.sep)) { response.writeHead(404); response.end(); return }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(dist, 'index.html')
  response.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' })
  fs.createReadStream(file).pipe(response)
})
server.on('connection', socket => { sockets.add(socket); socket.on('close', () => sockets.delete(socket)) })
server.listen(0, '127.0.0.1', () => {
  port = server.address().port
  save('browser-proxy-ready.json', { processId: process.pid, frontendPort: port, backendPort: ready.backendPort, origin: `http://127.0.0.1:${port}`, dist, executionMode: ready.executionMode, providerCalls: 0, topology: 'TEST_STATIC_SAME_ORIGIN_PROXY_NOT_NGINX' })
})
const until = Date.now() + 900000
const timer = setInterval(() => {
  if (fs.existsSync(path.join(evidence, 'RELEASE_LATE'))) {
    for (const deliver of held.splice(0)) deliver()
  }
  if (!stopping && (fs.existsSync(path.join(evidence, 'STOP')) || Date.now() > until)) {
    stopping = true; clearInterval(timer)
    for (const deliver of held.splice(0)) deliver()
    server.close(() => {
      // 实际重新连接必须被拒；只调用 close() 不能证明监听已经释放。
      const probe = net.connect({ port, host: '127.0.0.1' })
      probe.once('connect', () => { probe.destroy(); save('browser-proxy-exit.json', { frontendListenerExited: false }); process.exitCode = 1 })
      probe.once('error', error => {
        const exited = error.code === 'ECONNREFUSED'
        save('browser-proxy-exit.json', { processId: process.pid, frontendListenerExited: exited, explicitStopReceived: fs.existsSync(path.join(evidence, 'STOP')), providerCalls: 0, heldResponsesRemaining: held.length })
        if (!exited || !fs.existsSync(path.join(evidence, 'STOP'))) process.exitCode = 1
      })
    })
    for (const socket of sockets) socket.destroy()
  }
}, 100)
