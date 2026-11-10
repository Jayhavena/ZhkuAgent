#!/usr/bin/env node
/**
 * 真实 Chrome 持久登录验收脚本（无第三方依赖，直接用 Node 24 自带的 fetch/WebSocket 驱动 CDP）。
 *
 * 为什么手写 CDP 而不引入 Playwright/Puppeteer：本机是离线环境，agent/frontend/node_modules
 * 里没有这两个库也不允许安装；Node 24 自带的 WebSocket 足够完成 Target/Runtime/Page 协议对话。
 *
 * 为什么分成多个 --phase、每个阶段一个独立进程：持久登录的验收点是"关掉浏览器再打开仍然登录"，
 * 只有让 Chrome 进程真的退出、只保留 --user-data-dir，才等价于用户关窗重开；
 * 在同一个进程里刷新页面证明不了这一点。
 *
 * 用法（在仓库根目录执行）：
 *   node agent/frontend/tests/manual/persistent-login-browser.mjs --phase self-check
 *   node ... --phase register --web-url http://127.0.0.1:8080
 *   node ... --phase login
 *   node ... --phase verify-session       # 冷启动后的第一次校验
 *   node ... --phase direct-account
 *   node ... --phase logout
 *   node ... --phase replay-after-logout
 *
 * 退出码：0 = 该阶段所有断言通过；1 = 有断言失败或环境/启动错误（详情打印到 stdout，并写入 JSON）。
 * 本脚本不启动 nginx/后端/数据库，也不修改 src 代码；它只消费 Lead 已经起好的页面。
 *
 * 选择器来源（改前端时必须同步这些位置）：
 *   - agent/frontend/src/views/RuleAccount.vue：login/register/account 三态页面
 *       #account-username / #account-password / #confirm-password / label.remember input[type=checkbox]
 *       form button[type=submit] / p.feedback[role=status] / dl(dt+dd 账号、用户 ID) / 退出登录按钮
 *   - agent/frontend/src/components/rule-check/RuleHeader.vue：data-testid="identity-button"、data-testid="logout"
 *   - agent/frontend/src/views/RuleCheckReplayView.vue：回放 /rules 页不使用 RuleHeader，
 *     它自己的导航里只有一个文本为“退出登录”的按钮，所以退出逻辑要做多重回退。
 */
import { spawn, spawnSync } from 'node:child_process'
import { createServer as createNetServer } from 'node:net'
import { createServer as createHttpServer } from 'node:http'
import { mkdir, writeFile, readFile, rm, stat } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
// agent/frontend/tests/manual -> 上四级就是仓库根；--repo-root 可覆盖，便于脚本被复制到别处使用。
const REPO_ROOT = path.resolve(SCRIPT_DIR, '../../../..')
const API_PREFIX = '/api/zhku/controlled-preview/v2'
const PHASES = ['register', 'login', 'verify-session', 'direct-account', 'logout', 'replay-after-logout', 'self-check', 'self-check-fail', 'self-check-vue', 'self-check-profile-write', 'self-check-profile-read']
// 只有这些阶段需要脚本内置的合成夹具页面，真实阶段一律只连 Lead 起好的前端。
const SELF_CHECK_PHASES = ['self-check', 'self-check-fail', 'self-check-vue', 'self-check-profile-write', 'self-check-profile-read']
// 真实六阶段按顺序执行；自检阶段不属于账号验收链路。
const REAL_PHASES = ['register', 'login', 'verify-session', 'direct-account', 'logout', 'replay-after-logout']
const DEFAULT_CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'

// ---------------------------------------------------------------------------
// 参数
// ---------------------------------------------------------------------------
function parseArgs(argv) {
  const options = {
    webUrl: 'http://127.0.0.1:8080',
    chrome: DEFAULT_CHROME,
    // 默认相对仓库根：tmp/ 已在 .gitignore 中，不会污染工作区。
    profileDir: 'tmp/persistent-login-chrome-profile',
    out: null,
    phase: null,
    username: null,
    password: null,
    userId: null,
    // 记录上一阶段产出的账号信息，供后续独立进程复用（不含密码）。
    statePath: 'tmp/persistent-login-browser/account.json',
    headless: 'new',
    resetProfile: false,
    timeoutMs: 20000,
    repoRoot: REPO_ROOT,
    help: false
  }
  for (let i = 2; i < argv.length; i++) {
    const arg = argv[i]
    const next = () => {
      const value = argv[++i]
      if (value === undefined) throw new Error(`参数 ${arg} 缺少取值`)
      return value
    }
    switch (arg) {
      case '--web-url': options.webUrl = next().replace(/\/+$/, ''); break
      case '--chrome': options.chrome = next(); break
      case '--profile-dir': options.profileDir = next(); break
      case '--out': options.out = next(); break
      case '--phase': options.phase = next(); break
      case '--username': options.username = next(); break
      case '--password': options.password = next(); break
      case '--user-id': options.userId = next(); break
      case '--state': options.statePath = next(); break
      // Chrome 156 上 --headless=new 仍可用；万一某台机器不接受，可退化为 --headless 或不加参数。
      case '--headless': options.headless = next(); break
      case '--reset-profile': options.resetProfile = true; break
      case '--timeout': options.timeoutMs = Number(next()); break
      case '--repo-root': options.repoRoot = path.resolve(next()); break
      case '--help': case '-h': options.help = true; break
      default: throw new Error(`未知参数：${arg}`)
    }
  }
  return options
}

function usage() {
  return [
    '用法：node agent/frontend/tests/manual/persistent-login-browser.mjs --phase <阶段> [选项]',
    '',
    `阶段：${PHASES.join(' | ')}`,
    '',
    '选项：',
    '  --web-url <url>       前端入口，默认 http://127.0.0.1:8080',
    `  --chrome <path>       Chrome 可执行文件，默认 ${DEFAULT_CHROME}`,
    '  --profile-dir <dir>   Chrome 用户数据目录（相对仓库根），默认 tmp/persistent-login-chrome-profile；阶段之间保留',
    '  --out <file>          结果 JSON 路径，默认 tmp/persistent-login-browser/<phase>.json',
    '  --username <name>     注册/登录用的账号，默认自动生成 e2e-<时间戳>',
    '  --password <pwd>      密码，默认由账号确定性派生（Pw-<username>-RuleCheck!）；不会写入任何文件',
    '  --user-id <uuid>      期望的用户 ID（verify 阶段断言用，缺省从 state 文件读）',
    '  --state <file>        跨阶段账号信息缓存，默认 tmp/persistent-login-browser/account.json',
    '  --headless <mode>     new | old | none，默认 new（传给 Chrome 的是 --headless=new）',
    '  --reset-profile       启动前删除 profile 目录（默认保留）',
    '  --timeout <ms>        单个等待条件超时，默认 20000',
    '',
    '  --phase all           依次跑完六个真实阶段（每阶段独立进程），首处失败即停止',
    '  --phase self-check*   不依赖后端的自检阶段（fixture 页面在脚本内）'
  ].join('\n')
}

// ---------------------------------------------------------------------------
// 工具
// ---------------------------------------------------------------------------
async function freePort() {
  return await new Promise((resolve, reject) => {
    const server = createNetServer()
    server.unref()
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address()
      server.close(() => resolve(port))
    })
  })
}

