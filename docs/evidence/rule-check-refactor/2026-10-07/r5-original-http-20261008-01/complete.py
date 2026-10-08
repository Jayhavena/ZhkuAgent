from pathlib import Path
import hashlib,json,difflib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists();resolution=read(batch/'review-resolution.json')
assert resolution['criticalRemaining']==resolution['importantRemaining']==0 and (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-original-http-final-20261008-01');directory=batch.parent/phase;r=read(directory/'results.json')
assert r['passed'] and r['exitCode']==0 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited'] and not r['missingXml']
dbnames={'KnowledgeSchemaTest','RetrievalSchemaTest','ZhkuFlywayTestSupportTest','JdbcFeedbackRepositoryTest','JdbcAnonymousTelemetryRepositoryTest','JdbcCitationAuditRepositoryTest','JdbcKnowledgeGovernanceRepositoryTest','KnowledgeGovernanceSchemaTest','ReviewedKnowledgePublisherTest','JdbcRetrievalIndexingTest','JdbcEmbeddingProfileStoreTest','KnowledgeGovernancePublisherTest','JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest','RuleCheckPreviewHttpTest','RuleCheckOriginalHttpTest'}
counts=dict(unit=0,database=0,credentialConfiguration=0)
for suite in r['suites']:
 p=directory/'xml'/suite['xml'];xml=ET.parse(p).getroot()
 assert sha(p)==suite['sha256'] and int(xml.get('tests'))==suite['tests'] and all(int(xml.get(k,'0'))==0 for k in ['failures','errors','skipped'])
 name=suite['name'].split('.')[-1];group='database' if name in dbnames else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests']
assert len(r['suites'])==48 and r['testCount']>=498 and sum(counts.values())==r['testCount'] and counts['credentialConfiguration']==12
sources=read(directory/'source-after.json');assert all(sha(root/x['path'])==x['sha256'] for x in sources)
before=read(batch/'source-before.json');assert len(before['files'])==15 and all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22' and not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
red=read(batch.parent/'r5-original-http-controller-red-20261008-01/results.json')
assert not red['passed'] and red['testCount']==22 and sum(s['failures'] for s in red['suites'])==7 and sum(s['errors']+s['skipped'] for s in red['suites'])==0
for red_phase,tests,failures in [('r5-original-http-review-red-20261008-01',24,2),('r5-original-http-review-db-red-20261008-01',9,1)]:
 rr=read(batch.parent/red_phase/'results.json')
 assert not rr['passed'] and rr['testCount']==tests and sum(s['failures'] for s in rr['suites'])==failures and sum(s['errors']+s['skipped'] for s in rr['suites'])==0 and rr['resourcesVerified'] and rr['sourceDrift']==0
stage='ORIGINAL_HTTP_AND_FINAL_IDENTITY_SESSION_RECHECK_VERIFIED_SYNTHETIC_ONLY';total=int(r['testCount'])
http_count=sum(s['tests'] for s in r['suites'] if s['name'].split('.')[-1] in {'RuleCheckPreviewHttpTest','RuleCheckOriginalHttpTest'})
summary=f'48类{total}项（{counts["unit"]}纯单元+{counts["database"]}专用临时库含{http_count}实际HTTP+12凭据相关配置检查），29启动器计数替身合同'
resolution['verificationStatus']='VERIFIED_FINAL_GREEN';write(batch/'review-resolution.json',resolution)
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 原文HTTP和回答末端核对（Task2O，2026-10-08）

独立v2 POST现装配isCurrent、fresh identity和Session保存SecurityContext的完整actor检查；澄清和引用generation同锁开始/发布，I/O锁外。请求初始的新问题立即替代旧引用，续答先验证delta再开始新引用轮。CitationReply增加originalToken（随机UUID），其他字段沿用既有协议；ReleaseBinding不进DTO/Session。异常只清自己的generation，不清并发新登录/新问题，不用迟到401响应无条件删Cookie。

USER GET /api/zhku/controlled-preview/v2/rule-check/originals/{originalToken}只读取本Session凭据，拒绝任何query参数；过期/未知/跨Session404，已知但目录/资料/会话变化409，当前身份撤销401、核验故障503。两次fresh locate必须FOUND且完整View一致，前后approved目录精确membership，再fresh identity和Session actor/generation/TTL检查；失败不带旧正文或cause，全部no-store。View记录页码只称RECORDED_SOURCE_PAGE，物理/印刷页映射仍未知；默认allowHosts为空，无已审核链接则unknown。生产CLOSED目录和真实DRAFT不可见不变。

独立9项真实HTTP测试连接实际RuntimeFactory/词法/捕获/审计/决定/当前replay/原文SQL；规则和账号合成、固定selector、向量故障词法fallback，不能称供应商语义或真实规则质量。旧12项HTTP里必要澄清组仍明确mock isCurrent，仅证明协议。审查I1后首问和续答在任何consume/begin前核对Session全actor，避免迟到旧请求覆盖新账号已完成slot；实际入口身份SQL的spy仅callRealMethod后阻塞，不mock许可。检查是顺序窗口，不是数据库/目录/身份/网络原子提交；缓存/代理/浏览器和多实例语义另验。浏览器需按身份和问题轮次丢弃迟到正文，A08–A10继续推进。
''')
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(f'''
## 2026-10-08 Task2O：A06原文HTTP与A07末端身份/Session复查

{summary}，零失败/异常/跳过。最终实测数据库目标为`{db['target']}`。修复前两次混合验收误写DatabasePort55470未绑定而使用默认55439，仍通过专用无卷回环目标门禁；审查RED/最终GREEN改为显式Port55470。保留command/database-attempt/database和实际退出记录，不改写旧报告。V22/连接0/容器实际退出，{len(sources)}源码0漂移/XML哈希复核。

原文opaque handle仅服务端Session解析；fresh approved membership、两次当前资格/全文hash/span/元数据比对、fresh身份、完成时Session全actor和generation/TTL；POST同样接入实际资料/规则replay和身份/轮次gate。实际反例涵盖撤回、version、正文保持旧hash、切片变化、目录清空、决策后authrev、定位后撤回/元数据/authrev、在途换账号和GET被新问题替代；旧慢响应不毁新登录或新凭据。有效初始RED22/7；65/2error是Mockito默认Answer被when(any())的null触发、96/2fail是审计夹具错误字段，均保留并不称业务RED；首次误用Database混合模式启动前拒绝无外部操作。[完整证据](../evidence/rule-check-refactor/2026-10-07/r5-original-http-20261008-01/acceptance.md)。

工程HTTP已连通，真实2025资料绑定/审核/分享、物理/印刷页映射、供应商选题语义、浏览器/普通终端与用户收益G2仍OPEN。两次SQL和末端身份不能证明最终网络下发原子性；默认host空/目录CLOSED。整体请求耗时/查询数门禁仍待性能验证。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';text=plan.read_text(encoding='utf-8');a=text.index('## Task2O / A06');plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';text=backlog.read_text(encoding='utf-8')
text=text.replace('卡点表和Task2N最终证据','卡点表和Task2O最终证据').replace('47类482项（347纯单元+123专用临时库含12实际HTTP+12凭据相关配置检查）',f'48类{total}项（{counts["unit"]}纯单元+{counts["database"]}专用临时库含{http_count}实际HTTP+12凭据相关配置检查）').replace('见Task2N证据','见Task2O证据')
text=text.replace('真实具体范围/例外审核、最终下发及原文定位、学生页面','真实具体范围/例外审核、最终下发原子窗口及真实页码映射、学生页面')
text=text.replace('内部定位/会话引用凭据已验收，HTTP/实际页码待续','HTTP/会话凭据已验收，真实页码/浏览器待续').replace('内部资料/规则复查已验收，HTTP身份/Session待续','HTTP资料/规则/身份/Session复查已验收，下发原子窗口仍有局限')
text=text.replace('框架14项与旧v1 9项','框架15项与旧v1 9项')
text=text.replace('[最新Task2N工程证据](../evidence/rule-check-refactor/2026-10-07/r5-release-guard-20261008-01/acceptance.md)','[最新Task2O工程证据](../evidence/rule-check-refactor/2026-10-07/r5-original-http-20261008-01/acceptance.md)');backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2O/A06+A07 HTTP: complete — 初始有效RED22/7，先前两类夹具错误及Database混合拒绝完整保留；{summary}，V22连接0/退出/源码{len(sources)}和XML哈希完整复核。仅合成真实Runtime/HTTP，真实资料/语义/G2不放行。\n')
 f.write('\nTask2O: Ruling: Port参数更正记录 — 前两次database.json为默认55439，误写DatabasePort55470未绑定；最终显式Port55470且隔离库仍合格 — 成本：文档只报实测，保留原报告。\n')
 for ruling in resolution.get('rulings',[]):f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution.get('deferredMinors',[]):f.write('\nFinal: minor (deferred): '+minor+'\n')
 for fixed in resolution.get('fixed',[]):f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n接续Task3学生Vue页面/迟到响应丢弃/真实浏览器与普通终端验收；B/C/G2不变。\n')
after=[];diff=[]
for row in before['files']:
 p=root/row['path'];data=p.read_bytes();after.append(dict(path=row['path'],sha256=sha(p)))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=total,classes=48,counts=counts,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,database=db,httpIntegrated=True,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text(f'''# Task2O：原文HTTP与末端资料/身份/会话复查

2026-10-08。{stage}。

|解决的问题|实现|验证|
|---|---|---|
|引用ID不能授权看原文|本Session随机handle绑定完整身份、轮次与有界核验摘录|未知/跨会话/新题/5分钟边界拒绝；拒客户端source/span等参数|
|决定后资料/规则/身份变化仍下发|isCurrent原集合replay、fresh身份、Session全actor/两代发布|实际HTTP中撤回、目录清空、authrev拒绝无旧正文|
|定位后资格或元数据变化|两次fresh locate完整View一致，目录前后membership，再身份/Session/TTL|实际原文读取后withdraw/locator/authrev拒绝|
|迟到请求毁新问题或新登录|仅清own generation；末端旧身份401不自动删cookie|真实Tomcat在途换账号、新题替代GET；新登录/新凭据可用|

{summary}，全部零失败/异常/跳过。{len(sources)}源码0漂移/XML哈希复算，15项before/after/增量、其他main和真实DRAFT/已确认20参考/36题不变。有效初始RED22/7/0error；其后Mockito null夹具2error和审计计数错字段2fail均保留，不称业务RED。审查I1有效RED24/2纯+9/1实际HTTP，均0error/skip→开始前Session actor校验；新账号先完成原文/澄清后旧请求不能消费/覆盖，实际身份spy只callRealMethod后阻塞。首次Database混合选类在启动前拒绝。29合同externalStarts=9来自计数替身，不是真实外部启动。最终临时库目标{db['target']}，V22、连接0、无卷、容器及测试Java实际退出；前两次误写DatabasePort默认55439，审查RED/最终GREEN显式Port55470，报告以实测为准。

新增9真实HTTP连接实际RuntimeFactory/词法/捕获/审计/决定/replay/原文；selector固定合成ID、embedding固定故障词法fallback，无供应商/真实语义。原12HTTP里的澄清协议组明确替身isCurrent，不冒称政策链。一次独立审查，重要项按review-resolution修复，不重审；最终文档补充未要求二次审查。

正式CLOSED/真实2025DRAFT不可见、默认原件host空；物理/印刷页码映射、真实审核/分享/质量、浏览器/代理/普通终端、用户收益G2继续OPEN。资料/目录/身份顺序检查不能保证网络交付原子性；完整请求查询数/时限和多实例语义尚无验收。没有原库写入、真实模型调用、提交、推送或部署。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,tests=total,sourceFiles=len(sources)),ensure_ascii=False))
