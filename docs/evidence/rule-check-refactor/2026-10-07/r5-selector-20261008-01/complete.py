from pathlib import Path
import hashlib,json,difflib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists()
resolution=read(batch/'review-resolution.json');assert resolution['criticalRemaining']==resolution['importantRemaining']==0 and (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-selector-acceptance-20261008-01');directory=batch.parent/phase;r=read(directory/'results.json')
assert r['passed'] and r['exitCode']==0 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited'] and not r['missingXml']
counts={'unit':0,'database':0,'credentialConfiguration':0};classes={k:0 for k in counts}
for suite in r['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256'];actual=ET.parse(xml).getroot()
 assert int(actual.get('tests'))==suite['tests'] and all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped'])
 name=suite['name'].split('.')[-1];group='database' if name in {'JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest','RuleCheckPreviewHttpTest'} else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert sum(counts.values())==r['testCount'] and r['testCount']>=374 and len(r['suites'])==31
sources=read(directory/'source-after.json');assert all(sha(root/x['path'])==x['sha256'] for x in sources)
before=read(batch/'source-before.json');assert all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22' and not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
red=read(batch.parent/'r5-selector-red-20261008-01'/'results.json')
assert not red['passed'] and red['testCount']==17 and sum(s['failures'] for s in red['suites'])==17 and sum(s['errors']+s['skipped'] for s in red['suites'])==0 and red['resourcesVerified'] and red['sourceDrift']==0
resolution['verificationStatus']='VERIFIED_FINAL_GREEN';write(batch/'review-resolution.json',resolution)
total=int(r['testCount']);stage='BOUNDED_SELECTOR_PROTOCOL_VERIFIED_NOT_PROVIDER_ASSEMBLED'
summary=f'31类{total}项（{counts["unit"]}纯单元+{counts["database"]}真库含12HTTP+12凭据配置）、29计数替身合同；无卷55465 V22连接0/退出。'
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(f'''
## 2026-10-08 Task2J / A05第一段有界选题组件

选择协议仅精确审核目录ID/null；不复用事实生成ModelResponse，不收答案/工具/额外字段/重复/尾随/越权ID。Unicode/UTF16输入上限和UTF8请求65536/响应1024字节预算；单worker/单未完成调用、每实例128以内总尝试、每轮5秒以内等待，不重试；cancel不证明外发停止，非协作任务实际退出前BUSY，close不无限等待。{summary}正式CLOSED未装配新组件；真实供应商的物理读取/连接超时/重试/成本及合法ID语义匹配、简单选题基线仍A05后段/C组，不能以prompt声称注入安全。原timeout断言常量重名已修，RED17中只有16项有效行为失败，另1不是业务证据。[证据](../evidence/rule-check-refactor/2026-10-07/r5-selector-20261008-01/acceptance.md)。
''')
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 有界选题协议组件（Task2J / A05第一段，2026-10-08）

独立provider-neutral Transport接收systemConstraints及结构化userPayload（question、审核ruleId/questionScope）；不额外传原文、个人条件/凭据，用户问题可能自行含个人信息，不宣称脱敏。模型仅一个JSON字段ruleId，精确目录ID文本或null；null为不确定/歧义拒绝，无第一项/关键词猜测回退。重复键/尾随/其他字段/答案/工具/错误类型/非法Unicode与越权ID拒绝；合法ID语义错误仍可能发生，须C05人工参考评估。

问题2048 UTF16、目录64项、ID128/scope2048；SYSTEM+结构化payload合计65536 UTF8字节，String返回后解析前1024字节上限。每实例准入尝试1–128总限额、每轮等待至多5秒，失败/超时也计，无隐式重试；单worker/单未完成任务，BUSY不排调用者，executor队列1仅线程handoff。取消不证明供应商停止，实际run退出前保留占用；close拒绝新请求/interrupt，不无限等待非协作任务，diagnostic继续展示未退出状态。

此批只进程内Transport替身，正式CLOSED未装配；String后的预算不证明实际网络读取已限长，真实适配须验证响应读流/连接超时/无重试，供应商用量未知不可估成金额。组件白名单和prompt不证明相关性/注入语义安全/学生收益，A05整体及C组保持OPEN。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';text=plan.read_text(encoding='utf-8');a=text.index('## Task2J / A05第一段：');plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';text=backlog.read_text(encoding='utf-8')
text=text.replace('卡点表和Task2I最终证据','卡点表和Task2J最终证据').replace('30类357项（268纯单元+77专用临时库含12实际HTTP+12凭据相关配置检查）',f'31类{total}项（{counts["unit"]}纯单元+77专用临时库含12实际HTTP+12凭据相关配置检查）').replace('见Task2I证据','见Task2J证据')
text=text.replace('| A05 | 有界自然语言规则选择适配 |','| A05 | 有界自然语言规则选择适配（协议组件已验收，真实适配/语义待续） |')
text=text.replace('[最新Task2I工程证据](../evidence/rule-check-refactor/2026-10-07/r5-clarification-20261008-01/acceptance.md)','[最新Task2J工程证据](../evidence/rule-check-refactor/2026-10-07/r5-selector-20261008-01/acceptance.md)')
backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2J/A05第一段: complete — 原RED17/17（timeout同名常量错误1项不计有效行为RED，其余16项有效）、首GREEN69/1为测试错误，WAIT_BUDGET更名后69PASS；最终{summary}一次增量审查。正式CLOSED/真实资料保护不变，无原库/供应商/部署/commit。\n')
 for ruling in resolution['rulings']:f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution['deferredMinors']:f.write('\nFinal: minor (deferred): '+minor+'\n')
 for fixed in resolution.get('fixed',[]):f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n接续原文定位A06与最终外发A07；A05真实供应商/物理网络/语义及B/C/G2保持OPEN。\n')
after=[];diff=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=sha(root/row['path'])))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=total,classes=len(r['suites']),counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,unchanged=before['unchanged'],database=db,containerExited=True,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text(f'''# R5 Task2J / A05第一段：有界选题协议验收

2026-10-08。{stage}。

|解决的问题|实现|验证|
|---|---|---|
|选题可能越界生成事实/工具或自造规则|只有ruleId/null，精确白名单，不复用答案协议|额外answer/toolCalls/错误类型/重复/尾随/越权ID拒绝；无匹配不取第一项|
|目录/问题/输出不受控消耗预算|Unicode、数量、UTF16及UTF8预算，原始响应解析前限1024字节|空/重复/非法目录及超大中文输入不调用；过长中文/错误Unicode输出拒绝|
|失败重试或超时持续创建外发任务|总尝试有界，无重试；单worker/单current/有界等待|失败计入预算、耗尽拒绝；非协作超时100次BUSY且替身只1调用，release后清零|
|关闭等待供应商无限阻塞、异常正文泄漏|关闭取消/interrupt不无限等待；固定Outcome不返回cause|并发/关闭latch与finally退出、异常不含输入/cause|

原RED17项17断言失败，timeout测试常量误与状态重名，因此仅其余16行为可计有效RED；首GREEN69中1失败比较Duration与TIMEOUT状态，状态正确；更名WAIT_BUDGET后69PASS。旧报告保留，不伪造timeout业务失败证据。最终{summary}0失败/异常/跳过；源码{len(sources)}及XMLhash复算，8项before/after，原DRAFT/20参考/36题/此前rulecheckJava/所有profileYAML/18迁移不变。

一次增量审查见review.md/review-resolution.json。本批纯Transport替身证明协议/资源合同，不是供应商效果、HTTP端语义或网络抓包。响应String预算是在返回后检查，不证明物理读流已受限；真实适配须另验网络超时/读流/无隐式重试/实际用量。单实例总预算不宣称全局/分布式限额。白名单合法或prompt约束不证明选题正确/注入语义安全；简单基线比较与真实问题人工参考留C组。正式CLOSED未装配，A05整体/原文A06/最终外发A07/页面浏览器/B/C/G2仍OPEN。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,migration=db['migration'],sourceFiles=len(sources)),ensure_ascii=False))
