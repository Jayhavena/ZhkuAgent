param(
    [ValidateSet('preview', 'admin')][string]$Mode = 'preview',
    [Parameter(Mandatory = $true)][string]$DataRoot,
    [int]$Port = 0,
    [string]$CurrentAcademicTerm,
    [string]$CourseSelectionOfficialQueryUrl,
    [switch]$EnableRag,
    [switch]$EnableCampusAgent
)

$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = (Resolve-Path -LiteralPath $DataRoot).Path
$envFile = Join-Path $runtimeRoot 'infra/.env'

$hasTerm = -not [string]::IsNullOrWhiteSpace($CurrentAcademicTerm)
$hasQueryUrl = -not [string]::IsNullOrWhiteSpace($CourseSelectionOfficialQueryUrl)
if ($hasTerm -xor $hasQueryUrl) {
    throw 'CurrentAcademicTerm and CourseSelectionOfficialQueryUrl must be supplied together.'
}
if (($EnableRag -or $EnableCampusAgent) -and $Mode -ne 'preview') {
    throw 'RAG and campus Agent switches are preview-only.'
}
if ($EnableCampusAgent -and -not $EnableRag) {
    throw 'Campus Agent requires -EnableRag.'
}

$previousPassword = [Environment]::GetEnvironmentVariable('ZHKU_DB_PASSWORD', 'Process')
$previousApiKey = [Environment]::GetEnvironmentVariable('DASHSCOPE_API_KEY', 'Process')
try {
    & (Join-Path (Split-Path $moduleRoot -Parent) 'scripts/Import-ZhkuCredentials.ps1') `
        -UseDatabase -RequireDashScope:$EnableRag
    if ([string]::IsNullOrWhiteSpace($env:ZHKU_DB_PASSWORD)) {
        # 旧数据目录仍可启动；已配置的进程/用户/系统环境变量不得被 .env 覆盖。
        if (-not (Test-Path -LiteralPath $envFile)) {
            throw '当前环境无 ZHKU_DB_PASSWORD，且数据目录缺少兼容回退文件 infra/.env。'
        }
        $passwordLine = @(Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' })
        if ($passwordLine.Count -ne 1) {
            throw 'ZHKU_DB_PASSWORD must be present exactly once in infra/.env.'
        }
        $env:ZHKU_DB_PASSWORD = $passwordLine[0] -replace '^ZHKU_DB_PASSWORD=', ''
        if ([string]::IsNullOrWhiteSpace($env:ZHKU_DB_PASSWORD)) {
            throw 'ZHKU_DB_PASSWORD in infra/.env is empty.'
        }
    }

    $jar = Join-Path $moduleRoot ("target/knowledge-0.0.1-SNAPSHOT-" + $Mode + ".jar")
    if (-not (Test-Path -LiteralPath $jar)) {
        throw 'Build the knowledge module before starting this service.'
    }

    $javaArguments = @()
    $javaArguments += '-jar'
    $javaArguments += $jar
    $javaArguments += '--server.address=127.0.0.1'
    $javaArguments += ('--zhku.local-admin.storage-directory=' + (Join-Path $runtimeRoot 'data/local-admin'))
    if ($Mode -eq 'preview') {
        if ($EnableRag) { $javaArguments += '--zhku.rag.enabled=true' }
        if ($EnableCampusAgent) { $javaArguments += '--zhku.campus-agent.enabled=true' }
        if ($hasTerm) {
            $javaArguments += ('--zhku.local-preview.current-academic-term=' + $CurrentAcademicTerm)
            $javaArguments += ('--zhku.local-preview.course-selection-official-query-url=' + $CourseSelectionOfficialQueryUrl)
            Write-Host ('Dynamic course-selection notice enabled for term: ' + $CurrentAcademicTerm)
        }
        else {
            Write-Host 'Dynamic course-selection notice disabled: no term and official query URL supplied.'
        }
    }
    if ($Port -gt 0) {
        $javaArguments += ('--server.port=' + $Port)
    }

    Push-Location -LiteralPath $runtimeRoot
    try {
        & java @javaArguments
        $serviceExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($serviceExitCode -ne 0) {
        throw 'The knowledge service exited unexpectedly.'
    }
} finally {
    # PowerShell 对 null 赋值可能留下空变量；原本不存在时应真正移除。
    if ($null -eq $previousPassword) {
        Remove-Item Env:ZHKU_DB_PASSWORD -ErrorAction SilentlyContinue
    } else {
        [Environment]::SetEnvironmentVariable('ZHKU_DB_PASSWORD', $previousPassword, 'Process')
    }
    if ($null -eq $previousApiKey) {
        Remove-Item Env:DASHSCOPE_API_KEY -ErrorAction SilentlyContinue
    } else {
        [Environment]::SetEnvironmentVariable('DASHSCOPE_API_KEY', $previousApiKey, 'Process')
    }
}