async function exists(file) {
  try { await stat(file); return true } catch { return false }
}

function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)) }

/** 账号格式与后端 PreviewIdentityRepository 的 "[a-z][a-z0-9-]{2,31}" 完全一致。 */
function defaultUsername() {
  return `e2e-${Date.now().toString(36)}`
}

/**
 * 密码必须满足 validPassword：长度 16–64，且每个字符都是可打印 ASCII（32..126）。
 * 用确定性派生而不是随机值，是为了让独立进程的 login 阶段能重算出同一个密码，
 * 从而不必把密码落盘；仍是测试专用口令，不用于任何真实账号。
 */
function passwordFor(username) {
  return `Pw-${username}-RuleCheck!`
}

// ---------------------------------------------------------------------------
// CDP 客户端（flatten 会话模式）
// ---------------------------------------------------------------------------
class CdpClient {
  constructor(url, { timeoutMs = 30000 } = {}) {
    this.url = url
    this.timeoutMs = timeoutMs
    this.nextId = 1
    this.pending = new Map()
    this.listeners = new Map()
    this.closed = false
    this.socket = null
  }

  static async connect(url, options) {
    const client = new CdpClient(url, options)
    await client.open()
    return client
  }

  open() {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(this.url)
      this.socket = socket
      const timer = setTimeout(() => reject(new Error('连接 CDP WebSocket 超时')), this.timeoutMs)
      socket.addEventListener('open', () => { clearTimeout(timer); resolve() })
      socket.addEventListener('error', event => {
        clearTimeout(timer)
        const detail = event?.error?.message || event?.message || 'WebSocket 错误'
        reject(new Error(`CDP WebSocket 连接失败：${detail}`))
      })
      socket.addEventListener('message', event => this.#onMessage(event.data))
      socket.addEventListener('close', () => {
        this.closed = true
        for (const { reject: fail } of this.pending.values()) fail(new Error('CDP 连接已关闭'))
        this.pending.clear()
      })
    })
  }

  #onMessage(raw) {
    let message
    try { message = JSON.parse(typeof raw === 'string' ? raw : raw.toString()) } catch { return }
    if (message.id !== undefined) {
      const entry = this.pending.get(message.id)
      if (!entry) return
      this.pending.delete(message.id)
      clearTimeout(entry.timer)
      if (message.error) entry.reject(new Error(`${entry.method} 失败：${message.error.message}（code ${message.error.code}）`))
      else entry.resolve(message.result)
      return
    }
    const handlers = this.listeners.get(message.method)
    if (!handlers) return
    for (const handler of handlers) {
      try { handler(message.params, message.sessionId) } catch { /* 监听器异常不能打断协议对话 */ }
    }
  }

  on(method, handler) {
    if (!this.listeners.has(method)) this.listeners.set(method, [])
    this.listeners.get(method).push(handler)
  }

  send(method, params = {}, sessionId) {
    if (this.closed) return Promise.reject(new Error(`CDP 已关闭，无法发送 ${method}`))
    const id = this.nextId++
    const payload = { id, method, params }
    if (sessionId) payload.sessionId = sessionId
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id)
        reject(new Error(`CDP ${method} 超时（${this.timeoutMs}ms）`))
      }, this.timeoutMs)
      this.pending.set(id, { resolve, reject, method, timer })
      try { this.socket.send(JSON.stringify(payload)) }
      catch (error) { clearTimeout(timer); this.pending.delete(id); reject(error) }
    })
  }

  close() {
    this.closed = true
    try { this.socket?.close() } catch { /* 已关闭 */ }
  }
}

function chromeArgs({ port, profileDir, headless }) {
  const args = [`--remote-debugging-port=${port}`, `--user-data-dir=${profileDir}`]
  if (headless === 'new') args.push('--headless=new')
  else if (headless === 'old') args.push('--headless')
  args.push(
    '--no-first-run',
    '--no-default-browser-check',
    // 新版 Chrome 对来自非浏览器页面的 DevTools 连接校验 Origin；Node 客户端不带 Origin，
    // 这里显式放开隔离，避免某些版本直接拒绝握手。
    '--remote-allow-origins=*',
    '--disable-extensions',
    '--disable-sync',
    '--disable-default-apps',
    '--disable-background-networking',
    '--disable-background-timer-throttling',
    '--disable-renderer-backgrounding',
    '--disable-features=Translate,MediaRouter,OptimizationHints',
    '--window-size=1280,900',
    'about:blank'
  )
  return args
}

async function fetchJson(url, timeoutMs = 2000) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(url, { signal: controller.signal })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    return await response.json()
  } finally { clearTimeout(timer) }
}

/** 启动 Chrome 并等到 /json/version 可用；返回 {child, wsUrl, version, stderr}。 */
async function launchChrome(options) {
  const profileDir = path.resolve(options.repoRoot, options.profileDir)
  await mkdir(profileDir, { recursive: true })
  if (options.resetProfile) await rm(profileDir, { recursive: true, force: true })
  const port = await freePort()
  const args = chromeArgs({ port, profileDir, headless: options.headless })
  const stderr = []
  const child = spawn(options.chrome, args, { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true })
  child.stdout?.on('data', () => {})
  child.stderr?.on('data', chunk => {
    const text = chunk.toString()
    stderr.push(text)
    if (stderr.join('').length > 8000) stderr.splice(0, stderr.length - 4)
  })
  let exited = null
  child.on('exit', (code, signal) => { exited = { code, signal } })

  const deadline = Date.now() + options.timeoutMs
  let version = null
  let lastError = null
  while (Date.now() < deadline) {
    if (exited) break
    try {
      version = await fetchJson(`http://127.0.0.1:${port}/json/version`, 1000)
      break
    } catch (error) { lastError = error; await sleep(150) }
  }
  if (!version) {
    killTree(child)
    const tail = stderr.join('').trim().slice(-2000)
    const reason = exited
      ? `Chrome 启动后立即退出（code=${exited.code}, signal=${exited.signal}）`
      : `等待 http://127.0.0.1:${port}/json/version 超时：${lastError?.message || '未知错误'}`
    throw new Error(`${reason}\n启动参数：${args.join(' ')}\nChrome stderr（截断）：\n${tail || '（空）'}`)
  }
  return { child, port, wsUrl: version.webSocketDebuggerUrl, version, stderr, profileDir, args }
}

/** 先请求 Browser.close 优雅退出；超时后按 PID 结束进程树（Windows 会留下子进程占住 profile）。 */
async function stopChrome(child) {
  if (!child || child.exitCode !== null) return
  const exited = new Promise(resolve => child.once('exit', resolve))
  await Promise.race([exited, sleep(5000)])
  if (child.exitCode !== null) return
  killTree(child)
  await Promise.race([exited, sleep(3000)])
}

/**
 * 前置探测：确认前端入口真的在监听。
 * 没有这一步时，一个没起的 nginx 只会表现为“Vue 应用挂载超时”，
 * 让人误以为是前端 bug；这里直接说清是服务没起。
 */
async function preflightWeb(options) {
  try {
    const response = await fetch(options.webUrl, { signal: AbortSignal.timeout(5000) })
    // 只要拿到 HTTP 响应就算可达；状态码由断言阶段判断。
    return `HTTP ${response.status}`
  } catch (error) {
    throw new Error(`前端入口不可达：${options.webUrl}（${error.message}）。`
      + '请先用 agent/frontend/start-nginx-preview.ps1 -Action Start -EntryMode replay -BackendPort <n> -WebPort <m> 起好页面；'
      + '本脚本不负责启动 nginx/后端/数据库。')
  }
}

