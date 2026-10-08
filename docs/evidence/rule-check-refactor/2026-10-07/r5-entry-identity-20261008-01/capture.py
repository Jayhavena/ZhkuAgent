from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['knowledge/src/main/java/com/zhku/knowledge/security/PreviewSecurityConfiguration.java','knowledge/src/test/java/com/zhku/knowledge/retrieval/RuleCheckOriginalHttpTest.java','knowledge/src/test/java/com/zhku/knowledge/retrieval/RuleCheckPreviewHttpTest.java','knowledge/src/test/java/com/zhku/knowledge/security/RuleCheckPreviewSecurityConfigurationTest.java','docs/product/rule-check-decision-contract.md','docs/product/rule-check-blockers.md','docs/product/rule-check-remaining-tasks-2026-10-08.md','docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md','.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert not (batch/'source-before.json').exists();(batch/'before').mkdir()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();rows=[]
for i,path in enumerate(paths):
 p=root/path;q=batch/'before'/f'{i:02}.bin';q.write_bytes(p.read_bytes());rows.append(dict(path=path,before=str(q.relative_to(batch)),sha256=sha(p)))
protected={}
for base in ['knowledge/src','agent-runtime/src','agent/frontend/src','agent/frontend/tests','docs/eval']:
 for p in (root/base).rglob('*'):
  if p.is_file() and str(p.relative_to(root)).replace('\\','/') not in paths:protected[str(p.relative_to(root)).replace('\\','/')]=sha(p)
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged=protected),indent=2)+'\n',encoding='utf-8')
print(f'Captured {len(rows)} whitelist / {len(protected)} protected')
