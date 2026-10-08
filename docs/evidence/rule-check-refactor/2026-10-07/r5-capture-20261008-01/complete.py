from pathlib import Path
import hashlib, json, difflib
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[5]
batch = Path(__file__).resolve().parent
assert not (batch / 'acceptance.md').exists(), '不得复写收口历史'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
write = lambda p, value: p.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
baseline = read(batch / 'source-before.json')
resolution = read(batch / 'review-resolution.json')
assert resolution['criticalRemaining'] == resolution['importantRemaining'] == 0
assert (batch / 'review.md').exists()
final_unit = resolution.get('finalUnitPhase', 'r5-capture-regression-20261008-01')
final_db = resolution.get('finalDatabasePhase', 'r5-capture-db-green-20261008-01')
verified = {}
for phase in [final_unit, final_db]:
    directory = batch.parent / phase
    result = read(directory / 'results.json')
    assert result['passed'] and result['exitCode'] == 0 and result['sourceDrift'] == 0
    assert result['resourcesVerified'] and result['containerExited'] and not result['missingXml']
    count = 0
    for suite in result['suites']:
        xml = directory / 'xml' / suite['xml']
        assert sha(xml) == suite['sha256']
        actual = ET.parse(xml).getroot()
        assert all(int(actual.get(k, '0')) == 0 for k in ['failures','errors','skipped'])
        count += int(actual.get('tests'))
    assert count == result['testCount']
    sources = read(directory / 'source-after.json')
    assert all(sha(root / row['path']) == row['sha256'] for row in sources)
    verified[phase] = dict(tests=count, classes=len(result['suites']), sourceFiles=len(sources), xmlVerified=True,
                           sourceCurrent=True, resourcesVerified=True)
database = read(batch.parent / final_db / 'database.json')
assert database['verified'] and database['connectionsAfterTests'] == 0
assert not read(batch.parent / final_db / 'database-attempt.json')['volumeMounted']
contract = read(batch / 'runner-contract.json')
assert contract['passed'] and contract['checks'] == 29
original = root / 'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json'
assert sha(original) == baseline['original36Sha256']
reference = read(root / 'docs/product/rule-check-2025-human-reference-development.json')
draft = read(root / 'docs/product/rule-check-2025-rule-template-draft.json')
assert draft['ruleDefinitionReviewStatus'] == 'DRAFT' and not draft['runtimeActivated'] and not draft['studentVisible']
assert draft['referenceArtifactSha256'] == sha(root / 'docs/product/rule-check-2025-human-reference-development.json')

plan = root / 'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md'
text = plan.read_text(encoding='utf-8')
start = text.index('### Task2C：')
end = text.index('## Task3：', start)
text = text[:start] + text[start:end].replace('- [ ]', '- [x]') + text[end:]
plan.write_text(text, encoding='utf-8')
note = f'''
## 2026-10-08 R5 Task2C共享检索证据连接

现有两路候选SQL已读sourceId但RetrievedChunk丢弃它。本批保留持久来源身份，复用引用ID工厂、当前核验器及审计仓储，整批通过才返回不可变快照。旧七参构造器保持兼容，缺来源身份只被新链路拒绝，不伪造UUID。

实际：12项定向GREEN；最终{verified[final_unit]['classes']}类{verified[final_unit]['tests']}项相关纯单元、{verified[final_db]['tests']}项临时库测试、29项runner合同通过。数据库为无卷合成夹具，V{database['migration']}，结束连接0，容器退出；embedding及审计为替身，没有供应商成绩。详见[R5 Task2C验收](../evidence/rule-check-refactor/2026-10-07/r5-capture-20261008-01/acceptance.md)。

工程状态RETRIEVAL_CAPTURE_VERIFIED_NOT_HTTP_INTEGRATED。B-03推进共享检索→当前核验连接；旧自由RAG风险仍OPEN。B-01真实2025资料/规则审核与展示资格、B-02报告补样/任务家族/rubric、HTTP账号/CSRF/最终下发及实际用户效果未完成。捕获及决策顺序重读不等于最终下发原子性；审计失败可能留部分CAPTURE记录，但不返回证据。
'''
with (root / 'docs/product/rule-check-blockers.md').open('a', encoding='utf-8') as f: f.write(note)
with (root / 'docs/product/rule-check-decision-contract.md').open('a', encoding='utf-8') as f:
    f.write('''
## 共享检索证据捕获合同（Task2C）

RuleCheckEvidenceCapture仅消费服务端两路检索结果；RetrievedChunk现在保留SQL中实际sourceId。旧七参构造器仍可用于旧链路，但新链拒绝缺来源身份、重复chunk或不唯一引用别名，不按标题/正文猜来源。捕获复用已有CitationIdFactory与CitationAuditRepository，按整段原文调用现有核验器检查hash/UTF-16/当前身份及字段资格。所有片段通过后再写审计，全部审计成功才返回不可变快照。

RULE_CHECK_CAPTURE表示证据取得尝试，不是学生已看到回答。逐条仓储写入失败可保留部分审计，不返回快照、不承诺事务原子性；实际审计数据库持久化另行验收。捕获后规则决策仍须再次核验必要绑定，不能消除最终下发窗口。未添加客户端快照DTO、HTTP入口、自然语言规则选择或真实规则激活。临时库只验证合成SQL链路，不改变2025资料发布状态。
''')
issues_path = root / 'docs/evidence/rule-check-refactor/2026-10-07/issues.json'
issues = read(issues_path)
matches = []
def visit(value):
    if isinstance(value, dict):
        if value.get('id') == 'RC-04-SEMANTICS': matches.append(value)
        for nested in value.values(): visit(nested)
    elif isinstance(value, list):
        for nested in value: visit(nested)
