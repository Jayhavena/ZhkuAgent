from pathlib import Path
import os,subprocess,json,datetime,time
R=Path.cwd();O=R/'docs/evidence/rule-agent-provider/2026-10-15/t05-63-p02-eligibility-live';read=lambda n:json.loads((O/n).read_text(encoding='utf-8-sig'));auth=read('authorization.json');assert auth['status']=='AUTHORIZED_PREFLIGHT';assert auth['maximumCalls']==10 and auth['maximumCny']==12
assert datetime.datetime.now(datetime.timezone.utc)<datetime.datetime.fromisoformat('2026-10-15T14:07:00+00:00')
port=read('environment-preflight.json')['databasePort'];ps=os.environ['WINDIR']+'/System32/WindowsPowerShell/v1.0/powershell.exe'
env=dict(os.environ)
for k in list(env):
 if k.startswith(('ZHKU_','DASHSCOPE_','POSTGRES_')) or k in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):del env[k]
paths=subprocess.check_output([ps,'-NoProfile','-Command',"[Environment]::ExpandEnvironmentVariables([Environment]::GetEnvironmentVariable('Path','Machine')+';'+[Environment]::GetEnvironmentVariable('Path','User'))"],env=env).decode('utf-8-sig').strip();env['PATH']=paths
start=datetime.datetime.now(datetime.timezone.utc).isoformat();auth['status']='RUNNING_SINGLE_BATCH';auth['executionStartedUtc']=start;(O/'authorization.json').write_bytes((json.dumps(auth,ensure_ascii=False,indent=2)+'\n').encode())
with (O/'probe-started.json').open('xb') as f:f.write(json.dumps({'startedUtc':start,'packageSha256':auth['packageSha256'],'batchOrdinal':1,'maximumBatches':1,'port':port}).encode())
code='[Console]::InputEncoding=[Text.UTF8Encoding]::new($false);[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false);$OutputEncoding=[Text.UTF8Encoding]::new($false); & ".\\knowledge\\scripts\\start-rule-agent-probe.cmd" -Probe -OnlyP02 -ApprovedPackageSha256 '+auth['packageSha256']+' -DatabasePort '+str(port)+';exit $LASTEXITCODE'
p=subprocess.Popen([ps,'-NoProfile','-Command',code],cwd=R,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE);report={'startedUtc':start,'ownedLauncherPid':p.pid,'batchOrdinal':1,'packageSha256':auth['packageSha256'],'outerTimeoutSeconds':330,'databasePort':port,'rebuild':False,'automaticRetry':False}
try:
 output,error=p.communicate(timeout=330);report.update(exitCode=p.returncode,timedOut=False,launcherProcessExited=True)
except subprocess.TimeoutExpired:
 subprocess.run(['taskkill','/PID',str(p.pid),'/T','/F'],capture_output=True,timeout=10);output,error=p.communicate(timeout=10);report.update(exitCode=p.returncode,timedOut=True,launcherProcessExited=True)
(O/'launcher-console.log').write_bytes(output+b'\n'+error);report['finishedUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat();(O/'execution.json').write_bytes((json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode());print(json.dumps(report))
