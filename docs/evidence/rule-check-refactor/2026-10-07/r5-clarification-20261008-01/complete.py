from pathlib import Path
import hashlib,json,difflib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists()
resolution=read(batch/'review-resolution.json');assert resolution['criticalRemaining']==resolution['importantRemaining']==0
assert (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-clarification-acceptance-20261008-01');directory=batch.parent/phase;r=read(directory/'results.json')
assert r['passed'] and r['exitCode']==0 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited'] and not r['missingXml']
counts={'unit':0,'database':0,'credentialConfiguration':0};classes={k:0 for k in counts}
for suite in r['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256']
 actual=ET.parse(xml).getroot();assert all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped']) and int(actual.get('tests'))==suite['tests']
 name=suite['name'].split('.')[-1]
 group='database' if name in {'JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest','RuleCheckPreviewHttpTest'} else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert sum(counts.values())==r['testCount'] and r['testCount']>=355 and len(r['suites'])==30
sources=read(directory/'source-after.json');assert all(sha(root/x['path'])==x['sha256'] for x in sources)
before=read(batch/'source-before.json');assert all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22'
assert not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
fix_red=read(batch.parent/'r5-clarification-review-red-20261008-01'/'results.json')
assert not fix_red['passed'] and fix_red['testCount']==27 and sum(s['failures'] for s in fix_red['suites'])==1
assert sum(s['errors']+s['skipped'] for s in fix_red['suites'])==0 and fix_red['sourceDrift']==0 and fix_red['resourcesVerified']
for name,tests,failures in [('r5-clarification-red-20261008-01',32,13),('r5-clarification-db-red-20261008-01',24,2),('r5-clarification-generation-red-20261008-01',10,3),('r5-clarification-api-red-20261008-01',15,4),('r5-clarification-http-red-20261008-01',12,4)]:
 red=read(batch.parent/name/'results.json');assert not red['passed'] and red['testCount']==tests
 assert sum(s['failures'] for s in red['suites'])==failures and sum(s['errors']+s['skipped'] for s in red['suites'])==0 and red['sourceDrift']==0 and red['resourcesVerified']
resolution['verificationStatus']='VERIFIED_FINAL_GREEN';write(batch/'review-resolution.json',resolution)
stage='NECESSARY_CLARIFICATION_HTTP_VERIFIED_CATALOGUE_CLOSED';total=int(r['testCount'])
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(f'''
## 2026-10-08 Task2I / A04 必要澄清上下文

服务端绑定原问题、规则全文审核快照、运行settings、明确条件及待问项；续答只补一个允许值，不重选规则，每轮重新取证/审计/决策，规则变化/退役与来源撤回拒绝。每Session仅一个slot，无完整历史/回复缓存；5分钟按每轮开始计时、错误尝试不延长；token一次消费、全actor快照、generation保护旧慢请求不能覆盖/删除新问题。HTTP返回合并USER_DECLARED、审核问题范围/声明条件/事实when；过期/被替代/身份变化409，错误输入400供更正，异常503不回显cause。

最终30类{total}项（{counts['unit']}纯单元+{counts['database']}真库含12HTTP+12凭据配置）、29合同；无卷55464 V22连接0/容器退出。4新HTTP协议测试使用真实socket/数据库身份及测试-only合成service，非完整政策正向链；24真库审计类另验证fresh续答及来源撤回。真实2025仍DRAFT/不可见、正式目录CLOSED，A05/A06/A07/A08–A10与B/C/G2继续OPEN。[证据](../evidence/rule-check-refactor/2026-10-07/r5-clarification-20261008-01/acceptance.md)。
''')
contract_path=root/'docs/product/rule-check-decision-contract.md'
text=contract_path.read_text(encoding='utf-8')+'''
## 必要澄清续答合同（Task2I，2026-10-08）

同一POST的首次请求保留question/confirmedConditions两字段。NEED_CLARIFICATION才发clarificationToken；续答加该可选字段，必须canonical UUID文本、原问题完全一致、confirmedConditions仅本轮待问key及一个allowedValue，不能回传ruleId、旧可信条件、审核或证据。服务端合并此前明确值并标记USER_DECLARED；回复增加审核目录scope（ruleId/questionScope/declaredConditions/factPreconditions）以及每个主事实when，不把固定知识scope当个人适用，不自造独立例外。

每登录HttpSession只保存一个必要上下文。绑定原问题/已审核CatalogEntry全文/settings/待问条件与TrustedActor全快照；5分钟按本轮开始绝对计时，错误尝试不延长，合法续答开启下一轮。新问题替换旧slot；nonce一次消费且交接下一generation在同session锁内；慢旧请求finish/异常清理必须匹配generation，不能覆盖或删新问题。失效/过期/换账号/revision变化/已消费/被替代返回固定409及no-store，要求重新查证；错误delta/不同问题400仍可更正；服务异常503。网络丢失返回后从新问题重建，不承诺咨询历史恢复或自动重试安全。

续答跳过选择器但重新复核目录/settings、重新检索/捕获/审计/当前核验与决策；不缓存旧正文证据。缺资料不构造续答，已选规则变化/退役拒绝。此工程协议不证明自然语言选题语义、个人学籍、真实例外完整或最终HTTP外发原子性；最终身份/资料复核仍A07。正式目录CLOSED、真实2025草稿保持关闭。
'''
contract_path.write_text(text,encoding='utf-8')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';text=plan.read_text(encoding='utf-8');a=text.index('## Task2I / A04：')
plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';text=backlog.read_text(encoding='utf-8')
text=text.replace('卡点表和Task2H最终证据','卡点表和Task2I最终证据').replace('29类327项（244纯单元+71专用临时库含8实际HTTP+12凭据相关配置检查）',f'30类{total}项（{counts["unit"]}纯单元+{counts["database"]}专用临时库含12实际HTTP+12凭据相关配置检查）').replace('见Task2H证据','见Task2I证据')
text=text.replace('跨轮/具体范围/例外/最终下发及原文定位','真实具体范围/例外审核、最终下发及原文定位')
text=text.replace('| A04 | 多轮必要澄清 |','| A04 | 多轮必要澄清（工程完成，真实规则与浏览器待B/A10） |')
text=text.replace('1. **下一批A01**，之后A02/A03；合成工程不等待资料放行。','1. A01身份、A02独立启动、A03严格接口及A04必要澄清工程已验证；接续A05有界选题/A06定位/A07最终边界，A02普通终端/代理后段与遗留H01按依赖推进。')
text=text.replace('[最新Task2E工程证据](../evidence/rule-check-refactor/2026-10-07/r5-application-20261008-01/acceptance.md)','[最新Task2I工程证据](../evidence/rule-check-refactor/2026-10-07/r5-clarification-20261008-01/acceptance.md)')
backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2I/A04: complete — 核心RED32/13、真库24/2、generation10/3、API15/4、HTTP12/4，0异常/跳过→定向GREEN163；最终30类{total}项({counts["unit"]}纯单元+{counts["database"]}真库含12HTTP+12凭据配置)、29合同，无卷V22/连接0/容器退出；r5-clarification-20261008-01。一次独立增量审查，未commit/部署/原库/模型。\n')
 for ruling in resolution['rulings']:f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution['deferredMinors']:f.write('\nFinal: minor (deferred): '+minor+'\n')
 for fixed in resolution.get('fixed',[]):f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n接续有界自然语言选题A05；原文A06/最终下发A07/页面浏览器与真实B/C/G2保持OPEN。\n')
after=[];diff=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=sha(root/row['path'])))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=total,classes=len(r['suites']),counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,unchanged=before['unchanged'],database=db,containerExited=True,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text(f'''# R5 Task2I / A04：必要澄清验收

2026-10-08。{stage}。

|解决的问题|实现|验证|
|---|---|---|
|追问后条件与规则可能漂移|服务端绑定原问题/目录全文/settings/当前待问；只补一个允许值|规则变化/退役/settings变化拒绝；分支不重复追问，错误键/值/已声明值拒绝|
|续答可能沿用撤回前的旧引用|跳过选题但重新检索/捕获/审计/决策|实际临时库新审计2轮、selector只1轮；ARCHIVED来源后缺证、无事实/续答|
|token可重放、串账号、期限滑动|每Session单slot、随机一次nonce、全actor绑定、本轮绝对5分钟|store到期边界、role/revision；真实HTTP换账号、受控clock300秒、重复消费409|
|较早慢请求覆盖或误删新问题|begin先占generation；consume交接原子锁；finish及异常清理只匹配本轮|10项store含三并发合同；真实socket并发旧请求409、新问题可续答|
|条件、范围与事实不透明|用户声明合并USER_DECLARED；审核scope/条件与主事实when|15接口投影/续答；不自造结构化例外、不证明个人学籍|

有效RED：32/13、24/2、10/3、15/4、HTTP12/4，0异常/跳过；core GREEN88、generation GREEN35、定向GREEN163、最终30类{total}项，{counts['unit']}纯单元+{counts['database']}真库含12HTTP+12凭据配置。29计数替身runner合同另计。无卷55464/18既有迁移至V22/连接0/容器退出；每HTTP夹具监听与身份线程退出。源码{len(sources)}及XMLhash复算；16项before/after，真实DRAFT/20参考/36题/YAML/18迁移/旧安全与配置保护不变。

4新增HTTP用真实数据库身份/socket/cookie及测试-only Primary合成service/clock，证明续答协议而非完整JDBC政策正向质量；真实核心数据库续答/撤回由独立审计类验证。模拟clock到期不称真实等待5分钟。每合法续答开启下一轮TTL；错误尝试不延长本轮。每Session仅一个slot，新问题替代旧问题、丢失响应需要重新查证，不提供完整咨询历史。

一次增量审查见review.md/review-resolution.json；I1第17项无法合法续答，实际RED27/1后单次修复，最终完整相关GREEN；已有空白第16项可补填保留。A04工程完成，真实2025仍DRAFT/不可见、正式目录CLOSED；真实语义/例外/展示B组、模型A05/C、原文A06、最终身份/资料再核验及窗口A07、页面/浏览器A08–A10/G2仍OPEN。不是供应商效果/全校准确率/学生收益。既有正文UTF8与runner配置-only误报延期不由本批关闭。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,migration=db['migration'],sourceFiles=len(sources)),ensure_ascii=False))
