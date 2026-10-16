from pathlib import Path
import json,hashlib,datetime,subprocess,os
R=Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-66-condition-key-local-control';P=R/'docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request';load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def save(n,d):(O/n).write_bytes((json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode())
b=load(O/'identity-before.json');v=load(O/'replay-result.json');proc=load(O/'process.json');assert v['checksPassed'] and proc['passed'] and proc['temporaryDirectoryRemoved'];assert v['original']['taskStatus']=='FAILED' and v['keyOnly']['taskStatus']=='WAITING_USER';assert v['directGateVerdict']=='UNRESOLVED';assert v['keyOnly']['providerResponseReceived'] is False and v['keyOnly']['answerSource']=='VERIFIED_RULE_ONLY'
for n,h in b['sourceSha256'].items():assert sha(R/n)==h,n
for n,h in b['rawT05_65FilesSha256'].items():assert sha(R/n)==h,n
assert sha(P/'request-package.json')==b['requestPackageSha256'];assert sha(R/'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar')==b['jarSha256'];assert subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()==b['head'];assert not subprocess.check_output(['git','diff','--name-only']);assert not subprocess.check_output(['git','diff','--cached','--name-only'])
main=Path('D:/alldesk/Projects/ZhkuAgent');assert subprocess.check_output(['git','-C',str(main),'rev-parse','HEAD'],text=True).strip()==b['mainHead']
for n,h in b['mainDirtyFileSha256'].items():assert sha(main/n)==h,n
auth=load(P/'authorization.json');assert auth['status']=='CLOSED_FAILED'
readme='''# T05-66：t05-65原响应的条件键单变量对照\n\n本轮仅本地验证，零真实模型调用、零数据库、零Docker、零应用重建。提示澄清保留为候选，未修改提示、条件记录、生产守卫或预算。\n\n使用t05-65实际jar（SHA256：'''+b['jarSha256']+'''），准备前HEAD为'''+b['head']+'''。助手仅在独立临时目录编译，运行时加载实际jar中的解码器、能力目录、Gate、Runner和发布器；关键类来源见replay-result.json。Java禁止所有SocketPermission，子进程移除凭证；临时目录已删除。\n\n对照结果：\n\n- 原始核验响应未经改写，实际解码稳定拒绝：REVIEW_BINDING / REVIEW_CONDITION_UNKNOWN。本机Runner也落FAILED。\n- 仅另建副本，将requirements[0/1].necessaryConditionIds[0]由S10/RETAKE_ONLY:courseType改为courseType。JSON差异严格限于这两个位置；需求ID、跨度、subject、CORE绑定、unresolvedDemands和inputFingerprint全部保持。原始轨迹和响应文件不修改。\n- 副本通过关联校验和独立Gate，Gate返回UNRESOLVED（条件未知时的澄清判定，不能写成完整答案通过）。\n- 本机受控Runner经过SEARCH→READ→ASK→PUBLICATION_REVIEW并发布WAITING_USER/INCOMPLETE；无答案主张，澄清键courseType、允许值PUBLIC/PROFESSIONAL，有1份原文证据及关联。尚未注入PUBLIC，未运行第二run。\n\n完整事实定义来自t05-65 REVIEW_INPUT_SNAPSHOT，核验输入同时与真实REQUEST逐项比对；重新构造的SemanticInput与原输入完全相等，包括完整事实指纹产生的inputFingerprint。ASK使用该批实际提议，不替换支持事实。\n\n替身边界：检索与原文读取是内存端口，只提供该批已读原文；模板条件/事实/范围来自历史快照，测试审核者、主体、运行ID、检索配置和覆盖夹具是本机构造。真实数据库权限、索引、fresh回读、任务仓发布一致性和网络时延未在本轮重测。原始审计文件完整保留。\n\nRunner的execution.mode字段在源码中固定为ONLINE，不能据此声称本轮外发。决定/核验端口只是读取历史资料，providerResponseReceived=false、answerSource=VERIFIED_RULE_ONLY；两组各3次决定端口调用加1次核验端口调用，均不是真实模型调用。\n\n本结果证明：对这份具体输入和响应，改正两个引用键足以在现有Gate及本机发布路径得到必要澄清。它不证明重复条件是模型加前缀的原因，不证明提示增强有效，不代表真实P02两run整链、可靠性或产品质量通过。未重新运行历史204/205项测试，也未将局部对照冒充完整回归。\n\n335项生产/启动输入、实际jar、t05-65原始文件和主仓库既有4份改动的字节均保持，旧授权仍CLOSED_FAILED，不恢复或补充额度。撤回本轮仅需另建逆向文档提交，不改历史证据、不操作生产历史。\n\n工程成果已冻结，真实在线验收未通过。\n''';(O/'README.md').write_bytes(readme.encode())
save('verification.json',{'checkedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'localControlPassed':True,'originalResponseRejected':'REVIEW_BINDING/REVIEW_CONDITION_UNKNOWN','keyOnlyDirectGateVerdict':'UNRESOLVED','keyOnlyControlledTaskStatus':'WAITING_USER','changedJsonPointers':load(O/'control-diff.json')['changedJsonPointers'],'allOtherResponseFieldsUnchanged':True,'originalSafeInputAndFingerprintReconstructedExactly':True,'sourceInputsUnchanged':335,'jarUnchanged':True,'allOriginalT05_65LiveFilesUnchanged':True,'mainHeadAndFourDirtyDocsPreserved':True,'actualModelCalls':0,'databaseOperations':0,'dockerOperations':0,'applicationRebuilds':0,'productionChanges':0,'historicalFullSuiteRerun':False,'oldAuthorizationStatus':auth['status'],'promptEffectivenessVerified':False,'secondRunExecuted':False,'onlineAcceptancePassed':False,'temporaryDirectoryRemoved':True,'runnerOnlineModeFieldIsNotExternalCallEvidence':True})
# Fresh credential lookup is used only for the local artifact scan; no credential values are printed or saved.
cmd="$r=@();foreach($n in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD')){foreach($s in @('Process','User','Machine')){$v=[Environment]::GetEnvironmentVariable($n,$s);if($v){$r+=$v}}};ConvertTo-Json -InputObject @($r) -Compress";values=json.loads(subprocess.check_output([os.environ['WINDIR']+'/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-Command',cmd]).decode('utf-8-sig'));assert isinstance(values,list)
files=[f for f in O.iterdir() if f.is_file()]
def scan(x):
 if isinstance(x,str):
  for s in values:
   if s:assert s not in x,'Secret detected; details omitted'
 elif isinstance(x,list):
  for i in x:scan(i)
 elif isinstance(x,dict):
  for k,v in x.items():scan(k);scan(v)
for f in files:
 for s in values:
  if s:assert s.encode() not in f.read_bytes(),'Secret detected; details omitted'
 if f.suffix=='.json':scan(load(f))
save('secret-scan.json',{'freshScopes':['Process','User','Machine'],'filesScanned':len(files),'rawAndDecodedJsonChecked':True,'matchedSecrets':0,'valuesPrintedOrSaved':False,'passed':True})
files=sorted(f for f in O.iterdir() if f.is_file() and f.name not in ('artifact-manifest.json','stage-files.json'));names=[f.relative_to(R).as_posix() for f in files]+[(O/'stage-files.json').relative_to(R).as_posix(),(O/'artifact-manifest.json').relative_to(R).as_posix()];save('stage-files.json',names);save('artifact-manifest.json',{'kind':'ZERO_EGRESS_T05_65_CONDITION_KEY_CONTROL','files':{n:sha(R/n) for n in names if not n.endswith('/artifact-manifest.json')}})
subprocess.run(['git','add','--']+names,check=True);staged=subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines();assert sorted(staged)==sorted(names)
for n in staged:assert subprocess.check_output(['git','show',':'+n])==(R/n).read_bytes(),n
subprocess.run(['git','-c','core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol','diff','--cached','--check','--','.',' :(exclude)*.log'.strip()],check=True)
print(json.dumps({'readyToCommit':True,'files':len(staged),'original':'REVIEW_CONDITION_UNKNOWN','keyOnly':'WAITING_USER','actualModelCalls':0,'productionChanges':0}))
