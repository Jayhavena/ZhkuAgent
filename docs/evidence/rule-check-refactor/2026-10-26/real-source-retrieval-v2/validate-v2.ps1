$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$casesPath = Join-Path $root 'cases.json'
$snapshotPath = Join-Path $root 'corpus-snapshot.json'
$readmePath = Join-Path $root 'README.md'
$manifestPath = Join-Path $root 'manifest.json'

foreach ($path in @($casesPath, $snapshotPath, $readmePath, $manifestPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required artifact missing: $path"
    }
}

$casesDoc = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($casesPath, [Text.Encoding]::UTF8))
$snapshot = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($snapshotPath, [Text.Encoding]::UTF8))
$manifest = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8))

if ($casesDoc.schemaVersion -ne 2 -or $casesDoc.datasetVersion -ne 'v2.0.0-rc1') {
    throw 'Unexpected v2 dataset schema/version.'
}
if ($casesDoc.status -ne 'CANDIDATE_PENDING_INDEPENDENT_REVIEW') {
    throw 'Dataset status must not imply independent review or final freeze.'
}
if ($casesDoc.sourceSnapshot.sha256 -ne $snapshot.sourceSnapshot.sha256) {
    throw 'Source snapshot hash differs between cases and corpus inventory.'
}
if ($casesDoc.corpusSnapshot.corpusFingerprint -ne $snapshot.corpusFingerprint.value) {
    throw 'Dataset references a different corpus fingerprint.'
}
if ($manifest.datasetVersion -ne $casesDoc.datasetVersion -or $manifest.corpusFingerprint -ne $snapshot.corpusFingerprint.value) {
    throw 'Manifest version or corpus fingerprint mismatch.'
}

# Recompute the canonical fingerprint over all 17 rows; excluded rows are metadata-only.
$canonicalLines = @()
foreach ($chunk in @($snapshot.chunks | Sort-Object chunkId)) {
    $sharing = ([string][bool]$chunk.sharingRightsConfirmed).ToLowerInvariant()
    $eligible = ([string][bool]$chunk.eligibleForStudentRetrieval).ToLowerInvariant()
    $canonicalLines += @(
        $chunk.chunkId,
        $chunk.sourceId,
        $chunk.contentChecksum,
        $chunk.sourceVersion,
        $chunk.publicationStatus,
        $chunk.visibilityScope,
        $sharing,
        $chunk.knowledgeScope,
        [string]$chunk.pageNumber,
        $chunk.sourceLocator,
        $eligible
    ) -join '|'
}
$canonical = ($canonicalLines -join ([string][char]10)) + ([string][char]10)
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $digest = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical))
} finally {
    $sha.Dispose()
}
$recomputedFingerprint = ([BitConverter]::ToString($digest)).Replace('-', '').ToLowerInvariant()
if ($recomputedFingerprint -ne $snapshot.corpusFingerprint.value) {
    throw "Corpus fingerprint mismatch: expected $($snapshot.corpusFingerprint.value), got $recomputedFingerprint"
}

$allChunks = @($snapshot.chunks)
$eligibleChunks = @($allChunks | Where-Object { $_.eligibleForStudentRetrieval -eq $true })
$excludedChunks = @($allChunks | Where-Object { $_.eligibleForStudentRetrieval -ne $true })
if ($allChunks.Count -ne 17 -or $eligibleChunks.Count -ne 8 -or $excludedChunks.Count -ne 9) {
    throw "Unexpected corpus counts: total=$($allChunks.Count), eligible=$($eligibleChunks.Count), excluded=$($excludedChunks.Count)"
}
if ($snapshot.counts.totalChunks -ne $allChunks.Count -or
    $snapshot.counts.eligibleChunks -ne $eligibleChunks.Count -or
    $snapshot.counts.excludedChunks -ne $excludedChunks.Count) {
    throw 'Corpus snapshot counts do not match inventory.'
}

