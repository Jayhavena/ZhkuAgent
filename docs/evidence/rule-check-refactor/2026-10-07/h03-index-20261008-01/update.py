from pathlib import Path
import hashlib,json,difflib,re
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
paths=['docs/superpowers/plans/2026-10-07-rule-check-refactor-roadmap.md','docs/product/rule-check-scope.md','docs/evidence/rule-check-refactor/2026-10-07/README.md','docs/product/rule-check-remaining-tasks-2026-10-08.md','.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md','docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md']
assert not (batch/'acceptance.md').exists();(batch/'before').mkdir();rows=[]
for i,path in enumerate(paths):
 p=root/path;q=batch/'before'/f'{i:02}.bin';q.write_bytes(p.read_bytes());rows.append(dict(path=path,before=str(q.relative_to(batch)),sha256=sha(p)))
write(batch/'source-before.json',dict(files=rows))
backend=read(batch.parent/'r5-entry-identity-final-green-20261008-02/results.json');assert backend['passed'] and backend['testCount']==503
front=read(batch.parent/'r5-student-page-20261008-01/metrics.json');assert front['nodeTests']==80 and not front['proxyChainVerified']
proxy=read(batch.parent/'r5-proxy-20261008-01/metrics.json');assert proxy['actualProxyChecks']==5 and not proxy['browserChainVerified']
summary='''**当前工程阶段：R4与R5的内部链、v2认证/严格接口/必要澄清/当前引用与原文HTTP复查已验收；学生页面已构建，完整代理浏览器链待续。最新后端48类503项、前端80项及Nginx→Node夹具5项通过，三者分别记录，不相加为产品正确率。20道开发参考已获用户确认；真实规则绑定/语义审核/展示资格、16题报告冻结及用户收益仍未完成，G0/G2未放行。**'''
p=root/paths[0];t=p.read_text(encoding='utf-8');lines=t.splitlines();lines[2]='更新日期：2026-10-08。'+summary+' 执行见[R5任务计划](2026-10-08-rule-check-r5-plan.md)及[剩余任务](../../product/rule-check-remaining-tasks-2026-10-08.md)，历史P0记录保留。';p.write_text('\n'.join(lines)+'\n',encoding='utf-8')
p=root/paths[1];t=p.read_text(encoding='utf-8');lines=t.splitlines();lines[2]='版本：1.6；日期：2026-10-08。用户已确定产品方向。'+summary;t='\n'.join(lines)+'\n';t=t.replace('资料审核与人工参考仍需完成；','20道开发参考已确认，真实资料与规则语义审核仍需完成；');p.write_text(t,encoding='utf-8')
p=root/paths[2];t=p.read_text(encoding='utf-8');lines=t.splitlines();lines[2]='更新日期：2026-10-08；目录日期是计划开始日期。'+summary+' 历史批次不改写。';t='\n'.join(lines)+'\n'
t=t.replace('定位已由用户确定；R0隔离入口和工程基线已验证，当前资料、人工金标准与查证流程未完成，G0未通过。','定位已确定；R0工程、20道开发参考已确认，R5合成工程链与页面已推进；真实资料/模板语义、独立报告参考及用户收益未完成，G0未通过。')
t=t.replace('| RC-05 激活/资格/时间约束不完整 | R4/R6 | 静态确认，OPEN |','| RC-05 激活/资格/时间约束不完整 | R4/R6 | PARTIAL；R4已有字段资格完成，完整时效/清单OPEN |')
t=t.replace('| RC-06 受控咨询链与通用RAG分离 | R5 | 静态确认，OPEN |','| RC-06 受控咨询链与通用RAG分离 | R5 | PARTIAL；v2合成HTTP与页面完成，真实资料/整链OPEN |')
t=t.replace('| RC-10 集成测试可能误连原库 | R0 | PARTIAL；新入口已验证，旧直接运行风险仍OPEN | 新入口安全证据已归档；旧测试默认URL不能因此被称已修复 |','| RC-10 集成测试可能误连原库 | R0/H01/F06 | PARTIAL；旧默认原库回退已防护，完整CI矩阵OPEN | H01 32处回退受guard保护、12类旧真库验证；不称所有旧测试都实际执行 |')
i=t.index('## 1. 初始问题索引');current='''## 当前交付证据（2026-10-08）

- [Task2P入口身份与恢复](r5-entry-identity-20261008-01/acceptance.md)：48类503项（358纯单元+133临时库含22实际HTTP+12配置）；29工具合同为计数替身。当前Java唯一后测改动是中文注释等价同步，范围明确记录。
- [Task2O原文HTTP](r5-original-http-20261008-01/acceptance.md)：实际Runtime/当前资料资格/会话句柄与原文链，全部合成资料，无供应商。
- [Task3B学生页面](r5-student-page-20261008-01/acceptance.md)：80项（既有69+SFC SSR9+Vue生命周期虚拟host2）、实际bundle及静态浏览器；不等于代理/登录闭环。
- [Task3C1代理](r5-proxy-20261008-01/acceptance.md)：实际Nginx→Node夹具5项、v1段保持；非Java/数据库/浏览器整链。
- [H01旧库隔离](h01-database-isolation-20261008-01/acceptance.md)：旧默认原库回退防护，不以拿到凭据授权写原库。
- [47项任务清单](../../../product/rule-check-remaining-tasks-2026-10-08.md)：按工程、真实资料、Eval与门禁后阶段区分；P2未启动。

历史263/501或R3阶段记录保留当时含义，不当作当前总进度；所有合成/配置/工具合同成绩都不证明真实政策或用户收益。真实2025模板仍DRAFT，不重复要求确认已完成20题；报告候选13道、需补3个独立家族且人工核对后冻结16道。

''';t=t[:i]+current+t[i:];p.write_text(t,encoding='utf-8')
p=root/paths[3];t=p.read_text(encoding='utf-8').replace('和Task2O最终证据','及Task2P/Task3B/Task3C1证据')
t=t.replace('- 最新相关验收为48类501项（357纯单元+132专用临时库含21实际HTTP+12凭据相关配置检查）及29项启动器合同，见Task2O证据；另Task3A前端69项通过，尚未新页面装配；默认关闭目录接口通过不等于真实学生查证已交付。','- 最新后端48类503项（358纯单元+133临时库含22实际HTTP+12配置）及29工具计数替身合同；前端80项与实际页面bundle；代理Nginx→Node夹具5项。三类分开，不称完整Java/浏览器/数据库整链或真实政策通过。')
t=t.replace('最终下发原子窗口及真实页码映射、学生页面、真实模型语义评估','最终下发窗口及真实页码映射、完整学生浏览器链、真实模型语义评估')
t=t.replace('| A10 | 浏览器端到端验收 |','| A10 | 浏览器端到端验收（v2代理夹具已通过，实际Java整链待续） |')
t=t.replace('| H03 | 更新阶段记录与证据索引 |','| H03 | 更新阶段记录与证据索引（2026-10-08已同步，后续每批维护） |')
p.write_text(t,encoding='utf-8')
with (root/paths[4]).open('a',encoding='utf-8') as f:f.write('''
Task3C1: complete — 实际Nginx→Node代理有效404/403RED→5 GREEN，原v1段逐字相同，C0/I0/M0审查，58480/58184及独立容器/网络退出。仅代理工程，无Java/DB/provider/浏览器身份，完整Task3C2另续。
TaskH03: complete — 主路线/范围卡/证据README/任务清单当前状态按实际503/80/5分开更新，20开发参考已确认、真实DRAFT/B/C/G0/G2未放行；旧历史阶段保留，当前链接核验，无业务代码变化。
TaskH03: Ruling: 文档不能继续称人工20题待确认或停在R3，也不能用合成测试关闭产品父问题 — 所有完成声明按证据阶段和输入范围 — 成本：持续维护最新索引，历史批次保持原时点。
''')
with (root/paths[5]).open('a',encoding='utf-8') as f:f.write('''
## Task3C1 / A10 v2代理边界（2026-10-08）

- [x] 有效RED：Nginx尚未v2，预期上游403实际404；独立loopback Node夹具、无Java/DB/provider。
- [x] GREEN：完整URI/no-store/no重放、bare及其他namespace404不上游、原v1保持、SPA/缺资源，实际5项。
- [x] 一次增量审查C0/I0/M0，独立容器/网络/端口/Node关闭，2项before/diff/hash；完整Java浏览器Task3C2待续。

## TaskH03 / 当前索引同步（2026-10-08）

- [x] 主路线/范围/证据索引/47项任务当前503/80/5分别表达，20开发参考已确认；合成、SSR、配置、计数替身和真实资料效果边界完整。
- [x] 证据链接/计数对照、before/after/diff/hash；不重跑无变化业务代码，真实B/C/G0/G2不放行。
''')
linked=[]
for path in paths[:4]:
 p=root/path
 for target in re.findall(r'\]\(([^\s)]+)\)',p.read_text(encoding='utf-8')):
  if '://' in target or target.startswith('#'):continue
  resolved=(p.parent/target.split('#')[0]).resolve()
  # 只验当前新增交付链接，不把历史预规划尚无目录链接当本轮造假。
  if any(s in target for s in ['r5-entry-identity-','r5-original-http-','r5-student-page-','r5-proxy-','h01-database-isolation-','rule-check-remaining-tasks-','2026-10-08-rule-check-r5-plan']):
   assert resolved.exists(),(path,target);linked.append(dict(file=path,target=target,exists=True))
