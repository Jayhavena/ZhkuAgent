"""Prepare an isolated review draft; never modify the v2 evaluation resource."""
import copy
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SOURCE = ROOT / 'knowledge/src/test/resources/agent-effect-eval/rule-fact-check-effect-v2-dev.json'
MANIFEST = SOURCE.with_name('manifest-v2-dev.json')
OUT = Path(__file__).resolve().parent


def digest(data):
    return hashlib.sha256(data).hexdigest()


def save_new_or_identical(path, content):
    if path.exists() and path.read_bytes() != content:
        raise FileExistsError(f'Refusing to overwrite edited file: {path}')
    path.write_bytes(content)


def create(output):
    original = SOURCE.read_bytes()
    source_manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    if digest(original) != source_manifest['datasetSha256']:
        raise ValueError('v2 source hash does not match its manifest')
    dataset = copy.deepcopy(json.loads(original))
    dataset['datasetVersion'] = 'v3-review-draft'
    dataset['sourceSnapshot']['caseAuthoringNote'] += ' RFE-017 按用户复核意见调整；其他题的审核记录在审核卡，尚未回写本数据。'
    cases = {c['caseId']: c for c in dataset['cases']}
    c17 = cases['RFE-017']
    optional = [f for f in c17['expectedFacts'] if f['semanticFactId'] == 'schedule-previous-semester']
    if len(optional) != 1 or '说明开课前一个学期排课' not in c17['requiredResponseElements']:
        raise ValueError('RFE-017 source does not match expected v2 baseline')
    c17['expectedFacts'] = [f for f in c17['expectedFacts'] if f['semanticFactId'] != 'schedule-previous-semester']
    c17['optionalContextFacts'] = optional
    c17['requiredResponseElements'].remove('说明开课前一个学期排课')
    dataset['scoringContract']['citationApplicability'] = {
        'scope': '当 requiredEvidence 为空时，引用维度适用性单独判断；其他维度仍照常判分。',
        'noRequiredEvidenceAndNoCitation': 'NOT_APPLICABLE',
        'citationPresent': 'VERIFY_EACH_CITATION',
        'citationVerificationUnavailable': 'UNJUDGEABLE',
        'rule': '无需引用且未提供引用时不要求引用，引用维度为 NOT_APPLICABLE，不计入该维度分母；如提供引用须核对来源及其对所关联主张的支持，错误或虚构引用为 CITATION_MISMATCH；无法核验则为 UNJUDGEABLE，不静默计入 PASS。',
        'casePassPolicy': 'NOT_APPLICABLE 仅表示该维度不适用，不自动判整题通过；其余适用的关键维度按原 passPolicy 评判。'
    }
    c20 = cases['RFE-020']
    if c20['requiredEvidence'] or 'CITATION_MISMATCH' not in c20['criticalFailureTags']:
        raise ValueError('RFE-020 source does not match expected v2 baseline')
    c20['citationReviewNote'] = '无引用时引用维度不适用；如给出错误或虚构引用仍可触发 CITATION_MISMATCH，无法核验则标记 UNJUDGEABLE。'
    name = 'rule-fact-check-effect-v3-review-draft.json'
    payload = (json.dumps(dataset, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    manifest = copy.deepcopy(source_manifest)
    manifest.update(datasetVersion=dataset['datasetVersion'], datasetFile=name, datasetSha256=digest(payload),
                    notes='Isolated review draft based on hash-pinned v2; not activated, not a frozen gold or report set. Citation applicability is a proposed scoring contract, not an implemented evaluator.')
    output.mkdir(parents=True, exist_ok=True)
    save_new_or_identical(output / name, payload)
    save_new_or_identical(output / 'manifest-v3-review-draft.json', (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
    return dataset, manifest


if __name__ == '__main__':
    data, manifest = create(OUT)
    print(f"{manifest['datasetFile']} cases={len(data['cases'])} sha256={manifest['datasetSha256']}")
