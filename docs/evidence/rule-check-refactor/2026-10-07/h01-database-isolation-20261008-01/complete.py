from pathlib import Path
import hashlib,json,difflib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists()
resolution=read(batch/'review-resolution.json');assert resolution['criticalRemaining']==resolution['importantRemaining']==0 and (batch/'review.md').exists()
phase=resolution.get('finalPhase','h01-database-isolation-final-20261008-01');directory=batch.parent/phase;r=read(directory/'results.json')
assert r['passed'] and r['exitCode']==0 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited'] and not r['missingXml']
old_db={'KnowledgeSchemaTest','RetrievalSchemaTest','ZhkuFlywayTestSupportTest','JdbcFeedbackRepositoryTest','JdbcAnonymousTelemetryRepositoryTest','JdbcCitationAuditRepositoryTest','JdbcKnowledgeGovernanceRepositoryTest','KnowledgeGovernanceSchemaTest','ReviewedKnowledgePublisherTest','JdbcRetrievalIndexingTest','JdbcEmbeddingProfileStoreTest','KnowledgeGovernancePublisherTest'}
counts=dict(unit=0,database=0,credentialConfiguration=0);classes={k:0 for k in counts}
for suite in r['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256'];actual=ET.parse(xml).getroot()
 assert int(actual.get('tests'))==suite['tests'] and all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped'])
 name=suite['name'].split('.')[-1];group='database' if name in old_db|{'JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest','RuleCheckPreviewHttpTest'} else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert sum(counts.values())==r['testCount'] and r['testCount']>=444 and len(r['suites'])==45
sources=read(directory/'source-after.json');assert all(sha(root/x['path'])==x['sha256'] for x in sources)
before=read(batch/'source-before.json');assert len(before['files'])==42 and all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22' and not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
for redphase,tests,failures in [('h01-database-isolation-red-20261008-01',11,10),('h01-database-compat-red-20261008-01',14,2)]:
 red=read(batch.parent/redphase/'results.json');assert not red['passed'] and red['testCount']==tests and sum(s['failures'] for s in red['suites'])==failures and sum(s['errors']+s['skipped'] for s in red['suites'])==0 and red['resourcesVerified'] and red['sourceDrift']==0
resolution['verificationStatus']='VERIFIED_FINAL_GREEN';write(batch/'review-resolution.json',resolution)
stage='LEGACY_DEFAULT_DATABASE_TARGETS_GUARDED_NOT_FULL_CI';total=int(r['testCount'])
summary=f'45类{total}项（{counts["unit"]}纯单元+{counts["database"]}专用临时库含12HTTP+12凭据配置）、29计数替身合同；无卷55467 V22连接0/退出。'
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(f'''
## 2026-10-08 Task2L / H01旧数据库测试默认入口隔离

32处原演示库默认URL改显式guarded getter，static字段/数据源构造先拒绝，共享测试Flyway再次guard；只允许精确回环非54329端口、既有三个专用临时库名，无URL扩展。窄archive/rulecheck/demo guard保持。孤立产品启动探针新增实际继承环境URL的自包含archive guard，不能用父测试属性代替子进程保护。

{summary}纯单元RED11/10、兼容/探针RED14/2均0异常/跳过，目标下旧功能首GREEN13类50PASS。32默认站点是源码改造数，实际只额外执行12旧DB类39项；供应商/报告评测未运行。连接入口清单及一次审查见[证据](../evidence/rule-check-refactor/2026-10-07/h01-database-isolation-20261008-01/acceptance.md)。H01旧默认入口风险已处理，完整CI分类F06与打包探针正向验收不由本批关闭；真实资料/模型/浏览器/G2继续OPEN。
''')
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 旧数据库测试目标边界（Task2L / H01，2026-10-08）

所有32处旧原库默认配置改用显式zhku.db.url校验，shared Flyway配置前二次校验；固定127.0.0.1、1024..65535非54329、三个已存在专用测试库名zhkuagent_rulecheck_e2e/zhkuagent_run_archive_e2e/zhkuagent_e2e，不接受userinfo/query/fragment/尾随。archive/rulecheck/demo既有更窄边界不扩大。缺目标的直接全量Maven将安全失败，纯/DB/HTTP/供应商显式分类仍F06。测试密码来源不变，不把库名守卫当生产资料授权。

