from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['agent/frontend/src/views/RuleCheckView.vue','agent/frontend/src/components/RuleCheckFact.vue','agent/frontend/src/router/index.js','agent/frontend/src/views/Home.vue','agent/frontend/tests/rule-check-page.test.mjs','docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md','.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md','docs/product/rule-check-remaining-tasks-2026-10-08.md','docs/product/rule-check-blockers.md','docs/product/rule-check-decision-contract.md']
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not (batch/'source-before.json').exists();(batch/'before').mkdir(exist_ok=True)
rows=[]
for i,path in enumerate(paths):
 p=root/path;old=f'before/{i:02}.bin' if p.exists() else None
 if old:(batch/old).write_bytes(p.read_bytes())
 rows.append(dict(path=path,before=old,existed=p.exists(),sha256=sha(p) if p.exists() else None))
protected={str(p.relative_to(root)).replace('\\','/'):sha(p) for base in ['knowledge/src/main','agent/frontend/src','agent/frontend/tests'] for p in (root/base).rglob('*') if p.is_file() and str(p.relative_to(root)).replace('\\','/') not in paths}
for path in ['docs/product/rule-check-2025-rule-template-draft.json','docs/product/rule-check-2025-human-reference-development.json','docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json']:
 protected[path]=sha(root/path)
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged=protected),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Captured student page 10-file whitelist; API/Java/v1 protected')
