// T07 两窗口身份失效验收工具（attach-only）。
//
// 设计前提：本工具**绝不启动任何应用宿主**（不起 Java、不起 nginx、不起数据库）。
// 它只附加到验收宿主已经提供的 base URL，因此天然满足「等待宿主提供稳定提交及退出方式、
// 不绕过退出卡点」的约束：宿主没就位就跑不了，而不是先跑起来再补退出证据。
//
// 两种模式：
//   --self-check            只验证本工具自身与浏览器机制（自有静态夹具页 + 自有端口），
//                           不含应用集成；用于在宿主就位前证明工具可用与退出可证。
//   --base-url <url>        对真实宿主执行 A/B 两窗口验收（需宿主与测试身份）。
//
// 用法（playwright-core 装在托管 node 工作区，ESM 下用 createRequire + NODE_PATH 解析）：
//   NODE_PATH=<node工作区>/node_modules node two-window-acceptance.mjs --self-check --out <json>
//   ZHKU_ACCEPTANCE_USERNAME=<测试账号> ZHKU_ACCEPTANCE_PASSWORD=<测试口令> \
//   NODE_PATH=... node two-window-acceptance.mjs --base-url http://127.0.0.1:<port> --out <json>
//
// 纪律：口令只从环境变量读取、绝不打印；只使用本工具自有的端口与浏览器进程；
// 结束时按 PID 核对浏览器与夹具端口真实退出。

import http from 'node:http'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import { execFileSync } from 'node:child_process'

const require = createRequire(import.meta.url)
function loadPlaywright() {
  const candidates = [
    process.env.NODE_PATH,
    'C:/Users/nangua/.workbuddy/binaries/node/workspace/node_modules',
    new URL('./node_modules', import.meta.url).pathname
  ].filter(Boolean)
  for (const base of candidates) {
    try { return require(require.resolve('playwright-core', { paths: [base] })) } catch { /* 试下一个 */ }
  }
  throw new Error('找不到 playwright-core；请设置 NODE_PATH 指向托管 node 工作区的 node_modules')
}

const args = process.argv.slice(2)
const flag = name => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : null }
const SELF_CHECK = args.includes('--self-check')
const BASE_URL = flag('--base-url')
const OUT = flag('--out')

const FIXTURE_PORT = 8151
const FIXTURE_HTML = `<!doctype html><meta charset="utf-8"><title>t07-selfcheck</title>
<body><p id="mark">selfcheck</p>
<script>
// 与身份通道同构：一次广播、接收端不改写、不回投。
window.__received = [];
window.__rebroadcast = 0;
const channel = new BroadcastChannel('t07-selfcheck');
channel.onmessage = event => { window.__received.push(event.data); if (event.data && event.data.relay) { window.__rebroadcast++; channel.postMessage({ relay: true }) } };
window.__send = value => channel.postMessage(value);
</script></body>`

// 返回该端口当前是否**可以绑定**（即没人监听）。绑定成功即为空闲。
function portFree(port) {
  return new Promise(resolve => {
    const probe = http.createServer(() => {})
    probe.once('error', () => resolve(false))
    probe.once('listening', () => probe.close(() => resolve(true)))
    probe.listen(port, '127.0.0.1')
  })
}

async function startFixture() {
  const server = http.createServer((request, response) => {
    response.setHeader('Content-Type', 'text/html; charset=utf-8')
    response.end(FIXTURE_HTML)
  })
  await new Promise(resolve => server.listen(FIXTURE_PORT, '127.0.0.1', resolve))
  return server
}

// 退出核对只统计**本工具拉起的**浏览器进程：按命令行特征（headless / playwright 临时 profile）归属，
// 而不是"运行期间新出现的任意 msedge"。本机用户自己的 Edge 会随时开关标签页，
// 实测一次被误判成泄漏的两个 PID 全带 --lang=zh-CN、无 headless/playwright 特征。
function edgePids() {
  const script = "Get-CimInstance Win32_Process -Filter \"Name='msedge.exe'\" | Where-Object { $_.CommandLine -match 'headless|playwright' } | ForEach-Object { $_.ProcessId }"
  try {
    const out = execFileSync('powershell', ['-NoProfile', '-NonInteractive', '-Command', script], { encoding: 'utf8' })
    return new Set(out.split(/\r?\n/).map(line => line.trim()).filter(Boolean))
  } catch { return new Set() }
}
const leaked = baseline => [...edgePids()].filter(pid => !baseline.has(pid))
async function waitForExit(baseline, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs
  for (;;) {
    const remaining = leaked(baseline)
    if (!remaining.length) return []
    if (Date.now() > deadline) return remaining
    await new Promise(resolve => setTimeout(resolve, 500))
  }
}

