$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8
[Console]::OutputEncoding=$utf8
$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$expected='7bcb597074997edd9fc2ef22e5e85003df093e9eaf92b2cebc6d5a5d14a3f560'
$jarSha='d9559bace280d41f7647933df6808ead00f2f7b21c189761adf682ffd6388b83'
$receipt=Join-Path $PSScriptRoot 'authorization-and-preflight.json'
if(Test-Path -LiteralPath $receipt){throw '本批只运行一次；禁止重试或复用额度'}
if((git rev-parse HEAD)-ne '53616cc1636301b314fb1247c7d2a899ba4025b6'){throw '源码检查点改变，不执行'}
if(git -c core.excludesfile= diff --name-only -- knowledge/src knowledge/scripts agent-runtime/src scripts){throw '运行源码已漂移，不执行'}
if((Get-FileHash 'docs/evidence/rule-agent-provider/2026-10-14/t05-19/request-package.json').Hash.ToLowerInvariant()-ne $expected){throw '已授权包改变'}
if((Get-FileHash 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar').Hash.ToLowerInvariant()-ne $jarSha){throw '已验收jar改变'}
& (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase -RequireDashScope
$secrets=@($env:ZHKU_DB_PASSWORD,$env:DASHSCOPE_API_KEY)
$ready=Invoke-ZhkuOwnedProcess -Executable (Get-Command docker.exe).Source -Arguments @('version','--format','{{.Server.Version}}') -TimeoutSeconds 10 -Secrets $secrets
if($ready.exitCode-ne 0-or $ready.timedOut-or !$ready.resourcesClosed){throw 'Docker不可用，不调用供应商'}
$port=55522
if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw '专用临时端口已占用，不执行'}
$start=[DateTimeOffset]::Now
$record=@{
 authorization='DIRECT_USER_MESSAGE:call_dd93875e53e3456c89bb8b169e48f195'
 answer='明确允许上述新包最多4次外发'
 packageSha256=$expected
 jarSha256=$jarSha
 sourceHead=(git rev-parse HEAD)
 maximumProviderCalls=4
 caseSelection='P01_READ_REFERENCE_03'
 automaticallyRetry=$false
 oldRemaining10Reusable=$false
 databasePort=$port
 databaseCredentialAvailable=$true
 supplierCredentialAvailable=$true
 dockerAvailable=$true
 startedAt=$start.ToString('o')
}
[IO.File]::WriteAllText($receipt,($record|ConvertTo-Json -Depth 5),$utf8)
$base=Split-Path $PSScriptRoot -Parent
$existing=@(Get-ChildItem -LiteralPath $base -Directory -Filter 't05-live-*'|ForEach-Object Name)
# 凭证仅注入子进程；固定包、当前源码及一批4次额度，不继承旧批次剩余次数。
& (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase -RequireDashScope
$result=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','knowledge\scripts\start-rule-agent-probe.cmd','-Probe','-OnlyP01','-ApprovedPackageSha256',$expected,'-DatabasePort',"$port") -TimeoutSeconds 150 -Secrets $secrets
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'approved-launcher.log'),$result.output+"`n"+$result.errorOutput,$utf8)
$created=@(Get-ChildItem -LiteralPath $base -Directory -Filter 't05-live-*'|Where-Object Name -notin $existing|ForEach-Object Name)
$finish=@{exitCode=$result.exitCode;timedOut=$result.timedOut;launcherResourcesClosed=$result.resourcesClosed;startedAt=$start.ToString('o');finishedAt=[DateTimeOffset]::Now.ToString('o');evidenceDirectories=$created;packageSha256=$expected;retryPerformed=$false;maximumProviderCalls=4}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'approved-launcher-result.json'),($finish|ConvertTo-Json -Depth 5),$utf8)
$finish|ConvertTo-Json -Depth 5 -Compress
if($result.exitCode-ne 0-or $result.timedOut-or !$result.resourcesClosed){exit 1}
