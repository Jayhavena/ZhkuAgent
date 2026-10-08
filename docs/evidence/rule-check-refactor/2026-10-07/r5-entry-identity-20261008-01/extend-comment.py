from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
path='knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckController.java';p=root/path
before=json.loads((batch/'source-before.json').read_text(encoding='utf-8-sig'))
sha=hashlib.sha256(p.read_bytes()).hexdigest();assert sha==before['unchanged'][path]
assert not (batch/'scope-extension.json').exists()
(batch/'before/09-controller-comment.bin').write_bytes(p.read_bytes())
(batch/'scope-extension.json').write_text(json.dumps(dict(reason='Review M2: Chinese boundary comment must match changed v2 cleanup; comment only, no runtime change',files=[dict(path=path,before='before/09-controller-comment.bin',sha256=sha)]),indent=2)+'\n',encoding='utf-8')
print('Explicit scope extension: Controller Chinese comment only; original 9-file before preserved')
