from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['agent/frontend/src/api/controlled.js','agent/frontend/src/views/CampusRun.vue','agent/frontend/tests/controlled-copy.test.mjs','docs/product/rule-check-blockers.md','docs/product/rule-check-remaining-tasks-2026-10-08.md','docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md','.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert not (batch/'source-before.json').exists();(batch/'before').mkdir();sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();rows=[]
for i,path in enumerate(paths):
 p=root/path;q=batch/'before'/f'{i:02}.bin'
 if p.exists():q.write_bytes(p.read_bytes())
 rows.append(dict(path=path,before=str(q.relative_to(batch)) if p.exists() else None,sha256=sha(p) if p.exists() else None))
protected={}
for base in ['knowledge/src','agent-runtime/src','agent/frontend/src','agent/frontend/tests','docs/eval']:
 for p in (root/base).rglob('*'):
  path=str(p.relative_to(root)).replace('\\','/')
  if p.is_file() and path not in paths:protected[path]=sha(p)
(batch/'source-before.json').write_text(json.dumps(dict(files=rows,unchanged=protected),indent=2)+'\n',encoding='utf-8')
print(f'Legacy copy captured {len(rows)} files / {len(protected)} protected')
