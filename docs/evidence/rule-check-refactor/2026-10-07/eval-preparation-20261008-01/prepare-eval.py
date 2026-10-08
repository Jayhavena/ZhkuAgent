"""基于已确认参考审计题族并准备评分草案；不生成虚构运行或评分结果。"""
from pathlib import Path
import datetime, hashlib, json

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[4]
NOW=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat()
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def save(p,x): p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
assert not (HERE/'verification.json').exists() and not (HERE/'source-before.json').exists()
reference=ROOT/'docs/product/rule-check-2025-human-reference-development.json'
original=ROOT/'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json'
ref=read(reference); draft=read(original)
assert ref['approvedDevelopmentCount']==20 and ref['pendingDevelopmentCount']==0
assert all(r['humanReview']['decision']=='CONFIRMED' for r in ref['records'])
source_hashes=[{'path':p.relative_to(ROOT).as_posix(),'sha256':sha(p)} for p in (reference,original)]
development_articles={e['article'] for r in ref['records'] for e in r['humanReview']['acceptedEvidence']}
report=[r for r in draft['cases'] if r['split']!='DEVELOPMENT']
assert len(report)==16
overlap=[]; retained=[]
for row in report:
    shared=sorted(development_articles & {e['article'] for e in row['requiredEvidence']})
    if shared: overlap.append({'caseId':row['caseId'],'sharedArticles':shared,'selection':'EXCLUDED_FROM_REPORT_CANDIDATES'})
    else: retained.append(row['caseId'])
assert len(overlap)==3 and all(x['sharedArticles']==[18] for x in overlap)
assert len(retained)==13
audit={
    'recordedAt':NOW,'referenceRevision':ref['referenceRevision'],'method':'ARTICLE_DEPENDENCY_INTERSECTION',
    'developmentArticles':sorted(development_articles),'originalReportCandidateCount':16,
    'overlappingReportCandidates':overlap,'retainedReportCandidateCount':len(retained),
    'remainingCandidateArticleOverlapCount':0,'isolationClaim':'ARTICLE_LEVEL_ONLY',
    'crossArticleTaskFamilyIsolationVerified':False,
    'exposureStatus':'AI_ASSISTED_PREVIEWED_NOT_BLIND','reportCandidatesLocked':False,
    'notes':['检查实际证据依赖而不是只看questionFamily；Q05已关联第18条。',
             '第18条相关3题不得用于独立报告成绩；可另作开发回归，未改原36题草稿。',
             '余下13题只通过条款交叉检查，仍需任务家族、参考与评分核对；不能宣称盲测。'],
}
save(HERE/'report-family-isolation-audit.json',audit)
save(HERE/'report-candidate-selection.json',{
    'recordedAt':NOW,'status':'CANDIDATE_SELECTION_NOT_LOCKED','plannedReportCount':16,
    'retainedCandidateIds':retained,'excludedCandidateIds':[x['caseId'] for x in overlap],
    'replacementCandidateCountNeeded':3,'originalDatasetModified':False,
    'humanReferenceApproved':False,'usableForProductQualityGate':False,
    'exposureStatus':'AI_ASSISTED_PREVIEWED_NOT_BLIND',
    'replacementConstraints':['未与开发条款/任务家族交叉；仅换问法不能消除泄漏。',
                              '优先由真实目标用户或独立采样提供；取得可核对证据并人工审核。',
                              '记录题目接触与调优用途；若无法合理补齐，显式修订报告规模，不复制题凑16。'],
})
save(HERE/'development-rubric-draft.json',{
    'schemaVersion':1,'recordedAt':NOW,'referenceRevision':ref['referenceRevision'],
    'referenceSha256':sha(reference),'status':'DERIVED_DRAFT_NOT_CALIBRATED',
    'referenceHumanConfirmed':True,'rubricHumanConfirmed':False,'productEvaluationRun':False,
    'gradingMethod':'DETERMINISTIC_CONTRACTS_AND_MANUAL_SEMANTIC_REVIEW',
    'judgements':['PASS','FAIL','UNJUDGEABLE','ERROR'],
    'rules':['语义相同表达可以接受；关键词出现或引用存在不等于主张受支持。',
             '预期状态、证据完整性、条件/对象/数字和未知项分别评分；未知不能记通过。',
             '引用第18条等例外的必要证据按集合核对；只命中一条不能记为充分证据。',
             '资料资格未放行时，不得执行学生质量报告或把资格检查忽略。'],
    'records':[
        {'reviewId':r['reviewId'],'caseId':r['sourceCaseId'],'question':r['question'],
         'expectedStatus':r['humanReview']['acceptedStatus'],
         'confirmedReferencePoints':[{'id':f'{r["reviewId"]}-F{i+1}','text':f}
                                     for i,f in enumerate(r['humanReview']['acceptedFacts'])],
         'confirmedBoundary':r['humanReview'].get('acceptedBoundary'),
         'draftBoundaryWhenNotSeparatelyConfirmed':None if 'acceptedBoundary' in r['humanReview'] else r['proposedBoundary'],
         'requiredEvidence':r['humanReview']['acceptedEvidence'],
         'evaluationStatus':'NOT_RUN','score':None,'outputHash':None,'gradingReviewer':None}
        for r in ref['records']],
})

