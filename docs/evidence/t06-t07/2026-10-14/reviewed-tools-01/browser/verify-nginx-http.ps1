$ErrorActionPreference='Stop'
$base='http://127.0.0.1:53217/api/zhku/controlled-preview/v2'
$records=[Collections.Generic.List[object]]::new()
$session=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
$other=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
function Login-Synthetic($s,$user){
 $csrf=(Invoke-WebRequest -Uri "$base/auth/csrf" -WebSession $s -TimeoutSec 3).Content|ConvertFrom-Json
 $headers=@{};$headers[$csrf.headerName]=$csrf.token
 $r=Invoke-WebRequest -Method Post -Uri "$base/auth/login" -WebSession $s -Headers $headers -ContentType 'application/x-www-form-urlencoded' -Body @{username=$user;password='synthetic-only-password'} -SkipHttpErrorCheck -TimeoutSec 3
 if($r.StatusCode -ne 200){throw '合成登录未通过'}
 return (Invoke-WebRequest -Uri "$base/auth/csrf" -WebSession $s -TimeoutSec 3).Content|ConvertFrom-Json
}
$token=Login-Synthetic $session 'student-b';$otherToken=Login-Synthetic $other 'student-a'
function Invoke-Task($method,$path,$body,$expected,$s=$session,$csrf=$token,$withCsrf=$true){
 $args=@{Uri="$base$path";WebSession=$s;Method=$method;SkipHttpErrorCheck=$true;TimeoutSec=3;Headers=@{}}
 if($method -ne 'GET'){$args.ContentType='application/json; charset=UTF-8';$args.Body=$body|ConvertTo-Json -Compress;if($withCsrf){$args.Headers[$csrf.headerName]=$csrf.token}}
 $r=Invoke-WebRequest @args
 $records.Add([ordered]@{method=$method;path=$path;input=$body;status=[int]$r.StatusCode;cacheControl=[string]$r.Headers['Cache-Control'];actual=($r.Content|ConvertFrom-Json)})
 if($r.StatusCode -ne $expected){throw "预期HTTP$expected，实际$($r.StatusCode)"}
 if(![string]$r.Headers['Cache-Control'].Contains('no-store')){throw '响应缓存边界未通过'}
 return $r.Content|ConvertFrom-Json
}
function Wait-Task($id){$end=[DateTime]::UtcNow.AddSeconds(7);do{$x=Invoke-Task GET "/tasks/$id" $null 200;if($x.taskStatus -ne 'ACTIVE'){return $x};Start-Sleep -Milliseconds 150}while([DateTime]::UtcNow -lt $end);throw '模拟运行未及时结束'}
$input=@{question='澄清';clientRequestId=[guid]::NewGuid().ToString()}
$null=Invoke-Task POST '/tasks' $input 403 $session $token $false
$a=Invoke-Task POST '/tasks' $input 202
$dup=Invoke-Task POST '/tasks' $input 202
if(!$dup.duplicate -or $dup.runId -ne $a.runId){throw '幂等回执不一致'}
$null=Invoke-Task GET "/tasks/$($a.taskId)" $null 404 $other $otherToken
$x=Wait-Task $a.taskId
if($x.taskStatus -ne 'WAITING_USER'){throw '澄清未发布'}
$m=@{message='PUBLIC';continuationToken=$x.continuationToken;expectedVersion=1;clientRequestId=[guid]::NewGuid().ToString()}
$null=Invoke-Task POST "/tasks/$($a.taskId)/messages" $m 409
$m.expectedVersion=$x.version
$null=Invoke-Task POST "/tasks/$($a.taskId)/messages" $m 202
$m.clientRequestId=[guid]::NewGuid().ToString();$null=Invoke-Task POST "/tasks/$($a.taskId)/messages" $m 409
$x=Wait-Task $a.taskId
if($x.taskStatus -ne 'RESOLVED'){throw '续答未结束'}
$null=Invoke-Task GET "/tasks/$($a.taskId)/originals/$($x.result.evidence[0].originalToken)" $null 200
foreach($case in @('部分','故障','取消')){
 $a=Invoke-Task POST '/tasks' @{question=$case;clientRequestId=[guid]::NewGuid().ToString()} 202
 if($case -eq '取消'){$null=Invoke-Task POST "/tasks/$($a.taskId)/cancel" @{runId=$a.runId;expectedVersion=$a.version;clientRequestId=[guid]::NewGuid().ToString()} 202}
 $x=Wait-Task $a.taskId
 if($case -eq '部分' -and $x.result.coverage -ne 'PARTIAL'){throw '部分覆盖未发布'}
 if($case -eq '故障' -and $x.taskStatus -ne 'FAILED'){throw '故障状态未发布'}
 if($case -eq '取消' -and $x.taskStatus -ne 'STOPPED'){throw '取消最终状态未发布'}
}
$source=git rev-parse HEAD
$value=[ordered]@{sourceSha=$source;schemaVersion=3;executionMode='TEST_SIMULATION_NO_PROVIDER';proxy='ACTUAL_NGINX';providerCalls=0;databaseCalls=0;passed=$true;exchanges=$records}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'http-exchanges.json'),($value|ConvertTo-Json -Depth 18).Replace("`r`n","`n")+"`n",[Text.UTF8Encoding]::new($false))
