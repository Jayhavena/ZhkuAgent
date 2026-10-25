#requires -Version 7.0
param(
 [ValidateSet('CheckEnvironment','Prepare','CheckPackage','StartApproved')][string]$Action='CheckEnvironment',
 [string]$PackageDirectory,
 [ValidatePattern('^[a-f0-9]{64}$')][string]$PackageSha256,
 [string]$AssetsRoot=''
)
# 2026-10-24迁移后外层旧目录只剩残缺docs副本；默认以当前工作树为资料根（git内审核资料完整，
# 手册PDF由Reviewed2025ControlledCatalogue按迁移布局回溯定位）。仍可显式传其他完整资料根。
if(!$AssetsRoot){$AssetsRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path}
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$importer=Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
$dist=Join-Path $root 'agent/frontend/dist-student'
$base=Join-Path $root 'docs/evidence/rule-agent-integration'
if($Action -eq 'CheckEnvironment'){
 [ordered]@{mode='ONLINE_HTTP_PACKAGE_PREFLIGHT_ONLY';javaAvailable=($null-ne(Get-Command java.exe -ErrorAction SilentlyContinue));nodeAvailable=($null-ne(Get-Command node.exe -ErrorAction SilentlyContinue));dockerAvailable=($null-ne(Get-Command docker.exe -ErrorAction SilentlyContinue));gitAvailable=($null-ne(Get-Command git.exe -ErrorAction SilentlyContinue));encodingSample='规则查证：先审包，再授权';encoding=[Console]::OutputEncoding.CodePage;providerCalls=0;databaseOperations=0;builds=0}|ConvertTo-Json
 exit 0
}
if(!$PackageDirectory){if($Action -ne 'Prepare'){throw '校包/启动须显式指定独立批次目录'};$PackageDirectory=Join-Path $base ((Get-Date -Format 'yyyy-MM-dd')+'/online-http-'+(Get-Date -Format 'HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8))}
$PackageDirectory=[IO.Path]::GetFullPath($PackageDirectory)
if(!$PackageDirectory.StartsWith($base+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)-or (Split-Path $PackageDirectory -Leaf) -notmatch '^online-http-[A-Za-z0-9-]+$'){throw '须使用本候选 evidence/rule-agent-integration 下唯一 online-http-* 批次'}
foreach($path in @($root,$AssetsRoot,$dist,$PackageDirectory)){if($path -match '[%!,&|<>^"\r\n]'){throw '启动路径包含不支持的命令解释字符'}}
# 覆盖/旧包拒绝必须早于构建，否则会先改变已冻结JAR身份。
if($Action -eq 'Prepare' -and (Test-Path -LiteralPath $PackageDirectory)){throw '目录已存在，原包及构建均保留；请另建批次'}
if($Action -ne 'Prepare'){
 if(!$PackageSha256 -or !(Test-Path -LiteralPath (Join-Path $PackageDirectory 'request-package.json')) -or (Get-FileHash -LiteralPath (Join-Path $PackageDirectory 'request-package.json')).Hash.ToLowerInvariant() -ne $PackageSha256){throw '包字节或明确指纹缺失，不启动任何外部资源'}
}
if($Action -eq 'StartApproved' -and $env:ZHKU_HTTP_APPROVED_PACKAGE_SHA256 -ne $PackageSha256){throw '缺少本批明确人工许可的SHA；参数/构包本身不是授权'}
$authorizationDirectory=Join-Path $base 'authorization-reservations'
$reservationFile=if($PackageSha256){Join-Path $authorizationDirectory ($PackageSha256+'.json')}else{$null}
if($Action -eq 'StartApproved' -and (Test-Path -LiteralPath $reservationFile)){throw '本包已占用或执行过；失败也不恢复额度，不能重新启动'}
$AssetsRoot=(Resolve-Path -LiteralPath $AssetsRoot).Path
$trackedEnv=@('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','ZHKU_DB_URL','ZHKU_RULE_AGENT_MODE','ZHKU_RULE_AGENT_AUTHORIZATION_ID','VITE_ENTRY_MODE','ZHKU_HTTP_RESERVATION_TOKEN')
$saved=@{};foreach($name in $trackedEnv){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$container=$null;$containerAttempted=$false;$failed=$false
$launchLog=Join-Path $env:TEMP ('zhku-online-http-launch-'+[guid]::NewGuid().ToString('N')+'.log')
function Invoke-HttpOwnedChild([string[]]$Arguments,[int]$Timeout=150){
 $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$env:ComSpec;$info.WorkingDirectory=$root;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
 $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8
 # 每个参数单独引用；上方已拒绝可触发CMD解释的路径字符，凭证从不进入此参数列表。
 # CMD按裸名称调用PATH中的批处理时，%~dp0可能退成工作目录；先固定实际可执行路径，避免npm寻找错误安装根。
 $localExecutable=Join-Path $root $Arguments[0]
 $resolvedExecutable=if(Test-Path -LiteralPath $localExecutable){(Resolve-Path -LiteralPath $localExecutable).Path}else{(Get-Command $Arguments[0] -CommandType Application -ErrorAction Stop).Source}
 $resolvedArguments=@($resolvedExecutable)+@($Arguments|Select-Object -Skip 1)
 $command=($resolvedArguments|ForEach-Object{'"'+$_+'"'}) -join ' '
 $info.Arguments='/d /s /c "'+$command+'"'
 $process=[Diagnostics.Process]::new();$process.StartInfo=$info;$timedOut=$false;$started=$false
 try{
  if(!$process.Start()){throw '子进程未启动'};$started=$true
  $out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync()
  if(!$process.WaitForExit($Timeout*1000)){$timedOut=$true;$process.Kill($true);$process.WaitForExit(10000)|Out-Null}
  if(![Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($out,$err),1000)){throw ('自有子进程管道未关闭，资源退出未知；诊断：'+$launchLog)}
  $text=$out.GetAwaiter().GetResult()+[Environment]::NewLine+$err.GetAwaiter().GetResult()
  foreach($secret in @($env:ZHKU_DB_PASSWORD,$env:DASHSCOPE_API_KEY,$env:POSTGRES_PASSWORD)){if(![string]::IsNullOrWhiteSpace($secret)){$text=$text.Replace($secret,'[REDACTED]')}}
  [IO.File]::AppendAllText($launchLog,$text,$utf8)
  if($timedOut-or $process.ExitCode -ne 0){throw ('操作失败或超时；已保留脱敏诊断：'+$launchLog)}
  return $text
 }finally{if($started -and !$process.HasExited){$process.Kill($true);$process.WaitForExit(10000)|Out-Null};$process.Dispose()}
}
function Invoke-HttpDocker([string[]]$Arguments){
 & $importer -RequireDatabase
 return Invoke-HttpOwnedChild -Arguments (@('docker.exe')+$Arguments) -Timeout 30
}
function Get-HttpMavenArguments([string]$Method){
 return @('mvnw.cmd','-o',('-Dmaven.repo.local='+$env:USERPROFILE.Replace('\','/')+'/.m2/repository'),'-pl','knowledge',('-Dtest=RuleAgentOnlineHttpHostTest#'+$Method),('-Dzhku.http.codeRoot='+$root),('-Dzhku.http.assetsRoot='+$AssetsRoot),('-Dzhku.http.studentDist='+$dist),('-Dzhku.http.packageDir='+$PackageDirectory))
}
try{
 # 准备与校包阶段撤去密钥；即使调用方原有值，也不能令只读准备越过外发边界。
 foreach($name in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','ZHKU_DB_URL')){Remove-Item -LiteralPath ('Env:'+ $name) -ErrorAction SilentlyContinue}
 if($Action -eq 'Prepare'){
  $env:VITE_ENTRY_MODE='student'
  $null=Invoke-HttpOwnedChild -Arguments @('npm.cmd','--prefix','agent/frontend','run','build','--','--outDir','dist-student') -Timeout 60
  $null=Invoke-HttpOwnedChild -Arguments @('mvnw.cmd','-o',('-Dmaven.repo.local='+$env:USERPROFILE.Replace('\','/')+'/.m2/repository'),'-pl','knowledge','-am','-DskipTests','package') -Timeout 180
  $result=Invoke-HttpOwnedChild -Arguments ((Get-HttpMavenArguments 'prepareRequestPackage')+@('-Dzhku.http.preparePackage=true','test')) -Timeout 90
  Write-Host ('已生成独立审包：'+$PackageDirectory)
  Get-Content -LiteralPath (Join-Path $PackageDirectory 'request-package.sha256')
  Write-Host '未获外发许可；数据库和模型调用均为0。'
  exit 0
 }
 $null=Invoke-HttpOwnedChild -Arguments ((Get-HttpMavenArguments 'checkRequestPackage')+@('-Dzhku.http.checkPackage=true',('-Dzhku.http.packageSha256='+$PackageSha256),'test')) -Timeout 90
 if($Action -eq 'CheckPackage'){Write-Host '包、源码、构建、学生页面与审核资料一致；零外发校验通过。';exit 0}
 # 此分支只供已取得本包明确人工许可后调用；旧额度和任何模拟成功都不能授权此分支。
 if([DateTime]::Now.Date -gt [DateTime]'2026-10-25'){throw '本包价格核对已过期，须重新核价及审包'}
 # 校包子进程撤去凭证后，获准执行仍须优先使用调用方原有Process配置，再重新检查用户/系统回退。
 foreach($name in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','ZHKU_DB_URL')){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
 & $importer -RequireDatabase -RequireDashScope
 $env:ZHKU_RULE_AGENT_MODE='ONLINE';$env:ZHKU_RULE_AGENT_AUTHORIZATION_ID=$PackageSha256
 New-Item -ItemType Directory -Path $authorizationDirectory -Force | Out-Null
 $env:ZHKU_HTTP_RESERVATION_TOKEN=[guid]::NewGuid().ToString('D')
 $record=[ordered]@{packageSha256=$PackageSha256;reservationId=$env:ZHKU_HTTP_RESERVATION_TOKEN;reservedAt=(Get-Date).ToString('o');status='RESERVED_NONREFUNDABLE';packageDirectory=$PackageDirectory}
 # 全候选按SHA原子占用，先于任何容器；失败和复制包都不能自动获得第二批额度。
 $stream=[IO.File]::Open($reservationFile,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
 try{$bytes=$utf8.GetBytes(($record|ConvertTo-Json));$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
 $container='zhku-http-browser-'+[guid]::NewGuid().ToString('N')
 $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
 if($port -eq 54329){throw '拒绝原演示库端口'}
 $env:POSTGRES_PASSWORD=$env:ZHKU_DB_PASSWORD;$containerAttempted=$true
 $null=Invoke-HttpDocker @('run','--rm','-d','--pull','never','--name',$container,'--publish',"127.0.0.1:${port}:5432",'--env','POSTGRES_PASSWORD','--env','POSTGRES_USER=zhkuagent','--env','POSTGRES_DB=zhkuagent_rulecheck_e2e','pgvector/pgvector:0.8.6-pg16-bookworm')
 $ready=$false;$until=[DateTime]::UtcNow.AddSeconds(45)
 while([DateTime]::UtcNow -lt $until){try{$null=Invoke-HttpDocker @('exec',$container,'pg_isready','-U','zhkuagent','-d','zhkuagent_rulecheck_e2e');$ready=$true;break}catch{Start-Sleep -Milliseconds 500}}
 if(!$ready){throw '新临时库未就绪，不运行在线宿主'}
 $env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:${port}/zhkuagent_rulecheck_e2e"
 & $importer -RequireDatabase -RequireDashScope
 $null=Invoke-HttpOwnedChild -Arguments ((Get-HttpMavenArguments 'approvedOnlineBrowserLease')+@('-Dzhku.http.liveLease=true',('-Dzhku.http.packageSha256='+$PackageSha256),('-Dzhku.db.url='+$env:ZHKU_DB_URL),'test')) -Timeout 1000
} catch { $failed=$true;throw } finally {
 $cleanupFailure=$false
 if($containerAttempted){
  try{$null=Invoke-HttpDocker @('rm','-f',$container);$remaining=Invoke-HttpDocker @('ps','-a','--filter',('name=^/'+$container+'$'),'--format','{{.Names}}');if(!$remaining.Trim().Equals('')){throw '自有临时容器仍存在'}}catch{$cleanupFailure=$true;Write-Warning ('自有临时库回收未确认：'+$container)}
 }
 foreach($name in $trackedEnv){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
 if($cleanupFailure -and !$failed){throw '业务已结束但自有资源未确认回收，不能视为成功'}
}