from pathlib import Path
import hashlib,json,difflib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[5];batch=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert not (batch/'acceptance.md').exists()
resolution=read(batch/'review-resolution.json');assert resolution['criticalRemaining']==resolution['importantRemaining']==0 and (batch/'review.md').exists()
phase=resolution.get('finalPhase','r5-original-acceptance-20261008-01');directory=batch.parent/phase;r=read(directory/'results.json')
assert r['passed'] and r['exitCode']==0 and r['sourceDrift']==0 and r['resourcesVerified'] and r['containerExited'] and not r['missingXml']
counts={'unit':0,'database':0,'credentialConfiguration':0};classes={k:0 for k in counts}
for suite in r['suites']:
 xml=directory/'xml'/suite['xml'];assert sha(xml)==suite['sha256'];actual=ET.parse(xml).getroot()
 assert int(actual.get('tests'))==suite['tests'] and all(int(actual.get(k,'0'))==0 for k in ['failures','errors','skipped'])
 name=suite['name'].split('.')[-1];group='database' if name in {'JdbcCandidateRetrieverTest','JdbcRuleCheckEvidenceReaderTest','JdbcRuleCheckCitationAuditTest','RuleCheckPreviewHttpTest'} else 'credentialConfiguration' if name=='ControlledPreviewConfigurationTest' else 'unit'
 counts[group]+=suite['tests'];classes[group]+=1
