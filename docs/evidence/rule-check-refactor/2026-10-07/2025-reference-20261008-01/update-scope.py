from pathlib import Path
import datetime,hashlib,json
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[4]
paths=['docs/product/rule-check-scope.md','docs/product/rule-check-blockers.md',
 'docs/product/rule-check-review-handoff.md','docs/product/rule-check-eval-contract.md',
 'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md',
 'docs/superpowers/plans/2026-10-07-rule-check-refactor-roadmap.md',
 'docs/evidence/rule-check-refactor/2026-10-07/issues.json',
 '.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md']
assert not (HERE/'source-before.json').exists()
before=[]
for name in paths:
 p=ROOT/name;backup=HERE/'before'/name;backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(p.read_bytes())
 before.append({'path':name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
(HERE/'source-before.json').write_text(json.dumps({'createdAt':datetime.datetime.now().astimezone().isoformat(),'files':before},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def append(name,text):
 p=ROOT/name;p.write_text(p.read_text(encoding='utf-8-sig').rstrip()+'\n\n'+text+'\n',encoding='utf-8')
p=ROOT/paths[0];s=p.read_text(encoding='utf-8-sig').replace('版本：1.4','版本：1.5')
anchor='首期围绕校园教务规则，从一个有可靠资料的主题开始。'
assert anchor in s
s=s.replace(anchor,anchor+'\n\n**2026-10-08用户决定：首轮以2025版手册为准。** 页面、回答与评估标明该版本；2026版获取不再阻断指定2025文本的试验。详见[版本决定](rule-check-2025-reference-decision.md)。资料审核与人工参考仍需完成；当前适用性超出已确认范围时说明缺证。')
s=s.replace('| 现行性 | 版本、生效区间、撤回/取代关系适用于本次查询；时效未知不能默认有效 |','| 版本与时效范围 | 首轮指定2025文本查询核对该版本及范围；当前适用性查询仍需生效区间与撤回/取代依据，未知不能默认有效 |')
p.write_text(s,encoding='utf-8')
p=ROOT/paths[1];s=p.read_text(encoding='utf-8-sig')
s=s.replace('| B-01 | OPEN | G0当前有效资料正文、版本适用性及学生传播资格尚未确认；见[R0验收](../evidence/rule-check-refactor/2026-10-07/r0-baseline-01/acceptance.md) | 真实单主题查证质量结论、学生试点；不阻断独立合成工程反例 | 资料维护者确认完整来源快照、适用对象/时间、公开资格与版本；建立可核对原文及审核记录，再锁定主题 |',
 '| B-01 | OPEN，2025版本已由用户确定 | 首轮固定2025版；本主题原文/范围/条件例外和学生展示资格待核对。2026版获取不再阻断指定2025文本试验；见[版本决定](rule-check-2025-reference-decision.md) | 2025主题正式资料放行与学生试点；当前学年适用性另保持未知 | 核对2025快照、本主题原文位置及范围、展示资格，记录实际审核；回答明确2025依据，版本选择不自动通过审核 |')
p.write_text(s,encoding='utf-8')
append(paths[1],'## 2026-10-08版本决定更新\n\n用户确定先以2025版为准；接续中的“当前资料”对首轮解释试验改为指定2025快照。此前2026正文待取得记录保持历史事实，不再作这一范围的前置条件。人工参考、主题审核与展示资格仍待完成。')
p=ROOT/paths[2];s=p.read_text(encoding='utf-8-sig').replace('已有2025历史PDF与AI辅助案例可供核对，但不能自动当当前2026适用依据。','用户已确定首轮以2025版为准，使用既有2025快照与AI辅助候选作核对起点。回答标明2025依据；不自动推断2026现行性。2026全文不再是这一范围试验的前置条件。')
p.write_text(s,encoding='utf-8')
append(paths[3],'**2026-10-08参考范围决定：**首轮人工参考及产品评估以指定2025版文本为依据，回答显式标版本；不要求先取得2026版。该限定范围可评价查找/理解/原文核对能力，不能由此评价2026现行政策判断。已存在草稿仍待人工逐题核对，原草稿文件与来源审核标记不覆盖。')
append(paths[4],'**2026-10-08用户范围更新：**先以2025版为准。[版本决定](../../product/rule-check-2025-reference-decision.md)将本计划首轮资料门禁绑定2025快照，2026正文获取不再作前置条件；主题审核、展示资格及人工参考仍必需。Task2–4沿指定2025范围接续，当前适用性未知单独处理，不补造审核。')
append(paths[5],'**2026-10-08版本决定：**用户确定首轮以2025版手册为准。范围详见[版本决定](../../product/rule-check-2025-reference-decision.md)。2026资料取得不再阻断指定2025版解释试验；人工审核、资料展示资格与真实质量/收益门禁保持。')
p=ROOT/paths[6];issues=json.loads(p.read_text(encoding='utf-8-sig'))
issues.setdefault('scopeDecisions',[]).append({'decidedDate':'2026-10-08','authority':'EXPLICIT_USER_DECISION',
 'referenceRevision':'2025.9.2','sourceSnapshotSha256':'dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653',
 'referencePolicy':'EXPLICIT_2025_TEXT_SCOPE','requires2026DocumentForThisScope':False,
 'humanReferenceApproved':False,'studentVisibilityChanged':False,'g0Passed':False,
 'decisionPath':'docs/product/rule-check-2025-reference-decision.md'})
p.write_text(json.dumps(issues,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
append(paths[7],'## 2026-10-08用户选定2025版\n\nRuling: 首轮引用与评估固定2025快照，移除2026正文获取前置要求 — 用户明确决定先以2025版为准，优先完成窄范围查证 — 成本是输出显式标版本，不能推断当前年份适用。\n\n版本选择已确认，人工参考和展示资格未确认；不修改旧draft annotation或外部资料标记，不运行数据库/模型。先整理人工参考解释和实际核对记录，Codex不能把本次版本选择当逐题人工审核。')
after=[{'path':name,'sha256':hashlib.sha256((ROOT/name).read_bytes()).hexdigest()} for name in paths]
(HERE/'source-after.json').write_text(json.dumps({'files':after,'codeChanged':False,'databaseRun':False,'providerRun':False,'testsRun':False},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS 2025 scope recorded; 2026 acquisition prerequisite removed for this scope; human review pending')
