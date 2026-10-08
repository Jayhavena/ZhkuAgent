"""记录本批真实确认并保留前态；人工参考确认不代表模型验收或资料发布。"""
from pathlib import Path
import copy
import datetime
import hashlib
import json
import re

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
NOW = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat()
NAMES = [
    'docs/product/rule-check-2025-human-reference-development.json',
    'docs/product/rule-check-2025-human-reference-development.md',
    'docs/product/rule-check-blockers.md',
    'docs/product/rule-check-review-handoff.md',
    'docs/evidence/rule-check-refactor/2026-10-07/issues.json',
    '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md',
]
FACTS = {
    'Q06': ['公共选修课授课时间原则上为16学时，保留“原则上”的限定。'],
    'Q07': ['公共选修课最多不超过32学时，单位是学时。'],
    'Q08': ['校内公共选修课选课人数一般要求30–150人，低于30人不予开课。'],
    'Q09': ['31人选课不足以确认实际开班。', '人数条款针对校内公共选修课，实际开课结果仍需核实。'],
    'Q10': ['人才培养方案是选课的重要依据。', '个人具体选修要求需要对应的培养方案。'],
}
NEXT_FACTS = {
    'Q11': ['原文比例以人才培养方案要求的选修课学分为基数，网络公选课一般不能超过其一半。',
            '不能改成全部毕业学分或单学期学分；进一步的类别口径需要对应培养方案确认。'],
    'Q12': ['原文使用“一般不能”，应保留限定。', '第8条未列出具体例外或审批办法，不能自行编造。'],
    'Q13': ['仅凭手册不能确定个人还缺多少选修学分。',
            '可解释培养方案依据；个人缺口还需要对应培养方案和可靠的已修记录。'],
    'Q14': ['依据教务处公布的公共选修课开课计划，在规定时间内完成网上选课。',
            '具体时间和入口需另查当期通知。'],
    'Q15': ['未经选课者，不得参加课程考核或考核成绩无效。',
            '保留原文“或”的关系，不能直接判定某个人的成绩。'],
}

def parse(raw):
    return json.loads(raw.decode('utf-8-sig'))

def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

def digest(raw):
    return hashlib.sha256(raw).hexdigest()

assert not (HERE / 'verification.json').exists(), '本批证据不能重复覆盖'
assert not (HERE / 'source-before.json').exists(), '若上次中途失败，应检查现状而非重跑'
before = {name: (ROOT / name).read_bytes() for name in NAMES}
data = parse(before[NAMES[0]])
assert len(data['records']) == 20 and data['approvedDevelopmentCount'] == 5
old_records = copy.deepcopy(data['records'])
raw_path = ROOT / 'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json'
raw_hash = digest(raw_path.read_bytes())
approved = []
for row in data['records']:
    if row['reviewId'] not in FACTS:
        continue
    assert row['humanReview']['decision'] is None
    row['annotationStatus'] = 'HUMAN_CONFIRMED'
    row['humanReview'] = {
        'reviewer': '用户（项目开发者，自核）', 'reviewedAt': '2026-10-08', 'recordedAt': NOW,
        'authority': 'DIRECT_USER_MESSAGE', 'confirmationText': '确认', 'decision': 'CONFIRMED',
        'confirmationScope': 'Q06–Q10（上一轮展示的五题审核批次）',
        'acceptedStatus': row['proposedStatus'], 'acceptedFacts': FACTS[row['reviewId']],
        'acceptedEvidence': copy.deepcopy(row['sourceEvidence']),
        'reason': '用户在上一轮Q06–Q10原文位置及参考要点展示后回复“确认”，按该批次上下文记录。',
        'reviewMethod': 'AUTHOR_SELF_REVIEW_WITH_AI_ASSISTED_DRAFT', 'independentSecondReviewer': False,
        'statusMappingNote': '业务状态映射自已展示的回答/部分回答类别判断；未运行模型输出评分。',
    }
    approved.append(copy.deepcopy(row))
assert len(approved) == 5
assert data['records'][:5] == old_records[:5] and data['records'][10:] == old_records[10:]
data.update(approvedDevelopmentCount=10, pendingDevelopmentCount=10,
            humanReferenceApprovalStatus='PARTIAL_10_OF_20_DEVELOPMENT_CASES')
