from pathlib import Path
import json,hashlib,datetime,subprocess,os,re
R=Path.cwd();B=R/'docs/evidence/rule-agent-provider/2026-10-15';P=B/'t05-62-eligibility-request';O=B/'t05-63-p02-eligibility-live';L=next(P.glob('live-*'));load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def save(n,d):(O/n).write_bytes((json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode())
assert subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()=='6002b5a289e0960e89b660d559d667e143693d7b';assert not subprocess.check_output(['git','diff','--name-only']);assert not subprocess.check_output(['git','diff','--cached','--name-only'])
m=load(P/'artifact-manifest.json')
for n,h in m['files'].items():assert sha(R/n)==h,n
pack=load(P/'request-package.json');receipt=load(P/'package-receipt.json')
for n,h in {**pack['buildIdentity']['sourceSha256'],**receipt['additionalBuildInputSha256']}.items():assert sha(R/n)==h,n
for item in pack['runtimeAssets']:assert sha((R/item['path']).resolve())==item['sha256']
assert sha(R/'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar')==receipt['jarSha256']
for n,h in receipt['frozen6JarSha256'].items():assert sha(R/n)==h,n
baseline=load(B/'t05-61-request-path-binding-local/identity-before.json');main=Path('D:/alldesk/Projects/ZhkuAgent');assert subprocess.check_output(['git','-C',str(main),'rev-parse','HEAD'],text=True).strip()==baseline['mainHead']
for n,h in baseline['mainDirtyFilesSha256'].items():assert sha(main/n)==h,n
assert sha(B/'t05-55-clarification-request/request-package.json')==baseline['old55PackageSha256']
auth=load(O/'authorization.json');summary=load(O/'result-summary.json');ledger=load(O/'attempt-ledger.json');assert auth['status']=='CLOSED_FAILED' and auth['remainingAuthorizedCalls']==0 and auth['consumedCalls']==4 and auth['unusedCallsClosed']==6;assert len(ledger['attempts'])==4 and not summary['passed']
now=datetime.datetime.now(datetime.timezone.utc).isoformat();save('identity-postflight.json',{'checkedUtc':now,'sourceInputsMatched':335,'additionalInputsMatched':14,'runtimeAssetsMatched':15,'defaultJarAndFrozen6JarsUnchanged':True,'originalRequestArchiveManifestUnchanged':True,'old55PackageUnchanged':True,'mainHeadAndFourDirtyDocsUnchanged':True,'sourceChangesThisTurn':0,'rebuildsThisTurn':0,'trackedContentDiffEmpty':True,'passed':True})
f=O/'README.md';text=f.read_text(encoding='utf-8');text=text.replace('本批到此结束。','本批到此结束。62/approval-state.json保留准备阶段未授权快照；本轮生效授权及已关闭状态以本目录authorization.json为准。');f.write_bytes(text.encode())
# Never print credential values; collect only in process memory and scan raw plus bounded nested JSON.
command="$r=@();foreach($n in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD')){foreach($s in @('Process','User','Machine')){$v=[Environment]::GetEnvironmentVariable($n,$s);if($v){$r+=$v}}};ConvertTo-Json -InputObject @($r) -Compress"
raw=subprocess.check_output([os.environ['WINDIR']+'/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-Command',command]);secrets=json.loads(raw.decode('utf-8-sig'));assert isinstance(secrets,list)
files=list(O.glob('*'));files=[f for f in files if f.is_file()];files+=list(L.glob('*'));files += list(P.glob('launcher-*.log'));assert all(f.is_file() for f in files)
def scan(x,depth=0):
 if isinstance(x,str):
  for s in secrets:
   if s:assert s not in x,'Secret in decoded content; detail omitted'
  if depth<6 and x.lstrip().startswith(('{','[')):
   try:y=json.loads(x)
   except json.JSONDecodeError:return
   scan(y,depth+1)
 elif isinstance(x,list):
  for v in x:scan(v,depth+1)
 elif isinstance(x,dict):
  for k,v in x.items():scan(k,depth+1);scan(v,depth+1)
for f in files:
 data=f.read_bytes()
 for s in secrets:
  if s:assert s.encode() not in data,'Secret in artifact bytes; detail omitted'
 if f.suffix=='.json':scan(load(f))
 elif f.suffix=='.jsonl':
  for line in data.decode('utf-8-sig').splitlines():scan(json.loads(line))
save('secret-scan.json',{'checkedUtc':now,'freshScopes':['Process','User','Machine'],'credentialNames':['DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD'],'credentialValuesPrintedOrSaved':False,'filesScanned':len(files),'rawAndNestedDecodedJsonScanned':True,'matchedSecrets':0,'passed':True})
save('verification.json',{'checkedUtc':now,'archiveIntegrityPassed':True,'onlineAcceptancePassed':False,'exactlyOneAuthorizedBatchLaunched':True,'actualCalls':4,'unusedCallsClosed':6,'remainingAuthorizedCalls':0,'all4RequestsResponsesAndFinalTracesLinked':True,'nonRedactedBodyHashesVerified':True,'singleTaskSingleRun':True,'noPublicReplyOrSecondRun':True,'firstFailureStage':'REVIEW_BINDING','firstFailureCode':'REVIEW_CONDITION_UNKNOWN','noTimeout':True,'resourceChecksPassed':True,'identityPreserved':True,'secretScanPassed':True,'actualInvoiceCny':None,'repairs':0,'replays':0,'automaticRetries':0,'nextBatchStarted':False,'unknowns':['Actual supplier invoice','Temporary DB rows immediately before deletion','Exact task-store failed snapshot not independently archived','Why reviewer chose prefixed key','Whether corrected response would pass remaining gate checks']})
# Include only this batch's new files and launcher output; original preparation archive is immutable.
files=sorted([f for f in O.iterdir() if f.is_file() and f.name not in ('archive-manifest.json','stage-files.json')]+list(L.iterdir())+list(P.glob('launcher-*.log')))
names=[f.relative_to(R).as_posix() for f in files];names += [(O/'stage-files.json').relative_to(R).as_posix(),(O/'archive-manifest.json').relative_to(R).as_posix()];save('stage-files.json',names)
save('archive-manifest.json',{'packageSha256':auth['packageSha256'],'batchStatus':'CLOSED_FAILED','files':{n:sha(R/n) for n in names if not n.endswith('/archive-manifest.json')}})
subprocess.run(['git','add','--']+names,check=True);staged=subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines();assert sorted(staged)==sorted(names)
for n in staged:assert subprocess.check_output(['git','show',':'+n])==(R/n).read_bytes(),n
subprocess.run(['git','-c','core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol','diff','--cached','--check','--','.',' :(exclude)*.log'.strip()],check=True)
print(json.dumps({'archiveReady':True,'stagedFiles':len(staged),'batchPassed':False,'calls':4,'remainingAuthorizedCalls':0,'secretScanPassed':True}))