after=[];diff=[]
for row in rows:
 p=root/row['path'];after.append(dict(path=row['path'],sha256=sha(p)))
 diff.extend(difflib.unified_diff((batch/row['before']).read_text(encoding='utf-8-sig').splitlines(True),p.read_text(encoding='utf-8-sig').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage='CURRENT_DOCUMENTATION_INDEX_SYNCHRONIZED',backendTestsReferenced=503,frontendTestsReferenced=80,proxyFixtureChecksReferenced=5,newCurrentLinksChecked=len(linked),links=linked,businessCodeChanged=False,businessTestsRerun=False,humanReferencesConfirmed=20,qualityGatePassed=False))
(batch/'acceptance.md').write_text('''# H03：当前阶段与证据索引同步

旧主路线/范围卡/证据README仍称当前停在R3、20人工参考待确认、旧测试原库fallback未修及学生页面未开发。按实际证据更新为R5合成HTTP/原文/身份恢复、已构建学生页及独立代理边界，最新503/80/5分开表达。20开发参考已确认，真实DRAFT/语义审核/可见性/16题独立报告/用户收益仍未完成，G0/G2不放行。

六项文档before/after/diff/hash与新增当前本地链接检查。旧批次263/501等保留当时记录，不当当前总分；父问题未因局部工程自动关闭。无代码/数据库/模型操作，不重跑未变业务测试。来源三批metrics/results核验，合成/SSR/生命周期虚拟host/静态浏览器/代理替身与真实产品效果分开。47项任务清单同步，后续每批继续维护。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after))
print(f'H03 complete: 6 docs / {len(linked)} current links checked / no code or DB operation')
