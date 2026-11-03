import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile, mkdtemp, writeFile, rm, readdir, access, mkdir} from 'node:fs/promises';
import {createServer} from 'node:http';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {syntheticLogin, ownedDevToolsEndpoint, startOwnedBrowser, stopOwnedBrowser, loginSyntheticStudent, browserEnvironment, createBrowserLease, acceptBrowserTicket, stopBrowserLease, probeSessionNetwork, withBrowserLease, CDP} from './rule-agent-e02-page-live.mjs';
import {spawn} from 'node:child_process';

test('E02 宿主合成身份可从宿主 fixture 获得，不泄露口令', async () => {
  const fixture = await readFile('knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentSimulatedChainTempDbHttpTest.java', 'utf8');
  const login = syntheticLogin(fixture);
  assert.equal(login.username, 't06-chain-a');
  assert.ok(login.password && fixture.includes('"' + login.password + '"'));
  assert.throws(() => syntheticLogin(''), /fixture/i);
});

test('本轮 nonce 和包 SHA 握手后才能停止且拒绝被替换票据', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'e02-lease-'));
  const batch = path.join(root, 'batch'); await mkdir(batch); const sha = 'a'.repeat(64);
  try {
    await assert.rejects(createBrowserLease(root, root, sha), /root|batch/i);
    await assert.rejects(createBrowserLease(batch, root, 'invalid'), /sha/i);
    const lease = await createBrowserLease(batch, root, sha);
    const request = JSON.parse(await readFile(path.join(batch, 'browser-driver-lease.json'), 'utf8'));
    assert.match(request.nonce, /^[a-f0-9]{64}$/); assert.equal(request.packageSha256, sha);
    await assert.rejects(createBrowserLease(batch, root, sha));
    const ready = {schemaVersion:4, driverLeaseSchemaVersion:1, profile:'E02', executionMode:'ONLINE', browserRequired:true, maximumTasks:1, maximumRuns:1, maximumProviderCalls:5, hardBudgetRmb:'4.02', javaPostAllowed:false, proxyPort:12345, packageSha256:sha, driverLeaseNonce:request.nonce};
    const ticket = path.join(batch, 'browser-ready.json');
    await writeFile(ticket, JSON.stringify({...ready, driverLeaseNonce:'b'.repeat(64)}));
    await assert.rejects(acceptBrowserTicket(lease), /nonce/i);
    for (const bad of [{maximumProviderCalls:6}, {hardBudgetRmb:'4.03'}, {javaPostAllowed:true}]) {
      await writeFile(ticket, JSON.stringify({...ready, ...bad}));
      await assert.rejects(acceptBrowserTicket(lease));
    }
    await writeFile(ticket, JSON.stringify(ready));
    await acceptBrowserTicket(lease);
    await writeFile(ticket, JSON.stringify({...ready, proxyPort:12346}));
    await assert.rejects(stopBrowserLease(lease), /ticket|changed/i);
    await assert.rejects(access(path.join(batch, 'STOP')), {code:'ENOENT'});
    const batch2 = path.join(root, 'batch2'); await mkdir(batch2);
    const lease2 = await createBrowserLease(batch2, root, sha);
    const request2 = JSON.parse(await readFile(path.join(batch2, 'browser-driver-lease.json'), 'utf8'));
    await writeFile(path.join(batch2, 'browser-ready.json'), JSON.stringify({...ready, driverLeaseNonce:request2.nonce}));
    await acceptBrowserTicket(lease2);
    await assert.rejects(withBrowserLease(lease2, async () => { throw new Error('synthetic driver failure'); }), /synthetic driver failure/);
    assert.equal(typeof await readFile(path.join(batch2, 'STOP'), 'utf8'), 'string');
    await assert.rejects(stopBrowserLease(lease2));
  } finally { await rm(root, {recursive:true,force:true}); }
});

test('浏览器子进程只继承白名单环境，敏感合成哨兵不可见', async () => {
  const dir = await mkdtemp(path.join(tmpdir(), 'e02-env-'));
  const result = path.join(dir, 'result.json');
  try {
    const env = browserEnvironment({SystemRoot:process.env.SystemRoot, DASHSCOPE_API_KEY:'synthetic-secret', ZHKU_DB_PASSWORD:'synthetic-db', E02_PRIVATE_SENTINEL:'synthetic-other'});
    const script = `require('fs').writeFileSync(process.argv[1],JSON.stringify({key:'DASHSCOPE_API_KEY' in process.env,db:'ZHKU_DB_PASSWORD' in process.env,other:'E02_PRIVATE_SENTINEL' in process.env,system:!!process.env.SystemRoot}))`;
    const child = spawn(process.execPath, ['-e', script, result], {stdio:'ignore', env});
    await new Promise((resolve,reject) => { child.once('error',reject); child.once('exit',code=>code===0?resolve():reject(Error('synthetic child failed'))); });
    assert.deepEqual(JSON.parse(await readFile(result,'utf8')), {key:false, db:false, other:false, system:true});
  } finally { await rm(dir,{recursive:true,force:true}); }
});

test('启动失败会报告错误并移除未被进程使用的本轮 profile', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'e02-offline-'));
  try {
    await assert.rejects(startOwnedBrowser(path.join(root, 'missing.exe'), root), error => error.code === 'ENOENT');
    assert.deepEqual(await readdir(root), []);
  } finally { await rm(root, {recursive:true, force:true}); }
});

