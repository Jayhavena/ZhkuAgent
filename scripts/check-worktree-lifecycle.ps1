# check-worktree-lifecycle.ps1 - read-only Worktree audit, create preflight and closeout preflight.
# Never creates/removes worktrees, modifies refs, stages, commits, or cleans files.
# Use powershell.exe or pwsh. ASCII-only for Windows PowerShell 5.1 compatibility.
# Examples:
#   powershell -NoProfile -File scripts/check-worktree-lifecycle.ps1 -Mode Audit
#   powershell -NoProfile -File scripts/check-worktree-lifecycle.ps1 -Mode SelfTest
#   powershell -NoProfile -File scripts/check-worktree-lifecycle.ps1 -Mode Create -TaskId issue-123 -Owner team-a -TargetPath D:/alldesk/Projects/ZhkuAgent/worktrees/issue-123 -Branch agent/issue-123 -BaseSha <full-sha> -IsolationReason Parallel -Scope backend-task -Acceptance regression-green
#   powershell -NoProfile -File scripts/check-worktree-lifecycle.ps1 -Mode Close -TargetPath D:/alldesk/Projects/ZhkuAgent/worktrees/issue-123
# Exit codes: 0 = audit complete / preflight ready for HUMAN authorization; 1 = blocked; 2 = invalid input or check failure.

