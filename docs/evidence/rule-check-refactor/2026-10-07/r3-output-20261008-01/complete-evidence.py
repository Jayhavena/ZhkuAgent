"""仅整理已产生的R3证据与台账，不运行测试、数据库或模型。"""
from pathlib import Path
import datetime
import difflib
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[5]
PROGRAM = ROOT / 'docs/evidence/rule-check-refactor/2026-10-07'
EVIDENCE = Path(__file__).resolve().parent


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def save_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def replace_text(path, old, new):
    original = path.read_text(encoding='utf-8')
    assert original.count(old) == 1, f'Unexpected document state: {path}'
    path.write_text(original.replace(old, new), encoding='utf-8')


baseline = read_json(EVIDENCE / 'source-before.json')
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == baseline['head']
batches = {}
for phase in ['r3-20261008-red-01', 'r3-20261008-red-02', 'r3-20261008-green-01', 'r3-20261008-regression-01']:
    result = read_json(PROGRAM / phase / 'results.json')
    assert result['sourceDrift'] == 0 and result['resourcesVerified']
    assert not result['database'] and not result['providerAllowed']
    batches[phase] = result
assert batches['r3-20261008-red-01']['testCount'] is None
assert batches['r3-20261008-red-02']['testCount'] == 46
assert sum(int(s['failures']) for s in batches['r3-20261008-red-02']['suites']) == 17
assert not batches['r3-20261008-red-02']['passed']
assert batches['r3-20261008-green-01']['passed'] and batches['r3-20261008-green-01']['testCount'] == 46
final = batches['r3-20261008-regression-01']
assert final['passed'] and final['testCount'] == 96 and len(final['suites']) == 11 and not final['missingXml']
for suite in final['suites']:
    xml = ET.parse(PROGRAM / final['phase'] / 'xml' / suite['xml']).getroot()
    assert all(int(xml.attrib.get(key, '0')) == 0 for key in ['failures', 'errors', 'skipped'])

probe = read_json(EVIDENCE / 'semantic-gap.json')
assert probe['expectedRejected'] and not probe['actualRejected'] and probe['composerStatus'] == 'ANSWERED'
now = datetime.datetime.now().astimezone().isoformat()

# 本目录README尚未改动，追加其原始字节备份，再同步证据入口。
readme_rel = 'docs/evidence/rule-check-refactor/2026-10-07/README.md'
readme = ROOT / readme_rel
copy = EVIDENCE / 'before' / readme_rel
copy.parent.mkdir(parents=True, exist_ok=True)
copy.write_bytes(readme.read_bytes())
baseline['files'].append({'path': readme_rel, 'existed': True, 'sha256': digest(readme), 'bytes': readme.stat().st_size})
baseline['additionalSnapshotAt'] = now
save_json(EVIDENCE / 'source-before.json', baseline)

scope = ROOT / 'docs/product/rule-check-scope.md'
replace_text(scope, '版本：1.3；日期：2026-10-07。', '版本：1.4；日期：2026-10-08。')
replace_text(scope, 'R1融合截断、R2管理员原文对照已修复复验；', 'R1融合截断、R2管理员原文对照、R3输出结构与非空引用已修复复验；一般事实支持仍未完成；')

p0 = ROOT / 'docs/superpowers/plans/2026-10-07-rule-check-p0-plan.md'
replace_text(p0, 'Task 4–6未开始。', 'Task 4结构工程验证完成，事实支持仍开放；Task 5–6未开始。2026-10-08执行R3。')
replace_text(p0, '[R2验收](../../evidence/rule-check-refactor/2026-10-07/r2-admin-01/acceptance.md)。', '[R2验收](../../evidence/rule-check-refactor/2026-10-07/r2-admin-01/acceptance.md)、[R3验收](../../evidence/rule-check-refactor/2026-10-07/r3-output-20261008-01/acceptance.md)。')
replace_text(p0, '新增同包测试 `ModelOutputParserTest.java`、`AnswerEvidenceGuardTest.java` |', '新增同包测试 `ModelOutputParserTest.java`、`AnswerEvidenceGuardTest.java`；扩展 `GroundedAnswerComposerTest.java`、`SpringAiAdapterContractTest.java` 与入口白名单（仅本机HTTP桩） |')
start = p0.read_text(encoding='utf-8')
prefix, section = start.split('## Task 4：结构、范围与空引用守卫', 1)
task4, suffix = section.split('## Task 5：在线profile与合法候选读取', 1)
assert task4.count('- [ ] **Step') == 6
task4 = task4.replace('- [ ] **Step', '- [x] **Step')
task4 += '\n**实际：**r3-red-01因测试访问私有常量编译失败，不算行为RED；r3-red-02四类46项/17断言失败。GREEN46项与相关回归11类96项通过，入口合同29项通过。ModelResponse保持不变；合成4→40探针仍ANSWERED，RC-04结构CLOSED、语义OPEN、父项PARTIAL。真实供应商/质量/用户收益未测，G0未通过。\n\n'
p0.write_text(prefix + '## Task 4：结构、范围与空引用守卫' + task4 + '## Task 5：在线profile与合法候选读取' + suffix, encoding='utf-8')

