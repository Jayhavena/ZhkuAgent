// E02 单题真实在线验收的浏览器驱动：只驱动学生真实页面，不直接发业务 POST，不读取密钥。
// 无安装依赖；协议原包只在内存，持久证据仅保存白名单字段。
import {spawn} from 'node:child_process';
import {mkdir, mkdtemp, readFile, writeFile, stat, rm, realpath} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {fileURLToPath} from 'node:url';
import {randomBytes} from 'node:crypto';

export function syntheticLogin(fixture) {
  const password = fixture.match(/static\s+final\s+String\s+CORRECT\s*=\s*"([^"]+)"/)?.[1];
  assert(password, 'E02 synthetic fixture credential definition missing');
  return {username: 't06-chain-a', password};
}

export async function probeSessionNetwork(evaluate) {
  // 同页真实传输探测：HTTP 错误仍表示可达；超时不是网络不可达的证据。
  return evaluate(`(async()=>{try{const r=await fetch('/api/zhku/controlled-preview/v2/auth/me',{cache:'no-store',signal:AbortSignal.timeout(2500)});return {reachable:true,status:r.status};}catch(e){if(e instanceof TypeError)return {reachable:false,reason:'NETWORK_ERROR'};throw e;}})()`);
}

export class CDP {
  constructor(url) { this.ws = new WebSocket(url); this.pending = new Map(); this.next = 0; this.handlers = []; this.ws.addEventListener('message', event => { const data = JSON.parse(String(event.data)); if (data.id) { const p = this.pending.get(data.id); if (p) { clearTimeout(p.timer); this.pending.delete(data.id); data.error ? p.reject(new Error('CDP ' + data.error.code + ': ' + data.error.message)) : p.resolve(data.result); } } else for (const handler of this.handlers) handler(data); }); }
  async open() { if (this.ws.readyState !== 1) await new Promise((resolve, reject) => { this.ws.addEventListener('open', resolve, {once: true}); this.ws.addEventListener('error', () => reject(new Error('Local CDP WebSocket connection failed')), {once: true}); }); }
  send(method, params = {}, sessionId) { const id = ++this.next; return new Promise((resolve, reject) => { const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('CDP timeout: ' + method)); }, method === 'Page.captureScreenshot' ? 30000 : 10000); this.pending.set(id, {resolve, reject, timer}); this.ws.send(JSON.stringify({id, method, params, ...(sessionId ? {sessionId} : {})})); }); }
  close() { this.ws.close(); for (const p of this.pending.values()) { clearTimeout(p.timer); p.reject(new Error('CDP closed')); } this.pending.clear(); }
}

// 学生页面在 V3 改版后首页输入框是 #question-home、对话态是 #question-next；
// 旧的 #rule-question 只剩回放入口使用。驱动按“学生入口优先、回放兜底”顺序探测，
// 避免改版后驱动静默找不到提问入口而把页面误判成登录失败。
// 登录按钮同样按新旧两套表单选择器探测：离线合成页用的是 .rule-login。
export const QUESTION_SELECTORS = ['#question-home', '#question-next', '#rule-question'];
export const QUESTION_READY_EXPRESSION = `(()=>{for(const s of ${JSON.stringify(QUESTION_SELECTORS)}){const e=document.querySelector(s);if(e&&!e.disabled)return s}return null})()`;

export async function loginSyntheticStudent({fill, click, wait, evaluate}, fixture) {
  const {username, password} = syntheticLogin(fixture);
  await fill('#username', username);
  await fill('input[type="password"]', password);
  await evaluate(`(()=>{const b=document.querySelector('.rule-login button[type="submit"]')||document.querySelector('form.login-form button[type="submit"]');if(!b)throw new Error('login submit button missing');b.click();})()`);
  await wait(() => evaluate(QUESTION_READY_EXPRESSION), 'student logged in');
}

export function browserEnvironment(source = process.env) {
  const env = {};
  // 浏览器不需要模型或数据库配置；白名单避免继承调用者的凭据及演示模式。
  for (const key of ['SystemRoot', 'SYSTEMROOT', 'WINDIR', 'TEMP', 'TMP', 'USERPROFILE', 'LOCALAPPDATA', 'APPDATA', 'PROGRAMDATA', 'ProgramFiles', 'ProgramFiles(x86)', 'CommonProgramFiles', 'COMSPEC']) {
    if (source[key] !== undefined) env[key] = source[key];
  }
  return env;
}

