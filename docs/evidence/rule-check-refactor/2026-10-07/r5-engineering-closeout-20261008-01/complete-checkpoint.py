from pathlib import Path
import json, hashlib, difflib, re

root=Path(__file__).resolve().parents[5]; batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not (batch/'acceptance.md').exists()
v2=read(batch.parent/'r5-browser-live-20261008-05/results.json')
legacy=read(batch.parent/'r5-legacy-live-20261008-02/results.json')
red=read(batch.parent/'r5-browser-cleanup-red-20261008-01/results.json')
terminal=read(batch.parent/'r5-terminal-http-20261008-01/results.json')
ordinary=read(batch/'ordinary-terminal-http.json')
for result in [v2,legacy,terminal]:
    assert result['passed'] and result['resourcesVerified'] and result['sourceDrift']==0
assert terminal['testCount']==3 and red['testCount']==2 and red['suites'][0]['failures']==1 and red['suites'][0]['errors']==0
assert ordinary['passed'] and ordinary['parentPowerShell'].startswith('5.1.') and ordinary['ordinaryUserPath']
assert not ordinary['pwshOnOrdinaryPath'] and ordinary['collectedExistingHttp'] and not ordinary['actualHttpRunThisInvocation']
log=(batch/'frontend-final.log').read_text(encoding='utf-8-sig')
for key,n in [('tests',85),('pass',85),('fail',0),('cancelled',0),('skipped',0)]:assert re.search(rf'^# {key} {n}$',log,re.M)
assert read(batch/'runner-contract.json')['passed']
assert 'built in' in (batch/'build.log').read_text(encoding='utf-8-sig')
write(batch/'browser-matrix.json',dict(synthetic=True,providerCalled=False,
    v2Passed=['student-login','answered','original-quote-4','partial-with-unknown','necessary-clarification','continuation-shows-declared-condition','conflict-both-sides','original-quote-6-page2','withdraw-original-denied','withdraw-insufficient','logout-clears-view','student-B-empty-question-and-evidence','maintainer-no-student-form','edit-clears-old-answer-and-original','home-primary-entry','reload-session-empty-question','1280-width-no-overflow'],
    legacyHttpPassed=['login','submission-accepted','terminal-query','same-key-changed-content-409'],
    legacyBrowserPassed=False,legacyBrowserReason='CUA URL security policy rejected binding existing tab; no workaround attempted',
    ordinaryTerminal=dict(windowsPowerShell='5.1',userMachinePath=True,pwshOnPath=False,wrapperFallback=True,utf8Probe=True,actualHttpTests=3),
    userBrowserClosedAfterRetentionRequest=False,fullA10Passed=False))
write(batch/'review-resolution.json',dict(criticalRemaining=0,importantRemaining=0,minorDeferred=[],
    fixed=['I1 extra-chunk setup failure cleanup: actual second SQL FK rejection, RED2/1fail/0error -> related ordinary-terminal GREEN3/3'],
    noRereview=True, rulings=['旧v1浏览器工具策略阻断，HTTP与宿主退出不代替浏览器验收；A10继续OPEN',
    '真实资料/供应商/收益门禁不变；合成工程不能批准真实发布',
    '固定FixtureClock与宿主租约不证明墙钟TTL；保留既有Cookie交付窗口和发布非原子性限制',
    '普通终端HTTP已通过；首次结果采集因PS5.1默认GBK读UTF8文件失败，显式UTF8修正后只重采结果，不冒称重新运行DB']))
