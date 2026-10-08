from pathlib import Path
import hashlib,json,difflib,re
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists();before=read(batch/'source-before.json');extension=read(batch/'scope-extension.json')
resolution=read(batch/'review-resolution.json');assert resolution['criticalRemaining']==resolution['importantRemaining']==0
ex=extension['files'][0];comment=lambda s:re.sub(r'//[^\n]*|/\*[\s\S]*?\*/','',s)
assert comment((root/ex['path']).read_text(encoding='utf-8-sig'))==comment((batch/ex['before']).read_text(encoding='utf-8-sig'))
protected={p:h for p,h in before['unchanged'].items() if p!=ex['path']};assert all(sha(root/p)==h for p,h in protected.items())
final=batch.parent/'r5-entry-identity-final-green-20261008-02';r=read(final/'results.json')
assert r['passed'] and r['testCount']==503 and len(r['suites'])==48 and r['sourceDrift']==0 and r['resourcesVerified']
assert all(not(s['failures'] or s['errors'] or s['skipped']) for s in r['suites'])
for name,tests,fail in [('r5-entry-identity-red-valid-20261008-01',37,3),('r5-entry-recovery-red-20261008-01',16,1)]:
 t=read(batch.parent/name/'results.json');assert t['testCount']==tests and sum(s['failures'] for s in t['suites'])==fail
 assert all(not(s['errors'] or s['skipped']) for s in t['suites']) and t['sourceDrift']==0 and t['resourcesVerified']
