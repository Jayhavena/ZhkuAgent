#requires -Version 7.0
# 独立门闩保证子进程在归入 Job 前不能创建 Maven/JVM；不能用先启动再追踪 PID 的竞态方案。
if(-not ('ZhkuOwnedJob' -as [type])) {
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public sealed class ZhkuOwnedJob : IDisposable {
 [StructLayout(LayoutKind.Sequential)] struct Basic { public long a,b; public uint flags; public UIntPtr c,d; public uint e; public UIntPtr f; public uint g,h; }
 [StructLayout(LayoutKind.Sequential)] struct IO { public ulong a,b,c,d,e,f; }
 [StructLayout(LayoutKind.Sequential)] struct Extended { public Basic basic; public IO io; public UIntPtr a,b,c,d; }
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode)] static extern IntPtr CreateJobObject(IntPtr a,string b);
 [DllImport("kernel32.dll",SetLastError=true)] static extern bool SetInformationJobObject(IntPtr a,int b,IntPtr c,uint d);
 [DllImport("kernel32.dll",SetLastError=true)] static extern bool AssignProcessToJobObject(IntPtr a,IntPtr b);
 [DllImport("kernel32.dll")] static extern bool TerminateJobObject(IntPtr a,uint b);
 [DllImport("kernel32.dll")] static extern bool QueryInformationJobObject(IntPtr a,int b,IntPtr c,uint d,IntPtr e);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr a);
 IntPtr handle;
 public ZhkuOwnedJob() {
  handle=CreateJobObject(IntPtr.Zero,null); if(handle==IntPtr.Zero)throw new Exception("CreateJobObject failed");
  var info=new Extended(); info.basic.flags=0x2000;
  int n=Marshal.SizeOf(info); var p=Marshal.AllocHGlobal(n);
  try { Marshal.StructureToPtr(info,p,false); if(!SetInformationJobObject(handle,9,p,(uint)n))throw new Exception("SetInformationJobObject failed"); }
  finally { Marshal.FreeHGlobal(p); }
 }
 public void Assign(IntPtr process) { if(!AssignProcessToJobObject(handle,process))throw new Exception("AssignProcessToJobObject failed: "+Marshal.GetLastWin32Error()); }
 public int[] Pids() {
  var p=Marshal.AllocHGlobal(65536);
  try { if(!QueryInformationJobObject(handle,3,p,65536,IntPtr.Zero))throw new Exception("QueryInformationJobObject failed");
   int n=Marshal.ReadInt32(p,4); var ids=new int[n]; for(int i=0;i<n;i++)ids[i]=(int)Marshal.ReadIntPtr(p,8+i*IntPtr.Size); return ids;
  } finally { Marshal.FreeHGlobal(p); }
 }
 public void Kill() { if(!TerminateJobObject(handle,124))throw new Exception("TerminateJobObject failed"); }
 public void Dispose() { if(handle!=IntPtr.Zero) { CloseHandle(handle); handle=IntPtr.Zero; } }
}
'@
}
function Initialize-Safety([string]$Directory,[int]$Seconds) {
    $script:SafetyDir=$Directory
    $script:SafetyClock=[Diagnostics.Stopwatch]::StartNew()
    $script:SafetyLimit=$Seconds
    $script:CleanupMode=$false
    $script:Sequence=0
    $script:Secrets=@()
    # 只在内存保存脱敏词；不会把环境变量清单或 Docker inspect 的 Env 写入证据。
    foreach($scope in @('Process','User','Machine')) {
        foreach($entry in [Environment]::GetEnvironmentVariables($scope).GetEnumerator()) {
            if($entry.Key -match '(?i)password|api.?key|token|secret' -and ([string]$entry.Value).Length -ge 4){$script:Secrets+=([string]$entry.Value)}
        }
    }
}
function Protect-Text([string]$Text) {
    foreach($secret in ($script:Secrets|Select-Object -Unique|Sort-Object Length -Descending)){
        # JSON/XML 会转义口令中的引号、反斜杠或换行；只替换原字面量会漏掉证据里的编码值。
        $json=ConvertTo-Json -InputObject $secret -Compress
        $Text=$Text.Replace($json.Substring(1,$json.Length-2),'[REDACTED]')
        $Text=$Text.Replace([Security.SecurityElement]::Escape($secret),'[REDACTED]')
        $Text=$Text.Replace($secret,'[REDACTED]')
    }
    return $Text
}
function Save-Json([string]$Name,$Value) {
    [IO.File]::WriteAllText((Join-Path $script:SafetyDir $Name),(Protect-Text ($Value|ConvertTo-Json -Depth 30)),[Text.UTF8Encoding]::new($false))
}
function Assert-Budget {
    if($script:SafetyClock.Elapsed.TotalSeconds -ge $script:SafetyLimit){throw 'ROUND_TIMEOUT'}
    if(!$script:CleanupMode -and (Test-Path (Join-Path $script:SafetyDir 'STOP'))){throw 'ROUND_STOP_REQUESTED'}
}
function Invoke-Owned([string]$Exe,[string[]]$Arguments,[string]$Name,[int]$Seconds=120) {
    Assert-Budget
    $script:Sequence++
    $prefix=('{0:D3}-{1}' -f $script:Sequence,$Name)
    $request=Join-Path $script:SafetyDir "$prefix.request.json"
    $gate=Join-Path $script:SafetyDir "$prefix.gate"
    $worker=Join-Path $PSScriptRoot 'Worker.ps1'
    Save-Json "$prefix.command.json" @{executable=$Exe;arguments=$Arguments;workingDirectory=(Get-Location).Path}
    # 请求中只有非敏感参数；凭证从子进程环境传递，拒绝凭证进入参数。
    $raw=@{executable=$Exe;arguments=$Arguments;cwd=(Get-Location).Path}|ConvertTo-Json -Depth 8
    foreach($value in @($Exe)+$Arguments){if((Protect-Text $value) -cne $value){throw 'Sensitive command argument rejected'}}
    [IO.File]::WriteAllText($request,$raw)
    $info=[Diagnostics.ProcessStartInfo]::new((Join-Path $PSHOME 'pwsh.exe'))
    $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
    foreach($arg in @('-NoProfile','-File',$worker,'-Request',$request,'-Gate',$gate)){$info.ArgumentList.Add($arg)}
    # 模拟验收不继承供应商凭证，也不继承可能注入在线模式/额外 Maven 命令的选项。
    foreach($key in @($info.Environment.Keys)) {
        if($key -match '(?i)dashscope|openai|anthropic|api.?key|^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_ARGS|MAVEN_OPTS|SERVER__PORT|SPRING_APPLICATION_JSON|SPRING_PROFILES_ACTIVE)$') {[void]$info.Environment.Remove($key)}
    }
    $p=[Diagnostics.Process]::new();$p.StartInfo=$info
    $job=[ZhkuOwnedJob]::new(); $assigned=$false;$timed=$false;$stopped=$false;$seen=@{};$left=@();$output=[Text.StringBuilder]::new()
    $watch=[Diagnostics.Stopwatch]::StartNew()
    $writer=[IO.StreamWriter]::new((Join-Path $script:SafetyDir "$prefix.log"),$false,[Text.UTF8Encoding]::new($false));$writer.AutoFlush=$true
    try {
        [void]$p.Start()
        $job.Assign($p.Handle);$assigned=$true
        Save-Json "$prefix.owner.json" @{pid=$p.Id;startTime=$p.StartTime.ToUniversalTime().ToString('o');jobAssigned=$true;gateReleased=$false}
        [IO.File]::WriteAllText($gate,'go')
        Save-Json "$prefix.owner.json" @{pid=$p.Id;startTime=$p.StartTime.ToUniversalTime().ToString('o');jobAssigned=$true;gateReleased=$true}
        $outTask=$p.StandardOutput.ReadLineAsync();$errTask=$p.StandardError.ReadLineAsync();$outDone=$false;$errDone=$false
        while(!$p.HasExited -or !$outDone -or !$errDone) {
            foreach($n in $job.Pids()){$seen[[string]$n]=$true}
            foreach($stream in @('out','err')) {
                $task=if($stream -eq 'out'){$outTask}else{$errTask}
                $done=if($stream -eq 'out'){$outDone}else{$errDone}
                $drained=0
                while(!$done -and $task.IsCompleted -and $drained -lt 512) {
                    $drained++
                    $line=$task.GetAwaiter().GetResult()
                    if($null -eq $line){$done=$true;if($stream -eq 'out'){$outDone=$true}else{$errDone=$true}}
                    else {
                        $line=Protect-Text $line; $writer.WriteLine($line);[void]$output.AppendLine($line)
                        if($stream -eq 'out'){$outTask=$p.StandardOutput.ReadLineAsync();$task=$outTask}else{$errTask=$p.StandardError.ReadLineAsync();$task=$errTask}
                    }
                }
            }
            $stopped=(!$script:CleanupMode -and (Test-Path (Join-Path $script:SafetyDir 'STOP')))
            if($watch.Elapsed.TotalSeconds -ge $Seconds -or $script:SafetyClock.Elapsed.TotalSeconds -ge $script:SafetyLimit -or $stopped) {
                $timed=!$stopped; $left=@($job.Pids());$job.Kill();break
            }
            Start-Sleep -Milliseconds 15
        }
        if($timed -or $stopped) {
            [void]$p.WaitForExit(2000)
            # TerminateJobObject 关闭全部后代的管道，继续保存此前缓冲的尾部日志。
            $tailWatch=[Diagnostics.Stopwatch]::StartNew()
            while((!$outDone -or !$errDone) -and $tailWatch.Elapsed.TotalSeconds -lt 2) {
                foreach($stream in @('out','err')) {
                    $task=if($stream -eq 'out'){$outTask}else{$errTask}
                    $done=if($stream -eq 'out'){$outDone}else{$errDone}
                    if(!$done -and $task.IsCompleted -and !$task.IsFaulted) {
                        $tail=$task.GetAwaiter().GetResult()
                        if($null -eq $tail){if($stream -eq 'out'){$outDone=$true}else{$errDone=$true}}
                        else {
                            $tail=Protect-Text $tail;$writer.WriteLine($tail);[void]$output.AppendLine($tail)
                            if($stream -eq 'out'){$outTask=$p.StandardOutput.ReadLineAsync()}else{$errTask=$p.StandardError.ReadLineAsync()}
                        }
                    }
                }
                Start-Sleep -Milliseconds 5
            }
        }
        $native=if($p.HasExited){$p.ExitCode}else{$null}
        if(!$timed -and !$stopped){$left=@($job.Pids())}
        $job.Kill()
        $end=[Diagnostics.Stopwatch]::StartNew()
        while(@($job.Pids()).Count -gt 0 -and $end.Elapsed.TotalSeconds -lt 3){Start-Sleep -Milliseconds 30}
        $remaining=@($job.Pids())
        $result=@{exitCode=$(if($timed){124}elseif($stopped){130}else{$native});nativeExitCode=$native;timedOut=$timed;stopped=$stopped;tailComplete=($outDone -and $errDone);observedPids=@($seen.Keys);pidsBeforeTermination=$left;remainingPids=$remaining;seconds=$watch.Elapsed.TotalSeconds;output=$output.ToString()}
        Save-Json "$prefix.exit.json" ($result|Select-Object -Property * -ExcludeProperty output)
        if($remaining.Count){throw 'Owned job still has processes'}
        return $result
    } finally {
        if(!$assigned -and $p.Id -and !$p.HasExited){$p.Kill()}
        $job.Dispose();$writer.Dispose();$p.Dispose()
    }
}
function Close-Safety { }
