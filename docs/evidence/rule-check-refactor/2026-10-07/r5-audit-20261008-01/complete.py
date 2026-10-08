from pathlib import Path
import hashlib, json, difflib
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5]; batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists()
resolution=read(batch/'review-resolution.json')
assert resolution['criticalRemaining']==resolution['importantRemaining']==0
assert (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-audit-acceptance-20261008-01'); directory=batch.parent/phase
result=read(directory/'results.json'); command=read(directory/'command.json')
assert result['passed'] and result['exitCode']==0 and result['sourceDrift']==0
assert result['resourcesVerified'] and result['containerExited'] and not result['missingXml']
db_names={'JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest'}
counts={'unit':0,'database':0}; classes={'unit':0,'database':0}
for suite in result['suites']:
 xml=directory/'xml'/suite['xml']; assert sha(xml)==suite['sha256']
 actual=ET.parse(xml).getroot()
 assert all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped'])
 assert int(actual.get('tests'))==suite['tests']
 group='database' if suite['name'].split('.')[-1] in db_names else 'unit'
 counts[group]+=suite['tests']; classes[group]+=1
assert sum(counts.values())==result['testCount']
sources=read(directory/'source-after.json')
assert all(sha(root/r['path'])==r['sha256'] for r in sources)
before=read(batch/'source-before.json')
assert all(sha(root/p)==h for p,h in before['unchanged'].items())
old_sources=read(batch.parent/'r5-audit-red-20261008-01'/'source-before.json')
historic=[r for r in old_sources if '/db/migration/' in r['path']]
assert all(sha(root/r['path'])==r['sha256'] for r in historic)
db=read(directory/'database.json')
assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22'
assert not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json'); assert contract['passed'] and contract['checks']==29
readiness=read(batch/'source-readiness.json')
assert all(sha(Path(p))==h for p,h in readiness['hashes'].items())
manifest=read(Path(next(p for p in readiness['hashes'] if Path(p).parent.name=='manifests')))
assert manifest['id']==readiness['manifestSourceId']
draft=read(root/'docs/product/rule-check-2025-rule-template-draft.json')
assert draft['ruleDefinitionReviewStatus']=='DRAFT' and not draft['runtimeActivated'] and not draft['studentVisible']

plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md'
text=plan.read_text(encoding='utf-8'); a=text.index('### Task2D：'); z=text.index('## Task3：',a)
plan.write_text(text[:a]+text[a:z].replace('- [ ]','- [x]')+text[z:],encoding='utf-8')
note=f'''
## 2026-10-08 R5 Task2D实际引用审计收口

旧仓储按chunk取最新entry且旧DB CHECK拒绝捕获路由。本批增加固定profile/切分/scope与捕获source/chunk/revision/hash/正文绑定的JDBC仓储，一条INSERT SELECT重核已有字段资格后落审计；旧仓储拒绝CAPTURE误装，四参旧路由仍兼容。新增V22扩充CHECK，历史迁移不改，只在无卷专用临时库运行。

实际验收：{len(result['suites'])}类{result['testCount']:.0f}项，其中{classes['unit']}类{counts['unit']}纯单元、{classes['database']}类{counts['database']}真库（新审计18项）；29项runner合同。V22、连接0、容器退出。共享词法/混合→捕获→真实JDBC审计→规则决策及顺序撤回拒绝已验证。合成规则/资料不代表2025资料审核或学生放行。

资料盘点：原件SHA匹配，外部清单/候选保持本地验证且学生不可见/传播资格未确认。原容器已停止，本轮未启动或连接原库，在线资料资格UNKNOWN，不能推断无资料。外部“2025版”和规则“2025.9.2”不自动互换，运行时版本映射待核对。

阶段JDBC_CAPTURE_AUDIT_VERIFIED_NOT_HTTP_INTEGRATED。审计固定配置还须与后续检索/current reader装配一致；没有召回entryId追踪或跨组件自动校验。捕获尝试不等于已向学生下发；整批事务/最终输出与历史撤回窗口未验收。B-01/B-02、真实规则绑定/语义审核及v2应用/页面/实际质量仍OPEN。详见[Task2D验收](../evidence/rule-check-refactor/2026-10-07/r5-audit-20261008-01/acceptance.md)。
'''
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(note)
with (root/'docs/product/rule-check-review-handoff.md').open('a',encoding='utf-8') as f:
 f.write('''
**2026-10-08 Task2D接续：**共享检索与实际引用审计工程链已验证；来源盘点见r5-audit-20261008-01/source-readiness.json。原库停止且未连接，不能确认在线ID/资格；外部manifest的id为资料清单身份，不自动当在线已发布身份。资料与规则仍不激活。接续须记录真实source/chunk及revision映射、原文跨度，核对固定索引配置与实际规则范围/条件/例外和展示资格，再装配受控v2。没有重发20题确认请求。
''')
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:
 f.write('''
## 固定索引JDBC捕获审计（Task2D）

捕获传入完整source/chunk/revision/hash/正文绑定；JdbcRuleCheckCitationAuditRepository只接受RULE_CHECK_CAPTURE，并以固定服务端profile/切分/scope在一条参数化INSERT SELECT中重新检查当前资料资格、run一致性、来源版本及原文，匹配唯一entry后插入。任何0行、重复引用或数据库失败均阻断。旧JdbcCitationAuditRepository显式拒绝CAPTURE，防止误装时退回latest解析；旧四参/旧路由不改语义。V22仅扩充现有CHECK，未更改历史迁移；原库未应用该迁移。

当前快照不保存召回entryId，审计绑定的是固定配置下仍符合捕获内容的当前entry；不得声称追踪召回时entry或自动校验跨组件配置。后续装配必须统一检索、current reader与审计的profile/切分/scope，并验证切换边界。单条SQL快照不等于整批审计或最终HTTP/历史下发原子性。真实2025规则仍DRAFT，来源指纹一致不等于展示资格。
''')
issues_path=root/'docs/evidence/rule-check-refactor/2026-10-07/issues.json'; issues=read(issues_path); found=[]
def visit(x):
 if isinstance(x,dict):
  if x.get('id')=='RC-04-SEMANTICS':found.append(x)
  for v in x.values():visit(v)
 elif isinstance(x,list):
  for v in x:visit(v)
visit(issues); assert len(found)==1 and found[0]['status']=='OPEN'
found[0]['engineeringComponents'].append(dict(name='JdbcRuleCheckCitationAuditRepository',stage='JDBC_CAPTURE_AUDIT_VERIFIED_NOT_HTTP_INTEGRATED',
 evidencePath='docs/evidence/rule-check-refactor/2026-10-07/r5-audit-20261008-01/acceptance.md',migration='V22_TEMP_DATABASE_ONLY',manualEvaluationStatus='NOT_RUN'))
found[0]['closureReminder']='共享检索、当前核验与实际JDBC捕获审计已连接；真实2025规则/资料审核和绑定、统一索引配置装配、HTTP/自然语言与最终下发、报告质量及用户收益尚未通过，旧自由RAG风险仍OPEN'
write(issues_path,issues)
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2D: complete — Unit18/6失败与DB18/6失败→GREEN18；综合{len(result["suites"])}类{result["testCount"]:.0f} ({counts["unit"]}单元+{counts["database"]}真库)，29合同；V22、连接0、容器退出；一次独立审查及指纹见r5-audit-20261008-01。无commit/部署或原库迁移。\n')
 for ruling in resolution['rulings']:f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution['deferredMinors']:f.write('\nFinal: minor (deferred): '+minor+'\n')
 f.write('\n完整R5/G0/G2及Task3/4保持未完成，不以真实临时SQL替代规则/资料审核和用户验证。\n')
after=[]; diff=[]
for r in before['files']:
 data=(root/r['path']).read_bytes(); after.append(dict(path=r['path'],sha256=sha(root/r['path'])))
 old=(batch/r['before']).read_bytes() if r['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').splitlines(True),data.decode('utf-8').splitlines(True),fromfile='before/'+r['path'],tofile='after/'+r['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
metrics=dict(stage='JDBC_CAPTURE_AUDIT_VERIFIED_NOT_HTTP_INTEGRATED',phase=phase,tests=result['testCount'],classes=len(result['suites']),
 counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,
 historicMigrationsUnchanged=len(historic),database=db,containerExited=True,sourceReadiness=readiness,
 realJdbcAuditVerified=True,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution)
write(batch/'metrics.json',metrics)
(batch/'acceptance.md').write_text(f'''# R5 Task2D：实际JDBC捕获审计验收

2026-10-08。阶段JDBC_CAPTURE_AUDIT_VERIFIED_NOT_HTTP_INTEGRATED，完整R5与学生入口未完成。

解决两个实际缺口：旧引用仓储按片段选择latest entry，可能记到较新但不同profile/切分；旧DB CHECK不能保存CAPTURE路由。现新增固定配置、完整来源/版本/hash/正文绑定的JDBC审计，一条INSERT SELECT检查当前已有资格后绑定唯一entry；旧仓储拒绝CAPTURE误装。V22只扩充CHECK并保留所有旧路由；历史{len(historic)}个迁移文件指纹一致，原库没有启动/连接/迁移。

|验收|结果|
|---|---|
|有效RED|Unit18项6断言失败；DB18项6断言失败；均0异常/0跳过|
|定向GREEN|18项通过|
|综合验收|{len(result['suites'])}类{result['testCount']:.0f}通过，{counts['unit']}纯单元+{counts['database']}真库，失败/异常/跳过均0|
|新增覆盖|实际JDBC审计18项、目标/误装合同6项，捕获测试补完整绑定检查|
|脚本合同|29通过，外部执行器是计数替身|
|临时库|无数据卷，V22，连接0，容器退出|
|证据|XML/当前源码{len(sources)}个文件指纹核对；15条白名单before/after及增量保存|
|独立审查|一次，见review.md及review-resolution.json|
|资料|原件SHA匹配；原36题、20题参考与DRAFT模板未改，展示资格未改变|

真实临时数据库覆盖较新BUILDING/不同切分不抢审计、缺配置不回退、来源/版本/hash/正文漂移、撤回/分享/scope/profile/run及切分不一致、重复引用失败、hash大小写、旧路由兼容；共享词法/混合→捕获→实际JDBC审计→决策，再顺序撤回→缺证。合成规则只在测试中APPROVED，不代表真实2025资料审核。向量路径为既有合成embedding测试，无供应商效果或费用。

边界：审计是证据捕获尝试，不是学生已看到回答；逐条审计不承诺整批事务。快照未保存召回entryId，后续必须统一检索/current reader/审计配置，不能把固定配置匹配当前entry说成自动配置验证或原召回entry追踪。最终HTTP下发、历史再展示撤回及跨片段一致性仍需单独验收，RC-04-SEMANTICS与B-01/B-02保持OPEN。

资料盘点见source-readiness.json：本机2025原件与manifest/draft指纹一致；清单待审，候选仅本地审核、学生不可见/分享资格未确认；原容器停止且未连接，在线来源UNKNOWN。manifest身份不可当在线已发布ID；“2025版”与“2025.9.2”不得未经核对互换。真实规则绑定/语义审核、v2身份/CSRF与自然语言/页面、报告补样/评分及用户收益仍待实施。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},
 currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,migration=db['migration'],sourceFiles=len(sources)),ensure_ascii=False))
