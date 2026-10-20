#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Prepare','Chain')][string]$Mode='Prepare',
    [string]$SourceRoot=(Split-Path (Split-Path $PSScriptRoot -Parent) -Parent),
    [string]$AssetsRoot='D:\alldesk\Projects\ZhkuAgent',
    [string]$EvidenceRoot=(Join-Path $env:LOCALAPPDATA 'ZhkuAgent\controlled-acceptance'),
    [string]$ExpectedIdentity='',
    [ValidateRange(60,1800)][int]$TimeoutSeconds=900
)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Safety.ps1"
. "$PSScriptRoot/DockerResources.ps1"
$SourceRoot=(Resolve-Path -LiteralPath $SourceRoot).Path
$AssetsRoot=(Resolve-Path -LiteralPath $AssetsRoot).Path
$batch='m1-'+$Mode.ToLower()+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N')
$run=Join-Path $EvidenceRoot $batch
[void][IO.Directory]::CreateDirectory($run)
Initialize-Safety $run $TimeoutSeconds
# 父监督者异常时，人工只可按 PID 与启动时间核实本轮父进程，不能依赖进程名批量停止。
Save-Json 'supervisor-owner.json' @{pid=$PID;startTime=[Diagnostics.Process]::GetCurrentProcess().StartTime.ToUniversalTime().ToString('o');executable=(Join-Path $PSHOME 'pwsh.exe');evidenceDirectory=$run}
$initialLocation=Get-Location
$exitCode=1;$mavenExit=$null;$failure='';$containerId='';$containerName='';$volumeName='';$port=$null;$ledgerPath=''
$cleanup=@{database='not-created';http='not-started';jvm='not-started';container='not-created';volume='not-created';ports='not-started'}
$observations=@();$summary=@();$model=@{providerCalls=$null;modelAttemptCount=$null;basis='not-run'}
Write-Output "证据目录：$run"
function Run-Checked([string]$Exe,[string[]]$Arguments,[string]$Name,[int]$Seconds=120) {
    $r=Invoke-Owned $Exe $Arguments $Name $Seconds
    if($r.exitCode -ne 0){throw "$Name failed: exit=$($r.exitCode); timeout=$($r.timedOut); stopped=$($r.stopped)"}
    return $r.output.Trim()
}
function Snapshot([string]$Root,[string[]]$Paths) {
    $rows=@()
    foreach($relative in ($Paths|Sort-Object -Unique)) {
        Assert-Budget
        $path=Join-Path $Root $relative
        if(!(Test-Path -LiteralPath $path -PathType Leaf)){throw "Identity input missing: $relative"}
        $rows+=@{path=$relative;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash;length=(Get-Item -LiteralPath $path).Length}
    }
    return $rows
}
function Fingerprint($Rows) {
    $text=($Rows|ForEach-Object {"$($_.path)`t$($_.sha256)`t$($_.length)"}) -join "`n"
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text)))
}
function Read-DatabaseCredential {
    # 每个数据库操作前重读统一入口，绝不从资料目录 .env 获取口令。
    . (Join-Path $SourceRoot 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
}
try {
    Set-Location -LiteralPath $SourceRoot
    $git=(Get-Command git.exe -ErrorAction Stop).Source
    $head=Run-Checked $git @('rev-parse','HEAD') 'head' 15
    $branch=Run-Checked $git @('branch','--show-current') 'branch' 15
    $paths=(Run-Checked $git @('-c','core.quotepath=false','ls-files','--cached','--others','--exclude-standard') 'source-files' 30) -split '\r?\n'
    # 复制实际文件（包含已存在的工作区修改），不把 HEAD 冒充验收源码；排除历史证据/构建/原 PDF。
    $sourcePaths=@($paths|Where-Object {$_ -match '^(pom.xml|mvnw.cmd|\.mvn/|scripts/|tools/m1-02/|agent-runtime/(pom.xml|src/)|agent/pom.xml|knowledge/(pom.xml|src/))' -and $_ -notmatch '(?i)\.(pdf|class|jar)$|(^|/)target/'})
    $assetPaths=@(Get-ChildItem -LiteralPath (Join-Path $AssetsRoot 'knowledge/data') -Recurse -File |ForEach-Object {[IO.Path]::GetRelativePath($AssetsRoot,$_.FullName).Replace('\','/')})
    $approval=Get-Content -LiteralPath (Join-Path $AssetsRoot 'docs/product/rule-check-2025-b03-publication-review.json') -Raw|ConvertFrom-Json
    $assetPaths+=@('docs/product/rule-check-2025-verbatim-import-draft.json','docs/product/rule-check-2025-b02-semantic-review.json','docs/product/rule-check-2025-b03-publication-review.json','docs/product/rule-check-c05-topic-catalogue.json')
    $assetPaths+=@($approval.inputs|ForEach-Object {$_.path})
    $assetPaths+=[string]$approval.confirmationEvidence
    $assetPaths+=@('docs/evidence/rule-check-refactor/2026-10-09/b03-student-qualification-01/presented-publication-review.json','docs/evidence/rule-check-refactor/2026-10-09/b03-student-qualification-01/presented-review.md','docs/evidence/rule-check-refactor/2026-10-09/b02-semantic-review-01/human-confirmation.json','docs/evidence/rule-check-refactor/2026-10-09/b02-semantic-review-01/presented-semantic-draft.json','docs/evidence/rule-agent-evidence/2026-10-13/t02-01/article4-overlay-draft.json','docs/evidence/rule-agent-evidence/2026-10-13/t02-01/human-confirmation.json','../SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf')
    $historical=@(Get-ChildItem -LiteralPath $SourceRoot -Directory -Recurse -Filter surefire-reports|ForEach-Object {Get-ChildItem -LiteralPath $_.FullName -File}|Where-Object {$_.Extension -in '.xml','.txt'}|ForEach-Object {[IO.Path]::GetRelativePath($SourceRoot,$_.FullName).Replace('\','/')})
    $sourceBefore=Snapshot $SourceRoot $sourcePaths;$assetsBefore=Snapshot $AssetsRoot $assetPaths;$reportsBefore=Snapshot $SourceRoot $historical
    $identity=@{head=$head;branch=$branch;sourceFingerprint=(Fingerprint $sourceBefore);assetsFingerprint=(Fingerprint $assetsBefore);historicalReportsFingerprint=(Fingerprint $reportsBefore);source=$sourceBefore;assets=$assetsBefore;historicalReports=$reportsBefore}
    Save-Json 'identity-before.json' $identity
    if($Mode -eq 'Chain') {
        if(!$ExpectedIdentity){throw 'Chain requires reviewed Prepare identity file'}
        $expected=Get-Content -LiteralPath $ExpectedIdentity -Raw|ConvertFrom-Json
        if($expected.head -ne $head -or $expected.sourceFingerprint -ne $identity.sourceFingerprint -or $expected.assetsFingerprint -ne $identity.assetsFingerprint){throw 'Reviewed source/assets identity changed; stop before creating resources'}
    }
    $copy=Join-Path $run 'workspace';[void][IO.Directory]::CreateDirectory($copy)
    foreach($relative in $sourcePaths){Assert-Budget;$destination=Join-Path $copy $relative;[void][IO.Directory]::CreateDirectory((Split-Path $destination -Parent));[IO.File]::Copy((Join-Path $SourceRoot $relative),$destination,$false)}
    if((Fingerprint (Snapshot $copy $sourcePaths)) -ne $identity.sourceFingerprint){throw 'Source copy mismatch'}
    $props=Get-Content -LiteralPath (Join-Path $SourceRoot '.mvn/wrapper/maven-wrapper.properties') -Raw|ConvertFrom-StringData
    if($env:MVNW_REPOURL -or $env:MAVEN_USER_HOME){throw 'Nondefault Maven wrapper location: review before proceeding'}
    $url=[string]$props.distributionUrl
    $hash=[Convert]::ToHexString([Security.Cryptography.MD5]::HashData([byte[]][char[]]$url)).ToLower()
    $distribution=([IO.Path]::GetFileName($url) -replace '-bin\.zip$','')
    $maven=Join-Path $env:USERPROFILE ".m2/wrapper/dists/$distribution/$hash/bin/mvn.cmd"
    if(!(Test-Path -LiteralPath $maven)){throw 'Exact Maven wrapper distribution not cached; no download permitted'}
    Save-Json 'toolchain.json' @{maven=$maven;mavenHash=(Get-FileHash -LiteralPath $maven).Hash;powershell=(Join-Path $PSHOME 'pwsh.exe');offline=$true;copy=$copy}
    $script:Docker=(Get-Command docker.exe -ErrorAction Stop).Source
    $image='pgvector/pgvector:0.8.6-pg16-bookworm'
    $imageInfo=Run-Checked $script:Docker @('image','inspect','--format','{"id":{{json .Id}},"volumes":{{json .Config.Volumes}}}',$image) 'image-cache' 20
    $imageObject=$imageInfo|ConvertFrom-Json
    if(@($imageObject.volumes.PSObject.Properties.Name).Count -ne 1 -or !$imageObject.volumes.PSObject.Properties['/var/lib/postgresql/data']){throw 'Image mount layout differs; stop'}
    Save-Json 'image-cache.json' $imageObject
    [void](Run-Checked $script:Docker @('ps','-a','--no-trunc','--format','{{json .}}') 'docker-before' 20)
    # 不执行数据库读写，仅检查凭证可读性；Prepare 不向 Docker 注入口令。
    $credentialPresent=$false
    foreach($scope in @('Process','User','Machine')){if(![string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable('ZHKU_DB_PASSWORD',$scope))){$credentialPresent=$true;break}}
    Save-Json 'credential-access.json' @{databaseCredentialReadable=$credentialPresent;providerCredentialUsed=$false}
    Set-Location -LiteralPath $copy
    [void](Run-Checked $maven @('-o','-B','-ntp','-pl','knowledge','-am','-DskipTests','test-compile') 'offline-test-compile' 480)
    $chainDir=Join-Path $run 'chain';[void][IO.Directory]::CreateDirectory($chainDir)
    $reportRequest=Join-Path $run 'requested-surefire'
    $test='RuleAgentContractTest'
    $args=@('-o','-B','-ntp','-pl','knowledge','-am',('-Dsurefire.reportsDirectory='+$reportRequest),'-Dsurefire.failIfNoSpecifiedTests=false')
    if($Mode -eq 'Chain') {
        if(!$credentialPresent){throw 'Database credential unavailable'}
        $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
        if($port -eq 54329){throw 'Forbidden demonstration database port'}
        $containerName='zhku-'+$batch;$volumeName=$containerName+'-data'
        $ledgerPath=Join-Path $run 'resource-intent.json'
        $ledger=@{batch=$batch;containerName=$containerName;volumeName=$volumeName;containerId='';port=$port;database='zhkuagent_rulecheck_e2e';imageId=$imageObject.id}
        # 使用四种真实生命周期探针共用的创建/所有权/回收实现，不改变临时库与测试语义。
        Read-DatabaseCredential
        $previousPostgresPassword=[Environment]::GetEnvironmentVariable('POSTGRES_PASSWORD','Process')
        $env:POSTGRES_PASSWORD=$env:ZHKU_DB_PASSWORD
        try {
            $owned=New-OwnedDockerResource $ledger $ledgerPath @('-e','POSTGRES_USER=zhkuagent','-e','POSTGRES_PASSWORD','-e','POSTGRES_DB=zhkuagent_rulecheck_e2e')
            $containerId=$owned.id
        } finally {[Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD',$previousPostgresPassword,'Process')}
        $ready=$false
        for($i=0;$i -lt 30;$i++) {
            Read-DatabaseCredential
            $r=Invoke-Owned $script:Docker @('exec',$containerId,'pg_isready','-U','zhkuagent','-d','zhkuagent_rulecheck_e2e') 'database-ready' 8
            if($r.exitCode -eq 0){$ready=$true;break};Start-Sleep -Seconds 1
        }
        if(!$ready){throw 'Temporary database readiness failed'}
        $test='RuleAgentSimulatedChainTempDbHttpTest#realIdentityAndReviewedAssetsOverActualHttpWithCsrfIdempotencyAndIsolation+realTempDbCancelCapacityAndTerminalStatesOverActualHttp'
        Read-DatabaseCredential
        $args+=@("-Dzhku.db.url=jdbc:postgresql://127.0.0.1:${port}/zhkuagent_rulecheck_e2e","-Dzhku.t06.root=$AssetsRoot","-Dzhku.rulecheck.evidenceDir=$chainDir")
        $cleanup.database='started';$cleanup.http='requires-test-evidence';$cleanup.jvm='requires-job-exit'
    }
    $args+=@("-Dtest=$test",'test')
    $mavenResult=Invoke-Owned $maven $args 'maven-test' 480
    $mavenExit=$mavenResult.exitCode
    Save-Json 'maven-result.json' @{exitCode=$mavenExit;nativeExitCode=$mavenResult.nativeExitCode;timedOut=$mavenResult.timedOut;stopped=$mavenResult.stopped;remainingPids=$mavenResult.remainingPids}
    $cleanup.jvm=if(@($mavenResult.remainingPids).Count -eq 0){'job-empty'}else{'unconfirmed'}
    if($mavenExit -ne 0){throw "Maven failed: exit=$mavenExit; timeout=$($mavenResult.timedOut)"}
    $actual=@(Get-ChildItem -LiteralPath $copy -Recurse -File -Filter 'TEST-*.xml')
    Save-Json 'report-location.json' @{requested=$reportRequest;requestedExists=(Test-Path -LiteralPath $reportRequest);actualXml=@($actual.FullName);propertyEffective=(@($actual|Where-Object {$_.FullName.StartsWith($reportRequest,[StringComparison]::OrdinalIgnoreCase)}).Count -gt 0);isolation='entire-build-in-unique-copy'}
    foreach($file in $actual) {
        [xml]$xml=Get-Content -LiteralPath $file.FullName -Raw
        $summary+=@{class=[string]$xml.testsuite.name;tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped;methods=@($xml.testsuite.testcase|ForEach-Object {[string]$_.name})}
    }
    Save-Json 'test-summary.json' $summary
    $class=if($Mode -eq 'Chain'){'com.zhku.knowledge.ruleagent.web.RuleAgentSimulatedChainTempDbHttpTest'}else{'com.zhku.knowledge.ruleagent.RuleAgentContractTest'}
    $selected=@($summary|Where-Object {$_.class -eq $class})
    $count=if($Mode -eq 'Chain'){2}else{7}
    if($selected.Count -ne 1 -or $selected[0].tests -ne $count -or $selected[0].failures -ne 0 -or $selected[0].errors -ne 0 -or $selected[0].skipped -ne 0){throw 'Expected test methods/counts not proven'}
    if($Mode -eq 'Chain') {
        foreach($name in @('simulated-chain-tempdb.json','cancel-capacity-terminal.json')) {
            $j=Get-Content -LiteralPath (Join-Path $chainDir $name) -Raw|ConvertFrom-Json
            if($j.executionMode -ne 'SIMULATED_ENGINEERING_REPLAY' -or $j.providerCalls -ne 0 -or $j.modelAttemptCount -ne 0 -or !$j.resourcesExited){throw 'Chain model/resource evidence invalid'}
        }
        $model=@{providerCalls=0;modelAttemptCount=0;basis='both-chain-json-and-static-SIMULATED-host'};$cleanup.http='both-tests-resourcesExited'
    } else {$model=@{providerCalls=0;modelAttemptCount=0;basis='pure-contract-probe-only; chain-not-run'}}
    $exitCode=0
} catch {
    $failure=Protect-Text $_.ToString()
    if($failure -match 'ROUND_TIMEOUT|timeout=True|exit=124'){$exitCode=124}elseif($failure -match 'STOP_REQUESTED|exit=130'){$exitCode=130}else{$exitCode=1}
    Save-Json 'preparation-or-run-failure.json' @{message=$failure;scriptStack=$_.ScriptStackTrace}
} finally {
    # 业务时限与退出预算分开；退出阶段最多 120 秒，失败即记录剩余资源，不无限等待。
    $script:CleanupMode=$true;$script:SafetyLimit=[Math]::Min($TimeoutSeconds,$script:SafetyClock.Elapsed.TotalSeconds)+120
    try {
        if($ledgerPath -and (Test-Path -LiteralPath $ledgerPath)) {
            $resourceExit=Remove-OwnedDockerResource $ledgerPath
            $cleanup.container='absent-after-owned-removal';$cleanup.volume='absent-after-owned-removal';$cleanup.database='container-absent';$cleanup.ports='database-listener-absent'
        }
    } catch {$cleanup.failure=Protect-Text $_.ToString();if($exitCode -eq 0){$exitCode=2}}
    try {
        if($identity) {
            $sourceAfter=Snapshot $SourceRoot $sourcePaths;$assetsAfter=Snapshot $AssetsRoot $assetPaths;$reportsAfter=Snapshot $SourceRoot $historical
            $unchanged=((Fingerprint $sourceAfter) -eq $identity.sourceFingerprint -and (Fingerprint $assetsAfter) -eq $identity.assetsFingerprint -and (Fingerprint $reportsAfter) -eq $identity.historicalReportsFingerprint)
            Save-Json 'identity-after.json' @{sourceFingerprint=(Fingerprint $sourceAfter);assetsFingerprint=(Fingerprint $assetsAfter);historicalReportsFingerprint=(Fingerprint $reportsAfter);unchanged=$unchanged}
            if(!$unchanged){$exitCode=3}
        }
        # 失败也收集已有报告；尾部堆栈留在控制台日志，第一份失败和最内层 Cause 单独提取。
        $archive=Join-Path $run 'surefire';[void][IO.Directory]::CreateDirectory($archive)
        $first=$null
        if($copy -and (Test-Path -LiteralPath $copy)) {
            foreach($file in @(Get-ChildItem -LiteralPath $copy -Recurse -File|Where-Object {$_.Directory.Name -eq 'surefire-reports' -and $_.Extension -in '.xml','.txt','.dump','.dumpstream'})) {
                $text=Protect-Text ([IO.File]::ReadAllText($file.FullName));[IO.File]::WriteAllText($file.FullName,$text)
                $module=[IO.Path]::GetRelativePath($copy,$file.FullName).Split([IO.Path]::DirectorySeparatorChar)[0]
                [IO.File]::WriteAllText((Join-Path $archive ($module+'-'+$file.Name)),$text)
                if(!$first -and $file.Extension -eq '.xml') {
                    [xml]$x=$text
                    foreach($t in $x.testsuite.testcase){foreach($element in @($t.ChildNodes|Where-Object {$_.Name -in 'failure','error'})){if(!$first){$first=$element.InnerText}}}
                }
            }
        }
        $causes=if($first){@([regex]::Matches($first,'(?m)^\s*Caused by:.*$')|ForEach-Object {$_.Value.Trim()})}else{@()}
        Save-Json 'first-failure.json' @{stack=$first;innermostCause=$(if($causes.Count){$causes[-1]}else{$null});note='null means no Surefire failure stack available; see preparation-or-run-failure and logs'}
    } catch {$cleanup.evidenceFailure=Protect-Text $_.ToString();if($exitCode -eq 0){$exitCode=2}}
    Save-Json 'result.json' @{batch=$batch;mode=$Mode;exitCode=$exitCode;mavenExitCode=$mavenExit;failure=$failure;model=$model;cleanup=$cleanup;elapsedSeconds=$script:SafetyClock.Elapsed.TotalSeconds;runLimitSeconds=$TimeoutSeconds;cleanupLimitSeconds=120;chainExecuted=($Mode -eq 'Chain' -and $null -ne $mavenExit);manualRecovery='If interrupted, inspect resource-intent.json and exact batch label; verify ID/name/image/mounts/volume label before deleting. Never prune or kill by process name.'}
    Set-Location -LiteralPath $initialLocation
    Close-Safety
}
Write-Output "结果：$run/result.json；退出码：$exitCode"
exit $exitCode
