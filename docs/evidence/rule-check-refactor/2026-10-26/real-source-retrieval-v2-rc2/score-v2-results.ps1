param(
    [Parameter(Mandatory = $true)]
    [string]$ResultsPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$casesPath = Join-Path $root 'cases.json'
$snapshotPath = Join-Path $root 'corpus-snapshot.json'
if (-not (Test-Path -LiteralPath $ResultsPath -PathType Leaf)) { throw "Results file missing: $ResultsPath" }
if (-not (Test-Path -LiteralPath $casesPath -PathType Leaf)) { throw "Dataset missing: $casesPath" }
if (-not (Test-Path -LiteralPath $snapshotPath -PathType Leaf)) { throw "Corpus snapshot missing: $snapshotPath" }
if (Test-Path -LiteralPath $OutputPath) { throw "Refusing to overwrite score output: $OutputPath" }

$dataset = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($casesPath, [Text.Encoding]::UTF8))
$snapshot = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($snapshotPath, [Text.Encoding]::UTF8))
$resultsDoc = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText((Resolve-Path -LiteralPath $ResultsPath), [Text.Encoding]::UTF8))

if ($resultsDoc.datasetVersion -ne $dataset.datasetVersion) { throw 'Result datasetVersion mismatch.' }
if ($resultsDoc.corpusFingerprint -ne $snapshot.corpusFingerprint.value) { throw 'Result corpusFingerprint mismatch.' }
if ($resultsDoc.retrievalConfig.resultLimitK -ne 5 -or
    $resultsDoc.retrievalConfig.candidateLimitPerPath -ne 8 -or
    $resultsDoc.retrievalConfig.rrfK -ne 60) {
    throw 'Result retrieval configuration differs from the v2 comparison contract.'
}

$allChunks = @($snapshot.chunks)
$chunkById = @{}
foreach ($chunk in $allChunks) { $chunkById[[string]$chunk.chunkId] = $chunk }
$caseById = @{}
foreach ($case in @($dataset.cases)) { $caseById[[string]$case.caseId] = $case }
$positiveIds = @($dataset.cases | Where-Object { $_.caseClass -eq 'POSITIVE_RETRIEVAL' } | ForEach-Object { [string]$_.caseId } | Sort-Object)
$authIds = @($dataset.cases | Where-Object { $_.caseClass -eq 'AUTHORIZATION_EXCLUSION' } | ForEach-Object { [string]$_.caseId } | Sort-Object)
$scopeIds = @($dataset.cases | Where-Object { $_.caseClass -eq 'SCOPE_CONTRACT' } | ForEach-Object { [string]$_.caseId } | Sort-Object)
$requiredAlgorithms = @('LEXICAL', 'VECTOR', 'RRF_HYBRID')
$algorithmsPresent = @($resultsDoc.runs | ForEach-Object { [string]$_.algorithm } | Sort-Object -Unique)
foreach ($algorithm in $requiredAlgorithms) {
    if ($algorithmsPresent -notcontains $algorithm) { throw "Missing required algorithm run: $algorithm" }
}