export async function startOwnedBrowser(executable, tempRoot = tmpdir()) {
  const profile = await mkdtemp(path.join(tempRoot, 'zhku-e02-page-live-'));
  try {
    await writeFile(path.join(profile, 'zhku-cdp-owner'), 'e02-page-live\n', {flag: 'wx'});
    const args = ['--headless=new', '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0', '--user-data-dir=' + profile, '--no-first-run', '--no-default-browser-check', '--disable-background-networking', '--disable-component-update', '--disable-sync', '--disable-extensions', '--disable-features=OptimizationHints,MediaRouter,AutofillServerCommunication', '--window-size=1440,1100', 'about:blank'];
    const chrome = spawn(executable, args, {stdio: 'ignore', env: browserEnvironment()});
    await new Promise((resolve, reject) => { chrome.once('spawn', resolve); chrome.once('error', reject); });
    return {chrome, profile, tempRoot};
  } catch (error) {
    // 启动失败没有子进程持有 profile，才能在这里删除。
    await rm(profile, {recursive: true, force: true});
    throw error;
  }
}

export async function ownedDevToolsEndpoint(profile) {
  const [portText, browserPath] = (await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).trim().split(/\r?\n/);
  const port = Number(portText);
  assert(Number.isInteger(port) && port > 0 && port <= 65535 && /^\/devtools\/browser\/[a-z0-9-]+$/i.test(browserPath || ''), 'Invalid owned Chrome DevToolsActivePort');
  const response = await fetch(`http://127.0.0.1:${port}/json/version`, {signal: AbortSignal.timeout(1000)});
  assert(response.ok, 'Owned Chrome CDP unavailable');
  const version = await response.json();
  // 不凭端口识别进程：只有同时匹配本轮 profile 中的随机 browser 路径才可连接。
  assert.equal(version.webSocketDebuggerUrl, `ws://127.0.0.1:${port}${browserPath}`, 'Chrome CDP ownership mismatch');
  return {port, version};
}

const fileExists = async file => { try { await stat(file); return true; } catch (error) { if (error.code === 'ENOENT') return false; throw error; } };

export async function createBrowserLease(batch, evidenceRoot, expectedSha) {
  assert(/^[a-f0-9]{64}$/.test(expectedSha || ''), 'Expected package SHA is required');
  const root = await realpath(evidenceRoot), dir = await realpath(batch);
  const relative = path.relative(root.toLowerCase(), dir.toLowerCase());
  assert(relative && relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative), 'Batch must be a child of evidence root');
  assert((await stat(dir)).isDirectory(), 'Batch must be a directory');
  for (const name of ['browser-ready.json', 'STOP', 'live-result.json', 'browser-driver-lease.json'])
    assert(!(await fileExists(path.join(dir, name))), 'Existing batch evidence cannot be reused');
  const nonce = randomBytes(32).toString('hex');
  const leaseText = JSON.stringify({schemaVersion:1, nonce, packageSha256:expectedSha}) + '\n';
  await writeFile(path.join(dir, 'browser-driver-lease.json'), leaseText, {flag:'wx'});
  return {dir, root, nonce, expectedSha, leaseText, accepted:null, stopped:false};
}

export async function acceptBrowserTicket(lease, profile = 'E02') {
  assert(['E02','E13'].includes(profile), 'Unknown single-page profile');
  assert(!lease.accepted, 'Ready ticket already accepted');
  assert.equal(await realpath(lease.dir), lease.dir, 'Batch path changed');
  assert.equal(await readFile(path.join(lease.dir, 'browser-driver-lease.json'), 'utf8'), lease.leaseText, 'Lease changed');
  assert(!(await fileExists(path.join(lease.dir, 'STOP'))) && !(await fileExists(path.join(lease.dir, 'live-result.json'))), 'Batch already stopped');
  const file = path.join(lease.dir, 'browser-ready.json');
  const bytes = await readFile(file, 'utf8'); const info = await stat(file); const host = JSON.parse(bytes);
  assert.equal(host.driverLeaseNonce, lease.nonce, 'Driver lease nonce mismatch');
  assert.equal(host.driverLeaseSchemaVersion, 1); assert.equal(host.schemaVersion, 4);
  assert.equal(host.packageSha256, lease.expectedSha, 'Package SHA mismatch');
  assert.equal(host.profile, profile); assert.equal(host.executionMode, 'ONLINE'); assert.equal(host.browserRequired, true);
  assert.equal(host.maximumTasks, 1); assert.equal(host.maximumRuns, 1);
  assert.equal(host.maximumProviderCalls, 5); assert.equal(host.hardBudgetRmb, '4.02'); assert.equal(host.javaPostAllowed, false);
  assert(Number.isInteger(host.proxyPort) && host.proxyPort >= 1024 && host.proxyPort <= 65535, 'Invalid loopback proxy port');
  lease.accepted = {bytes, ino: info.ino, mtimeMs: info.mtimeMs};
  return host;
}

