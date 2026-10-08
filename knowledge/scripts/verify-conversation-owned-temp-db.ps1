param(
    [Parameter(Mandatory=$true)][ValidateSet('migration','owned','http','4a','regression','no-db','build')][string]$Mode,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]+$')][string]$Phase,
    [string]$Tests='',
    [ValidateRange(1024,65535)][int]$Port=55436,
    [ValidateSet(19,20)][int]$ExpectedMigration=20
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$evidenceRoot=Join-Path $root 'docs/evidence/milestone-3b/2026-10-05/B/b2-b5'
$directory=Join-Path $evidenceRoot $Phase
$leasePath=Join-Path $evidenceRoot 'lease.json'
$boardPath=Join-Path $root 'docs/plans/agent-sync/2026-10-05-4a-3b/BOARD.md'
if(Test-Path -LiteralPath $directory){throw '证据目录已存在，重试必须另命名，不能覆盖失败'}
if(Test-Path -LiteralPath $leasePath){
    $oldLease=Get-Content -Raw -LiteralPath $leasePath|ConvertFrom-Json
    if($oldLease.status -eq 'ACTIVE'){throw '全局lease仍被占用'}
}
if((Get-Content -Raw -LiteralPath $boardPath) -match '\| lease \| \*\*ACTIVE'){throw 'BOARD已有ACTIVE lease'}
if($Port -eq 54329){throw '拒绝演示库端口'}
$selected=@($Tests.Split(',')|Where-Object{$_}|ForEach-Object{$_.Trim()})
if($Mode -ne 'build' -and !$selected.Count){throw '测试必须显式选类'}
if(@($selected|Select-Object -Unique).Count -ne $selected.Count){throw '测试选择不能重复'}
foreach($selector in $selected){
    if($selector -notmatch '^[A-Za-z][A-Za-z0-9]*Test(#[A-Za-z][A-Za-z0-9]*)?$' -or $selector -match 'LiveProvider|DashScope|RealModel'){
        throw '拒绝任意参数、供应商或通配符选类'
    }
    $class=($selector -split '#')[0]
    $found=@(rg --files (Join-Path $root 'knowledge/src/test/java') (Join-Path $root 'agent/src/test/java') (Join-Path $root 'agent-runtime/src/test/java')|
        Where-Object{[IO.Path]::GetFileName($_) -eq "$class.java"})
    if($found.Count -ne 1){throw '选定测试源码必须唯一存在'}
}
if($Mode -eq 'owned'){
    $gate=Join-Path $evidenceRoot 'migration-isolation-green/results.json'
    if(!(Test-Path -LiteralPath $gate) -or (Get-Content -Raw -LiteralPath $gate|ConvertFrom-Json).passed -ne $true){
        throw 'owned真库写入前必须有本轮迁移和匿名隔离GREEN'
    }
}
New-Item -ItemType Directory -Path $directory|Out-Null
function SaveJson($value,[string]$name){$value|ConvertTo-Json -Depth 9|Set-Content -Encoding utf8 -LiteralPath (Join-Path $directory $name)}
function Snapshot {
    $sourceRoots=@('agent-runtime/src','agent/src','knowledge/src','knowledge/scripts')|ForEach-Object{Join-Path $root $_}
    $paths=@(rg --files @sourceRoots)
    $paths+=@('pom.xml','agent-runtime/pom.xml','agent/pom.xml','knowledge/pom.xml','AGENTS.md')|ForEach-Object{Join-Path $root $_}
    @($paths|Sort-Object -Unique|ForEach-Object{
        [ordered]@{path=[IO.Path]::GetRelativePath($root,$_).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_).Hash.ToLowerInvariant()}
    })
}
function BoardLease([string]$text){
    $board=Get-Content -Raw -LiteralPath $boardPath
    [regex]::Replace($board,'(?m)^\| lease \|.*$',('| lease | '+$text+' |'))|Set-Content -Encoding utf8 -LiteralPath $boardPath
}
$before=Snapshot
SaveJson $before 'source-before.json'
function GitState([string]$tag){
    Push-Location $root
    try{
        $currentHead=git rev-parse HEAD
        if($LASTEXITCODE -ne 0){throw 'HEAD读取失败'}
        $dirty=git -c core.quotePath=false status --porcelain=v1 --untracked-files=all
        if($LASTEXITCODE -ne 0){throw '完整dirty状态读取失败'}
        $dirtyPath=Join-Path $directory ("git-status-$tag.txt")
        $dirty|Set-Content -Encoding utf8 -LiteralPath $dirtyPath
        SaveJson ([ordered]@{observedAt=([DateTimeOffset]::Now).ToString('o');head=$currentHead;
            dirtySha256=(Get-FileHash -LiteralPath $dirtyPath).Hash.ToLowerInvariant()}) ("git-$tag.json")
        return $currentHead
    }finally{Pop-Location}
}
$headBefore=GitState 'before'
$started=[DateTimeOffset]::Now
$lease=[ordered]@{owner='B';stage=$Phase;status='ACTIVE';writersPaused=$true;aPausedBy='A/19-b-handoff.md';
    selectedClasses=$selected;startedAt=$started.ToString('o');sourceManifest="$Phase/source-before.json";providerAllowed=$false}
