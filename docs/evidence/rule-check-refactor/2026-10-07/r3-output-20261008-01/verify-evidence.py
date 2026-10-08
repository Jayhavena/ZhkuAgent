"""核对R3验收的原始备份、最终源码、实际XML和台账，输出完整性清单。"""
from pathlib import Path
import datetime
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

EVIDENCE = Path(__file__).resolve().parent
ROOT = EVIDENCE.parents[4]
PROGRAM = EVIDENCE.parent


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


before = read(EVIDENCE / 'source-before.json')
after = read(EVIDENCE / 'source-after.json')
assert before['head'] == after['head'] == subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
for row in before['files']:
    if row['existed']:
        assert sha(EVIDENCE / 'before' / row['path']) == row['sha256'], row['path']
for row in after['files']:
    assert sha(ROOT / row['path']) == row['sha256'], row['path']
unchanged = {row['path'] for row in after['files'] if not row['changed']}
assert 'knowledge/src/main/java/com/zhku/knowledge/rag/ModelResponse.java' in unchanged
assert 'scripts/tests/verify-rule-check-refactor-contract.ps1' in unchanged

batch = PROGRAM / after['testBatch']
for row in read(batch / 'source-after.json'):
    assert sha(ROOT / row['path']) == row['sha256'], 'Current tree differs from tested tree: ' + row['path']
result = read(batch / 'results.json')
assert result['passed'] and result['resourcesVerified'] and result['sourceDrift'] == 0
assert not result['database'] and not result['providerAllowed'] and not result['newJavaRemaining']
tests = failures = errors = skipped = 0
for path in (batch / 'xml').glob('TEST-*.xml'):
    suite = ET.parse(path).getroot()
    tests += int(suite.attrib['tests'])
    failures += int(suite.attrib.get('failures', 0))
    errors += int(suite.attrib.get('errors', 0))
    skipped += int(suite.attrib.get('skipped', 0))
assert tests == 96 and failures == errors == skipped == 0
contracts = read(EVIDENCE / 'runner-contract-final.json')['results']
assert len(contracts) == 29 and all(row['passed'] for row in contracts)

issues = read(PROGRAM / 'issues.json')
rc04 = next(row for row in issues['issues'] if row['id'] == 'RC-04')
assert rc04['status'] == 'PARTIAL'
assert rc04['subissues'][0]['status'] == 'CLOSED'
assert rc04['subissues'][1]['status'] == 'OPEN'
assert issues['latestExecution']['g0'].startswith('NOT_PASSED')
assert not issues['latestExecution']['productEvaluationRun']
for rel, expected in rc04['subissues'][0]['verifiedSourceHashes'].items():
    assert sha(ROOT / rel) == expected
probe = read(EVIDENCE / 'semantic-gap.json')
assert not probe['qualityGatePassed'] and not probe['actualRejected']

# 逐一核对本批材料及四个测试批次；清单不自包含，避免循环哈希。
artifacts = []
for directory in [EVIDENCE] + [PROGRAM / name for name in read(EVIDENCE / 'execution-manifest.json')['tests']]:
    for path in sorted(directory.rglob('*')):
        if path.is_file() and path != EVIDENCE / 'integrity.json':
            artifacts.append({'path': path.relative_to(ROOT).as_posix(), 'sha256': sha(path), 'bytes': path.stat().st_size})
report = {
    'verifiedAt': datetime.datetime.now().astimezone().isoformat(), 'passed': True,
    'originalByteBackupsVerified': sum(row['existed'] for row in before['files']),
    'finalManifestFilesVerified': len(after['files']),
    'currentTreeMatchesFinalTestBatch': True, 'headUnchanged': True,
    'regressionTests': tests, 'regressionFailures': failures, 'regressionErrors': errors, 'regressionSkipped': skipped,
    'contractChecks': len(contracts), 'resourcesVerified': result['resourcesVerified'],
    'structureIssueClosed': True, 'semanticIssueOpen': True, 'g0Passed': False,
    'productQualityGatePassed': False, 'artifactCount': len(artifacts), 'artifacts': artifacts,
}
(EVIDENCE / 'integrity.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'PASS integrity; regression={tests}; contracts={len(contracts)}; artifacts={len(artifacts)}; semantics=OPEN; G0=NOT_PASSED')