$runKeys = @()
$algorithmSummaries = @()
$globalGateTotals = [ordered]@{
    forbiddenChunkLeakCount = 0
    unknownChunkCount = 0
    checksumMismatchCount = 0
    sourceBindingMismatchCount = 0
    scopeViolationCount = 0
    authorizationDenyListHitCount = 0
}
foreach ($run in @($resultsDoc.runs)) {
    $mode = [string]$run.scopeMode
    $algorithm = [string]$run.algorithm
    if ($mode -notin @('ALL_ELIGIBLE_STUDENT_VISIBLE', 'CONFIGURED_PUBLIC_ELECTIVE')) {
        throw "Unknown scopeMode: $mode"
    }
    $runKey = "$mode|$algorithm"
    if ($runKeys -contains $runKey) { throw "Duplicate algorithm/scope run: $runKey" }
    $runKeys += $runKey

    $caseResults = @($run.caseResults)
    $resultCaseIds = @($caseResults | ForEach-Object { [string]$_.caseId })
    if (@($resultCaseIds | Sort-Object -Unique).Count -ne $resultCaseIds.Count) {
        throw "Duplicate case result in run $runKey"
    }
    if ($mode -eq 'ALL_ELIGIBLE_STUDENT_VISIBLE') {
        $expectedIds = @($positiveIds + $authIds | Sort-Object)
    } else {
        $expectedIds = @($scopeIds | Sort-Object)
    }
    $missingIds = @($expectedIds | Where-Object { $resultCaseIds -notcontains $_ })
    $unexpectedIds = @($resultCaseIds | Where-Object { $expectedIds -notcontains $_ })
    if ($missingIds.Count -gt 0 -or $unexpectedIds.Count -gt 0) {
        throw "Case coverage mismatch for $runKey. Missing=[$($missingIds -join ',')], unexpected=[$($unexpectedIds -join ',')]"
    }

    $runGateTotals = [ordered]@{
        forbiddenChunkLeakCount = 0
        unknownChunkCount = 0
        checksumMismatchCount = 0
        sourceBindingMismatchCount = 0
        scopeViolationCount = 0
        authorizationDenyListHitCount = 0
    }
    $caseMetrics = @()
    foreach ($caseResult in $caseResults) {
        $caseId = [string]$caseResult.caseId
        $case = $caseById[$caseId]
        $rankedChunks = @($caseResult.rankedChunks)
        if ($rankedChunks.Count -gt 5) { throw "More than Top 5 results returned for $runKey / $caseId" }
        $rankedIds = @()
        foreach ($hit in $rankedChunks) {
            $chunkId = [string]$hit.chunkId
            if ([string]::IsNullOrWhiteSpace($chunkId)) { throw "Result without chunkId: $runKey / $caseId" }
            if ($rankedIds -contains $chunkId) { throw "Duplicate chunk in ranking: $runKey / $caseId / $chunkId" }
            $rankedIds += $chunkId

            if (-not $chunkById.ContainsKey($chunkId)) {
                $runGateTotals.unknownChunkCount++
                continue
            }
            $goldChunk = $chunkById[$chunkId]
            if ($goldChunk.eligibleForStudentRetrieval -ne $true) {
                $runGateTotals.forbiddenChunkLeakCount++
            }
            if ([string]$hit.contentChecksum -ne [string]$goldChunk.contentChecksum) {
                $runGateTotals.checksumMismatchCount++
            }
            if ([string]$hit.sourceId -ne [string]$goldChunk.sourceId -or
                [string]$hit.knowledgeScope -ne [string]$goldChunk.knowledgeScope -or
                [int]$hit.pageNumber -ne [int]$goldChunk.pageNumber -or
                [string]$hit.sourceLocator -ne [string]$goldChunk.sourceLocator) {
                $runGateTotals.sourceBindingMismatchCount++
            }
            if ($mode -eq 'CONFIGURED_PUBLIC_ELECTIVE' -and
                [string]$goldChunk.knowledgeScope -ne 'PUBLIC_ELECTIVE') {
                $runGateTotals.scopeViolationCount++
            }
        }

        if ($case.caseClass -eq 'AUTHORIZATION_EXCLUSION') {
            foreach ($denyId in @($case.mustNotReturnChunkIds)) {
                if ($rankedIds -contains [string]$denyId) {
                    $runGateTotals.authorizationDenyListHitCount++
                }
            }
        }
        if ($case.caseClass -eq 'POSITIVE_RETRIEVAL') {
            if ($mode -ne 'ALL_ELIGIBLE_STUDENT_VISIBLE') {
                throw "Positive retrieval case executed in wrong scope mode: $caseId"
            }
            $requiredIdsForCase = @($case.requiredEvidence | ForEach-Object { [string]$_.chunkId } | Sort-Object -Unique)
            $hits = 0
            $firstRank = $null
            foreach ($requiredId in $requiredIdsForCase) {
                $rank = [Array]::IndexOf([string[]]$rankedIds, $requiredId)
                if ($rank -ge 0) {
                    $hits++
                    if ($null -eq $firstRank -or ($rank + 1) -lt $firstRank) { $firstRank = $rank + 1 }
                }
            }
            $recall = if ($requiredIdsForCase.Count -gt 0) { [double]$hits / [double]$requiredIdsForCase.Count } else { 0.0 }
            $reciprocalRank = if ($null -ne $firstRank) { 1.0 / [double]$firstRank } else { 0.0 }
            $complete = ($hits -eq $requiredIdsForCase.Count)
            $caseMetrics += [pscustomobject]@{
                caseId = $caseId
                queryFamily = $case.queryFamily
                requiredEvidenceCount = $requiredIdsForCase.Count
                hitEvidenceCountAt5 = $hits
                recallAt5 = [Math]::Round($recall, 6)
                reciprocalRank = [Math]::Round($reciprocalRank, 6)
                completeEvidenceSetAt5 = $complete
                rankedChunkIds = $rankedIds
            }
        }
    }
    foreach ($key in @($runGateTotals.Keys)) {
        $globalGateTotals[$key] += [int]$runGateTotals[$key]
    }

    if ($mode -eq 'ALL_ELIGIBLE_STUDENT_VISIBLE') {
        $recallMean = ($caseMetrics | Measure-Object -Property recallAt5 -Average).Average
        $mrrMean = ($caseMetrics | Measure-Object -Property reciprocalRank -Average).Average
        $completeMean = ($caseMetrics | Where-Object { $_.completeEvidenceSetAt5 } | Measure-Object).Count / [double]$caseMetrics.Count
        $algorithmSummaries += [pscustomobject]@{
            algorithm = $algorithm
            scopeMode = $mode
            scoredCaseCount = $caseMetrics.Count
            macroRecallAt5 = [Math]::Round([double]$recallMean, 6)
            mrr = [Math]::Round([double]$mrrMean, 6)
            completeEvidenceSetRateAt5 = [Math]::Round([double]$completeMean, 6)
            caseMetrics = $caseMetrics
            gateCounts = $runGateTotals
        }
    } else {
        $algorithmSummaries += [pscustomobject]@{
            algorithm = $algorithm
            scopeMode = $mode
            scoredCaseCount = 0
            macroRecallAt5 = $null
            mrr = $null
            completeEvidenceSetRateAt5 = $null
            caseMetrics = @()
            gateCounts = $runGateTotals
        }
    }
}