SubmissionProductionStartupProbe只挂载单class，独立校验继承环境URL仅为archive目标后才启动Application，固定错误不回显URL/cause；不能依赖父系统属性或未挂载helper。本批仅纯单元guard验证，未声称打包子进程正向启动或全部旧测试执行。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';text=plan.read_text(encoding='utf-8');a=text.index('## Task2L / H01：');plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';text=backlog.read_text(encoding='utf-8')
text=text.replace('卡点表和Task2K最终证据','卡点表和Task2L最终证据').replace('32类391项（298纯单元+81专用临时库含12实际HTTP+12凭据相关配置检查）',f'45类{total}项（{counts["unit"]}纯单元+{counts["database"]}专用临时库含12实际HTTP+12凭据相关配置检查）').replace('见Task2K证据','见Task2L证据')
text=text.replace('| H01 / P0遗留 | 其他旧数据库测试默认目标风险 |','| H01 / P0遗留已处理 | 旧默认入口已guard，完整测试分类仍F06 |')
text=text.replace('清点KnowledgeSchemaTest等旧入口，连接/setup/cleanup之前拒绝原库；新隔离runner通过不等于所有测试直接运行安全；须单独白名单与验收','32默认站点统一显式专用目标，静态字段/迁移/子进程启动前拒绝原库；连接入口源审查，额外12旧DB类39项通过；不能据此称全部旧类已执行或完整CI完成')
text=text.replace('[最新Task2K工程证据](../evidence/rule-check-refactor/2026-10-07/r5-original-20261008-01/acceptance.md)','[最新Task2L工程证据](../evidence/rule-check-refactor/2026-10-07/h01-database-isolation-20261008-01/acceptance.md)')
backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2L/H01: complete — RED11/10及兼容/探针14/2，首GREEN13类50；最终{summary}一次42项增量审查。32默认站点/实际额外12旧DB类39项分开，无原库/模型/报告留出/部署/commit，生产main保护。\n')
 for ruling in resolution.get('rulings',[]):f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution.get('deferredMinors',[]):f.write('\nFinal: minor (deferred): '+minor+'\n')
 for fixed in resolution.get('fixed',[]):f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n接续A06原文HTTP服务端引用绑定与A07最终外发；真实资料B/模型A05后段/C/页面及用户G2保持OPEN。\n')
after=[];diff=[]
for row in before['files']:
 p=root/row['path'];data=p.read_bytes();after.append(dict(path=row['path'],sha256=sha(p)))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=total,classes=len(r['suites']),counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,unchanged=before['unchanged'],database=db,containerExited=True,legacyFallbacksRemoved=32,extraOldDatabaseClasses=12,extraOldDatabaseTests=39,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text(f'''# Task2L / H01：旧数据库测试默认目标隔离验收

2026-10-08。{stage}。

|解决的问题|实现|证据|
|---|---|---|
|直接运行旧测试可能回退原演示库|32处fallback改shared guarded getter|无DB RED11/10，缺目标/原库/非回环/扩展URL/非法端口拒绝；3代表性class init反例不调用生命周期|
|外层runner安全仍可能被直接迁移绕过|共享测试Flyway配置前guard|源检查固定三个既有测试库名，正向临时库V22；更窄archive/rulecheck/demo guard保持|
|改造误伤已有专用供应商测试库|补精确zhkuagent_e2e兼容|兼容/探针RED14/2；只调用target helper，不调用模型|
|子进程继承URL与父测试系统属性不同|孤立启动探针自包含archive guard在Application前运行|纯单元拒绝原库/其他库/扩展URL，合法archive通过；未称实际打包probe正向运行|
|测试数量被误当完整旧功能或产品质量|源入口清单与实际选类分开|32默认站点改造，额外12旧DB类39项执行；未运行供应商/报告留出|

{summary}0失败/异常/跳过；源码{len(sources)}与XML哈希复算、42项before/after、生产main全部文件和真实2025DRAFT/20确认参考/36题不变。第一GREEN13类50PASS。一次审查见review.md/review-resolution.json，所有故障原报告保留。连接入口清单是源审查，不代表各入口均已执行。

H01旧默认原库入口风险已处理。默认全量Maven缺显式目标将fail closed；完整纯/DB/HTTP/供应商CI分类仍F06，不宣称全仓CI绿。无原库写入/启动/迁移、真实模型、报告集评测、部署或commit；正式目录CLOSED、真实2025DRAFT不可见不变。A06/A07、B/C及浏览器/用户收益G2继续。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,sourceFiles=len(sources)),ensure_ascii=False))
