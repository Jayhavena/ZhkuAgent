from pathlib import Path
import hashlib,json,difflib,re
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists();before=read(batch/'source-before.json');resolution=read(batch/'review-resolution.json')
assert resolution['criticalRemaining']==resolution['importantRemaining']==0 and (batch/'review.md').exists()
assert all(sha(root/p)==h for p,h in before['unchanged'].items())
full=(batch/'review-final-green.log').read_text(encoding='utf-8-sig')
for key,count in [('tests',69),('pass',69),('fail',0),('cancelled',0),('skipped',0)]:assert re.search(rf'^# {key} {count}$',full,re.M)
red=(batch/'red.log').read_text(encoding='utf-8-sig');assert 'tests 17' in red and 'fail 17' in red and 'cancelled 0' in red
guard=(batch/'guard-red.log').read_text(encoding='utf-8-sig');assert '# tests 12' in guard and '# fail 2' in guard
review=(batch/'review-red.log').read_text(encoding='utf-8-sig');assert '# tests 16' in review and '# fail 3' in review and '# cancelled 0' in review
assert 'built in' in (batch/'review-final-build.log').read_text(encoding='utf-8-sig')
backend=read(batch.parent/'r5-original-http-review-final-20261008-01/results.json');assert backend['passed'] and backend['testCount']==501
for row in read(batch.parent/'r5-original-http-review-final-20261008-01/source-after.json'):assert sha(root/row['path'])==row['sha256']
stage='STUDENT_V2_CLIENT_WORKFLOW_VERIFIED_NOT_PAGE_OR_BROWSER_ASSEMBLED'
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 学生v2客户端与本地状态（Task3A，2026-10-08）

独立ruleCheckSession复用已有Session工厂，不混用v1身份或submission。只提供v2 auth/查证/原文，同源cookie、no-store、CSRF；客户端仅提交自然语言、显式条件及可选server clarificationToken。必要澄清逐轮只传当前awaited key的allowed value，不猜其他条件。

身份epoch、问题round和Workflow本地generation丢弃旧成功/错误/原文；查询的前置CSRF也绑定round，错误token/结构请求先拒不替换正确轮次。当前HTTP401在解析正文前按身份失效清本地，即使空/HTML；旧epoch/round401不得清新身份。login/logout串行，公开me在转换时拒绝，login完成用私有读取；me核对完整actor变化时清旧内容/CSRF。跨tab仅me/当前401检测，不承诺即时同步或浏览器Set-Cookie网络顺序原子性。

五状态/revision/USER_DECLARED/非空依据/UTF16及原文跨度、pageKind/HTTPS无userinfo/端口结构校验，只证明消息结构，不证明语义、相关性或资料资格。网络JSON仍整体读取后验证，不声称下载字节/内存硬上限；可展示条目/文本上限可能比未来审核模板严格，真实B/C联调需校准。Workflow失败/新题/身份变化/离开清旧正文/原文；网络结果未确认、不自动重试或宣称教务受理。本批仅Node实际import测试，无页面/路由/代理/浏览器装配。
''')
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write('''
## 2026-10-08 Task3A：v2学生客户端与状态准备

独立客户端和本地状态工程完成：新增23+既有46=69项Node测试通过、零失败/取消/跳过，现有Vue构建成功（尚未import新页面，不能称新页面bundle/真实浏览器）。有效初始17/17RED；补401与Unicode12/2RED；独立审查三项16/3RED→69GREEN，见[证据](../evidence/rule-check-refactor/2026-10-07/r5-student-client-20261008-01/acceptance.md)。修复当前空/HTML401不清主体、退出期间me复活旧actor、旧问题CSRF迟到401伤新题三条时序。Unicode反例初始引文失败后同一测试context断言未执行；最终GREEN两者执行，不冒称独立context RED。

A08页面/A09旧v1 RC-11实际文案/A10浏览器代理及普通终端仍OPEN；API只工程合同，跨tab即时同步/网络JSON下载硬上限未实现，真实规则字段与范围/例外仍B/C。后端501项对应Task2O代码保护不变，无DB/供应商/原库操作。本轮未重跑Java，不能称新增501测试执行。

