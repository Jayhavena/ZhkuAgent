param([switch]$ActualHttp,[switch]$CollectExistingHttp)
$ErrorActionPreference='Stop'
$checkRoot='D:/alldesk/Projects/ZhkuAgent'
$checkOutput=Join-Path $checkRoot 'docs/evidence/rule-check-refactor/2026-10-07/r5-engineering-closeout-20261008-01'
Set-Location -LiteralPath $checkRoot
# Rebuild ordinary user PATH, excluding the executor's injected tools.
$env:PATH=[Environment]::GetEnvironmentVariable('PATH','Machine')+';'+[Environment]::GetEnvironmentVariable('PATH','User')
$checkUtf8=New-Object Text.UTF8Encoding($false)
[Console]::InputEncoding=$checkUtf8;[Console]::OutputEncoding=$checkUtf8;$OutputEncoding=$checkUtf8
$cmd=Join-Path $env:SystemRoot 'System32/cmd.exe'
$manual=& $cmd /d /c knowledge\scripts\start-controlled-manual-demo.cmd -CheckEnvironment
if($LASTEXITCODE){throw 'Manual launcher failed'}
$m=($manual -join "`n")|ConvertFrom-Json
if($m.consoleInputCodePage -ne 65001 -or $m.consoleOutputCodePage -ne 65001 -or $m.pipelineOutputCodePage -ne 65001){throw 'UTF8 boundary not preserved'}
if(!$m.javaAvailable -or !$m.dockerAvailable -or !$m.mavenAvailable){throw 'Ordinary terminal dependency missing'}
if($m.encodingSample.Contains([char]0xfffd)-or [int]$m.encodingSample[0] -ne 0x4e2d){throw 'Chinese output corrupted'}
if($m.databaseOperations -ne 0 -or $m.providerCalls -ne 0){throw 'Environment probe performed external work'}
$verify=& $cmd /d /c knowledge\scripts\verify-rule-check-refactor.cmd -CheckEnvironment -Mode Database -Port 55470 -Phase terminal-only-20261008 -Tests RuleCheckLegacyBrowserHttpTest
if($LASTEXITCODE){throw 'Verification launcher failed'}
$v=($verify -join "`n")|ConvertFrom-Json
if($v.consoleCodePage -ne 65001 -or !$v.mavenWrapperAvailable -or !$v.javaAvailable -or !$v.dockerAvailable -or $v.externalStarts -ne 0){throw 'Verification ordinary terminal failed'}
if($v.encodingSample.Contains([char]0xfffd)-or [int]$v.encodingSample[0] -ne 0x4e2d){throw 'Verification Chinese corrupted'}
$actual=$null
if($ActualHttp -and $CollectExistingHttp){throw 'Choose run or collect, not both'}
if($ActualHttp -or $CollectExistingHttp) {
    if($ActualHttp) {
    $checkTests='RuleCheckBrowserHttpTest,RuleCheckLegacyBrowserHttpTest'
    & $cmd /d /c knowledge\scripts\verify-rule-check-refactor.cmd -Mode Database -Port 55470 -Phase r5-terminal-http-20261008-01 -Tests $checkTests
    if($LASTEXITCODE){throw 'Ordinary terminal actual HTTP verification failed'}
    }
    # Windows PowerShell 5.1 file decoding is independent of Console encoding.
    $actual=Get-Content -Encoding UTF8 -LiteralPath (Join-Path $checkRoot 'docs/evidence/rule-check-refactor/2026-10-07/r5-terminal-http-20261008-01/results.json') -Raw | ConvertFrom-Json
    if(!$actual.passed -or !$actual.resourcesVerified -or $actual.sourceDrift -ne 0 -or $actual.testCount -ne 3){throw 'Actual HTTP evidence incomplete'}
}
$report=[ordered]@{passed=$true;parentPowerShell=$PSVersionTable.PSVersion.ToString();ordinaryUserPath=$true;
 pwshOnOrdinaryPath=($null -ne (Get-Command pwsh.exe -ErrorAction SilentlyContinue));utf8SessionOnly=$true;
 manual=$m;verification=$v;actualHttp=$actual;actualHttpRunThisInvocation=[bool]$ActualHttp;
 collectedExistingHttp=[bool]$CollectExistingHttp;providerCalls=0;globalSettingsChanged=$false}
$reportName=if($ActualHttp -or $CollectExistingHttp){'ordinary-terminal-http.json'}else{'ordinary-terminal.json'}
[IO.File]::WriteAllText((Join-Path $checkOutput $reportName),($report|ConvertTo-Json -Depth 8),$checkUtf8)
Write-Output 'Ordinary Windows PowerShell terminal and UTF8 wrapper verification passed.'