assert not data['locked'] and not data['humanReferenceApproved'] and not data['usableForProductQualityGate']
data['notes'].append('2026-10-08用户回复“确认”，按上一轮Q06–Q10批次记录；累计10/20，Q11–Q20待核对，未进行产品评分或资料放行。')
after = {NAMES[0]: encoded(data)}
text = before[NAMES[1]].decode('utf-8-sig')
text = text.replace('Q01–Q05已有用户逐项人工确认，Q06–Q20仍为待核对草稿。',
                    'Q01–Q10已有用户人工确认（首批逐项、第二批按上下文批量确认），Q11–Q20仍为待核对草稿。')
text = text.replace('Q01–Q05已于2026-10-08确认；接下来审核Q06–Q10。',
                    'Q01–Q10已于2026-10-08确认，累计10/20；接下来审核Q11–Q15。')
for row in approved:
    match = re.search(rf'(### {row["reviewId"]}：.*?\n)(.*?)(?=\n### Q|\n## 本批记录边界)', text, re.S)
    assert match
    body = match.group(2)
    assert '- 你的结论及修改理由：________' in body
    body = body.replace('- 参考要点草稿：', '- 原草稿要点（保留）：')
    body = body.replace('- 审核选择：□ 确认　□ 修改　□ 依据不足　□ 有争议',
                        '- 审核结果：☑ 确认（用户确认本批Q06–Q10）')
    body = body.replace('- 你的结论及修改理由：________', '- 人工确认结论：' + '；'.join(FACTS[row['reviewId']]))
    body = body.replace('- 核对人／日期：________', '- 核对人／日期：用户（项目开发者，自核）／2026-10-08')
    text = text[:match.start(2)] + body + text[match.end(2):]
text = text.replace('已保存Q01–Q05实际人工确认子集，其他题仍待核对；',
                    '已分批保存Q01–Q10实际人工确认子集，其他题仍待核对；')
after[NAMES[1]] = text.encode('utf-8')

def appended(name, content):
    return (before[name].decode('utf-8-sig').rstrip() + '\n\n' + content + '\n').encode('utf-8')

after[NAMES[2]] = appended(NAMES[2], '## 2026-10-08第二批人工参考确认\n\n用户对上一轮Q06–Q10回复“确认”，已记录本批5题，累计10/20，Q11–Q20仍待核对。B-02保持OPEN：整套参考及报告候选未冻结，未执行产品质量评分。Q05关联第18条引出的家族隔离仍待复核。本批确认未改变资料学生展示资格。')
after[NAMES[3]] = appended(NAMES[3], '**2026-10-08第二批审核进度：**Q06–Q10按用户“确认”及上一轮上下文回填；[核对清单](rule-check-2025-human-reference-development.md)累计10/20。下一批Q11–Q15。历次确认和原草稿保留；报告集未冻结，条款家族隔离仍待复核。')
issues = parse(before[NAMES[4]])
subset_rel = (HERE / 'confirmed-development-subset.json').relative_to(ROOT).as_posix()
issues.setdefault('humanReferenceReviewHistory', []).append({
    'reviewDate': '2026-10-08', 'recordedAt': NOW, 'reviewer': '用户（项目开发者，自核）',
    'authority': 'DIRECT_USER_MESSAGE', 'confirmationText': '确认', 'confirmationScope': 'Q06–Q10',
    'confirmedIds': list(FACTS), 'approvedDevelopmentCount': 10, 'pendingDevelopmentCount': 10,
    'evidencePath': subset_rel, 'productEvaluationRun': False, 'studentReleaseAuthorized': False,
    'reportCandidatesLocked': False,
})
rc9 = next(x for x in issues['issues'] if x['id'] == 'RC-09')
rc9['evaluationPreparationStatus'] = 'PARTIAL_HUMAN_REFERENCE_10_DEV_2025_REPORT_NOT_LOCKED'
rc9.setdefault('latestEvidencePaths', []).append(subset_rel)
after[NAMES[4]] = encoded(issues)
after[NAMES[5]] = appended(NAMES[5], '## 2026-10-08 Q06–Q10人工确认\n\n用户回复“确认”，上下文为上一轮Q06–Q10审核批次，按已展示要点、处理状态和原文位置回填。累计10/20；前批5题及后10题记录保持不变。\n\n解决的问题：第二批参考判断此前仅为AI草稿；现有真实开发者自核记录及原回复。边界：无独立第二审核者，未执行模型评分、数据库操作或学生资料放行，B-02保持OPEN。\n\n独立证据批次：docs/evidence/rule-check-refactor/2026-10-07/human-reference-q06-q10-20261008-01。下一批Q11–Q15准备核对。')

