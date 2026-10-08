param()
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $taskRoot
$env:PATH=[Environment]::GetEnvironmentVariable('PATH','Machine')+';'+[Environment]::GetEnvironmentVariable('PATH','User')
$taskUtf8=New-Object Text.UTF8Encoding($false)
[Console]::InputEncoding=$taskUtf8;[Console]::OutputEncoding=$taskUtf8;$OutputEncoding=$taskUtf8
$taskCmd=Join-Path $env:SystemRoot 'System32/cmd.exe'
$taskManual=& $taskCmd /d /c knowledge\scripts\start-controlled-manual-demo.cmd -CheckEnvironment
if($LASTEXITCODE){throw 'Manual environment check failed'}
$taskManualReport=($taskManual -join "`n")|ConvertFrom-Json
if($taskManualReport.databaseOperations -ne 0 -or $taskManualReport.providerCalls -ne 0){throw 'Unexpected external operation'}
if($taskManualReport.consoleInputCodePage -ne 65001 -or $taskManualReport.consoleOutputCodePage -ne 65001 -or $taskManualReport.pipelineOutputCodePage -ne 65001){throw 'UTF8 boundary missing'}
if([int]$taskManualReport.encodingSample[0] -ne 0x4e2d){throw 'Chinese environment output corrupted'}
$taskWeb=& $taskCmd /d /c knowledge\scripts\start-controlled-web-demo.cmd -CheckEnvironment
if($LASTEXITCODE){throw 'Web environment check failed'}
$taskWebReport=($taskWeb -join "`n")|ConvertFrom-Json
if($taskWebReport.databaseOperations -ne 0 -or $taskWebReport.providerCalls -ne 0 -or $taskWebReport.encoding -ne 65001){throw 'Web environment boundary incorrect'}
$taskReport=[ordered]@{passed=$true;powerShell=$PSVersionTable.PSVersion.ToString();ordinaryPath=$true;pwshOnPath=($null -ne (Get-Command pwsh.exe -ErrorAction SilentlyContinue));manual=$taskManualReport;web=$taskWebReport;actualDatabaseOperations=0;providerCalls=0}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'ordinary-terminal.json'),($taskReport|ConvertTo-Json -Depth 8),$taskUtf8)
Write-Output 'Ordinary terminal environment checks passed; no database or provider operations.'
