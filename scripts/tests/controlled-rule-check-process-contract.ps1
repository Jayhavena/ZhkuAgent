param([string]$ReportPath)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$results=@();$failure=$null
try{
    . (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
    $exe=Join-Path $PSHOME 'pwsh.exe';if(!(Test-Path $exe)){throw '需要实际PowerShell7文件'}
    $normal=Invoke-ZhkuOwnedProcess -Executable $exe -Arguments @('-NoProfile','-Command','[Console]::Out.WriteLine("ok");[Console]::Error.WriteLine("synthetic-secret");exit 3') -TimeoutSeconds 5 -Secrets @('synthetic-secret')
    if($normal.exitCode-ne 3-or $normal.timedOut-or !$normal.resourcesClosed-or $normal.output-notmatch 'ok'-or $normal.errorOutput-match 'synthetic-secret'){throw '正常退出/失败码/脱敏合同不符'}
    $results+='normal-exit-and-redaction'
    $watch=[Diagnostics.Stopwatch]::StartNew()
    $hanging=Invoke-ZhkuOwnedProcess -Executable $exe -Arguments @('-NoProfile','-Command','Start-Sleep -Seconds 20') -TimeoutSeconds 1
    $watch.Stop()
    if(!$hanging.timedOut-or !$hanging.resourcesClosed-or $hanging.exitCode-eq 0-or $watch.Elapsed.TotalSeconds-gt 5){throw '挂起命令未在预算内停止自身进程'}
    $results+='hanging-process-bounded-cleanup'
    $info=[Diagnostics.ProcessStartInfo]::new($exe);$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    foreach($argument in @('-NoProfile','-Command','Start-Sleep -Seconds 20')){[void]$info.ArgumentList.Add($argument)}
    $other=[Diagnostics.Process]::Start($info)
    try{
        $own=Invoke-ZhkuOwnedProcess -Executable $exe -Arguments @('-NoProfile','-Command','Start-Sleep -Seconds 20') -TimeoutSeconds 1
        if($other.HasExited-or !$own.timedOut){throw '执行器误终止其他进程'};$results+='other-process-preserved'
    }finally{if(!$other.HasExited){$other.Kill($true);[void]$other.WaitForExit(2000)};$other.Dispose()}
}catch{$failure=$_.Exception.Message}
$report=@{passed=($null-eq $failure);contracts=$results;failure=$failure;databaseOperations=0;providerCalls=0}
if($ReportPath){[IO.File]::WriteAllText($ReportPath,($report|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))}
$report|ConvertTo-Json -Depth 5
if($failure){exit 1}
