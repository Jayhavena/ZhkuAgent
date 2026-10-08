"""只根据已核对的实际批次收口，不将工程通过换算为产品质量。"""
from pathlib import Path
import datetime, difflib, hashlib, json

HERE = Path(__file__).resolve().parent
BASE = HERE.parent
ROOT = HERE.parents[4]
now = datetime.datetime.now().astimezone().isoformat()
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p, value): p.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def append(p, text): p.write_text(p.read_text(encoding='utf-8-sig').rstrip()+'\n\n'+text+'\n', encoding='utf-8')

verification = read(HERE/'verification.json')
assert verification['passed'] and verification['tests']==151 and verification['rollbackChecks']==22
assert not (HERE/'acceptance.md').exists(), '禁止重复覆写收口'
r4=BASE/'r4-read-20261008-01'
metrics={'executedAt':now,'scope':'SYNTHETIC_ENGINEERING_ONLY','r4BehaviorRed':{'tests':22,'failures':11,'errors':0,'skipped':0},
 'r4TargetedGreen':{'tests':24,'failures':0},'finalRegression':{'classes':20,'tests':151,'failures':0,'errors':0,'skipped':0},
 'runnerContract':29,'rollbackChecks':22,'jdbcRows':{'syntheticIndexRows':3,'before':3,'after':1,'notDatabaseScanLimit':True},
 'providerCalled':False,'studentQualityEvaluation':'NOT_RUN','userBenefit':'NOT_RUN','fullLifecycle':'NOT_RUN','g0Passed':False}
