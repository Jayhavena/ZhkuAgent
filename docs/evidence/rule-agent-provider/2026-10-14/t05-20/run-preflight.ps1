param([switch]$Build)
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8
[Console]::OutputEncoding=$utf8
$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$secrets=@()
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY','POSTGRES_PASSWORD')){
 foreach($scope in @('Process','User','Machine')){
  try{$value=[Environment]::GetEnvironmentVariable($name,$scope);if($value){$secrets+=$value}}catch{}
 }
 [Environment]::SetEnvironmentVariable($name,$null,'Process')
}
$sha='7bcb597074997edd9fc2ef22e5e85003df093e9eaf92b2cebc6d5a5d14a3f560'
$paths=@('knowledge/scripts/start-rule-agent-probe.ps1','knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineProbeHost.java','knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineFactoryTest.java')
$before=@($paths|ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash.ToLowerInvariant()}})
function Invoke-LocalCase([string]$Name,[string[]]$Options,[bool]$ExpectedSuccess,[int]$Seconds=20){
 $started=[DateTimeOffset]::Now
 $previous=[Environment]::CurrentDirectory
 try{
  [Environment]::CurrentDirectory=$root
  $result=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments (@('/d','/c','knowledge\scripts\start-rule-agent-probe.cmd')+$Options) -TimeoutSeconds $Seconds -Secrets $secrets
 }finally{[Environment]::CurrentDirectory=$previous}
 $passed=(!$result.timedOut-and $result.resourcesClosed-and (($result.exitCode-eq 0)-eq $ExpectedSuccess))
 $record=@{name=$Name;arguments=$Options;exitCode=$result.exitCode;timedOut=$result.timedOut;resourcesClosed=$result.resourcesClosed;expectedSuccess=$ExpectedSuccess;passed=$passed;startedAt=$started.ToString('o');finishedAt=[DateTimeOffset]::Now.ToString('o');supplierCalls=0;databaseOperations=0;output=$result.output;errorOutput=$result.errorOutput}
 [IO.File]::WriteAllText((Join-Path $PSScriptRoot "$Name.json"),($record|ConvertTo-Json -Depth 8),$utf8)
 if(!$passed){throw "Local preflight failed: $Name"}
 return $record
}
if($Build){
 $record=Invoke-LocalCase 'build' @('-BuildOnly') $true 150
 @{buildPassed=$record.passed;resourcesClosed=$record.resourcesClosed;source=$before;jarSha256=(Get-FileHash 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar').Hash.ToLowerInvariant();supplierCalls=0;databaseOperations=0}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'build-identity.json') -Encoding utf8
 exit 0
}
# 使用用户持久PATH验证CMD实际可用性，不能把Codex注入PATH当作普通用户环境。
$env:PATH=([Environment]::GetEnvironmentVariable('PATH','Machine')+';'+[Environment]::GetEnvironmentVariable('PATH','User'))
$checks=@()
$checks+=Invoke-LocalCase 'check-environment-user-path' @('-CheckEnvironment') $true
$checks+=Invoke-LocalCase 'check-package-user-path' @('-CheckPackage','-OnlyP01','-ApprovedPackageSha256',$sha) $true
$checks+=Invoke-LocalCase 'reject-missing-scope' @('-Probe','-ApprovedPackageSha256',$sha) $false
$checks+=Invoke-LocalCase 'reject-p02' @('-Probe','-OnlyP01','-IncludeClarification','-ApprovedPackageSha256',$sha) $false
$checks+=Invoke-LocalCase 'reject-old-hash' @('-CheckPackage','-OnlyP01','-ApprovedPackageSha256','ca44be57b868e4ebdb8f588fc6cde8f1332d092207d9d18ed38900abee697f61') $false
$checks+=Invoke-LocalCase 'reject-edited-hash' @('-CheckPackage','-OnlyP01','-ApprovedPackageSha256',('a'*64)) $false
$accepted=$checks[1].output|ConvertFrom-Json
if(!$accepted.passed-or $accepted.mode-ne 'PACKAGE_CHECK_ONLY'-or $accepted.maximumProviderCalls-ne 4-or $accepted.providerCalls-ne 0-or $accepted.databaseOperations-ne 0-or $accepted.packageSha256-ne $sha){throw 'Package check did not prove zero-call P01 scope'}
if(!$checks[2].errorOutput.Contains('必须明确OnlyP01')){throw 'Missing scope rejected for wrong reason'}
if(!$checks[3].errorOutput.Contains('本批只允许P01')){throw 'P02 rejected for wrong reason'}
foreach($index in @(4,5)){if(!$checks[$index].errorOutput.Contains('待核对请求包指纹不同')){throw 'Hash rejected for wrong reason'}}
foreach($item in $before){if((Get-FileHash -LiteralPath $item.path).Hash.ToLowerInvariant()-ne $item.sha256){throw 'Source changed during preflight'}}
$package=Get-FileHash 'docs/evidence/rule-agent-provider/2026-10-14/t05-19/request-package.json'
if($package.Hash.ToLowerInvariant()-ne $sha){throw 'Frozen package changed'}
$summary=@{passed=$true;caseCount=$checks.Count;sourceDrift=0;resourcesClosed=$true;usedPersistentUserMachinePath=$true;packageSha256=$sha;jarSha256=(Get-FileHash 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar').Hash.ToLowerInvariant();providerCalls=0;databaseOperations=0;source=$before;authorizationGranted=$false;t05Complete=$false}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'preflight-result.json'),($summary|ConvertTo-Json -Depth 7),$utf8)
$summary|ConvertTo-Json -Depth 7
