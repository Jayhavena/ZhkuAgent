#requires -Version 7.0
param([Parameter(Mandatory)][string]$EvidenceRoot)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Safety.ps1"
$dir=Join-Path $EvidenceRoot ('supervisor-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($dir)
Initialize-Safety $dir 30
$script:Secrets+=@('probe\"secret&line'+"`n"+'value')
$encoded=@{failure=$script:Secrets[-1]}|ConvertTo-Json
$protected=Protect-Text $encoded
if($protected -eq $encoded -or !(($protected|ConvertFrom-Json).failure -eq '[REDACTED]')){throw 'JSON-escaped secret not redacted'}
$script:Secrets+=@('probe-AA','probe-BB')
if((Protect-Text 'probe-AA probe-BB') -ne '[REDACTED] [REDACTED]'){throw 'equal-length secrets not all redacted'}
$results=@()
try {
    $ok=Invoke-Owned $PSHOME/pwsh.exe @('-NoProfile','-Command','Write-Output "probe-success"; exit 0') 'success' 8
    if($ok.exitCode -ne 0 -or $ok.timedOut){throw 'success exit status lost'}
    $bad=Invoke-Owned $PSHOME/pwsh.exe @('-NoProfile','-Command','Write-Output "probe-failure"; exit 23') 'failure' 8
    if($bad.exitCode -ne 23 -or $bad.timedOut){throw 'failure exit status lost'}
    $flood=Invoke-Owned $PSHOME/pwsh.exe @('-NoProfile','-Command','1..3000 | ForEach-Object { Write-Output "log-$_" }; exit 0') 'high-output' 8
    if($flood.exitCode -ne 0 -or $flood.output -notmatch 'log-3000'){throw 'high output lost or starved deadline'}
    $rejected=$false
    try{Invoke-Owned $PSHOME/pwsh.exe @('-NoProfile','-Command',$script:Secrets[-1]) 'secret-argument' 8|Out-Null}catch{if($_.ToString() -match 'Sensitive command argument rejected'){$rejected=$true}else{throw}}
    if(!$rejected){throw 'special-character secret allowed in argument'}
    $child=Join-Path $dir 'child.ps1'
    [IO.File]::WriteAllText($child,'param($pidFile); [IO.File]::WriteAllText($pidFile,[string]$PID); Start-Sleep -Seconds 60')
    $pidFile=Join-Path $dir 'child.pid'
    $spawn=Join-Path $dir 'spawn.ps1'
    [IO.File]::WriteAllText($spawn,'param($exe,$scriptFile,$pidFile); Start-Process $exe -ArgumentList @("-NoProfile","-File",$scriptFile,$pidFile) -WindowStyle Hidden | Out-Null; Start-Sleep -Seconds 60')
    $timeout=Invoke-Owned $PSHOME/pwsh.exe @('-NoProfile','-File',$spawn,(Join-Path $PSHOME 'pwsh.exe'),$child,$pidFile) 'timeout' 6
    if(!$timeout.timedOut -or $timeout.exitCode -ne 124){throw 'timeout not identified'}
    if(!(Test-Path $pidFile)){throw 'descendant probe did not start'}
    $childPid=[int](Get-Content $pidFile)
    if(Get-Process -Id $childPid -ErrorAction SilentlyContinue){throw 'owned descendant survived timeout'}
    $results=@($ok,$bad,$flood,$timeout)
    Save-Json 'supervisor-test.json' @{passed=$true;cases=$results;jsonSecretRedacted=$true;secretArgumentRejected=$rejected;databaseOperations=0;providerCalls=0}
} finally { Close-Safety }
Write-Output $dir
