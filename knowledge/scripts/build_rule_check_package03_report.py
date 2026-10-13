"""实际报告记录与人工复核表；确定性核对和AI评分建议分开，不制造人工审核。"""
from pathlib import Path
import argparse
import json
from datetime import datetime, timezone
from capture_rule_check_selection import require, sha, read, save_new
from audit_rule_check_eval_assets import required_case_dimensions, case_verdict

BASE='docs/evidence/rule-check-refactor/2026-10-10/package-03'
ACTUAL='docs/evidence/rule-check-refactor/2026-10-07/package03-actual-replay-20261010-03/report-actual-results.json'
AI_PROPOSALS={
    'R01':('PASS','明确返回所依《课程选课工作通知》，原文及资格边界可核对。'),
    'R02':('FAIL','本应解释第14条的全部条件，实际选题返回null，未完成规则查证。'),
    'R03':('FAIL','检索已找到第14条，模型和主题目录却选择第13条；返回选退补改选通知，未解释已正式退选者是否符合0分条件。'),
    'R04':('PASS','原文保留严格超过30%、该门课程学期总学时分母及三项后果。'),
    'R05':('UNJUDGEABLE','原文保留“超过30%”，但未明确解释正好30%不满足该条件。原文是否已充分回答，需要人工裁定。'),
    'R06':('FAIL','特殊原因不能上课这一改写未被有限主题词识别，整题本地拒绝。'),
    'R07':('FAIL','“哪些部分综合评定”这一改写未识别成绩构成，没有展示第16条。'),
    'R08':('FAIL','缺证状态符合参考，但原因是候选未识别；没有说明第16条未给百分比，不能把状态一致判任务完成。'),
    'R09':('FAIL','缺证方向正确，但未以第17条解释当期截止日缺资料，只返回没有覆盖候选。'),
    'R10':('PASS','保留计划外课程范围、本人申请与教务处审核同意；范围说明明确为须经两道程序的一般规则。'),
    'R11':('FAIL','现行性保护阻断整题，连可独立回答的“教务处负责解释”也未展示；参考要求部分回答，必须检查E07。'),
    'R12':('PASS','教学任务性质、院部及教师责任均按第1条完整展示。'),
    'R13':('PASS','教学管理要求与必修相同，保留规范机制/质量标准，没有扩大到所有其他规则。'),
    'R14':('PASS','保留院部课程建设责任与教务处定期检查反馈，没有虚构频率、渠道或工单。'),
    'R15':('FAIL','“成绩由谁按规定时间和要求提交”未被有限主题词识别，没有返回任课教师。'),
    'R16':('PASS','按第20条列明废止旧办法及文号附件，没有把手册日期当公布日。'),
}

