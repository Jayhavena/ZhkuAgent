#requires -Version 7.0
param(
 [string]$LauncherScript=(Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'knowledge/scripts/start-rule-agent-http-check.ps1'),
 [switch]$ProcessCredentialRegressionOnly,
 [switch]$FixtureWorker
)
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$LauncherScript=(Resolve-Path -LiteralPath $LauncherScript).Path
if(!$FixtureWorker){
 # 在清除敏感继承的独立子进程中测试；父进程不读取、导入或打印本机真实凭证。
 $info=[Diagnostics.ProcessStartInfo]::new((Get-Process -Id $PID).Path)
 $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 $info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8
 foreach($argument in @('-NoProfile','-File',$PSCommandPath,'-FixtureWorker','-LauncherScript',$LauncherScript)){[void]$info.ArgumentList.Add($argument)}
 if($ProcessCredentialRegressionOnly){[void]$info.ArgumentList.Add('-ProcessCredentialRegressionOnly')}
 foreach($name in @($info.Environment.Keys)){if($name -match '^(DASHSCOPE_|ZHKU_|POSTGRES_)'){[void]$info.Environment.Remove($name)}}
 $process=[Diagnostics.Process]::Start($info)
 $out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync()
 try{
  if(!$process.WaitForExit(45000)){$process.Kill($true);$process.WaitForExit(3000)|Out-Null;throw 'Synthetic launcher test worker timed out'}
  $stdout=$out.GetAwaiter().GetResult();$stderr=$err.GetAwaiter().GetResult()
  if($stdout){Write-Output $stdout.TrimEnd()};if($stderr){Write-Output 'Fixture worker emitted an unexpected diagnostic; no credential values displayed.'}
  exit $process.ExitCode
 }finally{$process.Dispose()}
}
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($LauncherScript,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Launcher parser errors'}
$source=[IO.File]::ReadAllText($LauncherScript)
$externalStub=@'
function Invoke-HttpOwnedChild([string[]]$Arguments,[int]$Timeout=150){
 $kind='other'
 if($Arguments[0] -eq 'docker.exe'){$kind='docker-'+$Arguments[1]}
 elseif($Arguments -match '#checkRequestPackage$'){$kind='check'}
 elseif($Arguments -match '#prepareRequestPackage$'){$kind='prepare-package'}
 elseif($Arguments -match '#approvedOnlineBrowserLease$'){$kind='approved-lease'}
 elseif($Arguments[0] -eq 'npm.cmd'){$kind='frontend-build'}
 elseif($Arguments -contains 'package'){$kind='java-build'}
 $script:BoundaryKinds.Add($kind)
 foreach($secretName in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD','POSTGRES_PASSWORD')){
  foreach($argument in $Arguments){if($argument.Contains($script:CallerValues[$secretName])){$script:SecretInArguments=$true}}
 }
 if($kind -eq 'check'){
  foreach($name in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','ZHKU_DB_URL')){if(![string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name,'Process'))){$script:CheckSawCredential=$true}}
  if($script:FailCheck){throw 'fixture package check failed'}
  if($script:RaceReservation){New-Item -ItemType Directory -Path (Split-Path $script:ReservationPath -Parent) -Force|Out-Null;[IO.File]::WriteAllText($script:ReservationPath,'PREEXISTING_RESERVATION')}
 }
 if($kind -eq 'prepare-package'){
  New-Item -ItemType Directory -Path $PackageDirectory -Force|Out-Null
  [IO.File]::WriteAllText((Join-Path $PackageDirectory 'request-package.json'),$script:FixturePackageBytes)
  [IO.File]::WriteAllText((Join-Path $PackageDirectory 'request-package.sha256'),(Get-FileHash -LiteralPath (Join-Path $PackageDirectory 'request-package.json')).Hash.ToLowerInvariant())
 }
 if($kind -eq 'docker-run'){
  $script:ReservationExistedBeforeDocker=Test-Path -LiteralPath $script:ReservationPath
  if($Arguments -match '^POSTGRES_PASSWORD='){$script:SecretInArguments=$true}
 }
 if($kind -eq 'approved-lease'){
  $script:ApprovedDbMatches=($env:ZHKU_DB_PASSWORD -eq $script:CallerValues.ZHKU_DB_PASSWORD)
  $script:ApprovedKeyMatches=($env:DASHSCOPE_API_KEY -eq $script:CallerValues.DASHSCOPE_API_KEY)
  $script:ApprovedToken=$env:ZHKU_HTTP_RESERVATION_TOKEN
  if($script:FailApproved){throw 'fixture approved lease failed'}
 }
 if($kind -eq 'docker-ps'){return ''}
 return 'FIXTURE_OK'
}
'@
$edits=[Collections.Generic.List[object]]::new()
foreach($statement in $ast.EndBlock.Statements){
 if($statement -is [Management.Automation.Language.AssignmentStatementAst] -and $statement.Left.Extent.Text -eq '$root'){$edits.Add(@{start=$statement.Extent.StartOffset;length=$statement.Extent.EndOffset-$statement.Extent.StartOffset;text='$root=$script:CurrentFixtureRoot'})}
 if($statement -is [Management.Automation.Language.FunctionDefinitionAst] -and $statement.Name -eq 'Invoke-HttpOwnedChild'){$edits.Add(@{start=$statement.Extent.StartOffset;length=$statement.Extent.EndOffset-$statement.Extent.StartOffset;text=$externalStub})}
}
# 只替换进程外部边界、代码根解析及最终控制权返回；所有准入、凭证清除/恢复、finally和原子文件操作照原脚本执行。
foreach($exitNode in $ast.FindAll({param($node)$node -is [Management.Automation.Language.ExitStatementAst]},$true)){
 $edits.Add(@{start=$exitNode.Extent.StartOffset;length=$exitNode.Extent.EndOffset-$exitNode.Extent.StartOffset;text='$script:ObservedExitCode=0;return'})
}
foreach($edit in ($edits|Sort-Object start -Descending)){$source=$source.Remove($edit.start,$edit.length).Insert($edit.start,$edit.text)}
$launcher=[scriptblock]::Create($source)
$fixtureRoot=Join-Path $env:TEMP ('zhku-http-launcher-contract-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixtureRoot|Out-Null
$script:Passed=0;$script:Failed=0
function Assert-Launcher([bool]$Condition,[string]$Reason){if(!$Condition){$script:FailureReason=$Reason;throw 'controlled assertion'}}
function Invoke-Case([string]$Name,[scriptblock]$Body){
 $script:FailureReason=$null
 try{& $Body 6>$null;$script:Passed++;Write-Host ('[PASS] '+$Name)}
 catch{$script:Failed++;if($script:FailureReason){Write-Host ('[FAIL] '+$Name+': '+$script:FailureReason)}else{Write-Host ('[FAIL] '+$Name+': unexpected '+$_.Exception.GetType().Name)}}
}
function New-Fixture([switch]$WithoutPackage){
 $script:CurrentFixtureRoot=Join-Path $fixtureRoot ([guid]::NewGuid().ToString('N'))
 foreach($relative in @('scripts','knowledge/scripts','agent/frontend/dist-student','fixture-assets')){New-Item -ItemType Directory -Path (Join-Path $script:CurrentFixtureRoot $relative) -Force|Out-Null}
 $credentialStub=@'
param([switch]$RequireDatabase,[switch]$RequireDashScope)
$names=@();if($RequireDatabase){$names+='ZHKU_DB_PASSWORD'};if($RequireDashScope){$names+='DASHSCOPE_API_KEY'}
foreach($name in $names){
 $value=[Environment]::GetEnvironmentVariable($name,'Process')
 if([string]::IsNullOrWhiteSpace($value)){
  foreach($scope in @('USER','MACHINE')){$value=[Environment]::GetEnvironmentVariable(('ZHKU_HTTP_CONTRACT_'+$scope+'_'+$name),'Process');if(![string]::IsNullOrWhiteSpace($value)){[Environment]::SetEnvironmentVariable($name,$value,'Process');break}}
 }
 if([string]::IsNullOrWhiteSpace($value)){throw 'fixture required credential unavailable'}
}
'@
 [IO.File]::WriteAllText((Join-Path $script:CurrentFixtureRoot 'scripts/Import-ZhkuCredentials.ps1'),$credentialStub,$utf8)
 $script:PackageDirectory=Join-Path $script:CurrentFixtureRoot 'docs/evidence/rule-agent-integration/2026-10-18/online-http-fixture'
 $script:FixturePackageBytes='{"batchId":"synthetic-'+[guid]::NewGuid().ToString('N')+'","fixture":true}'
 if(!$WithoutPackage){New-Item -ItemType Directory -Path $script:PackageDirectory -Force|Out-Null;[IO.File]::WriteAllText((Join-Path $script:PackageDirectory 'request-package.json'),$script:FixturePackageBytes);$script:PackageSha256=(Get-FileHash -LiteralPath (Join-Path $script:PackageDirectory 'request-package.json')).Hash.ToLowerInvariant()}
 else{$script:PackageSha256='0'*64}
 $script:ReservationPath=Join-Path $script:CurrentFixtureRoot ('docs/evidence/rule-agent-integration/authorization-reservations/'+$script:PackageSha256+'.json')
 $script:CallerValues=[ordered]@{DASHSCOPE_API_KEY='synthetic-caller-key';ZHKU_DB_PASSWORD='synthetic-caller-db';POSTGRES_PASSWORD='synthetic-caller-postgres';ZHKU_DB_URL='synthetic-caller-url';ZHKU_RULE_AGENT_MODE='synthetic-caller-mode';ZHKU_RULE_AGENT_AUTHORIZATION_ID='synthetic-caller-authorization';VITE_ENTRY_MODE='synthetic-caller-entry';ZHKU_HTTP_RESERVATION_TOKEN='synthetic-caller-reservation'}
 foreach($name in $script:CallerValues.Keys){[Environment]::SetEnvironmentVariable($name,$script:CallerValues[$name],'Process')}
 foreach($scope in @('USER','MACHINE')){foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY')){[Environment]::SetEnvironmentVariable(('ZHKU_HTTP_CONTRACT_'+$scope+'_'+$name),$null,'Process')}}
 $env:ZHKU_HTTP_APPROVED_PACKAGE_SHA256=$script:PackageSha256
 $script:BoundaryKinds=[Collections.Generic.List[string]]::new();$script:SecretInArguments=$false;$script:CheckSawCredential=$false;$script:FailCheck=$false;$script:FailApproved=$false;$script:RaceReservation=$false;$script:ReservationExistedBeforeDocker=$false;$script:ApprovedDbMatches=$false;$script:ApprovedKeyMatches=$false;$script:ApprovedToken=$null;$script:ObservedExitCode=$null
}
function Invoke-FixtureLauncher([string]$Action,[string]$Directory=$script:PackageDirectory,[string]$Sha=$script:PackageSha256){
 $caught=$null
 try{& $launcher -Action $Action -PackageDirectory $Directory -PackageSha256 $Sha -AssetsRoot (Join-Path $script:CurrentFixtureRoot 'fixture-assets') 3>$null 6>$null|Out-Null}catch{$caught=$_}
 $restored=$true;foreach($name in $script:CallerValues.Keys){if([Environment]::GetEnvironmentVariable($name,'Process') -ne $script:CallerValues[$name]){$restored=$false}}
 return [pscustomobject]@{threw=($null-ne $caught);restored=$restored;errorType=if($caught){$caught.Exception.GetType().Name}else{$null}}
}
try{
 Invoke-Case 'Process-only 凭证经零密钥校包后仍可启动获准阶段并还原调用方' {
  New-Fixture
  $result=Invoke-FixtureLauncher 'StartApproved'
  Assert-Launcher (!$result.threw -and $script:BoundaryKinds.Contains('approved-lease') -and $script:ApprovedDbMatches -and $script:ApprovedKeyMatches) 'Process-only 配置在校包后丢失'
  Assert-Launcher (!$script:CheckSawCredential -and $result.restored -and !$script:SecretInArguments) '校包泄露凭证或调用方环境未还原'
 }
 if(!$ProcessCredentialRegressionOnly){
  Invoke-Case '已有包 Prepare 在构建前拒绝并保留原字节' {New-Fixture;$before=(Get-FileHash -LiteralPath (Join-Path $script:PackageDirectory 'request-package.json')).Hash;$result=Invoke-FixtureLauncher 'Prepare';Assert-Launcher ($result.threw -and $script:BoundaryKinds.Count -eq 0 -and (Get-FileHash -LiteralPath (Join-Path $script:PackageDirectory 'request-package.json')).Hash -eq $before) '已有包被覆盖或先执行构建'}
  Invoke-Case '坏 SHA Check 在构建或数据库边界前拒绝' {New-Fixture;$result=Invoke-FixtureLauncher 'CheckPackage' -Sha ('0'*64);Assert-Launcher ($result.threw -and $script:BoundaryKinds.Count -eq 0) '坏SHA仍启动外部边界'}
  Invoke-Case '未匹配本包人工批准 Start 在数据库前拒绝' {New-Fixture;$env:ZHKU_HTTP_APPROVED_PACKAGE_SHA256='0'*64;$result=Invoke-FixtureLauncher 'StartApproved';Assert-Launcher ($result.threw -and $script:BoundaryKinds.Count -eq 0 -and !(Test-Path -LiteralPath $script:ReservationPath)) '未匹配许可仍执行或占用额度'}
  Invoke-Case 'Check 子进程看不到凭证且调用后全部环境还原' {New-Fixture;$result=Invoke-FixtureLauncher 'CheckPackage';Assert-Launcher (!$result.threw -and $script:BoundaryKinds.Count -eq 1 -and $script:BoundaryKinds[0] -eq 'check' -and !$script:CheckSawCredential -and $result.restored) 'Check访问凭证或污染调用方'}
  Invoke-Case '获准阶段 Process 优先于合成 User 与 Machine 回退' {
   New-Fixture
   foreach($scope in @('USER','MACHINE')){foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY')){[Environment]::SetEnvironmentVariable(('ZHKU_HTTP_CONTRACT_'+$scope+'_'+$name),('synthetic-'+$scope+'-'+$name),'Process')}}
   $result=Invoke-FixtureLauncher 'StartApproved'
   Assert-Launcher (!$result.threw -and $script:ApprovedDbMatches -and $script:ApprovedKeyMatches -and $result.restored) '回退值覆盖调用方Process配置'
  }
  Invoke-Case '校包失败不创建数据库或占用授权并还原环境' {New-Fixture;$script:FailCheck=$true;$result=Invoke-FixtureLauncher 'StartApproved';Assert-Launcher ($result.threw -and !$script:BoundaryKinds.Contains('docker-run') -and !(Test-Path -LiteralPath $script:ReservationPath) -and $result.restored) '校包失败仍越过准入'}
  Invoke-Case '批准阶段失败仍保留非退款 SHA 占用且再次启动被拒绝' {
   New-Fixture;$script:FailApproved=$true;$first=Invoke-FixtureLauncher 'StartApproved';$calls=$script:BoundaryKinds.Count
   $record=if(Test-Path -LiteralPath $script:ReservationPath){Get-Content -LiteralPath $script:ReservationPath -Raw|ConvertFrom-Json}else{$null}
   $second=Invoke-FixtureLauncher 'StartApproved'
   Assert-Launcher ($first.threw -and $first.restored -and $record.status -eq 'RESERVED_NONREFUNDABLE' -and $record.packageSha256 -eq $script:PackageSha256 -and $second.threw -and $script:BoundaryKinds.Count -eq $calls) '失败后退款或重新获得调用额度'
  }
  Invoke-Case '复制同 SHA 包到另一目录也不能再次占用' {
   New-Fixture;$first=Invoke-FixtureLauncher 'StartApproved';$calls=$script:BoundaryKinds.Count
   $copy=Join-Path (Split-Path $script:PackageDirectory -Parent) 'online-http-copied';New-Item -ItemType Directory -Path $copy|Out-Null;[IO.File]::WriteAllText((Join-Path $copy 'request-package.json'),$script:FixturePackageBytes)
   $second=Invoke-FixtureLauncher 'StartApproved' -Directory $copy
   Assert-Launcher (!$first.threw -and $second.threw -and $script:BoundaryKinds.Count -eq $calls -and $script:ReservationExistedBeforeDocker) '按目录重复消耗同一个包额度'
  }
  Invoke-Case '原子占用竞争在容器前失败且不覆盖赢家记录' {
   New-Fixture;$script:RaceReservation=$true;$result=Invoke-FixtureLauncher 'StartApproved'
   $bytes=if(Test-Path -LiteralPath $script:ReservationPath){[IO.File]::ReadAllText($script:ReservationPath)}else{''}
   Assert-Launcher ($result.threw -and !$script:BoundaryKinds.Contains('docker-run') -and $bytes -eq 'PREEXISTING_RESERVATION' -and $result.restored) '原子竞争覆盖记录或创建了容器'
  }
  Invoke-Case '新目录 Prepare 仅运行三项合成构建且全程无凭证' {
   New-Fixture -WithoutPackage;$result=Invoke-FixtureLauncher 'Prepare'
   Assert-Launcher (!$result.threw -and ($script:BoundaryKinds.ToArray() -join ',') -eq 'frontend-build,java-build,prepare-package' -and $result.restored -and !$script:SecretInArguments -and (Test-Path -LiteralPath (Join-Path $script:PackageDirectory 'request-package.sha256'))) '新包准备流程或边界错误'
  }
 }
}finally{
 $resolved=(Resolve-Path -LiteralPath $fixtureRoot).Path;$tempRoot=(Resolve-Path -LiteralPath $env:TEMP).Path.TrimEnd('\')+'\'
 if(!$resolved.StartsWith($tempRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Fixture cleanup boundary invalid'}
 Remove-Item -LiteralPath $resolved -Recurse -Force
}
Write-Host ('HTTP launcher contract: {0} passed, {1} failed; real DB/Docker/Maven/model calls: 0.' -f $script:Passed,$script:Failed)
if($script:Failed){exit 1}
exit 0