# The two existing index profiles must refer to the same exact eight chunk IDs.
$eligibleIds = @($eligibleChunks | ForEach-Object { [string]$_.chunkId } | Sort-Object)
$eligibleIdCanonical = ($eligibleIds -join ([string][char]10)) + ([string][char]10)
$idSha = [Security.Cryptography.SHA256]::Create()
try {
    $idDigest = $idSha.ComputeHash([Text.Encoding]::UTF8.GetBytes($eligibleIdCanonical))
} finally {
    $idSha.Dispose()
}
$eligibleIdHash = ([BitConverter]::ToString($idDigest)).Replace('-', '').ToLowerInvariant()
if ($eligibleIdHash -ne $casesDoc.retrievalProtocol.eligibleChunkIdSetSha256) {
    throw 'Eligible chunk ID set hash mismatch.'
}
if ($casesDoc.retrievalProtocol.indexSnapshots.sameChunkIdSetVerified -ne $true -or
    $casesDoc.retrievalProtocol.indexSnapshots.lexical.entryCount -ne 8 -or
    $casesDoc.retrievalProtocol.indexSnapshots.lexical.entriesWithVectors -ne 0 -or
    $casesDoc.retrievalProtocol.indexSnapshots.vector.entryCount -ne 8 -or
    $casesDoc.retrievalProtocol.indexSnapshots.vector.entriesWithVectors -ne 8) {
    throw 'Recorded lexical/vector index snapshot does not match the agreed eight-chunk protocol.'
}
$gateIds = @($casesDoc.globalRetrievalGates.forbiddenChunkIds)
if ($gateIds.Count -ne $excludedChunks.Count) {
    throw 'Global forbidden chunk list must cover every excluded chunk.'
}
foreach ($id in $gateIds) {
    if (-not ($excludedChunks.chunkId -contains [string]$id)) {
        throw "Global forbidden chunk list includes an unknown/eligible ID: $id"
    }
}

$chunkById = @{}
foreach ($chunk in $allChunks) {
    if ($chunkById.ContainsKey([string]$chunk.chunkId)) {
        throw "Duplicate chunkId in corpus snapshot: $($chunk.chunkId)"
    }
    $chunkById[[string]$chunk.chunkId] = $chunk
    if ($chunk.eligibleForStudentRetrieval -eq $true) {
        if ([string]::IsNullOrWhiteSpace([string]$chunk.content)) {
            throw "Eligible chunk content missing: $($chunk.chunkId)"
        }
    } elseif ($chunk.PSObject.Properties.Name -contains 'content') {
        throw "Excluded chunk content must not be copied into this snapshot: $($chunk.chunkId)"
    }
}

$caseIds = @()
$positiveCases = @()
$authCases = @()
$scopeCases = @()
$usedPositiveChunkIds = @{}
foreach ($case in @($casesDoc.cases)) {
    if ([string]::IsNullOrWhiteSpace([string]$case.caseId)) {
        throw 'Case without caseId.'
    }
    if ($caseIds -contains [string]$case.caseId) {
        throw "Duplicate caseId: $($case.caseId)"
    }
    $caseIds += [string]$case.caseId

    switch ([string]$case.caseClass) {
        'POSITIVE_RETRIEVAL' {
            $positiveCases += $case
            if ($case.split -ne 'DEVELOPMENT') {
                throw "Scored case must remain in DEVELOPMENT: $($case.caseId)"
            }
            if (@($case.requiredEvidence).Count -lt 1) {
                throw "Positive case has no required evidence: $($case.caseId)"
            }
            if ($case.scoring.recallAt5 -ne $true -or
                $case.scoring.mrr -ne $true -or
                $case.scoring.completeEvidenceSetAt5 -ne $true) {
                throw "Positive case has inconsistent metric contract: $($case.caseId)"
            }
            if ($case.scoring.strictLocatorAccuracy -ne $false) {
                throw "Strict locator scoring must remain disabled pending source binding: $($case.caseId)"
            }
            foreach ($evidence in @($case.requiredEvidence)) {
                $id = [string]$evidence.chunkId
                if (-not $chunkById.ContainsKey($id)) {
                    throw "Gold chunk absent from corpus snapshot: case=$($case.caseId), chunk=$id"
                }
                $chunk = $chunkById[$id]
                if ($chunk.eligibleForStudentRetrieval -ne $true) {
                    throw "Ineligible chunk used as positive gold: case=$($case.caseId), chunk=$id"
                }
                if ($evidence.sourceId -ne $chunk.sourceId -or
                    $evidence.contentChecksum.ToUpperInvariant() -ne $chunk.contentChecksum.ToUpperInvariant() -or
                    $evidence.knowledgeScope -ne $chunk.knowledgeScope -or
                    [int]$evidence.pageNumber -ne [int]$chunk.pageNumber -or
                    $evidence.sourceLocator -ne $chunk.sourceLocator) {
                    throw "Gold binding mismatch: case=$($case.caseId), chunk=$id"
                }
                $usedPositiveChunkIds[$id] = $true
            }
        }
        'AUTHORIZATION_EXCLUSION' {
            $authCases += $case
            if (@($case.requiredEvidence).Count -ne 0 -or
                $case.scoring.recallAt5 -ne $false -or
                $case.scoring.mrr -ne $false -or
                $case.scoring.countAsSecurityGate -ne $true) {
                throw "Authorization case must not enter retrieval metrics: $($case.caseId)"
            }
            foreach ($idValue in @($case.mustNotReturnChunkIds)) {
                $id = [string]$idValue
                if (-not $chunkById.ContainsKey($id) -or $chunkById[$id].eligibleForStudentRetrieval -eq $true) {
                    throw "Authorization deny-list contains unknown/eligible chunk: case=$($case.caseId), chunk=$id"
                }
            }
            foreach ($idValue in @($case.restrictedCandidateChunkIds)) {
                $id = [string]$idValue
                if (-not $chunkById.ContainsKey($id) -or $chunkById[$id].eligibleForStudentRetrieval -eq $true) {
                    throw "Restricted candidate is not an excluded chunk: case=$($case.caseId), chunk=$id"
                }
            }
        }
        'SCOPE_CONTRACT' {
            $scopeCases += $case
            if ($case.scoring.recallAt5 -ne $false -or
                $case.scoring.mrr -ne $false -or
                $case.scoring.countAsScopeGate -ne $true) {
                throw "Scope case must not enter retrieval metrics: $($case.caseId)"
            }
            foreach ($evidence in @($case.outOfScopeEvidence)) {
                $id = [string]$evidence.chunkId
                if (-not $chunkById.ContainsKey($id) -or $chunkById[$id].eligibleForStudentRetrieval -ne $true) {
                    throw "Scope target must be a real eligible chunk: case=$($case.caseId), chunk=$id"
                }
                if ($chunkById[$id].knowledgeScope -eq $case.requiredScope) {
                    throw "Scope target is not actually out of scope: case=$($case.caseId), chunk=$id"
                }
                if (@($case.mustNotReturnChunkIds) -notcontains $id) {
                    throw "Out-of-scope chunk missing from deny list: case=$($case.caseId), chunk=$id"
                }
            }
        }
        default {
            throw "Unknown caseClass: $($case.caseClass)"
        }
    }
}