继续A07观察入口CurrentIdentityFilter延迟失败的潜在会话窗口：旧请求许可查询失败后该既有过滤器可能无条件invalidate/clearCookie；Task2O验证的是Controller开始/决定/原文窗口，没有全面验证入口失败发生于新登录之后的顺序。仅静态风险、尚未动态复现，不与已修I1混淆；后续最小反例决定修复，不将顺序check称全链原子保证。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';t=plan.read_text(encoding='utf-8');a=t.index('## Task3A / A08');plan.write_text(t[:a]+t[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';t=backlog.read_text(encoding='utf-8')
t=t.replace('| A08 | 学生查证页面与产品入口 |','| A08 | 学生查证页面与产品入口（v2客户端/状态已验收，页面待续） |')
t=t.replace('| A09 | 页面状态与兼容修复 |','| A09 | 页面状态与兼容修复（身份/问题迟到状态已验收，旧v1文案/浏览器待续） |')
t=t.replace('；默认关闭目录接口通过不等于真实学生查证已交付。','；另Task3A前端69项通过，尚未新页面装配；默认关闭目录接口通过不等于真实学生查证已交付。')
backlog.write_text(t,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write('\nTask3A: complete — RED17/17→GREEN17；401/Unicode12/2RED→Node65GREEN；一轮审查I1/I2/I3有效16/3RED→全Node69/69、0cancel/skip、现有Vue构建；保护Java/v1/真实DRAFT/已确认参考不变。阶段CLIENT_WORKFLOW_ONLY；新module尚未bundle装配，不称浏览器。\n')
 for ruling in resolution['rulings']:f.write('\nFinal: Ruling: '+ruling+'\n')
 for fixed in resolution['fixed']:f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n接续Task3B页面/首页入口，Task3C真实浏览器/代理；A07入口迟到失败先记静态风险，最小复现另批；B/C/G2继续OPEN。\n')
after=[];diff=[]
for row in before['files']:
 p=root/row['path'];data=p.read_bytes();after.append(dict(path=row['path'],sha256=sha(p)))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,nodeTests=69,newTests=23,legacyTests=46,failures=0,cancelled=0,skipped=0,buildPassed=True,pageIntegrated=False,browserVerified=False,backendChanged=False,backendLastTests=501,backendRerun=False,databaseCalled=False,providerCalled=False,studentVisible=False,qualityGatePassed=False,review=resolution))
(batch/'acceptance.md').write_text('''# Task3A：学生v2客户端与问题状态

2026-10-08，STUDENT_V2_CLIENT_WORKFLOW_VERIFIED_NOT_PAGE_OR_BROWSER_ASSEMBLED。

|解决的问题|实现|验证|
|---|---|---|
|旧学分登录/任务与查证混用|独立Session实例、只读v2 auth/查证/原文|独立路径/同源/CSRF/登录续验，不动v1|
|旧问题/身份的迟到成功和401伤新显示|身份epoch+问题round+本地generation；CSRF前置也归本题|忽略Abort的旧成功/401/原文丢弃；迟到前置JSON/HTML401保住新题|
|退出后me迟到复活主体|公开me与身份转换互斥、登录内部私有读取|logout pending→me→退出成功→迟到成功被挡|
|非JSON401当解析失败留下旧身份|先确认当前轮次再按HTTP401清主体|当前空/HTML401清actor+CSRF；旧epoch非JSON401不清新主体|
|伪状态/无依据/坏跨度显示成查证|五状态/revision/声明字段/非空依据/Unicode/span/pageKind/HTTPS结构|合法五状态通过，矛盾结构/无依据/半Unicode/坏context/不安全链接拒绝|
|网络失败重发或旧原文残留|Workflow当前问题、必要allowed delta、错误/新题/身份/离开清旧内容|未知结果未确认、调用一次；原文失败无旧正文，pending与业务状态分开|

初始可运行stub RED17/17、0cancel/skip，无import失败；GREEN17。补current401/半Unicode有效RED12/2→GREEN65；Unicode测试第一次失败的quote断言阻止context断言，该context反例在最终GREEN执行，不冒称独立RED。一次独立审查I1/I2/I3有效RED16/3→最终全Node69/69（新增23+既有46）、0fail/cancel/skip。原审查65和最终69各日志保留，无二次审查。旧Vue构建成功，新module尚无页面import，不称页面bundle或浏览器通过。10项before/after及diff/hash、保护Java/v1/真实DRAFT/已确认参考不变，后端501为上一批真实已验收，不在本批重跑。

格式不证明事实相关、完整或现行资格；网络JSON整体读后校验，不保证下载字节/内存上限；跨tab只me/当前401检测，未解决浏览器Cookie网络顺序原子性。默认CLOSED/真实不可见/B/C/G2不变。A08页面/A09旧v1文案/A10浏览器及普通终端继续；无新依赖、DB/供应商/原库、commit/push/deploy。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],backendProtected=True))
print('Task3A complete: 69 Node tests; protected Java/legacy frontend unchanged; page/browser pending')
