import http from 'node:http'
import {readFileSync,writeFileSync,existsSync} from 'node:fs'
import {resolve,extname} from 'node:path'
const evidence=resolve('docs/evidence/t06-t07/2026-10-13/t07-browser')
const backend=JSON.parse(readFileSync(resolve(evidence,'browser-ready.json'),'utf8')).backendPort
const dist=resolve('agent/frontend/dist'),events=[]
const server=http.createServer((req,res)=>{
 const path=new URL(req.url,'http://127.0.0.1').pathname
 if(path.startsWith('/api/zhku/controlled-preview/v2/')){
  const proxy=http.request({hostname:'127.0.0.1',port:backend,path:req.url,method:req.method,headers:req.headers},reply=>{res.writeHead(reply.statusCode,reply.headers);const chunks=[];reply.on('data',d=>{res.write(d);if(path.includes('/tasks'))chunks.push(d)});reply.on('end',()=>{res.end();events.push({method:req.method,path,status:reply.statusCode,...(chunks.length?{actual:Buffer.concat(chunks).toString('utf8')}:{})});writeFileSync(resolve(evidence,'proxy-exchanges.json'),JSON.stringify({mode:'TEST_SYNTHETIC_HTTP_PROXY_NOT_NGINX',schemaVersion:3,sourceSha:'27fd47352d0c214a92a0e6d6fffa9376e2d1c824',events},null,2)+'\n')})})
  proxy.on('error',()=>{res.writeHead(503,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end('{"code":"HTTP_PROXY_UNAVAILABLE"}')});req.pipe(proxy);return
 }
 const file=path.startsWith('/assets/')?resolve(dist,'.'+path):resolve(dist,'index.html')
 if(!file.startsWith(dist)||!existsSync(file)){res.writeHead(404);res.end();return}
 res.writeHead(200,{'Content-Type':extname(file)==='.js'?'text/javascript':extname(file)==='.css'?'text/css':'text/html; charset=utf-8','Cache-Control':'no-store'});res.end(readFileSync(file))
})
server.listen(0,'127.0.0.1',()=>{writeFileSync(resolve(evidence,'frontend-ready.json'),JSON.stringify({frontendPort:server.address().port,backendPort:backend,mode:'TEST_SYNTHETIC_HTTP_PROXY_NOT_NGINX'})+'\n')})
const timer=setInterval(()=>{if(existsSync(resolve(evidence,'STOP'))){clearInterval(timer);server.close(()=>{writeFileSync(resolve(evidence,'frontend-exit.json'),'{"frontendListenerExited":true}\n');process.exit(0)})}},100)
