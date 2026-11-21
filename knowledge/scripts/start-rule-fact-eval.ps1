#requires -Version 7.0
param(
 [ValidateSet('Prepare','Check','Assembly','Execute')][string]$Operation='Check',
 [switch]$Batch,
 [switch]$FullDataset,
 [switch]$RepairSubset,
 [ValidatePattern('^RFE-(00[1-9]|01[0-9]|020)$')][string]$CaseId='RFE-001',
 [string]$PackageRelativePath='docs/evidence/rule-agent-provider/2026-11-17-frozen-m1/request-package.json',
 [ValidatePattern('^[a-f0-9]{64}$')][string]$ApprovedPackageSha256,
 [ValidateRange(1024,65535)][int]$DatabasePort=55519,
 [ValidateSet('qwen4-max')][string]$Model
)
$ErrorActionPreference='Stop'
if($FullDataset-and $RepairSubset){throw 'Select only one batch mode'}
if($Model-and (($Operation-ne 'Prepare')-or ($Batch-and !$FullDataset))){throw 'Model selection is only allowed when preparing a single-question or full-dataset package'}
if($FullDataset-or $RepairSubset){$Batch=$true}
if($Batch-and !$PSBoundParameters.ContainsKey('PackageRelativePath')){throw 'Batch requires an explicit fresh PackageRelativePath'}
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
. (Join-Path $PSScriptRoot 'Invoke-ZhkuEvaluationProcess.ps1')
$importer=Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
$java=(Get-Command java.exe -ErrorAction Stop).Source
$jar=Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar'
$package=[IO.Path]::GetFullPath((Join-Path $root $PackageRelativePath))
$allowed=[IO.Path]::GetFullPath((Join-Path $root 'docs/evidence/rule-agent-provider'))+[IO.Path]::DirectorySeparatorChar
if([IO.Path]::IsPathRooted($PackageRelativePath)-or !$package.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Package must stay inside provider evidence root'}
$hostMain=if($Batch){'com.zhku.knowledge.eval.RuleFactBatchHost'}else{'com.zhku.knowledge.eval.RuleFactOnlineHost'}
$maximumCalls=if($FullDataset){105}elseif($RepairSubset){50}elseif($Batch){55}else{5}
$hostSeconds=if($FullDataset){2550}elseif($RepairSubset){1250}elseif($Batch){1350}else{130}
$hostSelection=if($FullDataset){'FULL_20'}elseif($RepairSubset){'REPAIR_9'}elseif($Batch){'SUBSET_10'}else{$CaseId}
$arguments=@('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',('-Dloader.main='+$hostMain),'-cp',$jar,'org.springframework.boot.loader.launch.PropertiesLauncher')
function RunHost([string[]]$hostArgs,[int]$seconds=20,[string[]]$secrets=@(),[string]$logPath) {
 $result=Invoke-ZhkuEvaluationProcess -Executable $java -Arguments ($arguments+$hostArgs) -TimeoutSeconds $seconds -Secrets $secrets
 if($logPath){[IO.Directory]::CreateDirectory((Split-Path $logPath -Parent))|Out-Null;[IO.File]::WriteAllText($logPath,(@{exitCode=$result.exitCode;timedOut=$result.timedOut;resourcesClosed=$result.resourcesClosed;output=$result.output;errorOutput=$result.errorOutput}|ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))}
 if($result.exitCode-ne 0-or $result.timedOut-or !$result.resourcesClosed){throw ('Evaluation host failed: '+$result.output+$result.errorOutput)}
 $result.output
}
Push-Location $root
try {
 if($Operation-eq 'Prepare') {
  if(Test-Path -LiteralPath $package){throw 'Preserve existing frozen package; select a fresh path'}
  $build=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','mvnw.cmd','-q','-pl','knowledge','-am','-DskipTests','package') -TimeoutSeconds 150
  if($build.exitCode-ne 0-or $build.timedOut-or !$build.resourcesClosed){throw 'Build failed; no package prepared'}
  $prepareArgs=@('prepare',$root,$hostSelection,$PackageRelativePath)
  if($Model){$prepareArgs+=$Model}
  RunHost $prepareArgs
  exit 0
 }
 if(!$ApprovedPackageSha256-or (Get-FileHash -LiteralPath $package).Hash.ToLowerInvariant()-ne $ApprovedPackageSha256){throw 'Explicit package SHA required'}
 RunHost @('check',$root,$PackageRelativePath,$ApprovedPackageSha256)
 if($Operation-eq 'Check'){exit 0}
 if($Operation-eq 'Execute') {
 $approvalPath=Join-Path (Split-Path $package -Parent) 'execution-approval.json'
 $approval=Get-Content -LiteralPath $approvalPath -Raw|ConvertFrom-Json
 if($approval.status-ne 'APPROVED'-or $approval.packageSha256-ne $ApprovedPackageSha256-or $approval.maximumCalls-ne $maximumCalls-or [DateTimeOffset]::Parse($approval.validUntil)-le [DateTimeOffset]::UtcNow){throw 'No valid package-specific approval'}
 if(Test-Path -LiteralPath (Join-Path (Split-Path $package -Parent) 'execution-reserved.json')){throw 'Batch already reserved; no retry or quota reuse'}
 }
 if($DatabasePort-eq 54329){throw 'Shared database port refused'}
 & (Join-Path $root 'knowledge/scripts/ensure-zhku-postgres.cmd') -DockerOnly
 if($LASTEXITCODE-ne 0){throw 'Docker readiness failed'}
 $docker=(Get-Command docker.exe -ErrorAction Stop).Source
 $container='zhku-rulefact-eval-'+[guid]::NewGuid().ToString('N')
 $database='zhkuagent_rulecheck_e2e'
 $outputKind=if($Operation-eq 'Assembly'){'assembly'}else{'live'}
 $out=(Split-Path $PackageRelativePath -Parent).Replace('\','/')+'/'+$outputKind+'-'+[guid]::NewGuid().ToString('N')
 function Docker([string[]]$dockerArgs) {
  & $importer -RequireDatabase
  $r=Invoke-ZhkuOwnedProcess -Executable $docker -Arguments $dockerArgs -TimeoutSeconds 15 -Secrets @($env:ZHKU_DB_PASSWORD)
  if($r.exitCode-ne 0-or $r.timedOut-or !$r.resourcesClosed){throw 'Owned Docker operation failed'}
 }
 & $importer -RequireDatabase
 if($Operation-eq 'Execute'){& $importer -RequireDashScope}
 $env:POSTGRES_PASSWORD=$env:ZHKU_DB_PASSWORD
 $env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/$database"
 $env:ZHKU_RULE_AGENT_MODE='ONLINE';$env:ZHKU_RULE_AGENT_AUTHORIZATION_ID=$ApprovedPackageSha256
 foreach($name in @('ZHKU_RULECHECK_TASK_BATCH_DIRECTORY','ZHKU_RULECHECK_TASK_PACKAGE_SHA256','ZHKU_RULECHECK_TASK_SUMMARY_SHA256')){Remove-Item -LiteralPath "Env:\$name" -ErrorAction SilentlyContinue}
 $operationFailure=$null
 try {
  Docker @('run','--rm','-d','--pull','never','--name',$container,'--publish',"127.0.0.1:${DatabasePort}:5432",'--env','POSTGRES_PASSWORD','--env','POSTGRES_USER=zhkuagent','--env',"POSTGRES_DB=$database",'pgvector/pgvector:0.8.6-pg16-bookworm')
  $ready=$false
  for($i=0;$i-lt 20;$i++) {
   try{Docker @('exec',$container,'pg_isready','-U','zhkuagent','-d',$database);$ready=$true;break}catch{Start-Sleep -Milliseconds 250}
  }
  if(!$ready){throw 'Temporary database did not become ready'}
  & $importer -RequireDatabase
  if($Operation-eq 'Assembly') {
   $assemblySeconds=if($Batch){90}else{60}
   RunHost @('assembly',$root,$PackageRelativePath,$ApprovedPackageSha256,$out) $assemblySeconds @($env:ZHKU_DB_PASSWORD) (Join-Path $root ($out+'/launcher-result.json'))
  } else {
   & $importer -RequireDashScope
   RunHost @('run',$root,$PackageRelativePath,$ApprovedPackageSha256,$out) $hostSeconds @($env:ZHKU_DB_PASSWORD,$env:DASHSCOPE_API_KEY) (Join-Path $root ($out+'/launcher-result.json'))
  }
  Write-Output "EVIDENCE_DIRECTORY=$out"
 } catch {$operationFailure=$_;throw} finally {
  # Cleanup needs no database credentials; a changed credential environment must not strand the owned container.
  $cleanup=Invoke-ZhkuOwnedProcess -Executable $docker -Arguments @('rm','-f',$container) -TimeoutSeconds 15
  $cleanupFailed=$cleanup.exitCode-ne 0-or $cleanup.timedOut-or !$cleanup.resourcesClosed
  $cleanupPath=Join-Path $root ($out+'/container-cleanup.json')
  [IO.Directory]::CreateDirectory((Split-Path $cleanupPath -Parent))|Out-Null
  [IO.File]::WriteAllText($cleanupPath,(@{container=$container;exitCode=$cleanup.exitCode;timedOut=$cleanup.timedOut;resourcesClosed=$cleanup.resourcesClosed;cleanupSucceeded=(!$cleanupFailed);hostFailed=($null-ne $operationFailure)}|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
  if($cleanupFailed){if($null-ne $operationFailure){Write-Warning ('Owned container cleanup also failed: '+$container)}else{throw ('Owned container cleanup failed: '+$container)}}
 }
} finally {Pop-Location}
