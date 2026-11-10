"""校验最小证据交付的完整性；不重新评分，也不调用任何外部服务。"""
import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
PREFIX = 'docs/evidence/g3-g4-closeout-20261110/frozen/'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-root', type=Path)
    args = parser.parse_args()
    listing = load(ROOT / 'delivery-manifest.json')
    files = {p.relative_to(ROOT).as_posix(): p for p in ROOT.rglob('*') if p.is_file()}
    require(set(files) == set(listing) | {'delivery-manifest.json'}, 'Unexpected/missing delivery files')
    for name, entry in listing.items():
        raw = files[name].read_bytes()
        require(len(raw) == entry['bytes'] and digest(raw) == entry['sha256'], 'Delivery digest mismatch: ' + name)
    review = load(ROOT / 'source-review-72.json')
    rows = review['files']
    require(len(rows) == 72 and len({r['sourcePath'] for r in rows}) == 72, '72 unique review entries required')
    counts = dict(collections.Counter(r['decision'] for r in rows))
    require(counts == review['counts'] == {'INCLUDE_EXACT': 8, 'DERIVE_ALLOWLIST': 3, 'DERIVE_IDENTITY': 9, 'EXCLUDE': 52}, 'Review counts differ')
    for row in rows:
        require(re.fullmatch('[a-f0-9]{64}', row['sha256']) is not None, 'Invalid source digest')
        require(row['owner'] and row['reason'], 'Missing ownership/reason')
        if row['decision'] == 'INCLUDE_EXACT':
            require(digest((ROOT / row['target']).read_bytes()) == row['sha256'], 'Exact copy differs')
        if row['decision'] == 'EXCLUDE':
            require(row['target'] is None, 'Excluded file has delivery target')
    forbidden_keys = {'body', 'systemPrompt', 'reviewSystemPrompt', 'maximumReviewedExcerpts', 'maximumReviewedRuleSemantics', 'article4ApprovedFacts', 'excerpt', 'contextText', 'message', 'nextStep', 'password', 'apiKey', 'continuationToken'}

    def inspect(value):
        if isinstance(value, dict):
            require(not forbidden_keys.intersection(value), 'Forbidden payload field')
            for item in value.values():
                inspect(item)
        elif isinstance(value, list):
            for item in value:
                inspect(item)

    # 只输出匹配计数/文件名，绝不把可能的秘密值写入校验报告。
    secrets = [os.environ.get(n) for n in ['ZHKU_DB_PASSWORD', 'DASHSCOPE_API_KEY']]
    if os.name == 'nt':
        import winreg
        for hive, subkey in [(winreg.HKEY_CURRENT_USER, 'Environment'), (winreg.HKEY_LOCAL_MACHINE, r'SYSTEM\CurrentControlSet\Control\Session Manager\Environment')]:
            try:
                with winreg.OpenKey(hive, subkey) as key:
                    for name in ['ZHKU_DB_PASSWORD', 'DASHSCOPE_API_KEY']:
                        try:
                            secrets.append(winreg.QueryValueEx(key, name)[0])
                        except FileNotFoundError:
                            pass
            except FileNotFoundError:
                pass
    for name, path in files.items():
        text = path.read_text(encoding='utf-8-sig')
        require(not re.search(r'[A-Za-z]:[\\/]', text), 'Absolute host path: ' + name)
        require(not re.search(r'(?<![A-Za-z0-9])sk-[A-Za-z0-9]{16,}', text), 'Credential-like value: ' + name)
        require(not any(secret and secret in text for secret in secrets), 'Configured secret match: ' + name)
        if path.suffix == '.json':
            inspect(load(path))
    report = load(ROOT / 'report.json')
    require((report['passed'], report['failed'], report['notEvaluable'], report['cases']) == (2, 0, 0, 2), 'Original verdict changed')
    suite = load(ROOT / 'eval-suite.json')
    fixtures = [load(p) for p in sorted((ROOT / 'collected').glob('fixture-*.json'))]
    require(len(fixtures) == 2, 'Expected two white-list fixtures')
    for case in suite['blocks'][0]['cases']:
        fixture = next(f for f in fixtures if f['runId'] == case['runId'])
        expected = case['expectation']
        for key in ['taskStatus', 'runState', 'coverage']:
            require(fixture['terminal'][key] == expected[key], 'Fixture/expectation mismatch')
    usage = load(ROOT / 'usage-summary.json')
    require((usage['calls'], usage['inputTokens'], usage['outputTokens'], usage['totalTokens']) == (8, 14617, 1026, 15643), 'Usage changed')
    require(usage['actualInvoiceCny'] is None and usage['withinApprovedCnyLimit'] is None and usage['settlementStatus'] == 'NOT_VERIFIED', 'Invoice boundary changed')
    require(load(ROOT / 'approval-state.json')['remainingAuthorizedCalls'] == 0, 'Closed allowance changed')
    source_checked = 0
    if args.source_root:
        original_manifest = args.source_root / 'docs/evidence/g3-g4-closeout-20261110/freeze-manifest.json'
        require(digest(original_manifest.read_bytes()) == review['sourceFreezeManifestSha256'], 'Source freeze identity differs')
        originals = load(original_manifest)
        require({e['path'][len(PREFIX):] for e in originals} == {r['sourcePath'] for r in rows}, 'Source review scope differs')
        for row in rows:
            raw = (args.source_root / (PREFIX + row['sourcePath'])).read_bytes()
            require(digest(raw) == row['sha256'] and len(raw) == row['bytes'], 'Source file drift')
            source_checked += 1
        package = load(args.source_root / (PREFIX + 'authorization-and-evaluation/request-package.json'))
        for entry in load(ROOT / 'grader-identity.json')['sources']:
            require(package['buildIdentity']['sourceSha256'][entry['path']] == entry['sha256'], 'Grader identity differs from approved package')
        public_text = '\n'.join(p.read_text(encoding='utf-8-sig') for p in files.values())
        for excerpt in package['maximumReviewedExcerpts']:
            text = excerpt['content']
            require(len(text) < 20 or text not in public_text, 'Reviewed source text leaked into delivery')
    print(json.dumps({'passed': True, 'deliveryFiles': len(files), 'reviewedSources': 72, 'sourceFilesVerified': source_checked, 'unchangedReports': True, 'secretMatches': 0, 'callsMade': 0, 'evaluationRerun': False}, ensure_ascii=False))


if __name__ == '__main__':
    main()