function report(record) {
  if (OUT) fs.writeFileSync(OUT, JSON.stringify(record, null, 2))
  return record
}

async function selfCheck() {
  const { chromium } = loadPlaywright()
  if (!(await portFree(FIXTURE_PORT))) throw new Error(`夹具端口 ${FIXTURE_PORT} 已被占用，拒绝在他人监听上做自检`)
  const server = await startFixture()
  const record = { mode: 'self-check', executionMode: 'TEST_SIMULATION_NO_PROVIDER', providerCalls: 0, databaseCalls: 0, startedHost: false, baseUrl: null, fixturePort: FIXTURE_PORT, checks: {}, unverified: [] }
  const baseline = edgePids()
  record.edgePidsBeforeLaunch = baseline.size
  let browser = null
  try {
    browser = await chromium.launch({ channel: 'msedge' })
    const context = await browser.newContext({ viewport: { width: 1200, height: 800 } })
    const externals = []
    const origin = `http://127.0.0.1:${FIXTURE_PORT}`
    const pageA = await context.newPage(), pageB = await context.newPage()
    for (const page of [pageA, pageB]) {
      page.on('request', request => { if (!request.url().startsWith(origin)) externals.push(request.url()) })
      await page.goto(origin, { waitUntil: 'load' })
    }
    // 1) 同一浏览器上下文的两个标签页共享 BroadcastChannel（身份通道依赖的前提）。
    await pageB.evaluate(() => window.__send({ from: 'B' }))
    await pageA.waitForFunction(() => window.__received.length > 0, null, { timeout: 5000 })
    record.checks.twoTabsShareChannel = (await pageA.evaluate(() => window.__received))[0]?.from === 'B'
    // 2) 接收端收到后不会回投（对应 identity-bus 的回声抑制）。
    await pageA.evaluate(() => window.__send({ from: 'A', relay: false }))
    await pageB.waitForFunction(() => window.__received.length > 0, null, { timeout: 5000 })
    record.checks.noEcho = (await pageB.evaluate(() => window.__rebroadcast)) === 0
    await pageA.waitForTimeout(300)
    record.checks.receiverDidNotAmplify = (await pageA.evaluate(() => window.__received.length)) === 1
    record.externalRequests = externals
    await context.close()
  } finally {
    if (browser) await browser.close()
  }
  // 3) 退出可证：本次新增的 msedge 进程与夹具端口都必须真实消失，不能只看命令返回。
  const stragglers = await waitForExit(baseline)
  record.edgePidsLeaked = stragglers
  record.checks.browserExitVerified = stragglers.length === 0
  await new Promise(resolve => server.close(resolve))
  record.checks.fixturePortReleased = await portFree(FIXTURE_PORT)
  record.unverified.push('应用集成（未附加任何宿主）', '登录/退出/任务结果与迟到响应', '真实身份与资料')
  return report(record)
}

