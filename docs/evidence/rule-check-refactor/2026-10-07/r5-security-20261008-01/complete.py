from pathlib import Path
import hashlib,json,difflib
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists()
resolution=read(batch/'review-resolution.json')
assert resolution['criticalRemaining']==resolution['importantRemaining']==0
assert (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-security-acceptance-20261008-01');directory=batch.parent/phase
result=read(directory/'results.json')
assert result['passed'] and result['exitCode']==0 and result['sourceDrift']==0
assert result['resourcesVerified'] and result['containerExited'] and not result['missingXml']
counts={'unit':0,'database':0,'credentialConfiguration':0};classes={k:0 for k in counts}
db_names={'JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest'}
for suite in result['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256']
 actual=ET.parse(xml).getroot();assert all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped'])
 assert int(actual.get('tests'))==suite['tests']
 name=suite['name'].split('.')[-1]
 group='database' if name in db_names else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert counts=={'unit':223,'database':63,'credentialConfiguration':12}
assert result['testCount']==298 and len(result['suites'])==26
sources=read(directory/'source-after.json');assert all(sha(root/r['path'])==r['sha256'] for r in sources)
before=read(batch/'source-before.json');assert all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22'
assert not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
red=read(batch.parent/'r5-security-red-20261008-03'/'results.json')
assert not red['passed'] and red['testCount']==14 and sum(s['failures'] for s in red['suites'])==13
assert sum(s['errors']+s['skipped'] for s in red['suites'])==0 and red['sourceDrift']==0 and red['resourcesVerified']
stage='AUTH_FILTER_CHAIN_VERIFIED_NOT_APPLICATION_ASSEMBLED'
note='''
## 2026-10-08 R5 Task2F / A01身份过滤链验收

解决v1 cookie固定路径不能供v2使用的缺口：采用独立zhku-rule-check-preview profile、v2 cookie与路径，复用身份Provider/当前身份校验，不扩大v1凭据范围。新入口只允许csrf/login/me/logout和USER查证POST；角色、自报身份、错误方法/路径、CSRF、撤销/故障、会话更新及旧token均有反例。守卫在资源初始化前要求显式会话配置、回环地址、独立profile和开启开关；默认不提供启动入口。

26类298项通过：223纯单元、63真库、12凭据相关配置检查；29启动器合同。临时无卷库V22、连接0、容器退出。阶段AUTH_FILTER_CHAIN_VERIFIED_NOT_APPLICATION_ASSEMBLED，身份仓储替身不等于真实HTTP/cookie创建或数据库登录；A02将继续独立启动验收，A03业务接口尚未装配。B-01/B-02、真实2025展示资格、旧自由RAG语义风险保持OPEN，不将局部工程验收解释为G2通过。详见[Task2F证据](../evidence/rule-check-refactor/2026-10-07/r5-security-20261008-01/acceptance.md)。
'''
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(note)
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 独立v2身份边界（2026-10-08）

新zhku-rule-check-preview独立profile/应用上下文；/api/zhku/controlled-preview/v2固定ZHKU_RULE_CHECK_SESSION cookie，15分钟闲置、内存、HttpOnly、SameSite Strict，仅本机HTTP；旧v1 cookie不扩宽，不提供SSO。四个auth路径之外只授权USER查证POST，MAINTAINER无角色继承；客户端身份头不可信。CSRF先于当前身份读取，登录换session ID并刷新token；撤销或revision不符清新cookie/session，身份库故障503且不输出事实。安全环境缺实际cookie配置或混用profile/匿名/治理/Agent入口时拒绝，不能靠守卫默认值补齐。

Task2F为真实安全过滤链与身份仓储替身工程验收；暂无独立启动Application/实际cookie创建/业务Controller。A02/A03仍待完成，真实规则及资料许可不由身份工程验收授权。证据见r5-security-20261008-01。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md'
text=plan.read_text(encoding='utf-8');a=text.index('## Task2F / A01：')
plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md'
text=backlog.read_text(encoding='utf-8')
text=text.replace('核对日期：2026-10-08；基于主路线图、R5任务计划、执行账本、卡点表和Task2E最终证据。','核对日期：2026-10-08；基于主路线图、R5任务计划、执行账本、卡点表和Task2F最终证据。')
text=text.replace('最新相关验收为23类263项（200单元+63专用临时库）及29项启动器合同；这是已有执行记录，本次整理没有重跑测试。','最新相关验收为26类298项（223纯单元+63专用临时库+12凭据相关配置检查）及29项启动器合同，见Task2F证据；新v2过滤链通过不等于真实HTTP已交付。')
text=text.replace('| A01 | v2身份、会话与CSRF边界 |','| A01 | v2身份、会话与CSRF边界（过滤链已完成，实际HTTP待A02） |')
text=text.replace('Codex可先设计并实现，不暗中扩大旧cookie路径','框架14项与旧v1 9项通过；独立cookie，不扩大旧路径；实际HTTP/cookie/临时库认证由A02验收')
backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write('\nTask2F/A01: complete — 有效RED14项13断言失败/0异常/0跳过→GREEN；最终26类298项(223纯单元+63真库+12凭据配置)、29合同；V22连接0无卷容器退出；一次独立增量审查，证据r5-security-20261008-01。首轮lambda编译失败及旧配置Unit凭据失败保留且不计有效RED；未commit/部署/原库/供应商调用。\n')
 for ruling in resolution['rulings']:f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution['deferredMinors']:f.write('\nFinal: minor (deferred): '+minor+'\n')
 f.write('\n接续A02独立启动/真实HTTP/cookie/临时库认证；A03业务接口、资料许可与G2仍未完成。\n')
after=[];diff=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=sha(root/row['path'])))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=result['testCount'],classes=len(result['suites']),counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,unchanged=before['unchanged'],database=db,containerExited=True,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text('''# R5 Task2F / A01：独立v2身份过滤链验收

2026-10-08。AUTH_FILTER_CHAIN_VERIFIED_NOT_APPLICATION_ASSEMBLED。

|解决的问题|实现|验证|
|---|---|---|
|v1 cookie范围不覆盖v2，直接扩宽会混用身份入口|独立profile与ZHKU_RULE_CHECK_SESSION固定v2路径，共享Provider与当前身份实现|新14项、旧v1 9项；只清对应cookie，旧路径/方法拒绝|
|客户端自报身份或维护者可能越权|可信PreviewPrincipal与当前身份库；USER无角色继承|身份头、外来同名USER、MAINTAINER拒绝，停用/revision变化拒绝|
|登录/注销及CSRF状态容易混淆|框架sessionFixation/CSRF；CSRF先于当前身份|真实会话token登录后刷新，旧token403，新token200；登出session失效|
|守卫的默认配置可能掩盖实际cookie配置缺失|显式cookie、独立profile、回环绑定与15分钟配置；禁止其他模式|缺设置、混合入口、域/持久化等反例；失败先于身份Bean初始化|
|配置回归需要process-only数据库凭据但被归类纯单元|移入凭据/专用临时库验收，不弱化生产守卫|12项配置检查通过；不全称真库查询，H01仍OPEN|

最终26类298项通过：22类223纯单元、3类63实际真库、1类12凭据配置检查；0失败/异常/跳过。29启动器合同为计数替身。有效RED14项13断言失败；首轮test lambda编译失败、首次GREEN旧配置因移除凭据失败均保存，不计有效RED。源码与XML指纹复算、无卷临时库V22、连接0、随机容器退出。

独立审查见review.md/review-resolution.json；逐条裁定与暂缓项记录执行账本。该批没有独立Application、DataSource装配或查证业务Controller，sentinel仅检验授权；仓储替身不证明真实HTTP/cookie创建/数据库认证。A02继续完成这些环节。真实2025草稿、20题人工参考、36题原件、旧controlled配置和18迁移哈希不变；B-01/B-02、完整R5/G2、模型语义与用户收益均未通过。

引用文档：版本固定Security6.4.4，Context7核对CSRF/sessionFixation/logout/MockMvc；未升级依赖。[CSRF官方文档](https://github.com/spring-projects/spring-security/blob/6.4.4/docs/modules/ROOT/pages/servlet/exploits/csrf.adoc)。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,migration=db['migration'],sourceFiles=len(sources)),ensure_ascii=False))
