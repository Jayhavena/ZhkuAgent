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
phase=resolution.get('finalPhase','r5-application-acceptance-20261008-01');directory=batch.parent/phase
result=read(directory/'results.json')
assert result['passed'] and result['exitCode']==0 and result['sourceDrift']==0
assert result['resourcesVerified'] and result['containerExited'] and not result['missingXml']
counts={'unit':0,'database':0};classes={'unit':0,'database':0}
db_names={'JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest'}
for suite in result['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256']
 actual=ET.parse(xml).getroot();assert all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped'])
 assert int(actual.get('tests'))==suite['tests']
 group='database' if suite['name'].split('.')[-1] in db_names else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert sum(counts.values())==result['testCount']
sources=read(directory/'source-after.json');assert all(sha(root/r['path'])==r['sha256'] for r in sources)
before=read(batch/'source-before.json');assert all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22'
assert not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
fix_red=read(batch.parent/'r5-application-review-red-20261008-01'/'results.json')
assert not fix_red['passed'] and fix_red['testCount']==22
assert sum(s['failures'] for s in fix_red['suites'])==1
assert sum(s['errors']+s['skipped'] for s in fix_red['suites'])==0
assert fix_red['sourceDrift']==0 and fix_red['resourcesVerified']
assert result['testCount']==263 and len(result['suites'])==23
draft=read(root/'docs/product/rule-check-2025-rule-template-draft.json')
assert draft['ruleDefinitionReviewStatus']=='DRAFT' and not draft['runtimeActivated']
unit_red=read(batch.parent/'r5-application-red-20261008-01'/'results.json')
db_red=read(batch.parent/'r5-application-db-red-20261008-02'/'results.json')
for red,expected in [(unit_red,11),(db_red,3)]:
 assert not red['passed'] and sum(s['failures'] for s in red['suites'])==expected
 assert sum(s['errors']+s['skipped'] for s in red['suites'])==0
 assert red['sourceDrift']==0 and red['resourcesVerified']
note=f'''
## 2026-10-08 R5 Task2E应用链路验收

解决实际装配缺口：检索原先只限定profile/scope，可能召回另一个切片版本，而核验/审计要求固定切片。现一份服务端配置同时装配两路检索、当前reader与审计，固定切片过滤在SQL截断前；旧构造器和离线模式保留。

应用链路只接受问题和显式条件，从已审且精确引用版本的服务端目录选择ID，复用混合检索→证据捕获/审计→目录复核→五状态决策。目录退役/变化或资料撤回拒绝，异常信息不回显。实际{len(result['suites'])}类{result['testCount']:.0f}项（{counts['unit']}单元+{counts['database']}真库）、29项脚本合同通过；无卷临时库V22、连接0、容器退出。

阶段APPLICATION_CHAIN_VERIFIED_NOT_HTTP_INTEGRATED。这是合成规则/模型替身工程验收，真实2025模板仍DRAFT、展示资格不变；合法ID不等于语义选题正确。B-01/B-02及RC-04-SEMANTICS保持OPEN。v2身份/CSRF/接口与页面、真实绑定/审核、供应商有界选择器和独立eval、用户收益仍待验证；旧v1安全合同未扩大。详见[Task2E证据](../evidence/rule-check-refactor/2026-10-07/r5-application-20261008-01/acceptance.md)。
'''
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(note)
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md'
text=plan.read_text(encoding='utf-8');a=text.index('## Task2E：')
plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
issues_path=root/'docs/evidence/rule-check-refactor/2026-10-07/issues.json';issues=read(issues_path);found=[]
def visit(x):
 if isinstance(x,dict):
  if x.get('id')=='RC-04-SEMANTICS':found.append(x)
  for v in x.values():visit(v)
 elif isinstance(x,list):
  for v in x:visit(v)
visit(issues);assert len(found)==1 and found[0]['status']=='OPEN'
found[0]['engineeringComponents'].append(dict(name='RuleCheckApplicationService/RuleCheckRuntimeFactory',
 stage='APPLICATION_CHAIN_VERIFIED_NOT_HTTP_INTEGRATED',evidencePath='docs/evidence/rule-check-refactor/2026-10-07/r5-application-20261008-01/acceptance.md',
 configuration='SHARED_PROFILE_CHUNKING_SCOPE',manualEvaluationStatus='NOT_RUN'))
found[0]['closureReminder']='统一配置及内部应用工程链通过；真实2025绑定/语义审核/展示资格、供应商选题与独立eval、v2身份/CSRF/HTTP和最终外发、报告与用户收益尚未通过，旧自由RAG语义风险仍OPEN'
write(issues_path,issues)
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2E: complete — Unit17/11断言失败和DB21/3断言失败→GREEN17；综合{len(result["suites"])}类{result["testCount"]:.0f} ({counts["unit"]}单元+{counts["database"]}真库)、29合同；V22、连接0、容器退出；一次独立增量审查，证据r5-application-20261008-01。首轮DB因EligibilityFilter漏传Clock未编译，保留，不计RED。未commit/部署/原库/供应商调用。\n')
 for ruling in resolution['rulings']:f.write('\nFinal: Ruling: '+ruling+'\n')
 f.write(f'\nFinal: fixed I1 — factoryKeepsLexicalEvidenceWhenActualEmbeddingAdapterFailuresOccur RED22/1→GREEN；最终{result["testCount"]:.0f}全相关选类通过。单次修复，无重复审查。\n')
 for minor in resolution['deferredMinors']:f.write('\nFinal: minor (deferred): '+minor+'\n')
 f.write('\nTask2整体、Task3/4、真实资料放行和G2仍未完成；内部链路不等于HTTP上线或用户收益。\n')