function killTree(child) {
  if (!child || child.exitCode !== null) return
  if (process.platform === 'win32') {
    // 只结束自己启动的 PID 树，不用镜像名杀全部 chrome.exe，避免误伤用户浏览器。
    spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true })
  } else {
    try { child.kill('SIGKILL') } catch { /* 已退出 */ }
  }
}

// ---------------------------------------------------------------------------
// 页面操作（全部通过 Runtime.evaluate 注入）
// ---------------------------------------------------------------------------
class Page {
  constructor(cdp, sessionId, options) {
    this.cdp = cdp
    this.sessionId = sessionId
    this.options = options
  }

  async evaluate(expression, { timeout = 10000 } = {}) {
    const result = await withTimeout(
      this.cdp.send('Runtime.evaluate', {
        expression,
        returnByValue: true,
        awaitPromise: true,
        userGesture: true
      }, this.sessionId),
      timeout,
      `Runtime.evaluate 超时：${expression.slice(0, 80)}`
    )
    if (result.exceptionDetails) {
      const description = result.exceptionDetails.exception?.description || result.exceptionDetails.text || '未知异常'
      throw new Error(`页面脚本异常：${description}`)
    }
    return result.result?.value
  }

  /** 轮询谓词直到为真；导航会销毁执行上下文，这类错误必须继续轮询而不是当失败。 */
  async waitFor(expression, { timeoutMs = this.options.timeoutMs, intervalMs = 120, label = expression } = {}) {
    const deadline = Date.now() + timeoutMs
    let lastError = null
    while (Date.now() < deadline) {
      try {
        const value = await this.evaluate(expression, { timeout: 3000 })
        if (value) return value
      } catch (error) { lastError = error }
      await sleep(intervalMs)
    }
    throw new Error(`等待超时（${timeoutMs}ms）：${label}${lastError ? `；最后一次错误：${lastError.message}` : ''}`)
  }

  async url() { return await this.evaluate('location.href') }
  async path() { return await this.evaluate('location.pathname') }

  async goto(url, { waitForApp = true } = {}) {
    await this.cdp.send('Page.navigate', { url }, this.sessionId)
    await this.waitFor('document.readyState === "complete"', { label: `页面加载完成 ${url}` })
    if (waitForApp) {
      // SPA 挂载完成后 #app 才有子节点；否则随后的选择器断言会在空白页上误判。
      await this.waitFor('(() => { const app = document.querySelector("#app"); return !!app && app.children.length > 0 })()',
        { label: 'Vue 应用挂载' })
    }
  }