names=['docs/product/rule-check-blockers.md','docs/product/rule-check-review-handoff.md',
       'docs/evidence/rule-check-refactor/2026-10-07/issues.json',
       '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
before={n:(ROOT/n).read_bytes() for n in names}
for n,raw in before.items():
    p=HERE/'before'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
save(HERE/'source-before.json',{'recordedAt':NOW,'files':source_hashes+[
    {'path':n,'sha256':hashlib.sha256(raw).hexdigest()} for n,raw in before.items()]})
base=HERE.relative_to(ROOT).as_posix()
blockers=before[names[0]].decode('utf-8-sig')
old='| B-02 | OPEN | 36题质量候选与独立风险草稿未形成锁定的人工参考、原子主张/条件/例外rubric |'
new='| B-02 | OPEN，开发参考20/20已自核 | 开发参考已记录；第18条跨开发/报告的3题已在独立选择清单中排除，剩13候选；报告参考、任务家族和评分标准未冻结 |'
assert old in blockers
blockers=blockers.replace(old,new)
blockers+='\n## 2026-10-08报告候选隔离审计与评分准备\n\n'+(
    f'20题开发参考已完成用户确认。已按实际关联条款审计：第18条交叉的3题不纳入报告候选，独立选择清单保留13题，原36题草稿不改写。计划16题仍缺3题；不能以改写开发问题凑数，也不能把AI已看过的候选称为盲测。详见[{base}/report-family-isolation-audit.json](../evidence/rule-check-refactor/2026-10-07/eval-preparation-20261008-01/report-family-isolation-audit.json)。\n\n'
    '已由确认结果生成开发评分草案，未校准、未运行、所有score为空。接续：准备审核主题规则和任务家族复核；补独立报告候选与实际审核、评分标准及一致性；按计划完成受控v2应用后执行真实回答评估。B-01展示资格仍阻断学生试点。\n')
(ROOT/names[0]).write_text(blockers,encoding='utf-8')
handoff=before[names[1]].decode('utf-8-sig')
handoff=handoff.replace('所有人工核对字段仍留空，报告候选未纳入该工作表。',
                        '20题开发参考已由用户确认（详见分批证据），报告候选未纳入该工作表。')
handoff+='\n**2026-10-08隔离与评分准备：**按实际证据依赖排除第18条相关3道报告候选，另存选择清单，余13题尚未审核/冻结；原草稿不改。需补3道独立任务或显式修订试验规模。开发评分草案由20题确认内容生成，未校准且未运行。不得用条款交叉检查证明任务家族或统计独立性。\n'
(ROOT/names[1]).write_text(handoff,encoding='utf-8')
issues=read(ROOT/names[2]);rc9=next(x for x in issues['issues'] if x['id']=='RC-09')
rc9['evaluationPreparationStatus']='HUMAN_REFERENCE_20_DEV_CONFIRMED_REPORT_13_CANDIDATES_NEEDS_3_REPLACEMENTS'
rc9['reportFamilyAuditStatus']='ARTICLE_OVERLAP_3_EXCLUDED_IN_SELECTION_TASK_FAMILY_REVIEW_PENDING'
rc9.setdefault('latestEvidencePaths',[]).extend([base+'/report-family-isolation-audit.json',base+'/development-rubric-draft.json'])
save(ROOT/names[2],issues)
progress=before[names[3]].decode('utf-8-sig').rstrip()+'\n\n## 2026-10-08评估准备接续\n\n'+(
    '完成20题开发参考后继续执行条款家族审计。真实依赖第18条的3道报告候选已在独立选择清单排除；余13候选，未冻结，计划16题缺3个独立任务。原36题草稿和开发参考字节未改。生成20题开发评分草案，score全部null，未运行数据库/模型。\n\n'
    '解决的问题：Q05新增关联后，原20/16分组继续使用会造成条款泄漏；已给出可执行排除清单，不能以问法不同来掩盖同条款。未解决：跨条款任务家族、报告人工参考/评分一致性、真实质量与用户收益。\n\n'
    f'证据：{base}。继续已授权的受控主题规则/应用验收准备；资料展示、报告补样与实际输出评分按各自门禁记录。\n')
(ROOT/names[3]).write_text(progress,encoding='utf-8')
doc=ROOT/'docs/product/rule-check-2025-eval-preparation.md'
assert not doc.exists()
doc.write_text('''# 2025版查证评估：准备进度与接续动作

更新：2026-10-08。开发参考20/20已由用户对照原文确认；核对方式为开发者自核，没有独立第二审核者。仅评价2025文本支持什么，不判断当前学年适用性。

| 产物 | 当前状态 | 解决的问题 |
| --- | --- | --- |
| 开发参考20题 | 全部确认、历史草稿保留 | 不再以AI草稿充当人工参考 |
| 报告候选隔离审计 | 第18条交叉3题已从独立选择清单排除，余13候选 | 避免同条款同时调优与报告 |
| 开发评分草案 | 按确认内容生成，未校准、未运行 | 给实际输出评分准备逐题依据 |
| 系统质量与用户收益 | 未执行 | 仍需真实输出和对照数据 |

上述审计只覆盖条款依赖交叉；跨条款的同任务家族仍需复核。报告候选已经被AI查看，不能标为盲测。原36题草稿不改写，选择结果另存。

接续按以下顺序执行：

1. 将确认参考整理为受控主题规则草案：对象、前提、结论、例外、缺证项分别绑定原文；参考确认不能直接变成资料发布资格。
2. 复核余13道报告候选的任务家族并补3道独立任务，或明确修订报告规模。换问法、复制条款题不算补独立样本。新问题需证据与人工参考，不能冒充真实学生问题。
3. 固定评分标准。检查预期状态、主张支持、条件/例外、未知项、引用定位；接受等义表达。评分草案未经过实际输出的一致性复核，不启用模型裁判自动放行。
4. 完成R5受控v2应用链，再保存真实候选、最终回答、版本和耗时，按同一标准评新旧输出。检索漏证、回答用证错误、资格/执行错误分开统计。
5. 汇总改善、退步、未知和样本范围。开发集分数仅供诊断；正式报告与学生对照须满足各自资料和参与者条件。

验收：确认记录20条且版本/原文位置可追溯；当前选择清单条款交叉为0；报告规模缺口明确；评分未运行时不得填成功率；任何实际失败与退步保留，未知不计通过。

本轮没有模型调用、数据库操作或产品质量成绩，学生展示资格仍未放行。

证据目录：[评估准备批次](../evidence/rule-check-refactor/2026-10-07/eval-preparation-20261008-01/)。开发参考见[人工核对清单](rule-check-2025-human-reference-development.md)。
''',encoding='utf-8')
for item in source_hashes: assert sha(ROOT/item['path'])==item['sha256']
rubric=read(HERE/'development-rubric-draft.json')
assert len(rubric['records'])==20 and all(r['score'] is None and r['evaluationStatus']=='NOT_RUN' for r in rubric['records'])
retained_articles={e['article'] for r in report if r['caseId'] in retained for e in r['requiredEvidence']}
assert not development_articles & retained_articles
paths=[HERE/'report-family-isolation-audit.json',HERE/'report-candidate-selection.json',HERE/'development-rubric-draft.json',doc]+[ROOT/n for n in names]
save(HERE/'verification.json',{'recordedAt':NOW,'passed':True,'sourceReferenceAndDraftUnchanged':True,
    'developmentReferenceCount':20,'retainedReportCandidateCount':13,'excludedArticleOverlapCount':3,
    'replacementCountNeeded':3,'remainingArticleOverlapCount':0,'taskFamilyIsolationVerified':False,
    'productEvaluationRun':False,'databaseRun':False,'modelCalled':False,'reportCandidatesLocked':False,
    'files':source_hashes+[{'path':p.relative_to(ROOT).as_posix(),'sha256':sha(p)} for p in paths]})
print('PASS: article-overlap audit: 3 excluded, 13 candidates remain, 3 replacements needed; rubric NOT_RUN.')