roadmap = ROOT / 'docs/superpowers/plans/2026-10-07-rule-check-refactor-roadmap.md'
replace_text(roadmap, 'R1融合截断、R2管理原文对照均已修复复验，预算调优和产品收益未实测', 'R1融合截断、R2管理原文对照、R3结构及非空引用均已修复复验（R3执行2026-10-08）；事实支持、预算调优和产品收益未实测')
text = roadmap.read_text(encoding='utf-8')
prefix, section = text.split('### R3 / P0：严格输出结构与非空证据', 1)
r3, suffix = section.split('### R4 / P0：激活状态和已知资料资格落实到读取', 1)
assert r3.count('- [ ]') == 4
r3 = r3.replace('- [ ]', '- [x]')
r3 += '\n**实际验收：**[R3证据](../../evidence/rule-check-refactor/2026-10-07/r3-output-20261008-01/acceptance.md)，46项定向与96项相关回归通过；4→40仍放行，RC-04仅结构子项关闭。G0未通过，R4未开始。\n\n'
roadmap.write_text(prefix + '### R3 / P0：严格输出结构与非空证据' + r3 + '### R4 / P0：激活状态和已知资料资格落实到读取' + suffix, encoding='utf-8')

replace_text(readme, '日期：2026-10-07。当前交付阶段：**R2本机管理原文对照工程与真实浏览器技术验证，R0资料/评估准备部分完成**。', '更新日期：2026-10-08；目录日期是计划开始日期。当前交付阶段：**R3输出结构与非空引用工程验证；一般事实支持未完成，R0资料/评估准备部分完成**。')
replace_text(readme, '[R2验收](r2-admin-01/acceptance.md)及独立批次；', '[R2验收](r2-admin-01/acceptance.md)、[R3验收](r3-output-20261008-01/acceptance.md)及独立批次；')
replace_text(readme, 'RC-02/03已修复复验，没有真实模型评估或用户收益数据', 'RC-02/03及RC-04结构子项已修复复验，没有真实模型评估或用户收益数据')
replace_text(readme, 'RC-02/03已CLOSED，RC-09/10部分推进，其余仍OPEN。', 'RC-02/03已CLOSED，RC-04为PARTIAL（结构CLOSED、语义OPEN），RC-09/10部分推进，其余仍OPEN。')
replace_text(readme, '| RC-04 输出协议与事实支撑不足 | R3/R5 | 静态确认，OPEN | 结构和事实两个子项分开；严格字段/空证据，以及数字/对象/例外反例 |', '| RC-04 输出协议与事实支撑不足 | R3/R5 | PARTIAL；结构CLOSED，语义OPEN | R3行为RED与96项回归；合成4→40仍放行，R5继续事实/条件/例外验证 |')

issues_path = PROGRAM / 'issues.json'
issues = read_json(issues_path)
previous_issues = read_json(EVIDENCE / 'before' / 'docs/evidence/rule-check-refactor/2026-10-07/issues.json')
assert issues['latestExecution']['stage'] == 'R2_VERIFIED_LOCAL_SYNTHETIC'
issues.setdefault('executionHistory', []).append(issues['latestExecution'])
issues['deliveryStage'] = 'OUTPUT_STRUCTURE_TECHNICAL_VALIDATION'
issues['latestExecution'] = {
    'stage': 'R3_VERIFIED_STRUCTURE_SEMANTICS_OPEN', 'executedAt': now,
    'evidencePath': EVIDENCE.relative_to(ROOT).as_posix() + '/acceptance.md',
    'testsExecuted': True, 'businessCodeChanged': True, 'testCount': 96,
    'browserRealChainRun': False, 'databaseOperationRun': False, 'pdfIngestionRun': False,
    'providerCalled': False, 'productEvaluationRun': False,
    'g0': 'NOT_PASSED_SOURCE_AND_HUMAN_REFERENCE_PENDING',
    'budgetCalibration': 'NOT_RUN_HUMAN_REFERENCE_NOT_LOCKED',
    'semanticKnownRisk': '4_TO_40_WITH_VALID_CITATION_STILL_ANSWERED',
    'planningTurnFieldsAreHistorical': True,
}
rc04 = next(issue for issue in issues['issues'] if issue['id'] == 'RC-04')
rc04.update(status='PARTIAL', verificationStatus='STRUCTURE_PASSED_SEMANTICS_OPEN', evidenceKind='UNIT_TEST_AND_SYNTHETIC_KNOWN_RISK_PROBE')
structure, semantics = rc04['subissues']
structure.update(status='CLOSED', verificationStatus='PASSED', closedAt=now,
                 evidencePath=issues['latestExecution']['evidencePath'],
                 closureScope='仅字段类型、正文与引用非空、有限分值、引用白名单和受控降级；不证明一般事实支持')