  async fill(selector, value) {
    const outcome = await this.evaluate(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) return { ok: false, reason: '找不到元素 ${selector}' };
      el.focus();
      const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
      // Vue 3 的 v-model 监听 input 事件；只赋 value 不会更新组件状态。
      setter.call(el, ${JSON.stringify(String(value))});
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return { ok: true, value: el.value };
    })()`)
    if (!outcome?.ok) throw new Error(outcome?.reason || '填写失败')
    return outcome.value
  }

  async readValue(selector) {
    return await this.evaluate(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      return el ? (el.type === 'checkbox' ? el.checked : el.value) : null;
    })()`)
  }

  async ensureChecked(selector, expected = true) {
    const outcome = await this.evaluate(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (!el) return { ok: false, reason: '找不到勾选框 ${selector}' };
      const already = el.checked === ${expected};
      if (!already) el.click();
      return { ok: true, checked: el.checked, clicked: !already };
    })()`)
    if (!outcome?.ok) throw new Error(outcome?.reason || '设置勾选失败')
    return outcome
  }

  /** 点击表单提交按钮；按钮在 busy 期间是 disabled，必须先等它恢复可点。 */
  async submitForm() {
    await this.waitFor('(() => { const b = document.querySelector("form button[type=submit]"); return !!b && !b.disabled })()',
      { label: '提交按钮可用' })
    const outcome = await this.evaluate(`(() => {
      const b = document.querySelector('form button[type=submit]');
      if (!b) return { ok: false, reason: '找不到提交按钮' };
      if (b.disabled) return { ok: false, reason: '提交按钮仍为禁用状态' };
      b.click();
      return { ok: true, text: (b.textContent || '').trim() };
    })()`)
    if (!outcome?.ok) throw new Error(outcome?.reason || '提交失败')
    return outcome.text
  }

  async text() {
    return await this.evaluate('document.body ? document.body.innerText : ""')
  }

  async feedback() {
    return await this.evaluate(`(() => {
      const el = document.querySelector('p.feedback') || document.querySelector('[role=status]');
      return el ? (el.textContent || '').trim() : '';
    })()`)
  }

  /** 读取 RuleAccount.vue 里 <dl><dt>…</dt><dd>…</dd></dl> 的标签文本。 */
  async definitionList() {
    return await this.evaluate(`(() => {
      const dl = document.querySelector('dl');
      if (!dl) return null;
      const out = {}, nodes = Array.from(dl.children);
      for (let i = 0; i + 1 < nodes.length; i += 2) {
        if (nodes[i].tagName === 'DT') out[(nodes[i].textContent || '').trim()] = (nodes[i + 1].textContent || '').trim();
      }
      return out;
    })()`)
  }

  /**
   * 退出按钮有三处实现：RuleHeader 的下拉菜单（data-testid="logout"，需先展开）、
   * RuleAccount 账号页的裸按钮、回放 /rules 导航里的文本按钮。按优先级依次回退，
   * 避免把“选择器换了”误报成“退出失败”。
   */
  async logout() {
    const outcome = await this.evaluate(`(() => {
      let el = document.querySelector('[data-testid="logout"]');
      let via = el ? 'data-testid=logout' : null;
      if (!el) {
        const opener = document.querySelector('[data-testid="identity-button"]');
        if (opener && !opener.disabled) { opener.click(); el = document.querySelector('[data-testid="logout"]'); via = el ? 'identity-button+logout' : null }
      }
      if (!el) {
        el = Array.from(document.querySelectorAll('button')).find(b => /退出登录|重试退出/.test(b.textContent || ''));
        via = el ? 'button-text' : null;
      }
      if (!el) return { ok: false, reason: '页面上未找到退出登录按钮' };
      if (el.disabled) return { ok: false, reason: '退出登录按钮处于禁用状态' };
      el.click();
      return { ok: true, via, text: (el.textContent || '').trim() };
    })()`)
    if (!outcome?.ok) throw new Error(outcome?.reason || '点击退出失败')
    return outcome
  }

  /** 在页面内用同源 fetch 读取 /auth/me，验证的是浏览器 Cookie 而不是脚本的假设。 */
  async meStatus() {
    const outcome = await this.evaluate(`(async () => {
      try {
        const response = await fetch(${JSON.stringify(API_PREFIX + '/auth/me')}, { credentials: 'same-origin', cache: 'no-store' });
        let body = null;
        try { body = await response.json() } catch { body = null }
        return { status: response.status, body };
      } catch (error) { return { status: 0, error: String(error) } }
    })()`)
    return outcome
  }

  async screenshot(file, { fullPage = true } = {}) {
    let width = 1280, height = 900
    if (fullPage) {
      try {
        const metrics = await this.cdp.send('Page.getLayoutMetrics', {}, this.sessionId)
        const size = metrics.cssContentSize || metrics.contentSize
        if (size?.width && size?.height) {
          width = Math.max(1, Math.min(1280, Math.ceil(size.width)))
          // 超长页面截到 6000px，避免生成几十 MB 的 PNG。
          height = Math.max(1, Math.min(6000, Math.ceil(size.height)))
          await this.cdp.send('Emulation.setDeviceMetricsOverride',
            { width, height, deviceScaleFactor: 1, mobile: false }, this.sessionId)
        }
      } catch { /* 视口覆盖失败时退回当前视口截图 */ }
    }
    try {
      const { data } = await this.cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true }, this.sessionId)
      await mkdir(path.dirname(file), { recursive: true })
      await writeFile(file, Buffer.from(data, 'base64'))
      return file
    } finally {
      if (fullPage) {
        try { await this.cdp.send('Emulation.clearDeviceMetricsOverride', {}, this.sessionId) } catch { /* 忽略 */ }
      }
    }
  }
}

function withTimeout(promise, ms, message) {
  let timer
  const timeout = new Promise((resolve, reject) => {
    timer = setTimeout(() => reject(new Error(message)), ms)
  })
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer))
}

// ---------------------------------------------------------------------------
// 断言与结果
// ---------------------------------------------------------------------------
class Assertions {
  constructor() { this.items = [] }

  /** 单个断言失败只记录，不中断整个阶段——一次运行就能看到全部缺陷。 */
  async check(name, fn) {
    try {
      const detail = await fn()
      this.items.push({ name, ok: true, detail: detail === undefined ? 'ok' : String(detail) })
      return true
    } catch (error) {
      this.items.push({ name, ok: false, detail: error?.message || String(error) })
      return false
    }
  }

  pass(name, detail = 'ok') { this.items.push({ name, ok: true, detail: String(detail) }) }
  fail(name, detail) { this.items.push({ name, ok: false, detail: String(detail) }) }
  get ok() { return this.items.length > 0 && this.items.every(item => item.ok) }
}

function expect(condition, message) { if (!condition) throw new Error(message) }
function expectEqual(actual, expected, label) {
  if (actual !== expected) throw new Error(`${label}：期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actual)}`)
  return actual
}

// ---------------------------------------------------------------------------
// 阶段实现
// ---------------------------------------------------------------------------
async function runRegister(ctx) {
  const { page, assertions, options } = ctx
  const username = ctx.username
  const password = ctx.password

  await page.goto(`${options.webUrl}/register`)
  await assertions.check('register.form_present', async () => {
    await page.waitFor('!!document.querySelector("#account-username")', { label: '注册表单出现' })
    return '找到 #account-username'
  })

  await assertions.check('register.fill_inputs', async () => {
    await page.fill('#account-username', username)
    await page.fill('#account-password', password)
    await page.fill('#confirm-password', password)
    const values = [
      await page.readValue('#account-username'),
      await page.readValue('#account-password'),
      await page.readValue('#confirm-password')
    ]
    expectEqual(values[0], username, '账号回填值')
    expectEqual(values[1], password, '密码回填值')
    expectEqual(values[2], password, '确认密码回填值')
    return `账号 ${username}，密码长度 ${password.length}`
  })

  await assertions.check('register.submit', async () => {
    const label = await page.submitForm()
    return `点击了“${label}”`
  })

  await assertions.check('register.lands_on_login', async () => {
    // 成功路径是 /login?registered=1；失败会停留在 /register 并显示提示。
    // 所以这里等“离开注册页 或 出现错误提示”，避免失败时白等满超时。
    await page.waitFor('(() => { if (location.pathname !== "/register") return true;'
      + ' const el = document.querySelector("p.feedback"); return !!el && (el.textContent || "").trim().length > 0 })()',
    { label: '离开注册页或出现提示' })
    const path = await page.path()
    if (path === '/register') throw new Error(`注册未通过，页面提示：${await page.feedback() || '（空）'}`)
    expectEqual(path, '/login', '注册后的落点')
    return `已跳转到 ${path}${await page.evaluate('location.search')}`
  })

  await assertions.check('register.success_notice', async () => {
    const text = await page.text()
    const feedback = await page.feedback()
    const matched = /注册成功/.test(text)
    expect(matched, `页面未显示“注册成功，请登录。”；当前提示：${feedback || '（空）'}`)
    return `提示：${feedback || '（无）'}`
  })

  const me = await page.meStatus()
  ctx.userId = me?.body?.userId || null
  if (me?.body?.username) expectEqual(me.body.username, username, '注册后 /auth/me 账号')
  return { username, userId: ctx.userId }
}

async function runLogin(ctx) {
  const { page, assertions, options } = ctx
  const username = ctx.username
  const password = ctx.password

  await page.goto(`${options.webUrl}/login`)
  await assertions.check('login.form_present', async () => {
    await page.waitFor('!!document.querySelector("#account-username")', { label: '登录表单出现' })
    return '找到 #account-username'
  })

  await assertions.check('login.fill_inputs', async () => {
    await page.fill('#account-username', username)
    await page.fill('#account-password', password)
    expectEqual(await page.readValue('#account-username'), username, '账号回填值')
    return `账号 ${username}`
  })

  await assertions.check('login.remember_checked', async () => {
    // 默认是 true；这里仍显式确认，因为持久登录断言完全依赖这个勾选。
    // 记录是否真的点击过：若前端把默认值改成 false，这条证据能说明脚本已代为勾选。
    const outcome = await page.ensureChecked('label.remember input[type=checkbox]', true)
    expect(outcome.checked === true, '“保持登录”未处于勾选状态')
    return outcome.clicked ? '保持登录原本未勾选，已由脚本点击勾选' : '保持登录已勾选（页面默认值）'
  })

  await assertions.check('login.submit', async () => {
    const label = await page.submitForm()
    return `点击了“${label}”`
  })

  await assertions.check('login.lands_on_protected_page', async () => {
    // 登录成功后路由 replace 到 safeRuleReturn，默认 /rules；失败会停在 /login 并显示错误提示。
    // 两个条件一起等：失败时立刻带出提示文本，而不是干等超时。
    await page.waitFor('(() => { if (location.pathname !== "/login") return true;'
      + ' const el = document.querySelector("p.feedback"); return !!el && (el.textContent || "").trim().length > 0 })()',
    { label: '离开登录页或出现提示' })
    const path = await page.path()
    if (path === '/login') throw new Error(`登录未通过，页面提示：${await page.feedback() || '（空）'}`)
    expect(path === '/rules' || path === '/account', `登录后落点应为 /rules 或 /account，实际 ${path}；提示：${await page.feedback()}`)
    return `落点 ${path}`
  })

  await assertions.check('login.session_restored', async () => {
    const me = await page.meStatus()
    expectEqual(me?.status, 200, '/auth/me 状态码')
    expect(me?.body?.username === username, `会话账号不匹配：${JSON.stringify(me?.body)}`)
    ctx.userId = me.body.userId
    return `userId=${me.body.userId}`
  })

  await assertions.check('login.account_page_shows_identity', async () => {
    // 回放 /rules 页头只显示“账号/退出登录”链接，不显示用户名；要验证“页面出现用户名/userId”
    // 必须再看一眼 /account（同一进程内深链，仍使用刚建立的会话）。
    await page.goto(`${options.webUrl}/account`)
    await page.waitFor('location.pathname === "/account"', { label: '账号页未跳转登录' })
    // 账号详情是挂载后异步拉取的：只等路由到位就取 <dl>，读到的必然是“未提供”的初始状态。
    // 先等账号文本出现，超时后仍由下面的 dl 断言报出实际内容，避免把时序问题误判成功能缺陷。
    await page.waitFor(`document.body.innerText.includes(${JSON.stringify(username)})`,
      { label: `账号页未在超时内显示出 ${username}` }).catch(() => {})
    const values = await page.definitionList()
    expect(values, '账号页没有渲染 dl 账号信息')
    const text = await page.text()
    const hasUser = text.includes(username)
    const hasId = ctx.userId ? text.includes(ctx.userId) : true
    expect(hasUser, `账号页未显示用户名 ${username}；dl=${JSON.stringify(values)}`)
    expect(hasId, `账号页未显示 userId ${ctx.userId}`)
    return `dl=${JSON.stringify(values)}`
  })

  return { username, userId: ctx.userId }
}

async function runVerifySession(ctx, { strict }) {
  const { page, assertions, options } = ctx
  const { username, userId } = ctx

  await assertions.check('verify.opens_account', async () => {
    await page.goto(`${options.webUrl}/account`)
    // 未登录时 RuleAccount 的 watch 会 replace 到 /login；等路径稳定再判断。
    await page.waitFor('location.pathname === "/account" || location.pathname === "/login"', { label: '账号页/登录页落点' })
    await sleep(400)
    const path = await page.path()
    expectEqual(path, '/account', '深链 /account 的落点（跳登录页说明持久登录未生效）')
    return `停留在 ${path}`
  })

  await assertions.check('verify.session_cookie_alive', async () => {
    const me = await page.meStatus()
    expectEqual(me?.status, 200, '/auth/me 状态码')
    expect(me?.body?.username === username, `会话账号应为 ${username}，实际 ${JSON.stringify(me?.body)}`)
    if (userId) expectEqual(me.body.userId, userId, '会话 userId')
    return `username=${me.body.username} userId=${me.body.userId}`
  })

  await assertions.check('verify.account_dl_visible', async () => {
    const values = await page.definitionList()
    expect(values, '账号页未渲染 dl 账号信息')
    expectEqual(values['账号'], username, '页面“账号”字段')
    if (userId) expectEqual(values['用户 ID'], userId, '页面“用户 ID”字段')
    if (strict) {
      const text = await page.text()
      expect(text.includes(username), `页面文本未包含用户名 ${username}`)
      if (userId) expect(text.includes(userId), `页面文本未包含 userId ${userId}`)
    }
    return `dl=${JSON.stringify(values)}`
  })

  return { username, userId }
}

async function runLogout(ctx) {
  const { page, assertions, options } = ctx

  await assertions.check('logout.page_logged_in', async () => {
    await page.goto(`${options.webUrl}/account`)
    await page.waitFor('location.pathname === "/account" || location.pathname === "/login"', { label: '账号页落点' })
    await sleep(300)
    expectEqual(await page.path(), '/account', '退出前应处于登录态')
    return '已登录并停在 /account'
  })

  await assertions.check('logout.click', async () => {
    const outcome = await page.logout()
    return `通过 ${outcome.via} 点击“${outcome.text}”`
  })

  await assertions.check('logout.lands_on_login', async () => {
    await page.waitFor('location.pathname === "/login"', { label: '退出后跳转登录页' })
    return '已跳转 /login'
  })

  await assertions.check('logout.protected_routes_redirect', async () => {
    // 退出后访问 /rules 时守卫会调 /auth/me，401 即跳登录；这条断言覆盖“会话已真正失效”。
    await page.goto(`${options.webUrl}/rules`)
    await page.waitFor('location.pathname === "/login" || location.pathname === "/rules"', { label: '/rules 守卫判定' })
    await sleep(600)
    const path = await page.path()
    expectEqual(path, '/login', '退出后访问 /rules 的落点')
    const me = await page.meStatus()
    expect(me?.status === 401, `退出后 /auth/me 应返回 401，实际 ${me?.status}`)
    return `/rules -> /login，/auth/me=${me.status}`
  })

  return {}
}

async function runReplayAfterLogout(ctx) {
  const { page, assertions, options } = ctx

  await assertions.check('replay.account_redirects_to_login', async () => {
    await page.goto(`${options.webUrl}/account`)
    await page.waitFor('location.pathname === "/account" || location.pathname === "/login"', { label: '账号页落点' })
    await sleep(600)
    const path = await page.path()
    expectEqual(path, '/login', '冷启动打开 /account 的落点（应被判定未登录）')
    return `停留在 ${path}`
  })

  await assertions.check('replay.persistent_cookie_revoked', async () => {
    const me = await page.meStatus()
    expect(me?.status === 401, `/auth/me 应返回 401，实际 ${me?.status}`)
    return '/auth/me=401'
  })

  return {}
}

// ---------------------------------------------------------------------------
// 自检阶段：不依赖后端，用脚本内置的合成页面验证 CDP 全链路
// ---------------------------------------------------------------------------
const FIXTURE_HTML = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>persistent-login self-check fixture</title></head>
<body>
<main class="account-card">
  <h1>账号信息（合成夹具）</h1>
  <dl><dt>账号</dt><dd id="login-name">未提供</dd><dt>用户 ID</dt><dd id="user-id">未提供</dd></dl>
  <form><label for="account-username">账号</label><input id="account-username" autocomplete="username" />
    <label for="account-password">密码</label><input id="account-password" type="password" />
    <p class="feedback" role="status" aria-live="polite"></p>
    <button type="submit">登录</button></form>
  <button data-testid="logout" type="button">退出登录</button>
</main>
<script>
document.querySelector('form').addEventListener('submit', event => {
  event.preventDefault();
  var user = document.querySelector('#account-username').value;
  document.querySelector('#login-name').textContent = user;
  document.querySelector('#user-id').textContent = '00000000-0000-4000-8000-000000000000';
  document.querySelector('.feedback').textContent = '合成夹具：已提交 ' + user;
  history.pushState({}, '', '/account');
});
document.querySelector('[data-testid="logout"]').addEventListener('click', () => {
  document.querySelector('.feedback').textContent = '合成夹具：已退出';
  history.pushState({}, '', '/login');
});
</script>
</body></html>`

