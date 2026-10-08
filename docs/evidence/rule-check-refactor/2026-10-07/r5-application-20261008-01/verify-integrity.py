from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
proof=read(batch/'integrity-check.json');assert proof['passed']
assert all(sha(batch/name)==value for name,value in proof['artifacts'].items())
assert all(sha(root/row['path'])==row['sha256'] for row in proof['currentWhitelist'])
assert all(sha(root/path)==value for path,value in proof['unchanged'].items())
metrics=read(batch/'metrics.json');directory=batch.parent/metrics['phase']
assert all(sha(root/row['path'])==row['sha256'] for row in read(directory/'source-after.json'))
assert all(sha(directory/'xml'/row['xml'])==row['sha256'] for row in read(directory/'results.json')['suites'])
print(json.dumps(dict(passed=True,artifacts=len(proof['artifacts']),sourceFiles=proof['sourceFiles'],tests=metrics['tests']),ensure_ascii=False))
