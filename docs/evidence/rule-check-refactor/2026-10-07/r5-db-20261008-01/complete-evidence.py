"""R5可独立部分收口；执行前须读完review并处理阻塞项。"""
from pathlib import Path
import argparse, datetime, difflib, hashlib, json
import xml.etree.ElementTree as ET

HERE=Path(__file__).resolve().parent;BASE=HERE.parent;ROOT=HERE.parents[4]
parser=argparse.ArgumentParser();parser.add_argument('--phase',required=True);args=parser.parse_args()
assert '/' not in args.phase and args.phase.replace('-','').isalnum()
BATCH=BASE/args.phase
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,x):p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def append(p,t):p.write_text(p.read_text(encoding='utf-8-sig').rstrip()+'\n\n'+t+'\n',encoding='utf-8')
result=read(BATCH/'results.json');database=read(BATCH/'database.json');now=datetime.datetime.now().astimezone().isoformat()
assert result['passed'] and result['resourcesVerified'] and result['sourceDrift']==0 and not result['missingXml']
assert not result['newJavaRemaining'] and not result['providerAllowed'] and result['containerExited']
assert database['verified'] and database['connectionsAfterTests']==0
assert database['target']=='jdbc:postgresql://127.0.0.1:55439/zhkuagent_rulecheck_e2e'
for row in read(BATCH/'source-after.json'):assert sha(ROOT/row['path'])==row['sha256'],row['path']
count=0
for row in result['suites']:
 p=BATCH/'xml'/row['xml'];assert sha(p)==row['sha256'];suite=ET.parse(p).getroot()
 assert all(int(suite.attrib.get(k,0))==0 for k in ['failures','errors','skipped'])
 assert int(suite.attrib['tests'])>0;count+=int(suite.attrib['tests'])
assert count==result['testCount']
contract=read(HERE/'runner-contract-final.json');assert all(x['passed'] for x in contract['results'])
assert (HERE/'review.md').exists() and not (HERE/'acceptance.md').exists()
component=BASE/'r5-evidence-20261008-01';assert (component/'review.md').exists()
verification={'verifiedAt':now,'phase':args.phase,'passed':True,'classes':len(result['suites']),
 'tests':count,'contractChecks':len(contract['results']),'databaseScope':'DEDICATED_RANDOM_TEMP_CONTAINER_SYNTHETIC',
 'migration':database['migration'],'connectionsAfterTests':0,'resourcesVerified':True,'providerCalled':False,
 'g0Passed':False,'qualityEvaluationRun':False,'studentIntegrated':False,'sourceHashes':read(BATCH/'source-after.json')}
write(HERE/'verification.json',verification)
write(HERE/'metrics.json',{'sampleKind':'SYNTHETIC_ENGINEERING_CONTRACT','excerptTargeted':19,
 'dbAdapterRed':{'tests':18,'failures':6,'errors':0,'skipped':0},'dbAdapterTargetedGreen':37,
 'latestRelatedRegression':{'classes':len(result['suites']),'tests':count,'failures':0,'errors':0,'skipped':0},
 'trueDatabaseCases':15,'newNoDatabaseTargetCases':3,'runnerContract':29,'qualityAccuracy':'NOT_MEASURED',
 'studentTimeSaved':'NOT_MEASURED','realProviderCost':'NOT_MEASURED','oldRagSemanticGap':'OPEN'})