/**
 * 真实 Vue 3 v-model 夹具：用仓库里已存在的 vue.global.prod.js（node_modules 自带，无需安装），
 * 目的是证明 fill() 派发的 input 事件确实驱动了组件状态，而不只是改了 DOM 的 value 属性。
 * 这一差别在持久登录验收里很致命：v-model 若没更新，提交上去的会是空用户名/空密码。
 */
const VUE_FIXTURE_HTML = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>v-model self-check</title></head>
<body><div id="app">
  <form @submit.prevent="submit">
    <label for="account-username">账号</label><input id="account-username" v-model="username" />
    <label for="account-password">密码</label><input id="account-password" type="password" v-model="password" />
    <label class="remember"><input type="checkbox" v-model="remember" /> 保持登录</label>
    <button type="submit">登录</button>
  </form>
  <dl><dt>账号</dt><dd id="state-username">{{ username || '未提供' }}</dd>
      <dt>用户 ID</dt><dd id="state-password">{{ password ? password.length : 0 }}</dd>
      <dt>记住</dt><dd id="state-remember">{{ remember }}</dd></dl>
</div>
<script src="/vue.global.prod.js"></script>
<script>
const { createApp, ref } = Vue;
createApp({
  setup() {
    const username = ref(''); const password = ref(''); const remember = ref(true);
    const submit = () => { document.title = 'submitted:' + username.value + ':' + password.value + ':' + remember.value };
    return { username, password, remember, submit };
  }
}).mount('#app');
</script></body></html>`

async function startFixtureServer() {
  // Vue 直接从仓库依赖里读，避免联网；不存在时 /vue.global.prod.js 返回 404，
  // self-check-vue 阶段会以断言失败的形式如实暴露，而不是假装通过。
  let vueBundle = null
  try {
    vueBundle = await readFile(path.join(REPO_ROOT, 'agent/frontend/node_modules/vue/dist/vue.global.prod.js'))
  } catch { vueBundle = null }

  const server = createHttpServer((request, response) => {
    if (request.url?.startsWith('/vue.global.prod.js')) {
      if (!vueBundle) { response.writeHead(404); response.end('vue bundle not found'); return }
      response.writeHead(200, { 'Content-Type': 'application/javascript; charset=utf-8', 'Cache-Control': 'no-store' })
      response.end(vueBundle)
      return
    }
    const html = request.url?.startsWith('/vue-fixture') ? VUE_FIXTURE_HTML : FIXTURE_HTML
    response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
    response.end(html)
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const { port } = server.address()
  return { server, url: `http://127.0.0.1:${port}`, port }
}

