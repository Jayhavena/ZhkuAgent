from pathlib import Path
import datetime,hashlib,json
from pypdf import PdfReader
HERE=Path(__file__).resolve().parent
SOURCE=Path('D:/alldesk/Projects/SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf')
assert SOURCE.is_file(),'2025原件路径不可读，不能伪造原文'
fingerprint=hashlib.sha256(SOURCE.read_bytes()).hexdigest()
assert fingerprint=='dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653'
reader=PdfReader(SOURCE)
pages=[{'physicalPage':n,'printedPage':n-12,'text':reader.pages[n-1].extract_text()} for n in [76,77]]
assert all(p['text'] for p in pages)
out=HERE/'source-pages.json';assert not out.exists()
out.write_text(json.dumps({'sourcePath':str(SOURCE),'sourceSha256':fingerprint,'pdfPageCount':len(reader.pages),
 'extractedAt':datetime.datetime.now().astimezone().isoformat(),'pages':pages,
 'extractionMethod':'pypdf_text_not_human_annotation','humanApproved':False},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for p in pages:print(f'物理页{p["physicalPage"]}/印刷页{p["printedPage"]}\n{p["text"]}\n')
