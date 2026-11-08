import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
OUT = Path(__file__).resolve().parent
DATA = ROOT / 'knowledge/src/test/resources/agent-effect-eval/rule-fact-check-effect-v2-dev.json'
MANIFEST = DATA.with_name('manifest-v2-dev.json')
B02 = ROOT / 'docs/product/rule-check-2025-b02-semantic-review.json'
B03 = ROOT / 'docs/product/rule-check-2025-b03-publication-review.json'

def read(path):
    return json.loads(path.read_text(encoding='utf-8'))

def sha(data):
    return hashlib.sha256(data).hexdigest()

d, m, b02, b03 = map(read, (DATA, MANIFEST, B02, B03))
cases = d['cases']
clauses = {c['locatorId']: c for c in b03['clauses']}
facts = {(r['reviewItemId'], f['id']): f for r in b02['rules'] for f in r['facts']}
issues = []
def check(condition, location, detail):
    if not condition:
        issues.append(f'{location}: {detail}')

check(len(cases) == d['caseCount'] == m['caseCount'] == 20, 'dataset', '题数与声明不一致')
check(len({c['caseId'] for c in cases}) == len(cases), 'dataset', '题号重复')
check({c['caseId'] for c in cases} == {f'RFE-{i:03d}' for i in range(1, 21)}, 'dataset', '题号集合不完整')
for key in ('datasetId', 'datasetVersion', 'corpusFingerprint'):
    check(d[key] == m[key], 'manifest', f'{key} 不一致')
check(m['datasetFile'] == DATA.name, 'manifest', '文件名不一致')
check(sha(DATA.read_bytes()) == m['datasetSha256'], 'manifest', '数据集 SHA-256 不一致')
for artifact in d['sourceSnapshot']['sourceArtifacts']:
    p = ROOT / artifact['path']
    check(p.is_file() and sha(p.read_bytes()) == artifact['sha256'], artifact['path'], '来源文件缺失或 SHA-256 不一致')

groups = defaultdict(list)
risks = {}
allowed_tags = set(d['scoringContract']['criticalFailureTags'])
for c in cases:
    cid = c['caseId']
    for key in ('caseId', 'split', 'samplingStatus', 'documentGroup', 'questionFamily', 'caseFamily', 'question', 'expectedDisposition', 'sourceCoverage', 'humanReviewStatus'):
        check(isinstance(c.get(key), str) and bool(c[key].strip()), cid, f'{key} 缺失或为空')
    for key in ('expectedFacts', 'requiredEvidence', 'requiredResponseElements', 'mustNotClaim', 'criticalFailureTags'):
        check(isinstance(c.get(key), list), cid, f'{key} 不是数组')
    tags = c['criticalFailureTags']
    check(bool(tags) and len(tags) == len(set(tags)) and set(tags) <= allowed_tags, cid, '标签为空、重复或未声明')
    locators = set()
    for e in c['requiredEvidence']:
        locator = e['locatorId']; locators.add(locator)
        clause = clauses.get(locator)
        if not clause:
            check(False, cid, f'B03 无定位 {locator}'); continue
        for key in ('article', 'physicalPage', 'printedPage', 'contentChecksum'):
            check(e[key] == clause[key], cid, f'{locator} 的 {key} 不一致')
        check(sha(clause['content'].encode('utf-8')) == clause['contentChecksum'], cid, f'{locator} 条款文本哈希不一致')
    for f in c['expectedFacts']:
        key = (f['reviewItemId'], f['semanticFactId']); source = facts.get(key)
        if source is None:
            check(False, cid, f'B02 无事实 {key}'); continue
        check(source['semanticReviewState'] == 'CONFIRMED', cid, f'{key} 未确认')
        check(source['text'] == f['expectedClaim'], cid, f'{key} 事实文字不一致')
        check(source['evidenceDraft']['locatorId'] in locators, cid, f'{key} 缺少对应引用')
    flags = []
    if c['expectedDisposition'] == 'ASK_CLARIFICATION' or 'CLARIFICATION' in c['caseFamily']:
        flags.append('澄清必要性：检查缺失条件是否影响结论、追问是否充分')
    if c['sourceCoverage'] != 'IN_SCOPE' or 'GAP' in c['caseFamily']:
        flags.append('证据缺口：区分规则与当期通知、个人记录或语料外信息')
    if any(x in c['caseFamily'] for x in ('SCOPE', 'QUALIFIED', 'BOUNDARY')):
        flags.append('适用范围/限定：核对对象、数字和例外，不得扩大结论')
    text = ' '.join([c['question']] + c['requiredResponseElements'] + c['mustNotClaim'] + [f['expectedClaim'] for f in c['expectedFacts']])
    if any(x in text for x in ('原则上', '可申请', '不一定', '例外')):
        flags.append('限定措辞：检查原则上、可申请等是否被强化为绝对结论')
    if len(c['requiredEvidence']) > 1 or len(c['expectedFacts']) > 1:
        flags.append('多事实/多证据：检查所有必要分支和引用是否完整')
    if not c['requiredEvidence']:
        flags.append('无引用依据：检查语料外处置；不能把无引用当成证据正确')
    if cid in {'RFE-001', 'RFE-013', 'RFE-018', 'RFE-020'}:
        flags.append('历史记录指定优先复核题')
    if cid == 'RFE-020':
        flags.append('已知待定：无事实/证据时 CITATION_MISMATCH 标签的含义尚未明确')
    risks[cid] = flags or ['常规核对：检查问题、预期和证据是否一致']
    if c['requiredEvidence']:
        for locator in sorted(locators, key=lambda v: clauses[v]['article'] if v in clauses else 999):
            groups[locator].append(c)
    else:
        groups[None].append(c)

