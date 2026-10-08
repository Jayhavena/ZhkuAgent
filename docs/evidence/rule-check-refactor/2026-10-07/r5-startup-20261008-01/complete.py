from pathlib import Path
import hashlib,json,difflib
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists()
resolution=read(batch/'review-resolution.json');assert resolution['criticalRemaining']==resolution['importantRemaining']==0
assert (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-startup-acceptance-20261008-01');directory=batch.parent/phase;result=read(directory/'results.json')
assert result['passed'] and result['exitCode']==0 and result['sourceDrift']==0 and result['resourcesVerified'] and result['containerExited'] and not result['missingXml']
counts={'unit':0,'database':0,'credentialConfiguration':0};classes={k:0 for k in counts}
db_names={'JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest','RuleCheckPreviewHttpTest'}
for suite in result['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256']
 actual=ET.parse(xml).getroot();assert all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped']) and int(actual.get('tests'))==suite['tests']
 name=suite['name'].split('.')[-1];group='database' if name in db_names else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert counts=={'unit':233,'database':66,'credentialConfiguration':12}
assert result['testCount']==311 and len(result['suites'])==28
sources=read(directory/'source-after.json');assert all(sha(root/r['path'])==r['sha256'] for r in sources)
before=read(batch/'source-before.json');assert all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22'
assert not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
for name,tests,failures in [('r5-startup-red-20261008-01',10,9),('r5-startup-http-red-20261008-01',3,3)]:
 red=read(batch.parent/name/'results.json');assert not red['passed'] and red['testCount']==tests
 assert sum(s['failures'] for s in red['suites'])==failures and sum(s['errors']+s['skipped'] for s in red['suites'])==0
 assert red['sourceDrift']==0 and red['resourcesVerified']
stage='CLOSED_CATALOGUE_HTTP_AUTH_VERIFIED_NOT_RULE_CHECK_API'
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write('''
## 2026-10-08 Task2G / A02第一段启动及实际身份HTTP验收

独立Application显式Import，无旧匿名/治理/Agent扫描；入口/初始化默认关闭。配置守卫复用A01，必须显式专用e2e回环非54329目标、process-only凭据、迁移同意和固定运行配置；一个DataSource装配迁移、身份与内部应用工厂。目录固定CLOSED，未审真实规则不外发。

实际socket+临时库合成账号验证Cookie name/path/HttpOnly/SameSite、会话ID/token轮换、旧token失效、logout、数据库revision撤销/换账号、v1 cookie拒绝和维护者403；监听与身份线程退出。最终28类311项（233纯单元+66真库含3 HTTP+12凭据配置）、29合同，无卷V22连接0容器退出。阶段CLOSED_CATALOGUE_HTTP_AUTH_VERIFIED_NOT_RULE_CHECK_API；仅启动/认证工程链，不代表真实资料展示或学生效果。普通终端启动器、代理、业务接口/页面还未完成，A02整体不勾选。B-01/B-02/G2保持OPEN。[证据](../evidence/rule-check-refactor/2026-10-07/r5-startup-20261008-01/acceptance.md)。
''')
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 默认关闭的独立应用装配（2026-10-08）

RuleCheckPreviewApplication只Import新配置、v2安全链与AuthController，不自动扫描。main initializer及直接配置BFPP/数据源都要求安全环境合格；profile YAML enabled/initialize默认false。当前只支持显式专用无卷验收库zhkuagent_rulecheck_e2e、127.0.0.1非54329，无URL参数/凭据，密码仅进程环境；取得密码不授权原库迁移。显式initialize才执行18份既有迁移至V22，DataSource/Flyway/身份/RuntimeFactory共享目标，自动Flyway关闭。

运行配置固定检索profile/切片/scope/2025.9.2与预算；目录固定为空，不接受客户端或配置伪造审核规则；选择/embedding外发拒绝。HTTP认证有实际合成临时库证据，业务Controller未装配。正式规则/展示及供应商适配须后续独立任务，不通过打开开关自动获得。A02普通用户终端与同源代理尚未交付。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';text=plan.read_text(encoding='utf-8');a=text.index('## Task2G / A02第一段：')
plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';text=backlog.read_text(encoding='utf-8')
text=text.replace('卡点表和Task2F最终证据','卡点表和Task2G最终证据')
text=text.replace('26类298项（223纯单元+63专用临时库+12凭据相关配置检查）','28类311项（233纯单元+66专用临时库含3实际HTTP+12凭据相关配置检查）')
text=text.replace('见Task2F证据；新v2过滤链通过不等于真实HTTP已交付','见Task2G证据；实际认证HTTP通过不等于真实学生查证接口已交付')
text=text.replace('（过滤链已完成，实际HTTP待A02）','（过滤链及实际HTTP认证已验证，浏览器待A10）')
text=text.replace('实际HTTP/cookie/临时库认证由A02验收','实际HTTP/cookie/临时库认证3项通过；浏览器流程待A10')
text=text.replace('| A02 | 受控启动与正式应用装配 |','| A02 | 受控启动与正式应用装配（独立启动/认证完成，终端/真实规则装配待续） |')
backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write('\nTask2G/A02第一段: complete — 配置RED10/9、HTTP RED3/3，均0异常/跳过→定向GREEN48；最终28类311项(233纯单元+66真库含3HTTP+12凭据配置)、29合同；临时无卷V22连接0/容器退出，HTTP监听/身份线程退出。一次独立增量审查；r5-startup-20261008-01。无commit/部署/原库/供应商调用。\n')
 for ruling in resolution['rulings']:f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution['deferredMinors']:f.write('\nFinal: minor (deferred): '+minor+'\n')
 for fixed in resolution.get('fixed',[]):f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n接续A03严格请求响应与业务接口；A02终端/真实规则、A06原文、A08页面与G2仍待完成。\n')
after=[];diff=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=sha(root/row['path'])))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=result['testCount'],classes=len(result['suites']),counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,unchanged=before['unchanged'],database=db,containerExited=True,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text('''# R5 Task2G / A02第一段：独立启动及实际HTTP身份验收

2026-10-08。CLOSED_CATALOGUE_HTTP_AUTH_VERIFIED_NOT_RULE_CHECK_API。

|解决的问题|实现|验证|
|---|---|---|
|过滤链已通过但没有独立应用|仅显式Import新配置/安全/Auth；新profile默认关闭|实际Boot/嵌入式监听，旧Controller/配置不存在，旧路由拒绝|
|配置错误可能先碰原库或其他入口|主initializer及BFPP/数据源守卫，精确专用e2e目标、单DataSource/Flyway/身份/工厂|目标、凭据来源、开关、预算反例；无卷55462，不连接原库|
|框架替身不能证明真实cookie和数据库身份|合成账号BCrypt/JDBC + 实际HTTP|name/path/HttpOnly/SameSite、会话/CSRF轮换、logout、revision撤销、换账号/v1 cookie拒绝/维护者403|
|未审真实规则可能因启动自动放行|CLOSED目录固定空，内部应用返回缺证，选择/embedding禁用|无确认事实，无业务Controller，真实草稿/参考/资料资格不变|
|测试通过但资源仍驻留|夹具清本批UUID账号，关闭上下文并观察socket/线程|每次监听不可连接、身份closed/inFlight0/liveThreads0；临时库连接0与容器退出|

有效RED：配置10项9断言失败，HTTP3项3断言失败，均0异常/跳过；定向GREEN48；最终28类311项零失败/异常/跳过：233纯单元、66真库含3实际HTTP、12凭据配置。29runner合同为执行器替身，另计。18既有迁移至V22，XML/源码指纹复算，11条before/after范围保存；新迁移/依赖/升级/原库/供应商均无。

一次独立审查及处置见review.md/review-resolution.json。普通用户终端启动器、同源代理、业务查证Controller/页面、真实资料与规则放行仍未完成；A02整体、完整R5/G2不因此通过。真实HTTP身份结果不是自然语言效果、浏览器结果或用户收益。A01留下仅配置选类时runner迁移误判Minor仍记录，不影响本批含实际迁移的组合验收。

[Spring Boot 3.4显式Import官方文档](https://docs.spring.io/spring-boot/3.4/reference/using/using-the-springbootapplication-annotation.html)，Context7核对，实际依赖3.4.4未升级。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,migration=db['migration'],sourceFiles=len(sources)),ensure_ascii=False))