assert read(batch/'runner-contract.json')['passed']
db=read(final/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22'
assert not read(final/'database-attempt.json')['volumeMounted']
for s in r['suites']:assert sha(final/'xml'/s['xml'])==s['sha256']
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write('''
## 2026-10-08 Task2P：v2入口迟到撤销和恢复路径

此前静态风险已用真实临时库/HTTP复现并修正。入口身份失败仅清本请求上下文并返回401/no-store，不invalidate共享Session或发删除Cookie，避免旧异常伤已完成B登录/新题/原文句柄。连续失效me/POST仍拒绝；精确csrf GET、login/logout POST允许身份恢复，CSRF前序和新Provider许可仍保留。旧v1处理不变。失效Session记录按闲置超时或显式退出回收，15m非绝对寿命，持续访问可延长。

有效37/3 Cookie RED、16/1 csrf恢复RED→48类503（358纯单元+133临时库含22实际HTTP+12配置）、0fail/error/skip/sourceDrift，V22/18迁移/连接0/退出核对；29合同计数替身。首次37/3并发夹具停在B登录，502回归含调用计数夹具，首次503回归1失败为登录后旧CSRF导致403；全部失败报告保留、不冒称对应行为RED/GREEN。最终[证据](../evidence/rule-check-refactor/2026-10-07/r5-entry-identity-20261008-01/acceptance.md)明确范围。审查C0/I0/M2；合同同步+Controller仅注释scope extension，原9项before保留，实际去注释语义等价，不重跑Java。前端80保护未改未重跑。

本批仅v2入口身份失败，不解决旧v1、主动logout或匿名CSRF新Session的网络Cookie交付窗口；资料/规则顺序核验非最终交付原子性。A10整链/真实内容B/C/G2仍OPEN，无模型/原库写入。
''')
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## v2入口身份失败策略（Task2P，2026-10-08）

失效身份每次fresh DB失败即拒绝业务/原文/me；只清请求上下文，不从在途失败删除共享Session或同名Cookie。保留记录不提供许可，结束职责为显式logout或15m闲置超时。精确GET auth/csrf仅提供CSRF，不授业务身份；精确POST login/logout在既有CSRF之后恢复/结束，新Provider仍authenticate+requireCurrent。旧v1未扩改；不宣称所有Set-Cookie网络顺序原子。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';t=plan.read_text(encoding='utf-8');i=t.index('## Task2P / A07');tail=t[i:].replace('- [ ]','- [x]').replace('实际撤销A后入口失败暂停，B登录并完成新题','入口A首SQL后暂停，B先登录完成新题，撤销A后第二次真实SQL交付入口失败').replace('自然Session TTL清理','闲置Session TTL清理');plan.write_text(t[:i]+tail,encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';t=backlog.read_text(encoding='utf-8').replace('HTTP资料/规则/身份/Session复查已验收，下发原子窗口仍有局限','HTTP复查及v2入口迟到撤销/身份恢复已验收，网络窗口仍有局限');backlog.write_text(t,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write('\nTask2P: complete — 最终48类503/0fail/error/skip/sourceDrift，资源退出；真实22 HTTP仅合成。有效37/3与恢复16/1RED；早期夹具/调用计数/旧CSRF失败保留。C0/I0/M2同步，显式Controller注释extension，纯语义相同，无二次审查。\n')
 for s in resolution['rulings']:f.write('\nFinal: Ruling: '+s+'\n')
 for s in resolution['fixed']:f.write('\nFinal: fixed '+s+'\n')
 f.write('\n接续Task3C1实际Nginx→Node边界已5项通过、审查中；随后H03索引同步、A09旧文案与完整Java临时库浏览器；B/C/G2保持OPEN。\n')
after=[];diff=[]
for row in before['files']+extension['files']:
 p=root/row['path'];after.append(dict(path=row['path'],sha256=sha(p)))
 diff.extend(difflib.unified_diff((batch/row['before']).read_text(encoding='utf-8-sig').splitlines(True),p.read_text(encoding='utf-8-sig').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage='V2_ENTRY_IDENTITY_RACE_AND_RECOVERY_VERIFIED',backendClasses=48,backendTests=503,pureTests=358,databaseTests=133,actualHttpTests=22,configurationTests=12,runnerContractChecks=29,sourceDrift=0,resourcesVerified=True,providerCalled=False,originalDatabaseWritten=False,frontendTestsLast=80,frontendRerun=False,postTestJavaChange='COMMENT_ONLY_EQUIVALENCE_VERIFIED',protectedFiles=len(protected),review=resolution))
(batch/'acceptance.md').write_text('''# Task2P：v2入口迟到撤销及身份恢复

2026-10-08，V2_ENTRY_IDENTITY_RACE_AND_RECOVERY_VERIFIED。

|问题|修复|证据|
|---|---|---|
|旧身份入口失败invalidate/清Cookie伤新登录|v2只清请求上下文并401/no-store，不改共享Session/Cookie|真实SQL首读→B先登录完成题→撤销A第二读失败交付；旧响应无Cookie，B me/原文句柄及audit1有效|
|保留记录会阻断重新登录/退出|精确CSRF GET/login/logout POST仅跳旧actor，CSRF和新Provider不跳|有效恢复16/1RED；正常撤销连续me/POST401，csrf新取/新账号登录/显式logout真正invalidates|
|边界注释/合同落后实现|合同说明闲置超时、Controller仅中文注释同步|C0/I0/M2审查；独立scope extension原字节、去注释语义等价|

有效37/3 Cookie断言RED，16/1恢复RED→最终48类503（358纯单元+133临时库含22实际HTTP+12凭据相关配置检查），0fail/error/skip/sourceDrift，570源码，V22/18迁移/连接0/Java及容器退出。29合同为外部计数替身另记录。所有阶段原报告/XML/hash保留：首次37/3并发未到Cookie断言，首次502含计数夹具，首次503唯一失败因登录后旧CSRF先403；不冒称有效对应业务RED。最终phase为r5-entry-identity-final-green-20261008-02。

一次独立审查C0/I0/M2；实际9项增量及595保护审查通过，后显式扩Controller注释为10项，594其他保护不变；原9项before不覆盖。唯一后测试Java变化为注释等价，不再运行业务测试。前端80仅上批成绩、保护不变、未重跑。真实目录CLOSED、资料DRAFT、B/C/G2不变；无模型/原库写入/commit/push/deploy。

保留失效Session不授许可，15m是闲置超时而非绝对寿命。仅修v2入口失败；旧v1、显式logout、匿名csrf新Session和总体网络Cookie交付窗口仍有局限，不称全系统原子性。Task3C完整浏览器代理仍待后续。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=protected,commentOnlyExtension=True,finalRun=str(final.relative_to(root)).replace('\\','/')))
print('Task2P complete: 503 backend tests / C0 I0 / explicit comment-only extension')
