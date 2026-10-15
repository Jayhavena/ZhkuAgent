"""Run only the local diagnostic harness against exact frozen application classes.
No Maven build, Provider, Docker or database setup is invoked.
"""
from pathlib import Path
import datetime,hashlib,json,os,shutil,subprocess,tempfile,zipfile
ROOT=Path(__file__).resolve().parents[5]
OUT=Path(__file__).resolve().parent
JAR=ROOT/'knowledge/target/clarification-796c9589-82c25684f599-controlled-frozen.jar'
LIVE=ROOT/'docs/evidence/rule-agent-provider/2026-10-15/t05-55-clarification-request/live-c284f31002e5419f8c4b9061dcf5a599'
SHA='82c25684f5993b96ddea4fa72b6bca85e25e055ab82bedac663593081f0b1541'
h=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert h(JAR)==SHA
identity=json.loads((ROOT/'docs/evidence/rule-agent-provider/2026-10-15/t05-56-local-build-identity/build-identity.json').read_text(encoding='utf-8'))
env=dict(os.environ)
for k in list(env):
    if k.startswith(('ZHKU_','DASHSCOPE_','POSTGRES_')) or k in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):
        del env[k]
env['PYTHONIOENCODING']='utf-8'
temp_parent=Path(tempfile.gettempdir()).resolve()
work=Path(tempfile.mkdtemp(prefix='zhku-t05-58-',dir=temp_parent)).resolve()
assert work.is_relative_to(temp_parent) and work.name.startswith('zhku-t05-58-')
started=datetime.datetime.now(datetime.timezone.utc).isoformat()
record={'startedUtc':started,'jarSha256':SHA,'credentialVariablesRemoved':True,'temporaryDirectory':str(work),'realProviderCalls':0,'databaseOperations':0,'dockerOperations':0}
try:
    app=work/'frozen-app';libs=work/'frozen-libs';classes=work/'harness-classes'
    for d in (app,libs,classes):d.mkdir()
    with zipfile.ZipFile(JAR) as z:
        for n in z.namelist():
            if '..' in Path(n).parts:raise AssertionError('Invalid jar entry')
            if n.startswith('BOOT-INF/classes/') and n.endswith('.class'):
                dest=app/n[len('BOOT-INF/classes/'):];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(n))
            elif n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):
                (libs/Path(n).name).write_bytes(z.read(n))
    matches=0
    for n,digest in identity['applicationClassSha256'].items():
        name=n.removeprefix('BOOT-INF/classes/')
        assert h(app/name)==digest,n
        matches+=1
    record['frozenApplicationClassesVerified']=matches
    javac=shutil.which('javac');java=shutil.which('java')
    assert javac and java
    cp=str(app)+os.pathsep+str(libs/'*')
    compiler=subprocess.run([javac,'-encoding','UTF-8','--release','21','-proc:none','-cp',cp,'-d',str(classes),str(OUT/'Batch57BoundaryReplay.java')],env=env,capture_output=True,timeout=60)
    (OUT/'compile.log').write_bytes(compiler.stdout+compiler.stderr)
    record['compilerExitCode']=compiler.returncode
    if compiler.returncode:raise RuntimeError('Diagnostic harness compilation failed; see compile.log')
    run=subprocess.run([java,'-Djava.security.manager=allow','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',str(classes)+os.pathsep+cp,'com.zhku.knowledge.ruleagent.Batch57BoundaryReplay',str(LIVE),str(OUT/'boundary-results.json')],env=env,capture_output=True,timeout=60)
    (OUT/'replay.log').write_bytes(run.stdout+run.stderr)
    record['replayExitCode']=run.returncode
    if run.returncode:raise RuntimeError('Boundary replay failed; see replay.log')
    result=json.loads((OUT/'boundary-results.json').read_text(encoding='utf-8'))
    assert result['allMatched'] and result['exactOriginalSemanticInputReconstructed']
    assert h(JAR)==SHA
    record['passed']=True
finally:
    # Only remove this verified, generated temporary tree; never touch project assets.
    assert work.is_relative_to(temp_parent) and work.name.startswith('zhku-t05-58-') and work!=temp_parent
    shutil.rmtree(work)
    record['temporaryResourcesRemoved']=not work.exists()
    record['finishedUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
    (OUT/'replay-process.json').write_bytes((json.dumps(record,ensure_ascii=False,indent=2)+'\n').encode('utf-8'))
print(json.dumps({'passed':True,'checks':len(result['checks']),'originalFingerprint':result['originalInputFingerprint'],'zeroModelDatabaseAndNetwork':True,'temporaryResourcesRemoved':record['temporaryResourcesRemoved']}))
