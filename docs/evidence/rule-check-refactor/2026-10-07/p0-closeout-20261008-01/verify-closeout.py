"""验证最终P0证据，并在专用样例目录演练恢复；不修改真实源码、不运行数据库。"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

EVIDENCE = Path(__file__).resolve().parent
ROOT = EVIDENCE.parents[4]
PROGRAM = EVIDENCE.parent
parser = argparse.ArgumentParser()
parser.add_argument('--phase', required=True)
args = parser.parse_args()
assert args.phase.replace('-', '').isalnum() and '/' not in args.phase
BATCH = PROGRAM / args.phase


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


result = read(BATCH / 'results.json')
assert result['passed'] and result['resourcesVerified'] and result['sourceDrift'] == 0
assert not result['missingXml'] and not result['newJavaRemaining'] and not result['providerAllowed']
database = read(BATCH / 'database.json')
assert database['verified'] and database['connectionsAfterTests'] == 0 and result['containerExited']
assert database['target'] == 'jdbc:postgresql://127.0.0.1:55439/zhkuagent_rulecheck_e2e'
for row in read(BATCH / 'source-after.json'):
    assert sha(ROOT / row['path']) == row['sha256'], row['path']
count = 0
for suite in result['suites']:
    path = BATCH / 'xml' / suite['xml']
    assert sha(path) == suite['sha256']
    xml = ET.parse(path).getroot()
    assert int(xml.attrib['tests']) > 0
    assert all(int(xml.attrib.get(key, '0')) == 0 for key in ['failures', 'errors', 'skipped'])
    count += int(xml.attrib['tests'])
assert count == result['testCount']
contract = read(EVIDENCE / 'runner-contract-final.json')
assert all(row['passed'] for row in contract['results'])

# 样例只在明确目录内写入/移除文件，不做递归删除，也不恢复真实工作区。
rehearsal = EVIDENCE / 'restore-rehearsal'
rehearsal.mkdir(exist_ok=False)
assert rehearsal.resolve().is_relative_to(EVIDENCE.resolve())
records = []
for name in ['r1-fusion-01', 'r2-admin-01', 'r3-output-20261008-01', 'r4-read-20261008-01']:
    source = PROGRAM / name
    before = read(source / 'source-before.json')
    for row in before['files']:
        if not row['path'].startswith('knowledge/'):
            continue
        actual = ROOT / row['path']
        original = source / 'before' / row['path']
        sample = rehearsal / name / row['path']
        assert sample.resolve().is_relative_to(rehearsal.resolve())
        sample.parent.mkdir(parents=True, exist_ok=True)
        if actual.exists():
            sample.write_bytes(actual.read_bytes())
        if original.exists():
            assert sha(original) == row['sha256'], f'Invalid original backup: {name} {row["path"]}'
            sample.write_bytes(original.read_bytes())
            assert sha(sample) == row['sha256']
            action = 'RESTORED_ORIGINAL_BYTES'
        else:
            assert row.get('existed', row.get('exists', True)) is False, f'Missing undeclared backup: {name} {row["path"]}'
            if sample.exists():
                sample.unlink()
            action = 'REMOVED_NEW_SAMPLE_FILE_ONLY'
        records.append({'batch': name, 'path': row['path'], 'action': action, 'passed': True})
with (EVIDENCE / 'rollback-rehearsal.json').open('w', encoding='utf-8') as output:
    json.dump({'performedAt': datetime.datetime.now().astimezone().isoformat(), 'passed': True,
               'sampleRoot': rehearsal.relative_to(ROOT).as_posix(), 'realWorktreeRestored': False,
               'recursiveDeleteUsed': False, 'checks': records,
               'entryClosureStrategy': '先停止/关闭受影响入口，按当前哈希审查逐文件增量恢复，再新批次复验；历史台账不回退'}, output, ensure_ascii=False, indent=2)
    output.write('\n')
manifest = {'verifiedAt': datetime.datetime.now().astimezone().isoformat(), 'passed': True,
            'phase': args.phase, 'classes': len(result['suites']), 'tests': count,
            'contractChecks': len(contract['results']), 'sourceDrift': 0, 'resourcesVerified': True,
            'migration': database['migration'], 'connectionsAfterTests': 0, 'containerExited': True,
            'providerRun': False, 'rollbackRehearsalPassed': True, 'rollbackChecks': len(records),
            'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
            'qualityEvaluationRun': False, 'g0Passed': False,
            'sourceHashes': read(BATCH / 'source-after.json')}
(EVIDENCE / 'verification.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'PASS closeout; classes={len(result["suites"])} tests={count} rollbackChecks={len(records)} G0=NOT_PASSED')
