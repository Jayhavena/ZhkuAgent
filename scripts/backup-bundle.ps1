# backup-bundle.ps1 - snapshot all refs and objects into a bundle OUTSIDE the repo.
#
# A bundle can fully restore the repository even if the .git directory is damaged.
# Run after every baseline consolidation or merge (documented in the collaboration
# manual, section 5.6).
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backup-bundle.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backup-bundle.ps1 -BackupDir D:\some\dir -Keep 10

param(
    [string]$BackupDir = 'D:\alldesk\Projects\ZhkuAgent-backups',
    [int]$Keep = 10
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path -LiteralPath $BackupDir)) {
    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$file = Join-Path $BackupDir ('zhkuagent-' + $stamp + '.bundle')

git -C $RepoRoot bundle create $file --all
if ($LASTEXITCODE -ne 0) {
    throw 'git bundle create failed'
}

# Keep only the newest $Keep bundles.
$old = Get-ChildItem -LiteralPath $BackupDir -Filter 'zhkuagent-*.bundle' |
    Sort-Object Name -Descending | Select-Object -Skip $Keep
foreach ($item in $old) { Remove-Item -LiteralPath $item.FullName -Force }

# Restore reference (for disaster recovery):
#   git clone <bundle> <new-repo-dir>
Write-Output ('BUNDLE: ' + $file)
