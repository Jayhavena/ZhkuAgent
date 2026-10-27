// 本次验收专用观测：只记录代理到回环 Java 的方法、去标识路径和状态；不读取头、正文或凭据。
import http from 'node:http';
import {syncBuiltinESMExports} from 'node:module';
import {appendFileSync} from 'node:fs';
const destination=process.env.ZHKU_CDP_PROXY_AUDIT;
if(!destination) throw new Error('Explicit audit destination required');
const base='/api/zhku/controlled-preview/v2';
const route=path=>String(path||'').split('?')[0].replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/ig,':opaque');
const original=http.request;
let sequence=0;
http.request=function(...args){
  const first=args[0], supplied=typeof first==='object'&&!(first instanceof URL)?first:(typeof args[1]==='object'?args[1]:{});
  const url=first instanceof URL?first:typeof first==='string'?new URL(first):null;
  const host=supplied.hostname||supplied.host||url?.hostname;
  const port=Number(supplied.port||url?.port||80);
  const path=supplied.path||url?.pathname||'';
  const watched=host==='127.0.0.1'&&port===18087&&path.startsWith(base);
  const row=watched?{sequence:++sequence,startedAt:new Date().toISOString(),destination:'127.0.0.1:18087',method:supplied.method||'GET',route:route(path)}:null;
  const req=Reflect.apply(original,this,args);
  if(row){
    let written=false;
    const record=fields=>{if(written)return;written=true;appendFileSync(destination,JSON.stringify({...row,...fields})+'\n',{encoding:'utf8'});};
    req.once('response',res=>record({status:res.statusCode}));
    req.once('error',error=>record({errorCode:['ECONNREFUSED','ECONNRESET','ETIMEDOUT'].includes(error.code)?error.code:'PROXY_TRANSPORT_ERROR'}));
  }
  return req;
};
syncBuiltinESMExports();
