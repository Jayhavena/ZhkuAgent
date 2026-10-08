from pathlib import Path
import hashlib, json
root=Path(__file__).resolve().parents[5]
batch=Path(__file__).resolve().parent
paths=[
 'knowledge/src/main/java/com/zhku/knowledge/rag/CitationAuditRecord.java',
 'knowledge/src/main/java/com/zhku/knowledge/rag/JdbcCitationAuditRepository.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceCapture.java',
 'knowledge/src/main/java/com/zhku/knowledge/rulecheck/JdbcRuleCheckCitationAuditRepository.java',
 'knowledge/src/main/resources/db/migration/V22__allow_rule_check_capture_audit.sql',
 'knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceCaptureTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcRuleCheckCitationAuditTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcRuleCheckCitationAuditTargetTest.java',
 'knowledge/scripts/verify-rule-check-refactor.ps1',
 'docs/product/rule-check-decision-contract.md','docs/product/rule-check-blockers.md',
 'docs/product/rule-check-review-handoff.md',
 'docs/evidence/rule-check-refactor/2026-10-07/issues.json',
 'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md',
 '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert not (batch/'source-before.json').exists()
(batch/'before').mkdir()
rows=[]
for i,path in enumerate(paths):
 p=root/path; data=p.read_bytes() if p.exists() else None
 saved=f'before/{i:02d}.bin' if data is not None else None
 if saved: (batch/saved).write_bytes(data)
 rows.append(dict(path=path,existed=data is not None,before=saved,sha256=hashlib.sha256(data).hexdigest() if data is not None else None))
fixed=['docs/product/rule-check-2025-rule-template-draft.json','docs/product/rule-check-2025-human-reference-development.json',
 'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json']
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in fixed}),indent=2),encoding='utf-8')
print('15条白名单和3份资料基线已保存')
