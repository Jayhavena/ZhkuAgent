"""收口后只读核对最新源码/工程报告与文档链接，不重复运行已通过的测试。"""
from pathlib import Path
import hashlib, json, re
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[3]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
latest=BASE/'r5-db-20261008-01'
manifest=read(latest/'verification.json')
for row in manifest['sourceHashes']:assert sha(ROOT/row['path'])==row['sha256'],row['path']
for name in ['r4-read-20261008-01','p0-closeout-20261008-01','r5-evidence-20261008-01','r5-db-20261008-01']:
 folder=BASE/name
 for row in read(folder/'integrity-check.json')['files']:assert sha(ROOT/row['path'])==row['sha256'],row['path']
 for p in folder.glob('acceptance.md'):
  for target in re.findall(r'\]\(([^)]+)\)',p.read_text(encoding='utf-8')):
   if not target.startswith(('http:','https:','#')):assert (p.parent/target).resolve().exists(),f'{p.name}: {target}'
issues=read(BASE/'issues.json')
assert issues['latestExecution']['testCount']==172
assert next(x for x in issues['issues'] if x['id']=='RC-04')['subissues'][1]['status']=='OPEN'
assert next(x for x in issues['issues'] if x['id']=='RC-05')['subissues'][0]['status']=='CLOSED'
assert next(x for x in issues['issues'] if x['id']=='RC-05')['subissues'][1]['status']=='OPEN'
print(f'PASS latest {len(manifest["sourceHashes"])} source hashes, 4 artifact manifests and acceptance links; semantic/lifecycle remain OPEN')
