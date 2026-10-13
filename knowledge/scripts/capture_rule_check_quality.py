"""质量收口单批采集：保留完整分母，不重试，不把计划格式正确当成任务成功。"""
from pathlib import Path
import argparse
import json
import time
from datetime import datetime, timezone
from urllib.request import Request, build_opener
from capture_rule_check_selection import require, sha, strict_json, read, save_new, reject_credential_echo, ENDPOINT, MODEL
from capture_rule_check_embeddings import credential, NoRedirect, run_bounded

BASE='docs/evidence/rule-check-quality/2026-10-11'
PACKAGE=BASE+'/request-package.json'
PACKAGE_SHA='cea598a7053a16abae4d7d1611a0a41f0940c84eb31b77fea86139b39eab2441'
FINAL_BASE='docs/evidence/rule-check-final/2026-10-11'

def batch_base(root, directory):
    # 只开放明确的旧批次与最终批次；目录参数不能绕过载荷和人工许可绑定。
    require(directory in (BASE,FINAL_BASE))
    target=(root/directory).resolve()
    require(target.is_relative_to((root/'docs/evidence').resolve()))
    return target

def frozen(root, directory=BASE):
    state=read(batch_base(root,directory)/'source-freeze.json')
    require(state['status']=='FROZEN_BEFORE_NEW_MODEL_OUTPUTS' and state['files'])
    for row in state['files']:
        target=(root/row['path']).resolve()
        require(target.is_relative_to(root.resolve()) and sha(target.read_bytes())==row['sha256'])

def admitted(root, expected_sha=PACKAGE_SHA, directory=BASE):
    base=batch_base(root,directory);package_path=directory+'/request-package.json'
    require(len(expected_sha)==64 and not (base/'provider-capture').exists())
    raw=(root/package_path).read_bytes();require(sha(raw)==expected_sha)
    pack=strict_json(raw);rows=pack['requests'];blocked=pack['localBlockedCases']
    require(pack['endpoint']==ENDPOINT and pack['model']==MODEL and 0<len(rows)<=43 and pack['maxCalls']==len(rows)
        and pack['retryCount']==0 and pack['maxOutputTokens']==512)
    final=directory==FINAL_BASE
    require(pack['regressionDenominator']==16 and pack['confirmationDenominator']==24 and pack['repeatDenominator']==(0 if final else 3))
    expected=({f'R{i:02}' for i in range(1,17)}|{f'N{i:02}' for i in range(25,49)}) if final else ({f'R{i:02}' for i in range(1,17)}|{f'N{i:02}' for i in range(1,25)}|{f'T{i:02}' for i in range(1,4)})
    ids=[r['caseId'] for r in rows+blocked];require(len(ids)==len(expected) and set(ids)==expected)
    if final:
        require(len(rows)==40 and not blocked)
        reference=read(base/'reference-confirmation.json')
        require(reference.get('authority')=='DIRECT_USER_MESSAGE' and reference.get('decision')=='CONFIRMED'
            and set(reference.get('confirmedDecisions',[]))=={'D01','D02','D03'}
            and reference.get('referenceSha256')==sha((base/'forty-reference-confirmed.json').read_bytes())
            and reference.get('scoringContractSha256')==sha((base/'scoring-contract-confirmed.json').read_bytes()))
    projection={r['caseId']:r['question'] for r in pack['questionProjection']};require(set(projection)==expected)
    authorization=read(base/'external-transfer-authorization.json')
    require(authorization.get('authority')=='DIRECT_USER_MESSAGE' and authorization.get('decision')=='CONFIRMED'
        and authorization.get('endpoint')==ENDPOINT and authorization.get('model')==MODEL and authorization.get('maxCalls')==len(rows)
        and authorization.get('packagePath')==package_path and authorization.get('packageSha256')==expected_sha
        and authorization.get('reportQueriesAllowed') is True and authorization.get('referenceAnswersAllowed') is False
        and authorization.get('databaseCredentialsAllowed') is False)
    for row in rows:
        body=row['body'];require(body['model']==MODEL and body['max_tokens']==512 and body['temperature']==0
            and body['stream'] is False and body['enable_thinking'] is False and body['response_format']=={'type':'json_object'}
            and len(body['messages'])==2 and len(json.dumps(body,ensure_ascii=False).encode())<=65536)
        require(body['messages'][0]['role']=='system' and body['messages'][1]['role']=='user')
        if final:require(body['messages'][0]['content']==(base/'system-prompt.txt').read_text(encoding='utf-8'))
        payload=strict_json(body['messages'][1]['content'])
        require(set(payload)=={'question','evidenceOptions'} and payload['question']==projection[row['caseId']]
            and 1<=len(payload['evidenceOptions'])<=26)
        for option in payload['evidenceOptions']:
            require(set(option)=={'ruleId','questionScope','verifiedFacts','actions','operands'})
        require(len({o['ruleId'] for o in payload['evidenceOptions']})==len(payload['evidenceOptions']))
    frozen(root,directory);return pack

