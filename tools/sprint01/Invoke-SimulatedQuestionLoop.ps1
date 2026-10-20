#requires -Version 7.0
param([Parameter(Mandatory)][string]$EvidenceRoot,[string]$PreparedDirectory='',
    [string]$AssetsRoot='D:\alldesk\Projects\ZhkuAgent',
    [ValidatePattern('^m104b-[a-z0-9-]{8,80}$')][string]$Batch=('m104b-sprint01-'+(Get-Date -Format 'yyyyMMdd-HHmmss')),
    [int]$DatabasePort=55442,[int]$BackendPort=58142,
    [switch]$BrowserAcceptance,[int]$WebPort=58143,[string]$PlaywrightModule='')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$EvidenceRoot=[IO.Path]::GetFullPath($EvidenceRoot)
$controller=Join-Path $EvidenceRoot ('controller-'+$Batch)
if(Test-Path $controller){throw 'Fresh controller directory required'}
[void][IO.Directory]::CreateDirectory($controller)
$ps51=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$normalPath=[Environment]::ExpandEnvironmentVariables((@([Environment]::GetEnvironmentVariable('Path','Machine'),[Environment]::GetEnvironmentVariable('Path','User'))|Where-Object {$_}) -join ';')
if(!$env:PATHEXT -or '.EXE' -notin ($env:PATHEXT -split ';') -or '.CMD' -notin ($env:PATHEXT -split ';')){throw 'Actual Windows PATHEXT required'}
function Literal([string]$Value){return "'"+$Value.Replace("'","''")+"'"}
function Start-Normal([string]$Command,[string]$Password='') {
    $code="`$ErrorActionPreference='Stop';[Console]::InputEncoding=[Console]::OutputEncoding=`$OutputEncoding=New-Object Text.UTF8Encoding(`$false);`$global:LASTEXITCODE=`$null;"+$Command+";if(`$null -eq `$LASTEXITCODE){exit 65};exit `$LASTEXITCODE"
    $info=[Diagnostics.ProcessStartInfo]::new($ps51);$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$info.RedirectStandardError=$true
    $info.StandardOutputEncoding=$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
    $info.Environment['PATH']=$normalPath;$info.Environment['PATHEXT']=$env:PATHEXT
    foreach($key in @($info.Environment.Keys)){if($key -match '(?i)dashscope|openai|anthropic|api.?key|^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_ARGS|MAVEN_OPTS)$|^(LOADER_|SPRING_|SERVER_|ZHKU_RAG_|ZHKU_RULECHECK_|ZHKU_RULE_AGENT_)'){[void]$info.Environment.Remove($key)}}
    if($Password){$info.Environment['ZHKU_SIM_DEMO_STUDENT_PASSWORD']=$Password}
    foreach($arg in @('-NoProfile','-NonInteractive','-EncodedCommand',[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($code)))){[void]$info.ArgumentList.Add($arg)}
    $p=[Diagnostics.Process]::Start($info)
    return @{process=$p;stdout=$p.StandardOutput.ReadToEndAsync();stderr=$p.StandardError.ReadToEndAsync()}
}
function Finish-Normal($Child,[string]$Name,[int]$Seconds=120) {
    $p=$Child.process
    if(!$p.WaitForExit($Seconds*1000)){
        # 监督进程可能仍持有数据库，不能在超时分支直接强杀并丢掉恢复身份。
        [IO.File]::WriteAllText((Join-Path $controller ($Name+'.timeout.json')),(@{pid=$p.Id;ownerInspectionRequired=$true;resourcesExited=$false}|ConvertTo-Json))
        $p.Dispose();throw ($Name+' timeout; inspect recorded owner before recovery')
    }
    $out=$Child.stdout.GetAwaiter().GetResult();$err=$Child.stderr.GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $controller ($Name+'.stdout.log')),$out,[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $controller ($Name+'.stderr.log')),$err,[Text.UTF8Encoding]::new($false))
    $code=$p.ExitCode;$p.Dispose()
    if($code -ne 0){throw ($Name+' failed: exit='+$code+'; see '+$controller)}
    return $out
}
$base='& '+(Literal (Join-Path $root 'knowledge/scripts/start-rule-agent-simulated-demo.cmd'))+' -EvidenceRoot '+(Literal $EvidenceRoot)+' -AssetsRoot '+(Literal $AssetsRoot)
if(!$PreparedDirectory){
    Write-Output 'Preparing existing simulated host in isolated offline build'
    $text=Finish-Normal (Start-Normal ($base+' -Action Prepare')) 'prepare' 600
    $PreparedDirectory=($text -split '\r?\n'|Where-Object {$_ -like 'PreparedDirectory: *'}|Select-Object -Last 1) -replace '^PreparedDirectory: ',''
    if(!$PreparedDirectory){throw 'Prepare artifact directory missing'}
}
$run=Join-Path $EvidenceRoot $Batch
if(Test-Path $run){throw 'Fresh batch required'}
# 合成密码只驻留内存和子进程环境；不写参数、回执或 HTTP 日志。
$password=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(16))
. (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
$start=Start-Normal ($base+' -Action Start -PreparedDirectory '+(Literal $PreparedDirectory)+' -Batch '+$Batch+' -DatabasePort '+$DatabasePort+' -BackendPort '+$BackendPort+' -LeaseSeconds 900 -ApproveTemporaryDatabase') $password
$passed=$false;$failure=$null;$checks=[Collections.Generic.List[string]]::new();$exchanges=[Collections.Generic.List[object]]::new()
$url="http://127.0.0.1:$BackendPort/api/zhku/controlled-preview/v2"
$http=[Net.Http.HttpClient]::new([Net.Http.HttpClientHandler]::new());$http.Timeout=[TimeSpan]::FromSeconds(10)
$csrf=$null;$webStarted=$false;$webProject=('zhku-'+$Batch);$webLauncher=Join-Path $root 'agent/frontend/start-nginx-preview.cmd'
function Request([string]$Method,[string]$Path,$Body=$null,[bool]$Token=$false,[int]$Expected=200) {
    $req=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),$url+$Path);$res=$null
    if($Token){[void]$req.Headers.TryAddWithoutValidation($script:csrf.headerName,$script:csrf.token)}
    if($null -ne $Body){$req.Content=[Net.Http.StringContent]::new(($Body|ConvertTo-Json -Depth 20 -Compress),[Text.Encoding]::UTF8,'application/json')}
    try {$res=$http.Send($req);$raw=$res.Content.ReadAsStringAsync().GetAwaiter().GetResult();$status=[int]$res.StatusCode
        $value=if($raw){$raw|ConvertFrom-Json}else{$null};$exchanges.Add(@{method=$Method;path=$Path;status=$status;actual=$value})
        if($status -ne $Expected){throw ("HTTP $Method $Path expected=$Expected actual=$status")};return $value
    }finally{$req.Dispose();if($res){$res.Dispose()}}
}
function Await-Task([string]$Id){
    $end=[DateTime]::UtcNow.AddSeconds(30)
    do {$s=Request GET ('/tasks/'+$Id);if($s.terminated){return $s};Start-Sleep -Milliseconds 100}while([DateTime]::UtcNow -lt $end)
    throw 'Task did not terminate in 30 seconds'
}
try {
    $end=[DateTime]::UtcNow.AddSeconds(180)
    while(!(Test-Path (Join-Path $run 'host-ready.json'))){if($start.process.HasExited -or [DateTime]::UtcNow -ge $end){throw 'Host did not become ready'};Start-Sleep -Milliseconds 250}
    $ready=Get-Content -Raw (Join-Path $run 'host-ready.json')|ConvertFrom-Json
    if($ready.mode -cne 'SIMULATED' -or $ready.providerCalls -ne 0){throw 'Ready identity mismatch'}
    $checks.Add('ordinary-cmd-host-ready-simulated');Write-Output 'Host ready; checking real login and question loop'
    [void](Request GET '/tasks/capabilities' $null $false 401);$csrf=Request GET '/auth/csrf'
    $fields=[Collections.Generic.Dictionary[string,string]]::new();$fields.Add('username',[string]$ready.loginName);$fields.Add('password',$password)
    $form=[Net.Http.FormUrlEncodedContent]::new($fields)
    $login=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post,$url+'/auth/login');$login.Content=$form
    [void]$login.Headers.TryAddWithoutValidation($csrf.headerName,$csrf.token)
    $reply=$http.Send($login);try{if([int]$reply.StatusCode -ne 200){throw 'Synthetic-account real login failed'}}finally{$reply.Dispose();$login.Dispose()}
    $csrf=Request GET '/auth/csrf';$caps=Request GET '/tasks/capabilities'
    if($caps.schemaVersion -ne 3 -or !$caps.asynchronous){throw 'Task API contract mismatch'}
    $checks.Add('login-csrf-authentication');[void](Request POST '/tasks' @{question=$ready.question;clientRequestId=[guid]::NewGuid().ToString()} $false 403)
    foreach($course in @(@{label='公共选修课';value='PUBLIC';fact='public-failed-no-retake'},@{label='专业选修课';value='PROFESSIONAL';fact='professional-failed-retake-application'})) {
        $body=@{question=$ready.question;clientRequestId=[guid]::NewGuid().ToString()};$ticket=Request POST '/tasks' $body $true 202
        $duplicate=Request POST '/tasks' $body $true 202
        if(!$duplicate.duplicate -or $duplicate.taskId -ne $ticket.taskId){throw 'Create idempotency failed'}
        $waiting=Await-Task $ticket.taskId
        if($waiting.taskStatus -cne 'WAITING_USER'){throw ('Expected clarification; got '+$waiting.taskStatus)}
        $next=Request POST ('/tasks/'+$ticket.taskId+'/messages') @{message=$course.label;continuationToken=$waiting.continuationToken;expectedVersion=$waiting.version;clientRequestId=[guid]::NewGuid().ToString()} $true 202
        if($next.taskId -ne $ticket.taskId -or $next.runId -eq $ticket.runId){throw 'Continuation identity mismatch'}
        $answer=Await-Task $ticket.taskId
        if($answer.taskStatus -cne 'RESOLVED' -or $answer.result.coverage -cne 'COMPLETE' -or $answer.execution.modelAttemptCount -ne 0 -or $answer.execution.mode -cne 'ENGINEERING_REPLAY' -or $answer.execution.providerResponseReceived){throw ('Expected complete simulated answer; got '+$answer.taskStatus)}
        $claims=@($answer.result.items|ForEach-Object {$_.claims})
        $claim=$claims|Where-Object {$_.factId -ceq ('S10/RETAKE_ONLY:'+$course.fact) -and $_.when.courseType -ceq $course.value}
        if(!$claim){throw 'Course-specific verified claim missing'}
        $linked=@($answer.result.evidenceRelations|Where-Object {$_.role -ceq 'CLAIM' -and $_.targetId -ceq $claim.id})
        if(!$linked.Count){throw 'Claim evidence relation missing'}
        if(@($answer.result.evidence).Count -eq 0){throw 'Evidence missing'}
        foreach($e in $answer.result.evidence){
            $original=Request GET ('/tasks/'+$ticket.taskId+'/originals/'+$e.originalToken)
            $offset=[int]($original.quoteStartInclusive-$original.contextStartInclusive);$length=[int]($original.quoteEndExclusive-$original.quoteStartInclusive)
            if($original.sourceId -cne $e.sourceId -or $original.chunkId -cne $e.chunkId -or $original.revision -cne $e.revision -or $original.quoteStartInclusive -ne $e.startInclusive -or $original.quoteEndExclusive -ne $e.endExclusive -or $offset -lt 0 -or $length -lt 1 -or $offset+$length -gt $original.contextText.Length -or $original.contextText.Substring($offset,$length) -cne $e.quote){throw 'Original quote identity/span/content mismatch'}
        }
        $checks.Add('same-task-clarification-answer-original-'+$course.fact);Write-Output ('Verified branch: '+$course.label)
    }
    if($BrowserAcceptance){
        if(!(Test-Path -LiteralPath $PlaywrightModule)){throw 'Provide installed Playwright module; no download allowed'}
        # 同一后端仍在租期内；独立构建学生入口，模拟标识不能依赖 URL 参数。
        $env:VITE_DEMO_MODE='simulated'
        $webStarted=$true
        [void](Finish-Normal (Start-Normal ('& '+(Literal $webLauncher)+' -Action Start -EntryMode student -BackendPort '+$BackendPort+' -WebPort '+$WebPort+' -ProjectName '+$webProject)) 'web-start' 180)
        . (Join-Path $root 'scripts/Resolve-ZhkuNode.ps1');$node=Get-ZhkuNodeToolchain
        $browserScript=Join-Path $PSScriptRoot 'student-question-loop.mjs'
        $command='& '+(Literal $node.Node)+' '+(Literal $browserScript)+' '+(Literal $PlaywrightModule)+' '+(Literal ("http://127.0.0.1:$WebPort"))+' '+(Literal $controller)
        [void](Finish-Normal (Start-Normal $command $password) 'browser' 180)
        $checks.Add('student-browser-two-branches-originals-cross-window-logout')
    }
    [void](Request POST '/auth/logout' $null $true 204);[void](Request GET ('/tasks/'+$ticket.taskId) $null $false 401)
    $checks.Add('logout-removes-task-access');$passed=$true
}catch{$failure=$_.Exception.Message}
finally {
    $http.Dispose();$password=$null
    if($webStarted){try{[void](Finish-Normal (Start-Normal ('& '+(Literal $webLauncher)+' -Action Stop -ProjectName '+$webProject)) 'web-stop' 45)}catch{$passed=$false;$failure+='; web stop: '+$_.Exception.Message}}
    if(!$start.process.HasExited -and (Test-Path (Join-Path $run 'launcher-owner.json'))){try{[void](Finish-Normal (Start-Normal ($base+' -Action Stop -Batch '+$Batch)) 'stop' 45)}catch{$passed=$false;$failure+='; stop: '+$_.Exception.Message}}
    try{[void](Finish-Normal $start 'start' 150)}catch{$passed=$false;$failure+='; start: '+$_.Exception.Message}
    $ended=$null;$summary=$null
    try{if(Test-Path (Join-Path $run 'host-exit.json')){$ended=Get-Content -Raw (Join-Path $run 'host-exit.json')|ConvertFrom-Json}}catch{$passed=$false;$failure+='; invalid host exit receipt'}
    try{if(Test-Path (Join-Path $run 'summary.json')){$summary=Get-Content -Raw (Join-Path $run 'summary.json')|ConvertFrom-Json}}catch{$passed=$false;$failure+='; invalid launcher summary'}
    if(!$ended.resourcesClosed -or !$summary.cleanupConfirmed -or $summary.exitCode -ne 0){$passed=$false;$failure+='; resource exit not confirmed'}else{$checks.Add('host-job-listener-container-volume-exit')}
    $report=[ordered]@{passed=$passed;failure=$failure;batch=$Batch;sourceRoot=$root;preparedDirectory=$PreparedDirectory;checks=$checks;mode='SIMULATED';providerCalls=0;hostExit=$ended;summary=$summary;exchanges=$exchanges}
    # 运行时需要续接/引用令牌，落盘证据只保留脱敏副本，避免回执成为可重放凭证。
    function Redact($Value){
        if($Value -is [Collections.IDictionary]){foreach($key in @($Value.Keys)){
            if($key -match '(?i)token|password'){$Value[$key]='[REDACTED]'}
            elseif($key -eq 'path' -and $Value[$key] -is [string]){$Value[$key]=$Value[$key] -replace '(/originals/)[^/?#]+','$1[REDACTED]'}
            else{Redact $Value[$key]}
        }}
        elseif($Value -is [Collections.IList]){foreach($item in $Value){Redact $item}}
    }
    $safe=$report|ConvertTo-Json -Depth 40|ConvertFrom-Json -AsHashtable;Redact $safe
    [IO.File]::WriteAllText((Join-Path $controller 'result.json'),($safe|ConvertTo-Json -Depth 40),[Text.UTF8Encoding]::new($false))
}
Write-Output ('Question loop passed='+$passed+'; evidence='+$controller)
if(!$passed){Write-Output $failure;exit 1}
