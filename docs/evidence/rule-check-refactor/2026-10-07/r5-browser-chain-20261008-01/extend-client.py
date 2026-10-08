from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
paths=['agent/frontend/src/api/rule-check.js','agent/frontend/tests/rule-check-api.test.mjs','agent/frontend/tests/fixtures/rule-check-answer-http.json']
assert not (batch/'scope-extension.json').exists();rows=[]
for i,path in enumerate(paths):
 p=root/path;q=batch/'before'/f'client-{i:02}.bin'
 if p.exists():q.write_bytes(p.read_bytes())
 rows.append(dict(path=path,before=str(q.relative_to(batch)) if p.exists() else None,sha256=hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None))
(batch/'scope-extension.json').write_text(json.dumps(dict(reason='Actual browser rejected correct Java reply: scope.declaredConditions wire field differs from UI conditions; preserve strict wire validation and normalize at client boundary',files=rows),indent=2)+'\n',encoding='utf-8')
fixture=root/paths[2];fixture.parent.mkdir(exist_ok=True)
fixture.write_bytes((batch/'actual-reply-diagnostic.json').read_bytes())
print('Explicit API extension captured; actual Java response fixture copied without rewriting fields')
