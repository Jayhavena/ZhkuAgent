from pathlib import Path
import json,hashlib,subprocess,zipfile,datetime
R=Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-69-review-condition-request';load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();b=load(O/'identity-before.json');p=load(O/'request-package.json');a=load(O/'OUTBOUND-STATIC-CONTENT.json');d=load(O/'OUTBOUND-DYNAMIC-FIELDS.json');old=load(R/'docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request/request-package.json');receipt=load(O/'package-receipt.json');source=b['sourceCommit'];now=datetime.datetime.now(datetime.timezone.utc).isoformat();checks={}
assert sha(O/'request-package.json')==receipt['packageSha256'];assert p['authorizationStatus']=='NOT_AUTHORIZED' and load(O/'approval-state.json')['authorizedProviderCalls']==0
for k in ['sourceSha256','additionalBuildInputSha256']:
 for n,h in b[k].items():assert sha(R/n)==h,n
checks['freshProductionAndAdditionalInputHashes']=dict(production=len(b['sourceSha256']),additional=len(b['additionalBuildInputSha256']))
names=list(b['sourceSha256'])+list(b['additionalBuildInputSha256']);result=subprocess.run(['git','cat-file','--batch'],input=''.join(source+':'+n+'\n' for n in names).encode(),stdout=subprocess.PIPE,check=True);data=result.stdout;i=0
for n in names:
 e=data.index(b'\n',i);parts=data[i:e].split();size=int(parts[-1]);blob=data[e+1:e+1+size];i=e+size+2
 if n in b['sourceSha256']:assert hashlib.sha256(blob).hexdigest()==b['sourceSha256'][n],n
 else:assert subprocess.check_output(['git','hash-object','--path',n,n]).strip()==parts[0],n
checks['sourceBoundToGitCommit']=source;jar=R/'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar';assert sha(jar)==b['jarSha256']==p['buildIdentity']['jarSha256'];assert p['buildIdentity']['sourceSha256']==b['sourceSha256'];assetHashes={q['path']:q['sha256'] for q in p['runtimeAssets']}
for n,h in assetHashes.items():assert sha(R/n)==h,n
checks['freshRuntimeAssets']=len(assetHashes)
with zipfile.ZipFile(jar) as z:
 app={n[len('BOOT-INF/classes/'):]:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith('BOOT-INF/classes/') and n.endswith('.class')};local={f.relative_to(R/'knowledge/target/classes').as_posix():sha(f) for f in (R/'knowledge/target/classes').rglob('*.class')};assert app==local
 runtime=next(n for n in z.namelist() if n.startswith('BOOT-INF/lib/agent-runtime-') and n.endswith('.jar'));rjar=R/'agent-runtime/target'/Path(runtime).name;assert hashlib.sha256(z.read(runtime)).hexdigest()==sha(rjar)
