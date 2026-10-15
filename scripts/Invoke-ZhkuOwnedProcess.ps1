# P02两run共用进程可显式申请235秒；默认5秒及各调用方原预算不变。
function Invoke-ZhkuOwnedProcess {
    param([Parameter(Mandatory)][string]$Executable,[string[]]$Arguments=@(),
        [ValidateRange(1,250)][int]$TimeoutSeconds=5,[string[]]$Secrets=@())
    $info=[Diagnostics.ProcessStartInfo]::new($Executable)
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
    foreach($argument in $Arguments){[void]$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::Start($info)
    $output=$process.StandardOutput.ReadToEndAsync();$errorOutput=$process.StandardError.ReadToEndAsync()
    $timedOut=$false;$closed=$false;$exitCode=$null;$standardOutput='';$standardError=''
    try{
        if(!$process.WaitForExit($TimeoutSeconds*1000)){
            $timedOut=$true
            # 只终止本函数创建的进程树；不能按程序名清理用户客户端或别人的任务。
            try{$process.Kill($true)}catch{}
            $closed=$process.WaitForExit(2000)
        }else{$closed=$true}
        if($closed){
            $exitCode=$process.ExitCode
            # 子孙进程若仍持有管道，读取同样不能无限等待；状态未确认不能记作资源退出。
            if([Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($output,$errorOutput),1000)){
                $standardOutput=$output.GetAwaiter().GetResult();$standardError=$errorOutput.GetAwaiter().GetResult()
            }else{$closed=$false}
        }
        foreach($secretValue in $Secrets){if($secretValue){$standardOutput=$standardOutput.Replace($secretValue,'[REDACTED]');$standardError=$standardError.Replace($secretValue,'[REDACTED]')}}
        [pscustomobject]@{exitCode=$exitCode;timedOut=$timedOut;resourcesClosed=$closed;output=$standardOutput;errorOutput=$standardError}
    }finally{$process.Dispose()}
}
