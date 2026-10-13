"""应用实际用户的逐题评分决定，保留未判维度，不将整体PASS复制成全部维度PASS。"""
from pathlib import Path
from collections import Counter
from capture_rule_check_selection import read, require, save_new, sha
from audit_rule_check_eval_assets import case_verdict

def finalize(root):
    base=root/'docs/evidence/rule-check-refactor/2026-10-10/package-03';receipt=read(base/'human-output-confirmation.json')
    require(receipt['authority']=='DIRECT_USER_MESSAGE' and receipt['decision']=='CONFIRMED')
    require(receipt['otherUnjudgedDimensionsRemainUnknown'] and receipt['E11RemainsUnknown'])
    for path_key,hash_key in [('reviewedWorksheetPath','reviewedWorksheetSha256'),('actualResultsPath','actualResultsSha256')]:
        require(sha((root/receipt[path_key]).read_bytes())==receipt[hash_key])
    prior=read(base/'assessment-final/automatic-annotations.json');cases=read(base/'assessment-final/case-review-proposals.json')['cases']
    decisions=receipt['caseLevelVerdicts'];require(set(decisions)=={c['caseId'] for c in cases} and len(cases)==16)
    annotations=prior['annotations']
    for row in annotations:
        cid=row['caseId'];decision=decisions[cid]
        if row['dimensionId']=='E08' and decision in {'PASS','FAIL'}:
            row.update(judgment=decision,severity='MAJOR' if decision=='FAIL' else 'NONE',
                method='HUMAN_CASE_LEVEL_TASK_REVIEW_WITH_CODE_SUPPORT',reviewer=receipt['reviewer'],reviewedAt=receipt['recordedAt'],
                reason='用户确认本题整体任务评分建议；映射到E08任务完成检查，其他未逐维度确认项仍未知。')
            row['evidenceRefs'].append({'path':(base/'human-output-confirmation.json').relative_to(root).as_posix()})
    rows=[]
    for case in cases:
        cid=case['caseId'];rows.append({'caseId':cid,'humanCaseLevelVerdict':decisions[cid],
            'frozenDimensionVerdict':case_verdict([a for a in annotations if a['caseId']==cid],set(case['requiredDimensions'])),
            'referenceStatus':case['expectedStatus'],'actualStatus':case['actualStatus'],'disputeId':'P03-R05-INTERPRETATION' if cid=='R05' else None})
    metrics=read(base/'assessment-final/report-metrics-pending-human.json')
    metrics.update(humanCaseLevelVerdicts=dict(Counter(r['humanCaseLevelVerdict'] for r in rows)),
        frozenDimensionVerdicts=dict(Counter(r['frozenDimensionVerdict'] for r in rows)),
        humanCaseLevelPassRate=7/16,humanCaseLevelReviewCompleted=True,humanSemanticReviewCompleted=False,
        confirmedTaskFailures=8,productGatePassed=False,G2Passed=False,gateOutcome='FAIL_CONFIRMED_TASK_FAILURES',
        taskPassRate=None,annotationBoundary='CASE_LEVEL_CONFIRMATION_DOES_NOT_FILL_UNJUDGED_DIMENSIONS',
        qualityPassUpperBoundFromEightConfirmedFailures=8/16)
    out=base/'confirmed-review';out.mkdir(exist_ok=False)
    save_new(out/'annotations.json',{'authority':'DIRECT_USER_CASE_REVIEW_AND_PRESERVED_DIMENSION_UNKNOWNS','annotations':annotations})
    save_new(out/'case-verdicts.json',{'cases':rows,'reportDenominator':16,'allDimensionsHumanConfirmed':False})
    save_new(out/'metrics.json',metrics)
    return metrics

if __name__=='__main__':
    result=finalize(Path(__file__).resolve().parents[2])
    print({k:result[k] for k in ['humanCaseLevelVerdicts','frozenDimensionVerdicts','confirmedTaskFailures','gateOutcome']})
