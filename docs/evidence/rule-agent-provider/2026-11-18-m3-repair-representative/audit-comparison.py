"""Check the approved batch evidence and compare the same cases against frozen M3."""
import hashlib
import json
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path

live = Path(sys.argv[1]).resolve()
baseline_path = Path(sys.argv[2]).resolve()

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def at(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00'))

def emit(path, value):
    with path.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')

receipt = read(live/'batch-plan.json')
approval = read(live/'execution-approval.json')
reservation = read(live/'execution-reserved.json')
close = read(live/'batch-closeout.json')
plan = receipt['plan']
package_sha = sha(live.parent/'request-package.json')
assert package_sha == receipt['packageSha256'] == approval['packageSha256'] == reservation['packageSha256']
assert receipt['approvalSha256'] == sha(live/'execution-approval.json')
assert receipt['reservationSha256'] == sha(live/'execution-reserved.json')
assert at(approval['approvedAt']) <= at(reservation['startedAt']) <= at(close['closedAt']) < at(approval['validUntil'])
assert close['remainingAuthorizedCalls'] == 0 and close['consumedCalls'] + close['unusedCallsClosed'] == 50
assert read(live/'container-cleanup.json')['cleanupSucceeded']

traces, rows = [], []
for case in plan['cases']:
    cid = case['caseId']
    folder = live/cid
    trace_file = folder/'provider-traces.jsonl'
    events = [json.loads(line) for line in trace_file.read_text(encoding='utf-8-sig').splitlines()] if trace_file.is_file() else []
    finished = [x for x in events if x['phase']=='FINISHED']
    assert len({(x['runId'],x['attempt']) for x in finished}) == len(finished)
    assert len(finished) <= case['maximumCalls']
    for run_id in {x['runId'] for x in finished}:
        attempts = [x['attempt'] for x in finished if x['runId']==run_id]
        assert sorted(attempts)==list(range(1,len(attempts)+1)) and len(attempts)<=5
    if (folder/'case-closeout.json').is_file():
        case_close = read(folder/'case-closeout.json')
        assert case_close['consumedCalls'] == len(finished) and case_close['resourcesClosed']
    for item in case['turns']:
        kind = item['kind']
        path = folder/kind
        capture_file = path/'capture.json'
        if not capture_file.is_file():
            rows.append({'caseId':cid,'kind':kind,'capture':None})
            continue
        capture = read(capture_file)
        ident = capture['execution']['identity']
        diagnostic = read(path/'publication-diagnostics.json')
        assert diagnostic['identity'] == ident and diagnostic['captureSha256'] == sha(capture_file)
        assert ident['caseId']==cid and ident['configurationSha256']==package_sha
        assert any(x['runId']==ident['runId'] and x['taskId']==ident['taskId'] for x in finished)
        trace = capture['execution']['trace']
        state = read(path/'turn-finished.json')
        assert state['taskStatus']==trace['taskStatus'] and state['runState']==trace['runState']
        rows.append({'caseId':cid,'kind':kind,'capture':capture_file.relative_to(live).as_posix(),
                     'captureSha256':sha(capture_file),'conditions':state['appliedConditions'],
                     'taskStatus':trace['taskStatus'],
                     'issues':[issue['reasonCode'] for i in capture['publishedResult']['items'] for issue in i['issues']],
                     'publicationReasons':sorted({x['publicationReason'] for x in trace['actions'] if x['publicationReason']}),
                     'providerValidation':[x['validation']['code'] for x in finished if x['runId']==ident['runId'] and x['validation']['code']!='NONE'],
                     'relevanceRejections':diagnostic['relevanceRejections']})
    traces.extend(finished)
assert len(traces)==close['consumedCalls']
for trace in traces:
    assert trace['outputTokens'] is None or trace['outputTokens']<=1024

baseline = read(baseline_path)['qualityReport']
current = read(live/'report-reviewed.json')['qualityReport']
ids = [x['caseId'] for x in plan['cases']]
assert list(current['cases'])==ids and len(ids)==9
def subset(report):
    cases = [report['cases'][cid] for cid in ids]
    return {'denominator':len(cases),'outcomes':dict(Counter(c['outcome'] for c in cases)),
            'fullyEvaluable':sum(c['fullyEvaluable'] for c in cases)}

comparison = {'caseIds':ids, 'baselineReportSha256':sha(baseline_path), 'repairReportSha256':sha(live/'report-reviewed.json'),
              'baseline':subset(baseline),'repair':subset(current),
              'perCase':[{'caseId':cid,'baseline':baseline['cases'][cid]['outcome'],'repair':current['cases'][cid]['outcome']}
                         for cid in ids]}
metrics = {'executionCompleted':close['executionCompleted'],'callsUsed':len(traces),
           'httpStatuses':dict(Counter(str(x['status']) for x in traces)),'unusedCallsClosed':close['unusedCallsClosed'],
           'remainingAuthorizedCalls':0, 'capturedTurns':sum(r['capture'] is not None for r in rows),
           'inputTokens':sum(x['inputTokens'] or 0 for x in traces), 'outputTokens':sum(x['outputTokens'] or 0 for x in traces)}
emit(live/'execution-audit.json', {'packageSha256':package_sha,'metrics':metrics,'turns':rows})
emit(live/'m3-comparison.json', comparison)
print(json.dumps({'metrics':metrics, 'comparison':comparison}, ensure_ascii=False))
