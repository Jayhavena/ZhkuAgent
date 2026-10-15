#requires -Version 7.0
param([switch]$CheckEnvironment,[switch]$PrepareProbe,[switch]$Probe,[switch]$BuildOnly,[switch]$OnlyP01,[switch]$OnlyP02,[switch]$IncludeClarification,[switch]$CheckPackage,
 [ValidatePattern('^[a-f0-9]{64}$')][string]$ApprovedPackageSha256,
 [ValidateRange(1024,65535)][int]$DatabasePort=55518)
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$importer=Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
$java=Get-Command java.exe -ErrorAction SilentlyContinue
$docker=Get-Command docker.exe -ErrorAction SilentlyContinue
$jar=Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar'
$package=Join-Path $root 'docs/evidence/rule-agent-provider/2026-10-15/t05-62-eligibility-request/request-package.json'
if($CheckEnvironment){
 & $importer -UseDashScope
 @{javaAvailable=($null-ne $java);dockerAvailable=($null-ne $docker);keyAvailable=![string]::IsNullOrWhiteSpace($env:DASHSCOPE_API_KEY);mode='P02_DIAGNOSTIC_PREFLIGHT';maximumProviderCalls=10;providerCalls=0;databaseOperations=0;builds=0;encodingSample='规则查证：在线探针、等待授权';encoding=[Console]::OutputEncoding.CodePage;requiresRipgrep=$false}|ConvertTo-Json
 exit 0
}
if(@($PrepareProbe,$Probe,$BuildOnly,$CheckPackage|Where-Object {$_}).Count-ne 1){throw '请选择PrepareProbe、Probe、BuildOnly或CheckPackage之一'}
if($IncludeClarification-or $OnlyP01){throw '旧P01/组合入口已关闭；本批仅OnlyP02'}
if(($Probe-or $CheckPackage)-and !$OnlyP02){throw '必须明确OnlyP02及本包独立授权'}
# 已存在的冻结申请必须在构建前拒绝，否则重复Prepare会先改写默认jar身份。
if($PrepareProbe-and (Test-Path -LiteralPath $package)){throw '请求包已存在，保留原包，不构建或覆盖'}
if(!$java){throw '普通终端未找到Java；先执行CheckEnvironment'}
Push-Location $root
try{
 if($BuildOnly-or $PrepareProbe){
  foreach($name in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD','POSTGRES_PASSWORD')){[Environment]::SetEnvironmentVariable($name,$null,'Process')}
  $built=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','mvnw.cmd','-o',"-Dmaven.repo.local=$env:USERPROFILE/.m2/repository",'-Dfile.encoding=UTF-8','-pl','knowledge','-am','-DskipTests','package') -TimeoutSeconds 150
  if($built.exitCode-ne 0-or $built.timedOut-or !$built.resourcesClosed){throw '探针构建失败或超时，不启动旧jar'}
  if($BuildOnly){exit 0}
 }
 $env:ZHKU_RULE_AGENT_ROOT=$root
 $arguments=@('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dloader.main=com.zhku.knowledge.ruleagent.RuleAgentOnlineProbeHost','-cp',$jar,'org.springframework.boot.loader.launch.PropertiesLauncher')
 if($CheckPackage){
  # 零调用包校验不导入真实凭证，也不构造模型或数据库客户端。
  if(!$ApprovedPackageSha256-or !(Test-Path -LiteralPath $package)-or (Get-FileHash -LiteralPath $package).Hash.ToLowerInvariant()-ne $ApprovedPackageSha256){throw '待核对请求包指纹不同'}
  $env:ZHKU_RULE_AGENT_PROBE_SHA256=$ApprovedPackageSha256
  $result=Invoke-ZhkuOwnedProcess -Executable $java.Source -Arguments ($arguments+@('--CheckPackage')) -TimeoutSeconds 15
  if($result.exitCode-ne 0-or !$result.resourcesClosed-or $result.timedOut){throw '零调用包校验失败；不执行模型'}
  $result.output
  exit 0
 }
 if($PrepareProbe){
  if(Test-Path -LiteralPath $package){throw '请求包已存在，保留原包，不覆盖'}
  $result=Invoke-ZhkuOwnedProcess -Executable $java.Source -Arguments ($arguments+@('--PrepareProbe')) -TimeoutSeconds 15
  if($result.exitCode-ne 0-or !$result.resourcesClosed-or $result.timedOut){throw '请求包准备未完成；不执行模型'}
  $result.output
  exit 0
 }
 # 哈希只绑定已审阅请求包，不等于获得许可；调用本分支须有用户明确批准本批外发。
 if(!$ApprovedPackageSha256-or !(Test-Path -LiteralPath $package)-or (Get-FileHash -LiteralPath $package).Hash.ToLowerInvariant()-ne $ApprovedPackageSha256){throw '缺少已批准的具体请求包指纹'}
 # 在导入凭证和启动容器前核对实际构建及全部运行资产；不能让旧jar消耗新批次。
 $env:ZHKU_RULE_AGENT_PROBE_SHA256=$ApprovedPackageSha256
 $checked=Invoke-ZhkuOwnedProcess -Executable $java.Source -Arguments ($arguments+@('--CheckPackage')) -TimeoutSeconds 15
 if($checked.exitCode-ne 0-or !$checked.resourcesClosed-or $checked.timedOut){throw '本批源码、构建或运行资产变化；不创建临时库、不调用模型'}
 if(!$docker-or $DatabasePort-eq 54329){throw '需要Docker及专用临时库端口，拒绝原库'}
 & $importer -RequireDatabase -RequireDashScope
 $secrets=@($env:ZHKU_DB_PASSWORD,$env:DASHSCOPE_API_KEY)
 $env:POSTGRES_PASSWORD=$env:ZHKU_DB_PASSWORD
 $env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/zhkuagent_rulecheck_e2e"
 $env:ZHKU_RULE_AGENT_MODE='ONLINE';$env:ZHKU_RULE_AGENT_AUTHORIZATION_ID=$ApprovedPackageSha256;$env:ZHKU_RULE_AGENT_PROBE_SHA256=$ApprovedPackageSha256
 $env:ZHKU_RULE_AGENT_PROBE_CASE='P02_CONTINUATION_01'
 # 当前.NET/PowerShell可能将null转换为空值而保留键；ONLINE拒绝任何回放键，必须真正删除。
 foreach($name in @('ZHKU_RULECHECK_TASK_BATCH_DIRECTORY','ZHKU_RULECHECK_TASK_PACKAGE_SHA256','ZHKU_RULECHECK_TASK_SUMMARY_SHA256')){Remove-Item -LiteralPath "Env:\$name" -ErrorAction SilentlyContinue}
 $env:ZHKU_RULE_AGENT_EVIDENCE='docs/evidence/rule-agent-provider/2026-10-15/t05-62-eligibility-request/live-'+[guid]::NewGuid().ToString('N')
 $container='zhku-rule-agent-probe-'+[guid]::NewGuid().ToString('N')
 function Docker([string[]]$argsForDocker){
  & $importer -RequireDatabase
  $r=Invoke-ZhkuOwnedProcess -Executable $docker.Source -Arguments $argsForDocker -TimeoutSeconds 10 -Secrets $secrets
  if($r.exitCode-ne 0-or !$r.resourcesClosed-or $r.timedOut){throw '本次Docker操作失败，保留失败结果，不重试探针'}
  return $r
 }
 try{
  $null=Docker @('run','--rm','-d','--pull','never','--name',$container,'--publish',"127.0.0.1:${DatabasePort}:5432",'--env','POSTGRES_PASSWORD','--env','POSTGRES_USER=zhkuagent','--env','POSTGRES_DB=zhkuagent_rulecheck_e2e','pgvector/pgvector:0.8.6-pg16-bookworm')
  $ready=$false;$deadline=[DateTime]::UtcNow.AddSeconds(30)
  while([DateTime]::UtcNow-lt $deadline){try{$null=Docker @('exec',$container,'pg_isready','-U','zhkuagent','-d','zhkuagent_rulecheck_e2e');$ready=$true;break}catch{Start-Sleep -Milliseconds 200}}
  if(!$ready){throw '临时库未就绪，不调用模型'}
  & $importer -RequireDatabase -RequireDashScope
  $result=Invoke-ZhkuOwnedProcess -Executable $java.Source -Arguments ($arguments+@('--Probe')) -TimeoutSeconds 235 -Secrets $secrets
  # 宿主可能在创建业务证据目录前失败，也必须保留已脱敏的启动诊断，不能丢失失败位置。
  $launcherLog=Join-Path $root ('docs/evidence/rule-agent-provider/2026-10-15/t05-62-eligibility-request/launcher-'+[guid]::NewGuid().ToString('N')+'.log')
  New-Item -ItemType Directory -Force (Split-Path $launcherLog -Parent)|Out-Null
  [IO.File]::WriteAllText($launcherLog,$result.output+"`n"+$result.errorOutput,$utf8)
  $directory=Join-Path $root $env:ZHKU_RULE_AGENT_EVIDENCE
  if(Test-Path -LiteralPath $directory){[IO.File]::WriteAllText((Join-Path $directory 'host.log'),$result.output+"`n"+$result.errorOutput,$utf8)}
  if($result.exitCode-ne 0-or !$result.resourcesClosed-or $result.timedOut){throw '真实探针未通过或退出未确认；不增加调用或自动重试'}
 }finally{
  # 只回收本次随机命名的临时容器，不能按进程名结束用户客户端或浏览器。
  $null=Docker @('rm','-f',$container)
 }
}finally{Pop-Location}
