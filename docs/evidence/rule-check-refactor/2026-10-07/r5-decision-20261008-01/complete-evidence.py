"""仅在实际最终回归、合同和审查存在后收口，未集成与未评测项保持开放。"""
from pathlib import Path
import datetime, difflib, hashlib, json, sys
HERE=Path(__file__).resolve().parent; ROOT=HERE.parents[4]
NOW=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat()
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def save(p,x): p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
assert len(sys.argv)==2, '指定实际最终回归批次'
regression=HERE.parent/sys.argv[1]
result=read(regression/'results.json'); snapshot=read(regression/'source-after.json')
assert result['passed'] and result['resourcesVerified'] and result['sourceDrift']==0
assert not result['database'] and not result['providerAllowed']
for item in snapshot: assert sha(ROOT/item['path'])==item['sha256'], item['path']
for suite in result['suites']:
    assert not suite['failures'] and not suite['errors'] and not suite['skipped']
    assert sha(regression/'xml'/suite['xml'])==suite['sha256']
contracts=read(HERE/'runner-contract.json'); assert contracts['passed'] and contracts['checks']==29
review=HERE/'review.md'; assert review.exists()
fixRed=read(HERE.parent/'r5-decision-review-fix-red-20261008-02/results.json')
fixGreen=read(HERE.parent/'r5-decision-review-fix-green-20261008-01/results.json')
assert not fixRed['passed'] and fixRed['testCount']==32 and fixRed['suites'][0]['failures']==3
assert not fixRed['suites'][0]['errors'] and not fixRed['suites'][0]['skipped']
assert fixGreen['passed'] and fixGreen['testCount']==32 and fixGreen['resourcesVerified']
fixEvidence={'findingId':'I1','severity':'Important','status':'FIXED_RED_GREEN_REGRESSION',
    'redPhase':fixRed['phase'],'redTests':32,'redFailures':3,'greenPhase':fixGreen['phase'],'greenTests':32,
    'finalRegressionPhase':regression.name,
    'testNames':['publicBranchDoesNotAskYearUsedOnlyByExcludedProfessionalBranch',
                 'irrelevantUnknownYearCannotRejectOtherwiseCoveredPublicBranch',
                 'missingCategoryIsAskedBeforePotentiallyIrrelevantBranchYear',
                 'professionalBranchStillAsksItsMissingYear',
                 'relevantUnknownYearCannotBeDiscardedToAnswerAnIndependentGeneralFact'],
    'fix':'先以合法已知条件收敛候选，仅校验仍相关的键；非法相关值不能用于过滤分支；按声明顺序只问一个未决条件。',
    'secondReviewPerformed':False}
save(HERE/'review-fix-verification.json',fixEvidence)
reference=ROOT/'docs/product/rule-check-2025-human-reference-development.json'
export=read(HERE/'template-export-verification.json')
assert sha(reference)==export['referenceSha256']
assert sha(ROOT/export['draftPath'])==export['draftSha256']
draft=read(ROOT/export['draftPath'])
assert draft['ruleDefinitionReviewStatus']=='DRAFT' and not draft['runtimeActivated'] and not draft['studentVisible']
assert len(draft['profiles'])==20 and len(draft['clauses'])==10
before=read(HERE/'source-before.json')
diff=[]; after=[]
for item in before['files']:
    p=ROOT/item['path']; assert p.exists()
    old=(HERE/'before'/item['path']).read_text(encoding='utf-8-sig') if item['existed'] else ''
    diff.extend(difflib.unified_diff(old.splitlines(True),p.read_text(encoding='utf-8-sig').splitlines(True),
                                   fromfile='before/'+item['path'],tofile='after/'+item['path']))
    after.append({'path':item['path'],'sha256':sha(p)})