after=[];diff=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=sha(root/row['path'])))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').splitlines(True),data.decode('utf-8').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
metrics=dict(stage='APPLICATION_CHAIN_VERIFIED_NOT_HTTP_INTEGRATED',phase=phase,tests=result['testCount'],classes=len(result['suites']),
 counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,
 unchanged=before['unchanged'],database=db,containerExited=True,studentVisible=False,qualityGatePassed=False,
 realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution)
write(batch/'metrics.json',metrics)
(batch/'acceptance.md').write_text(f'''# R5 Task2E：统一配置与内部应用链路验收

2026-10-08。APPLICATION_CHAIN_VERIFIED_NOT_HTTP_INTEGRATED。完整R5、学生入口及G2未完成。

|解决的问题|对应改动|验证证据|
|---|---|---|
|检索可能混入同profile不同切片，核验/审计却固定版本|RuntimeSettings/Factory统一profile、切片、scope；两路SQL先过滤再截断|真库两种切片并存，两路各1条；不存在的切片均0条，无latest回退|
|组件各自通过，应用链路尚未接通|共享Hybrid→Capture→JDBC Audit→目录复核→Decision|实际工厂返回有核验引用的ANSWERED，审计绑定指定entry|
|选择器可能返回不存在的规则或编造正文/条件|只传审核目录选项，只消费目录ID；输出事实来自审核模板|未知ID/异常/未审或其他版本/重复目录/未声明键拒绝，空目录无外部调用|
|处理途中规则/来源撤回仍可能用旧信息|捕获后目录复核，决策时当前reader再读|规则退役/模板改变与来源撤回均拒绝；真实临时库审计后撤回来源不输出事实|
|缺证、追问、部分回答、冲突易被压成一个答案|复用五状态决策，无自由事实生成|ANSWERED/PARTIALLY_ANSWERED/NEED_CLARIFICATION/INSUFFICIENT_EVIDENCE/CONFLICTING_EVIDENCE均覆盖|

|验收|结果|
|---|---|
|有效RED|Unit17项11断言失败；DB21项3断言失败；均0异常/0跳过|
|首次DB尝试|漏传Clock导致编译失败，已修正并保留，不计有效RED|
|定向GREEN|应用17项通过|
|相关综合验收|{len(result['suites'])}类{result['testCount']:.0f}通过：{classes['unit']}类{counts['unit']}纯单元+{classes['database']}类{counts['database']}真库，0失败/异常/跳过|
|启动器隔离合同|29项通过，外部执行器为计数替身|
|临时库|无数据卷，V22，连接0，随机容器退出；原库未操作|
|证据完整性|源码{len(sources)}文件、XML指纹复算；13条before/after白名单增量保存|
|独立审查|一次，见review.md/review-resolution.json；修复与裁定均保留|
|I1修复|真实embedding异常类型替身导致词法路丢失，RED22/1→工厂边界转换→最终全相关验收；无真实供应商调用|
|资料/迁移|真实DRAFT模板、20题人工参考、36题原件及18份既有迁移哈希一致|

本批仅工程合同：规则/审核身份与embedding/选择器为合成替身，不是2025真实规则激活、自然语言效果、报告集质量或用户收益。工厂无Controller/Bean，构造不连接/外发；旧v1 cookie及权限没有扩展。手工拼接组件不享有工厂配置保证；来源“2025版”和引用“2025.9.2”未自动映射。

独立审查原结论With fixes保留，I1已按有效断言RED→单次修复→最终相关全套GREEN处理，无第二次审查。M1保留：当前两个切片夹具证明固定过滤，但尚未构造不同chunk、错误切片排名更前、候选预算1的挤占反例；SQL顺序正确，不把此测试增强缺口写成已完成。

方向与边界：模型可在已审核问题范围内协助选择，不能自建事实、批准来源或猜测个人条件；合法ID仍可能选错语义，必须后续eval。目录复核与当前reader是顺序检查，不保证跨组件/最终HTTP外发原子性；审计表示捕获尝试，不表示学生已看到结果。异常正文不外泄，运营可观测性尚未装配。

接续顺序：v2身份/会话/CSRF与内部接口合同→受控接口/页面；并行准备真实source/chunk/revision/span与规则范围/条件/例外审核、展示资格；供应商有界选择器单独评估；最后独立报告集与真实用户PDF对照。真实资料未通过前入口保持关闭，不重复要求已确认的20题，也不补造审核。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},
 currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,migration=db['migration'],sourceFiles=len(sources)),ensure_ascii=False))