code_paths = [row['path'] for row in baseline['files'] if row['path'].startswith('knowledge/')]
structure['verifiedSourceHashes'] = {rel: digest(ROOT / rel) for rel in code_paths}
semantics.update(status='OPEN', verificationStatus='KNOWN_RISK_REPRODUCED', manualEvaluationStatus='NOT_RUN',
                 knownRiskEvidencePath=EVIDENCE.relative_to(ROOT).as_posix() + '/semantic-gap.json')
for prior, current in zip(previous_issues['issues'], issues['issues']):
    assert prior['id'] == current['id']
    if prior['id'] != 'RC-04':
        assert prior == current, f'Unrelated issue changed: {prior["id"]}'
save_json(issues_path, issues)

ledger = ROOT / '.superpowers/sdd/2026-10-07-rule-check-p0-plan/progress.md'
with ledger.open('a', encoding='utf-8') as output:
    output.write('\nTask 4: 首次r3-20261008-red-01新增测试访问私有DECLINE_MESSAGE导致编译失败，无测试执行；修为公开输出合同断言，不扩大常量可见性。新批次red-02实际46项/17断言失败，无错误/跳过，构成真实行为RED。\n')
    output.write('\nTask 4: GREEN四类46项通过；最终相关回归11类96项通过，入口合同29项通过，无源码漂移/Java残留，不运行数据库/真实供应商。守卫同时覆盖直接构造，ModelResponse/POM/系统提示/学生协议未改。\n')
    output.write('\nTask 4: 合成语义探针：原文4、回答40、合法引用；guard未拒绝且composer返回ANSWERED，qualityGatePassed=false。仅保留已知风险，不写成应当放行的回归，不算真实质量分。RC-04-STRUCTURE CLOSED，SEMANTICS OPEN，父项PARTIAL。\n')
    output.write('\nTask 4: VERIFIED_STRUCTURE_SEMANTICS_OPEN — 本批Steps1–6完成；G0未通过，R1预算校准仍NOT_RUN，R4未开始。按增量自审类型、分值、空引用、调用链与证据完整性，不代表完整P0最终独立审查，不提交/部署或清理其他dirty。\n')

save_json(EVIDENCE / 'metrics.json', {
    'executionDate': '2026-10-08', 'evidenceNamespaceDate': '2026-10-07',
    'measurementKind': 'ENGINEERING_CONTRACT_NOT_PRODUCT_EVAL',
    'compileFailureBatch': 'r3-20261008-red-01',
    'behaviorRed': {'batch': 'r3-20261008-red-02', 'tests': 46, 'failures': 17, 'errors': 0, 'skipped': 0},
    'targetedGreen': {'batch': 'r3-20261008-green-01', 'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0},
    'relatedRegression': {'batch': final['phase'], 'classes': 11, 'tests': 96, 'failures': 0, 'errors': 0, 'skipped': 0, 'suites': final['suites']},
    'runnerContract': {'checks': 29, 'evidence': 'runner-contract-final.json'},
    'countersAreJUnitTestMethods': True,
    'sourceDrift': final['sourceDrift'], 'resourcesVerified': final['resourcesVerified'],
    'databaseRun': False, 'realProviderRun': False, 'semanticProbe': probe,
    'qualitySetScored': False, 'graderCalibrationRun': False, 'userBenefitMeasured': False,
    'g0': 'NOT_PASSED', 'r1BudgetCalibration': 'NOT_RUN',
})

diff = []
after = []
for row in baseline['files']:
    path = ROOT / row['path']
    current = path.read_bytes()
    prior = (EVIDENCE / 'before' / row['path']).read_bytes() if row['existed'] else b''
    after.append({'path': row['path'], 'sha256': digest(path), 'bytes': len(current), 'changed': current != prior})
    if current != prior:
        diff.extend(difflib.unified_diff(prior.decode('utf-8').splitlines(keepends=True), current.decode('utf-8').splitlines(keepends=True),
                                         fromfile='before/' + row['path'] if row['existed'] else '/dev/null', tofile='after/' + row['path']))
(EVIDENCE / 'changes.diff').write_text(''.join(diff), encoding='utf-8')
save_json(EVIDENCE / 'source-after.json', {'head': baseline['head'], 'recordedAt': now, 'files': after, 'testBatch': final['phase']})
save_json(EVIDENCE / 'execution-manifest.json', {
    'evidencePath': EVIDENCE.relative_to(ROOT).as_posix(), 'executedAt': now,
    'historicalEvidencePreserved': True, 'originalDirtyWorktreePreserved': True,
    'headUnchanged': True, 'commitCreated': False, 'tests': list(batches),
    'frameworkDocs': {'context7Attempted': True, 'context7VersionMismatch': '3.x returned; not used',
                      'fallback': 'https://github.com/FasterXML/jackson-databind/blob/jackson-databind-2.18.3/src/main/java/com/fasterxml/jackson/databind/JsonNode.java'},
    'scope': 'R3 structure only', 'databaseOperationRun': False, 'realProviderRun': False,
})
print('Evidence and issue ledger updated; regression=96, semantic gap remains OPEN.')
