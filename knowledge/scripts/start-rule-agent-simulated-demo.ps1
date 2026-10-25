#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('CheckEnvironment','Prepare','Start','Stop','Recover')][string]$Action='CheckEnvironment',
    [string]$ExpectedIdentity='',
    [string]$AssetsRoot='',
    [string]$EvidenceRoot=(Join-Path $env:LOCALAPPDATA 'ZhkuAgent\controlled-acceptance\M1-04B'),
    [string]$PreparedDirectory='',
    [ValidatePattern('^m104b-[a-z0-9-]{8,80}$')][string]$Batch='m104b-20261020-demo01',
    [ValidateRange(1024,65535)][int]$DatabasePort=55441,
    [ValidateRange(1024,65535)][int]$BackendPort=58141,
    [ValidateRange(30,900)][int]$LeaseSeconds=600,
    [switch]$ApproveTemporaryDatabase
)
$ErrorActionPreference='Stop'
[Console]::InputEncoding=[Console]::OutputEncoding=$OutputEncoding=[Text.UTF8Encoding]::new($false)
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
# 2026-10-24迁移后外层旧目录只剩残缺docs副本；默认以当前工作树为资料根，仍可显式传其他完整资料根。
if(!$AssetsRoot){$AssetsRoot=$root}
if(!$ExpectedIdentity){$ExpectedIdentity=Join-Path $root 'docs/preparation/m1-04a/identity.json'}
if($DatabasePort -eq 54329 -or $BackendPort -eq 54329 -or $BackendPort -eq $DatabasePort){throw 'Forbidden or overlapping database/backend port'}
# 未批准 Start 必须先于工具、目录、Docker 或凭证访问失败；M1-04A 仅执行 Prepare/CheckEnvironment。
if($Action -eq 'Start' -and !$ApproveTemporaryDatabase){throw 'Start requires explicit approval of this batch temporary database resources'}
$EvidenceRoot=[IO.Path]::GetFullPath($EvidenceRoot)
if($EvidenceRoot.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $EvidenceRoot -eq $root){throw 'Use an external evidence/build directory'}
$run=Join-Path $EvidenceRoot $Batch

