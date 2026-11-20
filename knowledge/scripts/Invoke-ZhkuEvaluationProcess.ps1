# 仅评测宿主使用长预算；保留通用Invoke-ZhkuOwnedProcess的既有250秒上限。
function Invoke-ZhkuEvaluationProcess {
 param([Parameter(Mandatory)][string]$Executable,[string[]]$Arguments=@(),
       [ValidateRange(1,3600)][int]$TimeoutSeconds=20,[string[]]$Secrets=@())
 $info=[Diagnostics.ProcessStartInfo]::new($Executable)
 $info.UseShellExecute=$false;$info.CreateNoWindow=$true
 $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$info.StandardErrorEncoding=$info.StandardOutputEncoding
 foreach($argument in $Arguments){$info.ArgumentList.Add($argument)}
 $process=[Diagnostics.Process]::Start($info)
 $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
 $timedOut=$false;$closed=$false;$exitCode=$null;$output='';$errorOutput=''
 try {
  if(!$process.WaitForExit($TimeoutSeconds*1000)){
   $timedOut=$true
   # 只回收本调用拥有的进程树，不能按程序名关闭其他任务。
   try{$process.Kill($true)}catch{}
   $closed=$process.WaitForExit(5000)
  }else{$closed=$true}
  if($closed){
   $exitCode=$process.ExitCode
   $closed=[Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($stdout,$stderr),2000)
   if($closed){$output=$stdout.GetAwaiter().GetResult();$errorOutput=$stderr.GetAwaiter().GetResult()}
  }
  foreach($secret in $Secrets){if($secret){$output=$output.Replace($secret,'[REDACTED]');$errorOutput=$errorOutput.Replace($secret,'[REDACTED]')}}
  [pscustomobject]@{exitCode=$exitCode;timedOut=$timedOut;resourcesClosed=$closed;output=$output;errorOutput=$errorOutput}
 }finally{$process.Dispose()}
}
