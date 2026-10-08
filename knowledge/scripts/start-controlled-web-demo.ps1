#requires -Version 7.0
param(
    [ValidateRange(1024,65535)][int]$DatabasePort=55439,
    [ValidateRange(1024,65535)][int]$BackendPort=58123,
    [ValidateRange(1024,65535)][int]$WebPort=58080,
    [switch]$LiveAcceptance,
    [switch]$SmokeBackend,
    [switch]$SmokeWeb,
    [switch]$CheckEnvironment,
    [string]$Maven='D:/Software/Maven/apache-maven-3.9.15/bin/mvn.cmd'
)
$ErrorActionPreference='Stop'
$demoUtf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$demoUtf8; [Console]::OutputEncoding=$demoUtf8; $OutputEncoding=$demoUtf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$importer=Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
. (Join-Path $root 'scripts/Resolve-ZhkuNode.ps1')
if ($CheckEnvironment) {
    $nodeAvailable=$true; try {$nodeTools=Get-ZhkuNodeToolchain} catch {$nodeAvailable=$false}
    [ordered]@{powerShell=$PSVersionTable.PSVersion.ToString();javaAvailable=($null -ne (Get-Command java.exe -ErrorAction SilentlyContinue));
        mavenAvailable=(Test-Path -LiteralPath $Maven);dockerAvailable=($null -ne (Get-Command docker.exe -ErrorAction SilentlyContinue));
        nodeAndNpmAvailable=$nodeAvailable;encodingSample='受控校园：正常退出';encoding=[Console]::OutputEncoding.CodePage;databaseOperations=0;providerCalls=0;requiresRipgrep=$false} | ConvertTo-Json
    exit 0
}
if ($DatabasePort -eq 54329 -or @($DatabasePort,$BackendPort,$WebPort | Select-Object -Unique).Count -ne 3) { throw '禁止原演示库端口或端口混用' }
if (@($LiveAcceptance,$SmokeBackend,$SmokeWeb | Where-Object {$_}).Count -gt 1) { throw '真实样本、后端检查、网页检查必须分开，不能混用证据' }
if (!(Test-Path -LiteralPath $Maven)) { throw 'Maven路径不存在，请使用-Maven指定已安装版本' }
if(!$LiveAcceptance -and !$SmokeBackend){$nodeTools=Get-ZhkuNodeToolchain}
$evidenceRoot=Join-Path $root 'docs/evidence/controlled-web-demo/2026-10-06'
$leasePath=Join-Path $evidenceRoot 'lease.json'
foreach ($other in @($leasePath,(Join-Path $evidenceRoot 'setup-tests/lease.json'),(Join-Path $root 'docs/evidence/manual-demo/2026-10-05/lease.json'))) {
    if ((Test-Path -LiteralPath $other) -and ((Get-Content -Raw -LiteralPath $other | ConvertFrom-Json).status -eq 'ACTIVE')) { throw '测试或演示lease仍占用' }
}
$javaBefore=@(Get-Process java -ErrorAction SilentlyContinue | ForEach-Object Id)
if (@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
    $command=$_.CommandLine
    $demoMain=$command -match '(?:-Dloader\.main=|\s)\S*\.(?:ControlledWebDemoHost|ControlledLiveModelAcceptance|ControlledManualDemo|ConversationHttpRestartProbe)(?=[\s"]|$)'
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
    throw '已有构建/演示进程占用本仓库或目录无法确认，请正常退出后再运行；不会停止它。'
}
$phase=(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$directory=Join-Path $evidenceRoot $phase
New-Item -ItemType Directory -Path $directory -Force | Out-Null
function SaveJson($value,[string]$name) { $value | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $directory $name) }
function Snapshot {
    @(@('agent-runtime/src','agent/src','knowledge/src','knowledge/scripts','scripts') | ForEach-Object {
        Get-ChildItem -LiteralPath (Join-Path $root $_) -Recurse -File
    } | Sort-Object FullName | ForEach-Object { [ordered]@{path=[IO.Path]::GetRelativePath($root,$_.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName).Hash} })
}
$before=Snapshot; SaveJson $before 'source-before.json'
$lease=[ordered]@{owner='controlled-web-demo/main';status='ACTIVE';writersPaused=$true;providerAllowed=(!$SmokeBackend -and !$SmokeWeb);phase=$phase;startedAt=[DateTimeOffset]::Now.ToString('o')}
$lease | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $leasePath
SaveJson ([ordered]@{mode=$(if($LiveAcceptance){'live-acceptance'}elseif($SmokeBackend){'real-backend-start-close-only'}elseif($SmokeWeb){'real-web-start-restart-close-only'}else{'manual-real-web'});databasePort=$DatabasePort;backendPort=$BackendPort;webPort=$WebPort;
    expectedMigration=21;model='qwen-plus';denominator=$(if($LiveAcceptance){12}else{$null});startupSeconds=20;normalExitSeconds=10;forcedFailureCleanupSeconds=5;
    maxBatchSeconds=900;maxLogicalCallsPerRun=3;automaticPostRetries=0;webStartSeconds=40;webCloseSeconds=20;runnerSha256=(Get-FileHash $PSCommandPath).Hash}) 'command.json'