function Test-ProcessOwner($Owner) {
    $p=Get-Process -Id ([int]$Owner.pid) -ErrorAction SilentlyContinue
    if(!$p){return $false}
    return [Math]::Abs(($p.StartTime.ToUniversalTime()-([DateTimeOffset]$Owner.startTime).UtcDateTime).TotalMilliseconds) -le 1 -and $p.Path -ieq $Owner.executable
}
function Read-RunOwner {
    $owner=Get-Content -Raw -LiteralPath (Join-Path $run 'launcher-owner.json')|ConvertFrom-Json
    if($owner.batch -cne $Batch -or $owner.run -ine $run -or $owner.sourceRoot -ine $root -or $owner.databasePort -eq 54329 -or $owner.backendPort -eq 54329 -or $owner.manifestSha256 -notmatch '^[A-Fa-f0-9]{64}$'){throw 'Run owner mismatch; no signal or removal permitted'}
    return $owner
}
if($Action -in @('Stop','Recover')) {
    $owner=Read-RunOwner
    if($Action -eq 'Stop') {
        if(!(Test-ProcessOwner $owner)){throw 'Supervisor is not the recorded live owner; use Recover to inspect residual resources'}
        # 正常 STOP 由宿主关闭自己的 Context；不按 java.exe 名称终止其它进程。
        [IO.File]::WriteAllText((Join-Path $run 'STOP_HOST'),'stop')
        $deadline=[DateTime]::UtcNow.AddSeconds(20)
        while((Test-ProcessOwner $owner) -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 200}
        if(Test-ProcessOwner $owner){
            [IO.File]::WriteAllText((Join-Path $run 'STOP'),'stop owned Job')
            throw 'Graceful stop not confirmed; owned supervisor has received Job-stop signal. Inspect summary and Recover after supervisor exits'
        }
        $summary=Get-Content -Raw -LiteralPath (Join-Path $run 'summary.json')|ConvertFrom-Json
        if($summary.exitCode -ne 0 -or !$summary.cleanupConfirmed){throw 'Run exited with failure or unconfirmed cleanup; inspect summary and ledger'}
        Write-Output 'Owned run stopped and cleanup recorded';exit 0
    }
    if(Test-ProcessOwner $owner){throw 'Supervisor still owns the run; use Stop, not concurrent recovery'}
    $readyPath=Join-Path $run 'host-ready.json'
    if(Test-Path -LiteralPath $readyPath){
        $ready=Get-Content -Raw -LiteralPath $readyPath|ConvertFrom-Json
        if($ready.batch -cne $Batch){throw 'JVM batch record mismatch; no removal permitted'}
        $p=Get-Process -Id ([int]$ready.pid) -ErrorAction SilentlyContinue
        # Java Instant 与 .NET 的小数位格式不同；统一时间比较，兼容 Windows startInstant 的毫秒精度。
        if($p -and $p.Path -ieq $ready.executable -and [Math]::Abs(($p.StartTime.ToUniversalTime()-([DateTimeOffset]$ready.startTime).UtcDateTime).TotalMilliseconds) -le 1){throw 'Recorded JVM still alive; no database removal permitted. Verify its exact PID/executable and stop it manually'}
    }
    # ready 文件写入前也可能中断：用本轮 JVM 参数标记和持久化 Worker 身份确认无遗留，不能只看 HTTP 端口。
    $jvms=@(Get-CimInstance Win32_Process -Filter ("Name='java.exe' AND CommandLine LIKE '%-DzhkuSimulatedBatch="+$Batch+"%'"))
    if($jvms.Count){throw 'Batch-marked JVM remains; no database removal permitted. Verify its exact PID and stop it manually'}
    foreach($file in Get-ChildItem -LiteralPath $run -Filter '*.owner.json' -File){
        if($file.Name -eq 'launcher-owner.json'){continue}
        $workerOwner=Get-Content -Raw -LiteralPath $file.FullName|ConvertFrom-Json
        $workerOwner|Add-Member -NotePropertyName executable -NotePropertyValue $owner.executable -Force
        if(Test-ProcessOwner $workerOwner){throw 'Recorded Job worker remains; no database removal permitted'}
    }
    if(@(Get-NetTCPConnection -LocalPort ([int]$owner.backendPort) -State Listen -ErrorAction SilentlyContinue).Count){throw 'Backend port still listening; unknown process must not be killed'}
    $recovery=Join-Path $run ('recovery-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,6))
    [void][IO.Directory]::CreateDirectory($recovery)
    . (Join-Path $root 'tools/m1-02/Safety.ps1')
    . (Join-Path $root 'tools/m1-02/DockerResources.ps1')
    Initialize-Safety $recovery 120 -SecretNames @()
    $script:Docker=(Get-Command docker.exe -ErrorAction Stop).Source
    $ledger=Join-Path $run 'resource-intent.json'
    $record=Get-Content -Raw -LiteralPath $ledger|ConvertFrom-Json
    if($record.batch -cne $Batch -or $record.port -ne $owner.databasePort -or $record.containerName -cne ('zhku-'+$Batch) -or $record.volumeName -cne ('zhku-'+$Batch+'-data') -or $record.imageId -cne $owner.imageId){throw 'Recovery ledger disagrees with run owner'}
    $script:CleanupMode=$true
    $result=Remove-OwnedDockerResource $ledger
    Save-Json 'recovery-result.json' $result
    Write-Output ('Recovery verified; evidence: '+$recovery);exit 0
}

$identity=Get-Content -Raw -LiteralPath $ExpectedIdentity|ConvertFrom-Json
$currentHead=(& git -C $root rev-parse HEAD)
# 本地提交文档/验收入口后 HEAD 会前进；仍逐项核对实际源码，不能仅凭提交号放行不同字节。
if($identity.head -notmatch '^[a-fA-F0-9]{40}$'){throw 'Source identity HEAD invalid'}
& git -C $root merge-base --is-ancestor $identity.head $currentHead
if($LASTEXITCODE -ne 0){throw 'Source identity is not an ancestor of current HEAD'}
function Assert-Rows([string]$Base,$Rows) {
    foreach($row in $Rows){
        $path=Join-Path $Base $row.path
        if(!(Test-Path -LiteralPath $path -PathType Leaf) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $row.sha256){throw ('Identity mismatch: '+$row.path)}
    }
}
Assert-Rows $root $identity.source
Assert-Rows $AssetsRoot $identity.assets
Assert-Rows $root $identity.historicalReports
$java=(Get-Command java.exe -ErrorAction Stop).Source
$props=Get-Content -Raw -LiteralPath (Join-Path $root '.mvn/wrapper/maven-wrapper.properties')|ConvertFrom-StringData
$url=[string]$props.distributionUrl
$hash=[Convert]::ToHexString([Security.Cryptography.MD5]::HashData([byte[]][char[]]$url)).ToLower()
$distribution=([IO.Path]::GetFileName($url) -replace '-bin\.zip$','')
$maven=Join-Path $env:USERPROFILE ".m2/wrapper/dists/$distribution/$hash/bin/mvn.cmd"
if(!(Test-Path -LiteralPath $maven)){throw 'Exact Maven distribution not cached; no download permitted'}
$manifestSha=(Get-FileHash -LiteralPath $ExpectedIdentity).Hash
if($Action -eq 'CheckEnvironment') {
    [ordered]@{mode='SIMULATED';head=$identity.head;sourceFiles=@($identity.source).Count;assets=@($identity.assets).Count;
        powershell=(Join-Path $PSHOME 'pwsh.exe');java=$java;maven=$maven;offline=$true;databaseOperations=0;providerCalls=0;
        plannedBatch=$Batch;databasePort=$DatabasePort;backendPort=$BackendPort;dockerChecked=$false;executionAuthorized=$false}|ConvertTo-Json
    exit 0
}

. (Join-Path $root 'tools/m1-02/Safety.ps1')
. (Join-Path $root 'tools/m1-02/DockerResources.ps1')
function Checked([string]$Exe,[string[]]$Arguments,[string]$Name,[int]$Seconds=120) {
    $result=Invoke-Owned $Exe $Arguments $Name $Seconds
    if($result.exitCode -ne 0){throw ($Name+' failed: exit='+$result.exitCode)}
    return $result.output.Trim()
}
$main='com.zhku.knowledge.ruleagent.demo.RuleAgentSimulatedDemoHost'
$launcher='org.springframework.boot.loader.launch.PropertiesLauncher'
if($Action -eq 'Prepare') {
    $prep=Join-Path $EvidenceRoot ('prepare-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8))
    $copy=Join-Path $prep 'source';[void][IO.Directory]::CreateDirectory($copy)
    Initialize-Safety $prep 900 -SecretNames @()
    foreach($row in $identity.source){
        $dest=Join-Path $copy $row.path;[void][IO.Directory]::CreateDirectory((Split-Path $dest -Parent))
        Copy-Item -LiteralPath (Join-Path $root $row.path) -Destination $dest
    }
    Assert-Rows $copy $identity.source
    Set-Location -LiteralPath $copy
    [void](Checked $maven @('-o','-B','-ntp','-pl','knowledge','-am','-DskipTests','package') 'offline-package' 480)
    $jar=Join-Path $copy 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar'
    $entry=(Checked $java @('-Dfile.encoding=UTF-8',('-Dloader.main='+$main),'-cp',$jar,$launcher,'--check-entry') 'production-jar-entry' 30)|ConvertFrom-Json
    if($entry.main -cne $main -or $entry.mode -cne 'SIMULATED' -or $entry.databaseOperations -ne 0 -or $entry.providerCalls -ne 0 -or !$entry.productionClasspathOnly){throw 'Production JAR entry not proven'}
    Save-Json 'prepared.json' @{sourceRoot=$root;manifestSha256=$manifestSha;jar=$jar;jarSha256=(Get-FileHash -LiteralPath $jar).Hash;
        java=$java;javaSha256=(Get-FileHash -LiteralPath $java).Hash;entryVerified=$true;databaseOperations=0;providerCalls=0}
    Write-Output ('PreparedDirectory: '+$prep);exit 0
}

# Start 使用已核对的独立制品，不在申请运行授权后悄悄重建源码。
$prepared=Get-Content -Raw -LiteralPath (Join-Path $PreparedDirectory 'prepared.json')|ConvertFrom-Json
if($prepared.sourceRoot -ine $root -or $prepared.manifestSha256 -cne $manifestSha -or !$prepared.entryVerified -or (Get-FileHash -LiteralPath $prepared.jar).Hash -ine $prepared.jarSha256 -or (Get-FileHash -LiteralPath $java).Hash -ine $prepared.javaSha256){throw 'Prepared artifact identity mismatch'}
if(Test-Path -LiteralPath $run){throw 'Batch path already exists; no overwrite or automatic retry permitted'}
foreach($port in @($DatabasePort,$BackendPort)){$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port);try{$listener.Start()}finally{$listener.Stop()}}
[void][IO.Directory]::CreateDirectory($run)
Initialize-Safety $run ($LeaseSeconds+180) -SecretNames @('ZHKU_DB_PASSWORD','ZHKU_SIM_DEMO_STUDENT_PASSWORD')
$script:Docker=(Get-Command docker.exe -ErrorAction Stop).Source
$image='sha256:ccc6e83d6e35e931dc7c5def2022729d5a6c370318d099181995567ff1fb4d6b'
$owner=@{pid=$PID;startTime=[Diagnostics.Process]::GetCurrentProcess().StartTime.ToUniversalTime().ToString('o');executable=(Join-Path $PSHOME 'pwsh.exe');
    batch=$Batch;run=$run;sourceRoot=$root;manifestSha256=$manifestSha;databasePort=$DatabasePort;backendPort=$BackendPort;imageId=$image}
# 同批并发启动也不能覆盖所有权：CreateNew 是资源创建前的原子占有点。
$ownerFile=[IO.File]::Open((Join-Path $run 'launcher-owner.json'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try {$bytes=[Text.Encoding]::UTF8.GetBytes((Protect-Text ($owner|ConvertTo-Json -Depth 8)));$ownerFile.Write($bytes,0,$bytes.Length)}finally{$ownerFile.Dispose()}
$ledger=Join-Path $run 'resource-intent.json'
$exitCode=1;$failure='';$cleanupConfirmed=$false
$saved=@{};foreach($key in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','ZHKU_DB_URL','ZHKU_SIM_DEMO_BATCH','ZHKU_SIM_DEMO_APPROVED_BATCH','ZHKU_SIM_DEMO_PORT',
    'ZHKU_SIM_DEMO_SECONDS','ZHKU_SIM_DEMO_ASSETS','ZHKU_SIM_DEMO_RUN','ZHKU_SIM_DEMO_STUDENT_PASSWORD')){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
try {
    $cache=(Checked $script:Docker @('image','inspect','--format','{"id":{{json .Id}},"volumes":{{json .Config.Volumes}}}',$image) 'cached-image')|ConvertFrom-Json
    if($cache.id -cne $image -or @($cache.volumes.PSObject.Properties.Name).Count -ne 1 -or !$cache.volumes.PSObject.Properties['/var/lib/postgresql/data']){throw 'Cached image identity or mount mismatch'}
    $active=Checked $script:Docker @('ps','-q','--filter','label=zhku.m1.batch') 'exclusive-run'
    if($active){throw 'Another labelled acceptance container is running; stop without creating resources'}
    if(Test-Path -LiteralPath (Join-Path $run 'STOP_HOST')){throw 'ROUND_STOP_REQUESTED'}
    # 每个数据库阶段重新导入所需数据库口令；不请求 DashScope，不读取供应商环境值。
    . (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
    $script:Secrets+=($env:ZHKU_DB_PASSWORD)
    if(!$env:ZHKU_SIM_DEMO_STUDENT_PASSWORD -or $env:ZHKU_SIM_DEMO_STUDENT_PASSWORD.Length -lt 16 -or $env:ZHKU_SIM_DEMO_STUDENT_PASSWORD.Length -gt 64){throw 'Provide a 16..64 character synthetic student password in process environment; never as an argument'}
    $env:POSTGRES_PASSWORD=$env:ZHKU_DB_PASSWORD
    $resource=@{batch=$Batch;containerName=('zhku-'+$Batch);volumeName=('zhku-'+$Batch+'-data');containerId='';port=$DatabasePort;database='zhkuagent_rulecheck_e2e';imageId=$image}
    $container=New-OwnedDockerResource $resource $ledger @('-e','POSTGRES_USER=zhkuagent','-e','POSTGRES_PASSWORD','-e','POSTGRES_DB=zhkuagent_rulecheck_e2e')
    $ready=$false
    for($i=0;$i -lt 20;$i++){
        . (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
        $probe=Invoke-Owned $script:Docker @('exec',$container.id,'pg_isready','-U','zhkuagent','-d','zhkuagent_rulecheck_e2e') 'database-ready' 8
        if($probe.exitCode -eq 0){$ready=$true;break};Start-Sleep -Seconds 1
    }
    if(!$ready){throw 'Temporary database did not become ready'}
    if(Test-Path -LiteralPath (Join-Path $run 'STOP_HOST')){throw 'ROUND_STOP_REQUESTED'}
    . (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
    $env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:${DatabasePort}/zhkuagent_rulecheck_e2e"
    $env:ZHKU_SIM_DEMO_BATCH=$Batch;$env:ZHKU_SIM_DEMO_APPROVED_BATCH=$Batch;$env:ZHKU_SIM_DEMO_PORT=[string]$BackendPort
    $env:ZHKU_SIM_DEMO_SECONDS=[string]$LeaseSeconds;$env:ZHKU_SIM_DEMO_ASSETS=$AssetsRoot;$env:ZHKU_SIM_DEMO_RUN=$run
    Set-Location -LiteralPath (Split-Path $prepared.jar -Parent)
    Write-Output ('SIMULATED batch evidence: '+$run+'; readiness: host-ready.json; use Stop from a second terminal')
    $result=Invoke-Owned $java @('-Dfile.encoding=UTF-8',('-DzhkuSimulatedBatch='+$Batch),('-Dloader.main='+$main),'-cp',$prepared.jar,$launcher) 'simulated-host' ($LeaseSeconds+60)
    $exitCode=$result.exitCode
    if($exitCode -ne 0){throw ('Simulated host failed or lease ended: exit='+$exitCode)}
    $ended=Get-Content -Raw -LiteralPath (Join-Path $run 'host-exit.json')|ConvertFrom-Json
    if(!$ended.resourcesClosed -or $ended.providerCalls -ne 0){throw 'Host resource exit not proven'}
} catch {$failure=Protect-Text $_.ToString();if($failure -match 'exit=124|ROUND_TIMEOUT'){$exitCode=124}elseif($failure -match 'exit=130|STOP_REQUESTED'){$exitCode=130}else{$exitCode=1}}
finally {
    $script:CleanupMode=$true;$script:SafetyClock=[Diagnostics.Stopwatch]::StartNew();$script:SafetyLimit=120
    try {
        if(Test-Path -LiteralPath $ledger){[void](Remove-OwnedDockerResource $ledger)}
        if(@(Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue).Count){throw 'Backend listener remains; ownership not assumed'}
        $cleanupConfirmed=$true
    } catch {$exitCode=1;$failure+='; cleanup: '+(Protect-Text $_.ToString())}
    Save-Json 'summary.json' @{mode='SIMULATED';exitCode=$exitCode;failure=$failure;cleanupConfirmed=$cleanupConfirmed;providerCalls=0;ledger=$ledger}
    foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}
}
Write-Output ('Run exit='+$exitCode+'; evidence: '+$run)
exit $exitCode
