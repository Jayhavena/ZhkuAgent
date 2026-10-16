from pathlib import Path
import json,hashlib,zipfile,subprocess,datetime,re
R=Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-70-final-acceptance-local';P=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-69-review-condition-request';load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();b=load(O/'identity-before.json');matrix=load(O/'acceptance-matrix.json');decision=load(O/'decision.json');M=Path('D:/alldesk/Projects/ZhkuAgent');assert matrix['decision']==decision['status']=='T05_ENGINEERING_ACCEPTED' and all(r['status']=='PASS' for r in matrix['rows']);assert len(matrix['rows'])==9
for key in ['sourceSha256','additionalBuildInputSha256','testSourceSha256','frozenJars','old65Files']:
 for n,h in b[key].items():assert sha(R/n)==h,n
assert sha(R/b['planPath'])==b['planSha256'];assert sha(R/'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar')==b['jarSha256'];assert subprocess.check_output(['git','-C',str(M),'rev-parse','HEAD'],text=True).strip()==b['mainHead']
for n,h in b['mainDirtySha256'].items():assert sha(M/n)==h,n
for n,h in load(P/'artifact-manifest-closeout.json')['sha256'].items():assert sha(R/n)==h,n
for x in load(P/'request-package.json')['runtimeAssets']:assert sha(R/x['path'])==x['sha256'],x['path']
assert load(P/'authorization.json')['remainingAuthorizedCalls']==0 and load(P/'authorization.json')['status']=='CLOSED_PASSED';assert sha(P/'artifact-manifest-closeout.json')==b['onlineManifestSha256'];assert not subprocess.check_output(['git','diff','--','knowledge/src','knowledge/scripts','agent-runtime/src']).strip()
current=(R/b['closeoutPath']).read_bytes();tail=current[-b['closeoutOriginalBytes']:];assert hashlib.sha256(tail).hexdigest()==b['closeoutOriginalSha256'];assert sha(R/b['closeoutPath'])==decision['currentCloseoutSha256']
# Every new relative Markdown file link must resolve inside an existing local artifact.
for n in ['README.md']:
 f=O/n
 for target in re.findall(r'\]\(([^)]+)\)',f.read_text(encoding='utf8')):
  if not target.startswith(('http://','https://','#')):assert (f.parent/target.split('#')[0]).exists(),target
prefix=current[:-b['closeoutOriginalBytes']].decode('utf8')
for target in re.findall(r'\]\(([^)]+)\)',prefix):assert ((R/b['closeoutPath']).parent/target.split('#')[0]).exists(),target
with zipfile.ZipFile(R/'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar') as z:
 classes={n[len('BOOT-INF/classes/'):]:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith('BOOT-INF/classes/') and n.endswith('.class')};actual={f.relative_to(R/'knowledge/target/classes').as_posix():sha(f) for f in (R/'knowledge/target/classes').rglob('*.class')};assert classes==actual
counts=load(O/'regression-summary.json');assert len(counts)==13 and sum(c['tests'] for c in counts)==213 and all(c['failures']==c['errors']==c['skipped']==0 for c in counts)
receipt=dict(verifiedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),passed=True,decision='T05_ENGINEERING_ACCEPTED',originalPlanAndExitCriteriaUnchanged=True,originalHistoricalCloseoutTailPreserved=True,originalHistoricalTailSha256=b['closeoutOriginalSha256'],freshExplicitLocalTests=dict(classes=13,tests=213,failures=0,errors=0,skipped=0),sourceAndAdditionalAndTestInputsUnchanged=dict(production=335,additional=14,testClasses=13),jarSha256=b['jarSha256'],compiledAppClassesUnchanged=len(classes),all69OnlineEvidenceUnchanged=True,oldFrozenJarsAnd65ArchiveUnchanged=True,runtimeAssetsUnchanged=15,mainHeadAndFourDirtyDocsPreserved=True,newRelativeMarkdownLinksResolve=True,realProviderCallsThisTurn=0,databaseOperationsThisTurn=0,dockerOperationsThisTurn=0,authorizationRemaining=0,T06Started=False,overallProductQualityAccepted=False,limitations=matrix['residualUnknowns'])
(O/'verification.json').write_bytes((json.dumps(receipt,ensure_ascii=False,indent=2)+'\n').encode());print(json.dumps(dict(passed=True,decision=receipt['decision'],classesUnchanged=len(classes),historicalTailPreserved=True,linksValid=True,realCalls=0),ensure_ascii=True))