export async function stopBrowserLease(lease) {
  assert(lease.accepted && !lease.stopped, 'Current ticket not accepted or already stopped');
  assert.equal(await realpath(lease.dir), lease.dir, 'Batch path changed');
  assert.equal(await readFile(path.join(lease.dir, 'browser-driver-lease.json'), 'utf8'), lease.leaseText, 'Lease changed');
  const file = path.join(lease.dir, 'browser-ready.json'), info = await stat(file);
  assert(info.ino === lease.accepted.ino && info.mtimeMs === lease.accepted.mtimeMs && await readFile(file, 'utf8') === lease.accepted.bytes, 'Ready ticket changed');
  assert(!(await fileExists(path.join(lease.dir, 'live-result.json'))), 'Batch receipt already exists');
  await writeFile(path.join(lease.dir, 'STOP'), 'owned e02 page test complete\n', {flag:'wx'});
  lease.stopped = true;
}

export async function withBrowserLease(lease, work) {
  let failure;
  try { return await work(); } catch (error) { failure = error; throw error; }
  finally {
    // 未确认归属的批次绝不停止；已确认后，执行失败也必须尝试 STOP。
    if (lease.accepted && !lease.stopped) {
      try { await stopBrowserLease(lease); }
      catch (stopError) {
        if (failure) throw new AggregateError([failure, stopError], 'Driver failed and host STOP failed');
        throw stopError;
      }
    }
  }
}

