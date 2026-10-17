[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Check')][string]$Action = 'Start',
    [int]$BackendPort,
    [ValidateRange(1024, 65535)][int]$WebPort = 8080,
    [ValidatePattern('^[a-z0-9][a-z0-9_-]{0,62}$')][string]$ProjectName = 'zhku-web-preview',
    [ValidateSet('student', 'engineering', 'replay')][string]$EntryMode,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$previewRoot = $PSScriptRoot
$composeFile = Join-Path $previewRoot 'compose.preview.yaml'
$savedBackend = $env:ZHKU_CONTROLLED_BACKEND_PORT
$savedWeb = $env:ZHKU_WEB_PORT
$created = $false
try {
    # 模式写入构建产物；跳过构建不能保证磁盘上的页面与所选后端合同匹配。
    if ($Action -eq 'Start' -and $EntryMode -and $SkipBuild) {
        throw 'EntryMode requires a fresh frontend build; omit -SkipBuild.'
    }
    # Stop不需要真实后端；仅让Compose完成插值，绝不启动该端口的服务。
    if ($Action -ne 'Stop' -and ($BackendPort -lt 1024 -or $BackendPort -gt 65535)) {
        throw 'Provide -BackendPort with the actual controlled service port (1024..65535).'
    }
    $env:ZHKU_CONTROLLED_BACKEND_PORT = if ($Action -eq 'Stop') { '8123' } else { [string]$BackendPort }
    $env:ZHKU_WEB_PORT = [string]$WebPort
    $docker = (Get-Command docker.exe -ErrorAction Stop).Source
    $composeArgs = @('compose', '--project-name', $ProjectName, '--file', $composeFile)
    if ($Action -eq 'Stop') {
        & $docker @composeArgs down --timeout 10
        if ($LASTEXITCODE -ne 0) { throw 'Nginx preview cleanup failed.' }
        Write-Host 'Nginx preview stopped. The Java service and database are unchanged.'
        return
    }
    & $docker @composeArgs config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration is invalid.' }
    if ($Action -eq 'Check') {
        Write-Host 'Configuration valid. No build, container, database or model started.'
        return
    }
    # 名称冲突不覆盖既有展示容器；停止操作也只针对本项目的Compose资源。
    $existing = & $docker @composeArgs ps --all --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the preview project.' }
    if ($existing) { throw 'Preview already exists. Use -Action Stop before starting again.' }
    if (-not $SkipBuild) {
        . (Join-Path $previewRoot '../../scripts/Resolve-ZhkuNode.ps1')
        $nodeTools=Get-ZhkuNodeToolchain
        $nodePathBefore=$env:PATH
        $entryModeBefore=$env:VITE_ENTRY_MODE
        Push-Location $previewRoot
        try {
            $env:PATH=$nodeTools.Directory+';'+$nodePathBefore
            if ($EntryMode) { $env:VITE_ENTRY_MODE=$EntryMode }
            & $nodeTools.Npm run build
            if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
        } finally {
            $env:PATH=$nodePathBefore
            if ($null -eq $entryModeBefore) { Remove-Item Env:VITE_ENTRY_MODE -ErrorAction SilentlyContinue }
            else { $env:VITE_ENTRY_MODE=$entryModeBefore }
            Pop-Location
        }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $previewRoot 'dist/index.html') -PathType Leaf)) {
        throw 'Missing dist/index.html. Build the frontend first.'
    }
    # 后端由既有受控入口启动；这里不注入数据库密码或供应商密钥。
    & $docker @composeArgs up --detach --no-build --wait --wait-timeout 15
    $created = $true
    if ($LASTEXITCODE -ne 0) { throw 'Nginx preview did not start.' }
    & $docker @composeArgs exec --no-TTY web nginx -t
    if ($LASTEXITCODE -ne 0) { throw 'Nginx configuration check failed.' }
    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$WebPort/" -TimeoutSec 2
            $ready = $response.StatusCode -eq 200
        } catch { Start-Sleep -Milliseconds 200 }
    } while (-not $ready -and [DateTime]::UtcNow -lt $deadline)
    if (-not $ready) { throw 'Nginx page readiness failed.' }
    Write-Host "Nginx page ready: http://127.0.0.1:$WebPort/"
    Write-Host "Controlled API upstream: host.docker.internal:$BackendPort"
    Write-Host 'Only controlled v1/v2 namespaces are proxied; the selected backend determines which functions are available.'
    Write-Host "Stop this container with: .\start-nginx-preview.cmd -Action Stop -ProjectName $ProjectName"
} catch {
    if ($created) {
        & $docker @composeArgs down --timeout 10
        if ($LASTEXITCODE -ne 0) { Write-Warning "Cleanup failed; inspect only the $ProjectName project." }
    }
    Write-Error $_ -ErrorAction Continue
    exit 1
} finally {
    $env:ZHKU_CONTROLLED_BACKEND_PORT = $savedBackend
    $env:ZHKU_WEB_PORT = $savedWeb
}
