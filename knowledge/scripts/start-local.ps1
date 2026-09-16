param(
    [ValidateSet('preview', 'admin')][string]$Mode = 'preview',
    [Parameter(Mandatory = $true)][string]$DataRoot,
    [int]$Port = 0
)
$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path $PSScriptRoot -Parent
$runtimeRoot = (Resolve-Path -LiteralPath $DataRoot).Path
$envFile = Join-Path $runtimeRoot 'infra/.env'
# Read configuration only from the explicitly selected runtime directory.
if (-not (Test-Path -LiteralPath $envFile)) { throw 'The selected runtime directory is missing infra/.env.' }
$line = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }
if (@($line).Count -ne 1) { throw 'ZHKU_DB_PASSWORD must be present exactly once in infra/.env.' }
$previousPassword = $env:ZHKU_DB_PASSWORD
try {
    $env:ZHKU_DB_PASSWORD = $line -replace '^ZHKU_DB_PASSWORD=', ''
    $jar = Join-Path $moduleRoot "target/knowledge-0.0.1-SNAPSHOT-$Mode.jar"
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the knowledge module before starting this service.' }
    $arguments = @('-jar', $jar, '--server.address=127.0.0.1', "--zhku.local-admin.storage-directory=$(Join-Path $runtimeRoot 'data/local-admin')")
    if ($Port -gt 0) { $arguments += "--server.port=$Port" }
    Push-Location -LiteralPath $runtimeRoot
    try { & java @arguments; if ($LASTEXITCODE -ne 0) { throw 'The knowledge service exited unexpectedly.' } }
    finally { Pop-Location }
} finally { $env:ZHKU_DB_PASSWORD = $previousPassword }
