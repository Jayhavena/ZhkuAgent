// 两窗口浏览器验收的**同源脚手架**（自有端口，不是产品拓扑）。
//
// 为什么需要它：学生会话 Cookie 的 path 是 `/api/zhku/controlled-preview/v2`，且客户端用
// `credentials:'same-origin'`——浏览器必须从**与 /api 同源**的地址加载页面。T06 的受控后端按设计
// 只绑回环地址（LocalPreviewBindingGuard），所以这里用一个最小的 Node 静态+代理服务把「学生构建产物」
// 与「受控后端」放在同一个源上，避免引入容器内 nginx 与宿主侧转发（那是 T06 的验收脚手架范畴）。
//
// 本脚手架：只读 dist 目录、只代理到 127.0.0.1 上的后端、不落盘、不改任何仓库文件。
// 用法：node browser-attach-proxy.mjs --dist <学生dist> --backend-port <端口> [--port 8161]
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const args = process.argv.slice(2)
const flag = name => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null }
const dist = flag('--dist')
const backendPort = Number(flag('--backend-port'))
const port = Number(flag('--port') ?? 8161)
if (!dist || !fs.existsSync(dist)) { console.error('FAILED: --dist 必须是存在的构建产物目录'); process.exit(2) }
if (!Number.isInteger(backendPort)) { console.error('FAILED: --backend-port 必填'); process.exit(2) }

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.json': 'application/json' }

const root = path.resolve(dist)
const server = http.createServer((request, response) => {
  if (request.url.startsWith('/api/')) {
    // 透传方法/头/体，并把上游 Set-Cookie 原样带回，保证同源会话 Cookie 行为与真实部署一致。
    const upstream = http.request({ host: '127.0.0.1', port: backendPort, path: request.url, method: request.method, headers: { ...request.headers, host: `127.0.0.1:${backendPort}` } }, reply => {
      response.writeHead(reply.statusCode, reply.headers)
      reply.pipe(response)
    })
    upstream.on('error', error => { response.writeHead(502, { 'Content-Type': 'text/plain' }); response.end(`upstream error: ${error.code}`) })
    request.pipe(upstream)
    return
  }
  const url = new URL(request.url, 'http://127.0.0.1')
  // path.resolve 统一分隔符：dist 里是 '/'，path.join 产出 '\'，直接比较前缀会误判并回落 index.html，
  // 从而让 JS 资产以 text/html 返回、模块脚本被浏览器拒绝。
  let file = path.resolve(root, `.${decodeURIComponent(url.pathname)}`)
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(root, 'index.html')
  response.setHeader('Content-Type', TYPES[path.extname(file)] ?? 'application/octet-stream')
  fs.createReadStream(file).pipe(response)
})

server.listen(port, '127.0.0.1', () => console.log(JSON.stringify({ ready: true, origin: `http://127.0.0.1:${port}`, dist, backendPort })))
const shutdown = () => server.close(() => process.exit(0))
process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)
