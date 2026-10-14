param([switch]$ResumeBeforeDispatch)
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8
[Console]::OutputEncoding=$utf8
$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$expected='ca44be57b868e4ebdb8f588fc6cde8f1332d092207d9d18ed38900abee697f61'
$receipt=Join-Path $PSScriptRoot 'authorization-and-preflight.json'
if(Test-Path -LiteralPath $receipt){
    if(!$ResumeBeforeDispatch-or !(Test-Path -LiteralPath (Join-Path $PSScriptRoot 'launcher-parameter-error.json'))-or (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'approved-launcher-result.json'))-or (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'authorization-and-preflight-resumed.json'))){throw '本批不可重复执行'}
    # 唯一允许恢复的是已证实未进入函数体的参数拒绝，不覆盖原记录或补测供应商。
    $receipt=Join-Path $PSScriptRoot 'authorization-and-preflight-resumed.json'
}
if((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'request-package.json')).Hash.ToLowerInvariant()-ne $expected){throw '批准的包身份变化'}
$jar=Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar'
if((Get-FileHash -LiteralPath $jar).Hash.ToLowerInvariant()-ne '9b54ba0b81a8e06752e0d15f1967a136b8061055302594afbd9792e05d51abe6'){throw '已验收jar身份变化'}
& (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase -RequireDashScope
$secrets=@($env:ZHKU_DB_PASSWORD,$env:DASHSCOPE_API_KEY)
$ready=Invoke-ZhkuOwnedProcess -Executable (Get-Command docker.exe).Source -Arguments @('version','--format','{{.Server.Version}}') -TimeoutSeconds 10 -Secrets $secrets
if($ready.exitCode-ne 0-or $ready.timedOut-or !$ready.resourcesClosed){throw 'Docker当前不可用，不调用供应商'}
$start=[DateTimeOffset]::Now
$record=[ordered]@{
 authorization='DIRECT_USER_MESSAGE:call_0b75ea7bcd2c476082647541154a0cec'
 answer='明确允许上述本批最多12次外发'
 packageSha256=$expected
 sourceHead=(git rev-parse HEAD)
 jarSha256='9b54ba0b81a8e06752e0d15f1967a136b8061055302594afbd9792e05d51abe6'
 maximumProviderCalls=12
 automaticallyRetry=$false
 startedAt=$start.ToString('o')
 databasePort=55521
 databaseCredentialAvailable=$true
 supplierCredentialAvailable=$true
 dockerAvailable=$true
 secretsPrinted=$false
}
[IO.File]::WriteAllText($receipt,($record|ConvertTo-Json -Depth 5),$utf8)
$existing=@(Get-ChildItem -LiteralPath (Split-Path $PSScriptRoot -Parent) -Directory -Filter 't05-live-*'|ForEach-Object Name)
$result=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','knowledge\scripts\start-rule-agent-probe.cmd','-Probe','-IncludeClarification','-ApprovedPackageSha256',$expected,'-DatabasePort','55521') -TimeoutSeconds 150 -Secrets $secrets
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'approved-launcher.log'),$result.output+"`n"+$result.errorOutput,$utf8)
$created=@(Get-ChildItem -LiteralPath (Split-Path $PSScriptRoot -Parent) -Directory -Filter 't05-live-*'|Where-Object Name -notin $existing|ForEach-Object Name)
$finish=[ordered]@{exitCode=$result.exitCode;timedOut=$result.timedOut;launcherResourcesClosed=$result.resourcesClosed;startedAt=$start.ToString('o');finishedAt=[DateTimeOffset]::Now.ToString('o');evidenceDirectories=$created;packageSha256=$expected;retryPerformed=$false}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'approved-launcher-result.json'),($finish|ConvertTo-Json -Depth 5),$utf8)
$finish|ConvertTo-Json -Depth 5 -Compress
($result.output+"`n"+$result.errorOutput)-split "`r?`n"|Select-Object -Last 8
if($result.exitCode-ne 0-or $result.timedOut-or !$result.resourcesClosed){exit 1}
