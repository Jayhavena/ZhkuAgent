// 对真实CDP与代理的白名单证据进行只读核对；不把失败轮次改写为通过。
import {readFile,writeFile,readdir,stat} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
const base=path.resolve('docs/evidence/rule-check-refactor/2026-10-27');
const dir=path.join(base,'student-page-cdp-'+(process.argv[2]||'07'));
const load=async p=>JSON.parse(await readFile(p,'utf8'));
const result=await load(path.join(dir,'browser-result.json'));
const network=await load(path.join(dir,'browser-network.json'));
const consoleData=await load(path.join(dir,'browser-console.json'));
const hostDirectory=process.argv[3]?path.resolve(process.argv[3]):path.join(dir,'host');
const host=await load(path.join(hostDirectory,'browser-host-exit.json'));
const proxy=(await readFile(path.join(base,'student-page-cdp-03/proxy-to-java.jsonl'),'utf8')).trim().split('\n').map(x=>JSON.parse(x));
assert.equal(result.success,true);assert.equal(result.cleanup.profileRemoved,true);assert.equal(host.backendListenerExited,true);assert.equal(host.workerExited,true);assert.equal(host.activeJobs,0);assert.equal(host.providerCalls,0);assert.equal(host.databaseCalls,0);
const start=Date.parse(result.checks[0].at)-1000;
const rows=network.requests.filter(r=>r.route.startsWith('/api/'));
const backend=proxy.filter(r=>Date.parse(r.startedAt)>=start);
for(const route of ['/auth/login','/tasks','/messages','/originals/'])assert(backend.some(r=>r.route.includes(route)&&r.status>=200&&r.status<300),'Missing real downstream route: '+route);
assert(backend.some(r=>r.errorCode==='ECONNREFUSED'));assert(rows.some(r=>r.phase==='BACKEND_STOPPED'&&r.status>=500));
assert(rows.filter(r=>r.status===200).every(r=>r.fromServiceWorker===false&&r.remoteAddress==='127.0.0.1'&&r.remotePort===5174));
assert.equal(consoleData.events.filter(e=>e.type==='runtime-exception').length,0);
const fixtureSource=await readFile('knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentTaskHttpTest.java','utf8');
const credential=fixtureSource.match(/Arrays\.equals\(call\.getArgument\(1, char\[\]\.class\), "([^"]+)"\.toCharArray\(\)\)/)?.[1];assert(credential);
let filesChecked=0;
for(const item of await readdir(base,{withFileTypes:true})){
  if(!item.name.startsWith('student-page-cdp-'))continue;
  const walk=async target=>{for(const entry of await readdir(target,{withFileTypes:true})){const p=path.join(target,entry.name);if(entry.isDirectory())await walk(p);else if(/\.(json|jsonl|log|mjs|txt)$/.test(entry.name)){const value=await readFile(p,'utf8');assert(!value.includes(credential),'Sensitive value found in this round artifact (value suppressed)');if(/browser-network|proxy-to-java/.test(entry.name))assert(!/"(?:headers|postData|cookie|set-cookie|continuationToken|originalToken|token)"\s*:/i.test(value),'Disallowed raw network field');filesChecked++;}}};
  if(item.isDirectory())await walk(path.join(base,item.name));else if(/\.(mjs|log)$/.test(item.name)){assert(!(await readFile(path.join(base,item.name),'utf8')).includes(credential),'Sensitive value found (suppressed)');filesChecked++;}
}
const screenshots=[];for(const name of await readdir(dir)){if(name.endsWith('.png')){const bytes=await readFile(path.join(dir,name));screenshots.push({name,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')});}}
assert(screenshots.some(s=>s.name==='05-answer-evidence-original.png'));assert(screenshots.some(s=>s.name==='08-java-stopped-negative-control.png'));
const review={verdict:'PASS_REAL_LOCAL_CHROME_WITH_JAVA_FIXTURE_ONLY',browserChecks:result.checks.length,browserApiRequests:rows.length,proxyToJavaRowsInFinalRun:backend.length,runtimeExceptions:0,expectedConsoleNetworkStatuses:[401,404,500],credentialValueScan:{passed:true,filesChecked,secretValuesPrinted:false},rawSensitiveNetworkFieldsAbsent:true,causalNegativeControl:'Owned Java stopped -> proxy ECONNREFUSED -> browser HTTP 500 -> unavailable-login UI',screenshots};
await writeFile(path.join(dir,'review.json'),JSON.stringify(review,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(review));
