"""Bind independent review decisions to the actual repair run. Offline only."""
import hashlib
import json
import sys
from pathlib import Path

live = Path(sys.argv[1]).resolve()
root = next(p for p in live.parents if (p / 'knowledge/pom.xml').is_file())
reviewer = 'Codex 独立离线复核，非用户人工终审'

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def emit(path, value):
    with path.open('x', encoding='utf-8') as out:
        json.dump(value, out, ensure_ascii=False, indent=2)
        out.write('\n')

def digest(value):
    return hashlib.sha256(value).hexdigest()

source = root / 'docs/product/rule-check-2025-verbatim-import-draft.json'
chunks = {c['contentChecksum']: c for c in read(source)['chunks']}
decisions = read(Path(sys.argv[2]))
captures = sorted(live.glob('RFE-*/*/capture.json'))
assert set(decisions) == {p.parent.relative_to(live).as_posix() for p in captures}
outputs = []
for path in captures:
    key = path.parent.relative_to(live).as_posix()
    data = read(path)
    answer = data['execution']['answer']
    pub = data['publishedResult']
    capture_sha = digest(path.read_bytes())
    pending = read(path.parent / 'annotation-pending.json')
    assert pending['captureSha256'] == capture_sha and pending['answerSha256'] == digest(answer.encode())
    edges = pub['evidenceRelations']
    citations = {}
    checks = []
    decision = decisions[key]
    assert set(decision['citations']) == {e['id'] for e in pub['evidence']}
    for e in pub['evidence']:
        chunk = chunks.get(e['checksum'])
        valid = False
        if chunk:
            raw = chunk['content'].encode('utf-16-le')
            start, end = e['startInclusive'], e['endExclusive']
            valid = (0 <= start < end <= len(raw)//2 and digest(chunk['content'].encode()) == e['checksum']
                     and raw[start*2:end*2].decode('utf-16-le') == e['quote'] and e['revision'] == '2025.9.2')
        citations[e['id']] = decision['citations'][e['id']] if valid else 'MISMATCH'
        checks.append({'evidenceId': e['id'], 'checksum': e['checksum'], 'revision':e['revision'],
                       'article': chunk['article'] if chunk else None, 'quoteMatchesSource': valid,
                       'relations': [r for r in edges if r['evidenceId'] == e['id']],
                       'supportReview': citations[e['id']]})
    reviews = {name: {'verification': verdict, 'reason': reason, 'reviewer': reviewer, 'answerExcerpt': answer}
               for name, (verdict, reason) in decision['reviews'].items()}
    outputs.extend([(path.parent/'annotation-reviewed.json', {'captureSha256':capture_sha,
                    'answerSha256': pending['answerSha256'], 'factsComplete':True, 'matchedFacts':decision['facts'],
                    'citationsComplete':True, 'citationReviews':citations, 'reviews':reviews}),
                    (path.parent/'citation-checks.json', {'sourceSha256':digest(source.read_bytes()), 'checks':checks})])
for path, value in outputs:
    emit(path, value)
print(f'Bound {len(captures)} independent reviews and verified citation spans; providerCalls=0')