$lease|ConvertTo-Json -Depth 6|Set-Content -Encoding utf8 -LiteralPath $leasePath
BoardLease "**ACTIVE / B / $Phase**；A/B源码writer均暂停；source-before.json及选类已保存"
$javaBefore=@(Get-Process java -ErrorAction SilentlyContinue|ForEach-Object Id)
$db=$Mode -in @('migration','owned','http','4a','regression')
$container='zhku-3b-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$database='zhkuagent_run_archive_e2e'
$saved=@{}
$names=@('POSTGRES_PASSWORD','ZHKU_DB_PASSWORD','ZHKU_DB_URL','ZHKU_DB_USER')
foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$knownSecrets=@()
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY')){
    foreach($scope in @('Process','User','Machine')){
        $value=[Environment]::GetEnvironmentVariable($name,$scope)
        if(![string]::IsNullOrWhiteSpace($value)){$knownSecrets+=$value}
    }
}
$secret=$null;$running=$false;$exitCode=0;$safeError=$null;$migration=$null
$resourcesVerified=$true
function SafeText([string]$text){
    foreach($value in $knownSecrets){$text=$text.Replace($value,'[REDACTED]')}
    if($secret){$text=$text.Replace($secret,'[REDACTED]')}
    $text -replace '\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}','[REDACTED_HASH]' -replace 'Synthetic-[0-9a-fA-F-]{36}','[REDACTED_TEST_PASSWORD]'
}
$command=[ordered]@{owner='B';mode=$Mode;phase=$Phase;selectedClasses=$selected;database=$db;provider=$false;
    startedAt=$started.ToString('o');runnerSha256=(Get-FileHash -LiteralPath $PSCommandPath).Hash.ToLowerInvariant();
    writersPaused=$true;expectedMigration=$ExpectedMigration;credentialTransport='fresh process/user/machine check; disposable secret only environment'}
