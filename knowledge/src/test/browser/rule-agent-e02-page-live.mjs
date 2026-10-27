// E02 单题真实在线验收的浏览器驱动：只驱动学生真实页面，不直接发业务 POST，不读取密钥。
// 无安装依赖；协议原包只在内存，持久证据仅保存白名单字段。
import {spawn} from 'node:child_process';
import {mkdir, mkdtemp, readFile, writeFile, stat, rm, realpath} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';

// baseUrl 可传 '-'：由宿主写出的 browser-ready.json 里的 proxyPort 决定，
// 这样启动器无需预先知道回环端口，也不会由外部猜一个地址。
const [evidenceArg, baseArg, hostArg] = process.argv.slice(2);
assert(evidenceArg && hostArg, 'usage: rule-agent-e02-page-live.mjs <evidenceDir> <baseUrl|-> <hostDir>');
const evidence = path.resolve(evidenceArg);
let baseUrl = baseArg && baseArg !== '-' ? baseArg.replace(/\/+$/, '') : null;
const hostDir = path.resolve(hostArg);
const pageUrl = () => baseUrl + '/rules';
const root = process.cwd();
// 目录必须由调用方显式创建，避免覆盖上一轮证据。
assert.equal((await stat(evidence)).isDirectory(), true, 'evidence directory must already exist');
const save = (name, data) => writeFile(path.join(evidence, name), JSON.stringify(data, null, 2) + '\n', {flag: 'wx'});
const exists = async file => { try { await stat(file); return true; } catch (e) { if (e.code === 'ENOENT') return false; throw e; } };
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const wait = async (check, label, timeout = 15000) => { const end = Date.now() + timeout; while (Date.now() < end) { if (await check()) return; await delay(100); } throw new Error('Timeout: ' + label); };

const secrets = [];
const clean = text => secrets.reduce((s, value) => s.split(value).join('[REDACTED]'), String(text || '')).replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/ig, '[opaque]');

class CDP {
  constructor(url) { this.ws = new WebSocket(url); this.pending = new Map(); this.next = 0; this.handlers = []; this.ws.addEventListener('message', event => { const data = JSON.parse(String(event.data)); if (data.id) { const p = this.pending.get(data.id); if (p) { clearTimeout(p.timer); this.pending.delete(data.id); data.error ? p.reject(new Error('CDP ' + data.error.code + ': ' + data.error.message)) : p.resolve(data.result); } } else for (const handler of this.handlers) handler(data); }); }
  async open() { if (this.ws.readyState !== 1) await new Promise((resolve, reject) => { this.ws.addEventListener('open', resolve, {once: true}); this.ws.addEventListener('error', () => reject(new Error('Local CDP WebSocket connection failed')), {once: true}); }); }
  send(method, params = {}, sessionId) { const id = ++this.next; return new Promise((resolve, reject) => { const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('CDP timeout: ' + method)); }, method === 'Page.captureScreenshot' ? 30000 : 10000); this.pending.set(id, {resolve, reject, timer}); this.ws.send(JSON.stringify({id, method, params, ...(sessionId ? {sessionId} : {})})); }); }
  close() { this.ws.close(); for (const p of this.pending.values()) { clearTimeout(p.timer); p.reject(new Error('CDP closed')); } this.pending.clear(); }
}

const profile = await mkdtemp(path.join(tmpdir(), 'zhku-e02-page-live-'));
await writeFile(path.join(profile, 'zhku-cdp-owner'), 'e02-page-live\n', {flag: 'wx'});
const debugPort = 19271;
const args = ['--headless=new', '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=' + debugPort, '--user-data-dir=' + profile, '--no-first-run', '--no-default-browser-check', '--disable-background-networking', '--disable-component-update', '--disable-sync', '--disable-extensions', '--disable-features=OptimizationHints,MediaRouter,AutofillServerCommunication', '--window-size=1440,1100', 'about:blank'];
const chrome = spawn('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', args, {stdio: 'ignore'});

