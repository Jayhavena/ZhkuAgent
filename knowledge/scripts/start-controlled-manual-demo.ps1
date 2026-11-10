#requires -Version 7.0
param(
    [ValidateRange(1024, 65535)][int]$DatabasePort = 55438,
    [ValidateRange(1, 30)][int]$MaxMinutes = 30,
    [switch]$Verify,
    [switch]$Smoke,
    [switch]$CheckEnvironment,
    [string]$Maven = 'D:/Software/Maven/apache-maven-3.9.15/bin/mvn.cmd'
)
$ErrorActionPreference = 'Stop'
# Java菜单明确输出UTF-8；Console编码管显示/原生stdout解码，OutputEncoding管原生stdin，三者必须一致。
# 不使用7.7才引入的PSApplicationOutputEncoding，当前本机是7.6.5。
$demoUtf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $demoUtf8
[Console]::OutputEncoding = $demoUtf8
$OutputEncoding = $demoUtf8
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$credentialImporter = Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
$evidenceRoot = Join-Path $root 'docs/evidence/manual-demo/2026-10-05'
$leasePath = Join-Path $evidenceRoot 'lease.json'
$board = Join-Path $root 'docs/plans/agent-sync/2026-10-05-4a-3b/BOARD.md'
if ($CheckEnvironment) {
    # 包装入口的无副作用检查：不连接数据库、不取得lease，也不启动模型或Maven。
    [ordered]@{powerShellVersion=$PSVersionTable.PSVersion.ToString();
        powerShellExecutable=(Join-Path $PSHOME 'pwsh.exe');
        mavenAvailable=(Test-Path -LiteralPath $Maven);
        javaAvailable=($null -ne (Get-Command java.exe -ErrorAction SilentlyContinue));
        dockerAvailable=($null -ne (Get-Command docker.exe -ErrorAction SilentlyContinue));
        consoleInputCodePage=[Console]::InputEncoding.CodePage;consoleOutputCodePage=[Console]::OutputEncoding.CodePage;
        pipelineOutputCodePage=$OutputEncoding.CodePage;encodingSample='中文菜单：登录、退出';
        requiresRipgrep=$false;databaseOperations=0;providerCalls=0} | ConvertTo-Json
    exit 0
}
if ($DatabasePort -eq 54329) { throw '拒绝使用原演示库端口54329' }
if ($Verify -and $Smoke) { throw 'Verify和Smoke须分开执行，保持证据类型明确' }
# 临时库入口只拉起引擎，不启动或复用共享演示库；CheckEnvironment 仍保持无副作用。
. (Join-Path $root 'scripts/ZhkuDocker.ps1')
Ensure-ZhkuDocker -TimeoutSeconds 180
if (!(Test-Path -LiteralPath $Maven)) { throw 'Maven路径不存在，可通过-Maven指定本机已安装位置' }
if ((Test-Path -LiteralPath $board) -and (Get-Content -Raw -LiteralPath $board) -match '\| lease \| \*\*ACTIVE') { throw '旧共同lease仍占用，不能并行构建' }
if (Test-Path -LiteralPath $leasePath) {
    if ((Get-Content -Raw -LiteralPath $leasePath | ConvertFrom-Json).status -eq 'ACTIVE') { throw '人工演示lease已占用' }
}
# 只检查类型，不输出命令行；不同数据库端口也不能避免Maven覆写同一target。
$processes = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'")
if (@($processes | Where-Object { $_.CommandLine -match 'plexus.classworlds|maven.multiModuleProjectDirectory|ControlledManualDemo|ConversationHttpRestartProbe' }).Count) {
    throw '发现正在运行的构建/演示进程，先关闭后再启动'
}
$phase = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$directory = Join-Path $evidenceRoot $phase
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$started = [DateTimeOffset]::Now
$selected = if ($Verify) {
    @('ControlledManualDemoTest','CampusRunAccessServiceTest','ControlledPreviewConfigurationTest',
      'ControlledConversationControllerTest','LegacyPreviewSecurityCompatibilityTest','ControlledCampusHttpAcceptanceTest','ConversationHttpRestartTest')
} else { @('ControlledManualDemoTest#exportTestClasspath') }
function SaveJson($value, [string]$name) {
    $value | ConvertTo-Json -Depth 9 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $directory $name)
}
function SourceSnapshot {
    $sourceRoots = @('agent-runtime/src','agent/src','knowledge/src','knowledge/scripts','scripts') | ForEach-Object { Join-Path $root $_ }
    # 人工终端没有Codex注入的rg；文件清单使用内置枚举，避免多一个CLI安装条件。
    $files = @($sourceRoots | ForEach-Object { Get-ChildItem -LiteralPath $_ -Recurse -File } | ForEach-Object FullName)
    $files += @('pom.xml','agent-runtime/pom.xml','agent/pom.xml','knowledge/pom.xml','AGENTS.md') | ForEach-Object { Join-Path $root $_ }
    @($files | Sort-Object -Unique | ForEach-Object {
        [ordered]@{ path=[IO.Path]::GetRelativePath($root, $_).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $_).Hash.ToLowerInvariant() }
    })
}
$before = SourceSnapshot
SaveJson $before 'source-before.json'
$lease = [ordered]@{ owner='A/manual-demo'; status='ACTIVE'; writersPaused=$true; selectedClasses=$selected;
    startedAt=$started.ToString('o'); sourceManifest="$phase/source-before.json"; providerAllowed=$false }
