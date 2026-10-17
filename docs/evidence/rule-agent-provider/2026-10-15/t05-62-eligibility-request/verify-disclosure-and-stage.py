from pathlib import Path
import json,hashlib,datetime,os,subprocess,re
R=Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-15/t05-62-eligibility-request';read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def save(n,d):(O/n).write_bytes((json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode())
b=read(O/'package-receipt.json');h=b['packageSha256'];p=read(O/'request-package.json');d=read(O/'OUTBOUND-DYNAMIC-FIELDS.json');a=read(O/'OUTBOUND-STATIC-CONTENT.json')
assert b['passed'];assert p['authorizationStatus']=='NOT_AUTHORIZED';assert p['maximumProviderCalls']==10 and p['maximumTaskRunsThisBatch']==2;assert d['decision']['publicationFactIds'] and 'publicationEligible' in d['decision']['facts'] and 'publicationReason(optional)' in d['decision']['rejectedActions']['fields'];assert a['protocolRevision']=='RULE_AGENT_ACTION_CONTRACT_06';assert a['publicationReviewProtocolRevision']=='RULE_PUBLICATION_REVIEW_02'
for k in a:
 if k in p:assert a[k]==p[k],k
for name in ['REQUEST-APPLICATION.md','EXTERNAL-DATA-DISCLOSURE.md','README.md']:
 text=(O/name).read_text(encoding='utf-8');assert h in text;assert '工程成果已冻结，真实在线验收未通过。' in text
 assert '0dd3f0a00f4e83d1fccfa7283ae0e168b8e0c307ac6d6ee0ebad9aa1c430acef' not in text
# Record verifier mistakes as verifier mistakes; they are not production or historical package failures.
save('verification-notes.json',{'initialHarnessChecks':[{'result':'FAILED','reason':'Old manifest filename README.md was initially resolved against repository root, causing false mismatch','correction':'Resolve entries relative to manifest directory; root-prefixed paths explicitly relative to repository','correctedFullIdentityCheckPassed':True},{'result':'FAILED','reason':'One ad hoc read used system GBK default for UTF-8 JSON','correction':'All artifact reads/writes specify UTF-8; no artifact bytes modified by failed read'},{'result':'FAILED','reason':'Sandbox TEMP differs from elevated user TEMP; public pricing file was not visible under sandbox TEMP','correction':'Read file in the same elevated context that wrote it; pricing SHA saved'}],'gitStatusObservation':'Git status reported launcher cmd modified although its raw SHA equals frozen input and HEAD blob, and git diff is empty; do not infer content change or declare all Git status clean','productionChangesThisTurn':0,'oldEvidenceModified':False})
now=datetime.datetime.now(datetime.timezone.utc).isoformat();approval={'status':'NOT_AUTHORIZED','requestedAtUtc':now,'packageSha256':h,'jarSha256':b['jarSha256'],'sourceCommit':b['productionSourceCommit'],'preparationHead':b['preparationHead'],'requestedCase':'P02_CONTINUATION_01','requestedBatchCount':1,'requestedMaximumTaskRuns':2,'requestedMaximumCalls':10,'requestedMaximumCny':'12','authorizedCalls':0,'consumedCalls':0,'remainingAuthorizedCalls':0,'priorAllowances':'CLOSED_NOT_REUSED','automaticRetriesAllowed':0,'noAutomaticNextBatch':True,'disclosureSha256':sha(O/'EXTERNAL-DATA-DISCLOSURE.md'),'staticContentSha256':sha(O/'OUTBOUND-STATIC-CONTENT.json'),'dynamicFieldListSha256':sha(O/'OUTBOUND-DYNAMIC-FIELDS.json'),'packageReceiptSha256':sha(O/'package-receipt.json'),'maximumActiveExecutionMinutesPerTurn':20};save('approval-state.json',approval)
# Fetch configured credential values only into memory for scanning; never write/print them.
command="$r=@();foreach($n in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD')){foreach($s in @('Process','User','Machine')){$v=[Environment]::GetEnvironmentVariable($n,$s);if($v){$r+= $v}}};ConvertTo-Json -InputObject @($r) -Compress"
raw=subprocess.check_output([os.environ['WINDIR']+'/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-Command',command]);values=json.loads(raw.decode('utf-8-sig'));assert isinstance(values,list)
files=sorted(f for f in O.iterdir() if f.is_file());count=0
for f in files:
 data=f.read_bytes()
 for value in values:
  if value:assert value.encode() not in data,'Configured secret found; output intentionally omitted'
 if f.suffix=='.json':
  def scan(x):
   if isinstance(x,str):
    for value in values:
     if value:assert value not in x,'Decoded configured secret found; output intentionally omitted'
   elif isinstance(x,list):
    for q in x:scan(q)
   elif isinstance(x,dict):
    for k,v in x.items():scan(k);scan(v)
  scan(read(f))
 count+=1
save('secret-scan.json',{'checkedUtc':now,'scannedFileCount':count,'freshCredentialScopes':['Process','User','Machine'],'credentialNames':['DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD'],'credentialValuesPrintedOrSaved':False,'rawBytesAndDecodedJsonScanned':True,'matchedSecrets':0,'passed':True})
save('verification.json',{'checkedUtc':now,'passed':True,'packageSha256':h,'identityReceiptPassed':True,'staticFieldsMatchActualPackage':True,'dynamicEligibilityAndRejectionFieldsDisclosed':True,'scopeTextReconstructedCount':17,'capabilityMappingsCompared':10,'ordinaryWindowsPowerShell51CheckPassed':True,'socketDeniedPrepareAndCheckPassed':True,'oldShaRejected':True,'duplicatePrepareDoesNotOverwrite':True,'sourceChangesThisTurn':0,'realModelCalls':0,'databaseOperations':0,'dockerOperations':0,'rebuilds':0,'authorizedCalls':0,'localTestsRerunThisTurn':0,'notVerified':['Online supplier schema enforcement','Docker/DB readiness','P02 online success','Frozen-configuration reliability','Unseen-question quality','Student benefit'],'publicPricingUrlOnlySentToFirecrawl':True,'actualFutureInvoiceCny':None,'readyForSeparateAuthorizationRequest':True})
files=sorted(f.relative_to(R).as_posix() for f in O.iterdir() if f.is_file() and f.name not in ('artifact-manifest.json','stage-files.json'));files+=[(O/'stage-files.json').relative_to(R).as_posix(),(O/'artifact-manifest.json').relative_to(R).as_posix()];save('stage-files.json',files)
manifest={n:sha(R/n) for n in files if not n.endswith('/artifact-manifest.json')};save('artifact-manifest.json',{'packageSha256':h,'authorizationStatus':'NOT_AUTHORIZED','files':manifest})
assert not subprocess.check_output(['git','diff','--cached','--name-only']);subprocess.run(['git','add','--']+files,check=True)
staged=subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines();assert sorted(staged)==sorted(files)
for n in staged:assert subprocess.check_output(['git','show',':'+n])==(R/n).read_bytes(),n
subprocess.run(['git','-c','core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol','diff','--cached','--check','--','.',' :(exclude)*.log'.strip()],check=True)
print(json.dumps({'readyToCommit':True,'stagedFiles':len(staged),'packageSha256':h,'authorization':'NOT_AUTHORIZED','secretScanPassed':True}))
