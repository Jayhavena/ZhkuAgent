from pathlib import Path
import os,json,hashlib,zipfile,tempfile,shutil,subprocess,datetime
R=Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-66-condition-key-local-control';P=R/'docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request';L=next(P.glob('live-*'));load=lambda f:json.loads(f.read_text(encoding='utf-8-sig'));before=load(O/'identity-before.json');jar=R/'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar';assert hashlib.sha256(jar.read_bytes()).hexdigest()==before['jarSha256'];env=dict(os.environ)
for k in list(env):
 if k.startswith(('ZHKU_','DASHSCOPE_','POSTGRES_')) or k in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):del env[k]
parent=Path(tempfile.gettempdir()).resolve();work=Path(tempfile.mkdtemp(prefix='zhku-t05-66-',dir=parent)).resolve();assert work.is_relative_to(parent) and work.name.startswith('zhku-t05-66-');app=work/'compile-app';lib=work/'compile-libs';helper=work/'helper-only'
for d in [app,lib,helper]:d.mkdir()
report={'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'jarSha256':before['jarSha256'],'credentialsRemoved':True,'realModelCalls':0,'databaseOperations':0,'dockerOperations':0,'rebuilds':0,'temporaryDirectory':str(work)}
try:
 with zipfile.ZipFile(jar) as z:
  for n in z.namelist():
   assert '..' not in Path(n).parts
   if n.startswith('BOOT-INF/classes/') and n.endswith('.class'):
    f=app/n[len('BOOT-INF/classes/'):];f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(z.read(n))
   elif n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):(lib/Path(n).name).write_bytes(z.read(n))
 compile=subprocess.run([shutil.which('javac'),'-encoding','UTF-8','--release','21','-proc:none','-cp',str(app)+os.pathsep+str(lib/'*'),'-d',str(helper),str(O/'T05ConditionKeyReplay.java')],env=env,capture_output=True,timeout=45);(O/'compile.log').write_bytes(compile.stdout+compile.stderr);report['compilerExitCode']=compile.returncode;assert compile.returncode==0,'Helper compile failed; see compile.log'
 cmd=[shutil.which('java'),'-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Djava.security.manager=allow','-Dloader.path='+str(helper),'-Dloader.main=com.zhku.knowledge.ruleagent.T05ConditionKeyReplay','-cp',str(jar),'org.springframework.boot.loader.launch.PropertiesLauncher',str(R),str(O),str(L)]
 run=subprocess.run(cmd,env=env,capture_output=True,timeout=45);(O/'replay.log').write_bytes(run.stdout+run.stderr);report['replayExitCode']=run.returncode;report['processExited']=True;assert run.returncode==0,'Replay failed; see replay.log';assert load(O/'replay-result.json')['checksPassed'];report['passed']=True
finally:
 assert work.is_relative_to(parent) and work.name.startswith('zhku-t05-66-') and work!=parent;shutil.rmtree(work);report['temporaryDirectoryRemoved']=not work.exists();report['finishedUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat();(O/'process.json').write_bytes((json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode())
print(json.dumps(report))
