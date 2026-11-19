# check-task-delivery.ps1 - unified, read-only task delivery preflight.
# Reuses Git, validate-doc-links.ps1 and check-worktree-lifecycle.ps1.
# Does not run tests, stage, commit, push, merge, create/remove worktrees, or edit files.
# Use powershell.exe or pwsh. ASCII-only for Windows PowerShell 5.1 compatibility.
# Examples:
#   powershell -NoProfile -File scripts/check-task-delivery.ps1 -Mode SelfTest
#   powershell -NoProfile -File scripts/check-task-delivery.ps1 -Mode Audit -TaskId p3 -TestStatus PASS -TestEvidencePath tmp/test-run.log -DocsRoot docs/collaboration
#   powershell -NoProfile -File scripts/check-task-delivery.ps1 -Mode Audit -TaskId issue-123 -TestStatus PASS -TestEvidencePath tmp/tests.log -DocsRoot docs/feature -RequireClean -RequireDocsClean
#   powershell -NoProfile -File scripts/check-task-delivery.ps1 -Mode Closeout -CloseoutTarget D:/.../worktrees/issue-123 -Owner team-a -HandoffRef issue-123 -OwnerAcknowledged -DispositionRecorded
# Exit codes: 0 = READY_FOR_HUMAN_REVIEW; 1 = INCOMPLETE or BLOCKED; 2 = invalid input or check error.

