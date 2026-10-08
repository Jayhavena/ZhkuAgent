from pathlib import Path
import hashlib,json,difflib,re
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists()
before=read(batch/'source-before.json');resolution=read(batch/'review-resolution.json')
assert resolution['criticalRemaining']==resolution['importantRemaining']==0
assert all(sha(root/p)==h for p,h in before['unchanged'].items())
for name,total,fail in [('red.log',8,8),('login-unknown-red.log',9,1),('review-red-valid.log',11,1),('review-final-green.log',80,0)]:
 p=batch/name
 log=p.read_text(encoding='utf-8-sig')
 for key,count in [('tests',total),('fail',fail),('cancelled',0),('skipped',0)]:assert re.search(rf'^# {key} {count}$',log,re.M),(p,key)
assert 'RuleCheckView-' in (batch/'review-final-build.log').read_text(encoding='utf-8-sig')
stage='STUDENT_PAGE_BUNDLED_STATIC_BROWSER_CHECKED_NOT_PROXY_CHAIN'
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 学生查证页面（Task3B，2026-10-08）

/rules使用独立v2身份与查证客户端，首页主入口指向该页，旧学分/通用助手标实验。五状态独立pending；展示问题范围、用户声明条件、事实成立前提、未知项与冲突两侧；原文按上下文相对跨度对照，不造页码映射或PDF跳页。政策/元数据使用插值转义，外链HTTPS且无userinfo/非443，不替代服务端host许可。

身份epoch清输入/密码，编辑新题清旧内容，离开销毁Workflow。登录从开始即保留共享LOGIN_UNCONFIRMED，只同代成功/已知失败清理；页面销毁不取消服务端登录，也不丢未知，先只读me再登录。密码不持久化。SSR9项只模板；生命周期2项实际Vue虚拟host只setup/mount/unmount；静态浏览器只布局/路由，尚无代理或真实账号交互闭环。
''')
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write('''
## 2026-10-08 Task3B：学生查证页面工程完成

Node80/80=既有69+实际SFC SSR9+Vue生命周期虚拟host2，0fail/cancel/skip，真实页面bundle20.28KB。审查I1登录pending跨页面丢未知状态有效11/1RED→GREEN；M1尾斜杠class由实际浏览器确认。首次生命周期夹具缺SSR context的TypeError单独保留，不算业务RED。[本批证据](../evidence/rule-check-refactor/2026-10-07/r5-student-page-20261008-01/acceptance.md)。静态预览686宽登录页无水平溢出；/rules/ class正确，首页移除，主CTA返回/rules；无代理时me不可用为预期，不计A10。临时预览关闭，4175无监听。没有数据库/真实模型操作，Java/API/旧v1/真实DRAFT/参考保护不变。

A08页面可构建但尚未完整使用；A09旧v1 RC11、A10代理/真实后端/普通终端/浏览器矩阵继续OPEN。B/C/真实可见/G2不放行。A07入口过滤器迟到失败风险另批最小复现后处理。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';t=plan.read_text(encoding='utf-8');i=t.index('## Task3B / A08');plan.write_text(t[:i]+t[i:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';t=backlog.read_text(encoding='utf-8').replace('v2客户端/状态已验收，页面待续','v2客户端/状态与页面已验收，完整代理浏览器待续');backlog.write_text(t,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write('\nTask3B: complete — 初始8/8RED，登录未知9/1RED；审查生命周期夹具11/2 error不算业务RED，provide SSR context后11/1有效RED→Node80/80、0fail/cancel/skip；新页面实际bundle。静态浏览器686宽/rules/→首页→主CTA/rules确认，非代理/登录/任务闭环。4175预览停止。\n')
 for s in resolution['rulings']:f.write('\nFinal: Ruling: '+s+'\n')
 for s in resolution['fixed']:f.write('\nFinal: fixed '+s+'\n')
 f.write('\n继续Task2P A07入口迟到身份失败最小复现，然后Task3C代理/完整浏览器；B/C/G2仍OPEN。\n')
after=[];diff=[]
for row in before['files']:
 p=root/row['path'];data=p.read_bytes();after.append(dict(path=row['path'],sha256=sha(p)))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'build-manifest-final.json',{str(p.relative_to(root)).replace('\\','/'):sha(p) for p in (root/'agent/frontend/dist').rglob('*') if p.is_file()})
write(batch/'metrics.json',dict(stage=stage,nodeTests=80,legacyTests=69,sfcSsrTests=9,vueLifecycleVirtualHostTests=2,failures=0,cancelled=0,skipped=0,newPageBundled=True,staticBrowserVerified=True,proxyChainVerified=False,databaseCalled=False,providerCalled=False,backendChanged=False,backendRerun=False,studentVisible=False,qualityGatePassed=False,review=resolution))
(batch/'acceptance.md').write_text('''# Task3B：学生查证页面与首页入口

2026-10-08，STUDENT_PAGE_BUNDLED_STATIC_BROWSER_CHECKED_NOT_PROXY_CHAIN。

|解决的问题|实现|证据|
|---|---|---|
|产品入口仍围绕学分实验|首页查证主CTA、/rules独立v2页面、旧实验保留|实际SFC模板与静态浏览器首页点击|
|回答条件/未知/冲突与执行pending混淆|五状态、声明条件/事实前提/范围/未知/冲突双方，pending独立|9项真实SFC SSR检查，非事件或网络|
|引用难核对、页码易误读|原文旁栏相对跨度高亮、记录页码/未知、纯文本转义/HTTPS|SSR实际模板及恶意文本/URL拒绝|
|登录pending离开返回丢未知状态|共享状态先标未知，同epoch结束；组件销毁不清未知，先me再登录|有效11/1 RED→GREEN；Vue实际setup/mount/unmount虚拟host，旧身份不污染新身份|
|有效尾斜杠路由布局遗漏|命名路由及成功导航class|实际浏览器/rules/ bodyClass，首页移除，主CTA回/rules|

初始RED8/8、登录未知RED9/1；第一次生命周期夹具11/2 TypeError不算有效RED，修正SSR context后11/1断言失败有效。最终全Node80/80（旧69+SSR9+生命周期2）、0fail/cancel/skip；真实页面bundle20.28KB。一次独立审查I1/M1修正，未重审。静态浏览器686宽无水平溢出，主入口/实验链接可见，无后端proxy所以me错误是预期；没有真实登录/查证/原文交互，A10不算通过。4175预览服务已停止、浏览器临时tab关闭。10项before/after/diff/hash和保护文件核对；Java/API/旧v1/真实DRAFT/人工参考不变，无数据库/供应商操作。

工程交付不证明资料语义或收益。A07入口风险下一批，A09旧v1 RC11、Task3C代理/普通终端/实际浏览器继续，真实资料仍CLOSED，B/C/G2保持OPEN。无commit/push/deploy。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged']))
print('Task3B complete: 80 tests; new page bundled/static browser only; proxy chain pending')
