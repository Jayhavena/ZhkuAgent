param(
    [ValidateSet('preview', 'admin')][string]$Mode = 'preview',
    [Parameter(Mandatory = $true)][string]$DataRoot,
    [int]$Port = 0,
    [string]$CurrentAcademicTerm,
    [string]$CourseSelectionOfficialQueryUrl
)

$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = (Resolve-Path -LiteralPath $DataRoot).Path
$envFile = Join-Path $runtimeRoot 'infra/.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'The selected runtime directory is missing infra/.env.'
}

$passwordLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }
if (@($passwordLine).Count -ne 1) {
    throw 'ZHKU_DB_PASSWORD must be present exactly once in infra/.env.'
}

$hasTerm = -not [string]::IsNullOrWhiteSpace($CurrentAcademicTerm)
$hasQueryUrl = -not [string]::IsNullOrWhiteSpace($CourseSelectionOfficialQueryUrl)
if ($hasTerm -xor $hasQueryUrl) {
    throw 'CurrentAcademicTerm and CourseSelectionOfficialQueryUrl must be supplied together.'
}

$previousPassword = $env:ZHKU_DB_PASSWORD
$env:ZHKU_DB_PASSWORD = $passwordLine -replace '^ZHKU_DB_PASSWORD=', ''
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
& java @javaArguments
$serviceExitCode = $LASTEXITCODE
Pop-Location
$env:ZHKU_DB_PASSWORD = $previousPassword
if ($serviceExitCode -ne 0) {
    throw 'The knowledge service exited unexpectedly.'
}
