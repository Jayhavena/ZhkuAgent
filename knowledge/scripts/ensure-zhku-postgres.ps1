#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('zhkuagent-postgres')][string]$ContainerName = 'zhkuagent-postgres',
    [ValidateRange(0, 65535)][int]$Port = 0,
    [ValidateRange(1, 600)][int]$TimeoutSeconds = 180,
    [ValidateRange(1, 5000)][int]$PollIntervalMilliseconds = 500,
    [string]$DataRoot,
    [switch]$DockerOnly,
    [switch]$RepairConfiguration,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ZhkuPostgres.ps1')
Ensure-ZhkuPostgres -Port $Port -DataRoot $DataRoot -TimeoutSeconds $TimeoutSeconds `
    -PollIntervalMilliseconds $PollIntervalMilliseconds -DockerOnly:$DockerOnly `
    -RepairConfiguration:$RepairConfiguration -CheckOnly:$CheckOnly
