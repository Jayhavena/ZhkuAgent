"""接纳用户对Q11–Q20的实际确认；审核范围只含开发参考，不含上线与模型成绩。"""
from pathlib import Path
import copy, datetime, hashlib, json, re

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
NOW = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat()
REPLY = '11-20我都看过了，保持确认'
NAMES = ['docs/product/rule-check-2025-human-reference-development.json',
         'docs/product/rule-check-2025-human-reference-development.md',
         'docs/product/rule-check-blockers.md', 'docs/product/rule-check-review-handoff.md',
         'docs/evidence/rule-check-refactor/2026-10-07/issues.json',
         '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']

def parse(raw): return json.loads(raw.decode('utf-8-sig'))
def encode(value): return (json.dumps(value, ensure_ascii=False, indent=2)+'\n').encode('utf-8')
def sha(raw): return hashlib.sha256(raw).hexdigest()
def append(name, content): return (before[name].decode('utf-8-sig').rstrip()+'\n\n'+content+'\n').encode('utf-8')

assert not (HERE/'source-before.json').exists(), '保留既有批次，不重复覆盖'
before = {n:(ROOT/n).read_bytes() for n in NAMES}
data = parse(before[NAMES[0]])
assert len(data['records']) == 20 and data['approvedDevelopmentCount'] == 10
old = copy.deepcopy(data['records'])
original = ROOT/'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json'
original_hash = sha(original.read_bytes())
next_batch = parse((HERE.parent/'human-reference-q06-q10-20261008-01/next-review-batch.json').read_bytes())
shown = {r['reviewId']:r['proposedFactsForReview'] for r in next_batch['records']}
approved = []
for row in data['records'][10:]:
    assert row['humanReview']['decision'] is None
    row['annotationStatus'] = 'HUMAN_CONFIRMED'
    # 后五题按用户已读工作表的草稿与边界接纳，不补写未展示的新结论。
    facts = shown.get(row['reviewId'], row['proposedFacts'])
    row['humanReview'] = {
        'reviewer':'用户（项目开发者，自核）', 'reviewedAt':'2026-10-08', 'recordedAt':NOW,
        'authority':'DIRECT_USER_MESSAGE', 'confirmationText':REPLY, 'confirmationScope':'Q11–Q20',
        'decision':'CONFIRMED', 'acceptedStatus':row['proposedStatus'], 'acceptedFacts':copy.deepcopy(facts),
        'acceptedBoundary':row['proposedBoundary'], 'acceptedEvidence':copy.deepcopy(row['sourceEvidence']),
        'reviewedArtifact': ('上一轮Q11–Q15参考要点与核对清单' if row['reviewId'] in shown else '核对清单Q16–Q20草稿及边界'),
        'reason':'用户明确表示11–20均已看过并确认；仅按相应已展示要点及工作表边界记录。',
        'reviewMethod':'AUTHOR_SELF_REVIEW_WITH_AI_ASSISTED_DRAFT', 'independentSecondReviewer':False,
        'statusMappingNote':'已确认开发参考的预期业务状态；未对实际模型输出评分。',
    }
    approved.append(copy.deepcopy(row))
assert len(approved) == 10 and data['records'][:10] == old[:10]
data.update(approvedDevelopmentCount=20, pendingDevelopmentCount=0,
            humanReferenceApprovalStatus='CONFIRMED_20_OF_20_DEVELOPMENT_CASES',
            humanReferenceApproved=True, humanReferenceApprovalScope='DEVELOPMENT_CASES_ONLY',
            usableForDevelopmentEvaluation=True, usableForProductQualityGate=False, locked=False)
data['notes'].append('2026-10-08用户明确确认Q11–Q20，开发参考20/20完成自核。humanReferenceApproved仅指本工作表开发题；报告集/正式评分rubric未冻结，不能作为产品质量门禁。')
after = {NAMES[0]:encode(data)}
text = before[NAMES[1]].decode('utf-8-sig')
text = text.replace('Q01–Q10已有用户人工确认（首批逐项、第二批按上下文批量确认），Q11–Q20仍为待核对草稿。',
                    'Q01–Q20均已有用户人工确认，开发题核对完成20/20（首批逐项，后续批量确认）。')
text = text.replace('Q01–Q10已于2026-10-08确认，累计10/20；接下来审核Q11–Q15。',
                    'Q01–Q20已于2026-10-08全部确认，累计20/20。接下来复核开发/报告家族隔离并准备评分标准；开发参考完成不等于报告集验收。')
for row in approved:
    m = re.search(rf'(### {row["reviewId"]}：.*?\n)(.*?)(?=\n### Q|\n## 本批记录边界)', text, re.S)
    assert m and '- 你的结论及修改理由：________' in m.group(2)
    body = m.group(2).replace('- 参考要点草稿：', '- 原草稿要点（保留）：')
    body = body.replace('- 审核选择：□ 确认　□ 修改　□ 依据不足　□ 有争议', '- 审核结果：☑ 确认（用户明确确认Q11–Q20）')
    body = body.replace('- 你的结论及修改理由：________', '- 人工确认结论：'+'；'.join(row['humanReview']['acceptedFacts']))
    body = body.replace('- 核对人／日期：________', '- 核对人／日期：用户（项目开发者，自核）／2026-10-08')
    text = text[:m.start(2)]+body+text[m.end(2):]