/** 验证 v-model 真的被输入事件驱动；这是 fill() 在真实页面上生效的前提。 */async function runSelfCheckVue(ctx) {
  const { page, assertions, fixture } = ctx

  await assertions.check('vue.fixture_loaded', async () => {
    await page.goto(`${fixture.url}/vue-fixture`, { waitForApp: false })
    await page.waitFor('!!document.querySelector("#account-username")', { timeoutMs: 8000, label: 'Vue 夹具挂载' })
    const mounted = await page.evaluate('!!document.querySelector("#state-username")')
    expect(mounted, 'Vue 夹具未渲染（vue.global.prod.js 可能缺失）')
    return 'vue.global.prod.js 已加载并挂载'
  })

  await assertions.check('vue.vmodel_updated_by_input_event', async () => {
    await page.fill('#account-username', 'vue-model-user')
    // 关键：v-model 若没收到 input 事件，这里仍是“未提供”。
    const rendered = await page.evaluate('document.querySelector("#state-username").textContent')
    expectEqual(rendered, 'vue-model-user', 'v-model 驱动的渲染值')
    return 'v-model 状态已随 input 事件更新'
  })

  await assertions.check('vue.password_length_visible', async () => {
    const password = 'Vue-Model-Password-16'
    await page.fill('#account-password', password)
    const length = await page.evaluate('document.querySelector("#state-password").textContent')
    expectEqual(Number(length), password.length, 'v-model 密码长度')
    return `密码长度 ${length}`
  })

  await assertions.check('vue.checkbox_checked_by_ensureChecked', async () => {
    const outcome = await page.ensureChecked('label.remember input[type=checkbox]', true)
    expect(outcome.checked === true, '确保勾选后仍为 false')
    expectEqual(await page.evaluate('document.querySelector("#state-remember").textContent'), 'true', 'v-model 勾选状态')
    return '勾选框驱动 v-model 为 true'
  })

  await assertions.check('vue.submit_reads_component_state', async () => {
    await page.submitForm()
    await page.waitFor('document.title.startsWith("submitted:")', { timeoutMs: 5000, label: '提交处理生效' })
    const title = await page.evaluate('document.title')
    // 提交处理函数读取的是 ref 的值，能证明整条“DOM 事件 -> 组件状态 -> 提交”链路成立。
    expect(title.includes('vue-model-user'), `提交读取到的账号不含期望值：${title}`)
    expect(title.includes('Vue-Model-Password-16'), `提交读取到的密码不含期望值：${title}`)
    expect(title.endsWith(':true'), `提交读取到的保持登录不是 true：${title}`)
    return title
  })
  return {}
}

/**
 * profile 持久化自检（写入）：向夹具域写一个持久 Cookie。
 * 这是整套验收的地基假设——"同一个 --user-data-dir、新进程仍带旧 Cookie"。
 * 若这一条不成立，register/login 之后的 verify-session 失败就是环境问题而不是登录缺陷，
 * 所以必须先用两个独立进程把这条机制单独证明出来。
 */
async function runSelfCheckProfileWrite(ctx) {
  const { page, assertions, fixture } = ctx
  const cookieName = 'self_check_persistent'

  await assertions.check('profile.cookie_written', async () => {
    await page.goto(`${fixture.url}/`, { waitForApp: false })
    const value = `value-${Date.now()}`
    await page.evaluate(`(() => {
      document.cookie = ${JSON.stringify(cookieName)} + '=' + ${JSON.stringify(value)} + '; path=/; max-age=86400; SameSite=Lax';
      return document.cookie.includes(${JSON.stringify(cookieName)});
    })()`)
    ctx.cookieValue = value
    const visible = await page.evaluate('document.cookie')
    expect(visible.includes(cookieName), `Cookie 未写入：${visible}`)
    return `${cookieName}=${value}`
  })

  await assertions.check('profile.cookie_survives_refresh', async () => {
    await page.goto(`${fixture.url}/`, { waitForApp: false })
    const visible = await page.evaluate('document.cookie')
    expect(visible.includes(ctx.cookieValue), `刷新后 Cookie 丢失：${visible}`)
    return '同进程刷新后仍存在'
  })
  return { cookieValue: ctx.cookieValue }
}

/**
 * profile 持久化自检（读取）：全新 Chrome 进程、同一 --user-data-dir，
 * 断言上一步写入的持久 Cookie 还在。两个阶段必须由外部按顺序调用。
 */
async function runSelfCheckProfileRead(ctx) {
  const { page, assertions, fixture } = ctx
  const cookieName = 'self_check_persistent'

  await assertions.check('profile.cookie_survives_process_restart', async () => {
    await page.goto(`${fixture.url}/`, { waitForApp: false })
    const visible = await page.evaluate('document.cookie')
    expect(visible.includes(cookieName), `新进程未恢复持久 Cookie，实际 document.cookie=${JSON.stringify(visible)}；`
      + '若非空 profile 仍丢失，说明 --user-data-dir 未生效或 Chrome 未正常退出导致 Cookie 未落盘')
    return `新进程读到 ${cookieName}`
  })

  await assertions.check('profile.cookie_value_matches_state', async () => {
    const expected = ctx.state.cookieValue
    if (!expected) return '未提供 state.cookieValue，跳过取值比对'
    const visible = await page.evaluate('document.cookie')
    expect(visible.includes(expected), `Cookie 取值不匹配：期望包含 ${expected}，实际 ${visible}`)
    return `取值匹配 ${expected}`
  })

  // 清掉自检 Cookie，避免污染后续真实验收（真实 Cookie 走 ZHKU_RULE_CHECK_REMEMBER）。
  await page.evaluate(`(() => { document.cookie = ${JSON.stringify(cookieName)} + '=; path=/; max-age=0'; return true })()`)
  return {}
}