[CmdletBinding()]
param(
    [ValidateSet('Audit','Create','Close','SelfTest')][string]$Mode = 'Audit',
    [string]$TaskId,
    [string]$Owner,
    [string]$TargetPath,
    [string]$Branch,
    [string]$BaseSha,
    [ValidateSet('','Parallel','PreserveExistingChanges','IndependentExperiment')][string]$IsolationReason = '',
    [string]$Scope,
    [string]$Acceptance,
    [string]$HandoffRef,
    [switch]$OwnerAcknowledged,
    [switch]$DispositionRecorded
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$env:GIT_OPTIONAL_LOCKS = '0'
$script:Repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:ProjectRoot = [IO.Path]::GetFullPath((Join-Path $script:Repo '..'))
$script:WorktreeRoot = [IO.Path]::GetFullPath((Join-Path $script:ProjectRoot 'worktrees'))

function Canonical([string]$p) {
    return [IO.Path]::GetFullPath($p.Replace('/', '\')).TrimEnd('\')
}

function Parse-Worktrees([string[]]$lines) {
    $records = [System.Collections.Generic.List[object]]::new()
    $current = $null
    foreach ($line in $lines) {
        if ($line.StartsWith('worktree ')) {
            if ($null -ne $current) { $records.Add([pscustomobject]$current) }
            $current = [ordered]@{ Path = $line.Substring(9); Head = ''; Branch = ''; Locked = $false; Prunable = $false }
        } elseif ($null -ne $current) {
            if ($line.StartsWith('HEAD ')) { $current.Head = $line.Substring(5) }
            elseif ($line.StartsWith('branch ')) { $current.Branch = $line.Substring(7) -replace '^refs/heads/', '' }
            elseif ($line.StartsWith('locked')) { $current.Locked = $true }
            elseif ($line.StartsWith('prunable')) { $current.Prunable = $true }
        }
    }
    if ($null -ne $current) { $records.Add([pscustomobject]$current) }
    return $records.ToArray()
}

function Is-DirectChild([string]$p) {
    $parent = [IO.Path]::GetDirectoryName((Canonical $p))
    return [string]::Equals($parent, (Canonical $script:WorktreeRoot), [StringComparison]::OrdinalIgnoreCase)
}

function Task-Matches([string]$haystack, [string]$task) {
    $name = ($haystack -replace '\\', '/').ToLowerInvariant()
    $id = [regex]::Escape($task.ToLowerInvariant())
    return [bool]($name -match ('(^|[/_-])' + $id + '($|[/_-])'))
}

function Get-Worktrees {
    $lines = @(& git -C $script:Repo worktree list --porcelain 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'git worktree list failed' }
    return @(Parse-Worktrees $lines)
}

function Get-Changes([string]$p) {
    $lines = @(& git -C $p -c core.fsmonitor=false status --porcelain=v1 -uall 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ('git status failed at ' + $p) }
    $staged = 0; $unstaged = 0; $untracked = 0
    foreach ($line in $lines) {
        if ($line.Length -lt 2) { continue }
        if ($line.StartsWith('??')) { $untracked++; continue }
        if ($line.Substring(0,1) -ne ' ') { $staged++ }
        if ($line.Substring(1,1) -ne ' ') { $unstaged++ }
    }
    return [pscustomobject]@{ Staged=$staged; Unstaged=$unstaged; Untracked=$untracked }
}

function Show-Block([System.Collections.Generic.List[string]]$reasons) {
    foreach ($reason in $reasons) { Write-Output ('BLOCKED: ' + $reason) }
}

try {
    if ($Mode -eq 'SelfTest') {
        $fake = @('worktree C:/sample/main','HEAD aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','branch refs/heads/main','','worktree C:/sample/worktrees/issue-123','HEAD bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','branch refs/heads/agent/issue-123','')
        $parsed = @(Parse-Worktrees $fake)
        $a = ($parsed.Count -eq 2 -and $parsed[1].Branch -eq 'agent/issue-123')
        $b = (Task-Matches 'agent/issue-123-fix' 'issue-123')
        $c = -not (Task-Matches 'agent/issue-1234' 'issue-123')
        $d = (Is-DirectChild (Join-Path $script:WorktreeRoot 'issue-123'))
        $e = -not (Is-DirectChild (Join-Path (Join-Path $script:WorktreeRoot 'nested') 'issue-123'))
        $checks = @($a,$b,$c,$d,$e)
        for ($i=0; $i -lt $checks.Count; $i++) {
            Write-Output ('self-test {0}/5: {1}' -f ($i+1), $(if($checks[$i]){'PASS'}else{'FAIL'}))
        }
        if (@($checks | Where-Object {-not $_}).Count -gt 0) { exit 1 }
        Write-Output 'SelfTest: PASS (no filesystem writes)'
        exit 0
    }

    $registered = @(Get-Worktrees)
    if ($Mode -eq 'Audit') {
        Write-Output ('Registered worktrees: ' + $registered.Count)
        $failedChecks = 0
        foreach ($w in $registered) {
            $label = if ((Canonical $w.Path) -eq (Canonical $script:Repo)) { 'MAIN' }
                     elseif ($w.Path -match '(?i)[/\\]\.t3[/\\]worktrees[/\\]') { 'TOOL_MANAGED' }
                     else { 'PROJECT' }
            $state = 'OK'
            $c = $null
            try { $c = Get-Changes $w.Path }
            catch { $state = 'CHECK_FAILED'; $failedChecks++ }
            $counts = if ($null -ne $c) { ('s={0} u={1} ?={2}' -f $c.Staged,$c.Unstaged,$c.Untracked) } else { 'UNKNOWN' }
            Write-Output ('[{0}] {1} | branch={2} | HEAD={3} | {4} | {5} | locked={6} | prunable={7}' -f $label,$w.Path,$w.Branch,$w.Head.Substring(0,[Math]::Min(8,$w.Head.Length)),$counts,$state,$w.Locked,$w.Prunable)
        }
        Write-Output 'Owner and task ownership: UNKNOWN unless established by task handoff or tool-managed metadata.'
        Write-Output 'Audit does not grant permission to create or remove any Worktree.'
        if ($failedChecks -gt 0) { Write-Output ('BLOCKED: status checks failed for ' + $failedChecks + ' Worktrees'); exit 1 }
        exit 0
    }

    $reasons = [System.Collections.Generic.List[string]]::new()
    if ([string]::IsNullOrWhiteSpace($TargetPath)) { $reasons.Add('TargetPath is required') }
    $canonicalTarget = ''
    if (-not [string]::IsNullOrWhiteSpace($TargetPath)) {
        try { $canonicalTarget = Canonical $TargetPath }
        catch { $reasons.Add('TargetPath is invalid') }
    }

    if ($Mode -eq 'Create') {
        if ($TaskId -notmatch '^[a-z0-9][a-z0-9-]{2,48}$') { $reasons.Add('TaskId must be a 3-49 character lowercase slug') }
        if ([string]::IsNullOrWhiteSpace($Owner)) { $reasons.Add('Owner / responsible session is required') }
        if ([string]::IsNullOrWhiteSpace($Scope)) { $reasons.Add('Scope is required') }
        if ([string]::IsNullOrWhiteSpace($Acceptance)) { $reasons.Add('Acceptance criteria are required') }
        if ([string]::IsNullOrWhiteSpace($IsolationReason)) { $reasons.Add('An explicit isolation reason is required') }
        if ($BaseSha -notmatch '^[0-9a-fA-F]{40}$') { $reasons.Add('BaseSha must be a full 40-character commit SHA') }
        elseif (@(& git -C $script:Repo cat-file -t $BaseSha 2>$null) -ne 'commit') { $reasons.Add('BaseSha is not an available commit in this repository') }
        if ([string]::IsNullOrWhiteSpace($Branch)) { $reasons.Add('Branch is required') }
        else {
            & git -C $script:Repo check-ref-format --branch $Branch *> $null
            if ($LASTEXITCODE -ne 0) { $reasons.Add('Branch name fails git check-ref-format') }
            & git -C $script:Repo show-ref --quiet --verify ('refs/heads/' + $Branch)
            if ($LASTEXITCODE -eq 0) { $reasons.Add('A local branch with this name already exists') }
        }
        if ($canonicalTarget -ne '') {
            if (-not (Is-DirectChild $canonicalTarget)) { $reasons.Add('Manual Worktrees must be direct children of the designated project worktrees directory') }
            if ([IO.Directory]::Exists($canonicalTarget) -or [IO.File]::Exists($canonicalTarget)) { $reasons.Add('Target path already exists; do not reuse or overwrite it') }
        }
        if ($TaskId -match '^[a-z0-9][a-z0-9-]{2,48}$') {
            if ($canonicalTarget -ne '' -and -not (Task-Matches ([IO.Path]::GetFileName($canonicalTarget)) $TaskId)) {
                $reasons.Add('Target directory name must contain the TaskId as a distinct token')
            }
            if ($Branch -ne '' -and -not (Task-Matches $Branch $TaskId)) {
                $reasons.Add('Branch name must contain the TaskId as a distinct token')
            }
        }
        foreach ($w in $registered) {
            if ($canonicalTarget -ne '' -and [string]::Equals($canonicalTarget,(Canonical $w.Path),[StringComparison]::OrdinalIgnoreCase)) {
                $reasons.Add('Target path is already a registered Worktree')
            }
            if ($TaskId -match '^[a-z0-9][a-z0-9-]{2,48}$' -and ((Task-Matches $w.Path $TaskId) -or (Task-Matches $w.Branch $TaskId))) {
                $reasons.Add(('Potential duplicate TaskId in Worktree: ' + $w.Path))
            }
        }
        if ($reasons.Count) { Show-Block $reasons; exit 1 }
        Write-Output ('PRECHECK_READY: task={0} owner={1} branch={2} base={3} path={4} isolation={5}' -f $TaskId,$Owner,$Branch,$BaseSha,$canonicalTarget,$IsolationReason)
        Write-Output ('Scope: ' + $Scope)
        Write-Output ('Acceptance: ' + $Acceptance)
        Write-Output 'No Worktree was created. Explicit authorization, ownership confirmation, and resource isolation review are still required.'
        exit 0
    }

    if ($Mode -eq 'Close') {
        $match = @($registered | Where-Object {$canonicalTarget -ne '' -and [string]::Equals((Canonical $_.Path),$canonicalTarget,[StringComparison]::OrdinalIgnoreCase)})
        if ($match.Count -ne 1) { $reasons.Add('TargetPath must identify exactly one registered Worktree') }
        if ($canonicalTarget -ne '' -and [string]::Equals($canonicalTarget,(Canonical $script:Repo),[StringComparison]::OrdinalIgnoreCase)) {
            $reasons.Add('Main Worktree must never be removed by a task closeout')
        }
        if ($match.Count -eq 1) {
            $w = $match[0]
            if ($w.Path -match '(?i)[/\\]\.t3[/\\]worktrees[/\\]') { $reasons.Add('Tool-managed Worktree: use the owner tool for closeout; manual script preflight cannot authorize removal') }
            if ($w.Locked -or $w.Prunable) { $reasons.Add('Locked or prunable Worktree requires separate manual review') }
            try {
                $c = Get-Changes $w.Path
                if (($c.Staged + $c.Unstaged + $c.Untracked) -gt 0) {
                    $reasons.Add(('Uncommitted/untracked changes: staged={0}, unstaged={1}, untracked={2}' -f $c.Staged,$c.Unstaged,$c.Untracked))
                }
            } catch { $reasons.Add('Cannot verify Worktree status') }
        }
        if ([string]::IsNullOrWhiteSpace($Owner)) { $reasons.Add('Responsible Owner identity is required') }
        if ([string]::IsNullOrWhiteSpace($HandoffRef)) { $reasons.Add('A task or handoff reference is required') }
        if (-not $OwnerAcknowledged) { $reasons.Add('Responsible owner acknowledgement is missing') }
        if (-not $DispositionRecorded) { $reasons.Add('Branch, commits, evidence and artifact disposition are not documented') }
        if ($reasons.Count) { Show-Block $reasons; exit 1 }
        Write-Output ('CLOSEOUT_REVIEW_READY: {0} | owner={1} | handoff={2}' -f $match[0].Path,$Owner,$HandoffRef)
        Write-Output 'Owner and handoff assertions are caller-provided, not independently verified by Git.'
        Write-Output 'No removal performed. Project owner must separately approve removal after verifying external references and tool-managed ownership.'
        exit 0
    }
}
catch {
    [Console]::Error.WriteLine(('CHECK_ERROR: ' + $_.Exception.Message))
    exit 2
}
