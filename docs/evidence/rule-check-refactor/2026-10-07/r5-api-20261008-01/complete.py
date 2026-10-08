from pathlib import Path
import hashlib,json,difflib
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists()
resolution=read(batch/'review-resolution.json');assert resolution['criticalRemaining']==resolution['importantRemaining']==0
assert (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-api-acceptance-20261008-01');directory=batch.parent/phase;r=read(directory/'results.json')
assert r['passed'] and r['exitCode']==0 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited'] and not r['missingXml']
counts={'unit':0,'database':0,'credentialConfiguration':0};classes={k:0 for k in counts}
db_names={'JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest','RuleCheckPreviewHttpTest'}
for suite in r['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256']
 actual=ET.parse(xml).getroot();assert all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped']) and int(actual.get('tests'))==suite['tests']
 name=suite['name'].split('.')[-1];group='database' if name in db_names else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert sum(counts.values())==r['testCount'] and r['testCount']>=325 and len(r['suites'])==29
sources=read(directory/'source-after.json');assert all(sha(root/x['path'])==x['sha256'] for x in sources)
before=read(batch/'source-before.json');assert all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22'
assert not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
fix_red=read(batch.parent/'r5-api-review-red-20261008-01'/'results.json')
assert not fix_red['passed'] and fix_red['testCount']==8 and sum(s['failures'] for s in fix_red['suites'])==2
assert sum(s['errors']+s['skipped'] for s in fix_red['suites'])==0 and fix_red['sourceDrift']==0 and fix_red['resourcesVerified']
resolution['verificationStatus']='VERIFIED_FINAL_GREEN';write(batch/'review-resolution.json',resolution)
for name,tests,failures in [('r5-api-red-20261008-01',11,11),('r5-api-http-red-20261008-01',6,3)]:
 red=read(batch.parent/name/'results.json');assert not red['passed'] and red['testCount']==tests
 assert sum(s['failures'] for s in red['suites'])==failures and sum(s['errors']+s['skipped'] for s in red['suites'])==0 and red['sourceDrift']==0 and red['resourcesVerified']
stage='STRICT_JSON_HTTP_API_VERIFIED_CATALOGUE_CLOSED';total=int(r['testCount'])
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(f'''
## 2026-10-08 Task2H / A03第一段严格JSON查证接口

新Controller显式进入v2，只接受问题/显式条件，拒绝身份/审核/资格/快照字段；严格类型、必填、UTF-16边界及重复键/尾随JSON。五状态保留确认事实、摘录、澄清、未知与冲突；声明标记USER_DECLARED，固定知识scope不代表个人适用。媒体头守卫在CSRF前且不读body；实际32KiB限长在CSRF/当前身份/授权之后，保留旧v1默认16KiB。

实际HTTP验证CLOSED缺证与严格请求、角色/CSRF/415、固定长度及真正无Content-Length的chunked过大body413。最终29类{total}项（{counts['unit']}纯单元+{counts['database']}真库含8 HTTP+12凭据配置）、29合同，无卷55463 V22连接0/退出。五状态11项投影测试用服务替身，不是实际JDBC五状态或模型效果。I1实际编码路径曾200绕过大小，新增8/2有效RED后在新应用CSRF前拒绝全部编码路径，最终GREEN；M1正文UTF8尚未校验，媒体守卫仅约束charset声明，合同已收窄。A03第一段工程通过；具体规则范围/结构化例外、A04多轮、A06定位、A07最终下发、A08页面及B/C/G2仍OPEN。[证据](../evidence/rule-check-refactor/2026-10-07/r5-api-20261008-01/acceptance.md)。
''')
contract_path=root/'docs/product/rule-check-decision-contract.md'
text=contract_path.read_text(encoding='utf-8')
text=text.replace('2026-10-08。适用阶段：R5 Task2B独立工程组件，尚未装配自然语言入口、HTTP或学生页面。','2026-10-08。本文按阶段保留设计与验收记录；早期“尚无HTTP/Bean”是当时边界。最新Task2H已装配默认关闭目录的严格JSON接口，真实2025规则/展示、模型语义、页面及最终外发门禁仍未完成。')
text+='''
## 严格JSON查证接口（Task2H，2026-10-08）

POST /api/zhku/controlled-preview/v2/rule-check，仅question与confirmedConditions两项必填字段；question非空白至2048 UTF-16，条件至16项，键非空白至64/值文本至128。数字/布尔/null/数组不转换文本；额外可信字段、重复键与尾随JSON拒绝400。条件标记USER_DECLARED，仅用户声明，不证明个人学籍或资格。响应显式五状态、固定knowledgeScope/referenceRevision、声明条件、确认事实/核验摘录、澄清/未知/冲突；引用无内部checksum/qualified属性，不生成原文地址或虚构无例外说明。

application/json媒体头守卫只约束Content-Type及UTF-8 charset声明，先于CSRF且不读body，非JSON/不支持charset返回415；避免csrf读取表单参数提前解析业务正文。新应用所有原始路径含百分号时在CSRF前400，防止MVC解码后匹配而绕过按原路径判断的过滤器；此守卫不进入旧v1。CSRF→当前身份→USER授权→32KiB实际读取限长（最多32769字节），没有Content-Length仍413；旧v1默认16KiB与路径保留。输入400/应用异常503均固定消息、no-store、不回显cause。新profile目录仍CLOSED，合法实际请求仅返回缺证。实际正文严格UTF8验证未完成，Jackson可能识别其他Unicode编码，不能把声明检查称正文编码验证。

接口工程不证明合法规则ID的语义匹配、实际规则范围/例外完整、原文定位、跨轮上下文、最终外发原子性。A04/A06/A07及B02仍须独立验证；当前禁止真实规则激活，不把尚未完成的最终复查放任用于真实资料。
'''
contract_path.write_text(text,encoding='utf-8')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';text=plan.read_text(encoding='utf-8');a=text.index('## Task2H / A03第一段：')
plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';text=backlog.read_text(encoding='utf-8')
text=text.replace('卡点表和Task2G最终证据','卡点表和Task2H最终证据')
text=text.replace('28类311项（233纯单元+66专用临时库含3实际HTTP+12凭据相关配置检查）',f'29类{total}项（{counts["unit"]}纯单元+{counts["database"]}专用临时库含8实际HTTP+12凭据相关配置检查）')
text=text.replace('见Task2G证据；实际认证HTTP通过不等于真实学生查证接口已交付','见Task2H证据；默认关闭目录接口通过不等于真实学生查证已交付')
text=text.replace('真实2025规则绑定/语义审核/展示资格、v2身份与接口、学生页面','真实2025规则绑定/语义审核/展示资格、跨轮/具体范围/例外/最终下发及原文定位、学生页面')
text=text.replace('| A03 | v2请求、响应与接口 |','| A03 | v2请求、响应与接口（严格接口已验证，规则范围/例外待补） |')
backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2H/A03第一段: complete — Controller RED11/11、HTTP RED6/3，0异常/跳过→GREEN62；I1编码路径实际绕过RED8/2后单次修复；最终29类{total}项({counts["unit"]}纯单元+{counts["database"]}真库含8HTTP+12凭据配置)、29合同，无卷V22/连接0/容器退出；r5-api-20261008-01。一次独立增量审查，无commit/部署/原库/供应商。\n')
 f.write('\nTask2H: Ruling: 原HTTP维护者请求未刷新登录后的token可能只测到CSRF403，本批刷新后再发JSON测角色拒绝 — 保留旧结果但不把旧403作为角色因果证据；新用例与框架角色测试补足 — 代价：此前角色HTTP表述须按此校正。\n')
 for ruling in resolution['rulings']:f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution['deferredMinors']:f.write('\nFinal: minor (deferred): '+minor+'\n')
 for fixed in resolution.get('fixed',[]):f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n接续A04必要澄清绑定；A03具体规则范围/例外、A06/A07/A08及B/C/G2尚未完成。\n')
after=[];diff=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=sha(root/row['path'])))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=total,classes=len(r['suites']),counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,unchanged=before['unchanged'],database=db,containerExited=True,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text(f'''# R5 Task2H / A03第一段：严格JSON业务接口验收

2026-10-08。STRICT_JSON_HTTP_API_VERIFIED_CATALOGUE_CLOSED。

|解决的问题|实现|验证|
|---|---|---|
|未装配v2业务Controller|显式Import，复用内部service，不旁路身份|实际USER请求200且CLOSED缺证；CSRF403，维护者fresh token403|
|客户端可能伪造身份/审核/证据资格或隐式转换条件|仅两字段，明确JSON类型/必填/限长；重复/尾随拒绝|unknown trusted字段/null/标量/数量/UTF16反例；实际HTTP重复键与尾随400|
|各种判断状态与声明/事实易混淆|五状态投影，USER_DECLARED条件，知识scope/引用版本、事实摘录/澄清/未知/冲突|合成服务替身11项含五状态保留；不称实际真库五状态质量|
|表单CSRF解析可先读业务正文、缺Content-Length可能绕过限长|只读媒体声明头在CSRF前；授权之后实际最多32769字节，旧v1默认不变|非JSON/非UTF8声明415、媒体守卫不消耗正文、匿名大body401/维护者403；真实固定及无长度chunked413|
|编码普通字符被MVC解码但原URI过滤不匹配|新应用CSRF前拒绝全部百分号路径，包含编码前缀|实际大JSON编码路径曾200、表单曾403；RED8/2→修复→最终相关全套GREEN，编码路径400|
|错误原因可能外泄或被当政策结论|输入400/应用503固定消息与no-store|不回显输入/异常cause；正常响应也no-store|

有效RED：11项11断言失败、HTTP6项3断言失败，均0异常/跳过；定向GREEN62；I1实际HTTP8项2断言失败后单次修复。最终29类{total}项：{counts['unit']}纯单元、{counts['database']}真库含8 HTTP、12凭据配置。0失败/异常/跳过；29runner计数替身合同另计。无卷55463、18既有迁移至V22、连接0/容器退出；每个HTTP夹具socket与身份线程退出，只清本批UUID账号。源码{len(sources)}及XML哈希复算，16项before/after保存；真实DRAFT/20参考/36题/迁移及旧controlled配置不变。

独立审查及裁定见review.md/review-resolution.json。原审查With fixes保留，I1已实际RED→单次修复→最终GREEN，无二次审查。M1严格正文UTF8验证延期，最终注释/消息/合同只称媒体charset声明，不夸大保证。旧v1历史编码限长风险不由本批关闭。A03第一段通过，不整体关闭A03；具体规则范围/结构化条件例外A04/B02、原文定位A06、最终身份/资料再核验和窗口A07、页面A08及真实B/C/G2均未完成。新应用目录硬CLOSED，绝不开放真实规则补足未完安全边界；此结果不是供应商效果/浏览器结果/学生收益。原HTTP维护者POST曾未刷新token，可能只测CSRF403；本批刷新token改为JSON后验证角色403，旧结果保留且不作为角色因果证明。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,migration=db['migration'],sourceFiles=len(sources)),ensure_ascii=False))