async function runSelfCheck(ctx, { deliberateFailure }) {
  const { page, assertions, options, outPath } = ctx
  const fixture = ctx.fixture

  assertions.pass('cdp.browser_version', `Chrome ${ctx.browserVersion}`)
  assertions.pass('cdp.target_attached', `sessionId=${ctx.sessionId.slice(0, 12)}…`)

  await assertions.check('cdp.evaluate_works', async () => {
    const value = await page.evaluate('1 + 1')
    expectEqual(value, 2, 'Runtime.evaluate 返回值')
    return '1 + 1 = 2'
  })

  await assertions.check('cdp.navigate_fixture', async () => {
    await page.goto(`${fixture.url}/`, { waitForApp: false })
    const href = await page.url()
    expect(href.startsWith(fixture.url), `当前 URL ${href} 不属于夹具服务器 ${fixture.url}`)
    return href
  })

  await assertions.check('dom.fill_inputs', async () => {
    await page.fill('#account-username', 'self-check-user')
    await page.fill('#account-password', 'self-check-password-16')
    expectEqual(await page.readValue('#account-username'), 'self-check-user', '账号输入框值')
    return '输入框已通过原生 setter + input/change 事件写入'
  })

  await assertions.check('dom.submit_updates_page', async () => {
    await page.submitForm()
    await page.waitFor('location.pathname === "/account"', { timeoutMs: 5000, label: '夹具 form submit 生效' })
    const values = await page.definitionList()
    expectEqual(values?.['账号'], 'self-check-user', '夹具 dl 账号字段')
    return `dl=${JSON.stringify(values)}`
  })

  await assertions.check('screenshot.written', async () => {
    const file = path.join(path.dirname(outPath), 'self-check.png')
    await page.screenshot(file)
    const info = await stat(file)
    expect(info.size > 0, `截图文件为空：${file}`)
    ctx.screenshotPath = file
    return `${file}（${info.size} 字节）`
  })

  await assertions.check('assertions.failure_is_recorded_not_thrown', async () => {
    // 用一个临时 Assertions 实例验证失败路径：assert 必须记录 ok=false 且不抛出。
    const probe = new Assertions()
    await probe.check('deliberate-probe', async () => { throw new Error('预期的探测失败') })
    expect(probe.items.length === 1 && probe.items[0].ok === false, '失败断言没有被记录为 ok=false')
    expect(probe.ok === false, 'Assertions.ok 未反映失败')
    return `记录到：${probe.items[0].detail}`
  })

  if (deliberateFailure) {
    // 这个阶段故意失败：用来证明退出码 1、失败详情打印、截图与 HTML 落盘都真的工作。
    await assertions.check('deliberate_failure_path', async () => {
      throw new Error('这是 self-check-fail 阶段故意制造的失败，用于验证失败上报链路')
    })
  }
  return {}
}

// ---------------------------------------------------------------------------
// 主流程
// ---------------------------------------------------------------------------
/**
 * `--phase all`：把六个真实阶段依次作为独立子进程跑完。
 * 必须真的另起进程（而不是函数调用），因为持久登录的验证点就是“Chrome 关掉再打开”；
 * 同进程内复用实例证明不了持久化。
 */
async function runAllPhases(options, argv) {
  const results = []
  let allOk = true
  for (const phase of REAL_PHASES) {
    process.stdout.write(`\n=== 阶段 ${phase} ===\n`)
    const childArgs = [fileURLToPath(import.meta.url), '--phase', phase]
    // 只透传显式给出的参数，避免把默认值写死，也避免把 --phase all 传下去。
    for (let i = 0; i < argv.length; i++) {
      const arg = argv[i]
      if (!['--web-url', '--chrome', '--profile-dir', '--username', '--password', '--state', '--headless', '--timeout', '--repo-root'].includes(arg)) continue
      childArgs.push(arg, argv[i + 1]); i++
    }
    const code = await new Promise(resolve => {
      const child = spawn(process.execPath, childArgs, { stdio: 'inherit', windowsHide: true })
      child.on('exit', value => resolve(value ?? 1))
    })
    results.push({ phase, exitCode: code, ok: code === 0 })
    if (code !== 0) {
      allOk = false
      // 某一阶段失败后继续跑会污染后续阶段（例如 login 失败则 verify 必然失败），
      // 所以立刻停下，让 Lead 先看第一处真实缺陷。
      process.stdout.write(`\n阶段 ${phase} 失败（exit ${code}），已停止后续阶段。\n`)
      break
    }
  }
  const summaryPath = path.resolve(options.repoRoot, 'tmp/persistent-login-browser/all.json')
  await mkdir(path.dirname(summaryPath), { recursive: true })
  await writeFile(summaryPath, `${JSON.stringify({ ok: allOk, phases: results, finishedAt: new Date().toISOString() }, null, 2)}\n`)
  process.stdout.write(`\n汇总：${allOk ? 'PASS' : 'FAIL'}；明细 ${path.relative(process.cwd(), summaryPath)}\n`)
  process.exitCode = allOk ? 0 : 1
}