with zipfile.ZipFile(rjar) as z:
 shared={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')};local={f.relative_to(R/'agent-runtime/target/classes').as_posix():sha(f) for f in (R/'agent-runtime/target/classes').rglob('*.class')};assert shared==local
checks['classByteIdentity']=dict(application=len(app),shared=len(shared))
for k in ['protectedFrozenJars','protectedOld65Files']:
 for n,h in b[k].items():assert sha(R/n)==h,n
 checks[k+'Unchanged']=len(b[k])
M=Path('D:/alldesk/Projects/ZhkuAgent');assert subprocess.check_output(['git','-C',str(M),'rev-parse','HEAD'],text=True).strip()==b['mainHead']
for n,h in b['mainDirtySha256'].items():assert sha(M/n)==h,n
assert not subprocess.check_output(['git','diff','--cached','--name-only']).strip();assert not subprocess.check_output(['git','diff','--','knowledge/src','knowledge/scripts','agent-runtime/src','pom.xml','knowledge/pom.xml','agent-runtime/pom.xml','mvnw.cmd']).strip();checks['mainAndPreexistingChangesPreserved']=True
for k in a.keys()&p.keys():assert a[k]==p[k],k
checks['overlappingStaticFields']=len(a.keys()&p.keys());assert checks['overlappingStaticFields']==15
assert {k for k in old.keys()|p.keys() if old.get(k)!=p.get(k)}=={'buildIdentity','reviewSystemPrompt','publicationReviewContract'}
changed=[n for n,h in p['buildIdentity']['sourceSha256'].items() if old['buildIdentity']['sourceSha256'].get(n)!=h];assert set(changed)=={'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineProbeHost.java','knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProvider.java','knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationProtocol.java','knowledge/scripts/start-rule-agent-probe.ps1'};checks['changedProductionInputsVsClosed65']=changed
assert d['review']['responseConditionIdBoundary']==p['publicationReviewContract']['conditionIdForm'];assert p['publicationReviewContract']['conditionIdForm'] in p['reviewSystemPrompt'];assert d['limits']==p['budget'];assert d['batchLimits']['maxRuns']==2 and d['batchLimits']['maxCalls']==10 and d['batchLimits']['maxCnyRequested']=='12';assert p['cases']==[dict(id='P02',question='我这门选修课没过，能不能重修？',reply='PUBLIC')]
sem=load(R/'docs/product/rule-check-2025-b02-semantic-review.json');assert all(x['exceptionsAndUnknowns']==r['exceptionsAndUnknowns'] for x,r in zip(a['conditionalGapNotice'],sem['rules']));checks['conditionalNoticesEqualCurrentReviewedSemantics']=True
assert '请求包与授权指纹不同' in (O/'old-sha-rejected.log').read_text(encoding='utf8');assert 'FileAlreadyExistsException' in (O/'duplicate-prepare-rejected.log').read_text(encoding='utf8');assert '待核对请求包指纹不同' in (O/'ordinary-terminal-old-sha-rejected.log').read_text(encoding='utf8');assert '请求包已存在，保留原包，不构建或覆盖' in (O/'ordinary-terminal-duplicate-prepare-rejected.log').read_text(encoding='utf8')
for n in ['prepare-process.json','ordinary-terminal-process.json']:
 proc=load(O/n);assert proc['providerCalls']==proc['databaseOperations']==proc['dockerOperations']==0;assert all(x['resourcesClosed'] and not x['timedOut'] for x in proc['processes'])
checks['zeroCallCheckAndExpectedRejectionBranches']=True
for f in O.iterdir():
 if f.is_file():assert chr(0xfffd) not in f.read_text(encoding='utf-8-sig'),f.name
checks['utf8ArtifactsWithoutReplacementCharacters']=True
(O/'verification.json').write_bytes(json.dumps(dict(verifiedUtc=now,passed=True,packageSha256=receipt['packageSha256'],jarSha256=b['jarSha256'],checks=checks,realProviderCalls=0,databaseOperations=0,dockerOperations=0,builds=0,onlineEnvironmentReadiness='NOT_CHECKED',pricing='PUBLIC_PAGE_CHECKED_RECHECK_REQUIRED_BEFORE_EXECUTION',authorizationStatus='NOT_AUTHORIZED',limitations=['Local package acceptance does not verify provider schema support,model behavior,online success,reliability or product quality','No explicit new online approval received; cannot execute','No automatic monetary wallet cap; public maximum-token reservation and dispatch count require fresh confirmation']),ensure_ascii=False,indent=2).encode())
(O/'execution.json').write_bytes(json.dumps(dict(startedUtc=b['startedUtc'],hardDeadlineUtc=b['hardDeadlineUtc'],preparedUtc=now,preparationHead=b['preparationHead'],sourceCommit=source,packageSha256=receipt['packageSha256'],jarSha256=b['jarSha256'],providerCalls=0,databaseOperations=0,dockerOperations=0,builds=0,scope='New immutable application package,full disclosure,zero-call checks only',pricingFetch='Public official page only; no project payload',authorization='NOT_AUTHORIZED',historicalAllowancesReused=False,localTestsRerun=False,previous213Tests='Historical t05-68 evidence only,not online acceptance',guardOperations=['PrepareProbe','CheckPackage','reject wrong SHA','reject duplicate Prepare'],ordinaryTerminal='Persistent Machine+User PATH WindowsPowerShell5.1/CMD/PowerShell7; CheckPackage and two rejection boundaries observed',recovery='Keep packet unapproved; changes to source/build/assets/outbound data require new application and new explicit authorization. Never overwrite69 or reuse65 quota.'),ensure_ascii=False,indent=2).encode())
print(json.dumps(dict(passed=True,packageSha256=receipt['packageSha256'],checks=checks),ensure_ascii=True))