assert sum(counts.values())==r['testCount'] and r['testCount']>=390 and len(r['suites'])==32
sources=read(directory/'source-after.json');assert all(sha(root/x['path'])==x['sha256'] for x in sources)
before=read(batch/'source-before.json');assert all(sha(root/p)==h for p,h in before['unchanged'].items())
db=read(directory/'database.json');assert db['verified'] and db['connectionsAfterTests']==0 and db['migration']=='22' and not read(directory/'database-attempt.json')['volumeMounted']
contract=read(batch/'runner-contract.json');assert contract['passed'] and contract['checks']==29
fix_red=read(batch.parent/'r5-original-review-red-20261008-01'/'results.json')
assert not fix_red['passed'] and fix_red['testCount']==13 and sum(s['failures'] for s in fix_red['suites'])==1
assert sum(s['errors']+s['skipped'] for s in fix_red['suites'])==0 and fix_red['sourceDrift']==0 and fix_red['resourcesVerified']
red=read(batch.parent/'r5-original-red-20261008-01'/'results.json')
assert not red['passed'] and red['testCount']==31 and sum(s['failures'] for s in red['suites'])==13 and sum(s['errors']+s['skipped'] for s in red['suites'])==0 and red['resourcesVerified'] and red['sourceDrift']==0
resolution['verificationStatus']='VERIFIED_FINAL_GREEN';write(batch/'review-resolution.json',resolution)
total=int(r['testCount']);stage='BOUNDED_ORIGINAL_LOCATOR_VERIFIED_NOT_HTTP_ASSEMBLED'
summary=f'32类{total}项（{counts["unit"]}纯单元+{counts["database"]}真库含12HTTP+12凭据配置）、29计数替身合同；无卷55466 V22连接0/退出。'
with (root/'docs/product/rule-check-blockers.md').open('a',encoding='utf-8') as f:f.write(f'''
## 2026-10-08 Task2K / A06第一段原文定位组件

当前资格SQL一次读正文/元数据，绑定固定引用版本及source/chunk/hash/跨度；单片段65536数据库字符，URL/section SQL字节超限抑制。完整核验摘录左右最多256 UTF16，不切代理对、8192 UTF8字节超限拒绝。仅HTTPS精确host/no userinfo/标准端口返回原URL，不fetch/redirect/擅加页锚；页码标RECORDED_SOURCE_PAGE或UNKNOWN，物理/印刷映射未知。元数据纯文本待前端逃逸，不称HTML已净化。

{summary}有效RED31/13；首GREEN游标映射错误7fail+7error、次GREEN夹具违反V2约束1error已修，第三GREEN104PASS，旧报告保留。撤回/分享false夹具同步INTERNAL，是合法组合而非独立谓词因果隔离。内部组件未装配HTTP或Bean，未来需服务端Session引用proof；正式CLOSED/真实2025DRAFT不变，A06后段/原件页码B01/A07最终外发及B/C/G2继续OPEN。[证据](../evidence/rule-check-refactor/2026-10-07/r5-original-20261008-01/acceptance.md)。
''')
with (root/'docs/product/rule-check-decision-contract.md').open('a',encoding='utf-8') as f:f.write('''
## 有界原文定位内部组件（Task2K / A06第一段，2026-10-08）

JDBC read兼容委托readLocated，同一现有字段资格SQL返回正文与title/source_url/source_locator/page_number/section_title。单片段length(content)<=65536数据库字符；URL/section分别4096/8192 UTF8字节超限在SQL为NULL，不搬运无限元数据。当前行完整读取再检查rows.next唯一；所有已有profile/run/scope/版本/hash/学生展示与分享条件保留。此预算是此reader单片段，不宣称整条检索链下载限制或PDF页数限制。

内部定位只接服务端已核验摘录，固定revision、当前一次SQL持久身份/hash/资格匹配，复用Verifier memo校验本次正文hash/Unicode/span/摘录一致。memo不是第二次fresh DB；公开VerifiedExcerpt构造器不授权HTTP输入。返回同轮citation别名、元数据、有界context及原chunk UTF16跨度；完整quote左右最多256单位且不切代理对，context<=8192 UTF8字节，超限无正文拒绝而不截断quote。失败只有固定Code，不含旧正文/cause。

记录页码仅RECORDED_SOURCE_PAGE；缺页UNKNOWN，物理与印刷页映射待B01，不猜PDF页或造#page。SourceURL仅https、显式精确host、无userinfo、默认/443端口、合法URI和2048字符；只返回原字段，不服务端抓取/跟随重定向，不验证远端实际文件/版本。标题/条款/页码/地址缺失明确unknown；文本必须在未来前端逃逸渲染，不称本组件已净化HTML。原文HTTP Session引用绑定、真实点击/页码映射与最终当前身份/资料再核验窗口仍A06后段/A07，正式CLOSED不变。
''')
plan=root/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';text=plan.read_text(encoding='utf-8');a=text.index('## Task2K / A06第一段：');plan.write_text(text[:a]+text[a:].replace('- [ ]','- [x]'),encoding='utf-8')
backlog=root/'docs/product/rule-check-remaining-tasks-2026-10-08.md';text=backlog.read_text(encoding='utf-8')
text=text.replace('卡点表和Task2J最终证据','卡点表和Task2K最终证据').replace('31类374项（285纯单元+77专用临时库含12实际HTTP+12凭据相关配置检查）',f'32类{total}项（{counts["unit"]}纯单元+81专用临时库含12实际HTTP+12凭据相关配置检查）').replace('见Task2J证据','见Task2K证据')
text=text.replace('| A06 | 原文查询与定位入口 |','| A06 | 原文查询与定位入口（内部定位已验收，HTTP/实际页码待续） |')
text=text.replace('[最新Task2J工程证据](../evidence/rule-check-refactor/2026-10-07/r5-selector-20261008-01/acceptance.md)','[最新Task2K工程证据](../evidence/rule-check-refactor/2026-10-07/r5-original-20261008-01/acceptance.md)')
backlog.write_text(text,encoding='utf-8')
with (root/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md').open('a',encoding='utf-8') as f:
 f.write(f'\nTask2K/A06第一段: complete — 有效RED31/13，首GREEN104/7fail+7error为游标元数据映射、次104/1error为违反V2夹具，修复后104PASS；最终{summary}一次增量审查；原2025DRAFT/许可保护不变，无原库/模型/部署/commit。\n')
 for ruling in resolution['rulings']:f.write('\nFinal: Ruling: '+ruling+'\n')
 for minor in resolution['deferredMinors']:f.write('\nFinal: minor (deferred): '+minor+'\n')
 for fixed in resolution.get('fixed',[]):f.write('\nFinal: fixed '+fixed+'\n')
 f.write('\n接续A06原文HTTP服务端引用绑定与A07最终展示边界；真实文件/页码B01、供应商A05后段/C与学生浏览器/G2保持OPEN。\n')
after=[];diff=[]
for row in before['files']:
 data=(root/row['path']).read_bytes();after.append(dict(path=row['path'],sha256=sha(root/row['path'])))
 old=(batch/row['before']).read_bytes() if row['before'] else b''
 diff.extend(difflib.unified_diff(old.decode('utf-8').replace('\r\n','\n').splitlines(True),data.decode('utf-8').replace('\r\n','\n').splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(batch/'source-after.json',after);(batch/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
write(batch/'metrics.json',dict(stage=stage,phase=phase,tests=total,classes=len(r['suites']),counts=counts,suiteClasses=classes,runnerContractChecks=29,sourceFiles=len(sources),sourceDrift=0,xmlHashesVerified=True,unchanged=before['unchanged'],database=db,containerExited=True,studentVisible=False,qualityGatePassed=False,realProviderEvaluated=False,userBenefitEvaluated=False,review=resolution))
(batch/'acceptance.md').write_text(f'''# R5 Task2K / A06第一段：有界原文定位验收

2026-10-08。{stage}。

|解决的问题|实现|验证|
|---|---|---|
|引用可能指向撤回或变化的正文|固定revision/current身份hash资格，当前一次SQL正文/元数据同读|实际DB元数据、撤回/内部/分享false合法组合；旧reader版本/正文hash/profile/cut矩阵保留|
|读取整个大段或无限TEXT元数据|SQL单片段65536数据库字符、URL/section字节超限NULL|实际65537字符chunk拒绝；URL4097/section8193 bytes不返回，正文仍正确|
|显示上下文截断摘录、跨度乱码|完整quote左右有界、UTF16跨度与代理对边界、8192 UTF8字节|quote文字/hash/span/Unicode反例与emoji上下文；超大quote无正文拒绝|
|原文地址可成为危险链接、页码映射被猜造|https精确host/no userinfo/标准端口；页码仅记录值，不造锚|javascript/data/file/http/伪host/userinfo/异常端口拒绝；原片段/页映射未知明确|
|缺元数据或异常被伪装成定位成功|明确unknown，失败无原文/cause|12组件检查；标题/条款纯文本为未来逃逸渲染数据，不称HTML净化|

有效RED31/13（12unit9失败+19DB4失败），0异常/跳过；首GREEN104有7fail+7error，游标离行后读取metadata错误，先完整读当前行修复；次104仅1error，夹具撤回仍STUDENT_VISIBLE违反V2，改同步INTERNAL，未放宽constraint；第三GREEN104通过。旧报告保留，不把错误都计为有效业务RED。分享false组合含INTERNAL，不声称单独隔离SQL分享谓词。最终{summary}0失败/异常/跳过；源码{len(sources)}及XMLhash复算，11项before/after，原DRAFT/20参考/36题/其余rulecheckJava/profileYAML/18迁移不变。

一次增量审查见review.md/review-resolution.json。此批内部组件无原文HTTP/Bean装配，公开VerifiedExcerpt不能当客户端授权。复用Verifier的memo仅校验本次当前SQL快照，不是第二次fresh DB或最终外发原子性；未来Session引用proof/最终身份和资料复查仍A06后段/A07。地址协议白名单不证明远端文件/版本，记录页码不证明物理/印刷页；B01真实原件映射仍OPEN。正式目录CLOSED、真实2025DRAFT/不可见不变；真实供应商/语义/浏览器/用户收益及G2未通过。
''',encoding='utf-8')
write(batch/'integrity-check.json',dict(passed=True,artifacts={p.name:sha(p) for p in batch.iterdir() if p.is_file() and p.name!='integrity-check.json'},currentWhitelist=after,unchanged=before['unchanged'],sourceFiles=len(sources),xmlVerified=True))
print(json.dumps(dict(passed=True,counts=counts,classes=classes,migration=db['migration'],sourceFiles=len(sources)),ensure_ascii=False))