write(r4/'metrics.json',metrics)
r4text='''# R4 验收：在线激活与已知资料资格

执行日期：2026-10-08。RC-05-READ 工程修复通过；完整时间、取代与发布清单仍开放。

默认词法/向量入口要求 ACTIVE；显式离线评测工厂允许 BUILDING/ACTIVE，仍拒绝 RETIRED。向量在调用 embedding 前校验状态/维度，在调用后查询再次核对。SQL 过滤发布/学生可见/分享权、scope、完成run、run/profile/切分版本、source版本及chunk校验和一致性。向量过滤后精确排序并 LIMIT，词法保持原评分排序。最终Java资格检查保留。

行为 RED：[22项/11断言失败](../r4-20261008-red-03/results.json)，错误/跳过0。先前编译失败与非法数据库夹具批次保留，未当行为RED。[定向GREEN](../r4-20261008-green-01/results.json)24项通过。最终审查补修后[20类151项回归](../p0-20261008-final-01/results.json)通过，无失败/错误/跳过/源码漂移；[收口核验](../p0-closeout-20261008-01/verification.json)逐份核对XML与当前源码。

三条合成索引的实际JDBC读取由3行降为1行；[旧计划](../r4-20261008-red-03/r4-vector-query-plan.json)和[新计划](../r4-20261008-green-01/r4-vector-query-plan.json)记录真实 EXPLAIN ANALYZE。LIMIT不限制数据库扫描量，本批没有生产性能或真实召回提升结论。

独立审查发现新增测试激活可能在裸运行时改原库ACTIVE。补类内严格临时目标检查及BeforeEach失败后的清理保护。纯单元目标拒绝RED8项7失败、生命周期RED9项1失败；最终目标/实际setup/cleanup合同9项通过，真库24项仍通过。成本：直接运行该类必须显式专用临时URL，其他旧类默认地址风险仍开放。

数据库操作只在随机无卷临时容器，目标127.0.0.1:55439/zhkuagent_rulecheck_e2e，迁移V21，连接0、容器和新增Java退出。检索与eval为确定性合成夹具，未调用真实供应商。真实向量评测类仅编译，未执行。完整时间字段仍未映射，元数据一致性不代表核验原PDF字节或审核质量。G0未通过，没有学生试点或用户收益。

回退：先停止受影响入口，按before字节和当前哈希逐文件审查恢复。专用样例完成22项恢复/新增文件移除演练，未回退真实工作区；历史证据保留。
'''
(r4/'acceptance.md').write_text(r4text,encoding='utf-8')
(HERE/'acceptance.md').write_text('''# P0 局部工程修复技术验证

2026-10-08：R1融合截断、R2管理原文对照、R3结构守卫、R4在线读取资格的计划范围已完成工程验证。G1仅按P0局部工程合同判定通过；G0当前资料/人工参考未通过，校园查证上线与用户收益均未验证。

[最终批次](../p0-20261008-final-01/results.json)：20类151项，失败/错误/跳过/漂移0。专用临时库V21，连接0，容器和新增Java退出，无真实模型调用。[核验清单](verification.json)逐份核对XML和当前源码；[入口合同](runner-contract-final.json)29项通过；[回退演练](rollback-rehearsal.json)22项通过，只恢复隔离样例，未改变真实工作区。

[独立审查](review.md)发现1项Important、无Critical/Minor。P0-REVIEW-01已按RED→GREEN修复：新R4测试在迁移/连接前严格拒绝非专用目标，setup拒绝后的cleanup不连接；保留真实激活及状态反例。审查者核对的是修复前142项，最终151项由执行者实际重跑核验，不能把两者混为一次审查。

| 审查未判定范围 | 执行者裁定 |
| --- | --- |
| 当前资料/2026适用性/传播资格/人工参考 | B-01/B-02，G0仍不通过 |
| 一般事实支持、个人资格、数字/否定/条件/例外/完整注入 | RC-04语义OPEN；旧4→40仍放行，R5接管 |
| 完整时间/取代/清单/不同切分版本选择/索引完整性 | RC-05生命周期OPEN，R6后续；R4仅存储字段一致性 |
| 检索后至输出的撤回/权限竞态 | R5输出前复核待实现，R4仅覆盖embedding窗口 |
| 10/20/40真实预算、召回/查证耗时 | NOT_RUN；默认20未证明最优 |
| 真实模型格式违约/付费供应商/学生E2E/旧3C全量 | NOT_RUN；本批未改变controlled/身份装配 |
| 重复JSON键/尾随JSON/额外字段/完整Schema/精确小数政策 | R3范围外，严格类型不等于完整Schema |
| PDF上传/提取/OCR/原件/跨页 | NOT_RUN，R2使用SQL合成提取态候选 |
| 后端分页/大文档网络内存 | NOT_RUN，20条仅DOM上限 |
| 网络在途编辑/重复点击/异步加载草稿竞态 | 未验收，后续管理链专项；不扩张R2草稿保证 |
| 其他旧DB类直接运行/IDE绕锁/全仓安全 | RC-10旧默认风险OPEN，新R4激活风险已修复 |
| 全机外网抓包/生产规模性能/部署准备 | 未执行；选类与小库计划不能代替 |
| 最终台账/哈希/回退演练 | 本次已实际完成，verification与rollback为依据 |

用户已授权连续实施，下一步沿用[R5细化计划](../../../../superpowers/plans/2026-10-08-rule-check-r5-plan.md)推进独立原文组件；真实资料及人工评分的依赖见[卡点表](../../../../product/rule-check-blockers.md)。不重构公共runtime，不提交/推送/部署，不清理历史dirty工作区。
''',encoding='utf-8')
issuePath=BASE/'issues.json'; issues=read(issuePath)
issues['executionHistory'].append(issues['latestExecution'])
issues['latestExecution']={'stage':'P0_LOCAL_ENGINEERING_VERIFIED_G0_PENDING','executedAt':now,
 'evidencePath':str((HERE/'acceptance.md').relative_to(ROOT)).replace('\\','/'),'testsExecuted':True,
 'businessCodeChanged':True,'testCount':151,'testClassCount':20,'databaseOperationRun':True,
 'databaseScope':'DEDICATED_RANDOM_TEMP_CONTAINER_SYNTHETIC_ONLY','providerCalled':False,
 'productEvaluationRun':False,'g0':'NOT_PASSED_SOURCE_AND_HUMAN_REFERENCE_PENDING','planningTurnFieldsAreHistorical':True}
issues['deliveryStage']='P0_LOCAL_ENGINEERING_VALIDATION'
rc5=next(x for x in issues['issues'] if x['id']=='RC-05'); rc5['status']='PARTIAL';rc5['verificationStatus']='READ_PASSED_LIFECYCLE_OPEN'
part=rc5['subissues'][0];part.update(status='CLOSED',verificationStatus='PASSED',closedAt=now,
 evidencePath=str((r4/'acceptance.md').relative_to(ROOT)).replace('\\','/'),
 closureScope='仅ACTIVE与现有schema资格/版本/hash/run一致性；完整时效与输出前竞态未完成')