(HERE/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
save(HERE/'source-after.json',after)
save(HERE/'metrics.json',{'recordedAt':NOW,'targetedInitialGreenTests':27,
    'targetedFinalGreenTests':32,'reviewCriticalCount':0,'reviewImportantCount':1,'reviewImportantFixedCount':1,'reviewMinorCount':0,
    'finalRegressionClasses':len(result['suites']),'finalRegressionTests':result['testCount'],
    'runnerContractChecks':29,'reviewPath':review.relative_to(ROOT).as_posix(),
    'ruleReferenceProfiles':20,'clauseLocators':10,'productEvaluationRun':False,
    'databaseRun':False,'providerCalled':False,'studentReleased':False})

issuesPath=ROOT/'docs/evidence/rule-check-refactor/2026-10-07/issues.json'
issues=read(issuesPath); rc4=next(x for x in issues['issues'] if x['id']=='RC-04')
semantics=next(x for x in rc4['subissues'] if x['id']=='RC-04-SEMANTICS')
acceptancePath=(HERE/'acceptance.md').relative_to(ROOT).as_posix()
semantics.setdefault('engineeringComponents',[]).append({
    'name':'RuleCheckDecisionService','stage':'RULE_DECISION_COMPONENT_VERIFIED_NOT_INTEGRATED',
    'evidencePath':acceptancePath,'ruleDefinitionReviewStatus':'DRAFT','manualEvaluationStatus':'NOT_RUN'})
semantics['closureReminder']='已完成独立原文、现有字段真库重读和五状态决策组件；真实规则运行时绑定/资料资格、自然语言匹配、HTTP账号链与实际人工质量尚未通过，旧自由RAG风险仍OPEN'
save(issuesPath,issues)
def append(path,text):
    p=ROOT/path;p.write_text(p.read_text(encoding='utf-8-sig').rstrip()+'\n\n'+text+'\n',encoding='utf-8')
append('docs/product/rule-check-blockers.md',
    '## 2026-10-08 R5 Task2B工程组件收口\n\n'
    f'受控五状态决策组件通过本批最终{len(result["suites"])}类{int(result["testCount"])}项纯单元回归和29项runner合同。真实草案整理20个确认参考profile/10条款，不伪造UUID或将参考确认改标为资料发布。B-03只推进规则组件，旧自由RAG/学生应用/真实质量仍OPEN。\n\n'
    '接续缺口：真实已审片段的source/chunk/revision/hash/跨度绑定、事实独立性及条件/例外的规则定义审核、资料展示资格；随后装配规则选择/提问条件、controlled身份与v2输出链。报告参考与补样仍按B-02推进。工程合同没有模型或数据库调用，不能冒充真实问答与收益。')
append('docs/product/rule-check-review-handoff.md',
    f'**2026-10-08 Task2B接续：**独立规则决策组件及真实DRAFT整理已完成，[决策合同](rule-check-decision-contract.md)。最终{len(result["suites"])}类{int(result["testCount"])}项与29项runner合同通过。下一步补实际片段绑定和规则/资料审核，接v2应用与页面；不以问题ID选择规则，不把合成组件结果当20题产品成绩。')
scope=ROOT/'docs/product/rule-check-scope.md'; text=scope.read_text(encoding='utf-8-sig')
text=text.replace('以下为**目标业务协议，尚未在业务代码实现**。',
                  '以下为**目标业务协议；已实现独立决策组件，尚未装配学生应用链或验收真实回答**。')
scope.write_text(text,encoding='utf-8')
append('docs/product/rule-check-scope.md',
    f'**2026-10-08 R5 Task2B：**已实现服务端审核模板及五状态独立组件，整理20个确认参考profile/10条款的DRAFT，见[决策合同](rule-check-decision-contract.md)。最终{len(result["suites"])}类{int(result["testCount"])}项纯单元回归及29项入口合同通过；事实语义审核、真实片段绑定、学生入口和产品效果未通过，旧自由RAG语义风险保持开放。')
plan=ROOT/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md'; text=plan.read_text(encoding='utf-8-sig')
start=text.index('### Task2B：'); end=text.index('- [ ] 锁定一个具有当前有效',start)
text=text[:start]+text[start:end].replace('- [ ]','- [x]')+text[end:]
plan.write_text(text,encoding='utf-8')
append('.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md',
    f'Task2B: component complete — 最终{len(result["suites"])}类{int(result["testCount"])}项、runner29项，实际批次{regression.name}，源码及XML指纹一致、资源退出。真实DRAFT20个确认参考profile/10条款；未运行数据库/供应商/产品评分。\n\n'
    'Ruling: 本组件使用单快照参考版本，适用规则由可信上层选定；来源核验不证明模板相关性/独立性和条件完整 — 保持首轮2025文本限定与审核职责 — 成本是不能直接宣传任意问题/跨版本政策均已受控。\n\n'
    'Ruling: 保留本计划scratch与before证据，不清理历史dirty、不自动提交或删除工作区 — 当前主R5尚未完成，恢复依赖账本和可追溯增量 — 成本是不能以提交范围作为完整本批证据，必须用白名单哈希。\n\n'
    'Final: fixed I1已排除分支仍追问/误拒绝 — 新合同32项中3断言RED→GREEN32全过，适用分支缺条件与非法值反例保留 — 最终相关回归全过；只执行这一轮fix pass，无第二审查。\n\n'
    'Ruling: 本轮按声明顺序一次追问一个决定性条件，避免同时索取未来可能无关分支信息 — 已补missingCategory反例 — 成本是同时缺多个必需条件时可能需要多轮对话，应用层需观察实际澄清成本。\n\n'
    'Final: Ruling: 审核语义正确性由实际规则审核负责，来源核验不代替相关性/完整性/独立性判断 — 组件边界已明示 — 成本是未经语义审核不得放行真实规则。\n\n'
    'Final: Ruling: 真实PDF来源、传播资格与当前年份适用性保持未放行/未知 — 参考确认不推导资格 — 成本是学生试点仍受B-01约束。\n\n'
    'Final: Ruling: 自然语言匹配、HTTP身份/CSRF与页面未装配，不以未来入口缺失降级为本组件bug — 本批限定工程合同 — 成本是完整应用后必须另验收客户端不能伪造服务端对象。\n\n'
    'Final: Ruling: 顺序重读不声称最终下发或跨条款原子一致性 — 本轮无并发证据 — 成本是装配阶段仍需补撤回窗口。\n\n'
    'Final: Ruling: 公开record仅按可信服务端核验路径消费，不假设任意拼造即暴露给学生 — 当前无HTTP反序列化路径 — 成本是未来装配不得把这些类型直接作为客户端DTO。\n\n'
    'Final: Ruling: 参考profile不是自动生产规则、冲突发现或通用语义证明 — DRAFT未激活且规则声明由审核负责 — 成本是运行时绑定和规则选择仍未完成。\n\n'
    'Final: Ruling: 数据库目标/连接池预算、真实账号/费用/用户收益不外推Task2B单元合同 — 不重复Task2A或编造效果 — 成本是后续实际装配各自验收。\n\n'
    'Final: Ruling: 历史dirty、旧自由RAG与全仓默认测试不外推本批增量 — 保留旧风险 — 成本是RC-04与全仓风险仍OPEN。\n\n'
    '后续Task2/3/4保持未完成；资料绑定/审核、自然语言匹配、最终下发/身份链及实际质量仍需各自证据。')
acceptance=f'''# R5 Task2B验收：受控规则与五状态组件

2026-10-08。状态：RULE_DECISION_COMPONENT_VERIFIED_NOT_INTEGRATED。主R5未完成，旧自由RAG的4→40风险仍OPEN。

解决的问题：原文来源核验尚不能决定完整/部分/澄清/缺证/冲突；新增服务端审核模板、条件分支与逐事实全部依据绑定，复用现有核验器，输出仅取模板文字和核验摘录。缺资料不追问用户，部分事实只保留显式独立且前提匹配项。

真实2025内容整理为20个已确认问题参考profile和10条款定位；规则定义仍DRAFT，实际source/chunk/片段hash/跨度尚未绑定，不自动赋学生展示资格或生产审核状态。

验证：基线22项；首次沙箱AccessDenied及诊断失败均未执行测试，不计RED。实际RED27项/13断言失败/0错误/0跳过；初次GREEN27项。独立审查Critical0/Important1/Minor0，I1已以32项中3失败RED→GREEN32全过修复，未安排第二审查。最终回归{len(result['suites'])}类{int(result['testCount'])}项全过，入口合同29项全过。审查和修复详情见review.md及review-fix-verification.json；历史失败批次保留。源码{len(snapshot)}项与XML指纹已复核，资源退出已记录。

边界：本轮不连接数据库、不调用真实模型，不运行20题产品问答、报告集或用户对照。原文核验和逐绑定顺序重读不能证明模板语义，也不保证最终HTTP窗口或跨条款原子一致性。规则选择/自然语言条件提取、可信身份/CSRF、引用审计及学生页面尚未装配。

设计取舍及成本：复用已有worktree并限定增量（不外推历史dirty）；安全选类代替有历史库/供应商副作用的默认全仓执行（不称全仓全过）；参考确认只导出DRAFT（不宣传已上线）；单快照规则且语义由审核负责（不宣称通用事实证明）；一次只问一个决定性条件（可能增加多条件任务的轮数，需实测）；保留scratch而不自动提交/清理（用白名单/哈希恢复）。

审查未裁决项已逐条留账：模板语义；原件/传播/当期适用；自然语言与HTTP/页面；最终下发及原子快照；公开record越界构造；生产规则自动转换/冲突发现；数据库与账号/费用/收益；历史dirty/旧RAG/全仓测试。对应代价分别为后续实际审核、资料资格、入口验收、并发证据、客户端DTO隔离、真实绑定与选择、运行效果记录、保留原风险，不能用本组件验收关闭。

证据：source-before.json、source-after.json、changes-final.diff、metrics.json、runner-contract.json、review.md；最终回归../{regression.name}/results.json及xml，真实草案见docs/product/rule-check-2025-rule-template-draft.json。

接续：真实发布片段绑定与规则定义/资料展示核验→受控v2问答应用与页面→报告候选补样/审核、实际输出评分及用户查证对照。G2未通过不扩大主题或P2设施。
'''
(HERE/'acceptance.md').write_text(acceptance,encoding='utf-8')
paths=[ROOT/i['path'] for i in after]+[reference,issuesPath,review,
       ROOT/'docs/product/rule-check-scope.md',ROOT/'docs/product/rule-check-blockers.md',
       ROOT/'docs/product/rule-check-review-handoff.md',plan,
       ROOT/'docs/product/rule-check-decision-contract.md',
       ROOT/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md',
       HERE/'metrics.json',HERE/'source-after.json',HERE/'changes-final.diff',HERE/'acceptance.md',HERE/'runner-contract.json',HERE/'review-fix-verification.json']
save(HERE/'verification.json',{'recordedAt':NOW,'passed':True,'stage':'RULE_DECISION_COMPONENT_VERIFIED_NOT_INTEGRATED',
    'finalRegressionPhase':regression.name,'finalRegressionTestCount':result['testCount'],
    'sourceSnapshotFileCount':len(snapshot),'sourceSnapshotVerified':True,'xmlVerified':True,
    'resourcesVerified':result['resourcesVerified'],'productEvaluationRun':False,
    'databaseRun':False,'providerCalled':False,'studentReleased':False,
    'files':[{'path':p.relative_to(ROOT).as_posix(),'sha256':sha(p)} for p in paths]})
print(f'PASS: Task2B evidence finalized; {len(result["suites"])} classes / {int(result["testCount"])} tests; NOT_INTEGRATED.')