let cdp, session, success = false, failure = null;
const checks = [], network = [], consoleRows = [], pendingBodies = new Set(), requests = new Map(), bodyJobs = [];
let phase = 'SMOKE';
let providerCalls = -1, liveResult = null;

const command = (method, params = {}) => cdp.send(method, params, session);
const evaluate = async expression => { const result = await command('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true}); if (result.exceptionDetails) throw new Error('Page evaluation failed (details not persisted)'); return result.result.value; };
const click = async selector => { const rect = await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e||e.disabled)throw Error('Target unavailable');e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return{x:r.x+r.width/2,y:r.y+r.height/2};})()`); await command('Input.dispatchMouseEvent', {type: 'mousePressed', ...rect, button: 'left', clickCount: 1}); await command('Input.dispatchMouseEvent', {type: 'mouseReleased', ...rect, button: 'left', clickCount: 1}); };
const button = async text => { const selector = await evaluate(`(()=>{const a=[...document.querySelectorAll('button')];const i=a.findIndex(e=>e.textContent.trim()===${JSON.stringify(text)});if(i<0)throw Error('Button not found');a[i].setAttribute('data-cdp-target','current');return 'button[data-cdp-target="current"]';})()`); await click(selector); await evaluate(`document.querySelector('[data-cdp-target="current"]')?.removeAttribute('data-cdp-target')`); };
const fill = async (selector, value) => { await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e||e.disabled)throw Error('Input unavailable');e.focus();const p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(e,'');e.dispatchEvent(new Event('input',{bubbles:true}));})()`); await command('Input.insertText', {text: value}); };
const text = () => evaluate('document.body.innerText');
const has = async value => (await text()).includes(value);
const checkpoint = async (name, data = {}) => { checks.push({name, at: new Date().toISOString(), phase, ...data}); };
const shot = async name => { assert.equal(await evaluate(`[...document.querySelectorAll('input[type="password"]')].some(e=>e.value.length>0)`), false, 'Never capture a populated credential form'); await evaluate('window.scrollTo(0,0)'); const m = await command('Page.getLayoutMetrics'); const image = await command('Page.captureScreenshot', {format: 'png', captureBeyondViewport: true, clip: {x: 0, y: 0, width: 1440, height: Math.min(2600, Math.ceil(m.cssContentSize.height)), scale: 1}}); await writeFile(path.join(evidence, name), Buffer.from(image.data, 'base64'), {flag: 'wx'}); };

// 终态文案来自既有页面契约；这里只判断“已经停在一个明确终态”，不判断答案正确性。
const terminalMarkers = ['有依据可确认', '资料缺口', '本轮处理失败', '需要补充条件'];