part['verifiedSourceHashes']={x['path']:x['sha256'] for x in verification['sourceHashes'] if x['path'] in rc5['sourcePaths'][:2]}
rc10=next(x for x in issues['issues'] if x['id']=='RC-10')
rc10['subissues'][1]['problem']='其他旧KnowledgeSchemaTest等直接运行仍存在默认原库风险；R4 JdbcCandidateRetrieverTest本轮已在setup/cleanup前拒绝不安全目标，不能据此关闭全仓旧默认风险'
rc10['subissues'].append({'id':'RC-10-R4-ACTIVATION','task':'R4_REVIEW_FIX','status':'CLOSED','verificationStatus':'PASSED','evidencePath':str((HERE/'acceptance.md').relative_to(ROOT)).replace('\\','/')})
issues['reviewFindings']=[{'id':'P0-REVIEW-01','severity':'Important','status':'FIXED_VERIFIED','evidencePath':str((HERE/'acceptance.md').relative_to(ROOT)).replace('\\','/')}]
write(issuePath,issues)
p=ROOT/'docs/superpowers/plans/2026-10-07-rule-check-p0-plan.md';txt=p.read_text(encoding='utf-8-sig');txt=txt.replace('Task 5–6未开始。2026-10-08执行R3。','Task 5–6已完成局部工程技术验证（最终20类151项，G0仍未通过）。2026-10-08执行R3/R4及独立审查补修。')
start=txt.index('## Task 5：');end=txt.index('## 2. 每个任务',start);txt=txt[:start]+txt[start:end].replace('- [ ] **Step','- [x] **Step')+txt[end:];p.write_text(txt,encoding='utf-8')
append(p,'**P0最终实际：**[收口验收](../../evidence/rule-check-refactor/2026-10-07/p0-closeout-20261008-01/acceptance.md)。独立审查1项Important已RED→GREEN修复；20类151项、合同29项、回退样例22项通过。G1为局部工程验证，G0/产品质量/用户收益仍未通过。用户已授权连续执行，下一阶段可独立部分按R5计划推进。')
p=ROOT/'docs/superpowers/plans/2026-10-07-rule-check-refactor-roadmap.md';txt=p.read_text(encoding='utf-8-sig');start=txt.index('### R4 /');end=txt.index('### R5 /',start);section=txt[start:end].replace('- [ ]','- [x]');txt=txt[:start]+section+'**实际：**[R4验收](../../evidence/rule-check-refactor/2026-10-07/r4-read-20261008-01/acceptance.md)，24项真库合成定向通过；P0最终20类151项通过。RC-05读取CLOSED、生命周期OPEN，G0未通过。\n\n'+txt[end:];p.write_text(txt,encoding='utf-8')
append(ROOT/'docs/product/rule-check-scope.md','**2026-10-08补充：**R4和P0局部工程收口完成，最终20类151项通过，独立审查测试目标风险已补修；[收口证据](../evidence/rule-check-refactor/2026-10-07/p0-closeout-20261008-01/acceptance.md)。在线已知资格落实不代表完整时效、事实语义或用户收益。G0仍未通过；用户已授权连续实施，R5先推进独立原文组件，资料/人工参考卡点留账。')
append(BASE/'README.md','## 2026-10-08 P0收口\n\n[R4验收](r4-read-20261008-01/acceptance.md)与[P0收口](p0-closeout-20261008-01/acceptance.md)：20类151项、入口合同29项、样例恢复22项通过。RC-05-READ关闭；语义、生命周期、其他旧默认DB风险仍开放。G0/真实产品评估/用户收益未通过。')
append(ROOT/'.superpowers/sdd/2026-10-07-rule-check-p0-plan/progress.md','## Task5/Task6 final：2026-10-08\n\nTask5 VERIFIED_READ_LIFECYCLE_OPEN；Task6 LOCAL_ENGINEERING_VERIFIED_G0_PENDING。目标guard RED8项7失败→GREEN；真实setup/cleanup生命周期RED9项1失败→最终9项通过。最终Acceptance20类151项全过，合同29，XML/当前源码一致，资源退出；隔离恢复样例22项通过。\n\nRuling: 独立审查13项未判定逐项保留在收口报告，资料/语义/时效/规模/网络竞态/供应商/用户收益保持未完成 — 工程证据仅证明所测合同 — 成本是产品试点必须另过门禁。\n\nRuling: 不删除本计划账本和证据、不提交历史dirty文件 — 回退和职责证据必须保留且用户未授权推送 — 成本是工作区仍dirty。\n\nRuling: 连续授权允许进入R5可独立Task1，不等同通过G0或扩张P2 — 遵守用户持续实施指令与产品门禁 — 成本是新组件暂不接学生入口。详见R5独立计划/账本。')
after=[];diff=[]
for row in read(r4/'source-before.json')['files']:
 p=ROOT/row['path'];after.append({'path':row['path'],'exists':p.exists(),'sha256':sha(p) if p.exists() else None})
 before=r4/'before'/row['path'];old=before.read_text(encoding='utf-8-sig') if before.exists() else '';new=p.read_text(encoding='utf-8-sig') if p.exists() else ''
 diff.extend(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path']))
write(r4/'source-after.json',after);(r4/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
for directory in [r4,HERE]:
 paths=[p for p in directory.iterdir() if p.is_file() and p.name!='integrity-check.json']
 write(directory/'integrity-check.json',{'verifiedAt':now,'passed':True,'files':[{'path':p.relative_to(ROOT).as_posix(),'sha256':sha(p)} for p in paths]})
print('PASS R4/P0 reports and issue boundaries recorded; G0 NOT PASSED')