$lease | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $leasePath
SaveJson ([ordered]@{ mode=$(if($Verify){'verify'}elseif($Smoke){'scripted-smoke'}else{'manual'});
    startedAt=$started.ToString('o'); databasePort=$DatabasePort; maxMinutes=$MaxMinutes; expectedMigration=20;
    provider=$false; selectedClasses=$selected; credentialTransport='temporary process environment only';
    runnerSha256=(Get-FileHash -LiteralPath $PSCommandPath).Hash.ToLowerInvariant() }) 'command.json'
$saved = @{}
foreach ($name in @('ZHKU_DB_PASSWORD','ZHKU_DB_URL','ZHKU_DB_USER','POSTGRES_PASSWORD','DASHSCOPE_API_KEY','ZHKU_MANUAL_DEMO_MINUTES')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$container = 'zhku-manual-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$database = 'zhkuagent_run_archive_e2e'
$secret = $null
$knownSecrets = @()
foreach ($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY')) {
    foreach ($scope in @('Process','User','Machine')) {
        $value = [Environment]::GetEnvironmentVariable($name, $scope)
        if (![string]::IsNullOrWhiteSpace($value)) { $knownSecrets += $value }
    }
}
function SafeText([string]$text) {
    foreach ($value in $knownSecrets) { $text = $text.Replace($value, '[REDACTED]') }
    if ($secret) { $text = $text.Replace($secret, '[REDACTED]') }
    $text -replace '\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}','[REDACTED_HASH]' -replace 'Synthetic-[0-9a-fA-F-]{36}','[REDACTED_TEST_PASSWORD]'
}
$exitCode = 1; $resourcesVerified = $true; $containerStarted = $false; $mavenExit = $null; $demoExit = $null
$javaBefore = @(Get-Process java -ErrorAction SilentlyContinue | ForEach-Object Id)
$reports = @(); $safeError = $null; $migration = $null
Push-Location $root
try {
    & $credentialImporter -UseDatabase
    # 验证流程使用随机一次性口令；既有本机口令不复用到临时库，也不进入docker命令参数。
    $secret = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
    $env:POSTGRES_PASSWORD = $secret
    & $credentialImporter -UseDatabase
    docker run --rm -d --name $container --publish "127.0.0.1:${DatabasePort}:5432" --env POSTGRES_PASSWORD `
        --env POSTGRES_USER=zhkuagent --env "POSTGRES_DB=$database" pgvector/pgvector:0.8.6-pg16-bookworm | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '专用临时库启动失败' }
    $containerStarted = $true
    [Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD', $saved['POSTGRES_PASSWORD'], 'Process')
    $ready = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        & $credentialImporter -UseDatabase
        docker exec $container pg_isready -U zhkuagent -d $database 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw '临时库30秒启动预算耗尽' }
    $env:ZHKU_DB_PASSWORD=$secret; $env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/$database"; $env:ZHKU_DB_USER='zhkuagent'
    $env:DASHSCOPE_API_KEY=$null; $env:ZHKU_MANUAL_DEMO_MINUTES=[string]$MaxMinutes
    & $credentialImporter -RequireDatabase
    $arguments = @('-q','-Dmaven.repo.local=C:/Users/nangua/.m2/repository','-Dfile.encoding=UTF-8','-pl','knowledge','-am',
        "-Dtest=$($selected -join ',')",'-Dsurefire.failIfNoSpecifiedTests=false',"-Dzhku.db.url=$env:ZHKU_DB_URL",'-Dzhku.db.user=zhkuagent','test')
    SaveJson $arguments 'maven-arguments.json'
    Write-Host '正在编译测试专用入口；执行期间请暂停其他会话写入或构建。'
    & $Maven @arguments 2>&1 | ForEach-Object { SafeText $_.ToString() } | Tee-Object -FilePath (Join-Path $directory 'maven.log') | Out-Null
    $mavenExit = $LASTEXITCODE
    $xmlDirectory = Join-Path $directory 'xml'; New-Item -ItemType Directory -Path $xmlDirectory | Out-Null
    foreach ($selector in $selected) {
        $class = ($selector -split '#')[0]
        $fresh = @(Get-ChildItem (Join-Path $root 'knowledge/target/surefire-reports') -Filter "TEST-*.$class.xml" |
            Where-Object { $_.LastWriteTimeUtc -ge $started.UtcDateTime.AddSeconds(-1) })
        if ($fresh.Count -ne 1) {
            if ($mavenExit -eq 0) { throw '缺本轮唯一XML，不能使用历史结果' }
            continue # 编译/准备失败可能没有XML；不会伪造用例数量，原失败日志保留。
        }
        [xml]$xml = SafeText (Get-Content -Raw -LiteralPath $fresh[0].FullName)
        foreach ($node in @($xml.SelectNodes('//properties|//system-out|//system-err'))) { [void]$node.ParentNode.RemoveChild($node) }
        $xml.Save((Join-Path $xmlDirectory $fresh[0].Name))
        $reports += [ordered]@{ selector=$selector; tests=[int]$xml.testsuite.tests; failures=[int]$xml.testsuite.failures;
            errors=[int]$xml.testsuite.errors; skipped=[int]$xml.testsuite.skipped; xml=$fresh[0].Name }
    }
    # 失败也先归档本轮XML和计数，不能把已执行的失败组写成0项或丢掉原证据。
    if ($mavenExit -ne 0) { throw '编译或选定测试失败；本轮结果保留，未启动人工服务' }
    if (!$Verify) {
        $classpath = (Get-Content -Raw -LiteralPath (Join-Path $root 'knowledge/target/manual-demo/classpath.txt')).Trim()
        $java = (Get-Command java.exe -ErrorAction Stop).Source
        & $credentialImporter -RequireDatabase
        Write-Host '已启动前台人工演示：先输入1登录；h查看菜单；q退出。业务模型仅为测试替身。'
        $javaArguments = @('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',
            '-cp',$classpath,'com.zhku.knowledge.controlled.ControlledManualDemo')
        if ($Smoke) { $javaArguments += '--smoke' }
        # 不重定向交互输入，不保存完整HTTP答复或模型Prompt；账户凭据只在Java内存中。
        & $java @javaArguments
        $demoExit = $LASTEXITCODE
        if ($demoExit -ne 0) { throw '演示或实际资源关闭失败' }
    }
    & $credentialImporter -RequireDatabase
    $versions = @(docker exec $container psql -U zhkuagent -d $database -Atc 'SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL')
    if ($LASTEXITCODE -ne 0 -or $versions[-1] -ne '20') { throw '最高迁移不是V20' }
    $migration = $versions[-1]
    & $credentialImporter -RequireDatabase
    $connections = @(docker exec $container psql -U zhkuagent -d $database -Atc "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND backend_type='client backend' AND pid<>pg_backend_pid()")
    if ($LASTEXITCODE -ne 0 -or $connections[-1] -ne '0') { throw '数据库仍有未退出连接' }
    SaveJson ([ordered]@{ container=$container; migration=$migration; connectionsAfter=$connections[-1]; syntheticOnly=$true }) 'database.json'
    $exitCode=0
} catch {
    $safeError=$_.Exception.GetType().Name
    SaveJson ([ordered]@{ type=$safeError; detail=(SafeText $_.Exception.Message) }) 'failure.json'
    Write-Host '本轮未通过；查看本轮failure.json，未将环境/编译错误当作产品RED。'
} finally {
    if ($containerStarted) {
        & $credentialImporter -UseDatabase
        docker stop --time 10 $container | Out-Null
        if ($LASTEXITCODE -ne 0) { $resourcesVerified=$false }
        & $credentialImporter -UseDatabase
        $remaining = @(docker ps -a --filter "name=^/$container$" --format '{{.Names}}')
        if ($LASTEXITCODE -ne 0 -or $remaining.Count) { $resourcesVerified=$false }
    }
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    Pop-Location
}
$after = SourceSnapshot; SaveJson $after 'source-after.json'
$beforeMap=@{}; foreach ($file in $before) { $beforeMap[$file.path]=$file.sha256 }
$drift=@($after | Where-Object { !$beforeMap.ContainsKey($_.path) -or $beforeMap[$_.path] -ne $_.sha256 })
$javaRemaining=@(Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Id -notin $javaBefore } | ForEach-Object Id)
if ($javaRemaining.Count) { $resourcesVerified=$false }
$bad=@($reports | Where-Object { $_.tests -lt 1 -or $_.failures -or $_.errors -or $_.skipped })
$passed=$exitCode -eq 0 -and !$bad.Count -and !$drift.Count -and $before.Count -eq $after.Count -and $resourcesVerified
SaveJson ([ordered]@{ passed=$passed; mavenExit=$mavenExit; demoExit=$demoExit; suites=$reports;
    testExecutions=($reports.tests | Measure-Object -Sum).Sum; classpathBootstrapTests=1; databaseMigration=$migration;
    sourceDrift=$drift.Count; resourcesVerified=$resourcesVerified; javaRemaining=$javaRemaining; safeError=$safeError;
    provider=$false; endedAt=[DateTimeOffset]::Now.ToString('o') }) 'results.json'
if ($resourcesVerified) { $lease.status='RELEASED'; $lease.writersPaused=$false }
$lease.endedAt=[DateTimeOffset]::Now.ToString('o')
$lease | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $leasePath
$secret=$null; $knownSecrets=@()
Write-Host "MANUAL_DEMO_RESULT passed=$passed resources=$resourcesVerified evidence=$directory"
if (!$passed) { exit 1 }
