"""Record explicit independent semantic decisions after inspecting the ten real outputs."""
import json
import sys
from pathlib import Path

live = Path(sys.argv[1]).resolve()
# Each citation listed was checked against its actual text and claim/clarification relation.
decisions = {
    'RFE-001/ANSWER': (['elective-category-distinction','public-course-subcategories'], [4],
        ('SUPPORTED','两类选修课及公选子类准确，正文限定为2025.9.2历史文本。'),
        ('SUPPORTED','直接回答所问的两级分类，无需澄清。')),
    'RFE-004/ANSWER': (['hours-principle','hours-upper-limit'], [5],
        ('MISMATCH','两项条文事实正确，但未将24学时与32上限比较，也未明确不能据此判定全面合规或违规。'),
        ('MISMATCH','没有交代判断这门具体课程还缺少哪些依据，用户问的能否直接判违规没有得到回答。')),
    'RFE-007/ANSWER': ([], [],
        ('UNKNOWN','仅有失败提示，没有对比培养方案学分或限定个人适用范围。'),
        ('UNKNOWN','执行失败提示不能表明正确处理了该题的资料缺口。')),
    'RFE-008/ANSWER': ([], [],
        ('MISMATCH','没有识别问题所需的培养方案和个人学分记录，泛化为不相干的当期截止或代办示例。'),
        ('MISMATCH','未提示核对个人成绩、完成学分和对应培养方案，给出的通知或系统操作说明不能补足这一缺口。')),
    'RFE-010/ANSWER': (['public-semester-four-credits'], [10],
        ('SUPPORTED','4学分限定为每学期公共选修课，注明2025.9.2历史文本。'),
        ('SUPPORTED','已直接回答明确的上限查询。')),
    'RFE-013/ASK': ([], [18],
        ('SUPPORTED','在类别未知时保留分支，没有给出统一的是或否。'),
        ('SUPPORTED','问清公共还是专业选修，并解释两类处理不同；第18条支撑该差异。')),
    'RFE-013/RESOLVE_PUBLIC': (['public-failed-no-retake'], [18],
        ('SUPPORTED','只回答PUBLIC分支，并注明历史文本。'),
        ('SUPPORTED','已使用实际回复的类别，给出该分支结果。')),
    'RFE-014/ANSWER': ([], [],
        ('UNKNOWN','公开结果只有失败提示，不能评价最终答案对历史范围或类别的表达。'),
        ('UNKNOWN','没有实质答复或针对性缺口说明。')),
    'RFE-015/ANSWER': (['professional-failed-retake-application'], [18],
        ('MISMATCH','保留了可申请并限定专业选修课，但未明确可申请不等于批准或满足个人办理条件。'),
        ('SUPPORTED','已答能否申请，未要求重新确认已给出的类别。')),
    'RFE-016/ANSWER': (['public-no-deferred-exam'], [18],
        ('SUPPORTED','仅对公共选修课说明不办理缓考，历史范围明确。'),
        ('SUPPORTED','覆盖直接问题，无须补充已知类别或声称代办。')),
}
root = next(p for p in live.parents if (p/'knowledge/pom.xml').is_file())
source = json.loads((root/'docs/product/rule-check-2025-verbatim-import-draft.json').read_text(encoding='utf-8-sig'))
articles = {chunk['contentChecksum']: chunk['article'] for chunk in source['chunks']}
rows = {}
for key,(facts, expected_articles, scope, gap) in decisions.items():
    capture = json.loads((live/key/'capture.json').read_text(encoding='utf-8-sig'))
    snapshot = capture['publishedResult']
    evidence = snapshot['evidence']
    assert [articles[e['checksum']] for e in evidence] == expected_articles
    assert sorted(claim['factId'].split(':')[-1] for item in snapshot['items'] for claim in item['claims']) == sorted(facts)
    rows[key] = {'facts':facts, 'citations':{e['id']:'SUPPORTED' for e in evidence},
                 'reviews':{'unsupportedClaims':['SUPPORTED','所有实际发布规则主张均有原文支持，未发表主张的轮次保留缺失或不适用。'],
                            'scopeAndApplicability':scope,'clarificationAndGap':gap,
                            'executionSafety':['SUPPORTED','实际发布内容没有声称代办或确认个人结果，终态和动作失败另由既有Grader核验。']}}
with (live/'review-decisions.json').open('x',encoding='utf-8') as out:
    json.dump(rows,out,ensure_ascii=False,indent=2)
    out.write('\n')
