param(
    [string]$CandidateRoot = $PSScriptRoot,
    [string]$RunId = (Get-Date -Format "yyyyMMdd-HHmmss")
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($RunId -notmatch '^[A-Za-z0-9_-]{1,40}$') { throw 'RunId may contain only letters, digits, underscore and hyphen.' }
$CandidateRoot = (Resolve-Path -LiteralPath $CandidateRoot).Path
$repoRoot = (& git -C $CandidateRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
    throw 'Run this script from a candidate directory inside the ZhkuAgent Git worktree.'
}
$repoRoot = $repoRoot.Trim()
if ([string]::IsNullOrWhiteSpace($env:ZHKU_DB_PASSWORD)) {
    throw 'ZHKU_DB_PASSWORD is missing; set it in the process environment. Secret values are never printed.'
}

$manifestPath = Join-Path $CandidateRoot 'manifest.json'
$casesPath = Join-Path $CandidateRoot 'cases.json'
$snapshotPath = Join-Path $CandidateRoot 'corpus-snapshot.json'
$embeddingsPath = Join-Path $CandidateRoot 'query-embeddings.json'
$harnessPath = Join-Path $CandidateRoot 'Rc2RetrievalEvaluationHarnessTest.java'
$scorerPath = Join-Path $CandidateRoot 'score-v2-results.ps1'
foreach ($path in @($manifestPath,$casesPath,$snapshotPath,$embeddingsPath,$harnessPath,$scorerPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required input missing: $path" }
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.datasetVersion -ne 'v2.0.0-rc2' -or
    $manifest.databaseCandidate.targetContainer -ne 'zhkuagent-eval-v2-rc2' -or
    $manifest.databaseCandidate.targetDatabase -ne 'zhkuagent_eval_v2_rc2') {
    throw 'Candidate manifest does not identify the isolated rc2 evaluation target.'
}

$container = @(docker inspect zhkuagent-eval-v2-rc2 | ConvertFrom-Json)[0]
if ($container.State.Status -ne 'running') { throw 'Isolated rc2 PostgreSQL container is not running.' }
if ($container.Config.Env -notcontains 'POSTGRES_DB=zhkuagent_eval_v2_rc2') {
    throw 'Container default database is not the dedicated rc2 database.'
}
$portBindings = $container.NetworkSettings.Ports.'5432/tcp'
if (@($portBindings | Where-Object { $_.HostIp -eq '127.0.0.1' -and $_.HostPort -eq '55442' }).Count -ne 1) {
    throw 'Container does not have the expected loopback-only 127.0.0.1:55442 binding.'
}

$runRoot = Join-Path $env:TEMP ('zhkuagent-rc2-replay-' + $RunId)
if (Test-Path -LiteralPath $runRoot) { throw "Replay directory already exists: $runRoot" }
New-Item -ItemType Directory -Path $runRoot | Out-Null
foreach ($name in @('cases.json','corpus-snapshot.json','query-embeddings.json','score-v2-results.ps1','Rc2RetrievalEvaluationHarnessTest.java')) {
    Copy-Item -LiteralPath (Join-Path $CandidateRoot $name) -Destination (Join-Path $runRoot $name)
}

$testDir = Join-Path $repoRoot 'knowledge\src\test\java\com\zhku\knowledge\eval'
$temporaryTestPath = Join-Path $testDir 'Rc2RetrievalEvaluationHarnessTest.java'
if (Test-Path -LiteralPath $temporaryTestPath) {
    Remove-Item -LiteralPath $runRoot -Recurse -Force
    throw 'A temporary harness test already exists; refusing to overwrite it.'
}
New-Item -ItemType Directory -Force -Path $testDir | Out-Null
Copy-Item -LiteralPath $harnessPath -Destination $temporaryTestPath
try {
    Set-Location -LiteralPath $repoRoot
    & (Join-Path $repoRoot 'mvnw.cmd') -pl knowledge '-Dtest=Rc2RetrievalEvaluationHarnessTest' '-DfailIfNoTests=false' ("-Dzhku.rc2.root=" + $runRoot) test
    if ($LASTEXITCODE -ne 0) { throw "Maven retrieval replay failed with exit code $LASTEXITCODE" }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $runRoot 'score-v2-results.ps1') -ResultsPath (Join-Path $runRoot 'retrieval-results-v2-rc2.json') -OutputPath (Join-Path $runRoot 'scored-results-v2-rc2.json')
    if ($LASTEXITCODE -ne 0) { throw "Scoring replay failed with exit code $LASTEXITCODE" }
    Write-Output ('REPLAY_STATUS=PASS')
    Write-Output ('REPLAY_OUTPUT_DIRECTORY=' + $runRoot)
    Write-Output ('QUERY_EMBEDDINGS_SHA256=' + (Get-FileHash -LiteralPath (Join-Path $runRoot 'query-embeddings.json') -Algorithm SHA256).Hash.ToLowerInvariant())
    Write-Output ('RESULTS_SHA256=' + (Get-FileHash -LiteralPath (Join-Path $runRoot 'retrieval-results-v2-rc2.json') -Algorithm SHA256).Hash.ToLowerInvariant())
    Write-Output ('SCORED_RESULTS_SHA256=' + (Get-FileHash -LiteralPath (Join-Path $runRoot 'scored-results-v2-rc2.json') -Algorithm SHA256).Hash.ToLowerInvariant())
    Write-Output 'EMBEDDING_API_CALLS_DURING_REPLAY=0'
    Write-Output 'SECRET_VALUES_PRINTED=false'
} finally {
    if (Test-Path -LiteralPath $temporaryTestPath) { Remove-Item -LiteralPath $temporaryTestPath -Force }
}