[CmdletBinding()]
param(
    [ValidateSet('Audit','Closeout','SelfTest')][string]$Mode = 'Audit',
    [string]$TaskId,
    [ValidateSet('NOT_RUN','PASS','FAIL','PARTIAL','BLOCKED','NOT_EVALUABLE')][string]$TestStatus = 'NOT_RUN',
    [string]$TestEvidencePath,
    [string]$DocsRoot,
    [switch]$RequireClean,
    [switch]$RequireDocsClean,
    [string]$CloseoutTarget,
    [string]$Owner,
    [string]$HandoffRef,
    [switch]$OwnerAcknowledged,
    [switch]$DispositionRecorded
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$env:GIT_OPTIONAL_LOCKS = '0'
$script:Repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:P2Script = Join-Path $PSScriptRoot 'check-worktree-lifecycle.ps1'
$script:DocScript = Join-Path $PSScriptRoot 'validate-doc-links.ps1'

function Canonical([string]$PathValue) {
    return [IO.Path]::GetFullPath($PathValue.Replace('/', '\')).TrimEnd('\')
}

function Parse-Worktrees([string[]]$Lines) {
    $records = [System.Collections.Generic.List[object]]::new()
    $current = $null
    foreach ($line in $Lines) {
        if ($line.StartsWith('worktree ')) {
            if ($null -ne $current) { $records.Add([pscustomobject]$current) }
            $current = [ordered]@{ Path = $line.Substring(9); Head = ''; Branch = '' }
        } elseif ($null -ne $current) {
            if ($line.StartsWith('HEAD ')) { $current.Head = $line.Substring(5) }
            elseif ($line.StartsWith('branch ')) { $current.Branch = $line.Substring(7) -replace '^refs/heads/', '' }
        }
    }
    if ($null -ne $current) { $records.Add([pscustomobject]$current) }
    return $records.ToArray()
}

function Get-StatusCounts([string[]]$Lines) {
    $staged = 0; $unstaged = 0; $untracked = 0
    foreach ($line in $Lines) {
        if ($line.Length -lt 2) { continue }
        if ($line.StartsWith('??')) { $untracked++; continue }
        if ($line.Substring(0,1) -ne ' ') { $staged++ }
        if ($line.Substring(1,1) -ne ' ') { $unstaged++ }
    }
    return [pscustomobject]@{ Staged=$staged; Unstaged=$unstaged; Untracked=$untracked; Total=($staged+$unstaged+$untracked) }
}

function Get-TestEvidenceState([string]$Status, [bool]$EvidenceExists) {
    if ($Status -eq 'NOT_RUN') { return 'NOT_RUN' }
    if (-not $EvidenceExists) { return 'MISSING_EVIDENCE' }
    return ('DECLARED_' + $Status)
}

function Get-OverallState([int]$Blockers, [int]$Incomplete) {
    if ($Blockers -gt 0) { return 'BLOCKED' }
    if ($Incomplete -gt 0) { return 'INCOMPLETE' }
    return 'READY_FOR_HUMAN_REVIEW'
}

function Invoke-Git([string[]]$GitArgs) {
    $lines = @(& git -C $script:Repo @GitArgs 2>&1)
    $code = $LASTEXITCODE
    return [pscustomobject]@{ ExitCode=$code; Lines=$lines }
}

function Show-Check([string]$Name, [string]$State, [string]$Detail) {
    Write-Output ('{0}: {1}{2}' -f $Name,$State,$(if($Detail){' | '+$Detail}else{''}))
}

try {
    if ($Mode -eq 'SelfTest') {
        $fakeStatus = @(' M src/A.java','A  src/B.java','?? tmp/c.log','MM src/D.java')
        $counts = Get-StatusCounts $fakeStatus
        $fakeTrees = @(Parse-Worktrees @('worktree C:/repo/main','HEAD aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','branch refs/heads/main','','worktree C:/repo/other','HEAD bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','branch refs/heads/feature/other'))
        $checks = @(
            ($counts.Staged -eq 2 -and $counts.Unstaged -eq 2 -and $counts.Untracked -eq 1 -and $counts.Total -eq 5),
            ($fakeTrees.Count -eq 2 -and $fakeTrees[1].Branch -eq 'feature/other'),
            ((Get-TestEvidenceState 'PASS' $true) -eq 'DECLARED_PASS'),
            ((Get-TestEvidenceState 'PASS' $false) -eq 'MISSING_EVIDENCE'),
            ((Get-TestEvidenceState 'NOT_RUN' $false) -eq 'NOT_RUN'),
            ((Get-OverallState 0 0) -eq 'READY_FOR_HUMAN_REVIEW'),
            ((Get-OverallState 0 1) -eq 'INCOMPLETE'),
            ((Get-OverallState 1 0) -eq 'BLOCKED')
        )
        for ($i=0; $i -lt $checks.Count; $i++) {
            Write-Output ('self-test {0}/8: {1}' -f ($i+1),$(if($checks[$i]){'PASS'}else{'FAIL'}))
        }
        if (@($checks | Where-Object {-not $_}).Count -gt 0) { exit 1 }
        Write-Output 'SelfTest: PASS (no filesystem writes)'
        exit 0
    }

    if ($Mode -eq 'Closeout') {
        if ([string]::IsNullOrWhiteSpace($CloseoutTarget)) { throw 'CloseoutTarget is required for Closeout mode' }
        if ([string]::IsNullOrWhiteSpace($Owner)) { throw 'Owner is required for Closeout mode' }
        if ([string]::IsNullOrWhiteSpace($HandoffRef)) { throw 'HandoffRef is required for Closeout mode' }
        if (-not (Test-Path -LiteralPath $script:P2Script -PathType Leaf)) { throw 'P2 Worktree lifecycle script is missing' }
        Write-Output 'P3 CLOSEOUT: delegating to the existing P2 closeout preflight.'
        $p2Output = @(& $script:P2Script -Mode Close -TargetPath $CloseoutTarget -Owner $Owner -HandoffRef $HandoffRef -OwnerAcknowledged:$OwnerAcknowledged -DispositionRecorded:$DispositionRecorded 2>&1)
        $p2Exit = $LASTEXITCODE
        $p2Output | ForEach-Object { Write-Output $_ }
        if ($p2Exit -ne 0) { exit 1 }
        Write-Output 'CLOSEOUT_REVIEW_READY: no removal was performed; project-owner approval remains separate.'
        exit 0
    }

    $blockers = 0
    $incomplete = 0
    if ([string]::IsNullOrWhiteSpace($TaskId)) {
        Show-Check 'Task' 'UNKNOWN' 'TaskId was not supplied'
        $incomplete++
    } else {
        Show-Check 'Task' 'RECORDED' $TaskId
    }

    if (-not (Test-Path -LiteralPath $script:P2Script -PathType Leaf)) { throw 'P2 Worktree lifecycle script is missing' }
    if (-not (Test-Path -LiteralPath $script:DocScript -PathType Leaf)) { throw 'P1 documentation link validator is missing' }

    $top = Invoke-Git @('rev-parse','--show-toplevel')
    if ($top.ExitCode -ne 0) { throw 'git rev-parse --show-toplevel failed' }
    $topPath = Canonical ([string]$top.Lines[0])
    if (-not [string]::Equals($topPath,(Canonical $script:Repo),[StringComparison]::OrdinalIgnoreCase)) {
        throw ('Script repository root mismatch: ' + $topPath)
    }
    $headResult = Invoke-Git @('rev-parse','HEAD')
    $branchResult = Invoke-Git @('branch','--show-current')
    if ($headResult.ExitCode -ne 0 -or $branchResult.ExitCode -ne 0) { throw 'Unable to read HEAD or branch' }
    $head = [string]$headResult.Lines[0]
    $branch = [string]$branchResult.Lines[0]
    if ($head -notmatch '^[0-9a-fA-F]{40,64}$') { throw 'HEAD is not a valid object id' }
    if ([string]::IsNullOrWhiteSpace($branch)) {
        Show-Check 'Branch' 'BLOCKED' 'detached HEAD'
        $blockers++
    } else {
        Show-Check 'Branch' 'RECORDED' $branch
    }

    $treeResult = Invoke-Git @('worktree','list','--porcelain')
    if ($treeResult.ExitCode -ne 0) { throw 'git worktree list failed' }
    $trees = @(Parse-Worktrees $treeResult.Lines)
    $registered = @($trees | Where-Object { [string]::Equals((Canonical $_.Path),$topPath,[StringComparison]::OrdinalIgnoreCase) })
    if ($registered.Count -ne 1) {
        Show-Check 'CurrentWorktree' 'BLOCKED' 'current path is not registered exactly once'
        $blockers++
    } else {
        Show-Check 'CurrentWorktree' 'REGISTERED' $topPath
    }
    Show-Check 'HEAD' 'RECORDED' $head

    $statusResult = Invoke-Git @('-c','core.fsmonitor=false','status','--porcelain=v1','-uall')
    if ($statusResult.ExitCode -ne 0) { throw 'git status failed' }
    $counts = Get-StatusCounts $statusResult.Lines
    $gitState = if ($counts.Total -eq 0) { 'CLEAN' } else { 'DIRTY' }
    Show-Check 'GitStatus' $gitState ('staged={0}, unstaged={1}, untracked={2}' -f $counts.Staged,$counts.Unstaged,$counts.Untracked)
    if ($counts.Total -gt 0) {
        if ($RequireClean) { $blockers++ } else { $incomplete++ }
    }

    $stagedCheck = Invoke-Git @('diff','--cached','--check')
    $unstagedCheck = Invoke-Git @('diff','--check')
    if ($stagedCheck.ExitCode -ne 0 -or $unstagedCheck.ExitCode -ne 0) {
        $whitespaceDetail = @()
        if ($stagedCheck.ExitCode -ne 0) {
            $whitespaceDetail += ('staged exit={0}, output-lines={1}' -f $stagedCheck.ExitCode,$stagedCheck.Lines.Count)
        }
        if ($unstagedCheck.ExitCode -ne 0) {
            $whitespaceDetail += ('unstaged exit={0}, output-lines={1}' -f $unstagedCheck.ExitCode,$unstagedCheck.Lines.Count)
        }
        Show-Check 'Whitespace' 'BLOCKED' ($whitespaceDetail -join '; ')
        foreach ($line in @($stagedCheck.Lines + $unstagedCheck.Lines | Select-Object -First 5)) {
            Write-Output ('  detail: ' + $line)
        }
        $blockers++
    } else {
        Show-Check 'Whitespace' 'PASS' 'staged and unstaged diff checks exit 0'
    }

    $p2Output = @(& $script:P2Script -Mode Audit 2>&1)
    $p2Exit = $LASTEXITCODE
    $p2Summary = @($p2Output | Where-Object { $_ -match '^Registered worktrees:' })
    if ($p2Exit -ne 0) {
        Show-Check 'P2WorktreeAudit' 'BLOCKED' 'existing P2 audit returned non-zero'
        $blockers++
    } else {
        Show-Check 'P2WorktreeAudit' 'PASS' (($p2Summary -join '').Trim())
    }

    $testEvidenceExists = $false
    $testEvidenceFull = ''
    if ($TestStatus -ne 'NOT_RUN') {
        if ([string]::IsNullOrWhiteSpace($TestEvidencePath)) {
            $testState = 'MISSING_EVIDENCE'
        } else {
            $testEvidenceFull = if ([IO.Path]::IsPathRooted($TestEvidencePath)) { $TestEvidencePath } else { Join-Path $script:Repo $TestEvidencePath }
            $testEvidenceExists = Test-Path -LiteralPath $testEvidenceFull -PathType Leaf
            $testState = Get-TestEvidenceState $TestStatus $testEvidenceExists
        }
    } else {
        $testState = 'NOT_RUN'
    }
    $testDetail = if ($testEvidenceExists) { $testEvidenceFull + ' (caller-declared; command and result are not independently verified)' } else { 'no test execution is performed by this script' }
    Show-Check 'Tests' $testState $testDetail
    if ($testState -eq 'MISSING_EVIDENCE' -or $TestStatus -in @('FAIL','BLOCKED')) { $blockers++ }
    elseif ($testState -in @('NOT_RUN','DECLARED_PARTIAL','DECLARED_NOT_EVALUABLE')) { $incomplete++ }

    if ([string]::IsNullOrWhiteSpace($DocsRoot)) {
        Show-Check 'Documentation' 'NOT_RUN' 'supply -DocsRoot to run the existing P1 link validator'
        if ($RequireDocsClean) { $blockers++ } else { $incomplete++ }
    } else {
        $docsPath = if ([IO.Path]::IsPathRooted($DocsRoot)) { $DocsRoot } else { Join-Path $script:Repo $DocsRoot }
        if (-not (Test-Path -LiteralPath $docsPath -PathType Container)) { throw ('DocsRoot is not a directory: ' + $docsPath) }
        $docsPath = (Resolve-Path -LiteralPath $docsPath).Path
        $repoPrefix = (Canonical $script:Repo) + [IO.Path]::DirectorySeparatorChar
        if (-not $docsPath.StartsWith($repoPrefix,[StringComparison]::OrdinalIgnoreCase)) {
            throw 'DocsRoot must be inside the current repository'
        }
        $docOutput = @(& $script:DocScript -Root $docsPath 2>&1)
        $docExit = $LASTEXITCODE
        $docText = ($docOutput | ForEach-Object { [string]$_ }) -join "`n"
        $docSummary = @()
        foreach ($label in @('files scanned:','links found:','missing link targets:','unresolved reference labels:','scan errors:','result:')) {
            $match = [regex]::Match($docText,'(?m)^' + [regex]::Escape($label) + '.*$')
            if ($match.Success) { $docSummary += $match.Value.Trim() }
        }
        if ($docExit -eq 0) {
            Show-Check 'Documentation' 'PASS' (($docSummary -join '; ').Trim())
        } else {
            Show-Check 'Documentation' 'BROKEN' (($docSummary -join '; ').Trim())
            if ($RequireDocsClean) { $blockers++ } else { $incomplete++ }
        }
    }

    Show-Check 'WorktreeCloseout' 'NOT_REQUESTED' 'current Worktree is retained; use -Mode Closeout only after commit and disposition review'
    $overall = Get-OverallState $blockers $incomplete
    Show-Check 'OVERALL' $overall ('blockers={0}, incomplete={1}' -f $blockers,$incomplete)
    if ($overall -eq 'READY_FOR_HUMAN_REVIEW') {
        Write-Output 'This is a preflight result, not product acceptance or permission to push, merge, or remove a Worktree.'
        exit 0
    }
    if ($overall -eq 'BLOCKED') { exit 1 }
    exit 1
}
catch {
    [Console]::Error.WriteLine(('CHECK_ERROR: ' + $_.Exception.Message))
    exit 2
}