def validate_response(response):
    require(isinstance(response,dict) and isinstance(response.get('model'),str) and response['model'].strip())
    choices=response.get('choices');require(isinstance(choices,list) and len(choices)==1)
    choice=choices[0];require(choice.get('index')==0 and choice.get('finish_reason')=='stop')
    message=choice.get('message');require(isinstance(message,dict) and message.get('role')=='assistant'
        and not message.get('tool_calls') and not message.get('function_call'))
    content=message.get('content');require(isinstance(content,str) and content.strip() and len(content.encode('utf-8'))<=4096)
    diagnostic='INVALID_RESPONSE'
    try:
        inner=strict_json(content)
        if isinstance(inner,dict) and set(inner)=={'tasks'} and isinstance(inner['tasks'],list) and 1<=len(inner['tasks'])<=4:
            if all(isinstance(t,dict) and set(t)=={'ruleId','action','operand','value'} and all(isinstance(v,str) for v in t.values()) for t in inner['tasks']):
                diagnostic='STRUCTURE_ONLY_VALIDATED'
    except (ValueError,TypeError):pass
    # Java下游仍须检查本轮ID、动作与操作数；合法JSON不能自证语义或资料资格。
    usage=response.get('usage');usage=usage if isinstance(usage,dict) else {}
    prompt,completion,total=(usage.get(k) for k in ['prompt_tokens','completion_tokens','total_tokens'])
    known=all(type(v) is int and v>=0 for v in (prompt,completion,total)) and prompt+completion==total
    return {'status':'RESPONSE_RECEIVED','rawModelContent':content,'observedModel':response['model'],'requestId':response.get('id'),
        'protocolDiagnostic':diagnostic,'usageKnown':known,'promptTokens':prompt if known else None,
        'completionTokens':completion if known else None,'totalTokens':total if known else None,'humanSemanticGradeProduced':False}

def request_plan(item,notify):
    key=credential()  # 每一次操作重新读取；请求包和落盘证据永远不含密钥。
    body=json.dumps(item['body'],ensure_ascii=False,allow_nan=False).encode('utf-8');require(len(body)<=65536)
    request=Request(ENDPOINT,data=body,headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'},method='POST')
    notify()
    with build_opener(NoRedirect()).open(request,timeout=5) as reply:
        raw=reply.read(32769);require(len(raw)<=32768)
    require(key.encode('utf-8') not in raw)
    value=strict_json(raw);reject_credential_echo(value,key)
    record=validate_response(value);reject_credential_echo(record,key);return record

def capture(root,runner=None,directory=BASE,expected_sha=PACKAGE_SHA):
    pack=admitted(root,expected_sha,directory);out=batch_base(root,directory)/'provider-capture';out.mkdir(exist_ok=False)
    save_new(out/'batch-claim.json',{'status':'CLAIMED_BEFORE_NETWORK','maxCalls':len(pack['requests']),'retryCount':0,'singleUse':True})
    records=[];started=time.monotonic();execute=run_bounded if runner is None else runner
    for row in pack['requests']:
        remaining=300-(time.monotonic()-started)
        if remaining<=0:break
        frozen(root,directory);begin=time.monotonic()
        try:value=execute(request_plan,row,min(5,remaining))
        except Exception as error:value={'status':'ERROR','errorType':type(error).__name__,'actualHttpAttempt':False,'usageKnown':False}
        value={'caseId':row['caseId'],'kind':row['kind'],'elapsedMs':(time.monotonic()-begin)*1000,**value}
        target=out/(row['caseId']+'.json');save_new(target,value)
        records.append({'caseId':row['caseId'],'path':target.name,'sha256':sha(target.read_bytes()),'status':value['status'],
            'observedHttpAttemptSignal':value.get('actualHttpAttempt',False),'usageKnown':value.get('usageKnown',False),'totalTokens':value.get('totalTokens')})
        # 失败也占本批一次额度；剩余题保持缺失，不能换目录重试或删掉分母。
        if value['status']!='RESPONSE_RECEIVED':break
    frozen(root,directory)
    summary={'recordedAt':datetime.now(timezone.utc).isoformat(),'recordsProcessed':len(records),'records':records,
        'completedAllRequests':len(records)==len(pack['requests']) and all(r['status']=='RESPONSE_RECEIVED' for r in records),
        'regressionDenominator':16,'confirmationDenominator':24,'repeatDenominator':pack['repeatDenominator'],'localBlockedCount':len(pack['localBlockedCases']),
        'retryCount':0,'sourceUnchanged':True,'knownTokens':sum(r['totalTokens'] for r in records if r['usageKnown']),
        'allUsageKnown':len(records)==len(pack['requests']) and all(r['usageKnown'] for r in records),
        'humanSemanticGradesProduced':False,'defaultSwitched':False}
    save_new(out/'summary.json',summary);return summary

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--capture',action='store_true')
    parser.add_argument('--batch-directory',choices=[BASE,FINAL_BASE],default=BASE)
    parser.add_argument('--package-sha256',default=PACKAGE_SHA)
    args=parser.parse_args();require(args.capture)
    try:
        summary=capture(Path(__file__).resolve().parents[2],directory=args.batch_directory,expected_sha=args.package_sha256);print(json.dumps({k:summary[k] for k in ('recordsProcessed','completedAllRequests','knownTokens','sourceUnchanged')}))
        return 0 if summary['completedAllRequests'] else 2
    except Exception as failure:
        print(json.dumps({'status':'STOPPED','errorType':type(failure).__name__}));return 2

if __name__=='__main__':raise SystemExit(main())