foreach ($algorithm in $requiredAlgorithms) {
    foreach ($mode in @('ALL_ELIGIBLE_STUDENT_VISIBLE', 'CONFIGURED_PUBLIC_ELECTIVE')) {
        if ($runKeys -notcontains "$mode|$algorithm") {
            throw "Missing required run: $mode / $algorithm"
        }
    }
}
$gateFailures = 0
foreach ($value in $globalGateTotals.Values) { $gateFailures += [int]$value }
$report = [ordered]@{
    reportVersion = 1
    reportStatus = if ($gateFailures -eq 0) { 'SCORED_GATES_PASS' } else { 'SCORED_GATES_FAIL' }
    generatedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK')
    datasetVersion = $dataset.datasetVersion
    corpusFingerprint = $snapshot.corpusFingerprint.value
    inputResultsSha256 = (Get-FileHash -LiteralPath (Resolve-Path -LiteralPath $ResultsPath) -Algorithm SHA256).Hash.ToLowerInvariant()
    retrievalConfig = $resultsDoc.retrievalConfig
    primaryMetricsScope = 'ALL_ELIGIBLE_STUDENT_VISIBLE'
    metrics = $algorithmSummaries
    globalGateTotals = $globalGateTotals
    limitations = @(
        'Development comparison only; this score does not turn the small eight-chunk corpus into a production-representative benchmark.',
        'Strict locator accuracy is not scored because database source-to-PDF binding is unverified.',
        'Authorization exclusion is a contract gate only; excluded text was intentionally not indexed or sent to an Embedding provider.'
    )
}
$outDir = Split-Path -Parent $OutputPath
if (-not (Test-Path -LiteralPath $outDir -PathType Container)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$json = ConvertTo-Json -InputObject $report -Depth 40
$json = $json.Replace(([string][char]13+[string][char]10), [string][char]10).Replace([string][char]13, [string][char]10)
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($OutputPath, $json + [string][char]10, $utf8)

Write-Output "REPORT_STATUS=$($report.reportStatus)"
foreach ($metric in @($algorithmSummaries | Where-Object { $_.scopeMode -eq 'ALL_ELIGIBLE_STUDENT_VISIBLE' })) {
    Write-Output ("{0}: cases={1}, Recall@5={2}, MRR={3}, CompleteEvidenceSetRate@5={4}" -f
        $metric.algorithm, $metric.scoredCaseCount, $metric.macroRecallAt5, $metric.mrr, $metric.completeEvidenceSetRateAt5)
}
Write-Output ("GATES: forbidden={0}, unknown={1}, checksumMismatch={2}, sourceBindingMismatch={3}, scopeViolation={4}, authDenyListHit={5}" -f
    $globalGateTotals.forbiddenChunkLeakCount, $globalGateTotals.unknownChunkCount,
    $globalGateTotals.checksumMismatchCount, $globalGateTotals.sourceBindingMismatchCount,
    $globalGateTotals.scopeViolationCount, $globalGateTotals.authorizationDenyListHitCount)
Write-Output "OUTPUT=$OutputPath"