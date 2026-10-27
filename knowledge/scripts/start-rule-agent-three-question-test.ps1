#requires -Version 7.0
<# 仅测试classpath入口；默认Prepare不读模型凭证、不建数据库。Live必须另获本包SHA/16.10元授权。 #>
param(
 [ValidateSet('Prepare','Verify','Assembly','Live','PageFixture')][string]$Operation='Prepare',
 [ValidateSet('Three','E02')][string]$Profile='Three',
 [string]$AssetsRoot,
 [string]$BatchDirectory,
 [ValidateRange(1024,65535)][int]$HttpPort=18087,
 [ValidateSet('4.02','16.10')][string]$HardBudgetRmb=$(if($Profile -eq 'E02'){'4.02'}else{'16.10'}),
 [string]$PageEvidenceDirectory,
 [ValidateRange(0,65535)][int]$PageProxyPort=0,
 [string]$PackageSha256,
 [string]$ApprovedSha256,
 [string]$ApprovedBudgetRmb,
 [string]$AuthorizationId,
 [ValidateRange(1024,65535)][int]$DatabasePort=55527
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
# ONLINE页面预检只能附着零执行Assembly；绝不借此打开Live或沿用旧三题授权。
if($PageProxyPort -ne 0){
 if($Operation -ne 'Assembly' -or $PageProxyPort -lt 1024 -or $PageProxyPort -eq $DatabasePort){throw 'PageProxyPort仅允许Assembly独立前端回环端口'}
 if(Get-NetTCPConnection -State Listen -LocalPort $PageProxyPort -ErrorAction SilentlyContinue){throw '预检前端端口已占用，不替换现有服务'}
}
# 页面夹具先分流：不审旧Live包、不查Docker、不导入/读取任何本机凭证。
if($Operation -eq 'PageFixture'){
 if(Get-NetTCPConnection -State Listen -LocalPort $HttpPort -ErrorAction SilentlyContinue){throw '页面夹具端口已占用，不替换现有服务'}
 $allowed=[IO.Path]::GetFullPath((Join-Path $root 'docs/evidence/rule-check-refactor/2026-10-27'))
 if(!$PageEvidenceDirectory){$PageEvidenceDirectory=Join-Path $allowed ('student-page-fixture-'+[guid]::NewGuid().ToString('N'))}
 $page=[IO.Path]::GetFullPath($PageEvidenceDirectory)
 if(!$page.StartsWith($allowed+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw '页面证据须位于本轮独立子目录'}
 if(Test-Path -LiteralPath $page){throw '不覆盖已有页面验收目录'}
 [void][IO.Directory]::CreateDirectory($page)
 foreach($name in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','ZHKU_DB_URL')){Remove-Item -LiteralPath "Env:\$name" -ErrorAction SilentlyContinue}
 & mvn.cmd -f (Join-Path $root 'pom.xml') -o -pl knowledge -am '-DfailIfNoTests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=RuleAgentThreeQuestionHostTest' '-Dzhku.three.operation=page-fixture' '-Dzhku.t07.browserLease=true' "-Dzhku.t07.port=$HttpPort" "-Dzhku.rulecheck.evidenceDir=$page" test
 exit $LASTEXITCODE
}
if(!$AssetsRoot -or !$BatchDirectory){throw '非页面操作仍须显式AssetsRoot和BatchDirectory'}
$assets=(Resolve-Path -LiteralPath $AssetsRoot).Path
# E02 单题与三题各自独立证据根：不共用批次目录，也不续用旧授权。
$evidence=(Join-Path $root ($(if($Profile-eq 'E02'){'docs/evidence/rule-check-refactor/2026-10-27/e02-single-page-live'}else{'docs/evidence/rule-check-refactor/2026-10-26/three-question-online-prepare'})))
$batch=[IO.Path]::GetFullPath($BatchDirectory)
if(!$batch.StartsWith($evidence+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw '本批只允许独立证据目录内的子目录'}
if($Operation-eq 'Prepare'){
 if(Test-Path -LiteralPath $batch){throw '不覆盖已有批次；另建新目录'}
 [void][IO.Directory]::CreateDirectory((Split-Path -Parent $batch))
}else{
 if(!(Test-Path -LiteralPath (Join-Path $batch 'request-package.json') -PathType Leaf)-or $PackageSha256-notmatch '^[a-f0-9]{64}$'){throw '须显式提供已有包与完整SHA'}
}
if($Operation-eq 'Live'){
 if($ApprovedSha256-ne $PackageSha256-or $ApprovedBudgetRmb-ne $HardBudgetRmb-or $AuthorizationId-notmatch '^[A-Za-z0-9._-]{1,128}$'-or $AuthorizationId-eq 'LOCAL_TEST_ONLY'){throw "Live须先获本包SHA、$HardBudgetRmb元硬预算及新授权编号的明确人工授权"}
}
$env:ZHKU_DB_URL=$null
# 旧批次/旧回放变量不继承，ONLINE不准自动落回旧资产。
foreach($name in @('ZHKU_RULECHECK_TASK_BATCH_DIRECTORY','ZHKU_RULECHECK_TASK_PACKAGE_SHA256','ZHKU_RULECHECK_TASK_SUMMARY_SHA256','ZHKU_RULE_AGENT_AUTHORIZATION_ID','ZHKU_RULE_AGENT_MODE','DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD')){Remove-Item -LiteralPath "Env:\$name" -ErrorAction SilentlyContinue}
$importer=Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
$secrets=@()
$container=$null
$ownedContainer=$false
$failed=$false
$pwsh=(Get-Command pwsh.exe -ErrorAction Stop).Source
$mvn=(Get-Command mvn.cmd -ErrorAction Stop).Source
$docker=(Get-Command docker.exe -ErrorAction Stop).Source
# 使用本轮独立有界进程封装，不修改旧235秒进程预算。
function Invoke-Child([string]$exe,[string[]]$argv,[int]$seconds){
 $info=[Diagnostics.ProcessStartInfo]::new($exe);$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.WorkingDirectory=$root
 $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
 foreach($arg in $argv){[void]$info.ArgumentList.Add($arg)}
 $p=[Diagnostics.Process]::Start($info);$stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync();$timedOut=$false
 try{
  if(!$p.WaitForExit($seconds*1000)){$timedOut=$true;try{$p.Kill($true)}catch{};if(!$p.WaitForExit(5000)){throw '本次创建的进程树未退出'}}
  if(![Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($stdout,$stderr),2000)){throw '子进程输出管道未关闭'}
  $out=$stdout.GetAwaiter().GetResult();$err=$stderr.GetAwaiter().GetResult()
  foreach($secretValue in $secrets){if($secretValue){$out=$out.Replace($secretValue,'[REDACTED]');$err=$err.Replace($secretValue,'[REDACTED]')}}
  [pscustomobject]@{exitCode=$p.ExitCode;timedOut=$timedOut;stdout=$out;stderr=$err;processExited=$p.HasExited}
 }finally{$p.Dispose()}
}
function Invoke-Maven([string]$op,[int]$timeout){
 $args=@('-o','-pl','knowledge','-am','-DfailIfNoTests=false','-Dsurefire.failIfNoSpecifiedTests=false','-Dtest=RuleAgentThreeQuestionHostTest',"-Dzhku.three.operation=$($op.ToLowerInvariant())","-Dzhku.three.codeRoot=$root","-Dzhku.t06.root=$assets","-Dzhku.three.batch=$batch")
 # E02 单题计划与三题计划互不复用：缺省即 Three，显式才切 E02。
 if($Profile-eq 'E02'){$args+='-Dzhku.three.profile=E02'}
 if($PackageSha256){$args+="-Dzhku.three.sha256=$PackageSha256"}
 if($op -eq 'Assembly' -and $PageProxyPort -ne 0){$args+="-Dzhku.three.pageProxyPort=$PageProxyPort"}
 if($op-eq 'Live'){$args+=@("-Dzhku.three.approvedSha256=$ApprovedSha256","-Dzhku.three.approvedBudgetRmb=$ApprovedBudgetRmb","-Dzhku.three.authorizationId=$AuthorizationId")}
 $args+='test'
 # 参数值只包含路径/公开SHA/批号，不含任何凭证；凭证只由当前子进程环境继承。
 $quoted=@($args|ForEach-Object {"'"+$_.Replace("'","''")+"'"})-join ','
 $command="& '"+$mvn.Replace("'","''")+"' @($quoted)"+'; exit $LASTEXITCODE'
 $encoded=[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
 Invoke-Child $pwsh @('-NoProfile','-EncodedCommand',$encoded) $timeout
}
function Require-Pass($result,[string]$name){
 if($result.exitCode-ne 0-or $result.timedOut-or !$result.processExited){throw "$name 未通过；不进入下一阶段"}
}
try{
 if($Operation-eq 'Assembly'-or $Operation-eq 'Live'){
  # 先验证字节，再创建任何数据库；此阶段即使Live也没有模型密钥。
  $check=Invoke-Maven 'Verify' 120;Require-Pass $check '审包检查'
  if($DatabasePort-in @(54329,55441,55442)){throw '拒绝原演示/冻结评测端口'}
  if(Get-NetTCPConnection -State Listen -LocalPort $DatabasePort -ErrorAction SilentlyContinue){throw '临时端口被占用，不替换现有资源'}
  & $importer -RequireDatabase
  $secrets=@($env:ZHKU_DB_PASSWORD);$env:POSTGRES_PASSWORD=$env:ZHKU_DB_PASSWORD
  $env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/zhkuagent_rulecheck_e2e"
  $container='zhku-three-question-'+[guid]::NewGuid().ToString('N')
  $started=Invoke-Child $docker @('run','--rm','-d','--pull','never','--name',$container,'--label','zhku.owner=three-question-test','--publish',"127.0.0.1:${DatabasePort}:5432",'--env','POSTGRES_PASSWORD','--env','POSTGRES_USER=zhkuagent','--env','POSTGRES_DB=zhkuagent_rulecheck_e2e','pgvector/pgvector:0.8.6-pg16-bookworm') 20
  Require-Pass $started '一次性临时库创建';$ownedContainer=$true
  $ready=$false;$deadline=[DateTime]::UtcNow.AddSeconds(30)
  while([DateTime]::UtcNow-lt $deadline){$probe=Invoke-Child $docker @('exec',$container,'pg_isready','-U','zhkuagent','-d','zhkuagent_rulecheck_e2e') 5;if($probe.exitCode-eq 0){$ready=$true;break};Start-Sleep -Milliseconds 200}
  if(!$ready){throw '新临时库未就绪'}
  # 每次调用前按项目规范重新检查凭证；值不进入参数/日志/包。
  & $importer -RequireDatabase
  if($Operation-eq 'Live'){& $importer -RequireDashScope;$secrets+=@($env:DASHSCOPE_API_KEY)}
 }
 $timeout=if($Operation-eq 'Live'){660}elseif($Operation-eq 'Assembly'){240}else{120}
 $result=Invoke-Maven $Operation $timeout
 $log=Join-Path $evidence ($Operation.ToLowerInvariant()+'-'+[guid]::NewGuid().ToString('N')+'.log')
 [IO.File]::WriteAllText($log,$result.stdout+"`n"+$result.stderr,[Text.UTF8Encoding]::new($false))
 $result.stdout;$result.stderr
 Require-Pass $result $Operation
} catch {$failed=$true;throw} finally {
 $removed=$false
 # 只清理本轮随机唯一名称；绝不按镜像名/端口批量删除。创建未被确认也核对同名label后清理。
 if($container){
  $identity=Invoke-Child $docker @('inspect','--format','{{index .Config.Labels "zhku.owner"}}',$container) 10
  if($identity.exitCode-eq 0-and $identity.stdout.Trim()-eq 'three-question-test'){
   $cleanup=Invoke-Child $docker @('rm','-f',$container) 15
   if($cleanup.exitCode-eq 0){$remaining=Invoke-Child $docker @('ps','-a','--filter',"name=^/$container$",'--format','{{.Names}}') 10;$removed=$remaining.exitCode-eq 0-and [string]::IsNullOrWhiteSpace($remaining.stdout)}
   if(!$removed){throw '本批临时容器清理未获确认'}
  }elseif($ownedContainer){throw '已创建容器身份无法确认，不擅自删除'}
 }
 if(Test-Path -LiteralPath $batch -PathType Container){
  $receipt=@{operation=$Operation;packageSha256=$PackageSha256;paidModelCallsPermitted=($Operation-eq 'Live');ownedContainer=$container;containerRemoved=$removed;failed=$failed;finishedAt=[DateTime]::UtcNow.ToString('o')}
  $path=Join-Path $batch ('launcher-'+$Operation.ToLowerInvariant()+'-'+[guid]::NewGuid().ToString('N')+'.json')
  [IO.File]::WriteAllText($path,($receipt|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
 }
 foreach($name in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','ZHKU_DB_URL')){Remove-Item -LiteralPath "Env:\$name" -ErrorAction SilentlyContinue}
}
