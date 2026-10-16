import pathlib,subprocess,zipfile,tempfile,shutil,os,json,time
R=pathlib.Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-16/t05-68-path-build-identity-local';base=pathlib.Path(tempfile.gettempdir()).resolve();tmp=pathlib.Path(tempfile.mkdtemp(prefix='zhku-t05-68-',dir=base)).resolve();assert tmp.parent==base and tmp.name.startswith('zhku-t05-68-');jar=R/'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar';app=tmp/'app';lib=tmp/'lib';helper=tmp/'helper';helper.mkdir();app.mkdir();lib.mkdir();env={k:v for k,v in os.environ.items() if not k.startswith(('ZHKU_','DASHSCOPE_','POSTGRES_')) and k not in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')};processes=[]
def run(name,args,limit):
 start=time.monotonic();p=subprocess.run(args,cwd=R,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=limit);out=p.stdout.decode('utf8',errors='replace');err=p.stderr.decode('utf8',errors='replace');(O/(name+'.log')).write_text(out+'\n'+err,encoding='utf8');processes.append(dict(name=name,exitCode=p.returncode,seconds=round(time.monotonic()-start,3),timedOut=False,resourcesClosed=True));assert p.returncode==0,name+' failed; see log';return out
try:
 with zipfile.ZipFile(jar) as z:
  for n in z.namelist():
   if n.startswith('BOOT-INF/classes/') and n.endswith('.class'):
    p=app/n[len('BOOT-INF/classes/'):];p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
   elif n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):(lib/pathlib.PurePosixPath(n).name).write_bytes(z.read(n))
 javac=shutil.which('javac.exe');java=shutil.which('java.exe');assert javac and java
 run('runtime-helper-compile',[javac,'--release','21','-encoding','UTF-8','-proc:none','-cp',str(app)+';'+str(lib/'*'),'-d',str(helper),str(O/'RuntimeBuildIdentity.java')],30)
 out=run('runtime-identity',[java,'-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Djava.security.manager=allow','-Dloader.path='+str(helper),'-Dloader.main=com.zhku.knowledge.ruleagent.RuntimeBuildIdentity','-cp',str(jar),'org.springframework.boot.loader.launch.PropertiesLauncher',str(R)],40)
 result=json.loads(next(x for x in reversed(out.splitlines()) if x.startswith('{')));assert result['passed'];(O/'runtime-identity.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8');print(json.dumps(dict(passed=True,assets=len(result['runtimeAssets']),sampleRequestBytes=result['sampleRequestBytes'],providerCalls=result['providerCalls']),ensure_ascii=False))
finally:
 assert tmp.resolve().parent==base and tmp.name.startswith('zhku-t05-68-');shutil.rmtree(tmp);(O/'runtime-process.json').write_text(json.dumps(dict(processes=processes,temporaryDirectoryRemoved=not tmp.exists(),providerCalls=0,databaseOperations=0,dockerOperations=0),indent=2),encoding='utf8')
