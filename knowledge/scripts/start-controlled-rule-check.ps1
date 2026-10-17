#requires -Version 7.0
param(
    [switch]$CheckEnvironment,[switch]$Smoke,[switch]$BuildOnly,[switch]$TaskPlanReplay,
    [ValidateRange(1024,65535)][int]$DatabasePort=55500,
    [ValidateRange(1024,65535)][int]$BackendPort=58126,
    [ValidateRange(1,15)][int]$MaxMinutes=5
)
$ErrorActionPreference='Stop'
if($BuildOnly-and ($CheckEnvironment-or $Smoke)){throw '内部构建模式不能与环境检查/启动验收混用'}
$ruleUtf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$ruleUtf8;[Console]::OutputEncoding=$ruleUtf8;$OutputEncoding=$ruleUtf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$replayMode=if($TaskPlanReplay){'TASK_PLAN_REPLAY_NOT_LIVE'}else{'SEALED_REPLAY_NOT_LIVE'}
$taskBatch='docs/evidence/rule-check-final-candidate/2026-10-12'
$importer=Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$java=Get-Command java.exe -ErrorAction SilentlyContinue
$docker=Get-Command docker.exe -ErrorAction SilentlyContinue
if($BuildOnly){
    # 只供有界父进程调用；构建不需要数据库/供应商凭据。
    foreach($name in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','DASHSCOPE_API_KEY','ZHKU_RULECHECK_STUDENT_PASSWORD')){[Environment]::SetEnvironmentVariable($name,$null,'Process')}
    Push-Location $root
    try{& (Join-Path $root 'mvnw.cmd') -o "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" '-Dfile.encoding=UTF-8' -pl knowledge -am '-DskipTests' package;$buildExit=$LASTEXITCODE}finally{Pop-Location}
    exit $buildExit
}
if($CheckEnvironment){
    [ordered]@{powerShell=$PSVersionTable.PSVersion.ToString();javaAvailable=($null-ne $java);dockerAvailable=($null-ne $docker);
        encodingSample='规则查证：离线回放、正常退出';encoding=[Console]::OutputEncoding.CodePage;
        selectorMode=$replayMode;providerCalls=0;databaseOperations=0;builds=0;requiresRipgrep=$false}|ConvertTo-Json
    exit 0
}
if(!$java-or !$docker){throw '需要普通终端可调用的Java与Docker；-CheckEnvironment可先核对，不修改全局PATH'}
if($DatabasePort-eq 54329-or $DatabasePort-eq $BackendPort){throw '拒绝原演示库或端口混用'}
foreach($port in @($DatabasePort,$BackendPort)){
    $probe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
    try{$probe.Start()}finally{$probe.Stop()}
}
# 共享target不能并发构建；仅查看进程，绝不关闭用户的Java、客户端或浏览器。
if (@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
    $command=$_.CommandLine
    $demoMain=$command -match '(?:-Dloader\.main=|\s)\S*\.(?:Controlled2025RuleCheckHost)(?=[\s"]|$)'
    $mavenMain=$command -match '\borg\.codehaus\.plexus\.classworlds\.launcher\.Launcher\b'
    if($demoMain){return $true}
    if(!$mavenMain){return $false}
    # 冲突来自共享target，不能把别的仓库构建或IDE类路径jar当成本仓Maven。
    # 工作目录缺失/相对/无法解析时仍拒绝，不能用猜测绕过同仓串行保护。
    $project=[regex]::Match($command,'(?i)-Dmaven\.multiModuleProjectDirectory=(?:"([^"]+)"|([^\s]+))')
    if(!$project.Success){return $true}
    try {
        $directory=($project.Groups[1].Value+$project.Groups[2].Value).Trim([char[]]@('"','\'))
        if(![IO.Path]::IsPathFullyQualified($directory)){return $true}
        $directory=[IO.Path]::GetFullPath($directory).Replace('\','/').TrimEnd('/')
        $repository=[IO.Path]::GetFullPath($root).Replace('\','/').TrimEnd('/')
        $comparison=[StringComparison]::OrdinalIgnoreCase
        $sameTree=$directory.Equals($repository,$comparison) -or $directory.StartsWith($repository+'/',$comparison) -or $repository.StartsWith($directory+'/',$comparison)
        $explicitRepository=$command.Replace('\','/') -match ([regex]::Escape($repository)+'(?=[/"\s]|$)')
        return $sameTree -or $explicitRepository
    } catch {return $true}
}).Count) {
    throw '已有构建/查证宿主占用本仓库或目录无法确认，请正常退出后再运行；不会停止它。'
}
$phase=(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$directory=Join-Path $root ('docs/evidence/rule-check-refactor/2026-10-10/package-02/startup/'+$phase)
New-Item -ItemType Directory -Path $directory -Force|Out-Null
function Save($value,$path){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 12),$ruleUtf8)}
function Sources {
    @(Get-ChildItem (Join-Path $root 'knowledge/src/main'),(Join-Path $root 'agent-runtime/src/main') -File -Recurse|Sort-Object FullName|ForEach-Object {
        [ordered]@{path=[IO.Path]::GetRelativePath($root,$_.FullName);sha256=(Get-FileHash $_.FullName).Hash}
    })
}
$before=Sources;Save $before (Join-Path $directory 'source-before.json')
$names=@('ZHKU_DB_PASSWORD','ZHKU_DB_URL','POSTGRES_PASSWORD','DASHSCOPE_API_KEY','ZHKU_RULECHECK_ROOT','ZHKU_RULECHECK_PORT','ZHKU_RULECHECK_EVIDENCE','ZHKU_RULECHECK_STUDENT_PASSWORD')
$names+=@('ZHKU_RULECHECK_TASK_BATCH_DIRECTORY','ZHKU_RULECHECK_TASK_PACKAGE_SHA256','ZHKU_RULECHECK_TASK_SUMMARY_SHA256')
$saved=@{};foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$container='zhku-rulecheck-start-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$owned=$false;$hostProcess=$null;$resources=$true;$passed=$false;$failure=$null;$hostRuns=0
$dbSecret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
$password=if($Smoke){'Synthetic-'+[guid]::NewGuid().ToString()}else{
    $secure=Read-Host '为本次临时学生账号设置密码（16～64位；隐藏输入）' -AsSecureString
    $buffer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try{[Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer);$secure.Dispose()}
}
if($password.Length-lt 16-or $password.Length-gt 64){throw '账号密码长度必须16～64'}
function InvokeDocker([string[]]$arguments,[int]$seconds=5){
    & $importer -RequireDatabase
    $result=Invoke-ZhkuOwnedProcess -Executable $docker.Source -Arguments $arguments -TimeoutSeconds $seconds -Secrets @($dbSecret,$password)
    if($result.timedOut-or !$result.resourcesClosed){throw '本批Docker客户端超时，容器状态未确认'}
    return $result
}
function StartHost([int]$number){
    & $importer -RequireDatabase
    $runDirectory=Join-Path $directory "host-$number";New-Item -ItemType Directory -Path $runDirectory|Out-Null
    $info=[Diagnostics.ProcessStartInfo]::new($java.Source);$info.WorkingDirectory=$root;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.StandardInputEncoding=$ruleUtf8;$info.StandardOutputEncoding=$ruleUtf8;$info.StandardErrorEncoding=$ruleUtf8
    foreach($argument in @('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',
        '-Dloader.main=com.zhku.knowledge.rulecheck.web.Controlled2025RuleCheckHost','-cp',
        (Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar'),'org.springframework.boot.loader.launch.PropertiesLauncher')){[void]$info.ArgumentList.Add($argument)}
    $info.Environment['ZHKU_RULECHECK_ROOT']=$root;$info.Environment['ZHKU_RULECHECK_PORT']=[string]$BackendPort
    # 4次新调用与40题逻辑回放分开；准入由正式Java核验原授权和缓存来源，不增外发权限。
    if($TaskPlanReplay){
        $info.Environment['ZHKU_RULECHECK_TASK_BATCH_DIRECTORY']=$taskBatch
        $info.Environment['ZHKU_RULECHECK_TASK_PACKAGE_SHA256']=(Get-FileHash (Join-Path $root "$taskBatch/request-package.json") -Algorithm SHA256).Hash.ToLowerInvariant()
        $info.Environment['ZHKU_RULECHECK_TASK_SUMMARY_SHA256']=(Get-FileHash (Join-Path $root "$taskBatch/provider-capture/summary.json") -Algorithm SHA256).Hash.ToLowerInvariant()
    }else{foreach($name in @('ZHKU_RULECHECK_TASK_BATCH_DIRECTORY','ZHKU_RULECHECK_TASK_PACKAGE_SHA256','ZHKU_RULECHECK_TASK_SUMMARY_SHA256')){[void]$info.Environment.Remove($name)}}
    $info.Environment['ZHKU_RULECHECK_EVIDENCE']=$runDirectory;$info.Environment['ZHKU_RULECHECK_STUDENT_PASSWORD']=$password
    [void]$info.Environment.Remove('DASHSCOPE_API_KEY')
    $script:hostProcess=[Diagnostics.Process]::Start($info)
    $script:hostOut=$hostProcess.StandardOutput.ReadToEndAsync();$script:hostErr=$hostProcess.StandardError.ReadToEndAsync()
    $deadline=[DateTime]::UtcNow.AddSeconds(35)
    while(!(Test-Path (Join-Path $runDirectory 'host-ready.json'))-and !$hostProcess.HasExited-and [DateTime]::UtcNow-lt $deadline){Start-Sleep -Milliseconds 100}
    if(!(Test-Path (Join-Path $runDirectory 'host-ready.json'))){throw '宿主35秒内未就绪；查看本批退出记录，不重试'}
    $ready=Get-Content -Raw (Join-Path $runDirectory 'host-ready.json')|ConvertFrom-Json
    if($ready.port-ne $BackendPort-or $ready.selectorMode-ne $replayMode-or $ready.providerCalls-ne 0){throw '实际宿主模式/端口不符'}
    $script:hostRuns++;return $runDirectory
}
function StopHost($runDirectory){
    if(!$hostProcess.HasExited){$hostProcess.StandardInput.WriteLine('STOP');$hostProcess.StandardInput.Flush()}
    if(!$hostProcess.WaitForExit(10000)){throw '宿主未正常关闭；不能强杀冒充通过'}
    if($hostProcess.ExitCode-ne 0-or !(Test-Path (Join-Path $runDirectory 'host-closed.json'))){throw '宿主退出或资源确认失败'}
    $closed=Get-Content -Raw (Join-Path $runDirectory 'host-closed.json')|ConvertFrom-Json
    if(!$closed.resourcesClosed){throw '宿主资源未收敛'}
    $socket=[Net.Sockets.TcpClient]::new();try{$socket.Connect('127.0.0.1',$BackendPort);throw '宿主关闭后端口仍监听'}catch [Net.Sockets.SocketException]{}finally{$socket.Dispose()}
    $hostProcess.Dispose();$script:hostProcess=$null
}
function HttpSmoke($runDirectory){
    $uri="http://127.0.0.1:$BackendPort/api/zhku/controlled-preview/v2"
    $session=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $csrf=Invoke-RestMethod "$uri/auth/csrf" -WebSession $session -TimeoutSec 5
    $headers=@{};$headers[$csrf.headerName]=$csrf.token
    Invoke-RestMethod "$uri/auth/login" -Method Post -Body @{username='controlled-2025-student';password=$password} -Headers $headers -WebSession $session -TimeoutSec 5|Out-Null
    $csrf=Invoke-RestMethod "$uri/auth/csrf" -WebSession $session -TimeoutSec 5;$headers[$csrf.headerName]=$csrf.token
    if($TaskPlanReplay){$pack=Get-Content -Raw (Join-Path $root "$taskBatch/request-package.json")|ConvertFrom-Json;$request=$pack.allRequests|Where-Object caseId -eq 'R01'}
    else{$pack=Get-Content -Raw (Join-Path $root 'docs/evidence/rule-check-refactor/2026-10-10/package-01/request-package.json')|ConvertFrom-Json;$request=$pack.requests|Where-Object caseId -eq 'Q12'}
    $question=($request.body.messages[1].content|ConvertFrom-Json).question
    $body=@{question=$question;confirmedConditions=@{}}|ConvertTo-Json
    $reply=Invoke-RestMethod "$uri/rule-check" -Method Post -Body ([Text.Encoding]::UTF8.GetBytes($body)) -ContentType 'application/json' -Headers $headers -WebSession $session -TimeoutSec 5
    if($reply.status-ne 'ANSWERED'-or !$reply.confirmedFacts.Count){throw '实际受控HTTP未取得已审回答'}
    $token=$reply.confirmedFacts[0].citations[0].originalToken
    $original=Invoke-RestMethod "$uri/rule-check/originals/$token" -WebSession $session -TimeoutSec 5
    if($original.revision-ne '2025.9.2'-or $original.recordedPageNumber-notin @(76,77)){throw '实际原文版本/页码不符'}
    Save @{mode=$replayMode;providerCalls=0;response=$reply;original=$original;login=$true} (Join-Path $runDirectory 'http-smoke.json')
}
Push-Location $root
try{
    & $importer -UseDatabase
    $env:ZHKU_DB_PASSWORD=$dbSecret;$env:POSTGRES_PASSWORD=$dbSecret;$env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/zhkuagent_rulecheck_e2e"
    $env:DASHSCOPE_API_KEY=$null
    # 标记拥有关系后发起启动，启动确认丢失仍只回收随机命名的本次容器。
    $owned=$true
    $created=InvokeDocker @('run','--rm','-d','--pull','never','--name',$container,'--publish',"127.0.0.1:${DatabasePort}:5432",'--env','POSTGRES_PASSWORD','--env','POSTGRES_USER=zhkuagent','--env','POSTGRES_DB=zhkuagent_rulecheck_e2e','pgvector/pgvector:0.8.6-pg16-bookworm') 10
    if($created.exitCode-ne 0){throw '本机已有镜像/专用临时库启动失败'}
    $ready=$false;$databaseDeadline=[DateTime]::UtcNow.AddSeconds(30)
    while([DateTime]::UtcNow-lt $databaseDeadline){
        $remainingSeconds=[Math]::Max(1,[Math]::Min(5,[Math]::Floor(($databaseDeadline-[DateTime]::UtcNow).TotalSeconds)))
        $probe=InvokeDocker @('exec',$container,'pg_isready','-U','zhkuagent','-d','zhkuagent_rulecheck_e2e') $remainingSeconds
        if($probe.exitCode-eq 0){$ready=$true;break};Start-Sleep -Milliseconds 200
    }
    if(!$ready){throw '临时库30秒内未就绪'}
    $buildInfo=[Diagnostics.ProcessStartInfo]::new((Join-Path $PSHOME 'pwsh.exe'));$buildInfo.UseShellExecute=$false;$buildInfo.CreateNoWindow=$true;$buildInfo.WorkingDirectory=$root
    $buildInfo.RedirectStandardOutput=$true;$buildInfo.RedirectStandardError=$true;$buildInfo.StandardOutputEncoding=$ruleUtf8;$buildInfo.StandardErrorEncoding=$ruleUtf8
    foreach($argument in @('-NoProfile','-File',$PSCommandPath,'-BuildOnly')){[void]$buildInfo.ArgumentList.Add($argument)}
    foreach($name in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','DASHSCOPE_API_KEY','ZHKU_RULECHECK_STUDENT_PASSWORD')){[void]$buildInfo.Environment.Remove($name)}
    $buildProcess=[Diagnostics.Process]::Start($buildInfo);$buildOut=$buildProcess.StandardOutput.ReadToEndAsync();$buildErr=$buildProcess.StandardError.ReadToEndAsync()
    try{
        if(!$buildProcess.WaitForExit(150000)){$buildProcess.Kill($true);[void]$buildProcess.WaitForExit(5000);throw '构建150秒预算耗尽'}
        [IO.File]::WriteAllText((Join-Path $directory 'build.log'),$buildOut.GetAwaiter().GetResult()+$buildErr.GetAwaiter().GetResult(),$ruleUtf8)
        if($buildProcess.ExitCode-ne 0){throw '离线打包失败；不启动旧构建冒充新入口'}
    }finally{$buildProcess.Dispose()}
    Save @{exitCode=0;jarSha256=(Get-FileHash (Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar')).Hash;testClasspathAdded=$false} (Join-Path $directory 'build.json')
    Write-Host '启动2025规则查证（离线封存响应回放；新问题可能缺证）。'
    $run=StartHost 1
    if($Smoke){HttpSmoke $run;StopHost $run;$run=StartHost 2;HttpSmoke $run;StopHost $run}
    else{
        Write-Host "接口：http://127.0.0.1:$BackendPort/api/zhku/controlled-preview/v2；账号 controlled-2025-student"
        Write-Host "最长运行$MaxMinutes分钟，回车正常退出；网页代理可沿用已有规则查证前端，勿用于在线试点。"
        $deadline=[DateTime]::UtcNow.AddMinutes($MaxMinutes);$inputTask=[Console]::In.ReadLineAsync()
        while(!$inputTask.IsCompleted-and [DateTime]::UtcNow-lt $deadline-and !$hostProcess.HasExited){Start-Sleep -Milliseconds 200}
        StopHost $run
    }
    $passed=$true
}catch{$failure=$_.Exception.Message;Write-Host '本批启动验收未完成，已记录并回收本批资源。'}finally{
    if($hostProcess){try{if(!$hostProcess.HasExited){$hostProcess.Kill($true);[void]$hostProcess.WaitForExit(5000)};$hostProcess.Dispose()}catch{};$resources=$false}
    if($owned){
        # 守护进程挂起时也必须写出未确认状态并恢复本机环境；stop参数不是客户端的时间上限。
        try{
            $stopped=InvokeDocker @('stop','--time','5',$container) 8
            $remaining=InvokeDocker @('ps','-a','--filter',"name=^/$container$",'--format','{{.Names}}') 5
            if($remaining.exitCode-ne 0-or ![string]::IsNullOrWhiteSpace($remaining.output)){$resources=$false}
        }catch{$resources=$false;if(!$failure){$failure='本批临时容器退出状态未确认'}}
    }
    $after=Sources;$drift=($before|ConvertTo-Json -Depth 5 -Compress)-cne ($after|ConvertTo-Json -Depth 5 -Compress)
    Save @{passed=($passed-and $resources-and !$drift);normalHostRuns=$hostRuns;resourcesClosed=$resources;sourceDrift=$drift;failure=$failure;providerCalls=0;selectorMode=$replayMode;container=$container;phase=$phase} (Join-Path $directory 'results.json')
    foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')};Pop-Location
}
Write-Host "证据：$directory"
if(!$passed-or !$resources-or $drift){exit 1}
Write-Host '受控查证正常关闭；临时库退出；新模型调用0次。';exit 0