test('真实 Chrome 使用随机 CDP 并确认退出后清理自己的 profile', {timeout: 45000}, async t => {
  const executable = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  try { await access(executable); } catch { t.skip('Chrome unavailable'); return; }
  const owned = await startOwnedBrowser(executable);
  try {
    let endpoint;
    for (let i = 0; i < 150 && !endpoint; i++) {
      try { endpoint = await ownedDevToolsEndpoint(owned.profile); } catch { await new Promise(r => setTimeout(r, 100)); }
    }
    assert(endpoint, 'owned CDP was not published');
    assert(endpoint.port > 0);
    const cleanup = await stopOwnedBrowser(owned, endpoint.version.webSocketDebuggerUrl);
    assert.equal(cleanup.exited, true);
    assert.equal(cleanup.profileRemoved, true);
    await assert.rejects(access(owned.profile), {code: 'ENOENT'});
  } finally {
    if (owned.chrome.exitCode === null && owned.chrome.signalCode === null) {
      owned.chrome.kill();
      await new Promise(resolve => owned.chrome.once('exit', resolve));
    }
    await rm(owned.profile, {recursive: true, force: true});
  }
});

test('真实 Chrome 经合成宿主登录 E02 fixture，不提交问题', {timeout: 45000}, async t => {
  const executable = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  try { await access(executable); } catch { t.skip('Chrome unavailable'); return; }
  const fixture = await readFile('knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentSimulatedChainTempDbHttpTest.java', 'utf8');
  const expected = syntheticLogin(fixture); let logins = 0, otherPosts = 0;
  const server = createServer(async (req, res) => {
    if (req.method === 'POST') {
      if (req.url !== '/login') { otherPosts++; res.writeHead(404); res.end(); return; }
      let body = ''; for await (const chunk of req) body += chunk;
      const fields = new URLSearchParams(body);
      logins++;
      res.writeHead(fields.get('username') === expected.username && fields.get('password') === expected.password ? 200 : 401);
      res.end(); return;
    }
    res.setHeader('content-type', 'text/html');
    res.end(`<form class="rule-login"><input id="username" name="username"><input type="password" name="password"><button type="submit">Login</button></form><script>document.querySelector('form').onsubmit=async e=>{e.preventDefault();const r=await fetch('/login',{method:'POST',body:new URLSearchParams(new FormData(e.target))});if(r.ok)document.body.innerHTML='<textarea id="question-home"></textarea>';};</script>`);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const owned = await startOwnedBrowser(executable); let cdp;
  try {
    let endpoint; for (let i = 0; i < 150 && !endpoint; i++) { try { endpoint = await ownedDevToolsEndpoint(owned.profile); } catch { await new Promise(r => setTimeout(r, 100)); } }
    assert(endpoint);
    cdp = new CDP(endpoint.version.webSocketDebuggerUrl); await cdp.open();
    const target = await cdp.send('Target.createTarget', {url:'about:blank'});
    const session = (await cdp.send('Target.attachToTarget', {targetId:target.targetId, flatten:true})).sessionId;
    const cmd = (method, params={}) => cdp.send(method, params, session);
    const evalPage = async expression => (await cmd('Runtime.evaluate', {expression, returnByValue:true, awaitPromise:true})).result.value;
    const waitFor = async fn => { for (let i=0;i<100;i++) { if (await fn()) return; await new Promise(r=>setTimeout(r,100)); } throw Error('synthetic page timeout'); };
    await cmd('Page.enable'); await cmd('Page.navigate', {url:`http://127.0.0.1:${server.address().port}/rules`});
    await waitFor(() => evalPage('!!document.querySelector("#username")'));
    const fill = (selector, value) => evalPage(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));})()`);
    const click = selector => evalPage(`document.querySelector(${JSON.stringify(selector)}).click()`);
    await loginSyntheticStudent({fill, click, wait: waitFor, evaluate:evalPage}, fixture);
    assert.equal(await evalPage('!!document.querySelector("#question-home")'), true);
    assert.equal(logins, 1); assert.equal(otherPosts, 0);
    assert.equal((await probeSessionNetwork(evalPage)).reachable, true);
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
    assert.equal((await probeSessionNetwork(evalPage)).reachable, false);
  } finally {
    if (cdp) cdp.close();
    await stopOwnedBrowser(owned);
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
  }
});

test('只接受本轮 profile 的 DevToolsActivePort 与服务器匹配的 CDP', async () => {
  const profile = await mkdtemp(path.join(tmpdir(), 'e02-offline-'));
  let socketPath = '/devtools/browser/other';
  const server = createServer((req, res) => res.end(JSON.stringify({Browser: 'synthetic', webSocketDebuggerUrl: `ws://127.0.0.1:${server.address().port}${socketPath}`})));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const port = server.address().port;
    await writeFile(path.join(profile, 'DevToolsActivePort'), `${port}\n/devtools/browser/ours\n`);
    await assert.rejects(ownedDevToolsEndpoint(profile), /ownership/i);
    socketPath = '/devtools/browser/ours';
    const endpoint = await ownedDevToolsEndpoint(profile);
    assert.equal(endpoint.port, port);
    assert.equal(endpoint.version.webSocketDebuggerUrl, `ws://127.0.0.1:${port}${socketPath}`);
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); await rm(profile, {recursive:true, force:true}); }
});
