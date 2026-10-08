"""只整理开发候选并附实际原文；不能替用户填写人工审核结果。"""
from pathlib import Path
import datetime,hashlib,json,re

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[4]
DATA=ROOT/'docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/quality-cases-draft.json'
SOURCE=HERE/'source-pages.json'
OUT=ROOT/'docs/product/rule-check-2025-human-reference-development.md'
RECORDS=ROOT/'docs/product/rule-check-2025-human-reference-development.json'
SOURCE_MD=ROOT/'docs/product/rule-check-2025-source-excerpts.md'
assert not any(p.exists() for p in [OUT,RECORDS,SOURCE_MD,HERE/'verification.json']), '禁止覆盖人工核对工作表'
source=json.loads(SOURCE.read_text(encoding='utf-8'));data_hash_before=hashlib.sha256(DATA.read_bytes()).hexdigest()
dataset=json.loads(DATA.read_text(encoding='utf-8-sig'))
dev=[r for r in dataset['cases'] if r['split']=='DEVELOPMENT'];assert len(dev)==20
numbers=['','一','二','三','四','五','六','七','八','九','十','十一','十二','十三','十四','十五','十六','十七','十八','十九','二十']
articles={}
for page in source['pages']:
 matches=list(re.finditer(r'第([一二三四五六七八九十]+)条',page['text']))
 for i,m in enumerate(matches):
  number=numbers.index(m.group(1));assert number not in articles
  end=matches[i+1].start() if i+1<len(matches) else len(page['text'])
  paragraph=page['text'][m.start():end].strip()
  paragraph=re.split(r'\n第[一二三四五六七八九十]+章',paragraph)[0].strip()
  articles[number]={'article':number,'physicalPage':page['physicalPage'],'printedPage':page['printedPage'],'rawExtractedText':paragraph}
assert set(articles)==set(range(1,21))
status={'ANSWERED':'可以依据原文回答','PARTIALLY_ANSWERED':'只能回答有据部分','NEED_CLARIFICATION':'需要先补条件','INSUFFICIENT_EVIDENCE':'缺少必要依据','CONFLICTING_EVIDENCE':'有未解决冲突'}
records=[]
for i,row in enumerate(dev,1):
 refs=[]
 for ref in row['requiredEvidence']:
  article=articles[ref['article']]
  assert article['physicalPage']==ref['pdfPhysicalPage'] and article['printedPage']==ref['printedPage']
  assert ref['sha256']==source['sourceSha256']
  refs.append(article)
 question=row['question'].removeprefix('以2025版手册历史文本为依据：')
 records.append({'reviewId':f'Q{i:02d}','sourceCaseId':row['caseId'],'split':'DEVELOPMENT',
  'question':question,'questionFamily':row['questionFamily'],
  'proposedStatus':row['expectedStatusUnderReviewedHistoricalSnapshot'],
  'proposedFacts':row['requiredFacts'],'proposedBoundary':row['criticalBoundary'],
  'sourceEvidence':refs,'sourceQualification':'NOT_APPROVED_FOR_STUDENT_RELEASE',
  'annotationStatus':'PENDING_HUMAN_REVIEW','humanReview':{'reviewer':None,'reviewedAt':None,
  'decision':None,'acceptedStatus':None,'acceptedFacts':None,'acceptedEvidence':None,'reason':None}})
payload={'schemaVersion':1,'referenceRevision':'2025.9.2','sourceSha256':source['sourceSha256'],
 'originDatasetId':dataset['datasetId'],'samplingStatus':'AUTHORED_EXPLORATORY','locked':False,
 'humanReferenceApproved':False,'usableForProductQualityGate':False,'developmentCount':20,
 'reportCasesIncluded':False,'records':records,
 'notes':['问题与参考要点来自原开发草稿，状态/边界待人工核对；原文来自本轮真实PDF提取。',
 'Q05需核对缓考/重修的第十八条关联；补入或调优使用时重新检查开发/报告条款家族隔离。',
 '抽取或Codex复核不等于用户人工审核；不把原文位置参考改标为已审发布。']}
RECORDS.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
source_text=['# 2025版手册：公共选修核对原文','',
 '2026-10-08从已记录原件实际提取，SHA-256与既存快照一致。内容仅供本项目人工核对，未改变学生展示资格。断行来自PDF提取，视觉位置以页图或原件为准。',
 '', '原文位置：物理页76–77，印刷页64–65。《仲恺农业工程学院选修课程管理办法》（仲字〔2021〕65号）。',
 '', '[第76页原件页图](../evidence/rule-check-refactor/2026-10-07/r0-baseline-01/page-76.png) · [第77页原件页图](../evidence/rule-check-refactor/2026-10-07/r0-baseline-01/page-77.png)',
 '', '以下只排列本开发批次所需条款，Q05另附第十八条以便检查课程类别与缓考/重修关系。']
