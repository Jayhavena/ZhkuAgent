from pathlib import Path
import hashlib, json, re, difflib
root=Path(__file__).resolve().parents[5]; batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
target='knowledge/src/main/java/com/zhku/knowledge/rag/CitationAuditRecord.java'
row=next(r for r in read(batch/'source-before.json')['files'] if r['path']==target)
original=(batch/row['before']).read_bytes()
current=(root/target).read_bytes()
old_doc=re.search(rb'/\*\*.*?\*/',original,re.S).group()
expected=next(r['sha256'] for r in read(batch/'source-review.json') if r['path']==target)
candidates=[re.sub(rb'/\*\*.*?\*/',lambda m:d,current,count=1,flags=re.S)
            for d in [old_doc,old_doc.replace(b'\r\n',b'\n')]]
previous=next(data for data in candidates if hashlib.sha256(data).hexdigest()==expected)
assert previous.split(b'*/',1)[1]==current.split(b'*/',1)[1]
(batch/'comment-only-fix.diff').write_text(''.join(difflib.unified_diff(previous.decode('utf-8').splitlines(True),
    current.decode('utf-8').splitlines(True),fromfile='review/'+target,tofile='final/'+target)),encoding='utf-8')
(batch/'comment-fix-proof.json').write_text(json.dumps(dict(passed=True,reviewSha256=expected,
    finalSha256=hashlib.sha256(current).hexdigest(),change='FIRST_JAVADOC_ONLY',
    bytesAfterFirstJavadocUnchanged=True),indent=2)+'\n',encoding='utf-8')
print('修正仅首个Javadoc块；代码/构造器/字段字节未改变')
