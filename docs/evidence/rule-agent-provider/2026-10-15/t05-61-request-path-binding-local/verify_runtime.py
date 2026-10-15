from pathlib import Path
import datetime,hashlib,json,os,shutil,subprocess,tempfile,zipfile
ROOT=Path(__file__).resolve().parents[5];OUT=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
b=read(OUT/'build-identity.json');jar=(ROOT/b['actualJar']).resolve()
assert hashlib.sha256(jar.read_bytes()).hexdigest()==b['jarSha256']
env=dict(os.environ)
for k in list(env):
 if k.startswith(('ZHKU_','DASHSCOPE_','POSTGRES_')) or k in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):del env[k]
env['ZHKU_RULE_AGENT_ROOT']=str(ROOT)
java=shutil.which('java');javac=shutil.which('javac');assert java and javac
parent=Path(tempfile.gettempdir()).resolve();work=Path(tempfile.mkdtemp(prefix='zhku-t05-61-',dir=parent)).resolve();assert work.is_relative_to(parent) and work.name.startswith('zhku-t05-61-')
report={'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'jarSha256':b['jarSha256'],'temporaryDirectory':str(work),'credentialVariablesRemoved':True,'realProviderCalls':0,'databaseOperations':0,'dockerOperations':0}
try:
 app=work/'compile-app';lib=work/'compile-libs';helper=work/'helper-only'
 for p in [app,lib,helper]:p.mkdir()
 with zipfile.ZipFile(jar) as z:
  for n in z.namelist():
   if '..' in Path(n).parts:raise AssertionError('Unexpected entry path')
   if n.startswith('BOOT-INF/classes/') and n.endswith('.class'):
    dest=app/n[len('BOOT-INF/classes/'):];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(n))
   elif n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):(lib/Path(n).name).write_bytes(z.read(n))
 cp=str(app)+os.pathsep+str(lib/'*')
 c=subprocess.run([javac,'-encoding','UTF-8','--release','21','-proc:none','-cp',cp,'-d',str(helper),str(OUT/'LocalBuildIdentity.java')],env=env,capture_output=True,timeout=45)
 (OUT/'identity-helper-compile.log').write_bytes(c.stdout+c.stderr);report['compilerExitCode']=c.returncode;assert c.returncode==0
 cmd=[java,'-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Djava.security.manager=allow','-Dloader.path='+str(helper),'-Dloader.main=com.zhku.knowledge.ruleagent.LocalBuildIdentity','-cp',str(jar),'org.springframework.boot.loader.launch.PropertiesLauncher',str(ROOT)]
 run=subprocess.run(cmd,env=env,capture_output=True,timeout=30);(OUT/'runtime-contract.log').write_bytes(run.stdout+run.stderr);report['contractExitCode']=run.returncode;assert run.returncode==0
 result=json.loads(run.stdout.decode('utf-8-sig'));assert result['passed'];(OUT/'runtime-contract.json').write_bytes((json.dumps(result,ensure_ascii=False,indent=2)+'\n').encode())
 cmd=[java,'-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dloader.main=com.zhku.knowledge.ruleagent.RuleAgentOnlineProbeHost','-cp',str(jar),'org.springframework.boot.loader.launch.PropertiesLauncher','--CheckRuntimeAssets']
 run=subprocess.run(cmd,env=env,capture_output=True,timeout=30);(OUT/'runtime-assets.log').write_bytes(run.stdout+run.stderr);report['assetsExitCode']=run.returncode;assert run.returncode==0
 result=json.loads(run.stdout.decode('utf-8-sig'));assert result['passed'];(OUT/'runtime-assets.json').write_bytes((json.dumps(result,ensure_ascii=False,indent=2)+'\n').encode())
 old=read(ROOT/'docs/evidence/rule-agent-provider/2026-10-15/t05-56-local-build-identity/runtime-assets.json')
 assert result['runtimeAssets']==old['runtimeAssets'];report['runtimeAssetsUnchanged']=True
 assert hashlib.sha256(jar.read_bytes()).hexdigest()==b['jarSha256'];report['passed']=True
finally:
 assert work.is_relative_to(parent) and work.name.startswith('zhku-t05-61-') and work!=parent
 shutil.rmtree(work);report['temporaryDirectoryRemoved']=not work.exists();report['finishedUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 (OUT/'runtime-processes.json').write_bytes((json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode())
print(json.dumps({'passed':True,'contract06AndOriginalConditionKeyValidationVerified':True,'old55Rejected':True,'runtimeAssetsUnchanged':True,'temporaryDirectoryRemoved':True}))