used=sorted({a['article'] for row in records for a in row['sourceEvidence']}|{18})
for n in used:
 a=articles[n];source_text+=['',f'## 第{numbers[n]}条（物理页{a["physicalPage"]}／印刷页{a["printedPage"]}）','', '```text',a['rawExtractedText'],'```']
SOURCE_MD.write_text('\n'.join(source_text)+'\n',encoding='utf-8')
doc=['# 2025版公共选修：人工参考核对清单（开发集20题）','',
 '日期：2026-10-08。依据用户决定固定2025版手册。所有题目为开发者构造的探索案例；参考要点是待核对草稿，人工审核结果全部留空。',
 '', '**统一问题范围：依据2025版手册中的《选修课程管理办法》作答。** 个人记录、当期通知和系统入口状态不在这两页原文中；缺少依据时请标出缺口。',
 '', '[原文摘录与页图](rule-check-2025-source-excerpts.md) · [结构化审核记录](rule-check-2025-human-reference-development.json)',
 '', '## 怎么审核','',
 '每题选择“确认／修改／依据不足／有争议”，再填核对人、日期、结论与原文位置。不同合理表述均可接受；重点检查数字、单位、对象、必要条件和例外。确认参考要点与确认资料学生发布资格分别记录。',
 '', '可以在聊天中按编号回复，例如：`Q01确认；Q02改为……，依据第……条；Q03依据不足，因为……`。批量确认前请实际核对原文。',
 '', '## 问题总表','', '| 编号 | 问题 | 原文位置 |', '| --- | --- | --- |']
for row in records:
 loc='；'.join(f'第{a["article"]}条，PDF {a["physicalPage"]}/印刷 {a["printedPage"]}' for a in row['sourceEvidence'])
 doc.append(f'| {row["reviewId"]} | {row["question"]} | {loc} |')
doc+=['','## 逐题审核','',
 '建议先审核Q01–Q05建立标准，再继续其余开发题。Q05的缓考/重修关系还需对照第十八条；若新增该关联证据用于调优，需要复核与报告候选的条款家族隔离。']
for row in records:
 facts='；'.join(row['proposedFacts'])
 refs='；'.join(f'第{a["article"]}条，物理页{a["physicalPage"]}/印刷页{a["printedPage"]}' for a in row['sourceEvidence'])
 doc+=['',f'### {row["reviewId"]}：{row["question"]}','',
  f'- 原文位置：{refs}。',f'- 建议处理方式（待确认）：{status[row["proposedStatus"]]}。',
  f'- 参考要点草稿：{facts}',f'- 重点核对：{row["proposedBoundary"]}',
  '- 审核选择：□ 确认　□ 修改　□ 依据不足　□ 有争议',
  '- 你的结论及修改理由：________', '- 核对人／日期：________']
doc+=['','## 本批记录边界','',
 '这里只列20道开发题，16道报告候选未纳入本工作表，原草稿及分组没有改写。报告候选仍待独立人工审核与冻结；本批资料提取和浏览记录不能宣称盲测或独立人工金标准。',
 '', '本清单没有运行模型或产品评估，没有写入数据库、没有批准资料发布。回填真实人工结果之后再建立可用参考版本，保留原稿与修改记录。']
OUT.write_text('\n'.join(doc)+'\n',encoding='utf-8')
assert len(payload['records'])==20 and len({r['reviewId'] for r in records})==20
assert all(r['humanReview']['decision'] is None and r['annotationStatus']=='PENDING_HUMAN_REVIEW' for r in records)
assert all('r0-dev-' in r['sourceCaseId'] for r in records)
assert hashlib.sha256(DATA.read_bytes()).hexdigest()==data_hash_before
fingerprints=[{'path':p.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [DATA,SOURCE,OUT,RECORDS,SOURCE_MD]]
(HERE/'verification.json').write_text(json.dumps({'createdAt':datetime.datetime.now().astimezone().isoformat(),
 'passed':True,'developmentCount':20,'reportCasesIncluded':False,'rawPdfFingerprintMatched':True,
 'sourceArticlesParsed':20,'humanReviewApproved':False,'productEvaluationRun':False,'databaseRun':False,
 'modelCalled':False,'files':fingerprints},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS 20 development questions, actual source excerpts, blank human review fields; report candidates unchanged')
