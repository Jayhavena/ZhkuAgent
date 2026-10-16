from pathlib import Path
import json,hashlib,datetime,re,subprocess,urllib.request,tempfile
from decimal import Decimal
R=Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-69-review-condition-request';OLD=R/'docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request';B=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-68-path-build-identity-local';load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def save(n,v):(O/n).write_bytes((json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode())
p=load(O/'request-package.json');op=load(OLD/'request-package.json');identity=load(B/'build-identity.json');h=sha(O/'request-package.json');now=datetime.datetime.now(datetime.timezone.utc).isoformat()
delta=[k for k in set(p)|set(op) if p.get(k)!=op.get(k)];assert set(delta)=={'reviewSystemPrompt','publicationReviewContract','buildIdentity'},delta
assert p['runtimeAssets']==op['runtimeAssets'];assert p['factCapabilityAuthoritySha256']==op['factCapabilityAuthoritySha256'];assert p['authorizationStatus']=='NOT_AUTHORIZED';assert p['maximumProviderCalls']==10
static=load(OLD/'OUTBOUND-STATIC-CONTENT.json');matching=[k for k in static if k in p]
for k in matching:static[k]=p[k]
assert len(matching)==15
sem=load(R/'docs/product/rule-check-2025-b02-semantic-review.json');draft=load(R/'docs/product/rule-check-c05-topic-catalogue.json');rules={q['reviewItemId']:q for q in sem['rules']};scopes=[]
for item in draft['entries']:
 limits=rules[item['ruleId'].split('/')[0]]['exceptionsAndUnknowns'];scope=item['questionScope']+' 引用仅限2025.9.2文本，现行适用性未核实。'+(' 已审核资料边界：'+'；'.join(limits) if limits else '');scopes.append({'ruleId':item['ruleId'],'questionScope':scope})
overlay=(R/'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleArticle4Overlay.java').read_text(encoding='utf8');match=re.search(r'CatalogEntry\(rule,"([^"]+)"',overlay);assert match;scopes.append({'ruleId':'T02/ARTICLE4','questionScope':match[1]});assert scopes==static['possibleDecisionRuleContextScopes'];assert len(scopes)==17
cap='knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleFactCapabilityCatalogue.java';assert sha(R/cap)==op['buildIdentity']['sourceSha256'][cap];assert len(static['registeredCapabilityProjection'])==10
contract=(R/'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationContract.java').read_text(encoding='utf8');codes=re.search(r'public enum Code\s*\{([^}]+)\}',contract).group(1).replace('\n','').replace(' ','').split(',');assert codes==static['possiblePublicationRejectionCodes'];save('OUTBOUND-STATIC-CONTENT.json',static)
dynamic=load(OLD/'OUTBOUND-DYNAMIC-FIELDS.json');dynamic['review']['reviewContract']='Exact publicationReviewContract from this package, including conditionIdForm: copy original conditionId verbatim; same-name keys stay bare; prefixed keys rejected.';dynamic['review']['responseConditionIdBoundary']=p['publicationReviewContract']['conditionIdForm'];assert dynamic['limits']==p['budget'];assert dynamic['batchLimits']=={'maxRuns':2,'maxCalls':10,'maxCnyRequested':'12','ownedProcessMillis':235000,'authorizedCallsNow':0};save('OUTBOUND-DYNAMIC-FIELDS.json',dynamic)
url='https://help.aliyun.com/zh/model-studio/qwen3-8-flash';raw=urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=20).read();html=raw.decode('utf8');assert all(s in html for s in ['991808','131072','0.8','2.7']);snap=Path(tempfile.gettempdir())/'zhku-t05-69-public-pricing.html';snap.write_bytes(raw)
reserve=Decimal(10)*(Decimal(991808)*Decimal('0.8')+Decimal(131072)*Decimal('2.7'))/Decimal(1000000);assert reserve==Decimal('11.473408') and reserve<12
pricing=dict(checkedUtc=now,source=url,sourceTitle='阿里云qwen3.8-flash官方模型文档',retrieval='Public official page only, web reader verified Beijing price table/context limits; direct public HTML retrieval kept in temporary directory. No project payload or credentials transmitted.',publicPageSha256=hashlib.sha256(raw).hexdigest(),publicPageTemporaryPath=str(snap),region='华北2（北京）',currency='CNY',inputCnyPerMillion='0.8',outputCnyPerMillion='2.7',publicNonThinkingMaximumInputTokens=991808,publicMaximumOutputTokens=131072,requestOutputTokenLimit=1024,maximumCallsRequested=10,maximumCnyRequested='12',maximumPublicTokenReservationCny=str(reserve),assumeDiscountCacheOrFreeCredit=False,recheckBeforeExecution=True,actualInvoiceCny=None,status='PREPARATION_ONLY_NOT_AUTHORIZED',hardWalletCapEnforced=False);save('pricing-and-budget.json',pricing)
save('disclosure-generation.json',dict(generatedUtc=now,packageSha256=h,changedPackageTopLevelFields=sorted(delta),changedOutboundStaticFields=['reviewSystemPrompt','publicationReviewContract'],newOutboundInputFields=[],staticPackageFieldsMatched=len(matching),scopeStringsReconstructedAndMatched=len(scopes),capabilityAuthorityRawShaUnchanged=True,registeredCapabilityMappings=10,runtimeAssetsEqualOldPackage=True,modelBudgetAndGateUnchanged=True,maximumPublicTokenReservationCny=str(reserve),realProviderCalls=0))
save('approval-state.json',dict(packageSha256=h,status='NOT_AUTHORIZED',authorizedProviderCalls=0,authorizedCny='0',requestedBatch='ONE_P02_SAME_TASK_TWO_RUNS',requestedMaximumProviderCalls=10,requestedMaximumCny='12',oldAuthorizationsClosed=True,instructionEnteringNextStepAuthorizesPreparationOnly=True))
save('attempt-ledger.json',dict(packageSha256=h,realAttempts=[],actualProviderCalls=0,remainingAuthorizedCalls=0,proposedMaximumCalls=10,actualInvoiceCny=None,actualInvoiceStatus='NO_REAL_CALLS_THIS_BATCH',futureRecording='Deduplicate dispatch by task/run/attempt; record dispatched,response headers/requestId,status,duration,request/response SHA,usage,proposed action,provider rejection and gate branch. Missing usage or timeout after dispatch consumes allowance; invoice UNKNOWN not zero; no refunds,retries or replacement calls.'))
readme=f'''T05-69 新请求包已准备，状态 NOT_AUTHORIZED，当前授权真实调用为0。

包SHA256：`{h}`。冻结jar：`{identity['jarSha256']}`。生产源码检查点：`{identity['sourceCommit']}`；准备前HEAD：cb88e7da6d1856f63b7ce40858d06fd15f89254a。实际工作树D:/alldesk/Projects/ZhkuAgent-t05；主仓库HEAD58e1ef7未合入。

[授权申请](REQUEST-APPLICATION.md)、[完整外发披露](EXTERNAL-DATA-DISCLOSURE.md)、[全部静态内容](OUTBOUND-STATIC-CONTENT.json)、[动态字段及上限](OUTBOUND-DYNAMIC-FIELDS.json)。本次申请仅一个P02同任务两run，最多10次真实调用，人民币12元上限。历史5次/6元或其他包授权均不复用。

冻结jar直接Prepare及CheckPackage均在禁止网络的helper中执行，无构建；旧SHA、重复Prepare拒绝且包不变。普通用户持久PATH的Windows PowerShell5.1→CMD→PowerShell7入口CheckPackage、旧SHA及重复Prepare保护均通过。正式包83065字节是本机申请上限内容集合，不是单次HTTP载荷；实际每次请求仍受65536字节守卫。上一轮同输入样本7706字节、本地213项是已有证据，本轮未重新跑测试，也不保证未来动态请求都不超限。

相对65包只有reviewSystemPrompt、publicationReviewContract和buildIdentity三个顶级键不同；条件目录/能力映射、Gate及业务成功门槛均不变。17种范围文字重新从当前资产构建比对，10种能力映射源码及15运行资产不变。完整内容与变化见附件，不仅靠版本号推断。

本轮模型调用、数据库、Docker操作均0；仅核对公开价格网页，未向该网页发送项目内容。未核Docker/镜像/数据库就绪，不代表在线环境已就绪。后续必须重新核身份/价格/环境并获得本包明确授权后执行；失败归档关闭余量，不自动修复、补调用或开下一批。

恢复：保持本包未授权，不覆盖申请。若源码、jar、资产、目的地、模型、预算或提示变化，停止并重新准备独立申请；不恢复旧额度。Git不能恢复原PDF和本机依赖，应按清单核对。

工程成果已冻结，真实在线验收未通过。
''';(O/'README.md').write_bytes(readme.encode())
application=f'''T05-69 在线授权申请——尚未授权

本申请唯一包SHA256：`{h}`。
冻结jarSHA256：`{identity['jarSha256']}`。
源码检查点：`{identity['sourceCommit']}`。

申请范围：阿里云百炼/DashScope北京端点 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`，模型 `qwen3.8-flash`；仅本包一次P02，同一任务最多2 run，最多10次真实调用，人民币12元上限。申请传输的数据以[完整外发披露](EXTERNAL-DATA-DISCLOSURE.md)、[全部静态内容](OUTBOUND-STATIC-CONTENT.json)、[动态字段](OUTBOUND-DYNAMIC-FIELDS.json)为准，包含本次新增的条件键提示澄清、原文、审核事实/条件、派生scope文字及事实能力摘要。当前授权0；旧授权与剩余额度全部关闭。

固定首问“我这门选修课没过，能不能重修？”。首run必须真实检索、读取、必要追问及独立核验并发布合格WAITING_USER后，才提交预登记合成补充PUBLIC。续答同task新run，重新检索、读取、核验并发布。PUBLIC是测试输入；不向模型预置成功答案或验收factId，不默认c1，不截断完整候选，不合并条件，不放宽拒绝。

每run最多4决策+1独立核验、单次20秒、run110秒；两run最多220秒，Java外层235秒。最满run的80秒决策+20秒核验+5秒发布预留后仅剩5秒用于工具和本地I/O；并不保证4次完整20秒决策都可执行。Runner按剩余时间缩短或拒绝新调用。整个执行轮主动操作不超过20分钟。

官方公开最大token原价保守预留11.473408元，小于12元；不依赖请求输出1024一定被供应商执行，也不计优惠。12元不是账户钱包自动扣款硬限额，执行前价格/目的地/非思考文本配置或上限无法确认则不启动。usage估算与实际账单分开，派发后超时或无usage仍记尝试且费用UNKNOWN，不记零、不补额度。

首run不合格即停止，任一身份/条件/时间/容量/独立核验/发布/归档或退出失败均停止，归档全部尝试、失败分支及已知usage/未知费用，关闭余量；无P01、第三run、自动重试、修复或新批次。批准后才检查凭证、Docker引擎/镜像，必要时启动Docker；数据库仅随机自有容器、回环空闲端口专用临时库，拒绝原演示库54329。重复续答应幂等，不增run或调用。

用户需单独明确批准“仅本包SHA256 {h}，一次P02、最多10次真实调用、人民币12元上限，以及上述完整外发范围”。本轮“进入下一步”仅授权准备，不算外发批准。

工程成果已冻结，真实在线验收未通过。
''';(O/'REQUEST-APPLICATION.md').write_bytes(application.encode())
print(json.dumps(dict(packageSha256=h,changedTopLevel=delta,staticFieldsMatched=len(matching),scopes=17,reserve=str(reserve),status='NOT_AUTHORIZED'),ensure_ascii=False))