acceptance='''# 工程验收收口记录（旧v1浏览器一项受阻）

2026-10-08。v2合成浏览器矩阵、普通终端和本批审查修复已完成；完整A10继续OPEN，真实资料/G2不放行。

|解决的问题/验收目标|实际结果|证据|
|---|---|---|
|五状态只有组件测试、缺浏览器整链|实际Vue→Nginx→Java→专用临时库：回答/部分回答/必要澄清及续答/缺证/冲突；冲突两侧原文、记录页码及摘录高亮正确|live05和browser-matrix.json；浏览器操作由CUA完成，没有注入前端结果|
|撤回与身份切换容易保留旧内容|撤回后原文拒绝并清除正文、再次查证缺证；退出、学生B登录后空白问题/回答/原文；维护者无学生表单；编辑新题清旧依据；首页返回/刷新核对身份|本轮实际AX/DOM观察，1280宽无水平溢出|
|旧v1可能被新客户端破坏|实际应用/鉴权/派发、受理及完成查询、同键不同内容409通过；Nginx就绪|live02与terminal-http；浏览器工具拒绝接管保留页，旧v1页面动作未验证，不冒称通过|
|用户普通终端不能识别pwsh，中文编码边界不明|实际Windows PowerShell5.1，重建Machine/User PATH，pwshOnPath=false，CMD包装入口找到PS7；UTF8输入/输出/管道及中文样本正常；从该环境运行相关两类3项实际HTTP验收通过|ordinary-terminal.json、ordinary-terminal-http.json及terminal-http/results.json|
|额外片段初始化失败跳清理|真实第一INSERT成功，第二INSERT因无效run外键失败；有效RED2项/1fail/0error，首片段残留1；将初始化纳入finally后GREEN3项全通过，保留base source/chunk|browser-cleanup-red与terminal-http/XML；一次合并审查I1修复，不重审|

前端完整85/85，0fail/cancel/skip，实际构建成功；runner29合同通过，externalStarts是计数替身。没有重新跑无变更的503历史全套，测试数不累加成产品准确率。live05的JUnit1项包含HTTP预检与宿主结束，不是浏览器断言数；浏览器观察单列。

保留所有失败：live04新增夹具访问器编译错误；legacy-live01因Docker停止而未启动Maven/数据库，resources无法核验；恢复官方Docker后台服务后live02通过；前端首次沙箱realpath EPERM导致两个测试文件未运行，同集合正常权限重跑85通过；普通终端业务3项成功后，首次采集报告因PS5.1默认GBK读UTF8 JSON失败，明确-Encoding UTF8后仅重采既有报告通过，没有无意义重跑数据库。

一次fresh-context审查C0/I1/M0；I1真实SQL反例修复，当前C0/I0，无延期Minor。审查不判断的真实资料/语义/收益、墙钟TTL、Cookie网络窗口及发布原子性保留明确边界。两处旧文案和Java字段映射生产改动收齐；后续新Java全部test classpath，无新审批开关。

v2与旧v1专用Java/数据库/Nginx正常退出；数据仅随机无卷专用临时库，不写原演示库、不调用供应商。用户要求保留浏览器后没有主动关闭标签页/窗口。Docker后台恢复后不擅自退出用户的Docker应用。没有commit/push/deploy。

唯一尚未收齐的本轮页面验收：旧v1实际页面提交、完成刷新及换账号显示。浏览器工具以URL安全策略拒绝接管现有页，已告知并请求重新连接；不通过创建其他页面、shell浏览器自动化或其他表面绕过。恢复后续跑该项，完整A10才可决定关闭。
'''
(batch/'acceptance.md').write_text(acceptance,encoding='utf-8')
scope=read(batch.parent/'r5-browser-chain-20261008-01/source-before.json')
ext=read(batch.parent/'r5-browser-chain-20261008-01/scope-extension.json')['files']
old=read(batch.parent/'r5-legacy-copy-20261008-01/source-before.json')
allowed={r['path'] for r in scope['files']+ext+old['files']}|{'knowledge/src/test/java/com/zhku/knowledge/controlled/RuleCheckLegacyBrowserHttpTest.java'}
drift=[p for p,h in scope['unchanged'].items() if p not in allowed and sha(root/p)!=h]
assert not drift,drift
write(batch/'source-after.json',{p:sha(root/p) for p in sorted(allowed) if (root/p).exists()})
write(batch/'protected-check.json',dict(passed=True,protectedCount=sum(p not in allowed for p in scope['unchanged']),combinedScope=sorted(allowed)))
diff=[]
for r in scope['files']+ext:
    p=root/r['path']; prior=(batch.parent/'r5-browser-chain-20261008-01'/r['before'].replace('\\','/')).read_text(encoding='utf-8') if r['before'] else ''
    diff.extend(difflib.unified_diff(prior.splitlines(True),p.read_text(encoding='utf-8').splitlines(True),fromfile='before/'+r['path'],tofile='current/'+r['path']))