SaveJson $command 'command.json'
try{
    # 部分无库装配测试仅构造受控DataSource，也需重新继承已配置环境；不发连接、不打印值。
    if(!$db){ & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase }
    if($db){
        & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
        $secret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
        $env:POSTGRES_PASSWORD=$secret
        docker run --rm -d --name $container --publish "127.0.0.1:${Port}:5432" --env POSTGRES_PASSWORD `
            --env POSTGRES_USER=zhkuagent --env "POSTGRES_DB=$database" pgvector/pgvector:0.8.6-pg16-bookworm|Out-Null
        if($LASTEXITCODE -ne 0){throw '临时容器启动失败'}
        $running=$true
        [Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD',$saved['POSTGRES_PASSWORD'],'Process')
        $ready=$false
        for($attempt=0;$attempt -lt 30;$attempt++){
            & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
            docker exec $container pg_isready -U zhkuagent -d $database 2>$null|Out-Null
            if($LASTEXITCODE -eq 0){$ready=$true;break};Start-Sleep -Seconds 1
        }
        if(!$ready){throw '临时库30秒启动预算耗尽'}
        $env:ZHKU_DB_PASSWORD=$secret;$env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:$Port/$database";$env:ZHKU_DB_USER='zhkuagent'
        & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
    }
    $maven='D:/Software/Maven/apache-maven-3.9.15/bin/mvn.cmd'
    if(!(Test-Path -LiteralPath $maven)){throw 'Maven运行边界不可访问'}
    $arguments=@('-e','-Dmaven.repo.local=C:/Users/nangua/.m2/repository','-Dfile.encoding=UTF-8','-pl','agent,knowledge','-am')
    if($Mode -eq 'build'){$arguments+=@('-DskipTests','package')}
    else{$arguments+=@("-Dtest=$($selected -join ',')",'-Dsurefire.failIfNoSpecifiedTests=false')
        if($db){$arguments+=@("-Dzhku.db.url=$env:ZHKU_DB_URL",'-Dzhku.db.user=zhkuagent')};$arguments+='test'}
    SaveJson $arguments 'maven-arguments.json'
    Push-Location $root
    try{
        & $maven @arguments 2>&1|ForEach-Object{SafeText $_.ToString()}|Tee-Object -FilePath (Join-Path $directory 'maven.log')|Out-Null
        $exitCode=$LASTEXITCODE
    }finally{Pop-Location}
    if($db){
        & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
        $versions=@(docker exec $container psql -U zhkuagent -d $database -Atc 'SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL')
        if($LASTEXITCODE -ne 0){throw '最高迁移读取失败'}
        $migration=$versions[-1]
        if($migration -ne [string]$ExpectedMigration -and $exitCode -eq 0){throw '最高迁移不符'}
        & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
        $connections=@(docker exec $container psql -U zhkuagent -d $database -Atc "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND backend_type='client backend' AND pid<>pg_backend_pid()")
        if($LASTEXITCODE -ne 0 -or $connections[-1] -ne '0'){$resourcesVerified=$false}
        SaveJson ([ordered]@{target=$env:ZHKU_DB_URL;migration=$migration;connectionsAfterTests=$connections[-1];synthetic=$true;container=$container}) 'database.json'
    }
}catch{
    $exitCode=1;$safeError=$_.Exception.GetType().Name
    SaveJson ([ordered]@{exceptionType=$safeError;detail=SafeText $_.Exception.Message}) 'environment-failure.json'
}finally{
    if($running){
        & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
        docker stop --time 10 $container|Out-Null
        if($LASTEXITCODE -ne 0){$resourcesVerified=$false}
        & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
        $remaining=@(docker ps -a --filter "name=^/$container$" --format '{{.Names}}')
        if($LASTEXITCODE -ne 0 -or $remaining.Count){$resourcesVerified=$false}
    }
    foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
$xmlDirectory=Join-Path $directory 'xml';New-Item -ItemType Directory -Path $xmlDirectory|Out-Null
$suiteResults=@();$missing=@()
foreach($selector in $selected){
    $class=($selector -split '#')[0]
    $reportRoots=@('agent-runtime','agent','knowledge')|ForEach-Object{Join-Path $root ($_+'/target/surefire-reports')}
    $fresh=@(Get-ChildItem -Path $reportRoots -Filter "TEST-*.$class.xml" -File -ErrorAction SilentlyContinue|
        Where-Object{$_.LastWriteTimeUtc -ge $started.UtcDateTime.AddSeconds(-1)})
    if($fresh.Count -ne 1){$missing+=$selector;continue}
    $sanitized=SafeText (Get-Content -Raw -LiteralPath $fresh[0].FullName)
    [xml]$xml=$sanitized
    # 环境properties与自由stdout/stderr不属于用例证据；保留结构化计数、名称和失败事实。
    foreach($node in @($xml.SelectNodes('//properties|//system-out|//system-err'))){[void]$node.ParentNode.RemoveChild($node)}
    $sanitized=$xml.OuterXml
    $destination=Join-Path $xmlDirectory $fresh[0].Name
    $sanitized|Set-Content -Encoding utf8 -LiteralPath $destination
    $suiteResults+=[ordered]@{selector=$selector;name=$xml.testsuite.name;tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;
        errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped;xml=$fresh[0].Name;sha256=(Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant()}
}
$after=Snapshot;SaveJson $after 'source-after.json'
$headAfter=GitState 'after'
$beforeMap=@{};foreach($file in $before){$beforeMap[$file.path]=$file.sha256}
$drift=@($after|Where-Object{!$beforeMap.ContainsKey($_.path) -or $beforeMap[$_.path] -ne $_.sha256})
if($after.Count -ne $before.Count){$drift+='FILE_COUNT_CHANGED'}
if($headBefore -ne $headAfter){$drift+='HEAD_CHANGED'}
$newJava=@(Get-Process java -ErrorAction SilentlyContinue|Where-Object{$_.Id -notin $javaBefore}|ForEach-Object Id)
if($newJava.Count){$resourcesVerified=$false}
$testCount=($suiteResults|ForEach-Object{$_.tests}|Measure-Object -Sum).Sum
$bad=@($suiteResults|Where-Object{$_.tests -lt 1 -or $_.failures -or $_.errors -or $_.skipped})
$passed=$exitCode -eq 0 -and !$missing.Count -and !$bad.Count -and !$drift.Count -and $resourcesVerified
if($Mode -eq 'build'){$passed=$exitCode -eq 0 -and !$drift.Count -and $resourcesVerified}
$result=[ordered]@{phase=$Phase;mode=$Mode;passed=$passed;exitCode=$exitCode;tests=$testCount;suites=$suiteResults;missingXml=$missing;
    sourceDrift=$drift.Count;newJavaRemaining=$newJava;resourcesVerified=$resourcesVerified;safeError=$safeError;endedAt=([DateTimeOffset]::Now).ToString('o')}
SaveJson $result 'results.json'
foreach($probeKind in @('conversation-process-probes','controlled-process-probes','campus-archive-process-probes')){
    $probeRoot=Join-Path $root "knowledge/target/$probeKind"
    if(Test-Path -LiteralPath $probeRoot){
        foreach($probe in @(Get-ChildItem -LiteralPath $probeRoot -Filter 'phase.json' -File -Recurse|Where-Object{$_.LastWriteTimeUtc -ge $started.UtcDateTime.AddSeconds(-1)})){
            $probeDestination=Join-Path $directory ($probeKind+'/'+$probe.Directory.Name)
            New-Item -ItemType Directory -Path $probeDestination -Force|Out-Null
            SafeText (Get-Content -Raw -LiteralPath $probe.FullName)|Set-Content -Encoding utf8 -LiteralPath (Join-Path $probeDestination 'phase.json')
        }
    }
}
if($resourcesVerified){$lease.status='RELEASED';$lease.endedAt=([DateTimeOffset]::Now).ToString('o');BoardLease "**NONE**；B/${Phase}已结束，源码漂移=$($drift.Count)，实际资源退出已检查"}
else{$lease.status='ACTIVE';BoardLease "**ACTIVE / B / $Phase**；资源退出未确认，不释放lease、不恢复源码writer"}
$lease|ConvertTo-Json -Depth 6|Set-Content -Encoding utf8 -LiteralPath $leasePath
$secret=$null;$knownSecrets=@()
Write-Output "B_STAGE phase=$Phase passed=$passed tests=$testCount exit=$exitCode drift=$($drift.Count) missing=$($missing.Count) resources=$resourcesVerified"
if(!$passed){exit 1};exit 0