try {
  await wait(async () => { try { return (await fetch('http://127.0.0.1:' + debugPort + '/json/version', {signal: AbortSignal.timeout(1000)})).ok; } catch { return false; } }, 'Chrome loopback CDP', 20000);
  const version = await (await fetch('http://127.0.0.1:' + debugPort + '/json/version')).json();
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

  phase = 'HOST_READY';
  const readyFile = path.join(hostDir, 'browser-ready.json');
  let host;
  // ready 文件由 Java 宿主写入，存在不等于 JSON 已写完；等到完整内容且页面可访问。
  await wait(async () => { try { const candidate = JSON.parse(await readFile(readyFile, 'utf8')); if (!baseUrl) { assert(Number.isInteger(candidate.proxyPort) && candidate.proxyPort >= 1024 && candidate.proxyPort <= 65535, 'host must publish a loopback proxy port'); baseUrl = 'http://127.0.0.1:' + candidate.proxyPort; } if (!(await fetch(pageUrl(), {signal: AbortSignal.timeout(1000)})).ok) return false; host = candidate; return true; } catch (error) { if (error instanceof SyntaxError || error.code === 'ENOENT' || error.name === 'TimeoutError' || error instanceof TypeError) return false; throw error; } }, 'complete page and Java host ready', 240000);
  assert.equal(host.profile, 'E02');
  assert.equal(host.executionMode, 'ONLINE');
  assert.equal(host.browserRequired, true);
  // 单题单run：宿主只允许一次初始提交，不存在澄清续跑。
  assert.equal(host.maximumTasks, 1);
  assert.equal(host.maximumRuns, 1);
  await checkpoint('host-ready', {backendPort: host.backendPort, maximumProviderCalls: host.maximumProviderCalls, hardBudgetRmb: host.hardBudgetRmb});

  phase = 'ANONYMOUS'; await command('Page.navigate', {url: pageUrl()}); await wait(() => evaluate(`!!document.querySelector('#username:not(:disabled)')`), 'login form'); await shot('01-login-empty.png'); await checkpoint('login-form-visible');

  // 合成口令只从既有测试源码读入内存，不出现在脚本、参数、日志、截图或证据中。
  const fixtureSource = await readFile(path.join(root, 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentTaskHttpTest.java'), 'utf8');
  const password = fixtureSource.match(/Arrays\.equals\(call\.getArgument\(1, char\[\]\.class\), "([^"]+)"\.toCharArray\(\)\)/)?.[1]; assert(password, 'Existing synthetic credential definition missing'); secrets.push(password);

  phase = 'LOGIN'; await fill('#username', 'student-a'); await fill('input[type="password"]', password); await click('.rule-login button[type="submit"]'); await wait(() => evaluate(`!!document.querySelector('#rule-question:not(:disabled)')`), 'student logged in'); await checkpoint('student-login-complete');

  const plan = JSON.parse(await readFile(path.join(root, 'knowledge/src/test/resources/rule-agent-e02-page-plan.json'), 'utf8'));
  const question = plan.cases[0].question;
  assert.equal(host.question, question, 'host and plan must agree on the single question');

  phase = 'E02_QUESTION'; await fill('#rule-question', question); await button('查证规则'); await wait(() => has('问题已受理，正在查证。'), 'visible async progress'); await shot('02-active.png'); await checkpoint('submitted-single-question');
  // 真实模型耗时不可预估；上限按宿主租约收敛，不重试、不刷新。
  await wait(async () => { const body = await text(); return terminalMarkers.some(marker => body.includes(marker)) && !body.includes('问题已受理，正在查证。'); }, 'terminal answer state', 420000);
  const answer = await evaluate(`(()=>{const d=document.querySelector('#decision-title');const f=document.querySelector('.rule-fact h3');const q=document.querySelector('.related-evidence blockquote');const n=document.querySelector('.rule-notice');return{heading:d?d.textContent.trim():null,claim:f?f.textContent.trim():null,quote:q?q.textContent.trim():null,notice:n?n.textContent.trim():null,evidenceCount:document.querySelectorAll('.related-evidence').length,hasOriginalButton:[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='核对原文')};})()`);
  await shot('03-answer.png'); await checkpoint('answer-rendered', answer);
  if (answer.hasOriginalButton) { await button('核对原文'); await wait(() => has('原文'), 'original panel'); const original = await evaluate(`(()=>{const m=document.querySelector('#original-panel mark');return m?m.textContent.trim():null;})()`); await shot('04-original.png'); await checkpoint('original-rendered', {originalQuote: original, matchesQuote: Boolean(original && answer.quote && original.includes(answer.quote))}); }

  phase = 'LOGOUT'; await button('退出登录'); await wait(() => evaluate(`!!document.querySelector('#username:not(:disabled)')`), 'logout'); assert.equal(await evaluate(`document.querySelectorAll('.decision').length`), 0); await checkpoint('logout-clears-result');

  // 释放宿主租约：宿主写 live-result.json 后退出，页面必须失去后端（因果负控）。
  phase = 'HOST_STOP'; await writeFile(path.join(hostDir, 'STOP'), 'owned e02 page test complete\n', {flag: 'wx'});
  const resultFile = path.join(hostDir, 'live-result.json');
  await wait(() => exists(resultFile), 'host live receipt', 120000);
  liveResult = JSON.parse(await readFile(resultFile, 'utf8'));
  providerCalls = liveResult.providerCalls;
  assert.equal(liveResult.httpChecksPassed, true);
  assert.equal(liveResult.resourcesClosed, true);
  assert.equal(liveResult.profile, 'E02');
  assert.equal(liveResult.scopeRuns, 1);
  assert.equal(liveResult.browserTested, false, 'host must not claim browser success on the driver behalf');
  assert(providerCalls >= 1 && providerCalls <= plan.maximumProviderCalls, 'provider calls must stay inside the E02 ceiling');
  assert.equal(liveResult.keyReads >= 1, true);
  await command('Page.reload', {ignoreCache: true});
  await wait(() => has('暂时无法核对登录状态，请稍后重试。'), 'proxy backend unavailable UI');
  await shot('05-host-stopped-negative-control.png'); await checkpoint('host-stop-breaks-real-page-api');

  await Promise.all(bodyJobs);
  assert(network.some(r => r.phase === 'E02_QUESTION' && r.safeBody?.execution?.mode === 'ONLINE'));
  assert(network.some(r => r.phase === 'E02_QUESTION' && r.safeBody?.terminated === true));
  assert.equal(consoleRows.filter(r => r.type === 'runtime-exception').length, 0);
  // 单题单run：不出现第二个任务，也不出现澄清续跑消息。
  assert.equal(network.filter(r => r.phase === 'E02_QUESTION' && r.method === 'POST' && r.route.endsWith('/tasks')).length, 1);
  assert.equal(network.filter(r => r.phase === 'E02_QUESTION' && r.route.endsWith('/messages')).length, 0);
  success = true; await checkpoint('browser-acceptance-complete');
} catch (error) {
  failure = clean(error.message); console.error('E02_PAGE_LIVE_FAILED: ' + failure);
  try { await shot('99-failure.png'); } catch {}
} finally {
  if (cdp) { try { await cdp.send('Browser.close'); } catch {} cdp.close(); }
  await wait(() => chrome.exitCode !== null || chrome.signalCode !== null, 'owned Chrome exit', 10000).catch(() => { chrome.kill(); });
  const cleanup = {chromePid: chrome.pid, chromeExitCode: chrome.exitCode, chromeSignal: chrome.signalCode, profileRemoved: false};
  // 删除前核对绝对路径、独立随机前缀和本轮 owner 标记，不涉及用户原有 Chrome 目录。
  const resolved = await realpath(profile); const parent = await realpath(tmpdir()); assert.equal(path.dirname(resolved).toLowerCase(), parent.toLowerCase()); assert(path.basename(resolved).startsWith('zhku-e02-page-live-')); assert.equal(await readFile(path.join(resolved, 'zhku-cdp-owner'), 'utf8'), 'e02-page-live\n');
  if (chrome.exitCode !== null || chrome.signalCode !== null) { await rm(resolved, {recursive: true, maxRetries: 8, retryDelay: 250}); cleanup.profileRemoved = true; }
  await Promise.allSettled(bodyJobs);
  await save('browser-network.json', {source: 'REAL_CHROME_CDP_NETWORK_WHITELIST', headersSaved: false, requestBodiesSaved: false, rawResponseBodiesSaved: false, requests: network});
  await save('browser-console.json', {source: 'REAL_CHROME_CDP', consoleArgumentValuesSaved: false, events: consoleRows});
  await save('browser-result.json', {mode: 'REAL_CHROME_WITH_REAL_HOST_NOT_PRODUCT_ACCEPTANCE', success, failure, checks, cleanup, providerCalls, liveReceiptRead: liveResult !== null});
  console.log(JSON.stringify({browserAcceptance: success ? 'PASS' : 'FAIL', checks: checks.length, providerCalls, profileRemoved: cleanup.profileRemoved}));
}
process.exitCode = success ? 0 : 1;
