import pathlib,json,hashlib,subprocess,zipfile,shutil,datetime,os,tempfile
R=pathlib.Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-68-path-build-identity-local'
def sha(b):return hashlib.sha256(b).hexdigest()
def h(p):return sha(p.read_bytes())
b=json.loads((O/'identity-before.json').read_text(encoding='utf-8-sig'));head=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip();assert head.startswith('bc5cdf4')
source={n:h(R/n) for n in b['sourceBeforeSha256']};extra={n:h(R/n) for n in b['additionalBuildInputSha256']}
changed=[n for n,v in source.items() if v!=b['sourceBeforeSha256'][n]];assert set(changed)=={'knowledge/scripts/start-rule-agent-probe.ps1','knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineProbeHost.java'},changed
assert extra==b['additionalBuildInputSha256']
# Batch Git blobs bind exact production source bytes. Build files may have existing CRLF checkout conversion.
names=list(source)+list(extra);proc=subprocess.run(['git','cat-file','--batch'],input=(''.join(head+':'+n+'\n' for n in names)).encode(),stdout=subprocess.PIPE,check=True);data=proc.stdout;i=0;bindings={}
for n in names:
 end=data.index(b'\n',i);parts=data[i:end].split();size=int(parts[-1]);blob=data[end+1:end+1+size];i=end+size+2
 if n in source:assert sha(blob)==source[n],n
 else:assert subprocess.check_output(['git','hash-object','--path',n,n]).strip()==parts[0],n
 bindings[n]=parts[0].decode()
for key in ['protectedFrozenJars','protectedT05_65FilesSha256']:
 for n,v in b[key].items():assert h(R/n)==v,n
M=pathlib.Path('D:/alldesk/Projects/ZhkuAgent');assert subprocess.check_output(['git','-C',str(M),'rev-parse','HEAD'],text=True).strip()==b['mainHead']
for n,v in b['mainDirtySha256'].items():assert h(M/n)==v,n
assert not (R/b['newRequestPath']).parent.exists()
jar=R/'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar';s=h(jar);assert s!=b['defaultJarBeforeSha256'];frozen=R/('knowledge/target/review-'+head[:8]+'-'+s[:12]+'-controlled-frozen.jar');
if frozen.exists():assert h(frozen)==s
else:shutil.copyfile(jar,frozen)
assert h(frozen)==s
with zipfile.ZipFile(jar) as z:
 classes={n[len('BOOT-INF/classes/'):]:sha(z.read(n)) for n in z.namelist() if n.startswith('BOOT-INF/classes/') and n.endswith('.class')};disk={p.relative_to(R/'knowledge/target/classes').as_posix():h(p) for p in (R/'knowledge/target/classes').rglob('*.class')};assert classes==disk
 libs={n:sha(z.read(n)) for n in z.namelist() if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')};runtime=next(n for n in libs if '/agent-runtime-' in n);rjar=R/'agent-runtime/target'/pathlib.PurePosixPath(runtime).name;assert h(rjar)==libs[runtime]
 manifest=z.read('META-INF/MANIFEST.MF');(O/'jar-manifest.txt').write_bytes(manifest)
with zipfile.ZipFile(rjar) as z:rc={n:sha(z.read(n)) for n in z.namelist() if n.endswith('.class')};rd={p.relative_to(R/'agent-runtime/target/classes').as_posix():h(p) for p in (R/'agent-runtime/target/classes').rglob('*.class')};assert rc==rd
result=dict(verifiedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),sourceCommit=head,sourceParent=subprocess.check_output(['git','rev-parse','HEAD^'],text=True).strip(),sourceSha256=source,additionalBuildInputSha256=extra,gitBlobIdentity=bindings,changedProductionInputs=changed,jarPath=jar.relative_to(R).as_posix(),jarSha256=s,frozenCopy=frozen.relative_to(R).as_posix(),applicationClassCount=len(classes),applicationClassSha256=classes,embeddedLibrarySha256=libs,agentRuntimeClassCount=len(rc),agentRuntimeClassSha256=rc,protectedFrozenJarsUnchanged=len(b['protectedFrozenJars']),protectedOld65FilesUnchanged=len(b['protectedT05_65FilesSha256']),mainHeadAndDirtyDocsPreserved=True,formal69DirectoryAbsent=True,embeddedGitSha=False,identityBinding='Offline build after source commit; exact raw-source/Git blob binding; current compiled classes equal jar bytes; frozen jar SHA256; runtime test pending separately.')
(O/'build-identity.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps({k:result[k] for k in ['sourceCommit','jarSha256','frozenCopy','applicationClassCount','agentRuntimeClassCount','protectedFrozenJarsUnchanged']},ensure_ascii=False))
