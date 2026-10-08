import http from 'node:http'
const calls = []
// 仅代理工程夹具，无Java、身份、数据库或规则能力；固定403验证错误不被代理重放/伪装。
const server = http.createServer((req,res) => {
  if (req.url === '/fixture-calls') { res.setHeader('Content-Type','application/json');res.end(JSON.stringify(calls));return }
  calls.push({ method:req.method,path:req.url })
  res.writeHead(403,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'})
  res.end(JSON.stringify({code:'FIXTURE_DENIED',message:'合成代理拒绝'}))
})
server.listen(58184,'127.0.0.1',()=>process.stdout.write('PROXY_FIXTURE_READY port=58184 loopback=true\n'))
const stop=()=>server.close(()=>process.exit(0))
process.on('SIGINT',stop);process.on('SIGTERM',stop)
setTimeout(stop,15*60*1000).unref()