export async function stopOwnedBrowser({chrome, profile, tempRoot}) {
  const exited = () => chrome.exitCode !== null || chrome.signalCode !== null;
  if (!exited()) chrome.kill();
  const deadline = Date.now() + 10000;
  while (!exited() && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
  const cleanup = {exited: exited(), profileRemoved: false};
  // kill 只是请求，不是退出证明；未确认退出时保留目录供人工处理。
  if (!cleanup.exited) return cleanup;
  const resolved = await realpath(profile), parent = await realpath(tempRoot);
  assert.equal(path.dirname(resolved).toLowerCase(), parent.toLowerCase());
  assert(path.basename(resolved).startsWith('zhku-e02-page-live-'));
  assert.equal(await readFile(path.join(resolved, 'zhku-cdp-owner'), 'utf8'), 'e02-page-live\n');
  await rm(resolved, {recursive: true, maxRetries: 8, retryDelay: 250});
  cleanup.profileRemoved = true;
  return cleanup;
}

export async function runPageLive(profile = 'E02') {
assert(['E02','E13'].includes(profile), 'Unknown single-page profile');

// baseUrl 可传 '-'：由宿主写出的 browser-ready.json 里的 proxyPort 决定，
// 这样启动器无需预先知道回环端口，也不会由外部猜一个地址。
const [evidenceArg, baseArg, hostArg, expectedSha] = process.argv.slice(2);
assert(evidenceArg && hostArg && expectedSha, 'usage: rule-agent-e02-page-live.mjs <evidenceDir> <baseUrl|-> <hostDir> <expectedPackageSha256>');
const evidence = path.resolve(evidenceArg);
let baseUrl = baseArg && baseArg !== '-' ? baseArg.replace(/\/+$/, '') : null;
const hostDir = path.resolve(hostArg);
const pageUrl = () => baseUrl + '/rules';
const root = process.cwd();
// 目录必须由调用方显式创建，避免覆盖上一轮证据。
assert.equal((await stat(evidence)).isDirectory(), true, 'evidence directory must already exist');
// 必须由驱动先占用新批次；此握手只防误混批，不能对抗同用户恶意进程。
const evidenceRoot = path.join(root, `docs/evidence/rule-check-refactor/2026-10-28/${profile.toLowerCase()}-single-page-live`);
const lease = await createBrowserLease(hostDir, evidenceRoot, expectedSha);
const save = (name, data) => writeFile(path.join(evidence, name), JSON.stringify(data, null, 2) + '\n', {flag: 'wx'});
const exists = async file => { try { await stat(file); return true; } catch (e) { if (e.code === 'ENOENT') return false; throw e; } };
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const wait = async (check, label, timeout = 15000) => { const end = Date.now() + timeout; while (Date.now() < end) { if (await check()) return; await delay(100); } throw new Error('Timeout: ' + label); };

const secrets = [];
const clean = text => secrets.reduce((s, value) => s.split(value).join('[REDACTED]'), String(text || '')).replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/ig, '[opaque]');

let owned, cdp, session, success = false, failure = null;
const checks = [], network = [], consoleRows = [], pendingBodies = new Set(), requests = new Map(), bodyJobs = [];
let phase = 'SMOKE';
let providerCalls = -1, liveResult = null, finalSnapshot = null;
const observedTaskIds = new Set();

const command = (method, params = {}) => cdp.send(method, params, session);
const evaluate = async expression => { const result = await command('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true}); if (result.exceptionDetails) throw new Error('Page evaluation failed (details not persisted)'); return result.result.value; };
const click = async selector => { const rect = await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e||e.disabled)throw Error('Target unavailable');e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return{x:r.x+r.width/2,y:r.y+r.height/2};})()`); await command('Input.dispatchMouseEvent', {type: 'mousePressed', ...rect, button: 'left', clickCount: 1}); await command('Input.dispatchMouseEvent', {type: 'mouseReleased', ...rect, button: 'left', clickCount: 1}); };
const button = async text => { const selector = await evaluate(`(()=>{const a=[...document.querySelectorAll('button')];const i=a.findIndex(e=>e.textContent.trim()===${JSON.stringify(text)});if(i<0)throw Error('Button not found');a[i].setAttribute('data-cdp-target','current');return 'button[data-cdp-target="current"]';})()`); await click(selector); await evaluate(`document.querySelector('[data-cdp-target="current"]')?.removeAttribute('data-cdp-target')`); };
const fill = async (selector, value) => { await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e||e.disabled)throw Error('Input unavailable');e.focus();const p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(e,'');e.dispatchEvent(new Event('input',{bubbles:true}));})()`); await command('Input.insertText', {text: value}); };
const text = () => evaluate('document.body.innerText');
const has = async value => (await text()).includes(value);
// 提交按钮文案随界面改版变化（旧「查证规则」、新「提交问题/提交新问题」由 aria-label 标识），
// 统一按 aria-label 定位，避免因文案差异把“已提交”误判成“提交失败”。
const clickSubmit = async () => {
  const selector = await evaluate(`(()=>{const b=[...document.querySelectorAll('button[type="submit"],button')].find(e=>(e.getAttribute('aria-label')||'').includes('提交'));if(!b)throw Error('submit button not found');b.setAttribute('data-cdp-target','current');return 'button[data-cdp-target="current"]';})()`);
  await click(selector);
  await evaluate(`document.querySelector('[data-cdp-target="current"]')?.removeAttribute('data-cdp-target')`);
};
const checkpoint = async (name, data = {}) => { checks.push({name, at: new Date().toISOString(), phase, ...data}); };
const shot = async name => { assert.equal(await evaluate(`[...document.querySelectorAll('input[type="password"]')].some(e=>e.value.length>0)`), false, 'Never capture a populated credential form'); await evaluate('window.scrollTo(0,0)'); const m = await command('Page.getLayoutMetrics'); const image = await command('Page.captureScreenshot', {format: 'png', captureBeyondViewport: true, clip: {x: 0, y: 0, width: 1440, height: Math.min(2600, Math.ceil(m.cssContentSize.height)), scale: 1}}); await writeFile(path.join(evidence, name), Buffer.from(image.data, 'base64'), {flag: 'wx'}); };

// 终态文案来自既有页面契约；这里只判断“已经停在一个明确终态”，不判断答案正确性。
const terminalMarkers = ['已找到依据', '部分确认', '资料缺口', '本轮处理失败', '处理未完成', '需要补充', '尚无法确认'];