(component/'acceptance.md').write_text('''# R5 Task1：原文跨度核验组件

2026-10-08，阶段 COMPONENT_VERIFIED_NOT_INTEGRATED。四个新生产类型、一个测试；确定性核验，无新依赖/模型/学生接口。新组件对合法ID与位置但4→40的提议明确拒绝，失败不输出正文；未知/重复ID、UTF16跨度/Unicode、UTF8 SHA256、当前资格/身份/修订/hash/text及reader异常分别有合同。只有通过的server substring进入产物。

RED19项17断言失败、0错误/跳过；[GREEN19项](../r5-20261008-green-01/results.json)，[15类130项相关回归](../r5-20261008-regression-01/results.json)。这证明新组件合同，不能说旧RAG4→40已修。[独立审查](review.md)无Critical/Important，1项Minor：尚缺latest别名不同的独立正向单测，记录为延后；后续DB适配正常流程已用不同current别名通过，但不改写本次审查结论。

Task2A后来增加DB reader及runner选类，当前源码/白名单以[后续最终核验](../r5-db-20261008-01/verification.json)为准；本批组件源码未变。原文相等不证明相关、条件/例外完整、真实适用/传播资格，也不保证输出前最后一次读后的撤回窗口消失。服务端record是内部数据类型，公开构造不是授权证明；学生端不能反序列化这些快照自证资格。

RC-04-SEMANTICS保持OPEN，G0/真实模板/人工评分/G2收益未通过。回退清单见source-before/changes-final.diff，未恢复原工作区，不删历史证据。
''',encoding='utf-8')
(HERE/'acceptance.md').write_text(f'''# R5 Task2A：输出前真库复核准备

2026-10-08，阶段 DB_ADAPTER_VERIFIED_NOT_INTEGRATED。新增JdbcRuleCheckEvidenceReader按服务端profile、切分版本、scope及source/chunk精确读取；只允许ACTIVE和现有schema可表达的资料/index资格，参数绑定，不重新检索或排序，不加controller/迁移/依赖。

核验器与adapter联合在真实临时数据库验证：首次读后撤回、内部化、分享资格/scope改变、profile退役/BUILDING、run异常、source版本漂移/同步换版、正文变而hash没变、正文和hash一起变、切分版本不回退。全部为顺序读写间变更；没有压力并发或线性化证明。SQL快照之后到HTTP输出仍有窗口，当前学生HTTP链未装配。

RED：[18项6断言失败/0错误跳过](../r5-db-20261008-red-02/results.json)。首批一项orElseThrow错误已保留，改断言形式后才记干净RED。GREEN：[3类37项](../r5-db-20261008-green-01/results.json)。最终相关回归：[18类{count}项](../{args.phase}/results.json)，失败/错误/跳过/漂移0；[逐份XML和当前源码核验](verification.json)，[入口合同29项](runner-contract-final.json)。新15项真库合同与3项目标/连接失败纯单元单列，不能换算为质量准确率。

随机无卷临时库127.0.0.1:55439/zhkuagent_rulecheck_e2e，V21，连接0，容器与新增Java退出；未访问原演示库、未调用供应商。新DB测试类在setup迁移/连接前严格验证临时URL，拒绝后的cleanup不连接，真实方法纯单元已覆盖。

独立审查无Critical/Important，1项Minor：run/entry跨profile的专门反例未补，当前SQL正确阻断，作为覆盖缺口留账。见[审查报告](review.md)和执行账本。eligible=true仅表达所查存储字段通过；完整时间/取代/清单仍待R6，真实审核/个人适用/自由回答事实不由本组件证明。旧RAG4→40仍OPEN，五业务状态、审核模板和学生界面未实现；G0/G2未通过。

分享/撤回反例同时内部化以符合库约束，不声称独立隔离证明每个SQL谓词；正文变但hash未变由正文比较首先阻断，不声称该真库场景隔离证明了重新hash分支。重复结果防御与异常资源关闭做静态审查，未作故障注入。生产连接池/连接超时预算尚无应用装配，异常返回能受控拒绝不代表连接阻塞有时间上界。

回退先停止将来可能装配的v2入口，按本包before/新增文件及当前hash审查增量；此刻无学生入口可停。本批未回退工作区，历史失败与P0恢复样例保留。其余任务依赖见[卡点表](../../../../product/rule-check-blockers.md)。
''',encoding='utf-8')
issuePath=BASE/'issues.json';issues=read(issuePath);issues['executionHistory'].append(issues['latestExecution'])
issues['deliveryStage']='R5_EVIDENCE_COMPONENTS_VERIFIED_NOT_INTEGRATED'
issues['latestExecution']={'stage':'R5_EVIDENCE_AND_DB_ADAPTER_VERIFIED_NOT_INTEGRATED','executedAt':now,
 'evidencePath':(HERE/'acceptance.md').relative_to(ROOT).as_posix(),'testsExecuted':True,'businessCodeChanged':True,
 'testCount':count,'testClassCount':len(result['suites']),'databaseOperationRun':True,
 'databaseScope':'DEDICATED_RANDOM_TEMP_CONTAINER_SYNTHETIC_ONLY','providerCalled':False,'productEvaluationRun':False,
 'studentIntegrated':False,'g0':'NOT_PASSED_SOURCE_AND_HUMAN_REFERENCE_PENDING','semanticKnownRisk':'OLD_RAG_4_TO_40_STILL_OPEN',
 'planningTurnFieldsAreHistorical':True}
