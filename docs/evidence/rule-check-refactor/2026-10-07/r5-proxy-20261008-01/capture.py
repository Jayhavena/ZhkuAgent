from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
assert not (batch/'source-before.json').exists();(batch/'before').mkdir()
rows=[]
for i,path in enumerate(['agent/frontend/nginx.conf','agent/frontend/start-nginx-preview.ps1']):
 p=root/path;q=batch/'before'/f'{i:02}.bin';q.write_bytes(p.read_bytes());rows.append(dict(path=path,before=str(q.relative_to(batch)),sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
(batch/'source-before.json').write_text(json.dumps(dict(files=rows),indent=2)+'\n',encoding='utf-8')
print('Proxy: captured 2 existing files before mutation')
