param([switch]$AfterCommit)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
Push-Location $repoRoot
try {
    $audit = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot 'source-audit.json') | ConvertFrom-Json
    $documents = @(
        'README.md', 'knowledge/README.md', 'docs/evidence/rule-agent-progress.md',
        'docs/manual/nginx-local-preview.md', 'docs/plans/agent-sync/SYNC-STATUS.md',
        'docs/superpowers/plans/2026-10-03-post-3a-backend-roadmap.md',
        'docs/superpowers/plans/2026-10-07-rule-check-refactor-roadmap.md',
        'docs/superpowers/plans/2026-10-13-rule-check-agent-plan-review.md',
        'docs/zhkuagent/仲园Agent-校园Agent功能规划与能力清单.md',
        'docs/zhkuagent/2026-10-18-project-status.md',
        'docs/superpowers/plans/2026-10-18-project-integration-and-closeout-plan.md'
    )
    $errorsFound = [Collections.Generic.List[string]]::new()
    $linkCount = 0
    foreach ($path in $documents) {
        $absolute = Join-Path $repoRoot $path
        if (!(Test-Path -LiteralPath $absolute)) { $errorsFound.Add("Missing document: $path"); continue }
        # 只核对本轮新增链接；历史正文保留的旧链接不冒充本轮已经修复。
        if ($path -like '*2026-10-18*') {
            $addedText = Get-Content -Raw -Encoding UTF8 $absolute
        } else {
            $diff = @(& git -c core.quotepath=false diff --no-ext-diff --unified=0 $audit.baseCommit -- $path)
            if ($LASTEXITCODE -ne 0) { throw "Cannot read diff: $path" }
            $addedText = ($diff | Where-Object { $_.StartsWith('+') -and !$_.StartsWith('+++') } |
                ForEach-Object { $_.Substring(1) }) -join "`n"
        }
        foreach ($match in [regex]::Matches($addedText, '\[[^\]]+\]\(([^)]+)\)')) {
            $target = $match.Groups[1].Value.Trim('<', '>')
            if ($target -match '^(https?://|#|app://|codex://)') { continue }
            $target = ($target -split '#', 2)[0] -replace ':\d+$', ''
            $resolved = [IO.Path]::GetFullPath((Join-Path (Split-Path $absolute) $target))
            $linkCount++
            if (!(Test-Path -LiteralPath $resolved)) { $errorsFound.Add("Broken added link: $path -> $target") }
        }
    }
    foreach ($source in $audit.sources) {
        $actualBlob = (& git rev-parse ($source.commit + ':' + $source.path)).Trim()
        if ($LASTEXITCODE -ne 0 -or $actualBlob -ne $source.blob) { $errorsFound.Add("Source blob mismatch: $($source.id)") }
        if ($source.ref -ne 'HEAD') {
            $currentRef = (& git rev-parse $source.ref).Trim()
            if ($LASTEXITCODE -ne 0 -or $currentRef -ne $source.commit) { $errorsFound.Add("Branch moved since audit: $($source.ref)") }
        }
    }
    foreach ($preserved in $audit.preservedExistingChanges) {
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $preserved.path).Hash.ToLowerInvariant()
        if ($hash -ne $preserved.sha256) { $errorsFound.Add("Existing change was modified: $($preserved.path)") }
    }
    foreach ($preview in $audit.mergePreviews) {
        & git cat-file -e ($preview.tree + '^{tree}')
        if ($LASTEXITCODE -ne 0) { $errorsFound.Add('Missing preview tree') }
    }
    $selectedFiles = @($documents) + @(
        'docs/evidence/project-status/2026-10-18/source-audit.json',
        'docs/evidence/project-status/2026-10-18/verify-documentation.ps1',
        'docs/evidence/project-status/2026-10-18/verification.json'
    )
    $whitespaceOutput = @(& git diff --check $audit.baseCommit -- @selectedFiles)
    $whitespaceExit = $LASTEXITCODE
    if ($whitespaceExit -ne 0) { $errorsFound.Add('Selected documentation whitespace check failed') }
    $head = (& git rev-parse HEAD).Trim()
    if (!$AfterCommit -and $head -ne $audit.baseCommit) { $errorsFound.Add('HEAD changed before documentation commit') }
    if ($AfterCommit) {
        & git merge-base --is-ancestor $audit.baseCommit HEAD
        if ($LASTEXITCODE -ne 0) { $errorsFound.Add('Audit baseline no longer in current ancestry') }
    }
    $manifest = @(foreach ($path in $documents) {
        [ordered]@{path=$path; sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()}
    })
    $result = [ordered]@{
        scope='DOCUMENTATION_ONLY'; checkedAt=[DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(8)).ToString('o')
        baseCommit=$audit.baseCommit; checkedHead=$head; afterCommit=[bool]$AfterCommit
        documentsChecked=$documents.Count; addedLocalLinksChecked=$linkCount; fixedGitSourcesChecked=$audit.sources.Count
        existingDirtyDocumentsUnchanged=$audit.preservedExistingChanges.Count; pairwisePreviewTreesExist=$audit.mergePreviews.Count
        selectedWhitespaceExitCode=$whitespaceExit; documents=$manifest; errors=@($errorsFound.ToArray())
        passed=($errorsFound.Count -eq 0)
        runtimeTestsRerun=$false; databaseOperations=0; newProviderCalls=0
        precommitIssue='Initial staged whitespace check rejected CRLF in the two new JSON files under evidence -text rules; only these new files were normalized to LF and rechecked.'
        limitations=@('Historical runtime results were read, not rerun.','Only added local link targets checked; old links and Markdown anchors not exhaustively validated.','Pairwise previews do not prove combined runtime compatibility.')
    }
    # 提交后只输出核对结果，不改写已提交的验收回执，避免为自写 SHA 递归提交。
    if (!$AfterCommit) {
        $resultJson = ($result | ConvertTo-Json -Depth 8) -replace "`r`n", "`n"
        [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'verification.json'), $resultJson, [Text.UTF8Encoding]::new($false))
    }
    [ordered]@{passed=$result.passed; documents=$documents.Count; addedLinks=$linkCount; sources=$audit.sources.Count; errors=$result.errors; checkedHead=$head} | ConvertTo-Json -Depth 4
    if (!$result.passed) { exit 1 }
} finally { Pop-Location }