lines = ['# 规则查证评测：按条款分组的审核卡', '',
    '状态：待审核的开发草稿；本文件未改变原始数据、审核状态或运行时配置。',
    f'数据集 SHA-256：`{sha(DATA.read_bytes())}`；共 {len(cases)} 题。',
    '依据为 B03 保存的 2025.9.2 条款快照；原始 PDF 字节不在工作树，当期适用性及实际运行时绑定未核验。',
    '风险标记为基于字段和关键词的提示，并非已确认问题或自动审核结论。涉及多个条款的题会重复展示；审核时以题号为准。', '',
    '审核建议：先核对每组原文及事实，再核对问题、预期、遗漏和禁止断言。勾选或填写的意见仅记录在本卡片，不自动回写数据集。', '',
    f'## 结构检查结果：{len(issues)} 项异常', '',
    '检查范围：题数/ID、必填字段类型、标签、manifest 与来源文件哈希、B02 事实及 B03 条款定位/页码/文本哈希。空事实和空引用对语料外题可成立。',
    '这些检查不验证语义正确性、原始 PDF、当前适用性、实际数据库绑定，也不代表 Agent 效果。', '']
lines += [f'- {x}' for x in issues] if issues else ['- 本次检查范围内未发现结构异常。']
lines += ['', '## 条款审核卡', '']
order = sorted((k for k in groups if k is not None), key=lambda k: clauses[k]['article']) + ([None] if None in groups else [])
for locator in order:
    if locator is None:
        lines += ['### 无对应条款（语料外或无引用）', '']
    else:
        clause = clauses[locator]
        lines += [f"### 第 {clause['article']} 条 · {locator}", '', f"2025.9.2 快照；PDF 物理页 {clause['physicalPage']}，印刷页 {clause['printedPage']}。", '', '**B03 保存的条款原文：**', '', '> ' + clause['content'].replace('\n', '\n> '), '']
    for c in groups[locator]:
        lines += [f"#### {c['caseId']}：{c['question']}", '', f"- 题型：`{c['caseFamily']}`；覆盖：`{c['sourceCoverage']}`；预期处置：`{c['expectedDisposition']}`。", f"- 数据集审核状态：`{c['humanReviewStatus']}`。", '- 风险提示：']
        lines += [f'  - {x}' for x in risks[c['caseId']]]
        lines += ['', '**预期事实：**']
        lines += [f"- `{f['reviewItemId']}/{f['semanticFactId']}`：{f['expectedClaim']}" for f in c['expectedFacts']] or ['- 无。']
        lines += ['', '**回答应包含：**'] + [f'- {x}' for x in c['requiredResponseElements']]
        lines += ['', '**不得声称：**'] + [f'- {x}' for x in c['mustNotClaim']]
        lines += ['', '**引用：** ' + ('；'.join(f"第 {e['article']} 条 / 物理页 {e['physicalPage']} / 印刷页 {e['printedPage']} / `{e['locatorId']}`" for e in c['requiredEvidence']) if c['requiredEvidence'] else '无'), '',
                  '- [ ] 问题与预期匹配；事实及引用支持预期。', '- [ ] 适用条件、缺口与禁止断言合适。', '- [ ] 通过　- [ ] 需修改　- [ ] 暂无法判断', '- 审核人 / 日期：________　意见：________________', '']

report = '\n'.join(lines) + '\n'
(OUT / '审核卡.md').write_text(report, encoding='utf-8')
summary = {'datasetSha256': sha(DATA.read_bytes()), 'caseCount': len(cases), 'distinctCasesInCards': len({c['caseId'] for cs in groups.values() for c in cs}), 'articleGroups': len(groups) - (1 if None in groups else 0), 'unreferencedCases': [c['caseId'] for c in groups.get(None, [])], 'structuralIssues': issues, 'riskFlags': risks}
(OUT / '检查结果.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'caseCount': len(cases), 'articleGroups': summary['articleGroups'], 'unreferencedCases': summary['unreferencedCases'], 'issues': issues}, ensure_ascii=True))
