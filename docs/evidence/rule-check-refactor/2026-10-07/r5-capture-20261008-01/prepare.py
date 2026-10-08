from pathlib import Path
import hashlib, json

root = Path(__file__).resolve().parents[5]
batch = Path(__file__).resolve().parent
paths = [
    'knowledge/src/main/java/com/zhku/knowledge/retrieval/RetrievedChunk.java',
    'knowledge/src/main/java/com/zhku/knowledge/retrieval/JdbcLexicalCandidateRetriever.java',
    'knowledge/src/main/java/com/zhku/knowledge/retrieval/JdbcVectorCandidateRetriever.java',
    'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceCapture.java',
    'knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceCaptureTest.java',
    'knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcCandidateRetrieverTest.java',
    'knowledge/scripts/verify-rule-check-refactor.ps1',
    'docs/product/rule-check-decision-contract.md',
    'docs/product/rule-check-blockers.md',
    'docs/evidence/rule-check-refactor/2026-10-07/issues.json',
    'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md',
    '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md',
]
assert not (batch / 'source-before.json').exists(), '不得覆盖基线'
before = []
for i, relative in enumerate(paths):
    p = root / relative
    data = p.read_bytes() if p.exists() else None
    saved = f'before/{i:02d}.bin' if data is not None else None
    if saved:
        (batch / 'before').mkdir(exist_ok=True)
        (batch / saved).write_bytes(data)
    before.append(dict(path=relative, existed=data is not None, before=saved,
                       sha256=hashlib.sha256(data).hexdigest() if data is not None else None))
original = root / 'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json'
(batch / 'source-before.json').write_text(json.dumps(dict(files=before,
    original36Sha256=hashlib.sha256(original.read_bytes()).hexdigest()), indent=2), encoding='utf-8')
print('保存12条白名单基线；原36题指纹已留存')
