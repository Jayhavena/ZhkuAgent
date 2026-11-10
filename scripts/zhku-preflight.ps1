#requires -Version 5.1
# 点号调用会保留凭证继承；Docker/数据库就绪逻辑只由统一入口维护。
[CmdletBinding()]
param(
    [switch]$StartDocker,
    [switch]$CheckOnly,
    [switch]$DockerOnly,
    [switch]$RepairConfiguration,
    [string]$DataRoot,
    [ValidateRange(1, 600)][int]$TimeoutSeconds = 180
)
$ErrorActionPreference = 'Stop'
if (-not $DockerOnly) {
    & (Join-Path $PSScriptRoot 'Import-ZhkuCredentials.ps1') -UseDatabase -UseDashScope
}
# StartDocker 保留旧调用兼容性；现在默认按用户授权自动拉起，无需反复确认。
& (Join-Path $PSScriptRoot '../knowledge/scripts/ensure-zhku-postgres.ps1') `
    -TimeoutSeconds $TimeoutSeconds -DataRoot $DataRoot -DockerOnly:$DockerOnly `
    -RepairConfiguration:$RepairConfiguration -CheckOnly:$CheckOnly
Write-Host '[preflight] Readiness verified. Database-writing tests must use a dedicated temporary database.'
