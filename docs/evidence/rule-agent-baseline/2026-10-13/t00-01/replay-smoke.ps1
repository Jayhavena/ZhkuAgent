# T00只重验旧回放的启动/原文/退出，绝不调用供应商或写原库。
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$secrets=@();$credentialAvailability=@{}
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY')){
    $scopes=@{}
    foreach($scope in @('Process','User','Machine')){
        $value=[Environment]::GetEnvironmentVariable($name,$scope)
        $scopes[$scope]=![string]::IsNullOrWhiteSpace($value)
        if($value){$secrets+=$value}
    }
    $credentialAvailability[$name]=$scopes
}
# 每次数据库操作的再次检查由既有启动器/Import-ZhkuCredentials执行。
. (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
$env:DASHSCOPE_API_KEY=$null
$machinePath=[Environment]::GetEnvironmentVariable('Path','Machine')
$userPath=[Environment]::GetEnvironmentVariable('Path','User')
if(!$machinePath){throw 'Cannot reconstruct ordinary Windows terminal PATH'}
$env:Path=[Environment]::ExpandEnvironmentVariables($machinePath+';'+$userPath)
$processInfo=[Diagnostics.ProcessStartInfo]::new((Join-Path $PSHOME 'pwsh.exe'))
$processInfo.UseShellExecute=$false;$processInfo.CreateNoWindow=$true;$processInfo.WorkingDirectory=$root
$processInfo.RedirectStandardOutput=$true;$processInfo.RedirectStandardError=$true
$processInfo.StandardOutputEncoding=$utf8;$processInfo.StandardErrorEncoding=$utf8
foreach($arg in @('-NoProfile','-File',(Join-Path $root 'knowledge/scripts/start-controlled-rule-check.ps1'),'-TaskPlanReplay','-Smoke','-MaxMinutes','2','-DatabasePort','55513','-BackendPort','58133')){[void]$processInfo.ArgumentList.Add($arg)}
$started=[DateTimeOffset]::Now
$child=[Diagnostics.Process]::Start($processInfo)
$stdout=$child.StandardOutput.ReadToEndAsync();$stderr=$child.StandardError.ReadToEndAsync()
$timedOut=$false
try{
    # 留出既有启动器150秒构建、数据库就绪与两次宿主退出的总预算。
    if(!$child.WaitForExit(270000)){$timedOut=$true;$child.Kill($true);[void]$child.WaitForExit(5000)}
    $output=$stdout.GetAwaiter().GetResult()+"`n"+$stderr.GetAwaiter().GetResult()
    foreach($secret in $secrets){$output=$output.Replace($secret,'[REDACTED]')}
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'replay-smoke.log'),$output,$utf8)
    $record=[ordered]@{started=$started.ToString('o');finished=[DateTimeOffset]::Now.ToString('o');exitCode=$child.ExitCode;timedOut=$timedOut;pathSource='WINDOWS_MACHINE_PLUS_USER';credentials=$credentialAvailability;database='jdbc:postgresql://127.0.0.1:55513/zhkuagent_rulecheck_e2e';backendPort=58133;mode='TASK_PLAN_REPLAY_NOT_LIVE';providerCalls=0;timeoutSeconds=270;sourceHead=(git -C $root rev-parse HEAD)}
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'replay-smoke.json'),($record|ConvertTo-Json -Depth 8),$utf8)
    $record|ConvertTo-Json -Depth 8 -Compress
    $output -split "`r?`n"|Select-Object -Last 12
    if($child.ExitCode-ne 0-or $timedOut){exit 1}
}finally{$child.Dispose()}
