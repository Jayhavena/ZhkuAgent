param(
    [ValidateSet('preview', 'admin')][string]$Mode = 'preview',
    [Parameter(Mandatory = $true)][string]$DataRoot,
    [int]$Port = 0
)
$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path $PSScriptRoot -Parent
$runtimeRoot = (Resolve-Path -LiteralPath $DataRoot).Path
$envFile = Join-Path $runtimeRoot 'infra/.env'
# 仅从指定的原运行目录取配置，不复制密码、PDF 或数据卷。
if (-not (Test-Path -LiteralPath $envFile)) { throw '指定运行目录缺少 infra/.env。' }
$line = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }
if (@($line).Count -ne 1) { throw '数据库密码配置必须存在且唯一。' }
$previousPassword = $env:ZHKU_DB_PASSWORD
try {
    $env:ZHKU_DB_PASSWORD = $line -replace '^ZHKU_DB_PASSWORD=', ''
    $jar = Join-Path $moduleRoot "target/knowledge-0.0.1-SNAPSHOT-$Mode.jar"
    if (-not (Test-Path -LiteralPath $jar)) { throw '请先打包 knowledge 模块。' }
    $arguments = @('-jar', $jar, '--server.address=127.0.0.1', "--zhku.local-admin.storage-directory=$(Join-Path $runtimeRoot 'data/local-admin')")
    if ($Port -gt 0) { $arguments += "--server.port=$Port" }
    Push-Location -LiteralPath $runtimeRoot
    try { & java @arguments; if ($LASTEXITCODE -ne 0) { throw '知识库进程异常退出。' } }
    finally { Pop-Location }
} finally { $env:ZHKU_DB_PASSWORD = $previousPassword }