async function main() {
  let options
  try { options = parseArgs(process.argv) }
  catch (error) {
    process.stderr.write(`${error.message}\n\n${usage()}\n`)
    process.exitCode = 2
    return
  }
  if (options.help) { process.stdout.write(`${usage()}\n`); return }
  if (options.phase === 'all') { await runAllPhases(options, process.argv.slice(2)); return }
  if (!options.phase || !PHASES.includes(options.phase)) {
    process.stderr.write(`--phase 必须是以下之一：${PHASES.join(', ')}\n\n${usage()}\n`)
    process.exitCode = 2
    return
  }

  const outPath = path.resolve(options.repoRoot, options.out || `tmp/persistent-login-browser/${options.phase}.json`)
  const statePath = path.resolve(options.repoRoot, options.statePath)
  await mkdir(path.dirname(outPath), { recursive: true })

  // 跨阶段账号：register 生成、login 复用、verify 阶段从 state 文件取（密码不落盘，按规则重算）。
  let state = {}
  if (await exists(statePath)) {
    try { state = JSON.parse(await readFile(statePath, 'utf8')) } catch { state = {} }
  }
  const username = options.username || state.username || defaultUsername()
  const password = options.password || passwordFor(username)
  const userId = options.userId || state.userId || null

  const assertions = new Assertions()
  const consoleErrors = []
  const startedAt = new Date().toISOString()
  let chrome = null
  let cdp = null
  let fixture = null
  let phaseResult = {}
  let crash = null

  try {
    // 自检阶段需要一个本地夹具服务器；真实阶段不启动任何服务器（由 Lead 起 nginx/后端）。
    if (SELF_CHECK_PHASES.includes(options.phase)) fixture = await startFixtureServer()
    else await preflightWeb(options)

    chrome = await launchChrome(options)
    cdp = await CdpClient.connect(chrome.wsUrl, { timeoutMs: options.timeoutMs })

    const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' })
    const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true })
    await cdp.send('Page.enable', {}, sessionId)
    await cdp.send('Runtime.enable', {}, sessionId)
    await cdp.send('Network.enable', {}, sessionId)
    await cdp.send('Log.enable', {}, sessionId)

    // 控制台错误与未捕获异常是“页面其实坏了但断言碰巧过”的主要噪声源，单独收集进 JSON。
    const apiRequests = new Map()
    cdp.on('Runtime.consoleAPICalled', (params, fromSession) => {
      if (fromSession !== sessionId || params.type !== 'error') return
      consoleErrors.push(`console.error: ${params.args.map(arg => arg.value ?? arg.description ?? arg.type).join(' ')}`)
    })
    cdp.on('Runtime.exceptionThrown', (params, fromSession) => {
      if (fromSession !== sessionId) return
      const details = params.exceptionDetails
      consoleErrors.push(`uncaught: ${details?.exception?.description || details?.text || '未知异常'}`)
    })
    cdp.on('Log.entryAdded', (params, fromSession) => {
      if (fromSession !== sessionId || params.entry?.level !== 'error') return
      consoleErrors.push(`log.error: ${params.entry.text}`)
    })
    cdp.on('Network.requestWillBeSent', (params, fromSession) => {
      if (fromSession !== sessionId) return
      if (params.request?.url?.includes(API_PREFIX)) apiRequests.set(params.requestId, params.request.url)
    })
    cdp.on('Network.loadingFailed', (params, fromSession) => {
      if (fromSession !== sessionId || !apiRequests.has(params.requestId)) return
      consoleErrors.push(`network.failed: ${apiRequests.get(params.requestId)} -> ${params.errorText}`)
    })

    const page = new Page(cdp, sessionId, options)
    await page.waitFor('document.readyState === "complete"', { label: '初始空白页就绪' })

    const ctx = {
      page, assertions, options, outPath, sessionId,
      browserVersion: chrome.version?.Browser || 'unknown',
      username, password, userId, fixture, state
    }

    switch (options.phase) {
      case 'register': phaseResult = await runRegister(ctx); break
      case 'login': phaseResult = await runLogin(ctx); break
      case 'verify-session': phaseResult = await runVerifySession(ctx, { strict: true }); break
      case 'direct-account': phaseResult = await runVerifySession(ctx, { strict: false }); break
      case 'logout': phaseResult = await runLogout(ctx); break
      case 'replay-after-logout': phaseResult = await runReplayAfterLogout(ctx); break
      case 'self-check': phaseResult = await runSelfCheck(ctx, { deliberateFailure: false }); break
      case 'self-check-fail': phaseResult = await runSelfCheck(ctx, { deliberateFailure: true }); break
      case 'self-check-vue': phaseResult = await runSelfCheckVue(ctx); break
      case 'self-check-profile-write': phaseResult = await runSelfCheckProfileWrite(ctx); break
      case 'self-check-profile-read': phaseResult = await runSelfCheckProfileRead(ctx); break
      default: throw new Error(`未实现的阶段：${options.phase}`)
    }

    // URL 与 HTML 必须在关浏览器之前取：Browser.close 之后执行上下文已销毁，evaluate 一定失败。
    ctx.finalUrl = await safeUrl(page)
    ctx.finalHtml = assertions.ok ? '' : await safeHtml(page)

    if (!assertions.ok) {
      // 失败时先截图再取 HTML：截图必须在页面还停留在失败现场时拍。
      const shot = path.join(path.dirname(outPath), `${options.phase}.png`)
      try { ctx.screenshotPath = await page.screenshot(shot) } catch (error) {
        consoleErrors.push(`screenshot.failed: ${error.message}`)
      }
    }
    ctx.crash = null

    // 结束前把浏览器关掉，下一阶段才是真正的“关窗重开”，同时释放调试端口。
    try {
      await cdp.send('Browser.close')
      await stopChrome(chrome.child)
    } catch (error) { consoleErrors.push(`browser.close.failed: ${error.message}`) }

    // 跨阶段状态：账号与 userId 由 register/login 落盘，供后续独立进程复用（密码不落盘）。
    if (assertions.ok && REAL_PHASES.includes(options.phase)) {
      const nextState = { ...state, username, userId: ctx.userId || state.userId || null, phase: options.phase, updatedAt: new Date().toISOString() }
      await writeFile(statePath, `${JSON.stringify(nextState, null, 2)}\n`)
    }
    if (assertions.ok && options.phase === 'self-check-profile-write' && ctx.cookieValue) {
      await writeFile(statePath, `${JSON.stringify({ ...state, cookieValue: ctx.cookieValue, phase: options.phase }, null, 2)}\n`)
    }

    const result = {
      phase: options.phase,
      ok: assertions.ok,
      url: ctx.finalUrl || '',
      assertions: assertions.items,
      consoleErrors,
      ...(ctx.screenshotPath ? { screenshotPath: path.relative(options.repoRoot, ctx.screenshotPath).replaceAll('\\', '/') } : {}),
      ...(assertions.ok ? {} : { html: ctx.finalHtml || '' }),
      meta: { startedAt, username, passwordProvided: Boolean(options.password || state.username), profileDir: chrome.profileDir, chrome: chrome.version?.Browser }
    }
    await writeFile(outPath, `${JSON.stringify(result, null, 2)}\n`)
    report(result, outPath)
    process.exitCode = assertions.ok ? 0 : 1
  } catch (error) {
    crash = error
    const result = {
      phase: options.phase,
      ok: false,
      url: '',
      assertions: assertions.items,
      consoleErrors: [...consoleErrors, `phase.error: ${error.message}`],
      html: '',
      meta: { startedAt, username, crash: error.stack || String(error) }
    }
    if (cdp) {
      try { await cdp.send('Browser.close') } catch { /* 已断开 */ }
    }
    if (chrome) {
      try { await stopChrome(chrome.child) } catch { /* 已退出 */ }
    }
    await writeFile(outPath, `${JSON.stringify(result, null, 2)}\n`)
    report(result, outPath)
    process.exitCode = 1
  } finally {
    try { cdp?.close() } catch { /* 忽略 */ }
    fixture?.server.close()
    if (!crash && !assertions.ok) process.exitCode = 1
  }
}

async function safeUrl(page) {
  try { return await page.url() } catch { return '' }
}

async function safeHtml(page) {
  try {
    const html = await page.evaluate('document.documentElement ? document.documentElement.outerHTML : ""')
    return String(html).slice(0, 4000)
  } catch (error) {
    return `（读取 HTML 失败：${error.message}）`
  }
}

function report(result, outPath) {
  const lines = []
  lines.push(`[phase ${result.phase}] ${result.ok ? 'PASS' : 'FAIL'}  url=${result.url || '（未取得）'}`)
  for (const item of result.assertions) lines.push(`  ${item.ok ? 'PASS' : 'FAIL'}  ${item.name}  ${item.detail}`)
  if (result.consoleErrors?.length) {
    lines.push('  consoleErrors:')
    for (const entry of result.consoleErrors) lines.push(`    - ${entry}`)
  }
  if (result.screenshotPath) lines.push(`  screenshot: ${result.screenshotPath}`)
  lines.push(`  json: ${path.relative(process.cwd(), outPath)}`)
  process.stdout.write(`${lines.join('\n')}\n`)
}

await main()
