$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$casesPath = Join-Path $root 'cases.json'
$snapshotPath = Join-Path $root 'corpus-snapshot.json'
$readmePath = Join-Path $root 'README.md'
$manifestPath = Join-Path $root 'manifest.json'
$repairPath = Join-Path $root 'source-url-repair.json'
$repairSqlPath = Join-Path $root 'source-url-repair.sql'
$preAuditPath = Join-Path $root 'source-audit-pre-repair.json'
$comparisonPath = Join-Path $root '2026-handbook-comparison.md'
$databaseVerificationPath = Join-Path $root 'database-verification.json'
$queryEmbeddingsPath = Join-Path $root 'query-embeddings.json'
$harnessPath = Join-Path $root 'Rc2RetrievalEvaluationHarnessTest.java'
$retrievalResultsPath = Join-Path $root 'retrieval-results-v2-rc2.json'
$retrievalTracePath = Join-Path $root 'retrieval-trace-v2-rc2.json'
$scoredResultsPath = Join-Path $root 'scored-results-v2-rc2.json'
$retrievalReportPath = Join-Path $root 'retrieval-comparison-report.md'
$replayScriptPath = Join-Path $root 'run-retrieval-comparison.ps1'

foreach ($path in @($casesPath, $snapshotPath, $readmePath, $manifestPath, $repairPath, $repairSqlPath, $preAuditPath, $comparisonPath, $databaseVerificationPath, $queryEmbeddingsPath, $harnessPath, $retrievalResultsPath, $retrievalTracePath, $scoredResultsPath, $retrievalReportPath, $replayScriptPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required artifact missing: $path"
    }
}

$casesDoc = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($casesPath, [Text.Encoding]::UTF8))
$snapshot = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($snapshotPath, [Text.Encoding]::UTF8))
$manifest = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8))
$repair = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($repairPath, [Text.Encoding]::UTF8))
$databaseVerification = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($databaseVerificationPath, [Text.Encoding]::UTF8))
$queryEmbeddings = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($queryEmbeddingsPath, [Text.Encoding]::UTF8))
$retrievalResults = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($retrievalResultsPath, [Text.Encoding]::UTF8))
$retrievalTrace = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($retrievalTracePath, [Text.Encoding]::UTF8))
$scoredResults = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($scoredResultsPath, [Text.Encoding]::UTF8))

