from pathlib import Path
import hashlib,json,difflib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists();resolution=read(batch/'review-resolution.json')
assert resolution['criticalRemaining']==resolution['importantRemaining']==0 and (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-release-guard-final-20261008-01');directory=batch.parent/phase;r=read(directory/'results.json')
assert r['passed'] and r['exitCode']==0 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited'] and not r['missingXml']
dbnames={'KnowledgeSchemaTest','RetrievalSchemaTest','ZhkuFlywayTestSupportTest','JdbcFeedbackRepositoryTest','JdbcAnonymousTelemetryRepositoryTest','JdbcCitationAuditRepositoryTest','JdbcKnowledgeGovernanceRepositoryTest','KnowledgeGovernanceSchemaTest','ReviewedKnowledgePublisherTest','JdbcRetrievalIndexingTest','JdbcEmbeddingProfileStoreTest','KnowledgeGovernancePublisherTest','JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest','RuleCheckPreviewHttpTest'}
counts=dict(unit=0,database=0,credentialConfiguration=0);classes={k:0 for k in counts}
for suite in r['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256'];actual=ET.parse(xml).getroot()
 assert int(actual.get('tests'))==suite['tests'] and all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped'])
 name=suite['name'].split('.')[-1];group='database' if name in dbnames else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert r['testCount']>=482 and len(r['suites'])==47 and sum(counts.values())==r['testCount'] and counts['database']==123 and counts['credentialConfiguration']==12
sources=read(directory/'source-after.json');assert all(sha(root/x['path'])==x['sha256'] for x in sources)
before=read(batch/'source-before.json');assert len(before['files'])==12 and all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22' and not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
for redphase,tests,failures in [('r5-release-guard-red-20261008-01',14,13),('r5-release-guard-db-red-20261008-01',26,2),('r5-release-guard-review-valid-red-20261008-01',43,2)]:
 red=read(batch.parent/redphase/'results.json');assert not red['passed'] and red['testCount']==tests and sum(s['failures'] for s in red['suites'])==failures and sum(s['errors']+s['skipped'] for s in red['suites'])==0 and red['resourcesVerified'] and red['sourceDrift']==0
resolution['verificationStatus']='VERIFIED_FINAL_GREEN';write(batch/'review-resolution.json',resolution)
stage='FINAL_RECHECK_COMPONENT_VERIFIED_NOT_HTTP_ASSEMBLED';total=int(r['testCount']);summary=f'47类{total}项（{counts["unit"]}纯单元+123专用临时库含12HTTP+12凭据配置）、29计数替身合同；无卷55469 V22连接0/退出。'
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(f'''
## 2026-10-08 Task2N / A07返回前规则/资料复查组件

ScopedResult携仅请求内存ReleaseBinding(settings/全文审核entry/question/明确条件/本轮refs元数据)，无HTTP DTO/Session存储。isCurrent先检查精确settings/current approved entry和有界唯一refs，再fresh读原集合，保持alias/hash/revision/身份/资格；deterministic decide及Verifier再核验全文/跨度/当前资料，逐项比对decision/scope/continuation并后置目录复查。不重选/检索/embedding/capture/audit，不取新未检索证据升级partial。包含无输出quote的必要澄清，也保守拒绝非输出chunk变化。

{summary}有效RED unit14/13、DB26/2；相邻GREEN118PASS；3新实际DB反例涵盖决策后publication/version/body+hash/cut变化、无quote澄清撤回、未输出片段正文漂移，audit不增。审查I1双片段有效RED43/2→逐current正文纯SHA256/UTF16检查；前两次43/1fail+1error缺必填source_locator的夹具错误保留，不称有效业务RED。多片段清理补齐属于夹具支持，不冒称已复现独立cleanup缺陷。<=50是distinct原refs，不是整个SQL数/端到端时间保证；新增16纯guard检查不是HTTP/并发事务。isReferenceApproved只目录精确binding，不证明来源当前资格。[证据](../evidence/rule-check-refactor/2026-10-07/r5-release-guard-20261008-01/acceptance.md)。Controller/Security/Session/config保护未调用新方法，A06 GET与A07最终identity/Session/HTTP窗口仍OPEN，B/C/G2不变。
''')
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 返回前复查内部组件（Task2N / A07，2026-10-08）

非缺证ScopedResult由应用生成仅请求内存ReleaseBinding(settings、全文审核entry、question、明确条件、原capture元数据refs)；兼容3参数constructor不授权有依据结果release。isCurrent精确settings/entry及唯一alias/持久身份/版本/hash，原refs非空且<=resultLimit<=50；fresh reader重建同轮alias的原集合，不重新检索/外发/audit。deterministic decide/verifier继续全文hash/UTF16/span/current核验，exact比较完整decision/scope/continuation并后置catalogue。无scope/continuation/binding的INSUFFICIENT只给无facts/clarification/conflict的缺证理由，可不依赖catalogue可用。

isReferenceApproved仅当前APPROVED/精确revision目录facts和conflict bindings的source/chunk/revision/hash/span/quote匹配，citation alias不作持久身份；不证明当前source资格，HTTP仍须fresh locate和identity/Session完成后检查。所有原refs在重放前做纯正文UTF16/SHA256校验，即使未参与决策也不能只比较两份旧checksum字段；纯校验不增加查询或事实授权。public ReleaseBinding不证明来源真伪/自然语言选题相关性，不接受HTTP客户端构造。<=50是distinct refs，decide仍逐binding fresh verifier，不宣称整体SQL次数或时限受控。保守拒绝原非输出chunk变化及任何replay差异，可能增加拒答/延迟须C组评估。

新组件未Controller/Bean装配；A07真实HTTP身份/Session/撤回矩阵及最终下发窗口后段仍需实现。前后目录和顺序SQL核验不等于数据、身份和网络交付原子提交。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';text=plan.read_text(encoding='utf-8');a=text.index('## Task2N / A07');plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';text=backlog.read_text(encoding='utf-8')
text=text.replace('卡点表和Task2M最终证据','卡点表和Task2N最终证据').replace('46类463项（331纯单元+120专用临时库含12实际HTTP+12凭据相关配置检查）',f'47类{total}项（{counts["unit"]}纯单元+123专用临时库含12实际HTTP+12凭据相关配置检查）').replace('见Task2M证据','见Task2N证据')
text=text.replace('| A07 | 最终外发及再次展示边界 |','| A07 | 最终外发及再次展示边界（内部资料/规则复查已验收，HTTP身份/Session待续） |')
text=text.replace('[最新Task2M工程证据](../evidence/rule-check-refactor/2026-10-07/r5-citation-proof-20261008-01/acceptance.md)','[最新Task2N工程证据](../evidence/rule-check-refactor/2026-10-07/r5-release-guard-20261008-01/acceptance.md)');backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2N/A07复核组件: complete — unitRED14/13、DB26/2均0error/skip，GREEN118；后加partial不升级/预算内重复refs验证通过、不称新RED；审查I1双片段先两次43/1fail+1error为缺locator夹具，补定位字段与多片段清理后有效43/2→逐refs纯正文SHA256/UTF16核验；最终{summary}一次12项增量审查，一轮修复无重审。Controller/config/Session未装配，真实2025DRAFT/参考保护不变。\n')
 for ruling in resolution.get('rulings',[]):f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution.get('deferredMinors',[]):f.write('\nFinal: minor (deferred): '+minor+'\n')
 for fixed in resolution.get('fixed',[]):f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n接续A06原文HTTP与A07最终身份/Session/规则资料gate调用及实际HTTP矩阵；B/C/A08–A10/G2保持OPEN。\n')
after=[];diff=[]
for row in before['files']:
 p=root/row['path'];data=p.read_bytes();after.append(dict(path=row['path'],sha256=sha(p)))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=total,classes=len(r['suites']),counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,unchanged=before['unchanged'],database=db,containerExited=True,httpIntegrated=False,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text(f'''# Task2N / A07：返回前规则/资料复查组件验收

2026-10-08。{stage}。

|解决的问题|实现|验证|
|---|---|---|
|决策后资料或全文审核规则变化|绑定settings/entry/原refs，fresh读并重跑确定性决策、前后目录比对|publication/version/body+hash/cut实际DB拒绝；规则scope/责任/事实/复查期间变化单元反例|
|只查输出quote漏掉必要澄清|重新核验原集合并比对全decision/scope/continuation|无输出摘录的NEED_CLARIFICATION资料撤回后拒绝；四有scope状态重放|
|复查变成重新选题/外发/审计或擅自升级|保留alias及原集合，仅reader/decide|selector/retrieval/audit不增；新未检索证据可读也不将partial升级|
|兼容record构造或伪scope被当可信产物|非缺证无binding拒绝，元数据唯一有界及完整重放比对|旧3参数/伪事实/scope/continuation/错settings/缺或重复refs反例|
|目录引用membership混淆current资格|只精确binding匹配facts/conflict|不由本方法证明source当前资格；HTTP另fresh locate/identity/Session|

有效RED14/13pure+26/2DB均0error/skip，第一GREEN5类118PASS；后加partial与预算内重复refs验证，已有实现通过，不伪称额外RED。审查I1双片段前两次43/1fail+1error是缺必填source_locator夹具问题；修正定位字段和新增多片段清理后有效RED43/2/0error，补逐refs纯正文SHA256/UTF16检查而不增加查询，覆盖未输出片段漂移。清理改造是新夹具支持，不冒称独立cleanup故障已复现。{summary}最终0失败/异常/跳过，569源码/XMLhash复算，12项before/after、Controller/Security/config/Session及所有其余main/真实2025DRAFT/20参考/36题保护不变。一次审查及一次修复无重审，见review.md/review-resolution.json；所有失败报告保留。

refs<=50是distinct身份，不是总SQL数/端到端时限；decide仍逐binding再fresh核验。模板/question/条件/元数据只inflight，不存Session/回复DTO。公开record不证明原始query真实性/问题相关性，真实选题语义另C组。保守拒绝原非输出chunk变化可能增加拒答/延迟，需eval。

本批未HTTP/Bean装配，旧Controller尚未调用新gate。顺序复查不是数据/身份/网络原子提交；A06GET、A07最终当前身份/Session调用与真实HTTP矩阵、B/C及页面/用户收益G2继续OPEN。正式CLOSED/真实资料不可见不变，无原库/供应商/留出评测/部署/commit。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,sourceFiles=len(sources)),ensure_ascii=False))
