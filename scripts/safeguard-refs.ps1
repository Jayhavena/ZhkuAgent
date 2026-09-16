# safeguard-refs.ps1 - branch ref guard for the ZhkuAgent multi-worktree setup.
#
# Background: on this machine (git 2.52.0.windows.1, two copies verified), namespaced
# branch refs (refs/heads/<dir>/<name>) are silently dropped after some git write
# operations, and refs/heads/codex/ was wiped repeatedly on 2026-09-16. Branches have
# been migrated to top-level names; this script is the safety net that detects and
# restores any lost branch ref.
#
# Usage (PowerShell):
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\safeguard-refs.ps1            check only
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\safeguard-refs.ps1 -Fix       check + auto-restore
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\safeguard-refs.ps1 -Fix -DelayCheck   second check after 3s (post-commit hook)
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\safeguard-refs.ps1 -Resnap    recreate snapshot after intentional branch delete/rename
#
# The snapshot file lives at <git-common-dir>\refs-guard-snapshot.txt (outside refs/,
# so it survives ref wipes). A post-commit hook runs this script with -Fix -DelayCheck
# after every commit in any worktree.

param(
    [switch]$Fix,
    [switch]$DelayCheck,
    [switch]$Resnap
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot

$gitCommonDirOutput = (git -C $RepoRoot rev-parse --git-common-dir) | Select-Object -First 1
if ($LASTEXITCODE -ne 0 -or -not $gitCommonDirOutput) {
    Write-Output 'REF-GUARD: cannot resolve git common dir, skipping'
    exit 0
}
$gitCommonDirOutput = $gitCommonDirOutput.Trim()
if ([System.IO.Path]::IsPathRooted($gitCommonDirOutput)) {
    $CommonDir = [System.IO.Path]::GetFullPath($gitCommonDirOutput)
}
else {
    $CommonDir = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $gitCommonDirOutput))
}
$HeadsDir = Join-Path $CommonDir 'refs\heads'
$SnapshotFile = Join-Path $CommonDir 'refs-guard-snapshot.txt'

function Read-Snapshot {
    $map = @{}
    if (Test-Path -LiteralPath $SnapshotFile) {
        foreach ($line in (Get-Content -LiteralPath $SnapshotFile)) {
            $parts = $line -split ' ', 2
            if ($parts.Count -eq 2 -and $parts[0] -match '^[0-9a-f]{40}$') {
                $map[$parts[1].Trim()] = $parts[0]
            }
        }
    }
    return $map
}

function Get-CurrentHeads {
    # Read loose ref files directly: do not depend on git ref listing in this environment.
    $map = @{}
    if (Test-Path -LiteralPath $HeadsDir) {
        $headRoot = [System.IO.Path]::GetFullPath($HeadsDir)
        $files = Get-ChildItem -LiteralPath $HeadsDir -Recurse -File
        foreach ($file in $files) {
            $sha = (Get-Content -LiteralPath $file.FullName -Raw).Trim()
            if ($sha -match '^[0-9a-f]{40}$') {
                $rel = $file.FullName.Substring($headRoot.Length + 1) -replace '\\', '/'
                $map[$rel] = $sha
            }
        }
    }
    return $map
}

function Write-SnapshotFromCurrent {
    $current = Get-CurrentHeads
    if ($current.Count -gt 0) {
        $lines = @()
        foreach ($name in $current.Keys) { $lines += ($current[$name] + ' ' + $name) }
        Set-Content -LiteralPath $SnapshotFile -Value $lines -Encoding ascii
    }
    return $current.Count
}

if ($Resnap) {
    $count = Write-SnapshotFromCurrent
    Write-Output ('REF-GUARD: snapshot recreated with ' + $count + ' branches')
    exit 0
}

$Expected = Read-Snapshot
if ($Expected.Count -eq 0) {
    $count = Write-SnapshotFromCurrent
    Write-Output ('REF-GUARD: first run, snapshot created with ' + $count + ' branches')
    exit 0
}

function Invoke-Check([string]$Label) {
    $current = Get-CurrentHeads
    $script:Missing = @()
    foreach ($name in $Expected.Keys) {
        if (-not $current.ContainsKey($name)) { $script:Missing += $name }
    }
    if ($script:Missing.Count -eq 0) {
        Write-Output ('REF-GUARD[' + $Label + ']: OK ' + $current.Count + ' branches')
        return $true
    }
    foreach ($name in $script:Missing) {
        if ($Fix) {
            # Write the loose ref file directly; git update-ref is unreliable here.
            $path = Join-Path $HeadsDir ($name -replace '/', '\')
            $dir = Split-Path -Parent $path
            if (-not (Test-Path -LiteralPath $dir)) {
                New-Item -ItemType Directory -Force -Path $dir | Out-Null
            }
            Set-Content -LiteralPath $path -Value $Expected[$name] -NoNewline -Encoding ascii
            Write-Output ('REF-GUARD[' + $Label + ']: RESTORED ' + $name + ' -> ' + $Expected[$name].Substring(0, 7))
        }
        else {
            Write-Output ('REF-GUARD[' + $Label + ']: MISSING ' + $name + ' (rerun with -Fix to restore)')
        }
    }
    return $false
}

$healthy = Invoke-Check 't0'
if ($DelayCheck) {
    Start-Sleep -Seconds 3
    $second = Invoke-Check 't+3s'
    if (-not $second) { $healthy = $false }
}

# Refresh the snapshot only when every expected branch is present,
# so an unresolved incident never overwrites the recovery record.
$current2 = Get-CurrentHeads
$allPresent = $true
foreach ($name in $Expected.Keys) {
    if (-not $current2.ContainsKey($name)) { $allPresent = $false }
}
if ($allPresent -and $current2.Count -gt 0) {
    $lines = @()
    foreach ($name in $current2.Keys) { $lines += ($current2[$name] + ' ' + $name) }
    Set-Content -LiteralPath $SnapshotFile -Value $lines -Encoding ascii
}