if ($casesDoc.schemaVersion -ne 2 -or $casesDoc.datasetVersion -ne 'v2.0.0-rc2') {
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

# The rc2 change is metadata-only: preserve the rc1 ranking corpus and bind source URLs separately.
$baselineRoot = Split-Path -Parent $root
$baselineDir = Join-Path $baselineRoot 'real-source-retrieval-v2'
$baselineSnapshotPath = Join-Path $baselineDir 'corpus-snapshot.json'
$baselineManifestPath = Join-Path $baselineDir 'manifest.json'
if (-not (Test-Path -LiteralPath $baselineSnapshotPath -PathType Leaf) -or -not (Test-Path -LiteralPath $baselineManifestPath -PathType Leaf)) {
    throw 'Frozen rc1 parent assets missing.'
}
$baselineSnapshot = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($baselineSnapshotPath, [Text.Encoding]::UTF8))
$baselineManifest = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($baselineManifestPath, [Text.Encoding]::UTF8))
if ((Get-FileHash -LiteralPath $baselineSnapshotPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $baselineManifest.artifacts.corpusSnapshotSha256) {
    throw 'Frozen rc1 parent snapshot hash changed.'
}
if ((Get-FileHash -LiteralPath (Join-Path $baselineDir 'cases.json') -Algorithm SHA256).Hash.ToLowerInvariant() -ne $baselineManifest.artifacts.casesSha256) {
    throw 'Frozen rc1 parent cases hash changed.'
}
if ((Get-FileHash -LiteralPath $baselineManifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.parentCandidate.manifestSha256) {
    throw 'rc1 parent manifest hash mismatch.'
}
if ($manifest.parentCandidate.datasetVersion -ne 'v2.0.0-rc1' -or
    $manifest.parentCandidate.casesSha256 -ne $baselineManifest.artifacts.casesSha256 -or
    $manifest.parentCandidate.corpusSnapshotSha256 -ne $baselineManifest.artifacts.corpusSnapshotSha256) {
    throw 'Parent candidate lineage mismatch.'
}
if ($baselineSnapshot.corpusFingerprint.value -ne $snapshot.corpusFingerprint.value) {
    throw 'Metadata repair must not change the ranking corpus fingerprint.'
}
$requiredUrl = 'https://xsc.zhku.edu.cn/info/1154/15962.htm'
$requiredSourceIds = @(
    '72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14',
    '6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05',
    'f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5'
)
if ($repair.candidateVersion -ne $casesDoc.datasetVersion -or
    $repair.status -ne 'PASS' -or
    $repair.targetDatabase -ne 'zhkuagent_eval_v2_rc2' -or
    $repair.targetContainer -ne 'zhkuagent-eval-v2-rc2' -or
    $repair.officialPublicationPageUrl -ne $requiredUrl) {
    throw 'Source URL repair evidence does not describe this rc2 candidate.'
}
if ($repair.sourceRowsTotal -ne 6 -or $repair.sourceRowsUpdated -ne 3 -or
    $repair.chunkRowsTotal -ne 17 -or $repair.eligibleChunkRows -ne 8 -or
    $repair.excludedChunkRows -ne 9 -or $repair.chunkRowsWithUpdatedJoinedSourceUrl -ne 8 -or
    $repair.sourceEvalV1Unchanged -ne $true -or $repair.productionDatabaseWriteAttempted -ne $false -or
    $repair.knowledgeChunkRowsWritten -ne 0) {
    throw 'Source URL repair counts or isolation guarantees do not match the recorded result.'
}
if ($casesDoc.sourceSnapshot.officialPublicationPageUrl -ne $requiredUrl -or
    $snapshot.sourceSnapshot.officialPublicationPageUrl -ne $requiredUrl -or
    $casesDoc.sourceSnapshot.databaseSourceToPdfBinding -ne $snapshot.sourceSnapshot.databaseSourceToPdfBinding) {
    throw 'Official publication URL or source binding status differs between artifacts.'
}
$baselineById = @{}
foreach ($chunk in @($baselineSnapshot.chunks)) { $baselineById[[string]$chunk.chunkId] = $chunk }
$targetChunkCount = 0
foreach ($chunk in $allChunks) {
    $id = [string]$chunk.chunkId
    if (-not $baselineById.ContainsKey($id)) { throw "Chunk added to rc2 snapshot: $id" }
    $baseChunk = $baselineById[$id]
    foreach ($field in @('chunkId','sourceId','sourceTitle','sourceVersion','knowledgeScope','publicationStatus','visibilityScope','sharingRightsConfirmed','eligibleForStudentRetrieval','exclusionReasons','pageNumber','sourceLocator','contentChecksum')) {
        $oldValue = ConvertTo-Json -InputObject $baseChunk.$field -Depth 20 -Compress
        $newValue = ConvertTo-Json -InputObject $chunk.$field -Depth 20 -Compress
        if ($oldValue -cne $newValue) { throw "Non-URL chunk metadata drift for $id / $field" }
    }
    $oldHasContent = $baseChunk.PSObject.Properties.Name -contains 'content'
    $newHasContent = $chunk.PSObject.Properties.Name -contains 'content'
    if ($oldHasContent -ne $newHasContent) { throw "Chunk content-presence drift for $id" }
    if ($oldHasContent -and [string]$baseChunk.content -cne [string]$chunk.content) { throw "Chunk content drift for $id" }

    $oldUrl = if ($null -eq $baseChunk.sourceUrl) { '<NULL>' } else { [string]$baseChunk.sourceUrl }
    $newUrl = if ($null -eq $chunk.sourceUrl) { '<NULL>' } else { [string]$chunk.sourceUrl }
    if ($requiredSourceIds -contains [string]$chunk.sourceId) {
        $targetChunkCount++
        if ($oldUrl -ne '<NULL>' -or $newUrl -ne $requiredUrl) { throw "Target source URL not repaired exactly: $id" }
        if ($chunk.eligibleForStudentRetrieval -ne $true) { throw "Target source chunk unexpectedly ineligible: $id" }
    } elseif ($oldUrl -cne $newUrl) {
        throw "Non-target source URL changed: $id"
    }
}
if ($targetChunkCount -ne 8) { throw "Expected eight target chunks, got $targetChunkCount" }

function Get-SourceMetadataFingerprintForValidation([object[]]$Chunks) {
    $lines = @()
    foreach ($group in @($Chunks | Group-Object -Property sourceId | Sort-Object -Property Name)) {
        $first = $group.Group | Select-Object -First 1
        $firstUrl = if ($null -eq $first.sourceUrl) { '<NULL>' } else { [string]$first.sourceUrl }
        foreach ($item in $group.Group) {
            $itemUrl = if ($null -eq $item.sourceUrl) { '<NULL>' } else { [string]$item.sourceUrl }
            if ([string]$item.sourceTitle -cne [string]$first.sourceTitle -or
                [string]$item.sourceVersion -cne [string]$first.sourceVersion -or
                [string]$item.publicationStatus -cne [string]$first.publicationStatus -or
                $itemUrl -cne $firstUrl) {
                throw "Inconsistent source-level metadata across chunks: $($first.sourceId)"
            }
        }
        $lines += ((@([string]$first.sourceId,[string]$first.sourceTitle,[string]$first.sourceVersion,$firstUrl,[string]$first.publicationStatus)) -join '|')
    }
    $canonical = ($lines -join ([string][char]10)) + ([string][char]10)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
$metadataBefore = Get-SourceMetadataFingerprintForValidation -Chunks @($baselineSnapshot.chunks)
$metadataAfter = Get-SourceMetadataFingerprintForValidation -Chunks @($allChunks)
if ($metadataBefore -ne $repair.sourceMetadataFingerprintBefore) { throw 'Recorded pre-repair source metadata fingerprint mismatch.' }
if ($metadataAfter -ne $repair.sourceMetadataFingerprintAfter -or
    $metadataAfter -ne $snapshot.sourceMetadataFingerprint.value -or
    $metadataAfter -ne $manifest.sourceMetadataFingerprint.value) {
    throw 'Post-repair source metadata fingerprint mismatch.'
}
if ($snapshot.sourceMetadataFingerprint.canonicalization -ne 'sourceId|sourceTitle|sourceVersion|sourceUrl-or-<NULL>|publicationStatus; unique source rows sorted by sourceId; UTF-8 LF terminated') {
    throw 'Unexpected source metadata fingerprint canonicalization.'
}
$sourceIdDiff = @(Compare-Object (@($requiredSourceIds | Sort-Object)) (@($repair.changedSourceIds | Sort-Object)))
if (@($repair.changedSourceIds).Count -ne 3 -or $sourceIdDiff.Count -ne 0) {
    throw 'Repair evidence does not list exactly the three expected source IDs.'
}
$expectedChunkIds = @($allChunks | Where-Object { $requiredSourceIds -contains [string]$_.sourceId } | ForEach-Object { [string]$_.chunkId } | Sort-Object)
$chunkIdDiff = @(Compare-Object $expectedChunkIds (@($repair.changedChunkIds | Sort-Object)))
if ($chunkIdDiff.Count -ne 0) {
    throw 'Repair evidence changed-chunk list differs from the eight expected chunks.'
}
$preAudit = ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($preAuditPath, [Text.Encoding]::UTF8))
$preAuditUrlNonNull = @($preAudit.sourceMetadataEvidence | Where-Object { $null -ne $_.sourceUrl }).Count
if ($preAuditUrlNonNull -ne 0) {
    throw 'Historical pre-repair audit ledger must retain the original NULL sourceUrl state.'
}
if ($manifest.sourceUrlRepair.officialPublicationPageUrl -ne $requiredUrl -or
    $manifest.sourceUrlRepair.sourceRowsUpdated -ne 3 -or
    $manifest.sourceUrlRepair.chunkRowsWithUpdatedJoinedSourceUrl -ne 8) {
    throw 'Manifest source URL repair summary mismatch.'
}

# Verify the live isolated database query evidence recorded for this candidate.
if ($databaseVerification.candidateVersion -ne $casesDoc.datasetVersion -or
    $databaseVerification.verificationStatus -ne 'PASS' -or
    $databaseVerification.targetContainer -ne 'zhkuagent-eval-v2-rc2' -or
    $databaseVerification.targetDatabase -ne 'zhkuagent_eval_v2_rc2' -or
    $databaseVerification.targetEndpoint -ne '127.0.0.1:55442') {
    throw 'Database verification evidence does not describe this rc2 candidate.'
}
if ($databaseVerification.sourceContainerUnchanged -ne $true -or
    $databaseVerification.schemaMigrationsAppliedByRepair -ne 0 -or
    $databaseVerification.sourceRows -ne 6 -or
    $databaseVerification.targetSourceRowsWithOfficialUrl -ne 3 -or
    $databaseVerification.targetSourceRowsStillNull -ne 0 -or
    $databaseVerification.chunkRows -ne 17 -or
    $databaseVerification.eligibleChunks -ne 8 -or
    $databaseVerification.excludedChunks -ne 9 -or
    $databaseVerification.chunkIdentityChecksumSetUnchanged -ne $true -or
    $databaseVerification.knowledgeChunkRowsWritten -ne 0 -or
    $databaseVerification.modelCalls -ne 0 -or
    $databaseVerification.embeddingApiCalls -ne 0 -or
    $databaseVerification.retrievalComparisonExecuted -ne $false) {
    throw 'Database verification counts or isolation guarantees mismatch.'
}
if ($databaseVerification.sourceUrlStateSha256Before -ne $repair.sourceUrlStateSha256Before -or
    $databaseVerification.sourceUrlStateSha256After -ne $repair.sourceUrlStateSha256After) {
    throw 'Database verification source URL state hashes differ from repair evidence.'
}
if (@($databaseVerification.indexProfiles).Count -ne 2) { throw 'Expected two index profiles in database verification evidence.' }
$lexicalProfile = @($databaseVerification.indexProfiles | Where-Object { $_.profileId -eq 'real-source-lexical-only-v1-20261026' })
$vectorProfile = @($databaseVerification.indexProfiles | Where-Object { $_.profileId -eq 'dashscope-text-embedding-v4-1024' })
if ($lexicalProfile.Count -ne 1 -or $vectorProfile.Count -ne 1) { throw 'Expected named lexical and vector index profiles.' }
if ($lexicalProfile[0].entries -ne 8 -or $lexicalProfile[0].entriesWithEmbeddings -ne 0 -or
    $vectorProfile[0].entries -ne 8 -or $vectorProfile[0].entriesWithEmbeddings -ne 8 -or
    $lexicalProfile[0].chunkIdSetSha256 -ne $casesDoc.retrievalProtocol.eligibleChunkIdSetSha256 -or
    $vectorProfile[0].chunkIdSetSha256 -ne $casesDoc.retrievalProtocol.eligibleChunkIdSetSha256) {
    throw 'Lexical/vector profile entries do not share the exact eight-chunk candidate pool.'
}
if ($manifest.databaseCandidate.latestSuccessfulMigration -ne $databaseVerification.latestSuccessfulMigration -or
    $manifest.validation.databaseVerification -ne 'PASS') {
    throw 'Manifest database verification summary mismatch.'
}

# Verify the actual three-way retrieval comparison and its cached query vectors.
if ($queryEmbeddings.datasetVersion -ne $casesDoc.datasetVersion -or
    $queryEmbeddings.corpusFingerprint -ne $snapshot.corpusFingerprint.value -or
    $queryEmbeddings.provider -ne 'DashScope' -or
    $queryEmbeddings.model -ne 'text-embedding-v4' -or
    $queryEmbeddings.dimensions -ne 1024 -or
    $queryEmbeddings.inputCaseCount -ne 18 -or
    $queryEmbeddings.uniqueQueryCount -ne 18 -or
    $queryEmbeddings.embeddingApiCalls -ne 3 -or
    $queryEmbeddings.totalInputTokens -ne 306 -or
    @($queryEmbeddings.queryEmbeddings).Count -ne 18) {
    throw 'Cached query embedding metadata does not match the rc2 evaluation.'
}
$caseQuestionMap = @{}
foreach ($case in @($casesDoc.cases)) { $caseQuestionMap[[string]$case.question] = [string]$case.caseId }
foreach ($item in @($queryEmbeddings.queryEmbeddings)) {
    $question = [string]$item.question
    if (-not $caseQuestionMap.ContainsKey($question)) { throw 'Query embedding contains a question outside the frozen cases.' }
    if (@($item.vector).Count -ne 1024 -or @($item.caseIds).Count -lt 1) { throw 'Query embedding vector dimensions or case mapping are invalid.' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $questionHash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($question)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
    if ($questionHash -ne $item.questionSha256) { throw 'Cached query embedding question hash mismatch.' }
    foreach ($caseId in @($item.caseIds)) {
        if (@($casesDoc.cases | Where-Object { $_.caseId -eq $caseId -and $_.question -eq $question }).Count -ne 1) {
            throw "Cached query embedding case mapping mismatch: $caseId"
        }
    }
}
$embeddingsHash = (Get-FileHash -LiteralPath $queryEmbeddingsPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($retrievalResults.datasetVersion -ne $casesDoc.datasetVersion -or
    $retrievalResults.corpusFingerprint -ne $snapshot.corpusFingerprint.value -or
    $retrievalResults.retrievalConfig.resultLimitK -ne 5 -or
    $retrievalResults.retrievalConfig.candidateLimitPerPath -ne 8 -or
    $retrievalResults.retrievalConfig.rrfK -ne 60 -or
    $retrievalResults.execution.database -ne 'zhkuagent_eval_v2_rc2' -or
    $retrievalResults.execution.readOnlyDatabaseTransactions -ne $true -or
    $retrievalResults.execution.queryEmbeddingApiCallsDuringRetrieval -ne 0 -or
    $retrievalResults.execution.queryEmbeddingsSha256 -ne $embeddingsHash) {
    throw 'Retrieval results metadata or frozen evaluation configuration mismatch.'
}
if ($retrievalTrace.datasetVersion -ne $casesDoc.datasetVersion -or
    $retrievalTrace.corpusFingerprint -ne $snapshot.corpusFingerprint.value -or
    $retrievalTrace.candidateLimitPerPath -ne 8 -or
    $retrievalTrace.resultLimitK -ne 5 -or
    $retrievalTrace.rrfK -ne 60 -or
    $retrievalTrace.queryEmbeddingsSha256 -ne $embeddingsHash -or
    $retrievalTrace.readOnlyDatabaseTransactions -ne $true -or
    @($retrievalTrace.caseTraces).Count -ne 18) {
    throw 'Retrieval trace metadata mismatch.'
}
if (@($retrievalResults.runs).Count -ne 6) { throw 'Expected six algorithm/scope runs.' }
foreach ($algorithm in @('LEXICAL','VECTOR','RRF_HYBRID')) {
    foreach ($mode in @('ALL_ELIGIBLE_STUDENT_VISIBLE','CONFIGURED_PUBLIC_ELECTIVE')) {
        $matching = @($retrievalResults.runs | Where-Object { $_.algorithm -eq $algorithm -and $_.scopeMode -eq $mode })
        $expectedCaseCount = if ($mode -eq 'ALL_ELIGIBLE_STUDENT_VISIBLE') { 16 } else { 2 }
        if ($matching.Count -ne 1 -or @($matching[0].caseResults).Count -ne $expectedCaseCount) {
            throw "Retrieval results missing or mis-sized: $algorithm / $mode"
        }
    }
}
$resultsHash = (Get-FileHash -LiteralPath $retrievalResultsPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($scoredResults.datasetVersion -ne $casesDoc.datasetVersion -or
    $scoredResults.corpusFingerprint -ne $snapshot.corpusFingerprint.value -or
    $scoredResults.reportStatus -ne 'SCORED_GATES_PASS' -or
    $scoredResults.inputResultsSha256 -ne $resultsHash) {
    throw 'Scored results do not bind to the actual retrieval result file or did not pass gates.'
}
$expectedMetrics = @{
    LEXICAL = @{ recall = 1.0; mrr = 0.966667; complete = 1.0 }
    VECTOR = @{ recall = 1.0; mrr = 0.966667; complete = 1.0 }
    RRF_HYBRID = @{ recall = 1.0; mrr = 1.0; complete = 1.0 }
}
foreach ($algorithm in @('LEXICAL','VECTOR','RRF_HYBRID')) {
    $metric = @($scoredResults.metrics | Where-Object { $_.algorithm -eq $algorithm -and $_.scopeMode -eq 'ALL_ELIGIBLE_STUDENT_VISIBLE' })
    if ($metric.Count -ne 1 -or $metric[0].scoredCaseCount -ne 15 -or
        $metric[0].macroRecallAt5 -ne $expectedMetrics[$algorithm].recall -or
        $metric[0].mrr -ne $expectedMetrics[$algorithm].mrr -or
        $metric[0].completeEvidenceSetRateAt5 -ne $expectedMetrics[$algorithm].complete) {
        throw "Unexpected scored metrics for $algorithm."
    }
}
foreach ($property in $scoredResults.globalGateTotals.PSObject.Properties) {
    if ([int]$property.Value -ne 0) { throw "Retrieval gate is not zero: $($property.Name)" }
}
if ($manifest.retrievalComparison.status -ne 'SCORED_GATES_PASS' -or
    $manifest.retrievalComparison.queryEmbeddingApiCalls -ne 3 -or
    $manifest.retrievalComparison.queryEmbeddingApiCallsDuringRetrieval -ne 0 -or
    $manifest.retrievalComparison.databaseWritesDuringRetrieval -ne 0 -or
    $manifest.validation.v2RetrievalComparisonExecuted -ne $true -or
    $manifest.validation.retrievalComparison -ne 'PASS') {
    throw 'Manifest does not record the completed three-way comparison.'
}

# Every file hash in the manifest must match the on-disk artifact.
$hashChecks = @(
    @{ name = 'cases.json'; path = $casesPath; expected = [string]$manifest.artifacts.casesSha256 },
    @{ name = 'corpus-snapshot.json'; path = $snapshotPath; expected = [string]$manifest.artifacts.corpusSnapshotSha256 },
    @{ name = 'README.md'; path = $readmePath; expected = [string]$manifest.artifacts.readmeSha256 },
    @{ name = 'validate-v2-rc2.ps1'; path = $MyInvocation.MyCommand.Path; expected = [string]$manifest.artifacts.validatorSha256 },
    @{ name = 'score-v2-results.ps1'; path = (Join-Path $root 'score-v2-results.ps1'); expected = [string]$manifest.artifacts.scorerSha256 },
    @{ name = 'source-url-repair.json'; path = $repairPath; expected = [string]$manifest.artifacts.sourceUrlRepairSha256 },
    @{ name = 'source-url-repair.sql'; path = $repairSqlPath; expected = [string]$manifest.artifacts.repairSqlSha256 },
    @{ name = 'source-audit-pre-repair.json'; path = $preAuditPath; expected = [string]$manifest.artifacts.sourceAuditPreRepairSha256 },
    @{ name = '2026-handbook-comparison.md'; path = $comparisonPath; expected = [string]$manifest.artifacts.handbookComparisonSha256 },
    @{ name = 'database-verification.json'; path = $databaseVerificationPath; expected = [string]$manifest.artifacts.databaseVerificationSha256 },
    @{ name = 'query-embeddings.json'; path = $queryEmbeddingsPath; expected = [string]$manifest.artifacts.queryEmbeddingsSha256 },
    @{ name = 'Rc2RetrievalEvaluationHarnessTest.java'; path = $harnessPath; expected = [string]$manifest.artifacts.retrievalHarnessSha256 },
    @{ name = 'retrieval-results-v2-rc2.json'; path = $retrievalResultsPath; expected = [string]$manifest.artifacts.retrievalResultsSha256 },
    @{ name = 'retrieval-trace-v2-rc2.json'; path = $retrievalTracePath; expected = [string]$manifest.artifacts.retrievalTraceSha256 },
    @{ name = 'scored-results-v2-rc2.json'; path = $scoredResultsPath; expected = [string]$manifest.artifacts.scoredResultsSha256 },
    @{ name = 'retrieval-comparison-report.md'; path = $retrievalReportPath; expected = [string]$manifest.artifacts.retrievalReportSha256 },
    @{ name = 'run-retrieval-comparison.ps1'; path = $replayScriptPath; expected = [string]$manifest.artifacts.replayScriptSha256 }
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
Write-Output 'PASS: source URL repair mapping and source metadata fingerprint'
Write-Output 'PASS: database source URL and index-profile snapshot evidence'
Write-Output 'PASS: only source_url fields changed relative to frozen rc1 snapshot'
Write-Output 'PASS: query embedding cache and three-way retrieval results/metrics'
Write-Output 'PASS: all manifest artifact hashes'