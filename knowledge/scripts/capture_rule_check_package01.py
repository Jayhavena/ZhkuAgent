"""第一包一次性收窄选题采集；旧授权、重试及换目录重置预算均不允许。"""
from pathlib import Path
import argparse
import json
import time
from datetime import datetime, timezone
from capture_rule_check_selection import (
    require, sha, read, strict_json, save_new, request_selection, ENDPOINT, MODEL
)
from capture_rule_check_embeddings import run_bounded
from audit_rule_check_eval_assets import audit

BASE="docs/evidence/rule-check-refactor/2026-10-10/package-01"
PACKAGE=BASE+"/request-package.json"
PACKAGE_SHA="62d759d92163ea17aaa985bb1dae73b7a0208e6184df2b2bd58ffdec159bf168"
MAX_CALLS=20

def admitted(root):
    base=root/BASE
    require(not (base/'provider-capture').exists())
    authorization=read(base/'external-transfer-authorization.json')
    require(authorization.get('authority')=='DIRECT_USER_MESSAGE' and authorization.get('decision')=='CONFIRMED'
        and authorization.get('endpoint')==ENDPOINT and authorization.get('model')==MODEL
        and authorization.get('maxCalls')==MAX_CALLS and authorization.get('packageSha256')==PACKAGE_SHA
        and authorization.get('packagePath')==PACKAGE and authorization.get('reportQueriesAllowed') is False
        and authorization.get('databaseCredentialsAllowed') is False)
    raw=(root/PACKAGE).read_bytes();require(sha(raw)==PACKAGE_SHA)
    pack=strict_json(raw);rows=pack['requests'];blocked=pack['localBlockedCases']
    require(len(rows)==MAX_CALLS and len(blocked)==4)
    require([r['caseId'] for r in blocked]==['Q02','O01','O02','O03'])
    require([r['caseId'] for r in rows]==[f'Q{i:02}' for i in range(1,21) if i!=2]+['O04'])
    for row in rows:
        body=row['body'];require(body['model']==MODEL and body['max_tokens']==128 and body['temperature']==0
            and body['stream'] is False and body['enable_thinking'] is False and len(body['messages'])==2
            and body['response_format']=={'type':'json_object'})
        payload=strict_json(body['messages'][1]['content'])
        require(payload['options'] and set(payload)=={'question','options'})
    return pack

def capture(root,runner=None,asset_audit=None):
    pack=admitted(root)
    gate=(audit if asset_audit is None else asset_audit)(root,root.parent/'SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf')
    require(gate['validationPassed'] and gate['freezeGatePassed'])
    out=root/BASE/'provider-capture';out.mkdir(exist_ok=False)
    save_new(out/'batch-claim.json',{'status':'CLAIMED_BEFORE_NETWORK','maxCalls':MAX_CALLS,'retryCount':0,'singleUse':True})
    protected=[root/PACKAGE,Path(__file__).resolve(),Path(__file__).resolve().with_name('capture_rule_check_selection.py'),
        Path(__file__).resolve().with_name('capture_rule_check_embeddings.py')]
    hashes={str(p):sha(p.read_bytes()) for p in protected}
    save_new(out/'preflight.json',{'endpoint':ENDPOINT,'model':MODEL,'packageSha256':PACKAGE_SHA,'sourceHashes':hashes,
        'freezeGate':gate,'deadlineSeconds':150,'perRequestDeadlineSeconds':5,'localBlockedCount':4})
    records=[];started=time.monotonic();execute=run_bounded if runner is None else runner
    for row in pack['requests']:
        remaining=150-(time.monotonic()-started)
        if remaining<=0:break
        require(sha((root/PACKAGE).read_bytes())==PACKAGE_SHA)
        begin=time.monotonic()
        try:value=execute(request_selection,row,min(5,remaining))
        except Exception as error:value={'status':'ERROR','errorType':type(error).__name__,'usageKnown':False,'actualHttpAttempt':False}
        value={'caseId':row['caseId'],'kind':row['kind'],'elapsedMs':(time.monotonic()-begin)*1000,**value}
        target=out/(row['caseId']+'.json');save_new(target,value)
        records.append({'caseId':row['caseId'],'path':target.name,'sha256':sha(target.read_bytes()),'status':value['status'],
            'observedHttpAttemptSignal':value.get('actualHttpAttempt',False),'usageKnown':value.get('usageKnown',False),
            'totalTokens':value.get('totalTokens')})
        # 超时/失败占用准入额度，立即停止整批；不能用自动重试伪造一次成功。
        if value['status']!='RESPONSE_RECEIVED':break
    unchanged=all(sha(Path(p).read_bytes())==digest for p,digest in hashes.items())
    summary={'recordedAt':datetime.now(timezone.utc).isoformat(),'recordsProcessed':len(records),
        'responseRecords':sum(r['status']=='RESPONSE_RECEIVED' for r in records),
        'completedAllRequests':len(records)==MAX_CALLS and all(r['status']=='RESPONSE_RECEIVED' for r in records),
        'records':records,'localBlockedCount':4,'retryCount':0,'sourceUnchanged':unchanged,
        'knownTokens':sum(r['totalTokens'] for r in records if r['usageKnown']),
        'allUsageKnown':len(records)==MAX_CALLS and all(r['usageKnown'] for r in records),
        'reportCasesConsumed':0,'humanSemanticGradesProduced':False,'defaultSwitched':False}
    save_new(out/'summary.json',summary);require(unchanged)
    return summary

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--capture',action='store_true');args=parser.parse_args()
    require(args.capture)
    try:
        summary=capture(Path(__file__).resolve().parents[2])
        print(json.dumps({k:summary[k] for k in ['recordsProcessed','completedAllRequests','knownTokens','sourceUnchanged']}))
        return 0 if summary['completedAllRequests'] else 2
    except Exception as failure:
        print(json.dumps({'status':'STOPPED','errorType':type(failure).__name__}));return 2

if __name__=='__main__':raise SystemExit(main())