try {
  await withBrowserLease(lease, async () => {
  phase = 'HOST_READY'; let host;
  // 宿主必须回显本轮 nonce 与独立给定的包 SHA，才允许接入及异常 STOP。
  await wait(async () => { try { host = await acceptBrowserTicket(lease, profile); return true; } catch (error) { if (error.code === 'ENOENT' || error instanceof SyntaxError) return false; throw error; } }, 'current host ready ticket', 240000);
  const hostBase = 'http://127.0.0.1:' + host.proxyPort;
  assert(!baseUrl || baseUrl === hostBase, 'base URL must match current host loopback proxy');
  baseUrl = hostBase;
  await wait(async () => { try { return (await fetch(pageUrl(), {signal: AbortSignal.timeout(1000)})).ok; } catch { return false; } }, 'current host page', 30000);
  await checkpoint('host-ready', {backendPort: host.backendPort, maximumProviderCalls: host.maximumProviderCalls, hardBudgetRmb: host.hardBudgetRmb});
  phase = 'SMOKE';
  owned = await startOwnedBrowser('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe');
  let endpoint;
  await wait(async () => {
    if (owned.chrome.exitCode !== null || owned.chrome.signalCode !== null) throw new Error('Owned Chrome exited before CDP ready');
    try { endpoint = await ownedDevToolsEndpoint(owned.profile); return true; } catch (error) {
      if (error.code === 'ERR_ASSERTION' && /ownership mismatch/.test(error.message)) throw error;
      return false;
    }
  }, 'owned Chrome loopback CDP', 20000);
  const {port: debugPort, version} = endpoint;
  const chrome = owned.chrome;
  cdp = new CDP(version.webSocketDebuggerUrl); await cdp.open();
  const target = await cdp.send('Target.createTarget', {url: 'about:blank'});
  session = (await cdp.send('Target.attachToTarget', {targetId: target.targetId, flatten: true})).sessionId;
  await command('Page.enable'); await command('Runtime.enable'); await command('Network.enable'); await command('Log.enable');
  await command('Network.setCacheDisabled', {cacheDisabled: true}); await command('Network.setBypassServiceWorker', {bypass: true});
  await command('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false});
  cdp.handlers.push(event => {
    if (event.sessionId !== session) return;
    const p = event.params || {};
    if (event.method === 'Network.requestWillBeSent') {
      // 不保存 postData/headers，不订阅 ExtraInfo；CSRF 与 Session 完全留在浏览器正常协议内。
      if (baseUrl && p.request.url.startsWith(baseUrl + '/')) { const row = {sequence: network.length + 1, phase, method: p.request.method, route: new URL(p.request.url).pathname.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/ig, ':opaque'), resourceType: p.type, startedAt: new Date().toISOString()}; network.push(row); requests.set(p.requestId, row); }
    } else if (event.method === 'Network.responseReceived') {
      const row = requests.get(p.requestId); if (row) { Object.assign(row, {status: p.response.status, mimeType: p.response.mimeType, fromServiceWorker: p.response.fromServiceWorker === true}); if (row.route.startsWith('/api/zhku/controlled-preview/v2/tasks/') && row.method === 'GET' && p.response.status === 200 && !row.route.includes('/requests/')) pendingBodies.add(p.requestId); }
    } else if (event.method === 'Network.loadingFinished' && pendingBodies.delete(p.requestId)) {
      const row = requests.get(p.requestId);
      bodyJobs.push(command('Network.getResponseBody', {requestId: p.requestId}).then(body => {
        const value = JSON.parse(body.body);
        if (phase === `${profile}_QUESTION` && value.taskId) {
          observedTaskIds.add(value.taskId);
          if (value.terminated === true) finalSnapshot = value;
        }
        if (row.route.includes('/originals/')) row.safeBody = {title: clean(value.title), revision: value.revision, contextText: clean(value.contextText)};
        else row.safeBody = {schemaVersion: value.schemaVersion, taskStatus: value.taskStatus, version: value.version, terminated: value.terminated, execution: value.execution ? {mode: value.execution.mode, modelAttemptCount: value.execution.modelAttemptCount, providerResponseReceived: value.execution.providerResponseReceived} : null, result: value.result ? {referenceRevision: value.result.referenceRevision, coverage: value.result.coverage, evidenceCount: value.result.evidence?.length, quotes: value.result.evidence?.map(e => clean(e.quote)), claims: value.result.items?.flatMap(q => q.claims?.map(c => clean(c.text)) || []), issues: value.result.items?.flatMap(q => q.issues?.map(i => ({kind: i.kind, reasonCode: i.reasonCode})) || [])} : null};
      }).catch(() => { row.safeBodyRead = 'UNAVAILABLE'; }));
    } else if (event.method === 'Network.loadingFailed') {
      const row = requests.get(p.requestId); if (row) row.transportError = clean(p.errorText);
    } else if (event.method === 'Runtime.exceptionThrown') consoleRows.push({phase, type: 'runtime-exception', text: clean(p.exceptionDetails?.text), line: p.exceptionDetails?.lineNumber});
    else if (event.method === 'Runtime.consoleAPICalled' && ['error', 'warning', 'assert'].includes(p.type)) consoleRows.push({phase, type: p.type, argumentCount: p.args?.length, text: 'Argument values deliberately not captured'});
    else if (event.method === 'Log.entryAdded' && ['error', 'warning'].includes(p.entry.level)) consoleRows.push({phase, type: p.entry.level, source: p.entry.source, text: clean(p.entry.text).replace(/https?:\/\/[^\s]+/g, '[resource-url]')});
  });

  const smokeServer = createServer((req, res) => { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end('<!doctype html><title>Zhku CDP local smoke</title><button id="smoke" onclick="document.querySelector(\'h1\').textContent=\'CDP interaction confirmed\'">Test local interaction</button><h1>Pending</h1>'); });
  await new Promise(resolve => smokeServer.listen(0, '127.0.0.1', resolve));
  try { await command('Page.navigate', {url: 'http://127.0.0.1:' + smokeServer.address().port + '/cdp-smoke'}); await wait(() => evaluate(`!!document.querySelector('#smoke')`), 'loopback smoke document'); await click('#smoke'); }
  finally { smokeServer.closeAllConnections(); await new Promise(resolve => smokeServer.close(resolve)); }
  const smoke = await evaluate('({title:document.title,url:location.href,text:document.querySelector("h1").textContent})'); assert.equal(smoke.text, 'CDP interaction confirmed'); await shot('00-cdp-smoke.png');
  await save('cdp-ready.json', {browser: version.Browser, node: process.version, webSocket: typeof WebSocket, debugPort, chromePid: chrome.pid, isolatedProfile: true, headless: true, smoke});

  phase = 'ANONYMOUS'; await command('Page.navigate', {url: pageUrl()}); await wait(() => evaluate(`!!document.querySelector('#username:not(:disabled)')`), 'login form'); await shot('01-login-empty.png'); await checkpoint('login-form-visible');

  // 合成口令只从既有测试源码读入内存，不出现在脚本、参数、日志、截图或证据中。
  const fixtureSource = await readFile(path.join(root, 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentSimulatedChainTempDbHttpTest.java'), 'utf8');
  secrets.push(syntheticLogin(fixtureSource).password);

  phase = 'LOGIN'; await loginSyntheticStudent({fill, click, wait, evaluate}, fixtureSource); await checkpoint('student-login-complete');

  const plan = JSON.parse(await readFile(path.join(root, `knowledge/src/test/resources/rule-agent-${profile.toLowerCase()}-page-plan.json`), 'utf8'));
  const question = plan.cases[0].question;
  assert.equal(host.question, question, 'host and plan must agree on the single question');

  // 提问入口按新 UI 优先级探测：首页 #question-home、对话态 #question-next，回放页 #rule-question。
  const questionSelector = await evaluate(QUESTION_READY_EXPRESSION);
  assert(questionSelector, 'student question input must be visible and enabled');
  phase = `${profile}_QUESTION`; await fill(questionSelector, question); await clickSubmit(); await wait(() => has('正在查证相关规则…'), 'visible async progress'); await shot('02-active.png'); await checkpoint('submitted-single-question');
  // 真实模型耗时不可预估；上限按宿主租约收敛，不重试、不刷新。
  // 终态判定按状态行文字（V3 语义：已找到依据/部分确认/资料缺口/处理未完成…），
  // 同时排除仍在进行的进度提示，避免把“还在查证”当成已出结果。
  await wait(async () => {
    const body = await text();
    return terminalMarkers.some(marker => body.includes(marker)) && !body.includes('正在查证相关规则…');
  }, 'terminal answer state', 420000);
  // 结果按 V3 组件结构读取：标题即结论句，证据改为折叠区，原文入口按钮文案改为“在原文中定位”。
  const answer = await evaluate(`(()=>{const t=document.querySelector('[data-testid="answer-title"]');const s=document.querySelector('[data-testid="answer-status"]');const q=document.querySelector('[data-testid="evidence-quote"]');const o=document.querySelector('[data-testid="no-claim"]');const folds=[...document.querySelectorAll('[data-testid^="fold-"]')].map(e=>e.getAttribute('data-testid'));return{title:t?t.textContent.trim():null,status:s?s.textContent.trim():null,quote:q?q.textContent.trim():null,noClaim:o?o.textContent.trim():null,folds,hasOriginalButton:!!document.querySelector('[data-testid="open-original"]')};})()`);
  await shot('03-answer.png'); await checkpoint('answer-rendered', answer);
  assert.deepEqual(answer.folds, ['fold-explanation', 'fold-sources', 'fold-evidence'], 'V3 requires three collapsed detail sections');
  if (profile === 'E13') {
    await Promise.all(bodyJobs);
    assert.equal(observedTaskIds.size, 1, 'E13 must have one coherent task');
    assertE13Terminal(finalSnapshot, await text());
    await checkpoint('e13-data-gap-terminal-validated', {coverage:'NONE', productVerdict:'HUMAN_REVIEW_REQUIRED'});
  }
  // 原文入口改成折叠区内的按钮；打开后按 mark 核对高亮是否就是已发布摘录。
  if (answer.hasOriginalButton) {
    await click('[data-testid="open-original"]');
    await wait(() => evaluate(`!!document.querySelector('[data-testid="original-mark"]')`), 'original panel');
    const original = await evaluate(`(()=>{const m=document.querySelector('[data-testid="original-mark"]');const p=document.querySelector('[data-testid="original-page"]');return{mark:m?m.textContent.trim():null,page:p?p.textContent.trim():null};})()`);
    await shot('04-original.png');
    await checkpoint('original-rendered', {originalQuote: original.mark, originalPage: original.page, matchesQuote: Boolean(original.mark && answer.quote && original.mark.includes(answer.quote))});
  }

  phase = 'LOGOUT'; await evaluate(`(()=>{const b=document.querySelector('[data-testid="identity-button"]');if(b)b.click();})()`); await button('退出登录'); await wait(() => evaluate(`!!document.querySelector('#username:not(:disabled)')`), 'logout'); assert.equal(await evaluate(`document.querySelectorAll('[data-testid="answer-title"]').length`), 0); await checkpoint('logout-clears-result');

  // 宿主会同时关掉前端，不能 reload 后声称 UI 负控成功；先验证同页请求确实可达。
  phase = 'HOST_STOP';
  assert.equal((await probeSessionNetwork(evaluate)).reachable, true, 'session endpoint must be reachable before STOP');
  await stopBrowserLease(lease);
  const resultFile = path.join(hostDir, 'live-result.json');
  await wait(() => exists(resultFile), 'host live receipt', 120000);
  liveResult = JSON.parse(await readFile(resultFile, 'utf8'));
  providerCalls = liveResult.providerCalls;
  assert.equal(liveResult.httpChecksPassed, true);
  assert.equal(liveResult.resourcesClosed, true);
  assert.equal(liveResult.profile, profile);
  assert.equal(liveResult.scopeRuns, 1);
  assert.equal(liveResult.browserTested, false, 'host must not claim browser success on the driver behalf');
  assert(providerCalls >= 1 && providerCalls <= plan.maximumProviderCalls, 'provider calls must stay inside the E02 ceiling');
  assert.equal(liveResult.keyReads >= 1, true);
  await wait(async () => !(await probeSessionNetwork(evaluate)).reachable, 'session endpoint network unavailable', 30000);
  await checkpoint('host-stop-network-unreachable', {scope:'TRANSPORT_ONLY', uiNegativeControlVerified:false});

  await Promise.all(bodyJobs);
  assert(network.some(r => r.phase === `${profile}_QUESTION` && r.safeBody?.execution?.mode === 'ONLINE'));
  assert(network.some(r => r.phase === `${profile}_QUESTION` && r.safeBody?.terminated === true));
  assert.equal(consoleRows.filter(r => r.type === 'runtime-exception').length, 0);
  // 单题单run：不出现第二个任务，也不出现澄清续跑消息。
  assert.equal(network.filter(r => r.phase === `${profile}_QUESTION` && r.method === 'POST' && r.route.endsWith('/tasks')).length, 1);
  assert.equal(network.filter(r => r.phase === `${profile}_QUESTION` && r.route.endsWith('/messages')).length, 0);
  success = true; await checkpoint('browser-acceptance-complete');
  });
} catch (error) {
  success = false;
  failure = clean(error.message); console.error(profile + '_PAGE_LIVE_FAILED: ' + failure);
  try { await shot('99-failure.png'); } catch {}
} finally {
  // 仅关闭已校验的连接；兜底只结束本轮 spawn 的进程，绝不按固定端口关闭浏览器。
  if (cdp) { try { await cdp.send('Browser.close'); } catch {} cdp.close(); }
  let cleanup = {chromePid: owned?.chrome.pid ?? null, chromeExitCode: null, chromeSignal: null, profileRemoved: !owned};
  if (owned) {
    try {
      const result = await stopOwnedBrowser(owned);
      cleanup = {...cleanup, ...result, chromeExitCode: owned.chrome.exitCode, chromeSignal: owned.chrome.signalCode};
      if (!result.exited || !result.profileRemoved) { success = false; failure ||= 'Owned Chrome cleanup incomplete'; }
    } catch (error) { success = false; failure ||= clean(error.message); }
  }
  await Promise.allSettled(bodyJobs);
  await save('browser-network.json', {source: 'REAL_CHROME_CDP_NETWORK_WHITELIST', headersSaved: false, requestBodiesSaved: false, rawResponseBodiesSaved: false, requests: network});
  await save('browser-console.json', {source: 'REAL_CHROME_CDP', consoleArgumentValuesSaved: false, events: consoleRows});
  await save('browser-result.json', {mode: 'REAL_CHROME_WITH_REAL_HOST_NOT_PRODUCT_ACCEPTANCE', success, failure, checks, cleanup, providerCalls, liveReceiptRead: liveResult !== null, uiNegativeControlVerified: false});
  console.log(JSON.stringify({browserChecks: success ? 'PASS_WITHOUT_UI_NEGATIVE_CONTROL' : 'FAIL', checks: checks.length, providerCalls, profileRemoved: cleanup.profileRemoved, uiNegativeControlVerified: false}));
}
process.exitCode = success ? 0 : 1;
}

export function assertE13Terminal(s, visibleText) {
  assert(s && s.terminated === true && s.taskStatus === 'RESOLVED' && s.runState === 'INCOMPLETE', 'E13 must finish as honest incomplete result');
  assert.equal(s.execution?.mode, 'ONLINE'); assert.equal(s.execution?.providerResponseReceived, true);
  assert(s.execution.modelAttemptCount >= 1 && s.execution.modelAttemptCount <= 5);
  assert.equal(s.continuationToken, null); assert.equal(s.result?.coverage, 'NONE');
  assert.equal(s.result.evidence?.length, 0); assert.equal(s.result.items?.length, 1);
  const q = s.result.items[0]; assert.equal(q.claims?.length, 0); assert.equal(q.clarification?.length, 0);
  assert(q.issues?.length && q.issues.every(i => i.kind === 'DATA_GAP'));
  assert(q.issues.some(i => i.reasonCode === 'UNSUPPORTED_DEMAND'));
  assert.match(visibleText, /资料缺口/);
  // 证据不足时页面不得出现任何结论性标记：既不能是“已找到依据”，也不能是技术失败的“处理未完成”。
  assert.match(visibleText, /尚无法确认|暂时无法确认|目前没有足够依据|不作结论/);
  assert.match(visibleText, /当期/); assert.match(visibleText, /不作结论|无法确认|无法核实/);
  assert.match(visibleText, /不代办|不代替你办理|没有.*权限/); assert.match(visibleText, /教务/);
  assert(!visibleText.includes('本轮处理失败'), 'technical failure must not pass as gap');
  assert(!visibleText.includes('处理未完成'), 'technical failure must not pass as gap');
  assert(!visibleText.includes('已找到依据'), 'gap must not be presented as a resolved answer');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await runPageLive('E02');
