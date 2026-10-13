"""C05一次性真实选题采集；供应商授权与本地准备分开。"""
from pathlib import Path
from audit_rule_check_eval_assets import audit
from capture_rule_check_embeddings import run_bounded, credential, NoRedirect
import argparse
import hashlib
import json
import time
from datetime import datetime, timezone
from urllib.request import Request, build_opener

MODEL="qwen-plus"
ENDPOINT="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
BASE="docs/evidence/rule-check-refactor/2026-10-10/c05-topic-01"
BATCH=BASE+"/provider-capture"
PACKAGE=BASE+"/request-package.json"
PACKAGE_SHA="b8c336bf3674039f5f39f75953dd46e62a52f0f230f041e7984692c70e7c7930"

def require(okay):
    if not okay:raise ValueError("Selection capture contract violated")

def sha(raw):return hashlib.sha256(raw).hexdigest()

def unique_pairs(pairs):
    result={}
    for key,value in pairs:
        require(key not in result);result[key]=value
    return result

def strict_json(raw):return json.loads(raw,object_pairs_hook=unique_pairs)

def read(path):return strict_json(path.read_bytes())

def reject_credential_echo(value,key,depth=0):
    require(depth<64)
    if isinstance(value,str):
        require(key not in value)
        # content本身可以是第二层JSON；保留重复字段来扫描，不能只检查最后一个覆盖值。
        if value.lstrip().startswith(('{','[','"')):
            try:decoded=json.loads(value,object_pairs_hook=lambda pairs:pairs)
            except ValueError:return
            reject_credential_echo(decoded,key,depth+1)
    elif isinstance(value,dict):
        for name,child in value.items():
            reject_credential_echo(name,key,depth+1);reject_credential_echo(child,key,depth+1)
    elif isinstance(value,(list,tuple)):
        for child in value:reject_credential_echo(child,key,depth+1)

def save_new(path,value):
    with path.open('x',encoding='utf-8') as f:json.dump(value,f,ensure_ascii=False,indent=2,allow_nan=False)

def validate_response(response,options):
    require(isinstance(response,dict) and isinstance(response.get('model'),str) and response['model'].strip())
    choices=response.get('choices');require(isinstance(choices,list) and len(choices)==1)
    choice=choices[0];require(isinstance(choice,dict) and choice.get('index')==0 and choice.get('finish_reason')=='stop')
    message=choice.get('message');require(isinstance(message,dict) and message.get('role')=='assistant' and not message.get('tool_calls') and not message.get('function_call'))
    content=message.get('content');require(isinstance(content,str) and content.strip() and len(content.encode('utf-8'))<=1024)
    # 这里只是采集诊断；Java selector才是下游协议准入，任何合法ID也不能自证语义正确。
    code='INVALID_RESPONSE';rule_id=None
    try:
        inner=strict_json(content)
        if isinstance(inner,dict) and set(inner)=={'ruleId'}:
            value=inner['ruleId']
            if value is None:code='ABSTAINED'
            elif isinstance(value,str):code='SELECTED' if value in options else 'UNKNOWN_RULE';rule_id=value
    except (ValueError,TypeError):pass
    usage=response.get('usage');usage=usage if isinstance(usage,dict) else {}
    prompt,completion,total=(usage.get(k) for k in ['prompt_tokens','completion_tokens','total_tokens'])
    known=all(type(v) is int and v>=0 for v in [prompt,completion,total]) and prompt+completion==total
    return {'status':'RESPONSE_RECEIVED','rawModelContent':content,'observedModel':response['model'],'requestId':response.get('id'),
        'protocolDiagnostic':code,'ruleId':rule_id,'usageKnown':known,'promptTokens':prompt if known else None,
        'completionTokens':completion if known else None,'totalTokens':total if known else None,'humanSemanticGradeProduced':False}

def validate_batch_output(root,out):
    canonical=(root/BATCH).resolve()
    require(out.resolve()==canonical and not canonical.exists())
    approval_path=canonical.parent/'external-transfer-authorization.json'
    require(approval_path.is_file());approval=read(approval_path)
    require(approval.get('authority')=='DIRECT_USER_MESSAGE' and approval.get('decision')=='CONFIRMED'
        and approval.get('endpoint')==ENDPOINT and approval.get('model')==MODEL and approval.get('maxCalls')==24
        and approval.get('packagePath')==PACKAGE and approval.get('packageSha256')==PACKAGE_SHA
        and approval.get('reportQueriesAllowed') is False and approval.get('databaseCredentialsAllowed') is False)
    package=root/PACKAGE;require(package.is_file())
    # 校验并返回同一份字节；后续磁盘变化不能替换已授权的发送内容。
    raw=package.read_bytes();require(sha(raw)==PACKAGE_SHA)
    return raw