for p in ['knowledge/src/test/java/com/zhku/knowledge/controlled/RuleCheckLegacyBrowserHttpTest.java']:
    diff.extend(difflib.unified_diff([], (root/p).read_text(encoding='utf-8').splitlines(True),fromfile='before/'+p,tofile='current/'+p))
(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md'
t=backlog.read_text(encoding='utf-8')
t=t.replace('旧v1文案已修复待合并审查，完整浏览器待续','旧v1文案与合并审查完成，v2浏览器已验收，旧v1页面受工具权限阻断')
t=t.replace('实际合成回答/原文/撤回已观察，完整矩阵未通过','v2五状态/原文/撤回/身份及普通终端通过，旧v1浏览器受阻')
backlog.write_text(t,encoding='utf-8')
note='''\n## 2026-10-08 工程验收收口：v2矩阵已验，旧v1页面仍OPEN

实际v2浏览器五状态/澄清续答、两侧原文、撤回、学生账号切换、维护者拒绝、编辑清旧、首页/刷新通过；live05显式STOP、临时资源退出。旧v1实际HTTP受理/完成及内容冲突通过，页面接管被CUA URL策略拒绝，不绕过。普通WinPS5.1重建用户PATH（无pwsh）经CMD启动相关3项HTTP，全部通过；UTF8报告采集修正独立于业务运行。前端85/85、构建、runner29；一次合并审查I1首写成功/第二写失败清理，实际RED2/1→GREEN3/3，C0/I0/M0，不重审。A09本批文案/审查完成，A10因旧v1页面仍OPEN；真实DRAFT/B/C/G2不变。[本批证据](../evidence/rule-check-refactor/2026-10-07/r5-engineering-closeout-20261008-01/acceptance.md)。
'''
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(note)
with (root/'docs/product/rule-check-status-2026-10-08.md').open('a',encoding='utf-8') as f:f.write(note)
with (root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md').open('a',encoding='utf-8') as f:f.write('''\n## 本轮工程验收补齐进度

- [x] v2实际合成浏览器五状态、澄清续答、原文/撤回/身份/新题/路由/刷新矩阵，显式结束与资源退出。
- [x] 普通WinPS5.1用户PATH/CMD启动器、中文UTF8边界及相关3项实际HTTP验收。
- [x] 一次合并审查，I1实际SQL失败清理RED→GREEN；前端85/构建/runner29。
- [ ] 旧v1页面提交、完成刷新、换账号：浏览器工具URL策略拒绝接管，未绕过；恢复控制后继续。本批不标完整A10或真实G2通过。
''')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:f.write('''\n工程验收本轮：v2 live05 HTTP预检五状态及续答、实际浏览器完整矩阵通过，显式STOP/资源退出；旧v1 live02真实HTTP通过，浏览器接管被工具策略拒绝，A10保留OPEN。普通WinPS5.1用户PATH无pwsh，CMD启动相关3项HTTP均通过/sourceDrift0/resourcesTrue；UTF8文件采集曾失败，修正后只重采结果。
Final: fixed I1 — 第二INSERT真实FK失败后首chunk残留，RED2项/1fail/0error→初始化进入finally→普通终端GREEN3/3；一次审查C0/I1/M0，当前C0/I0，无重审/延期Minor。
Final: Ruling: 旧v1浏览器URL策略拒绝是工具阻断；不绕过、不拿HTTP成功或宿主STOP替代UI证据，完整A10仍OPEN — 成本：恢复浏览器控制后要补页面动作。
Final: Ruling: 合成工程不审核真实资料或证明供应商/用户效果；固定FixtureClock/宿主租约不证明墙钟TTL，Cookie交付和发布原子性限制沿既有边界 — 成本：后续分别验证，不能扩张发布声明。
''')
write(batch/'integrity.json',dict(stage='V2_BROWSER_AND_USER_TERMINAL_VERIFIED_LEGACY_BROWSER_BLOCKED',fullA10Passed=False,
    artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity.json'}))
print('Engineering checkpoint saved: v2 browser/ordinary terminal/review fixed; legacy browser OPEN; protected files unchanged')