visit(issues)
assert len(matches) == 1
semantic = matches[0]
assert semantic['status'] == 'OPEN'
semantic['engineeringComponents'].append(dict(name='RuleCheckEvidenceCapture', stage='RETRIEVAL_CAPTURE_VERIFIED_NOT_HTTP_INTEGRATED',
    evidencePath='docs/evidence/rule-check-refactor/2026-10-07/r5-capture-20261008-01/acceptance.md', manualEvaluationStatus='NOT_RUN'))
semantic['closureReminder'] = '共享检索已连接当前核验；真实2025资料与规则审核/展示资格、自然语言匹配、HTTP账号及最终下发、独立报告质量和用户收益仍未通过，旧自由RAG风险仍OPEN'
write(issues_path, issues)
ledger = root / '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md'
with ledger.open('a', encoding='utf-8') as f:
    f.write(f'\nTask2C: complete — 有效RED单元12/9失败、真库26/3失败→GREEN12及真库41；最终{verified[final_unit]["classes"]}类{verified[final_unit]["tests"]}单元、29合同。一次独立审查见r5-capture-20261008-01/review.md；结果及裁定见review-resolution.json。无commit/部署，源码增量按before字节留证。\n')
    for ruling in resolution['rulings']: f.write('\nFinal: Ruling: '+ruling+'\n')
    for minor in resolution['deferredMinors']: f.write('\nFinal: minor (deferred): '+minor+'\n')
    f.write('\n后续Task2/3/4及G0/G2保持未完成；工程连接不自动审核真实规则或资料，也不关闭旧RAG语义风险。\n')

after, diff = [], []
for row in baseline['files']:
    p = root / row['path']
    data = p.read_bytes()
    after.append(dict(path=row['path'], sha256=sha(p)))
    before = (batch / row['before']).read_bytes() if row['before'] else b''
    diff.extend(difflib.unified_diff(before.decode('utf-8').splitlines(True), data.decode('utf-8').splitlines(True),
        fromfile='before/'+row['path'], tofile='after/'+row['path']))
write(batch / 'source-after.json', after)
(batch / 'changes-final.diff').write_text(''.join(diff), encoding='utf-8')
metrics = dict(stage='RETRIEVAL_CAPTURE_VERIFIED_NOT_HTTP_INTEGRATED', verified=verified,
    targetedTests=12, runnerContractChecks=29, database=database, embedding='SYNTHETIC_STUB', audit='IN_MEMORY_STUB',
    sourceDrift=0, original36Unchanged=True, ruleDefinitionReviewStatus='DRAFT', runtimeActivated=False,
    studentVisible=False, qualityGatePassed=False, realProviderEvaluated=False, userBenefitEvaluated=False,
    review=resolution)
write(batch / 'metrics.json', metrics)
(batch / 'acceptance.md').write_text(f'''# R5 Task2C：共享检索证据连接验收

2026-10-08。阶段：RETRIEVAL_CAPTURE_VERIFIED_NOT_HTTP_INTEGRATED，完整R5/学生入口未完成。

解决问题：现有词法/向量SQL虽然取得sourceId，却在候选DTO丢弃，导致新核验无法确认持久来源身份。现已保留它，使用原有混合检索、引用工厂、当前reader及核验器生成证据，不新建检索引擎。旧七参构造器兼容，新链遇到缺身份不猜测来源。

|检查|实际结果|
|---|---|
|首轮测试编译|构造器误用已修；没有XML，不计RED，历史保留|
|有效单元RED→GREEN|12项，9断言失败/0异常/0跳过→12通过|
|有效真库RED→GREEN|26项，3断言失败/0异常/0跳过→最终2类41通过|
|相关纯单元回归|{verified[final_unit]['classes']}类{verified[final_unit]['tests']}，失败/异常/跳过均0|
|runner目标/报告/退出合同|29通过；计数替身，没有真实供应商或数据库操作|
|独立审查|一次，详情review.md和review-resolution.json|
|临时库退出|无数据卷、迁移V{database['migration']}、连接0、容器已退出|
|源码与XML|最终两批指纹核对通过，运行漂移0；白名单before/after及增量留存|
|资料边界|原36题未改；2025规则DRAFT、不激活、不展示学生；真实质量NOT_RUN|

纯单元验证正常持久身份/服务器别名、旧候选拒绝、重复片段/别名、工厂失败、4→40、撤回/版本变化、reader故障、部分审计失败及不可变产物。真库验证实际词法和向量检索携带sourceId，经现有混合排序进入当前核验器，以及顺序撤回后旧候选拒绝。embedding和引用审计均为替身，未验证审计实际数据库持久化或供应商质量。

审计失败可留下部分RULE_CHECK_CAPTURE尝试记录，但不返回证据；捕获不是回答下发，不宣称事务原子性。决策仍需重读；跨片段一致性与最终HTTP下发撤回窗口仍待补证。学生身份/CSRF、提问匹配、真实规则范围/条件/例外审核和报告/用户对照未装配或验收，RC-04-SEMANTICS保持OPEN。

下一步：在真实已审核且资格明确的2025主题上建立稳定条款绑定及规则审核记录，再装配受控v2应用。工程合成测试不能替代这个资料门禁；B-02另需补3条独立报告候选并核对任务家族与评分标准。
''', encoding='utf-8')
artifacts = {p.name: sha(p) for p in batch.iterdir() if p.is_file() and p.name != 'integrity-check.json'}
write(batch / 'integrity-check.json', dict(passed=True, artifacts=artifacts, verifiedFinalRuns=verified,
    original36Sha256=sha(original), sourceWhitelistAfter=after))
print(json.dumps(dict(passed=True, unit=verified[final_unit], database=verified[final_db], contract=29), ensure_ascii=False))