def recall(path, case, bindings):
    needed={e['article']:e for e in case['requiredEvidence']}
    delivered=set()
    for fact in path.get('result',{}).get('decision',{}).get('confirmedFacts',[]):
        for evidence in fact['evidence']:
            binding=bindings.get(evidence['chunkId']);require(binding is not None)
            require(binding['sourceId']==evidence['sourceId'] and evidence['revision']=='2025.9.2'
                and binding['checksum'].lower()==evidence['checksum'].lower())
            text=binding['text'].encode('utf-16-le');start=evidence['startInclusive'];end=evidence['endExclusive']
            require(0<=start<end<=len(text)//2 and text[start*2:end*2].decode('utf-16-le')==evidence['text'])
            article=binding['article'];required=needed.get(article)
            if required and required['contentChecksum'].lower()==evidence['checksum'].lower():delivered.add(article)
    if not path['retrievalCalls']:
        return {'observed':False,'necessaryArticles':sorted(needed),'hitArticles':[], 'recall':None,
            'retrievalHitContribution':0.0,'answerEvidenceArticles':sorted(delivered),'deliveredRecall':len(delivered)/len(needed)}
    hits=set()
    for chunk in path['top5']:
        binding=bindings.get(chunk['chunkId'])
        require(binding is not None and binding['sourceId']==chunk['sourceId'])
        require(sha(chunk['content'].encode())==chunk['contentChecksum'].lower())
        article=binding['article'];required=needed.get(article)
        if required and required['contentChecksum'].lower()==chunk['contentChecksum'].lower():hits.add(article)
    return {'observed':True,'necessaryArticles':sorted(needed),'hitArticles':sorted(hits),
        'recall':len(hits)/len(needed),'retrievalHitContribution':len(hits)/len(needed),
        'answerEvidenceArticles':sorted(delivered),'deliveredRecall':len(delivered)/len(needed)}

def summarize(rows):
    require(len(rows)==16 and len({r['caseId'] for r in rows})==16)
    observed=[r['actualRecall'] for r in rows if r['actualRecall']['observed']]
    return {'reportDenominator':16,'retrievalObservationCoverage':len(observed),
        'observedRetrievalMacroRecall':sum(r['recall'] for r in observed)/len(observed) if observed else None,
        'fullSixteenRetrievalMacroRecall':None if len(observed)!=16 else sum(r['recall'] for r in observed)/16,
        'endToEndNecessaryEvidenceDeliveryRecall':sum(r['actualRecall']['deliveredRecall'] for r in rows)/16,
        'fullCaseDenominatorRetrievalHitRecall':sum(r['actualRecall'].get('retrievalHitContribution',0) for r in rows)/16,
        'statusMatchesReference':sum(r['statusMatchesReference'] for r in rows),
        'confirmedVerdicts':{v:sum(r['confirmedVerdict']==v for r in rows) for v in ['PASS','FAIL','UNJUDGEABLE','ERROR']},
        'aiProposedVerdicts':{v:sum(r['aiProposedVerdict']==v for r in rows) for v in ['PASS','FAIL','UNJUDGEABLE']},
        'taskPassRate':None,'criticalFailureRate':None,'semanticClaimSupportRate':None,'unfairRefusalRate':None,
        'productGatePassed':False,'G2Passed':False,'humanSemanticReviewCompleted':False}

def normalize_decision(value):
    # 随机引用别名不改变政策/状态；来源、片段、版本、hash和跨度必须保留，否则会掩盖真实退步。
    if isinstance(value,dict):return {k:normalize_decision(v) for k,v in value.items() if k!='citationId'}
    if isinstance(value,list):return [normalize_decision(v) for v in value]
    return value

def build(root, output_relative=BASE):
    base=root/output_relative;require(base.resolve().is_relative_to((root/BASE).resolve()));base.mkdir(parents=True,exist_ok=True)
    actual=read(root/ACTUAL);reference=read(root/'docs/product/rule-check-2025-human-reference-report.json')
    rubric_path=root/'docs/product/rule-check-2025-scoring-rubric.json';rubric=read(rubric_path)
    require(rubric['locked'] and rubric['judgeEnabled'] is False and rubric['humanRubricApproved'])
    require(actual['runtimeSourceUnchanged'] and len(actual['cases'])==16)
    bindings={b['chunkId']:b for b in actual['bindings']};refs={c['caseId']:c for c in reference['cases']}
    dimensions={d['id']:d for d in rubric['dimensions']};rows=[];annotations=[];now=datetime.now(timezone.utc).isoformat()
    for output in actual['cases']:
        cid=output['caseId'];case=refs[cid];require(output['question']==case['question'])
        actual_path=output['actualPath'];decision=actual_path['result']['decision'];status=decision['status']
        actual_recall=recall(actual_path,case,bindings);baseline_recall=recall(output['baselinePath'],case,bindings)
        required=required_case_dimensions(case,status);case_annotations=[]
        for eid in sorted(required):
            judgment='UNJUDGEABLE';reason='本项需要对实际输出作人工语义判断；已有人工参考不等于输出已审核。';method='PENDING_HUMAN_REVIEW'
            if eid=='E01':
                require(decision['referenceRevision']=='2025.9.2' and status in {'ANSWERED','PARTIALLY_ANSWERED','INSUFFICIENT_EVIDENCE','NEED_CLARIFICATION','CONFLICTING_EVIDENCE'})
                require(actual_path['freshReleaseValidated'] and actual_path['selectorWorkersClosed'])
                judgment='PASS';method='CODE';reason='实际Java构造合同、绑定、fresh释放与worker退出已验收；不代表语义正确。'
            if eid=='E03':
                method='CODE_AND_CONFIRMED_REFERENCE'
                if actual_recall['observed']:
                    judgment='PASS' if actual_recall['recall']==1 else 'FAIL';reason='按已确认必要集合对实际前5的片段身份/正文hash核对。'
                else:reason='选题/范围边界阻断，检索未执行；检索器召回未观察，端到端证据交付记0，不能缩成9题宣称总体100%。'
            annotation={'id':eid,'runId':'package03-actual-replay-20261010-03','caseId':cid,'rubricId':rubric['rubricId'],
                'rubricSha256':sha(rubric_path.read_bytes()),'dimensionId':eid,'applicability':'APPLICABLE','judgment':judgment,
                'severity':dimensions[eid]['defaultFailSeverity'] if judgment=='FAIL' else 'NONE',
                'answerSegment':decision,'evidenceRefs':[{'path':ACTUAL,'caseId':cid,'jsonPointer':f'/cases/{len(rows)}/actualPath'},
                    {'path':'docs/product/rule-check-2025-human-reference-report.json','caseId':cid}],
                'reason':reason,'method':method,'requiredMethod':dimensions[eid]['method'],
                'reviewer':'CODEX_AUTOMATED_CODE_CHECK_NOT_HUMAN' if judgment!='UNJUDGEABLE' else None,
                'reviewedAt':now if judgment!='UNJUDGEABLE' else None,'disputeId':'P03-R05-INTERPRETATION' if cid=='R05' else None}
            case_annotations.append(annotation);annotations.append(annotation)
        proposal,reason=AI_PROPOSALS[cid]
        rows.append({'caseId':cid,'expectedStatus':case['expectedStatus'],'actualStatus':status,'statusMatchesReference':case['expectedStatus']==status,
            'confirmedVerdict':case_verdict(case_annotations,required),'aiProposedVerdict':proposal,'aiProposalReason':reason,
            'aiProposalAuthority':'AI_ASSISTED_DRAFT_NOT_HUMAN_DECISION','primaryFailureDimension':'E08' if proposal=='FAIL' else None,
            'proposedSeverity':'MAJOR' if proposal=='FAIL' else 'NONE','actualRecall':actual_recall,'baselineRecall':baseline_recall,
            'requiredDimensions':sorted(required),'actualOutput':actual_path['result'],'sourceReference':case,
            'actualLocations':actual_path['actualOriginalLocations'],'actualAuditCount':len(actual_path['persistedAudits'])})
    metrics=summarize(rows);metrics['actualAuditReads']=sum(r['actualAuditCount'] for r in rows)
    metrics['actualOriginalLocations']=sum(len(r['actualLocations']) for r in rows)
    metrics['baselineAuditReads']=sum(len(r['baselinePath']['persistedAudits']) for r in actual['cases'])
    metrics['baselineOriginalLocations']=sum(len(r['baselinePath']['actualOriginalLocations']) for r in actual['cases'])
    metrics['rawBaselineDecisionIdentityEquality']=sum(r['actualPath']['result']['decision']==r['baselinePath']['result']['decision'] for r in actual['cases'])
    metrics['sameBaselineDecisionsIgnoringCitationAliases']=sum(normalize_decision(r['actualPath']['result']['decision'])==normalize_decision(r['baselinePath']['result']['decision']) for r in actual['cases'])
    metrics['baselineNecessaryEvidenceDeliveryRecall']=sum(r['baselineRecall']['deliveredRecall'] for r in rows)/16
    metrics['pairedQualityJudgment']='PENDING_HUMAN_REVIEW_SAME_CORPUS_SAME_SELECTION_RESPONSES'
    save_new(base/'automatic-annotations.json',{'authority':'CODE_CHECKS_AND_EXPLICIT_UNJUDGEABLE_HUMAN_DIMENSIONS','annotations':annotations})
    save_new(base/'report-metrics-pending-human.json',metrics)
    save_new(base/'case-review-proposals.json',{'authority':'AI_ASSISTED_DRAFT_NOT_HUMAN_DECISION','cases':rows})
    save_new(base/'run-annotations.json',{'annotations':[{'id':'E11','runId':'package03-actual-replay-20261010-03','caseId':None,
        'rubricId':rubric['rubricId'],'rubricSha256':sha(rubric_path.read_bytes()),'dimensionId':'E11','applicability':'APPLICABLE',
        'judgment':'UNJUDGEABLE','severity':'NONE','answerSegment':None,
        'evidenceRefs':[{'path':BASE+'/provider-capture/preflight.json'},
            {'path':'docs/evidence/rule-check-refactor/2026-10-07/package03-engineering-final-20261010-01/results.json'},
            {'path':'docs/evidence/rule-check-refactor/2026-10-07/package03-actual-replay-20261010-03/results.json'}],
        'reason':'本批模型单次5秒/批次150秒、单次预算、当前正常退出和隔离已留证；现有验收runner的全部外部进程截止/挂起取消覆盖尚未证明，不以正常退出宣称全部E11通过。',
        'method':'PARTIAL_ACTUAL_ENGINEERING_TRACE','reviewer':None,'reviewedAt':None,'disputeId':None}],
        'E12':{'applicability':'NOT_APPLICABLE','reason':'第三包不执行C08学生/PDF用户对照，不以离线结果替代真实收益。'}})
    lines=['# 第三包16题实际输出与人工评分核对表','',
        '**状态：实际输出已产生，以下等级均为AI辅助建议，尚未经人工确认。** 原参考/评分1.0不变；不是重审原20题。',
        '','11次真实选题响应、5题本地拦截，完整16题；随后实际应用/临时库离线回放，非独立在线重复。',
        '',f"状态一致 {metrics['statusMatchesReference']}/16；检索观察 {metrics['retrievalObservationCoverage']}/16；全题分母下检索命中贡献 {metrics['fullCaseDenominatorRetrievalHitRecall']:.2%}；最终回答必要原文交付 {metrics['endToEndNecessaryEvidenceDeliveryRecall']:.2%}。最终原文交付也不等于必要事实解释正确。",
        '', '建议：7题PASS、8题FAIL（E08/MAJOR）、R05保留UNJUDGEABLE争议。人工确认前正式逐题总判均UNJUDGEABLE；G2未通过。',
        '', '| 题号 | 参考状态 | 实际状态 | 建议 | 原因 |','|---|---|---|---|---|']
    for row in rows:lines.append(f"| {row['caseId']} | {row['expectedStatus']} | {row['actualStatus']} | {row['aiProposedVerdict']} | {row['aiProposalReason']} |")
    lines.extend(['','请查看下面实际正文及参考要求。可以确认建议、逐题修改，或保留争议；确认不会把R05/其他未判断项变PASS，也不确认产品放行。'])
    for row in rows:
        decision=row['actualOutput']['decision'];scope=row['actualOutput'].get('scope');case=row['sourceReference']
        lines.extend(['',f"## {row['caseId']}",'',f"问题：{case['question']}",'',f"实际：{row['actualStatus']}；建议：{row['aiProposedVerdict']}",'', '实际确认事实：'])
        for fact in decision['confirmedFacts']:lines.extend(['', '> '+fact['text'].replace('\n','\n> ')])
        if not decision['confirmedFacts']:lines.append('无。')
        lines.extend(['','实际未知项：'+('；'.join(decision['unknownItems']) or '无。'),'',
            '实际范围说明：'+(scope['questionScope'] if scope else '未产生范围说明。'),'','参考必要事实：'+ '；'.join(case['requiredFacts']),
            '', '关键边界：'+case['criticalBoundary'],'','建议理由：'+row['aiProposalReason'],
            '', '实际前5必要集合：'+json.dumps(row['actualRecall'],ensure_ascii=False),
            '',f"原文定位次数：{len(row['actualLocations'])}；审计回读：{row['actualAuditCount']}；适用评分项：{','.join(row['requiredDimensions'])}。"])
    lines.extend(['','## 统计和边界','',
        '9题实际进入检索，必要集合均命中；另外7题未检索，完整16题检索器宏平均未知。全题分母下检索命中贡献9/16（56.25%）；最终回答必要原文交付8/16（50%）：R03前5有第14条，最终答复只引用第13条，不计必要原文交付。不称整体检索准确率100%，也不以原文交付代替任务成功。',
        '输出9条事实逐字引用已审原文；逐字一致、身份/跨度核验不等于语义评分器完成。R03即有第13条真实引用但答非所问。',
        '基线同20条/相同选题响应，仅关闭词法去重多样性；无噪声资料下结果不能证明抗噪改善。随机citation身份不能当质量回退。',
        'E11当前正常资源关闭、供应商预算/超时已记录；整个验收runner的外部进程挂起覆盖未证明。E12留待C08。',
        '作者自核/AI已预览、非盲测；16题来自10条款家族/1文档，不能按16个独立样本推全校泛化。',
        '本报告发现只转为后续开发问题；此冻结运行版本不按报告题修改，不能用同一报告再证明调优后的提升。'])
    target=base/'human-output-review.md';require(not target.exists());target.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    return metrics

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',action='store_true');parser.add_argument('--out-subdir',choices=['assessment-final']);args=parser.parse_args();require(args.build)
    output=BASE+'/'+args.out_subdir if args.out_subdir else BASE
    metrics=build(Path(__file__).resolve().parents[2],output);print(json.dumps(metrics,ensure_ascii=False))

if __name__=='__main__':main()
