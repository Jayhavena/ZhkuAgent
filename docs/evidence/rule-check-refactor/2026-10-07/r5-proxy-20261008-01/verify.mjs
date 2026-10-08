import assert from 'node:assert/strict'
const web='http://127.0.0.1:58480',backend='http://127.0.0.1:58184'
let total=0
const test=async(name,action)=>{await action();total++;process.stdout.write(`PASS ${name}\n`)}
await test('v2 routes preserve URI and upstream status/body',async()=>{
  const response=await fetch(web+'/api/zhku/controlled-preview/v2/auth/csrf')
  assert.equal(response.status,403);assert.equal((await response.json()).code,'FIXTURE_DENIED');assert.match(response.headers.get('cache-control'),/no-store/)
})
await test('v1 compatibility preserves URI',async()=>{
  const response=await fetch(web+'/api/zhku/controlled-preview/v1/auth/csrf');assert.equal(response.status,403);assert.equal((await response.json()).code,'FIXTURE_DENIED')
})
await test('unsupported namespace and bare prefix stay 404 without upstream calls',async()=>{
  const before=(await(await fetch(backend+'/fixture-calls')).json()).length
  for(const path of ['/api','/api/zhku/controlled-preview/v1','/api/zhku/controlled-preview/v2','/api/zhku/local-admin/sources','/api/zhku/local-preview/chat'])assert.equal((await fetch(web+path,{redirect:'manual'})).status,404)
  assert.equal((await(await fetch(backend+'/fixture-calls')).json()).length,before)
})
await test('v2 POST is forwarded once and never replayed',async()=>{
  const response=await fetch(web+'/api/zhku/controlled-preview/v2/rule-check',{method:'POST',headers:{'Content-Type':'application/json'},body:'{"question":"合成代理问题","confirmedConditions":{}}'})
  assert.equal(response.status,403);assert.equal((await response.json()).message,'合成代理拒绝')
  const calls=await(await fetch(backend+'/fixture-calls')).json();assert.equal(calls.filter(x=>x.method==='POST').length,1)
  assert.equal(calls.at(-1).path,'/api/zhku/controlled-preview/v2/rule-check')
})
await test('SPA route loads but absent assets are real 404',async()=>{
  const response=await fetch(web+'/rules/');assert.equal(response.status,200);assert.match(await response.text(),/<div id="app"><\/div>/)
  assert.equal((await fetch(web+'/assets/not-a-real-file.js')).status,404)
})
process.stdout.write(`PROXY checks=${total} passed=true upstream=NODE_FIXTURE java=false database=false provider=false\n`)
