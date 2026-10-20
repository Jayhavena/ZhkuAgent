#requires -Version 7.0
param([Parameter(Mandatory)][string]$EvidenceRoot,[string]$Batch='m1close-20261019-223808-8de898ae',[switch]$InterruptedWorker,[string]$RecoverLedger='')
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Safety.ps1"
. "$PSScriptRoot/DockerResources.ps1"
$script:Docker=(Get-Command docker.exe -ErrorAction Stop).Source
if($RecoverLedger) {
    $dir=Join-Path $EvidenceRoot ('recovery-'+[guid]::NewGuid().ToString('N'));[void][IO.Directory]::CreateDirectory($dir);Initialize-Safety $dir 120
    Remove-OwnedDockerResource $RecoverLedger;exit 0
}
$image='sha256:ccc6e83d6e35e931dc7c5def2022729d5a6c370318d099181995567ff1fb4d6b'
if($InterruptedWorker) {
    Initialize-Safety $EvidenceRoot 120
    $ledger=@{batch=($Batch+'-interrupted');containerName=('zhku-'+$Batch+'-interrupted');volumeName=('zhku-'+$Batch+'-interrupted-data');containerId='';port=55173;imageId=$image}
    [void](New-OwnedDockerResource $ledger (Join-Path $EvidenceRoot 'resource-intent.json') @('--entrypoint','/bin/sleep') @('120'))
    Save-Json 'parent-ready.json' @{pid=$PID;started=[Diagnostics.Process]::GetCurrentProcess().StartTime.ToUniversalTime().ToString('o')}
    [void](Invoke-Owned $script:Docker @('wait',$ledger.containerId) 'interrupted-wait' 90)
    exit 99
}
$run=Join-Path $EvidenceRoot $Batch
if(Test-Path -LiteralPath $run){throw 'Batch evidence already exists; no automatic retry'}
[void][IO.Directory]::CreateDirectory($run)
Write-Output "生命周期证据：$run"
$all=@();$failed=$false
foreach($case in @('success','failure','timeout','interrupted')) {
    $dir=Join-Path $run $case;[void][IO.Directory]::CreateDirectory($dir);Initialize-Safety $dir 180
    $ledgerPath=Join-Path $dir 'resource-intent.json'
    $ledger=@{batch=($Batch+'-'+$case);containerName=('zhku-'+$Batch+'-'+$case);volumeName=('zhku-'+$Batch+'-'+$case+'-data');containerId='';port=(55170+@('success','failure','timeout','interrupted').IndexOf($case));imageId=$image}
    $p=$null;$result=@{case=$case;passed=$false}
    try {
        $cached=Invoke-ResourceDocker @('image','inspect','--format','{{.Id}}',$image) 'image-cache'
        if($cached -cne $image){throw 'Exact cached image missing'}
        if($case -eq 'interrupted') {
            $info=[Diagnostics.ProcessStartInfo]::new((Join-Path $PSHOME 'pwsh.exe'));$info.UseShellExecute=$false;$info.CreateNoWindow=$true
            foreach($arg in @('-NoProfile','-File',$PSCommandPath,'-EvidenceRoot',$dir,'-Batch',$Batch,'-InterruptedWorker')){$info.ArgumentList.Add($arg)}
            $p=[Diagnostics.Process]::Start($info);$start=$p.StartTime
            $deadline=[DateTime]::UtcNow.AddSeconds(45)
            $waitOwner=''
            while([DateTime]::UtcNow -lt $deadline -and !$p.HasExited) {
                $waitOwner=Get-ChildItem -LiteralPath $dir -Filter '*interrupted-wait.owner.json'|Select-Object -First 1 -ExpandProperty FullName
                if($waitOwner -and (Get-Content -LiteralPath $waitOwner -Raw|ConvertFrom-Json).gateReleased){break};Start-Sleep -Milliseconds 100
            }
            if(!$waitOwner -or $p.HasExited -or $p.StartTime -ne $start){throw 'Owned parent not ready for interruption'}
            $worker=Get-Content -LiteralPath $waitOwner -Raw|ConvertFrom-Json
            Save-Json 'parent-kill-owner.json' @{pid=$p.Id;startTime=$start.ToUniversalTime().ToString('o');executable=$info.FileName;worker=$worker}
            $p.Kill();if(!$p.WaitForExit(5000)){throw 'Interrupted parent exit unconfirmed'}
            Start-Sleep -Milliseconds 500
            $live=Get-Process -Id $worker.pid -ErrorAction SilentlyContinue
            if($live -and $live.StartTime.ToUniversalTime().ToString('o') -eq $worker.startTime){throw 'Worker survived parent termination'}
            $ledger=Get-Content -LiteralPath $ledgerPath -Raw|ConvertFrom-Json
            $remaining=Get-OwnedContainer $ledger
            if(!$remaining -or $remaining.state -ne 'running'){throw 'Interruption did not leave expected recoverable container'}
            Save-Json 'interruption-residual.json' $remaining
            $result.parentExitConfirmed=$true;$result.workerExitConfirmed=$true;$result.detectedResidual=$true
        } else {
            $options=@('--entrypoint','/bin/sleep');$command=@('120')
            if($case -eq 'failure'){$options=@('--entrypoint','/bin/sh');$command=@('-c','exit 23')}
            [void](New-OwnedDockerResource $ledger $ledgerPath $options $command)
            if($case -eq 'success') {
                [void](Invoke-ResourceDocker @('stop','--time','2',$ledger.containerId) 'normal-stop')
                $state=Get-OwnedContainer $ledger;if($state.state -ne 'exited'){throw 'Normal container did not exit'}
            } elseif($case -eq 'failure') {
                $status=Invoke-ResourceDocker @('wait',$ledger.containerId) 'controlled-failure'
                if($status -ne '23'){throw 'Controlled failure code lost'};$result.expectedContainerExit=23
            } else {
                $timeout=Invoke-Owned $script:Docker @('wait',$ledger.containerId) 'timed-wait' 3
                if(!$timeout.timedOut -or $timeout.exitCode -ne 124 -or @($timeout.remainingPids).Count){throw 'CLI timeout/process exit not proven'}
                $remaining=Get-OwnedContainer $ledger;if(!$remaining -or $remaining.state -ne 'running'){throw 'Timeout residual not discoverable'}
                $result.supervisorExit=124;$result.workerExitConfirmed=$true;$result.detectedResidual=$true
            }
        }
        $result.passed=$true
    } catch {$result.failure=Protect-Text $_.ToString();$failed=$true}
    finally {
        $script:CleanupMode=$true;$script:SafetyLimit=$script:SafetyClock.Elapsed.TotalSeconds+120
        try {
            if($p -and !$p.HasExited){$p.Kill();[void]$p.WaitForExit(5000)}
            if(Test-Path -LiteralPath $ledgerPath){$result.cleanup=Remove-OwnedDockerResource $ledgerPath}else{throw 'No resource ledger; automatic cleanup refused'}
        } catch {$result.cleanupFailure=Protect-Text $_.ToString();$result.passed=$false;$failed=$true}
        if($p){$p.Dispose()};Save-Json 'case-result.json' $result
    }
    $all+=$result;Write-Output "$case passed=$($result.passed)"
    if($failed){break}
}
[IO.File]::WriteAllText((Join-Path $run 'results.json'),($all|ConvertTo-Json -Depth 15))
if($failed){exit 1};exit 0
