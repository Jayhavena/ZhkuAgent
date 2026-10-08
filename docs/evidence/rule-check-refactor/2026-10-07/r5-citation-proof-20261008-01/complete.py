from pathlib import Path
import hashlib,json,difflib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists();resolution=read(batch/'review-resolution.json')
assert resolution['criticalRemaining']==resolution['importantRemaining']==0 and (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-citation-proof-final-20261008-01');directory=batch.parent/phase;r=read(directory/'results.json')
assert r['passed'] and r['exitCode']==0 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited'] and not r['missingXml']
baseline=read(batch.parent/'h01-database-isolation-20261008-01/metrics.json')
dbnames={'KnowledgeSchemaTest','RetrievalSchemaTest','ZhkuFlywayTestSupportTest','JdbcFeedbackRepositoryTest','JdbcAnonymousTelemetryRepositoryTest','JdbcCitationAuditRepositoryTest','JdbcKnowledgeGovernanceRepositoryTest','KnowledgeGovernanceSchemaTest','ReviewedKnowledgePublisherTest','JdbcRetrievalIndexingTest','JdbcEmbeddingProfileStoreTest','KnowledgeGovernancePublisherTest','JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest','RuleCheckPreviewHttpTest'}
counts=dict(unit=0,database=0,credentialConfiguration=0);classes={k:0 for k in counts}
for suite in r['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256'];actual=ET.parse(xml).getroot()
 assert int(actual.get('tests'))==suite['tests'] and all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped'])
 name=suite['name'].split('.')[-1];group='database' if name in dbnames else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert r['testCount']>=463 and len(r['suites'])==46 and sum(counts.values())==r['testCount'] and counts['database']==120 and counts['credentialConfiguration']==12
sources=read(directory/'source-after.json');assert all(sha(root/x['path'])==x['sha256'] for x in sources)
before=read(batch/'source-before.json');assert len(before['files'])==8 and all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22' and not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
for redphase,tests,failures in [('r5-citation-proof-contract-red-20261008-01',18,18),('r5-citation-proof-deadline-red-20261008-01',19,1)]:
 red=read(batch.parent/redphase/'results.json');assert not red['passed'] and red['testCount']==tests and sum(s['failures'] for s in red['suites'])==failures and sum(s['errors']+s['skipped'] for s in red['suites'])==0 and red['resourcesVerified'] and red['sourceDrift']==0
resolution['verificationStatus']='VERIFIED_FINAL_GREEN';write(batch/'review-resolution.json',resolution)
stage='SESSION_CITATION_PROOF_VERIFIED_NOT_HTTP_ASSEMBLED';total=int(r['testCount']);summary=f'46类{total}项（{counts["unit"]}纯单元+120专用临时库含12HTTP+12凭据配置）、29计数替身合同；无卷55468 V22连接0/退出。'
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(f'''
## 2026-10-08 Task2M / A06服务端引用凭据组件

每Session单一轮次，完整actor/generation/随机handle/开始后固定5min；新问立即废旧引用，迟到publish/clearOwn和原文I/O后requireCurrent匹配当前轮/身份/TTL/完整quote。32输入项去重前限数，4096UTF16/8192UTF8单quote、32768唯一quote UTF8总量，ID/span/hash/代理对检查。只留有限摘录，不留问题/条件/完整回复历史；引用可重复打开不续期。

{summary}最初RED18/17fail+1NPE前置断言问题已保留；补断言后18/18有效失败，首GREEN56PASS，期限跨越反例19/1→构造后再current。新增19纯单元不等于HTTP/真实source资格。组件无Bean/Controller装配，A06原文HTTP/实际页码和A07身份/目录/资料最终复查继续OPEN。[证据](../evidence/rule-check-refactor/2026-10-07/r5-citation-proof-20261008-01/acceptance.md)。
''')
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 服务端引用凭据组件（Task2M / A06，2026-10-08）

只用于内部Session引用绑定，不提供HTTP/Bean入口。每Session仅一个generation及完整TrustedActor快照，begin废旧轮；固定5min从开始，不因publish/resolve延长。publish仅一次，32输入引用先限数再去重、alias/revision128、hash64hex、持久UUID、有效span/长度、Unicode；单quote4096UTF16/8192UTF8、唯一quote合计32768UTF8。同alias不同quote拒绝，精确重复共用随机UUIDhandle；空终态不能再补写。仅保留有限excerpt正文，不保留问题/条件/完整历史/原文全文。

resolve接受canonical opaque handle且匹配本Session/actor/TTL；requireCurrent供原文I/O后校验轮次和完整server条目，不靠public Ticket/VerifiedExcerpt构造器自证。clearOwn不能清新账号/新轮。Session失效或未知凭据固定Failure无cause。未来HTTP必须只传handle，另需A07当前身份/审核目录/资料复查，顺序检查不保证网络交付原子性；组件合同不证明来源/语义真实性。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';text=plan.read_text(encoding='utf-8');a=text.index('## Task2M / A06');plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';text=backlog.read_text(encoding='utf-8')
text=text.replace('卡点表和Task2L最终证据','卡点表和Task2M最终证据').replace('45类444项（312纯单元+120专用临时库含12实际HTTP+12凭据相关配置检查）',f'46类{total}项（{counts["unit"]}纯单元+120专用临时库含12实际HTTP+12凭据相关配置检查）').replace('见Task2L证据','见Task2M证据')
text=text.replace('原文查询与定位入口（内部定位已验收，HTTP/实际页码待续）','原文查询与定位入口（内部定位/会话引用凭据已验收，HTTP/实际页码待续）')
text=text.replace('[最新Task2L工程证据](../evidence/rule-check-refactor/2026-10-07/h01-database-isolation-20261008-01/acceptance.md)','[最新Task2M工程证据](../evidence/rule-check-refactor/2026-10-07/r5-citation-proof-20261008-01/acceptance.md)');backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2M/A06引用凭据: complete — 原18RED17fail+1NPE，前置断言修正后18/18有效失败；相邻GREEN56；期限19/1→构造后再current；最终{summary}一次8项增量审查。组件未HTTP/Bean装配；所有既有main/真实2025DRAFT/参考保护不变。\n')
 for ruling in resolution.get('rulings',[]):f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution.get('deferredMinors',[]):f.write('\nFinal: minor (deferred): '+minor+'\n')
 for fixed in resolution.get('fixed',[]):f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n继续A06原文HTTP与A07最终外发，B/C/A08–A10及G2保持OPEN。\n')
after=[];diff=[]
for row in before['files']:
 p=root/row['path'];data=p.read_bytes();after.append(dict(path=row['path'],sha256=sha(p)))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=total,classes=len(r['suites']),counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,unchanged=before['unchanged'],database=db,containerExited=True,httpIntegrated=False,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text(f'''# Task2M / A06：服务端引用凭据组件验收

2026-10-08。{stage}。

|解决的问题|实现|验证|
|---|---|---|
|客户端自报source/chunk/quote不能授权原文|内部Session/full actor/opaque handle|Session、用户、revision、角色、猜造handle与Ticket反例；未来HTTP不得接客户端excerpt|
|无限历史和过期正文恢复|单轮32引用/文本预算/开始后固定5min|精确数量、UTF16/UTF8及总字节边界；重复读不续期，空完成不可补写|
|旧慢请求覆盖或删新问题|generation publish/clearOwn/requireCurrent|新问即废旧handle；旧publish/clear不影响新轮，I/O后再次核对|
|处理期间跨过期限仍发布|校验和handle构造后再次current|19/1有效deadline RED后最小修复；过期不保留slot|

原RED18为17断言失败+1测试前置NPE，补handle存在断言后18/18有效失败/0error；首GREEN4类56PASS，新增期限反例19/1/0error。{summary}最终0失败/异常/跳过，568源码/XMLhash复算、8项before/after及全部既有main/真实2025DRAFT/20确认参考/36题保护不变。一次审查见review.md/review-resolution.json；故障报告保留。

新增19项是MockHttpSession/Clock纯单元，不等于实际HTTP/资料当前资格/供应商或用户成绩。只存有限摘录正文，不能称无正文存储；公开record不是真实性证明。组件未HTTP/Bean装配；正式CLOSED和真实资料不可见不变。A06后段原文HTTP/页码、A07身份/目录/证据最终复查及B/C/G2继续OPEN，无原库/模型/留出评测/部署/commit。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,sourceFiles=len(sources)),ensure_ascii=False))