$names=@('ZHKU_DB_PASSWORD','ZHKU_DB_URL','POSTGRES_PASSWORD','DASHSCOPE_API_KEY','ZHKU_DEMO_USER_A_PASSWORD','ZHKU_DEMO_USER_B_PASSWORD','ZHKU_DEMO_MAINTAINER_PASSWORD','ZHKU_DEMO_PRODUCT_JAR','ZHKU_DEMO_EVIDENCE','ZHKU_DEMO_BACKEND_PORT')
$saved=@{}; foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$knownSecrets=@(); $accountPasswords=@{}
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY')) { foreach($scope in @('Process','User','Machine')) {
    $value=[Environment]::GetEnvironmentVariable($name,$scope); if (![string]::IsNullOrWhiteSpace($value)){$knownSecrets+=$value}
} }
function SafeText([string]$value) { foreach($secret in $knownSecrets){$value=$value.Replace($secret,'[REDACTED]')}; $value -replace '\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}','[REDACTED_HASH]' }
$container='zhku-web-demo-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$containerStarted=$false; $hostProcess=$null; $hostStdout=$null; $hostStderr=$null; $exitCode=1; $resources=$true; $hostReady=$false
$webOwned=$false; $webProject=$container
$useWeb=(!$LiveAcceptance -and !$SmokeBackend)
$java=(Get-Command java.exe -ErrorAction Stop).Source
$jar=Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar'
function InvokeWeb([string]$action,[int]$budgetSeconds) {
    $info=[Diagnostics.ProcessStartInfo]::new((Join-Path $PSHOME 'pwsh.exe'))
    $info.WorkingDirectory=$root; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=$demoUtf8; $info.StandardErrorEncoding=$demoUtf8
    foreach($argument in @('-NoProfile','-File',(Join-Path $root 'agent/frontend/start-nginx-preview.ps1'),'-Action',$action,'-BackendPort',[string]$BackendPort,'-WebPort',[string]$WebPort,'-ProjectName',$webProject,'-SkipBuild')){[void]$info.ArgumentList.Add($argument)}
    # 代理只需要端口和构建产物，不能继承数据库、供应商或合成账号凭据。
    foreach($name in $names){[void]$info.Environment.Remove($name)}
    $process=[Diagnostics.Process]::Start($info)
    $output=$process.StandardOutput.ReadToEndAsync(); $errorOutput=$process.StandardError.ReadToEndAsync()
    try {
        if(!$process.WaitForExit($budgetSeconds*1000)){
            $process.Kill($true); [void]$process.WaitForExit(5000)
            throw '网页启动/关闭预算耗尽，强制回收不能算通过'
        }
        (SafeText ($output.GetAwaiter().GetResult()+$errorOutput.GetAwaiter().GetResult())) | Set-Content -Encoding utf8 -LiteralPath (Join-Path $directory ("web-$action-$([DateTime]::Now.Ticks).log"))
        if($process.ExitCode -ne 0){throw '网页启动/关闭未确认'}
    } finally {$process.Dispose()}
}
function StartWeb {
    # 独立项目名只属于本次演示；即使启动确认未知，也只清理本次资源。
    $script:webOwned=$true
    InvokeWeb 'Start' 40
}
function StopWeb {
    if(!$webOwned){return}
    InvokeWeb 'Stop' 20
    $script:webOwned=$false
}
function NewJava([string]$main,[string[]]$arguments=@(),[switch]$TestClasses,[switch]$AccountEnvironment) {
    $info=[Diagnostics.ProcessStartInfo]::new($java)
    $info.WorkingDirectory=$root; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardInput=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=$demoUtf8; $info.StandardErrorEncoding=$demoUtf8; $info.StandardInputEncoding=$demoUtf8
    foreach($argument in @('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',"-Dloader.main=$main")){[void]$info.ArgumentList.Add($argument)}
    if($TestClasses){[void]$info.ArgumentList.Add('-Dloader.path='+ (Join-Path $root 'knowledge/target/test-classes'))}
    foreach($argument in @('-cp',$jar,'org.springframework.boot.loader.launch.PropertiesLauncher')+$arguments){[void]$info.ArgumentList.Add($argument)}
    if(!$AccountEnvironment){foreach($name in $accountPasswords.Keys){[void]$info.Environment.Remove($name)}}
    # 只短期将输入交给准备/验收子进程，长期服务不继承账号明文；不使用命令行传口令。
    else {foreach($name in $accountPasswords.Keys){$info.Environment[$name]=$accountPasswords[$name]}}
    [Diagnostics.Process]::Start($info)
}
function StopHost {
    if(!$hostProcess){return}
    if(!$hostProcess.HasExited){$hostProcess.StandardInput.WriteLine('STOP');$hostProcess.StandardInput.Flush()}
    if(!$hostProcess.WaitForExit(10000)){throw '正常关闭10秒预算耗尽，不能用强杀冒充通过'}
    $output=$hostStdout.GetAwaiter().GetResult(); $errorOutput=$hostStderr.GetAwaiter().GetResult()
    # 不保存完整产品输出，日志中只记录固定就绪/退出信号及是否仍有监听。
    if($hostProcess.ExitCode -ne 0 -or !$output.Contains('DEMO_HOST_CLOSED resources=true')) {throw '产品宿主未确认资源关闭'}
    $socket=[Net.Sockets.TcpClient]::new()
    try {$socket.Connect('127.0.0.1',$BackendPort);throw '产品宿主退出后监听仍存在'} catch [Net.Sockets.SocketException] {} finally {$socket.Dispose()}
    SaveJson ([ordered]@{pid=$hostProcess.Id;normalExit=$true;exitCode=$hostProcess.ExitCode;listenerClosed=$true}) ("host-exit-$($hostProcess.Id).json")
    $hostProcess.Dispose(); $script:hostProcess=$null
}
function StartHost {
    & $importer -RequireDatabase -RequireDashScope
    $script:hostReady=$false
    $script:hostProcess=NewJava 'com.zhku.knowledge.controlled.demo.ControlledWebDemoHost'
    $script:hostStderr=$hostProcess.StandardError.ReadToEndAsync(); $deadline=[DateTime]::UtcNow.AddSeconds(20)
    while([DateTime]::UtcNow -lt $deadline -and !$hostProcess.HasExited) {
        $lineTask=$hostProcess.StandardOutput.ReadLineAsync()
        while(!$lineTask.IsCompleted -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 50}
        if(!$lineTask.IsCompleted){throw '产品宿主20秒启动预算耗尽'}
        $line=$lineTask.GetAwaiter().GetResult()
        if($line -match '^DEMO_HOST_READY '){$script:hostReady=$true;break}
    }
    if(!$hostReady){throw '产品宿主未就绪'}
    $script:hostStdout=$hostProcess.StandardOutput.ReadToEndAsync()
}
Push-Location $root
try {
    & $importer -RequireDashScope -UseDatabase
    $dbSecret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(18)); $knownSecrets+=$dbSecret
    $env:POSTGRES_PASSWORD=$dbSecret
    & $importer -UseDatabase
    docker run --rm -d --name $container --publish "127.0.0.1:${DatabasePort}:5432" --env POSTGRES_PASSWORD --env POSTGRES_USER=zhkuagent --env POSTGRES_DB=zhkuagent_run_archive_e2e pgvector/pgvector:0.8.6-pg16-bookworm | Out-Null
    if($LASTEXITCODE -ne 0){throw '专用临时库启动失败'}; $containerStarted=$true
    $env:POSTGRES_PASSWORD=$saved['POSTGRES_PASSWORD']; $env:ZHKU_DB_PASSWORD=$dbSecret
    $env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/zhkuagent_run_archive_e2e"
    $ready=$false
    for($attempt=0;$attempt -lt 30;$attempt++) { & $importer -RequireDatabase; docker exec $container pg_isready -U zhkuagent -d zhkuagent_run_archive_e2e 2>$null | Out-Null
        if($LASTEXITCODE -eq 0){$ready=$true;break};Start-Sleep -Seconds 1 }
    if(!$ready){throw '临时库30秒启动预算耗尽'}
    $buildArguments=@('-q','-Dmaven.repo.local=C:/Users/nangua/.m2/repository','-Dfile.encoding=UTF-8','-pl','knowledge','-am','-DskipTests','package')
    SaveJson $buildArguments 'build-command.json'
    Write-Host '正在打包真实受控后端；不运行供应商测试。'
    # Maven不需要供应商凭据；只在启动真实宿主前重新读取并注入。
    $env:DASHSCOPE_API_KEY=$null
    & $Maven @buildArguments 2>&1 | ForEach-Object {SafeText $_.ToString()} | Set-Content -Encoding utf8 -LiteralPath (Join-Path $directory 'build.log')
    $buildExit=$LASTEXITCODE
    SaveJson ([ordered]@{exitCode=$buildExit;testsExecuted=$false;frontendBuilt=$false}) 'build-result.json'
    if($buildExit -ne 0){throw '后端打包失败'}
    if($useWeb){
        Push-Location (Join-Path $root 'agent/frontend')
        $nodePathBefore=$env:PATH
        try {
            $env:PATH=$nodeTools.Directory+';'+$nodePathBefore
            & $nodeTools.Npm run build 2>&1 | ForEach-Object {SafeText $_.ToString()} | Set-Content -Encoding utf8 -LiteralPath (Join-Path $directory 'frontend-build.log')
            if($LASTEXITCODE -ne 0){throw '前端构建失败'}
        } finally {$env:PATH=$nodePathBefore;Pop-Location}
        SaveJson ([ordered]@{exitCode=0;indexSha256=(Get-FileHash (Join-Path $root 'agent/frontend/dist/index.html')).Hash}) 'frontend-build.json'
    }
    # 保留调用者显式进程Key优先级；临时为构建清空不能悄悄换成另一个用户级Key。
    $env:DASHSCOPE_API_KEY=$saved['DASHSCOPE_API_KEY']
    & $importer -RequireDatabase -RequireDashScope
    foreach($name in @('ZHKU_DEMO_USER_A_PASSWORD','ZHKU_DEMO_USER_B_PASSWORD','ZHKU_DEMO_MAINTAINER_PASSWORD')) {
        if($LiveAcceptance -or $SmokeBackend -or $SmokeWeb){$plain='Synthetic-'+[guid]::NewGuid().ToString()}
        else {
            $secure=Read-Host ($name.Replace('ZHKU_DEMO_','').Replace('_PASSWORD','')+' 合成账号密码（16～64位英文字符，隐藏输入）') -AsSecureString
            $buffer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
            try {$plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer)} finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer);$secure.Dispose()}
        }
        if($plain.Length -lt 16 -or $plain.Length -gt 64 -or $plain -match '[^\x20-\x7e]'){throw '账号密码必须为16～64位可打印英文字符'}
        $accountPasswords[$name]=$plain; $knownSecrets+=$plain; $plain=$null
    }
    & $importer -RequireDatabase
    $setup=NewJava 'com.zhku.knowledge.controlled.demo.ControlledWebDemoSetupCli' -AccountEnvironment
    $setupOut=$setup.StandardOutput.ReadToEndAsync(); $setupError=$setup.StandardError.ReadToEndAsync()
    if(!$setup.WaitForExit(20000)){throw '准备工具20秒预算耗尽'}
    $setupOutput=$setupOut.GetAwaiter().GetResult(); $setupErrorOutput=$setupError.GetAwaiter().GetResult()
    if($setup.ExitCode -ne 0 -or !$setupOutput.Contains('DEMO_SETUP_READY')){
        if($setupErrorOutput.Contains('DEMO_SETUP_UNCONFIRMED')){throw '合成账号提交确认未知，先检查本次库；禁止自动重试'}
        throw '合成账号准备未获确认；禁止自动重试'
    }
    $setup.Dispose(); $setup=$null
    $env:ZHKU_DEMO_PRODUCT_JAR=$jar; $env:ZHKU_DEMO_EVIDENCE=$directory
    $env:ZHKU_DEMO_BACKEND_PORT=if($LiveAcceptance){'0'}else{[string]$BackendPort}
    SaveJson ([ordered]@{jarSha256=(Get-FileHash $jar).Hash;accounts=@('user-a','user-b','maintainer');passwords='hidden input / child environment only';migration=21;syntheticOnly=$true}) 'setup.json'
    if($LiveAcceptance) {
        & $importer -RequireDatabase -RequireDashScope
        $acceptance=NewJava 'com.zhku.knowledge.controlled.demo.ControlledLiveModelAcceptance' @('--live') -TestClasses -AccountEnvironment
        $outputTask=$acceptance.StandardOutput.ReadToEndAsync(); $errorTask=$acceptance.StandardError.ReadToEndAsync()
        if(!$acceptance.WaitForExit(900000)){throw '真实样本15分钟预算耗尽'}
        # 只输出固定摘要；完整HTTP/Prompt/供应商异常不保存。
        $output=$outputTask.GetAwaiter().GetResult(); $errorOutput=$errorTask.GetAwaiter().GetResult()
        $summary=@($output -split '\r?\n' | Where-Object {$_ -match '^LIVE_ACCEPTANCE_RESULT '})
        $summary | Set-Content -Encoding utf8 -LiteralPath (Join-Path $directory 'live-summary.log')
        $acceptanceExit=$acceptance.ExitCode; $acceptance.Dispose(); $acceptance=$null
        if($acceptanceExit -ne 0){throw '固定真实样本验收未通过，保留首次结果，不自动重跑'}
    } else {
        StartHost
        if($useWeb){StartWeb}
        if($SmokeBackend -or $SmokeWeb) {
            $client=[Net.Http.HttpClient]::new()
            try {
                $client.Timeout=[TimeSpan]::FromSeconds(5)
                $httpPort=if($SmokeWeb){$WebPort}else{$BackendPort}
                $reply=$client.GetAsync("http://127.0.0.1:$httpPort/api/zhku/controlled-preview/v1/auth/me").GetAwaiter().GetResult()
                try {if([int]$reply.StatusCode -ne 401){throw '启动检查匿名GET未被拒绝'}} finally {$reply.Dispose()}
                if($SmokeWeb){
                    $page=$client.GetAsync("http://127.0.0.1:$WebPort/campus/login").GetAwaiter().GetResult()
                    try {if([int]$page.StatusCode -ne 200 -or !$page.Content.ReadAsStringAsync().GetAwaiter().GetResult().Contains('id="app"')){throw '真实网页路由未就绪'}} finally {$page.Dispose()}
                    $firstPid=$hostProcess.Id
                    StopWeb; StopHost; StartHost; StartWeb
                    if($hostProcess.Id -eq $firstPid){throw '重启PID未变化'}
                    $reply=$client.GetAsync("http://127.0.0.1:$WebPort/api/zhku/controlled-preview/v1/auth/me").GetAwaiter().GetResult()
                    try {if([int]$reply.StatusCode -ne 401){throw '重启后代理未就绪'}} finally {$reply.Dispose()}
                    SaveJson ([ordered]@{firstPid=$firstPid;secondPid=$hostProcess.Id;route=200;anonymousBefore=401;anonymousAfter=401;providerCalls=0;browserTest=$false}) 'web-smoke.json'
                }
            } finally {$client.Dispose()}
            SaveJson ([ordered]@{anonymousGet=401;productionChatModelConfigured=$true;providerCalls=0;frontend=[bool]$SmokeWeb}) 'startup-smoke.json'
        } else {
            Write-Host "校园网页已就绪 http://127.0.0.1:$WebPort/campus/login"
            Write-Host '账号 user-a / user-b / maintainer 使用刚输入的密码。服务使用真实模型，仅输入合成资料。'
            Write-Host '输入 r 正常重启并保留本次库，须重新登录；q正常退出并删除本次临时库。不要直接关闭终端。'
            do {
                $command=Read-Host '校园网页演示'
                if($command -eq 'r'){StopWeb;StopHost;StartHost;StartWeb;Write-Host '本次库及账号保持，网页已恢复，服务已更换PID；旧登录失效，请重新登录。'}
            } while($command -ne 'q')
        }
        StopWeb; StopHost
    }
    & $importer -RequireDatabase
    $migration=@(docker exec $container psql -U zhkuagent -d zhkuagent_run_archive_e2e -Atc 'SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL')
    & $importer -RequireDatabase
    $connections=@(docker exec $container psql -U zhkuagent -d zhkuagent_run_archive_e2e -Atc "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND backend_type='client backend' AND pid<>pg_backend_pid()")
    if($LASTEXITCODE -ne 0 -or $migration[-1] -ne '21' -or $connections[-1] -ne '0'){throw '迁移或连接退出未确认'}
    SaveJson ([ordered]@{container=$container;migration=21;connectionsAfter=0;database='zhkuagent_run_archive_e2e'}) 'database.json'
    $exitCode=0
} catch {
    SaveJson ([ordered]@{category=$_.Exception.GetType().Name;message=(SafeText $_.Exception.Message)}) 'failure.json'
    Write-Host '本轮未通过；有限失败类别已保存，不自动再次调用供应商。'
} finally {
    if($webOwned){try {StopWeb} catch {$resources=$false}}
    if($hostProcess -and $hostReady -and !$hostProcess.HasExited){try {StopHost} catch {$resources=$false}}
    # 强制回收只记失败；不把WaitForExit或Future取消等同于正常资源关闭。
    foreach($process in @($setup,$acceptance,$hostProcess)) {
        if($process -and !$process.HasExited) { $resources=$false; $process.Kill($true); if(!$process.WaitForExit(5000)){$resources=$false} }
        if($process){$process.Dispose()}
    }
    if($containerStarted){ & $importer -RequireDatabase; docker stop --time 10 $container | Out-Null
        if($LASTEXITCODE -ne 0){$resources=$false}
        & $importer -UseDatabase; $remaining=@(docker ps -a --filter "name=^/$container$" --format '{{.Names}}')
        if($LASTEXITCODE -ne 0 -or $remaining.Count){$resources=$false} }
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
    $accountPasswords.Clear();$knownSecrets=@(); Pop-Location
}
$after=Snapshot; SaveJson $after 'source-after.json'
$map=@{};foreach($file in $before){$map[$file.path]=$file.sha256}
$drift=@($after | Where-Object {!$map.ContainsKey($_.path) -or $map[$_.path] -ne $_.sha256})
$javaRemaining=@(Get-Process java -ErrorAction SilentlyContinue | Where-Object {$_.Id -notin $javaBefore} | ForEach-Object Id)
$resources=$resources -and !$javaRemaining.Count
$passed=$exitCode -eq 0 -and $resources -and !$drift.Count -and $before.Count -eq $after.Count
SaveJson ([ordered]@{passed=$passed;exitCode=$exitCode;resourcesVerified=$resources;sourceDrift=$drift.Count;javaRemaining=$javaRemaining;providerAllowed=(!$SmokeBackend -and !$SmokeWeb);webProject=$webProject;webCleanupConfirmed=(!$webOwned)}) 'results.json'
# 没有遗留PID才释放；失败但已物理清理可释放，原失败保持失败。
if(!$javaRemaining.Count -and !$webOwned){$lease.status='RELEASED';$lease.writersPaused=$false}
$lease.endedAt=[DateTimeOffset]::Now.ToString('o');$lease | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $leasePath
Write-Host "CONTROLLED_BACKEND_RESULT passed=$passed resources=$resources evidence=$directory"
if(!$passed){exit 1}
