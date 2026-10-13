"""第三包一次性冻结报告选题采集；16题分母保留，不复用旧预算，不按结果调运行代码。"""
from pathlib import Path
import argparse
import time
import json
from datetime import datetime, timezone
from capture_rule_check_selection import require, sha, read, strict_json, save_new, request_selection, ENDPOINT, MODEL
from capture_rule_check_embeddings import run_bounded
from audit_rule_check_eval_assets import audit

BASE='docs/evidence/rule-check-refactor/2026-10-10/package-03'
PACKAGE=BASE+'/request-package.json'
# 请求准备完成后写入确切指纹；占位状态绝不允许外发。
PACKAGE_SHA='1a93f2bc1925a6c1abe985f5959757bdd166e63fdf70f5758badadb23423ab3c'

def frozen(root, pack):
    freeze=root/BASE/'system-freeze.json'
    require(sha(freeze.read_bytes())==pack['systemFreezeSha256'])
    state=read(freeze)
    require(state['status']=='FROZEN_BEFORE_REPORT_QUESTION_PREPARATION' and state['files'])
    for row in state['files']:
        target=(root/row['path']).resolve()
        require(target.is_relative_to(root.resolve()) and sha(target.read_bytes())==row['sha256'])

def admitted(root):
    base=root/BASE;require(not (base/'provider-capture').exists())
    raw=(root/PACKAGE).read_bytes();require(sha(raw)==PACKAGE_SHA)
    pack=strict_json(raw);rows=pack['requests'];blocked=pack['localBlockedCases']
    require(pack['reportDenominator']==16 and 0<len(rows)<=16 and pack['maxCalls']==len(rows)
        and pack['retryCount']==0 and pack['maxOutputTokens']==128)
    ids=[r['caseId'] for r in rows+blocked]
    require(len(ids)==16 and set(ids)=={f'R{i:02}' for i in range(1,17)})
    questions=pack['questionProjection'];require(len(questions)==16)
    question_map={r['caseId']:r['question'] for r in questions};require(len(question_map)==16)
    authorization=read(base/'external-transfer-authorization.json')
    require(authorization.get('authority')=='DIRECT_USER_MESSAGE' and authorization.get('decision')=='CONFIRMED'
        and authorization.get('endpoint')==ENDPOINT and authorization.get('model')==MODEL
        and authorization.get('maxCalls')==len(rows) and authorization.get('packagePath')==PACKAGE
        and authorization.get('packageSha256')==PACKAGE_SHA and authorization.get('reportQueriesAllowed') is True
        and authorization.get('referenceAnswersAllowed') is False and authorization.get('databaseCredentialsAllowed') is False)
    for row in rows:
        body=row['body'];require(body['model']==MODEL and body['max_tokens']==128 and body['temperature']==0
            and body['stream'] is False and body['enable_thinking'] is False and len(body['messages'])==2
            and body['response_format']=={'type':'json_object'})
        require(body['messages'][0]['role']=='system' and body['messages'][1]['role']=='user')
        payload=strict_json(body['messages'][1]['content'])
        require(set(payload)=={'question','options'} and payload['question']==question_map[row['caseId']]
            and 1<=len(payload['options'])<=26)
        for option in payload['options']:require(set(option)=={'ruleId','questionScope'})
        require(len({o['ruleId'] for o in payload['options']})==len(payload['options']))
    frozen(root,pack);return pack

def capture(root, runner=None, asset_audit=None):
    pack=admitted(root)
    gate=(audit if asset_audit is None else asset_audit)(root,root.parent/'SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf')
    require(gate['validationPassed'] and gate['freezeGatePassed'])
    out=root/BASE/'provider-capture';out.mkdir(exist_ok=False)
    save_new(out/'batch-claim.json',{'status':'CLAIMED_BEFORE_NETWORK','maxCalls':len(pack['requests']),'retryCount':0,'singleUse':True})
    protected=[root/PACKAGE,Path(__file__).resolve(),Path(__file__).resolve().with_name('capture_rule_check_selection.py'),
        Path(__file__).resolve().with_name('capture_rule_check_embeddings.py')]
    hashes={str(p):sha(p.read_bytes()) for p in protected}
    save_new(out/'preflight.json',{'endpoint':ENDPOINT,'model':MODEL,'packageSha256':PACKAGE_SHA,'sourceHashes':hashes,
        'freezeGate':gate,'deadlineSeconds':150,'perRequestDeadlineSeconds':5,'reportDenominator':16,'localBlockedCount':len(pack['localBlockedCases'])})
    records=[];started=time.monotonic();execute=run_bounded if runner is None else runner
    for row in pack['requests']:
        remaining=150-(time.monotonic()-started)
        if remaining<=0:break
        require(sha((root/PACKAGE).read_bytes())==PACKAGE_SHA);frozen(root,pack)
        begin=time.monotonic()
        try:value=execute(request_selection,row,min(5,remaining))
        except Exception as error:value={'status':'ERROR','errorType':type(error).__name__,'actualHttpAttempt':False,'usageKnown':False}
        value={'caseId':row['caseId'],'kind':row['kind'],'elapsedMs':(time.monotonic()-begin)*1000,**value}
        target=out/(row['caseId']+'.json');save_new(target,value)
        records.append({'caseId':row['caseId'],'path':target.name,'sha256':sha(target.read_bytes()),'status':value['status'],
            'observedHttpAttemptSignal':value.get('actualHttpAttempt',False),'usageKnown':value.get('usageKnown',False),'totalTokens':value.get('totalTokens')})
        # 失败/超时占用一次额度，保留缺失案例；不能以重试或删题提高成绩。
        if value['status']!='RESPONSE_RECEIVED':break
    frozen(root,pack);unchanged=all(sha(Path(p).read_bytes())==digest for p,digest in hashes.items())
    total=len(pack['requests'])
    summary={'recordedAt':datetime.now(timezone.utc).isoformat(),'recordsProcessed':len(records),'records':records,
        'responseRecords':sum(r['status']=='RESPONSE_RECEIVED' for r in records),
        'completedAllRequests':len(records)==total and all(r['status']=='RESPONSE_RECEIVED' for r in records),
        'reportDenominator':16,'localBlockedCount':len(pack['localBlockedCases']),'retryCount':0,'sourceUnchanged':unchanged,
        'knownTokens':sum(r['totalTokens'] for r in records if r['usageKnown']),
        'allUsageKnown':len(records)==total and all(r['usageKnown'] for r in records),
        'humanSemanticGradesProduced':False,'defaultSwitched':False}
    save_new(out/'summary.json',summary);require(unchanged);return summary

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--capture',action='store_true');args=parser.parse_args();require(args.capture)
    try:
        summary=capture(Path(__file__).resolve().parents[2]);print(json.dumps({k:summary[k] for k in ['recordsProcessed','completedAllRequests','knownTokens','sourceUnchanged']}))
        return 0 if summary['completedAllRequests'] else 2
    except Exception as failure:
        print(json.dumps({'status':'STOPPED','errorType':type(failure).__name__}));return 2

if __name__=='__main__':raise SystemExit(main())