async function attachedRun() {
  if (!BASE_URL) throw new Error('需要 --base-url 指向验收宿主；本工具不会自行启动宿主')
  const username = process.env.ZHKU_ACCEPTANCE_USERNAME, password = process.env.ZHKU_ACCEPTANCE_PASSWORD
  if (!username || !password) throw new Error('需要 ZHKU_ACCEPTANCE_USERNAME / ZHKU_ACCEPTANCE_PASSWORD（值不会被记录）')
  const { chromium } = loadPlaywright()
  const reachable = await fetch(BASE_URL, { method: 'GET' }).then(r => r.ok || r.status < 500).catch(() => false)
  if (!reachable) throw new Error(`拒绝执行：${BASE_URL} 不可达。本工具不启动宿主，请先由验收方提供宿主`)
  const record = { mode: 'attached', baseUrl: BASE_URL, executionMode: 'TEST_SIMULATION_NO_PROVIDER', startedHost: false, checks: {}, steps: [], unverified: [] }
  const baseline = edgePids()
  record.edgePidsBeforeLaunch = baseline.size
  let browser = null
  try {
    browser = await chromium.launch({ channel: 'msedge' })
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
    const pageA = await context.newPage(), pageB = await context.newPage()
    // 同一上下文里后开的标签页会成为前台，另一个进入后台：Chromium 会限制后台页的渲染，
    // 可见性类断言会失败。每次操作前把目标页切到前台（这仍然是同上下文的两个窗口）。
    const focus = async page => { await page.bringToFront() }
    // 同源 Cookie 会话在两个窗口间共享：后开的窗口通常**已经带身份**，不会出现登录表单，
    // 因此这里必须幂等——先看是查证台还是登录区，只有真的未登录才走表单。
    const enterRuleCheck = async page => {
      await focus(page)
      await page.goto(`${BASE_URL}/rules`, { waitUntil: 'domcontentloaded' })
      await page.waitForSelector('#rule-question, #username', { timeout: 25000 })
      if (await page.locator('#username').count()) {
        await page.waitForSelector('#username:not([disabled])', { timeout: 20000 })
        await page.fill('#username', username)
        await page.fill('#password', password)
        await page.click('button[type=submit]')
      }
      await page.waitForSelector('#rule-question', { timeout: 25000 })
    }
    // A、B 同身份（同一 Cookie 会话）。
    await enterRuleCheck(pageA); record.steps.push('A in rule-check workbench')
    await enterRuleCheck(pageB); record.steps.push('B in rule-check workbench')
    // A 取得已发布结果。
    await focus(pageA)
    await pageA.fill('#rule-question', '合成问题：本工具的两窗口验收')
    await pageA.click('button[type=submit]')
    await pageA.waitForSelector('#decision-title', { timeout: 60000 })
    record.checks.aHadPublishedResult = true
    record.steps.push('A obtained a published result')
    // B 退出（换身份/退出的等价触发）。
    await focus(pageB)
    await pageB.click('button:has-text("退出登录")')
    await pageB.waitForSelector('#rule-login-title', { timeout: 20000 })
    record.steps.push('B logged out')
    // 另一窗口必须撤销展示，而不是等下一次响应。
    await focus(pageA)
    await pageA.waitForSelector('#rule-login-title', { timeout: 10000 })
    record.checks.otherWindowRevokedDisplay = (await pageA.locator('#decision-title').count()) === 0
    record.checks.revocationMessageShown = await pageA.getByText('登录状态已在其他窗口变更').count() > 0
    // 迟到响应不得复活旧结果：留出观察窗口后仍须保持已撤销。
    await focus(pageA)
    await pageA.waitForTimeout(5000)
    record.checks.lateResponseDidNotRevive = (await pageA.locator('#decision-title').count()) === 0
    // 未获后端确认前不能再提交。
    record.checks.cannotSubmitWithoutConfirmedIdentity = (await pageA.locator('#rule-question').count()) === 0
    // 经后端确认的新身份之后才允许继续。
    await enterRuleCheck(pageA)
    record.checks.resumesOnlyAfterBackendConfirmation = (await pageA.locator('#rule-question').count()) === 1
    record.steps.push('A re-authenticated and can query again')
    await context.close()
  } finally {
    if (browser) await browser.close()
  }
  const stragglers = await waitForExit(baseline)
  record.edgePidsLeaked = stragglers
  record.checks.browserExitVerified = stragglers.length === 0
  if (!record.checks.browserExitVerified) record.unverified.push('浏览器进程退出未获证')
  record.unverified.push('宿主自身的退出（由宿主方按其稳定退出方式核对）', '真实供应商（本轮零调用）')
  return report(record)
}

const main = SELF_CHECK ? selfCheck : attachedRun
main()
  .then(record => {
    const failed = Object.entries(record.checks).filter(([, value]) => value !== true)
    console.log(JSON.stringify({ ok: failed.length === 0, mode: record.mode, checks: record.checks, failed: failed.map(([key]) => key) }))
    process.exit(failed.length === 0 ? 0 : 1)
  })
  .catch(error => { console.error(`FAILED: ${error.message}`); process.exit(2) })