text = text.replace('- 建议处理方式（待确认）：', '- 已确认处理方式：')
text = text.replace('已分批保存Q01–Q10实际人工确认子集，其他题仍待核对；整套参考尚未冻结。',
                    '已分批保存Q01–Q20实际人工确认子集，开发参考核对完成。报告候选与正式评分标准尚未冻结；本工作表可用于开发诊断，不能代表产品质量通过。')
after[NAMES[1]] = text.encode('utf-8')
after[NAMES[2]] = append(NAMES[2], '## 2026-10-08开发参考核对完成\n\n用户原话“11-20我都看过了，保持确认”，已记录Q11–Q20，累计20/20。B-02的开发参考人工核对子任务完成；其余报告集家族隔离、人工审核与评分rubric/一致性、真实运行仍OPEN。B-01展示资格及当期适用性未改变。本轮继续执行家族隔离审计，不重复要求开发20题确认。')
after[NAMES[3]] = append(NAMES[3], '**2026-10-08开发参考完成：**用户确认Q11–Q20，累计20/20；[工作表](rule-check-2025-human-reference-development.md)已回填。开发参考可用于诊断；报告候选与评分标准未冻结。继续审计Q05第18条依赖造成的家族交叉，准备受控主题规则与评分草案；资料展示和模型效果仍分开验收。')
issues = parse(before[NAMES[4]])
subset = (HERE/'confirmed-development-subset.json').relative_to(ROOT).as_posix()
issues.setdefault('humanReferenceReviewHistory', []).append({
    'reviewDate':'2026-10-08', 'recordedAt':NOW, 'reviewer':'用户（项目开发者，自核）',
    'authority':'DIRECT_USER_MESSAGE', 'confirmationText':REPLY, 'confirmedIds':[r['reviewId'] for r in approved],
    'approvedDevelopmentCount':20, 'pendingDevelopmentCount':0, 'evidencePath':subset,
    'productEvaluationRun':False, 'studentReleaseAuthorized':False, 'reportCandidatesLocked':False})
rc9 = next(x for x in issues['issues'] if x['id']=='RC-09')
rc9['evaluationPreparationStatus'] = 'HUMAN_REFERENCE_20_DEV_CONFIRMED_2025_REPORT_NOT_LOCKED'
rc9.setdefault('latestEvidencePaths', []).append(subset)
after[NAMES[4]] = encode(issues)
after[NAMES[5]] = append(NAMES[5], '## 2026-10-08 Q11–Q20人工确认，开发参考20/20完成\n\n原回复“11-20我都看过了，保持确认”。Q11–Q15按上一轮已展示要点回填，Q16–Q20按核对工作表已读草稿及边界回填，前10题保持不变。开发者自核，无独立第二审核者。\n\n解决的问题：开发参考的人工核对空缺已补齐；形成可追溯的20题开发诊断依据。未解决：报告集家族隔离、评分rubric与一致性、真实回答运行、资料展示资格。humanReferenceApproved只覆盖开发工作表；quality gate仍false，locked仍false。\n\n独立证据批次：docs/evidence/rule-check-refactor/2026-10-07/human-reference-q11-q20-20261008-01。继续家族隔离审计与评分准备，不再请求这20题重复确认。')

assert all(r['humanReview']['decision']=='CONFIRMED' for r in data['records'])
assert all(r['sourceQualification']=='NOT_APPROVED_FOR_STUDENT_RELEASE' for r in data['records'])
for n, raw in before.items():
    p = HERE/'before'/n; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(raw)
(HERE/'source-before.json').write_bytes(encode({'recordedAt':NOW,'files':[{'path':n,'sha256':sha(b)} for n,b in before.items()]}))
for n, raw in after.items(): (ROOT/n).write_bytes(raw)
(HERE/'confirmed-development-subset.json').write_bytes(encode({
    'batchId':HERE.name, 'recordedAt':NOW, 'referenceRevision':data['referenceRevision'],
    'sourceSha256':data['sourceSha256'], 'confirmationText':REPLY, 'confirmationScope':'Q11–Q20',
    'authority':'DIRECT_USER_MESSAGE', 'reviewMethod':'AUTHOR_SELF_REVIEW_WITH_AI_ASSISTED_DRAFT',
    'independentSecondReviewer':False, 'batchConfirmedCount':10, 'approvedDevelopmentCount':20,
    'records':approved, 'productEvaluationRun':False, 'studentReleaseAuthorized':False, 'reportCandidatesLocked':False}))
assert parse((ROOT/NAMES[0]).read_bytes())['records'][:10] == old[:10]
assert sha(original.read_bytes()) == original_hash
assert all((ROOT/n).read_bytes()==b for n,b in after.items())
(HERE/'verification.json').write_bytes(encode({
    'recordedAt':NOW, 'passed':True, 'approvedDevelopmentCount':20, 'pendingDevelopmentCount':0,
    'priorConfirmedRecordsUnchanged':True, 'rawDraftUnchanged':True, 'humanReferenceApprovalScope':'DEVELOPMENT_CASES_ONLY',
    'reportCandidatesLocked':False, 'studentReleaseAuthorized':False, 'productEvaluationRun':False,
    'databaseRun':False, 'modelCalled':False,
    'files':[{'path':n,'sha256':sha((ROOT/n).read_bytes())} for n in NAMES]+[
        {'path':original.relative_to(ROOT).as_posix(),'sha256':original_hash},
        {'path':subset,'sha256':sha((HERE/'confirmed-development-subset.json').read_bytes())}]}))
print('PASS: Q11-Q20 recorded; development review 20/20; prior reviews and source draft preserved.')
