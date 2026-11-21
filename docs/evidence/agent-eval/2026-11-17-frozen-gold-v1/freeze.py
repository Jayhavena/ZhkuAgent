"""Freeze reviewed expectations, never model outputs; historical inputs remain untouched."""
import copy
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
OUT = Path(__file__).resolve().parent
SOURCE = ROOT / 'docs/evidence/agent-eval/2026-11-08-v3-review-revision/rule-fact-check-effect-v3-review-draft.json'
CARD = ROOT / 'docs/evidence/agent-eval/2026-11-06-review-cards/审核卡.md'


def digest(payload):
    return hashlib.sha256(payload).hexdigest()


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def save(path, payload):
    if path.exists() and path.read_bytes() != payload:
        raise FileExistsError(f'Refusing to overwrite frozen content: {path.name}')
    if not path.exists():
        path.write_bytes(payload)


def create(output=OUT):
    data = json.loads(SOURCE.read_bytes())
    source_manifest = json.loads(SOURCE.with_name('manifest-v3-review-draft.json').read_bytes())
    if digest(SOURCE.read_bytes()) != source_manifest['datasetSha256']:
        raise ValueError('source manifest mismatch')
    receipt_path = OUT / 'acceptance.json'
    receipt = json.loads(receipt_path.read_bytes())
    if receipt['acceptedCaseIds'] != ['RFE-017', 'RFE-020'] or receipt['answer'] != '接受这两项修订并据此冻结':
        raise ValueError('final acceptance missing')
    sections = dict(re.findall(r'(?ms)^#### (RFE-\d+)：(.*?)(?=^#### |\Z)', CARD.read_text(encoding='utf-8')))
    expected_ids = {f'RFE-{i:03}' for i in range(1, 21)}
    if set(sections) != expected_ids or {c['caseId'] for c in data['cases']} != expected_ids:
        raise ValueError('review card/case identity mismatch')
    records = []
    for case in data['cases']:
        cid = case['caseId']
        section = sections[cid]
        revised = cid in receipt['acceptedCaseIds']
        if not revised and '- [x] 通过' not in section:
            raise ValueError(f'{cid}: no historical approval')
        date = receipt['acceptedDate'] if revised else re.search(r'用户 / (\d{4}-\d{2}-\d{2})', section).group(1)
        records.append(dict(caseId=cid, humanReviewStatus='APPROVED', reviewer='用户', reviewedAt=date,
                            basis='FINAL_REVISION_ACCEPTANCE' if revised else 'HISTORICAL_REVIEW_CARD',
                            historicalCardSha256=digest(CARD.read_bytes()),
                            historicalSectionSha256=digest(section.encode('utf-8'))))
        case['humanReviewStatus'] = 'APPROVED'
        case['humanReview'] = copy.deepcopy(records[-1])
    decisions = dict(schemaVersion=1, scope='EXPECTED_ANSWER_CONTRACT_ONLY', cases=records)
    data.update(datasetVersion='v1.0', datasetStatus='FROZEN', frozenAt=receipt['recordedAt'], reviewer='用户',
                freezeScope='HISTORICAL_2025_09_02_DEVELOPMENT_EXPECTATIONS_ONLY')
    data['sourceSnapshot']['caseAuthoringNote'] = 'AI起草，用户逐题审核；RFE-017/020修订于2026-11-17最终接收。冻结历史快照开发预期，不含运行成绩，不是独立留出集。'
    eligibility = {}
    for case in data['cases']:
        eligibility[case['caseId']] = {
            'factSupport': 'APPLICABLE' if case['expectedFacts'] else 'NOT_APPLICABLE',
            'citationIntegrity': 'REQUIRED' if case['requiredEvidence'] else 'CONDITIONAL_ON_OBSERVED_CITATIONS',
        }
    data['dimensionApplicability'] = eligibility
    data['scoringContract']['scoreLimit'] = '冻结预期，不含实际Agent输出；任何质量分数均需独立执行和完整核验。'
    card = ['# Frozen v1.0 审核决定卡', '',
            '本卡为新版本最终审核决定；历史审核卡及v2/v3保持原样。APPROVED仅指预期答案契约。', '',
            '| caseId | humanReviewStatus | reviewer | reviewedAt | basis |', '|---|---|---|---|---|']
    card += [f"| {r['caseId']} | {r['humanReviewStatus']} | {r['reviewer']} | {r['reviewedAt']} | {r['basis']} |" for r in records]
    payloads = {'rule-fact-check-effect-v1.0.json': encode(data), 'review-decisions.json': encode(decisions),
                '审核决定卡.md': ('\n'.join(card) + '\n').encode('utf-8')}
    sources = [SOURCE, SOURCE.with_name('manifest-v3-review-draft.json'), CARD, receipt_path]
    for artifact in data['sourceSnapshot']['sourceArtifacts']:
        p = ROOT / artifact['path']
        if digest(p.read_bytes()) != artifact['sha256']:
            raise ValueError(f'source artifact drift: {p.name}')
        sources.append(p)
    manifest = dict(manifestVersion='v1', datasetId=data['datasetId'], datasetVersion='v1.0',
                    datasetFile='rule-fact-check-effect-v1.0.json', datasetSha256=digest(payloads['rule-fact-check-effect-v1.0.json']),
                    caseCount=20, datasetStatus='FROZEN', frozenAt=data['frozenAt'], reviewer=data['reviewer'],
                    freezeScope=data['freezeScope'], corpusFingerprint=data['corpusFingerprint'],
                    files={name: digest(value) for name, value in payloads.items()},
                    sources={p.relative_to(ROOT).as_posix(): digest(p.read_bytes()) for p in sources},
                    notes='Local hash-pinned development expectations; not WORM, runtime activation, current policy approval or model quality.')
    output.mkdir(parents=True, exist_ok=True)
    payloads['manifest.json'] = encode(manifest)
    for name, payload in payloads.items():
        if (output / name).exists() and (output / name).read_bytes() != payload:
            raise FileExistsError(f'Refusing to overwrite: {name}')
    for name, payload in payloads.items():
        save(output / name, payload)
    return manifest


if __name__ == '__main__':
    m = create()
    print(f"FROZEN cases={m['caseCount']} sha256={m['datasetSha256']}")