# 先检查完整内存结果，再备份和写入，避免失配时悄悄覆盖已确认内容。
assert sum(r['annotationStatus'] == 'HUMAN_CONFIRMED' for r in data['records']) == 10
assert all(r['sourceQualification'] == 'NOT_APPROVED_FOR_STUDENT_RELEASE' for r in data['records'])
for name, raw in before.items():
    backup = HERE / 'before' / name
    backup.parent.mkdir(parents=True, exist_ok=True)
    backup.write_bytes(raw)
(HERE / 'source-before.json').write_bytes(encoded({'recordedAt': NOW, 'files': [
    {'path': n, 'sha256': digest(b)} for n, b in before.items()]}))
for name, raw in after.items():
    (ROOT / name).write_bytes(raw)
(HERE / 'confirmed-development-subset.json').write_bytes(encoded({
    'batchId': HERE.name, 'recordedAt': NOW, 'referenceRevision': data['referenceRevision'],
    'sourceSha256': data['sourceSha256'], 'confirmationText': '确认', 'confirmationScope': 'Q06–Q10',
    'authority': 'DIRECT_USER_MESSAGE', 'reviewMethod': 'AUTHOR_SELF_REVIEW_WITH_AI_ASSISTED_DRAFT',
    'independentSecondReviewer': False, 'batchConfirmedCount': 5, 'approvedDevelopmentCount': 10,
    'records': approved, 'productEvaluationRun': False, 'studentReleaseAuthorized': False,
    'reportCandidatesLocked': False, 'reportFamilyIsolationStatus': data['reportFamilyIsolationStatus'],
}))
(HERE / 'next-review-batch.json').write_bytes(encoded({
    'batchId': 'Q11-Q15', 'referenceRevision': data['referenceRevision'], 'sourceSha256': data['sourceSha256'],
    'annotationStatus': 'PENDING_HUMAN_REVIEW', 'humanReferenceApproved': False,
    'records': [{'reviewId': r['reviewId'], 'question': r['question'], 'proposedStatus': r['proposedStatus'],
                 'proposedFactsForReview': NEXT_FACTS[r['reviewId']], 'sourceEvidence': r['sourceEvidence']}
                for r in data['records'] if r['reviewId'] in NEXT_FACTS],
}))
current = parse((ROOT / NAMES[0]).read_bytes())
assert current == data and current['records'][:5] == old_records[:5] and current['records'][10:] == old_records[10:]
assert digest(raw_path.read_bytes()) == raw_hash
assert all((ROOT / n).read_bytes() == b for n, b in after.items())
(HERE / 'verification.json').write_bytes(encoded({
    'recordedAt': NOW, 'passed': True, 'approvedDevelopmentCount': 10, 'pendingDevelopmentCount': 10,
    'priorConfirmedRecordsUnchanged': True, 'remainingPendingRecordsUnchanged': True, 'rawDraftUnchanged': True,
    'reportCandidatesLocked': False, 'studentReleaseAuthorized': False, 'productEvaluationRun': False,
    'databaseRun': False, 'modelCalled': False, 'files': [
        {'path': n, 'sha256': digest((ROOT / n).read_bytes())} for n in NAMES] + [
        {'path': raw_path.relative_to(ROOT).as_posix(), 'sha256': raw_hash},
        {'path': subset_rel, 'sha256': digest((HERE / 'confirmed-development-subset.json').read_bytes())},
        {'path': (HERE / 'next-review-batch.json').relative_to(ROOT).as_posix(),
         'sha256': digest((HERE / 'next-review-batch.json').read_bytes())}],
}))
print('PASS: Q06-Q10 recorded; 10 confirmed, 10 pending; prior reviews and original draft unchanged.')