rc4=next(x for x in issues['issues'] if x['id']=='RC-04');sem=next(x for x in rc4['subissues'] if x['id']=='RC-04-SEMANTICS')
sem['status']='OPEN';sem['engineeringComponents']=[
 {'name':'RuleCheckEvidenceVerifier','stage':'COMPONENT_VERIFIED_NOT_INTEGRATED','evidencePath':(component/'acceptance.md').relative_to(ROOT).as_posix()},
 {'name':'JdbcRuleCheckEvidenceReader','stage':'DB_ADAPTER_VERIFIED_NOT_INTEGRATED','evidencePath':(HERE/'acceptance.md').relative_to(ROOT).as_posix()}]
sem['closureReminder']='只证明摘录来源一致及现有字段输出前真库重读；审核模板/条件例外/账号链/人工真实评分仍未通过'
issues.setdefault('reviewFindings',[]).extend([
 {'id':'R5-TASK1-REVIEW-M1','severity':'Minor','status':'DEFERRED_COVERAGE','problem':'当前别名不同的独立组件正向用例未补；实现正确，后续DB正常流程已消费不同别名','evidencePath':(component/'review.md').relative_to(ROOT).as_posix()},
 {'id':'R5-DB-REVIEW-M1','severity':'Minor','status':'DEFERRED_COVERAGE','problem':'缺run/entry跨profile专项真库反例；当前SQL正确阻断','evidencePath':(HERE/'review.md').relative_to(ROOT).as_posix()}])
