#!/usr/bin/env node
import {readFileSync, writeFileSync} from 'node:fs';
import {resolve, relative} from 'node:path';
import {pathToFileURL} from 'node:url';
import {validateDataset,validateRubric,createManifest,verifyManifest,runDataset,aggregate,sha256} from './engine.mjs';
import {syntheticAdapter} from './synthetic-adapter.mjs';

const USAGE='Commands: validate | create-manifest | verify | run | aggregate. See sprint02/README.md. Offline only.';
function parse(argv) {
  const [command,...args]=argv;const options={command,files:[]};
  const allowed=new Set(['root','out','dataset','rubric','manifest','expect-manifest-sha256','candidate-id','source-sha','mode','result','group']);
  for(let i=0;i<args.length;i++) {
    if(args[i].startsWith('--')) {
      const k=args[i].slice(2);if(!allowed.has(k)||options[k]!==undefined||!args[i+1]||args[i+1].startsWith('--'))throw new Error('INVALID_ARGUMENT');options[k]=args[++i];
    } else options.files.push(args[i]);
  }
  return options;
}
const json=path=>JSON.parse(readFileSync(path,'utf8').replace(/^\uFEFF/,''));
function save(path,value) {if(!path)throw new Error('OUTPUT_REQUIRED');writeFileSync(path,JSON.stringify(value,null,2)+'\n',{encoding:'utf8',flag:'wx'});}
function requireOptions(o,keys){if(keys.some(k=>!o[k]))throw new Error('MISSING_ARGUMENT');}
function checkedFile(root,name) {createManifest(root,[name]);return resolve(root,name);}
function loadPinnedManifest(o) {
  requireOptions(o,['root','manifest','expect-manifest-sha256']);
  const bytes=readFileSync(checkedFile(o.root,o.manifest));
  if(sha256(bytes)!==o['expect-manifest-sha256'])return {findings:[{severity:'ERROR',code:'MANIFEST_PIN_MISMATCH',path:'manifest'}]};
  const manifest=JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/,''));
  return {manifest,findings:verifyManifest(o.root,manifest)};
}

export async function runCli(argv,io={out:v=>process.stdout.write(v+'\n'),err:v=>process.stderr.write(v+'\n')}) {
  try {
    const o=parse(argv);
    if(o.command==='validate') {
      if(!o.dataset&&!o.rubric)throw new Error('INPUT_REQUIRED');
      const findings=[...(o.dataset?validateDataset(json(o.dataset)):[]),...(o.rubric?validateRubric(json(o.rubric)):[])];
      io.out(JSON.stringify({ok:!findings.length,findings}));return findings.length?1:0;
    }
    if(o.command==='create-manifest') {
      requireOptions(o,['root','out']);
      // manifest必须独立于被冻结文件；自身身份由Git记录或外部SHA固定，不能内嵌自哈希。
      const own=relative(resolve(o.root),resolve(o.out)).replaceAll('\\','/').toLowerCase();
      if(o.files.some(f=>f.toLowerCase()===own))throw new Error('SELF_FREEZE');
      const manifest=createManifest(o.root,o.files);save(o.out,manifest);
      io.out(JSON.stringify({ok:true,files:manifest.files.length,manifestSha256:sha256(readFileSync(o.out))}));return 0;
    }
    if(o.command==='verify'||o.command==='run') {
      const pinned=loadPinnedManifest(o);
      if(pinned.findings.length){io.out(JSON.stringify({ok:false,findings:pinned.findings}));return 1;}
      if(o.command==='verify'){io.out(JSON.stringify({ok:true,files:pinned.manifest.files.length}));return 0;}
      requireOptions(o,['dataset','rubric','mode','candidate-id','source-sha','out']);
      const entry=name=>pinned.manifest.files.find(f=>f.path===name);
      if(!entry(o.dataset)||!entry(o.rubric))throw new Error('INPUT_NOT_FROZEN');
      const frozenJSON=name=>{
        const bytes=readFileSync(checkedFile(o.root,name));
        // 用真正传给parser的同一份字节再次匹配冻结，避免verify与读取之间换文件。
        if(sha256(bytes)!==entry(name).sha256||bytes.length!==entry(name).bytes)throw new Error('INPUT_CHANGED_AFTER_VERIFY');
        return JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/,''));
      };
      const report=await runDataset(frozenJSON(o.dataset),frozenJSON(o.rubric),{
        mode:o.mode,group:o.group,candidateId:o['candidate-id'],sourceSha:o['source-sha'],datasetSha256:entry(o.dataset).sha256,rubricSha256:entry(o.rubric).sha256,
        adapter:o.mode==='synthetic'?syntheticAdapter:undefined,
      });
      report.manifestSha256=o['expect-manifest-sha256'];
      // 即使case失败也先保存全部记录，再以非零退出码报告；不可丢弃失败后只存绿例。
      save(o.out,report);io.out(JSON.stringify({ok:report.summary.byEngineeringStatus.FAIL===0&&report.summary.byEngineeringStatus.INSUFFICIENT_EVIDENCE===0,mode:report.mode,summary:report.summary,providerCalls:0}));
      return report.summary.byEngineeringStatus.FAIL||report.summary.byEngineeringStatus.INSUFFICIENT_EVIDENCE?1:0;
    }
    if(o.command==='aggregate') {
      requireOptions(o,['result']);io.out(JSON.stringify(aggregate(json(o.result))));return 0;
    }
    io.err(USAGE);return 2;
  } catch(error) {
    // 文件路径、JSON解析片段与原始异常可能携带正文或密钥，不直接打印message。
    io.err(JSON.stringify({ok:false,error:'CLI_INPUT_OR_OUTPUT_ERROR',findings:Array.isArray(error.findings)?error.findings:[]}));return 2;
  }
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)process.exitCode=await runCli(process.argv.slice(2));
