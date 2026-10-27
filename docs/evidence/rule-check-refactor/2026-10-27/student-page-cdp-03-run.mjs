// 仅本轮 CDP 验收；无安装依赖、无模型调用。所有协议原包只在内存，持久证据采用字段白名单。
import {spawn} from 'node:child_process';
import {mkdir, mkdtemp, readFile, writeFile, stat, rm, realpath} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
const tag=process.argv[2]||'03';assert(/^\d{2}$/.test(tag));
const root=process.cwd(), evidence=path.join(root,'docs/evidence/rule-check-refactor/2026-10-27/student-page-cdp-'+tag);
await mkdir(evidence); // 不覆盖旧证据。
const save=(name,data)=>writeFile(path.join(evidence,name),JSON.stringify(data,null,2)+'\n',{flag:'wx'});
const exists=async file=>{try{await stat(file);return true;}catch(e){if(e.code==='ENOENT')return false;throw e;}};
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const wait=async(check,label,timeout=15000)=>{const end=Date.now()+timeout;while(Date.now()<end){if(await check())return;await delay(100);}throw new Error('Timeout: '+label);};
const pageUrl='http://127.0.0.1:5174/rules', prefix='/api/zhku/controlled-preview/v2';
const normalize=url=>{const u=new URL(url);return u.pathname.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/ig,':opaque');};
const secrets=[],clean=text=>secrets.reduce((s,value)=>s.split(value).join('[REDACTED]'),String(text||'')).replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/ig,'[opaque]');
class CDP {
  constructor(url){this.ws=new WebSocket(url);this.pending=new Map();this.next=0;this.handlers=[];this.ws.addEventListener('message',event=>{const data=JSON.parse(String(event.data));if(data.id){const p=this.pending.get(data.id);if(p){clearTimeout(p.timer);this.pending.delete(data.id);data.error?p.reject(new Error('CDP '+data.error.code+': '+data.error.message)):p.resolve(data.result);}}else for(const handler of this.handlers)handler(data);});}
  async open(){if(this.ws.readyState!==1)await new Promise((resolve,reject)=>{this.ws.addEventListener('open',resolve,{once:true});this.ws.addEventListener('error',()=>reject(new Error('Local CDP WebSocket connection failed')),{once:true});});}
  send(method,params={},sessionId){const id=++this.next;return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{this.pending.delete(id);reject(new Error('CDP timeout: '+method));},method==='Page.captureScreenshot'?30000:10000);this.pending.set(id,{resolve,reject,timer});this.ws.send(JSON.stringify({id,method,params,...(sessionId?{sessionId}:{})}));});}
  close(){this.ws.close();for(const p of this.pending.values()){clearTimeout(p.timer);p.reject(new Error('CDP closed'));}this.pending.clear();}
}
const profile=await mkdtemp(path.join(tmpdir(),'zhku-cdp-03-'));
await writeFile(path.join(profile,'zhku-cdp-owner'),'student-page-cdp-03\n',{flag:'wx'});
const args=['--headless=new','--remote-debugging-address=127.0.0.1','--remote-debugging-port=19227','--user-data-dir='+profile,'--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-component-update','--disable-sync','--disable-extensions','--disable-features=OptimizationHints,MediaRouter,AutofillServerCommunication','--window-size=1440,1100','about:blank'];
const chrome=spawn('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',args,{stdio:'ignore'});
let cdp,session,success=false,failure=null;
const checks=[],network=[],consoleRows=[],pendingBodies=new Set(),requests=new Map(),bodyJobs=[];
let phase='SMOKE';
const command=(method,params={})=>cdp.send(method,params,session);
const evaluate=async expression=>{const result=await command('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(result.exceptionDetails)throw new Error('Page evaluation failed (details not persisted)');return result.result.value;};
const click=async selector=>{const rect=await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e||e.disabled)throw Error('Target unavailable');e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return{x:r.x+r.width/2,y:r.y+r.height/2};})()`);await command('Input.dispatchMouseEvent',{type:'mousePressed',...rect,button:'left',clickCount:1});await command('Input.dispatchMouseEvent',{type:'mouseReleased',...rect,button:'left',clickCount:1});};
const button=async text=>{const selector=await evaluate(`(()=>{const a=[...document.querySelectorAll('button')];const i=a.findIndex(e=>e.textContent.trim()===${JSON.stringify(text)});if(i<0)throw Error('Button not found');a[i].setAttribute('data-cdp-target','current');return 'button[data-cdp-target="current"]';})()`);await click(selector);await evaluate(`document.querySelector('[data-cdp-target="current"]')?.removeAttribute('data-cdp-target')`);};
const fill=async(selector,value)=>{await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e||e.disabled)throw Error('Input unavailable');e.focus();const p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(e,'');e.dispatchEvent(new Event('input',{bubbles:true}));})()`);await command('Input.insertText',{text:value});};
const text=()=>evaluate('document.body.innerText');
const has=async value=>(await text()).includes(value);
const checkpoint=async(name,data={})=>{checks.push({name,at:new Date().toISOString(),phase,...data});};
const shot=async name=>{assert.equal(await evaluate(`[...document.querySelectorAll('input[type="password"]')].some(e=>e.value.length>0)`),false,'Never capture a populated credential form');await evaluate('window.scrollTo(0,0)');const m=await command('Page.getLayoutMetrics');const image=await command('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip:{x:0,y:0,width:1440,height:Math.min(2600,Math.ceil(m.cssContentSize.height)),scale:1}});await writeFile(path.join(evidence,name),Buffer.from(image.data,'base64'),{flag:'wx'});};
const submit=async question=>{await button('新问题');await fill('#rule-question',question);await button('查证规则');await wait(()=>has('问题已受理，正在查证。'),'visible async progress');await checkpoint('visible-progress',{question});};
try {
  await wait(async()=>{try{return (await fetch('http://127.0.0.1:19227/json/version',{signal:AbortSignal.timeout(1000)})).ok;}catch{return false;}},'Chrome loopback CDP',20000);
  const version=await(await fetch('http://127.0.0.1:19227/json/version')).json();
  cdp=new CDP(version.webSocketDebuggerUrl);await cdp.open();
  const target=await cdp.send('Target.createTarget',{url:'about:blank'});session=(await cdp.send('Target.attachToTarget',{targetId:target.targetId,flatten:true})).sessionId;
  await command('Page.enable');await command('Runtime.enable');await command('Network.enable');await command('Log.enable');await command('Network.setCacheDisabled',{cacheDisabled:true});await command('Network.setBypassServiceWorker',{bypass:true});await command('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
  cdp.handlers.push(event=>{
    if(event.sessionId!==session)return;
    const p=event.params||{};
    if(event.method==='Network.requestWillBeSent'){
      // 不保存 postData/headers，不订阅 ExtraInfo；CSRF与Session完全留在浏览器正常协议内。
      if(p.request.url.startsWith('http://127.0.0.1:5174/')){const row={sequence:network.length+1,phase,method:p.request.method,route:normalize(p.request.url),resourceType:p.type,startedAt:new Date().toISOString()};network.push(row);requests.set(p.requestId,row);}
    }else if(event.method==='Network.responseReceived'){
      const row=requests.get(p.requestId);if(row){Object.assign(row,{status:p.response.status,mimeType:p.response.mimeType,remoteAddress:p.response.remoteIPAddress,remotePort:p.response.remotePort,fromServiceWorker:p.response.fromServiceWorker===true});if(row.route.startsWith(prefix+'/tasks/')&&row.method==='GET'&&p.response.status===200&&!row.route.includes('/requests/'))pendingBodies.add(p.requestId);}
    }else if(event.method==='Network.loadingFinished'&&pendingBodies.delete(p.requestId)){
      const row=requests.get(p.requestId);
      bodyJobs.push(command('Network.getResponseBody',{requestId:p.requestId}).then(body=>{
        const value=JSON.parse(body.body);
        if(row.route.includes('/originals/'))row.safeBody={title:clean(value.title),revision:value.revision,contextText:clean(value.contextText)};
        else row.safeBody={schemaVersion:value.schemaVersion,taskStatus:value.taskStatus,version:value.version,terminated:value.terminated,execution:value.execution?{mode:value.execution.mode,modelAttemptCount:value.execution.modelAttemptCount,providerResponseReceived:value.execution.providerResponseReceived}:null,result:value.result?{referenceRevision:value.result.referenceRevision,coverage:value.result.coverage,evidenceCount:value.result.evidence?.length,quotes:value.result.evidence?.map(e=>clean(e.quote)),claims:value.result.items?.flatMap(q=>q.claims?.map(c=>clean(c.text))||[]),issues:value.result.items?.flatMap(q=>q.issues?.map(i=>({kind:i.kind,reasonCode:i.reasonCode}))||[])}:null};
      }).catch(()=>{row.safeBodyRead='UNAVAILABLE';}));
    }else if(event.method==='Network.loadingFailed'){
      const row=requests.get(p.requestId);if(row)row.transportError=clean(p.errorText);
    }else if(event.method==='Runtime.exceptionThrown')consoleRows.push({phase,type:'runtime-exception',text:clean(p.exceptionDetails?.text),line:p.exceptionDetails?.lineNumber});
    else if(event.method==='Runtime.consoleAPICalled'&&['error','warning','assert'].includes(p.type))consoleRows.push({phase,type:p.type,argumentCount:p.args?.length,text:'Argument values deliberately not captured'});
    else if(event.method==='Log.entryAdded'&&['error','warning'].includes(p.entry.level))consoleRows.push({phase,type:p.entry.level,source:p.entry.source,text:clean(p.entry.text).replace(/https?:\/\/[^\s]+/g,'[resource-url]')});
  });
  const smokeServer=createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end('<!doctype html><title>Zhku CDP local smoke</title><button id="smoke" onclick="document.querySelector(\'h1\').textContent=\'CDP interaction confirmed\'">Test local interaction</button><h1>Pending</h1>');});
  await new Promise(resolve=>smokeServer.listen(0,'127.0.0.1',resolve));
  try{await command('Page.navigate',{url:'http://127.0.0.1:'+smokeServer.address().port+'/cdp-smoke'});await wait(()=>evaluate(`!!document.querySelector('#smoke')`),'loopback smoke document');await click('#smoke');}
  finally{smokeServer.closeAllConnections();await new Promise(resolve=>smokeServer.close(resolve));}
  const smoke=await evaluate('({title:document.title,url:location.href,text:document.querySelector("h1").textContent})');assert.equal(smoke.text,'CDP interaction confirmed');await shot('00-cdp-smoke.png');
  await save('cdp-ready.json',{browser:version.Browser,node:process.version,webSocket:typeof WebSocket,debugPort:19227,chromePid:chrome.pid,isolatedProfile:true,headless:true,smoke});
  console.log('CDP_SMOKE_PASS; awaiting explicit loopback validation and fixture readiness');
  await wait(()=>exists(path.join(evidence,'ALLOW-ACCEPT')),'external loopback listener validation',600000);
  const hostDirectory=process.argv[3]?path.resolve(process.argv[3]):path.join(evidence,'host');
  const hostReady=path.join(hostDirectory,'browser-ready.json');
  let host;
  // ready文件由既有Java夹具直接写入，存在不等于JSON已写完；等待完整内容，不改宿主业务。
  await wait(async()=>{try{const candidate=JSON.parse(await readFile(hostReady,'utf8'));if(!(await fetch(pageUrl,{signal:AbortSignal.timeout(1000)})).ok)return false;host=candidate;return true;}catch(error){if(error instanceof SyntaxError||error.code==='ENOENT'||error.name==='TimeoutError'||error instanceof TypeError)return false;throw error;}},'complete page and Java fixture ready',150000);
  assert.equal(host.executionMode,'TEST_SIMULATION_NO_PROVIDER');assert.equal(host.providerCalls,0);assert.equal(host.databaseCalls,0);
  phase='ANONYMOUS';await command('Page.navigate',{url:pageUrl});await wait(()=>evaluate(`!!document.querySelector('#username:not(:disabled)')`),'login form');assert(await has('本机 HTTP FIXTURE 验收'));await shot('01-login-empty.png');await checkpoint('fixture-disclosure-visible');
  // 合成口令只从已有测试源码读入内存，不出现在脚本、参数、日志、截图或证据中。
  const fixtureSource=await readFile(path.join(root,'knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentTaskHttpTest.java'),'utf8');
  const password=fixtureSource.match(/Arrays\.equals\(call\.getArgument\(1, char\[\]\.class\), "([^"]+)"\.toCharArray\(\)\)/)?.[1];assert(password,'Existing synthetic credential definition missing');secrets.push(password);
  phase='WRONG_LOGIN';await fill('#username','student-a');const invalid=password+'-invalid';secrets.push(invalid);await fill('input[type="password"]',invalid);await click('.rule-login button[type="submit"]');await wait(()=>has('登录未成功，请检查账号和密码。'),'wrong login error');await shot('02-login-rejected.png');await checkpoint('wrong-login-401-visible');
  phase='LOGIN';await fill('#username','student-a');await fill('input[type="password"]',password);await click('.rule-login button[type="submit"]');await wait(()=>evaluate(`!!document.querySelector('#rule-question:not(:disabled)')`),'student logged in');await checkpoint('student-login-complete');
  phase='E04';await fill('#rule-question','我那门选修挂了，下次直接再上一遍就好了吧？');await button('查证规则');await wait(()=>has('问题已受理，正在查证。'),'initial active state');await shot('03-active.png');await wait(()=>has('需要补充条件'),'clarification state');assert.equal(await evaluate(`document.querySelector('#rule-condition')?.value`),'');await shot('04-clarification.png');await checkpoint('waiting-user-visible');
  await evaluate(`(()=>{const e=document.querySelector('#rule-condition');e.value='PUBLIC';e.dispatchEvent(new Event('change',{bubbles:true}));})()`);await button('确认条件并继续');await wait(()=>has('有依据可确认'),'resolved answer');assert(await has('合成确认内容'));await click('.related-evidence summary');assert(await has('合成条款：仅用于接口验收。'));await button('核对原文');await wait(()=>has('合成手册'),'original response rendered');await click('#original-panel summary');
  const displayed=await evaluate(`({heading:document.querySelector('#decision-title').textContent,claim:document.querySelector('.rule-fact h3').textContent,quote:document.querySelector('.related-evidence blockquote').textContent,originalQuote:document.querySelector('#original-panel mark').textContent,fixtureDisclosed:!!document.querySelector('[data-testid="http-fixture-notice"]')})`);assert.equal(displayed.quote,displayed.originalQuote);await shot('05-answer-evidence-original.png');await checkpoint('answer-and-original-match',displayed);
  phase='E13';await submit('这学期公选补选今晚几点关？现在还能帮我补上吗？');await wait(()=>has('资料缺口'),'data gap');assert(await has('本次尚无可确认的规则内容。'));assert.equal(await evaluate(`document.querySelectorAll('.related-evidence').length`),0);await shot('06-data-gap.png');await checkpoint('data-gap-no-invented-answer');
  phase='FAILURE';await submit('故障');await wait(()=>has('本轮处理失败'),'fixture failure');assert(await has('处理未完成'));await shot('07-failure.png');await checkpoint('processing-failure-visible');
  phase='LOGOUT';await button('退出登录');await wait(()=>evaluate(`!!document.querySelector('#username:not(:disabled)')`),'logout');assert.equal(await evaluate(`document.querySelectorAll('.decision').length`),0);await checkpoint('logout-clears-result');
  // 因果负控：结束本轮 Java 租约后再刷新浏览器；不把伪造响应或页面mock当成真实宿主证据。
  phase='BACKEND_STOPPED';await writeFile(path.join(hostDirectory,'STOP'),'owned browser test complete\n',{flag:'wx'});await wait(()=>exists(path.join(hostDirectory,'browser-host-exit.json')),'Java host clean shutdown',45000);await command('Page.reload',{ignoreCache:true});await wait(()=>has('暂时无法核对登录状态，请稍后重试。'),'proxy backend unavailable UI');await shot('08-java-stopped-negative-control.png');await checkpoint('java-stop-breaks-real-page-api');
  await Promise.all(bodyJobs);assert(network.some(r=>r.phase==='LOGIN'&&r.route.endsWith('/auth/login')&&r.status===200));assert(network.some(r=>r.phase==='E04'&&r.safeBody?.taskStatus==='WAITING_USER'));assert(network.some(r=>r.phase==='E04'&&r.safeBody?.taskStatus==='RESOLVED'));assert(network.some(r=>r.phase==='BACKEND_STOPPED'&&r.route.endsWith('/auth/me')&&r.status>=500));assert.equal(consoleRows.filter(r=>r.type==='runtime-exception').length,0);
  success=true;await checkpoint('browser-acceptance-complete');
} catch(error){failure=clean(error.message);console.error('CDP_ACCEPTANCE_FAILED: '+failure);try{await shot('99-failure.png');}catch{}}
finally {
  if(cdp){try{await cdp.send('Browser.close');}catch{}cdp.close();}
  await wait(()=>chrome.exitCode!==null||chrome.signalCode!==null,'owned Chrome exit',10000).catch(()=>{chrome.kill();});
  const cleanup={chromePid:chrome.pid,chromeExitCode:chrome.exitCode,chromeSignal:chrome.signalCode,profileRemoved:false};
  // 删除前核对绝对路径、独立随机前缀和本轮owner标记，不涉及用户原有Chrome目录。
  const resolved=await realpath(profile);const parent=await realpath(tmpdir());assert.equal(path.dirname(resolved).toLowerCase(),parent.toLowerCase());assert(path.basename(resolved).startsWith('zhku-cdp-03-'));assert.equal(await readFile(path.join(resolved,'zhku-cdp-owner'),'utf8'),'student-page-cdp-03\n');
  if(chrome.exitCode!==null||chrome.signalCode!==null){await rm(resolved,{recursive:true,maxRetries:8,retryDelay:250});cleanup.profileRemoved=true;}
  await Promise.allSettled(bodyJobs);
  await save('browser-network.json',{source:'REAL_CHROME_CDP_NETWORK_WHITELIST',headersSaved:false,requestBodiesSaved:false,rawResponseBodiesSaved:false,requests:network});
  await save('browser-console.json',{source:'REAL_CHROME_CDP',consoleArgumentValuesSaved:false,events:consoleRows});
  await save('browser-result.json',{mode:'REAL_CHROME_WITH_JAVA_FIXTURE_NOT_REAL_MODEL',success,failure,checks,cleanup,providerCalls:0,embeddingCalls:0});
  console.log(JSON.stringify({browserAcceptance:success?'PASS':'FAIL',checks:checks.length,profileRemoved:cleanup.profileRemoved}));
}
process.exitCode=success?0:1;
