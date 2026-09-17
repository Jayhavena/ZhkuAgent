param(
    [string]$ContainerName = 'zhkuagent-postgres',
    [int]$Port = 54329,
    [ValidateRange(1, 300)][int]$TimeoutSeconds = 60,
    [ValidateRange(1, 5000)][int]$PollIntervalMilliseconds = 500
)

$ErrorActionPreference = 'Stop'

function Invoke-DockerCommand {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& docker.exe @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        $details = ($output | Out-String).Trim()
        throw "Docker command failed ($($Arguments -join ' ')): $details"
    }
    return ($output | Out-String).Trim()
}

function Get-ContainerState {
    param([Parameter(Mandatory = $true)][string]$Name)

    $state = Invoke-DockerCommand @(
        'inspect',
        '--format',
        '{{.State.Running}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}',
        $Name
    )
    $parts = $state -split '\|', 2
    if ($parts.Count -ne 2) {
        throw "Docker returned an unreadable state for container $Name."
    }
    return [pscustomobject]@{
        Running = [System.Convert]::ToBoolean($parts[0])
        Health = $parts[1]
    }
}

# 只启动已存在的固定容器；调用 compose 可能因缺失 .env 创建不同配置或卷。
Invoke-DockerCommand @('info', '--format', '{{.ServerVersion}}') | Out-Null
$initialState = Get-ContainerState -Name $ContainerName
if (-not $initialState.Running) {
    Invoke-DockerCommand @('start', $ContainerName) | Out-Null
}

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    $state = Get-ContainerState -Name $ContainerName
    $portReady = Test-NetConnection -ComputerName '127.0.0.1' -Port $Port -InformationLevel Quiet
    if ($state.Running -and $state.Health -eq 'healthy' -and $portReady) {
        Write-Output "PostgreSQL ready: container=$ContainerName port=$Port"
        exit 0
    }
    Start-Sleep -Milliseconds $PollIntervalMilliseconds
} while ([DateTimeOffset]::UtcNow -lt $deadline)

throw "PostgreSQL container $ContainerName did not become healthy on 127.0.0.1:$Port within $TimeoutSeconds seconds."