if ($positiveCases.Count -ne 15 -or $authCases.Count -ne 1 -or $scopeCases.Count -ne 2 -or @($casesDoc.cases).Count -ne 18) {
    throw "Unexpected case counts: positive=$($positiveCases.Count), auth=$($authCases.Count), scope=$($scopeCases.Count), total=$(@($casesDoc.cases).Count)"
}
foreach ($chunk in $eligibleChunks) {
    if (-not $usedPositiveChunkIds.ContainsKey([string]$chunk.chunkId)) {
        throw "Eligible chunk has no positive retrieval case: $($chunk.chunkId)"
    }
}

# Every file hash in the manifest must match the on-disk artifact.
$hashChecks = @(
    @{ name = 'cases.json'; path = $casesPath; expected = [string]$manifest.artifacts.casesSha256 },
    @{ name = 'corpus-snapshot.json'; path = $snapshotPath; expected = [string]$manifest.artifacts.corpusSnapshotSha256 },
    @{ name = 'README.md'; path = $readmePath; expected = [string]$manifest.artifacts.readmeSha256 },
    @{ name = 'validate-v2.ps1'; path = $MyInvocation.MyCommand.Path; expected = [string]$manifest.artifacts.validatorSha256 },
    @{ name = 'score-v2-results.ps1'; path = (Join-Path $root 'score-v2-results.ps1'); expected = [string]$manifest.artifacts.scorerSha256 }
)
foreach ($item in $hashChecks) {
    $actual = (Get-FileHash -LiteralPath $item.path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $item.expected.ToLowerInvariant()) {
        throw "Artifact SHA-256 mismatch: $($item.name)"
    }
}

$v1Path = Join-Path (Split-Path -Parent $root) 'real-source-retrieval-v1\cases.json'
if (-not (Test-Path -LiteralPath $v1Path -PathType Leaf)) {
    throw 'Frozen v1 dataset missing; cannot verify preservation.'
}
$v1Hash = (Get-FileHash -LiteralPath $v1Path -Algorithm SHA256).Hash.ToLowerInvariant()
if ($v1Hash -ne 'bc9e5a8088540577b0deb6c8aaab984497144028e91bf72cb9b99dcc402ec223') {
    throw 'Frozen v1 dataset hash changed.'
}

Write-Output 'PASS: JSON/version/status'
Write-Output "PASS: corpus fingerprint $recomputedFingerprint"
Write-Output "PASS: corpus inventory total=$($allChunks.Count), eligible=$($eligibleChunks.Count), excluded=$($excludedChunks.Count)"
Write-Output "PASS: case counts positive=$($positiveCases.Count), authorization=$($authCases.Count), scope=$($scopeCases.Count), total=$(@($casesDoc.cases).Count)"
Write-Output 'PASS: all gold chunk/source/checksum/scope/page/locator bindings'
Write-Output 'PASS: excluded content omitted; every eligible chunk has a positive case'
Write-Output "PASS: frozen v1 unchanged ($v1Hash)"
Write-Output 'PASS: all manifest artifact hashes'