from pathlib import Path
import json,hashlib,subprocess,datetime,os
R=Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-67-review-condition-prompt-local';load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def save(n,d):(O/n).write_bytes((json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode())
b=load(O/'identity-before.json');v=load(O/'verification.json');assert v['localAcceptancePassed'];production=v['productionFilesChanged'];test='knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleReviewConditionPromptTest.java'
# Record pre-existing batch-path assertion mismatch from the unchanged baseline, not from a failed new implementation.
paths=['knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentRequestPathTest.java','knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineFactoryTest.java'];host='knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineProbeHost.java';oldHost=subprocess.check_output(['git','show',b['head']+':'+host]).decode();assert 't05-65-p02-eligibility-request/request-package.json' in oldHost
for name in paths:assert 't05-62-eligibility-request/request-package.json' in subprocess.check_output(['git','show',b['head']+':'+name]).decode()
save('preexisting-path-test-scope.json',{'identifiedBy':'Read-only source comparison at baseline commit','baseline':b['head'],'productionHostPackagePath':'docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request/request-package.json','excludedTestClasses':paths,'assertedTestPath':'docs/evidence/rule-agent-provider/2026-10-15/t05-62-eligibility-request/request-package.json','testExecutionsThisTurn':0,'notIntroducedByCurrentDiff':True,'fixedThisTurn':False,'impact':'Do not claim all ruleagent tests pass; future new-path/entry work must address these fixtures separately'})
files=[f for f in O.iterdir() if f.is_file() and f.name not in ('artifact-manifest.json','stage-files.json')]+list((O/'reports').glob('*.xml'))+[O/'test-evidence/t05-65-review-prompt-request.json',O/'test-evidence/t05-65-review-prompt-budget.json']+[R/n for n in production]+[R/test]
cmd="$r=@();foreach($n in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD')){foreach($s in @('Process','User','Machine')){$v=[Environment]::GetEnvironmentVariable($n,$s);if($v){$r+=$v}}};ConvertTo-Json -InputObject @($r) -Compress";values=json.loads(subprocess.check_output([os.environ['WINDIR']+'/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-Command',cmd]).decode('utf-8-sig'));assert isinstance(values,list)
def scan(x,depth=0):
 if isinstance(x,str):
  for s in values:
   if s:assert s not in x,'Secret in artifact; details omitted'
  if depth<5 and x.lstrip().startswith(('{','[')):
   try:q=json.loads(x)
   except json.JSONDecodeError:return
   scan(q,depth+1)
 elif isinstance(x,list):
  for q in x:scan(q,depth+1)
 elif isinstance(x,dict):
  for k,q in x.items():scan(k,depth+1);scan(q,depth+1)
for f in files:
 for s in values:
  if s:assert s.encode() not in f.read_bytes(),'Secret in raw artifact; details omitted'
 if f.suffix=='.json':scan(load(f))
save('secret-scan.json',{'checkedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'freshCredentialScopes':['Process','User','Machine'],'filesScanned':len(files),'rawAndNestedDecodedJsonScanned':True,'matchedSecrets':0,'valuesPrintedOrSaved':False,'passed':True});files.append(O/'secret-scan.json')
names=sorted(set(f.relative_to(R).as_posix() for f in files));names += [(O/'stage-files.json').relative_to(R).as_posix(),(O/'artifact-manifest.json').relative_to(R).as_posix()];save('stage-files.json',names);save('artifact-manifest.json',{'baseHead':b['head'],'scope':'LOCAL_REVIEW_CONDITION_PROMPT_ONLY; NO_MODEL_EFFECTIVENESS_CLAIM','files':{n:sha(R/n) for n in names if not n.endswith('/artifact-manifest.json')}})
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines()).issubset(set(names));subprocess.run(['git','add','--']+names,check=True);staged=subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines();assert sorted(staged)==sorted(names)
for n in staged:assert subprocess.check_output(['git','show',':'+n])==(R/n).read_bytes(),n
subprocess.run(['git','-c','core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol','diff','--cached','--check','--','.',' :(exclude)*.log'.strip(),':(exclude)docs/evidence/rule-agent-provider/2026-10-16/t05-67-review-condition-prompt-local/red-report.xml'],check=True)
print(json.dumps({'readyToCommit':True,'stagedFiles':len(staged),'productionFiles':2,'newTestClasses':1,'localTests':163,'postCaptureTests':5,'realModelCalls':0}))
