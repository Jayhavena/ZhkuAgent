#requires -Version 7.0
# 只抽取现有包装器的资源步骤，探针与正式执行共用同一套所有权判据，避免替身通过却未验证实际清理代码。
function Invoke-ResourceDocker([string[]]$Arguments,[string]$Name,[int]$Seconds=20) {
    $r=Invoke-Owned $script:Docker $Arguments $Name $Seconds
    if($r.exitCode -ne 0){throw "$Name failed: exit=$($r.exitCode); timeout=$($r.timedOut); stopped=$($r.stopped)"}
    return $r.output.Trim()
}
function Save-ResourceLedger($Ledger,[string]$Path) {
    # 先写临时文件再替换；强杀发生在写入中时，上一份完整意图账本仍可用。
    $temporary=$Path+'.next'
    [IO.File]::WriteAllText($temporary,(Protect-Text ($Ledger|ConvertTo-Json -Depth 12)),[Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporary,$Path,$true)
}
function Confirm-OwnedVolume($Ledger) {
    $v=(Invoke-ResourceDocker @('volume','inspect','--format','{"name":{{json .Name}},"labels":{{json .Labels}}}',$Ledger.volumeName) 'verify-volume-owner')|ConvertFrom-Json
    if($v.name -cne $Ledger.volumeName -or $v.labels.'zhku.m1.batch' -cne $Ledger.batch){throw 'Volume ownership mismatch; no removal permitted'}
    return $v
}
function Get-OwnedContainer($Ledger) {
    $ids=Invoke-ResourceDocker @('ps','-aq','--no-trunc','--filter',('label=zhku.m1.batch='+$Ledger.batch)) 'discover-owned-container'
    $items=@($ids -split '\r?\n'|Where-Object {$_})
    if(!$items.Count){return $null}
    if($items.Count -ne 1){throw 'Ambiguous batch ownership; no removal permitted'}
    $format='{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Image}},"labels":{{json .Config.Labels}},"mounts":{{json .Mounts}},"ports":{{json .HostConfig.PortBindings}},"state":{{json .State.Status}},"exitCode":{{json .State.ExitCode}}}'
    $c=(Invoke-ResourceDocker @('inspect','--format',$format,$items[0]) 'verify-container-owner')|ConvertFrom-Json
    $ports=@($c.ports.'5432/tcp');$mounts=@($c.mounts)
    if(($Ledger.containerId -and $c.id -cne $Ledger.containerId) -or $c.name -cne ('/'+$Ledger.containerName) -or $c.image -cne $Ledger.imageId -or $c.labels.'zhku.m1.batch' -cne $Ledger.batch -or @($c.ports.PSObject.Properties).Count -ne 1 -or $ports.Count -ne 1 -or $ports[0].HostIp -cne '127.0.0.1' -or [int]$ports[0].HostPort -ne [int]$Ledger.port -or $mounts.Count -ne 1 -or $mounts[0].Type -cne 'volume' -or $mounts[0].Name -cne $Ledger.volumeName -or $mounts[0].Destination -cne '/var/lib/postgresql/data'){throw 'Container ID/label/port/mount ownership mismatch; no removal permitted'}
    [void](Confirm-OwnedVolume $Ledger)
    return $c
}
function New-OwnedDockerResource($Ledger,[string]$LedgerPath,[string[]]$Options=@(),[string[]]$Command=@()) {
    if($Ledger.batch -notmatch '^[a-z0-9-]+$' -or $Ledger.containerName -notmatch '^zhku-[a-z0-9-]+$' -or $Ledger.volumeName -notmatch '^zhku-[a-z0-9-]+-data$' -or $Ledger.port -lt 1024 -or $Ledger.port -gt 65535 -or $Ledger.port -eq 54329){throw 'Invalid dedicated resource identity'}
    $existing=Invoke-ResourceDocker @('ps','-aq','--filter',('name=^/'+$Ledger.containerName+'$')) 'check-name-absent'
    $volumes=Invoke-ResourceDocker @('volume','ls','-q','--filter',('name=^'+$Ledger.volumeName+'$')) 'check-volume-absent'
    if($existing -or $volumes){throw 'Resource name already exists; ownership not acquired'}
    $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,[int]$Ledger.port)
    try{$listener.Start()}finally{$listener.Stop()}
    # 任何创建前必须有账本；Docker CLI 退出/超时不代表 daemon 没有创建资源。
    Save-ResourceLedger $Ledger $LedgerPath
    $label='zhku.m1.batch='+$Ledger.batch
    [void](Invoke-ResourceDocker @('volume','create','--label',$label,$Ledger.volumeName) 'create-volume')
    [void](Confirm-OwnedVolume $Ledger)
    $arguments=@('run','--pull=never','-d','--name',$Ledger.containerName,'--label',$label,'--mount',('type=volume,source='+$Ledger.volumeName+',target=/var/lib/postgresql/data'),'-p',('127.0.0.1:'+$Ledger.port+':5432'))+$Options+@($Ledger.imageId)+$Command
    $id=Invoke-ResourceDocker $arguments 'create-container' 30
    if($id -notmatch '^[a-f0-9]{64}$'){throw 'Docker did not return one exact container ID'}
    $Ledger.containerId=$id
    $owned=Get-OwnedContainer $Ledger
    if(!$owned){throw 'Created container not discoverable'}
    Save-ResourceLedger $Ledger $LedgerPath
    Save-Json 'resource-owner.json' $owned
    return $owned
}
function Remove-OwnedDockerResource([string]$LedgerPath) {
    $ledger=Get-Content -LiteralPath $LedgerPath -Raw|ConvertFrom-Json
    $owned=Get-OwnedContainer $ledger
    $volume=Invoke-ResourceDocker @('volume','ls','-q','--filter',('label=zhku.m1.batch='+$ledger.batch)) 'discover-owned-volume'
    if($volume -and $volume -cne $ledger.volumeName){throw 'Ambiguous volume ownership; no removal permitted'}
    if($volume){[void](Confirm-OwnedVolume $ledger)}
    if($owned) {
        # 从意图恢复时也先核对完整归属并持久化真实 ID，之后才允许删除。
        $ledger.containerId=$owned.id;Save-ResourceLedger $ledger $LedgerPath
        Save-Json 'resource-before-removal.json' $owned
        [void](Invoke-ResourceDocker @('rm','-f',$owned.id) 'remove-owned-container')
    }
    if(Invoke-ResourceDocker @('ps','-aq','--no-trunc','--filter',('label=zhku.m1.batch='+$ledger.batch)) 'container-exit-check'){throw 'Owned container remains'}
    if($volume){[void](Invoke-ResourceDocker @('volume','rm',$ledger.volumeName) 'remove-owned-volume')}
    if(Invoke-ResourceDocker @('volume','ls','-q','--filter',('label=zhku.m1.batch='+$ledger.batch)) 'volume-exit-check'){throw 'Owned volume remains'}
    $listeners=@(Get-NetTCPConnection -LocalPort ([int]$ledger.port) -State Listen -ErrorAction SilentlyContinue)
    $result=@{containerId=$ledger.containerId;containerName=$ledger.containerName;volumeName=$ledger.volumeName;port=$ledger.port;containerAbsent=$true;volumeAbsent=$true;portListenerAbsent=($listeners.Count -eq 0)}
    Save-Json 'resource-exit.json' $result
    if($listeners.Count){throw 'Port listener remains; unknown owner must not be killed'}
    return $result
}