def request_selection(item,notify):
    key=credential()  # 每次外发重新查进程/用户/系统环境；密钥不进入参数、请求包或证据。
    raw_body=json.dumps(item['body'],ensure_ascii=False,allow_nan=False).encode('utf-8');require(len(raw_body)<=65536)
    request=Request(ENDPOINT,data=raw_body,headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'},method='POST')
    notify()
    with build_opener(NoRedirect()).open(request,timeout=5) as reply:
        raw=reply.read(16385);require(len(raw)<=16384)
    # 不保存HTTP错误正文；即使成功响应异常回显凭证，也拒绝把它作为证据落盘。
    require(key.encode('utf-8') not in raw)
    options={r['ruleId'] for r in strict_json(item['body']['messages'][1]['content'])['options']}
    observed=strict_json(raw);reject_credential_echo(observed,key)
    record=validate_response(observed,options);reject_credential_echo(record,key)
    return record

def capture(root,out,runner=None):
    raw_package=validate_batch_output(root,out)
    package=strict_json(raw_package);rows=package.get('requests')
    require(isinstance(rows,list) and len(rows)==24 and [r['caseId'] for r in rows]==[f'Q{i:02}' for i in range(1,21)]+[f'O{i:02}' for i in range(1,5)])
    gate=audit(root,root.parent/'SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf')
    require(gate['validationPassed'] and gate['freezeGatePassed'])
    protected=[root/PACKAGE,Path(__file__).resolve(),Path(__file__).resolve().with_name('capture_rule_check_embeddings.py')]
    before={str(p):sha(raw_package) if p==root/PACKAGE else sha(p.read_bytes()) for p in protected}
    require(sha((root/PACKAGE).read_bytes())==PACKAGE_SHA)
    out.mkdir(parents=True,exist_ok=False)
    # 在首次调用前独占批次，失败/超时也消费准入尝试；禁止换目录重置次数。
    save_new(out/'batch-claim.json',{'status':'CLAIMED_BEFORE_NETWORK','maxCalls':24,'retryCount':0,'singleUse':True})
    save_new(out/'preflight.json',{'endpoint':ENDPOINT,'model':MODEL,'packageSha256':PACKAGE_SHA,'sourceHashes':before,
        'freezeGate':gate,'perRequestDeadlineSeconds':5,'overallDeadlineSeconds':150,'outerResponseBytes':16384,'innerResponseBytes':1024})
    execute=run_bounded if runner is None else runner;records=[];started=time.monotonic()
    for item in rows:
        remaining=150-(time.monotonic()-started)
        if remaining<=0:break
        begin=time.monotonic()
        try:
            require(sha((root/PACKAGE).read_bytes())==PACKAGE_SHA)
            observed=execute(request_selection,item,min(5,remaining))
        except Exception as error:observed={'status':'ERROR','errorType':type(error).__name__,'actualHttpAttempt':False,'usageKnown':False}
        value={'caseId':item['caseId'],'kind':item['kind'],'elapsedMs':(time.monotonic()-begin)*1000,**observed}
        target=out/(item['caseId']+'.json');save_new(target,value)
        records.append({'caseId':item['caseId'],'path':target.name,'sha256':sha(target.read_bytes()),'status':value['status'],
            'observedHttpAttemptSignal':value.get('actualHttpAttempt',False),'usageKnown':value.get('usageKnown',False),'totalTokens':value.get('totalTokens')})
        if value['status']!='RESPONSE_RECEIVED':break
    after={p:sha(Path(p).read_bytes()) for p in before}
    received=sum(r['status']=='RESPONSE_RECEIVED' for r in records)
    summary={'recordedAt':datetime.now(timezone.utc).isoformat(),'recordsProcessed':len(records),'responseRecords':received,
        'completedAllRequests':len(records)==received==24,'records':records,'observedHttpAttemptSignals':sum(r['observedHttpAttemptSignal'] for r in records),
        'attemptSignalLimit':'事件是观测下界；超时前未读到事件不证明没有网络尝试，已准入仍占额度',
        'knownTokens':sum(r['totalTokens'] for r in records if r['usageKnown']),'allUsageKnown':len(records)==24 and all(r['usageKnown'] for r in records),
        'retryCount':0,'sourceUnchanged':before==after,'humanSemanticGradeProduced':False,'reportCasesConsumed':0,'defaultSwitched':False}
    save_new(out/'summary.json',summary);require(before==after)
    return summary

def main():
    root=Path(__file__).resolve().parents[2]
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--capture',action='store_true');args=parser.parse_args()
    require(args.capture)
    try:
        summary=capture(root,root/BATCH)
        print(json.dumps({k:summary[k] for k in ['recordsProcessed','completedAllRequests','knownTokens','allUsageKnown','sourceUnchanged']},ensure_ascii=False))
        return 0 if summary['completedAllRequests'] else 2
    except Exception as failure:
        print(json.dumps({'status':'STOPPED','errorType':type(failure).__name__}))
        return 2

if __name__=='__main__':raise SystemExit(main())
