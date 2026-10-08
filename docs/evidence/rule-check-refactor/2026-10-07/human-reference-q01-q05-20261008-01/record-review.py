"""回填真实用户逐项确认；不把参考审核变成模型成绩或资料发布授权。"""
from pathlib import Path
import datetime,hashlib,json,re

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[4]
now=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat()
names=['docs/product/rule-check-2025-human-reference-development.json',
 'docs/product/rule-check-2025-human-reference-development.md','docs/product/rule-check-blockers.md',
 'docs/product/rule-check-review-handoff.md','docs/evidence/rule-check-refactor/2026-10-07/issues.json',
 '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert not (HERE/'verification.json').exists(),'禁止覆盖本次人工确认记录'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,x):p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
before=[]
for name in names:
 p=ROOT/name;b=HERE/'before'/name;b.parent.mkdir(parents=True,exist_ok=True);b.write_bytes(p.read_bytes())
 before.append({'path':name,'sha256':sha(p)})
write(HERE/'source-before.json',{'recordedAt':now,'files':before})
raw_reply='Q01确认；Q02确认；Q03确认；Q04确认；Q05确认'
facts={
 'Q01':['全日制普通本科学生必须按人才培养方案要求参加选修课学习。'],
 'Q02':['第2条没有提供研究生选修规则，不能据此判断研究生要求。'],
 'Q03':['专业选修课与公共选修课是两种类别。'],
 'Q04':['公共选修课包括校内公选课和网络公选课。'],
 'Q05':['判断具体课程的缓考、重修规定时，应先明确课程类别。',
  '公共选修课不办理缓考，成绩不合格者不安排重修。',
  '专业选修课成绩不合格者可申请重修。',
  '专业选修课的缓考规定仍需其他依据。']}
data=read(ROOT/names[0]);assert len(data['records'])==20
original=ROOT/'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json'
original_hash=sha(original)
pages=read(ROOT/'docs/evidence/rule-check-refactor/2026-10-07/human-reference-2025-dev-20261008-01/source-pages.json')
assert pages['sourceSha256']==data['sourceSha256']
page77=next(x for x in pages['pages'] if x['physicalPage']==77)
matches=list(re.finditer(r'第([一二三四五六七八九十]+)条',page77['text']))
index=next(i for i,m in enumerate(matches) if m.group(1)=='十八')
excerpt18=page77['text'][matches[index].start():matches[index+1].start()].strip()
evidence18={'article':18,'physicalPage':77,'printedPage':65,'rawExtractedText':excerpt18}
approved=[]
for row in data['records']:
 if row['reviewId'] not in facts:continue
 assert row['humanReview']['decision'] is None,'不覆盖既有审核'
 if row['reviewId']=='Q05':
  row['sourceEvidence'].append(evidence18)
  row['evidenceFamilies']=['clause-4','clause-18']
  row['sourceEvidenceUpdateReason']='用户确认的上一轮审核表明确使用第4和18条；补齐关联，不改原始36题草稿。'
 row['annotationStatus']='HUMAN_CONFIRMED'
 row['humanReview']={'reviewer':'用户（项目开发者，自核）','reviewedAt':'2026-10-08','recordedAt':now,
  'authority':'DIRECT_USER_MESSAGE','confirmationText':raw_reply,'decision':'CONFIRMED',
  'acceptedStatus':row['proposedStatus'],'acceptedFacts':facts[row['reviewId']],
  'acceptedEvidence':row['sourceEvidence'],'reason':'用户在查看上一轮原文位置及逐项参考判断后明确逐项确认。',
  'reviewMethod':'AUTHOR_SELF_REVIEW_WITH_AI_ASSISTED_DRAFT','independentSecondReviewer':False,
  'statusMappingNote':'业务状态映射自已展示的回答/缺证/先补类别判断；未运行模型输出评分。'}
 approved.append(row)
pending=[r['reviewId'] for r in data['records'] if r['humanReview']['decision'] is None]
assert len(approved)==5 and len(pending)==15
report=[r for r in read(original)['cases'] if r['split']!='DEVELOPMENT']
affected=[r['caseId'] for r in report if any(x['article']==18 for x in r['requiredEvidence'])]
data['approvedDevelopmentCount']=5;data['pendingDevelopmentCount']=15
data['humanReferenceApprovalStatus']='PARTIAL_5_OF_20_DEVELOPMENT_CASES'
data['humanReferenceApproved']=False;data['usableForProductQualityGate']=False;data['locked']=False
data['reportFamilyIsolationStatus']='REQUIRES_RECHECK_AFTER_Q05_ARTICLE18_BINDING'
data['notes'].append('2026-10-08用户逐项确认Q01–Q05。Q05第18条关联已补齐；报告候选相关家族需复核/重新划分，未锁定盲测。')
write(ROOT/names[0],data)
subset={'batchId':HERE.name,'recordedAt':now,'referenceRevision':data['referenceRevision'],
 'sourceSha256':data['sourceSha256'],'confirmationText':raw_reply,'authority':'DIRECT_USER_MESSAGE',
 'reviewMethod':'AUTHOR_SELF_REVIEW_WITH_AI_ASSISTED_DRAFT','independentSecondReviewer':False,
 'approvedDevelopmentCount':5,'records':approved,'productEvaluationRun':False,'studentReleaseAuthorized':False,
 'reportCandidatesLocked':False,'reportFamilyIsolation':{'needsRecheck':True,'affectedReportCandidateIds':affected}}
write(HERE/'confirmed-development-subset.json',subset)
p=ROOT/names[1];text=p.read_text(encoding='utf-8-sig')
text=text.replace('参考要点是待核对草稿，人工审核结果全部留空。','Q01–Q05已有用户逐项人工确认，Q06–Q20仍为待核对草稿。核对方式为开发者自核，未有独立第二审核者。')
text=text.replace('建议先审核Q01–Q05建立标准，再继续其余开发题。Q05的缓考/重修关系还需对照第十八条；若新增该关联证据用于调优，需要复核与报告候选的条款家族隔离。',
 'Q01–Q05已于2026-10-08确认；接下来审核Q06–Q10。Q05已补第十八条依据，报告候选的相关家族隔离待复核，当前不锁定报告集。')
text=text.replace('| Q05 | 我说的是选修课，但没说是哪一类，能判断缓考和重修吗？ | 第4条，PDF 76/印刷 64 |',
 '| Q05 | 我说的是选修课，但没说是哪一类，能判断缓考和重修吗？ | 第4条，PDF 76/印刷 64；第18条，PDF 77/印刷 65 |')
for row in approved:
 pattern=rf'(### {row["reviewId"]}：.*?\n)(.*?)(?=\n### Q|\n## 本批记录边界)'
 m=re.search(pattern,text,re.S);assert m,row['reviewId']
 body=m.group(2)
 body=body.replace('- 参考要点草稿：','- 原草稿要点（保留）：')
 body=body.replace('- 审核选择：□ 确认　□ 修改　□ 依据不足　□ 有争议','- 审核结果：☑ 确认（用户逐项确认）')
 body=body.replace('- 你的结论及修改理由：________','- 人工确认结论：'+'；'.join(facts[row['reviewId']]))
 body=body.replace('- 核对人／日期：________','- 核对人／日期：用户（项目开发者，自核）／2026-10-08')
 if row['reviewId']=='Q05':body=body.replace('- 原文位置：第4条，物理页76/印刷页64。','- 原文位置：第4条，物理页76/印刷页64；第18条，物理页77/印刷页65。')
 text=text[:m.start(2)]+body+text[m.end(2):]
text=text.replace('回填真实人工结果之后再建立可用参考版本，保留原稿与修改记录。',
 '已保存Q01–Q05实际人工确认子集，其他题仍待核对；整套参考尚未冻结。保留原稿与修改记录。')
p.write_text(text,encoding='utf-8')
def append(name,content):
 p=ROOT/name;p.write_text(p.read_text(encoding='utf-8-sig').rstrip()+'\n\n'+content+'\n',encoding='utf-8')
append(names[2],'## 2026-10-08首批人工参考确认\n\n用户明确确认Q01–Q05，已回填5/20道开发题，剩余15题待核对。B-02仍OPEN：报告候选未审核/冻结，未执行产品质量评估。Q05补第18条证据后，需复核与报告候选的条款家族隔离。确认内容仅为参考判断，资料展示资格未改变。')
append(names[3],'**2026-10-08审核进度：**Q01–Q05用户逐项确认已记录，[核对清单](rule-check-2025-human-reference-development.md)现为5/20已核对。下一批Q06–Q10；原草稿保留，报告候选未冻结，Q05第18条关联引出的家族隔离待复核。')
issues=read(ROOT/names[4]);issues.setdefault('humanReferenceReviewHistory',[]).append({
 'reviewDate':'2026-10-08','reviewer':'用户（项目开发者，自核）','authority':'DIRECT_USER_MESSAGE',
 'confirmedIds':list(facts),'approvedDevelopmentCount':5,'pendingDevelopmentCount':15,
 'evidencePath':(HERE/'confirmed-development-subset.json').relative_to(ROOT).as_posix(),
 'productEvaluationRun':False,'studentReleaseAuthorized':False,'reportCandidatesLocked':False})
rc9=next(x for x in issues['issues'] if x['id']=='RC-09')
rc9['evaluationPreparationStatus']='PARTIAL_HUMAN_REFERENCE_5_DEV_2025_REPORT_NOT_LOCKED'
rc9.setdefault('latestEvidencePaths',[]).append((HERE/'confirmed-development-subset.json').relative_to(ROOT).as_posix())
write(ROOT/names[4],issues)
append(names[5],'## 2026-10-08 Q01–Q05人工确认\n\n用户明确逐项确认Q01–Q05，已按原回复回填acceptedFacts/状态映射/原文位置及核对日期。开发者自核，无第二独立审核者。Q05补第18条，报告候选相关家族需复核或重划分；不将原36题草稿改标为人工通过。\n\nRuling: 只接纳用户实际确认的5题，不把其他15题、学生展示资格或模型成绩一并标通过 — 参考判断与资料放行/系统结果各自独立 — 成本是B-02整套参考仍OPEN，继续分批核对。\n\n本次无代码/数据库/模型执行；人工确认子集保存在独立证据批次。下一批Q06–Q10已准备原文与要点，等待实际核对。')
assert sha(original)==original_hash
current=read(ROOT/names[0]);assert sum(r['annotationStatus']=='HUMAN_CONFIRMED' for r in current['records'])==5
assert all(r['humanReview']['decision'] is None for r in current['records'][5:])
assert all(r['sourceQualification']=='NOT_APPROVED_FOR_STUDENT_RELEASE' for r in current['records'])
assert not current['locked'] and not current['usableForProductQualityGate']
assert {x['article'] for x in current['records'][4]['humanReview']['acceptedEvidence']}=={4,18}
write(HERE/'verification.json',{'recordedAt':now,'passed':True,'approvedDevelopmentCount':5,
 'pendingDevelopmentCount':15,'rawDraftUnchanged':True,'reportCandidatesLocked':False,
 'studentReleaseAuthorized':False,'productEvaluationRun':False,'databaseRun':False,'modelCalled':False,
 'files':[{'path':name,'sha256':sha(ROOT/name)} for name in names]+[
 {'path':original.relative_to(ROOT).as_posix(),'sha256':original_hash},
 {'path':(HERE/'confirmed-development-subset.json').relative_to(ROOT).as_posix(),'sha256':sha(HERE/'confirmed-development-subset.json')}]})
print(f'PASS human reference recorded: 5 confirmed, 15 pending; {len(affected)} report candidates need family-isolation recheck; no quality or release approval')