write(issuePath,issues)
p=ROOT/'docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md';txt=p.read_text(encoding='utf-8-sig')
start=txt.index('## Task1：');end=txt.index('## Task2：',start);txt=txt[:start]+txt[start:end].replace('- [ ]','- [x]')+txt[end:]
start=txt.index('### Task2A：');end=txt.index('- [ ] 锁定一个',start);txt=txt[:start]+txt[start:end].replace('- [ ]','- [x]')+txt[end:]
txt=txt.replace('P0工程收口进行中，进入Task1前必须确认最终回归与审查补修证据。','P0工程收口已完成20类151项及独立审查补修；Task1和Task2A已验证，尚未装配学生入口。')
p.write_text(txt,encoding='utf-8');append(p,f'**本批实际：**Task1 19项定向，Task2A 15项真库+3项目标/故障合同；最终18类{count}项相关回归通过，合同29项。见[R5组件](../../evidence/rule-check-refactor/2026-10-07/r5-evidence-20261008-01/acceptance.md)和[DB适配](../../evidence/rule-check-refactor/2026-10-07/r5-db-20261008-01/acceptance.md)。完整Task2/3/4仍待外部审核与真实参与者；不将部分勾选解释为整个R5完成。')
append(ROOT/'docs/product/rule-check-blockers.md','## 2026-10-08可独立工程已推进\n\nP0局部收口、R5原文组件与真库当前读取准备已验证，尚未装配学生入口。B-03仅完成摘录来源/现有字段复核部分，旧RAG4→40仍开放。Task2A不等待B-01/B-02已实施；剩余真实审核模板/五状态应用/学生页面依赖B-01/B-02，16题质量与5–8位用户对照还需真实审核者/参与者。\n\n接续顺序：确认当前资料完整来源、适用与传播资格→人工审核规则模板及参考题→Task2应用/账号/输出链真验收→Task3页面→Task4真实质量与用户对照。不需要再次说“开始下一步”来批准同一已授权范围；缺少外部证据时不能伪造审核或参与者。')
append(ROOT/'docs/product/rule-check-scope.md',f'**2026-10-08 R5工程准备：**原文核验与真库重读适配已完成确定性验收，最新相关18类{count}项通过，见[R5证据](../evidence/rule-check-refactor/2026-10-07/r5-db-20261008-01/acceptance.md)。阶段NOT_INTEGRATED；五业务状态/审核模板/学生闭环仍未实现，不能宣传旧自由RAG数字错误已修。')
append(ROOT/'docs/superpowers/plans/2026-10-07-rule-check-refactor-roadmap.md','**2026-10-08连续实施补充：**R5只完成独立原文组件与Task2A真库重读准备，[任务级计划](2026-10-08-rule-check-r5-plan.md)。主R5的应用/真实审核/学生质量/用户收益条目仍不勾选；R6–R8扩张仍按G2门禁。')
append(BASE/'README.md',f'## 2026-10-08 R5独立准备\n\n[原文组件](r5-evidence-20261008-01/acceptance.md)、[真库适配](r5-db-20261008-01/acceptance.md)：19项组件、15项真库、3项目标/故障合同；最新相关18类{count}项、入口合同29项通过。NOT_INTEGRATED，旧语义风险、G0/G2、完整时效仍OPEN。')
append(ROOT/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md',f'## Task1/Task2A final\n\nTask1 COMPONENT_VERIFIED_NOT_INTEGRATED，Task2A DB_ADAPTER_VERIFIED_NOT_INTEGRATED。DB RED18项6断言失败/0错误跳过→GREEN3类37项；最终18类{count}项通过、合同29，XML/current源码一致，临时库连接0/容器与新Java退出。无模型/浏览器/真实质量/用户实验。\n\nTask2A red-01正常路径先orElseThrow导致1 error，保留失败，改显式isPresent后red-02为纯断言RED；不是数据库约束失败。\n\nRuling: Task1审查Minor独立别名正向单测延后，当前实现正确且后续DB正常流程已验证不同current别名 — 不为非阻塞项重写本批测试 — 成本是独立组件单测仍有回归覆盖缺口。\n\nFinal: Ruling: 审查未判定的语义完整性/个人适用/可信server构造/账号授权/PDF与资料资格/完整时间/读后HTTP撤回/真实模型用户效果均保持OPEN — 当前只证明片段一致与现有字段真库复核 — 成本是不能接学生入口或关闭RC-04语义。\n\nRuling: 独立准备完成后不伪造审核模板或用户样本；剩余Task2–4所需外部资料/人工参考/参与者在blockers明列 — 所有不依赖这些条件的本阶段必要工程已准备 — 成本是本次不能宣布校园查证闭环完成。\n\n不删除ledger或历史证据，无commit/push/deploy；后续同一授权沿本计划接续。')
append(ROOT/'.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md','''## Final review rulings（逐项范围裁定）

Task2A审查Critical0/Important0/Minor1，报告已读；没有待修的阻塞发现。

Final: Ruling: M1跨profile专门真库反例延后，当前SQL正确要求run/profile一致 — 属于测试覆盖缺口而非已发现漏放，按审查Minor留账 — 成本是未来误删谓词可能没有本reader专项回归报警。

Final: Ruling: 完整时间/取代/清单/真实审核资格仍OPEN — 存储字段通过不能替资料审核 — 成本是G0及完整时效不能关闭。

Final: Ruling: 读后到HTTP/流式下发撤回仍OPEN — 仅顺序数据库变更合同、无学生入口 — 成本是集成阶段须确定下发与撤回策略。

Final: Ruling: 摘录相关/条件例外/自由事实与旧4→40仍OPEN — 两个组件只控制来源一致 — 成本是不能用新组件测试宣布旧RAG修复或产品正确。

Final: Ruling: server record与reader可信创建、绕过公开构造的应用误用未验收 — 当前无学生输入装配，类型名称不作为授权 — 成本是Task2必须拒绝客户端自证快照。

Final: Ruling: 真账号/浏览器/模型费用/用户收益未执行 — 不存在相应闭环或真实样本 — 成本是后续人工资料与参与者仍需到位。

Final: Ruling: PDF原件审核与传播资格未通过 — 文本hash只证明片段字节一致 — 成本是不可替原件和适用审核。

Final: Ruling: 历史dirty/P0/其他旧DB类不外推 — 本次审查限定增量，P0历史证据独立保留 — 成本是RC-10全仓旧默认风险仍OPEN。

Final: Ruling: 生产连接池/连接超时预算待应用装配 — DataSource异常返回已受控，阻塞时间未测 — 成本是Task2接入时必须落实调用预算，不承诺当前耗时上界。

Final: Ruling: 约束导致分享/撤回合并内部化，正文漂移首先由text比较阻断；重复行与异常关闭只做静态判断 — 接受真实覆盖边界，不冒充隔离谓词/并发/故障注入证据 — 成本是未来增加专项工程证据。
''')
for folder in [component,HERE]:
 after=[];diff=[]
 for row in read(folder/'source-before.json')['files']:
  p=ROOT/row['path'];after.append({'path':row['path'],'exists':p.exists(),'sha256':sha(p) if p.exists() else None})
  backup=folder/'before'/row['path'];old=backup.read_text(encoding='utf-8-sig') if backup.exists() else ''
  new=p.read_text(encoding='utf-8-sig') if p.exists() else ''
  diff+=difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='before/'+row['path'],tofile='after/'+row['path'])
 write(folder/'source-after.json',after);(folder/'changes-final.diff').write_text(''.join(diff),encoding='utf-8')
 write(folder/'integrity-check.json',{'verifiedAt':now,'passed':True,'files':[{'path':p.relative_to(ROOT).as_posix(),'sha256':sha(p)} for p in folder.iterdir() if p.is_file() and p.name!='integrity-check.json']})
print(f'PASS R5 independent preparation: {len(result["suites"])} classes/{count} tests; G0 pending, not integrated')
