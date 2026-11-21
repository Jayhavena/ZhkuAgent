# validate-doc-links.ps1 - read-only Markdown link validator for ZhkuAgent.
#
# Purpose:
#   - Scan Markdown files and verify that relative link/image targets exist on disk.
#   - Verify "#L<n>" (and "#L<n>-L<m>") line anchors fit the target file's line count.
#   - Ignore fenced code blocks, inline code, external URLs and same-file fragments.
#   - Never modify repository files. The only outputs are the console and -ReportFile.
#
# Relation to existing tooling:
#   docs/evidence/project-status/2026-10-18/verify-documentation.ps1 validates only the
#   links ADDED in one historical audit diff; it explicitly does not cover old links or
#   anchors. This script is the general, whole-file complement, not a replacement.
#
# Usage (from the repository root):
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\validate-doc-links.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\validate-doc-links.ps1 -ExcludeEvidence
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\validate-doc-links.ps1 -Root docs\zhkuagent
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\validate-doc-links.ps1 -ReportFile out.txt
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\validate-doc-links.ps1 -SelfTest
#
# Parameters:
#   -Root <path>       scan root; default = repository root (parent of this script's folder)
#   -ExcludeEvidence   do not scan files under docs/evidence as link SOURCES (gate mode);
#                      links pointing INTO docs/evidence are still validated
#   -ReportFile <path> also write a UTF-8 (no BOM) report with per-issue details
#   -SelfTest           built-in repeatable fixture test, so maintainers never depend on a
#                      one-off output file: round1 baseline clean (code-fence links ignored,
#                      external URL skipped, percent-encoded target resolved), round2 detects
#                      all references broken by a move+delete, round3 verifies that updating
#                      the moved references reduces breakage. Creates and removes only
#                      tmp/doc-links-selftest (gitignored temp partition). Exit 0 iff all
#                      three rounds pass.
#
# Exit codes: 0 = clean; 1 = missing link targets (or unresolved reference labels);
#             2 = usage error. Out-of-range "#L" anchors are reported but do NOT change
#             the exit code: they can drift when a linked file is edited and would make
#             a PR gate brittle (see docs/agent/m4/README.md gate philosophy).
#
# Limitations (intentional, keep it lightweight):
#   - External URLs are never fetched (no network, no model, no side effects).
#   - Same-file "#fragment" anchors are not verified against headings.
#   - Links are detected line-by-line; links wrapped across lines may be missed.
#   - On Windows PowerShell 5.1, very deep paths (>260 chars) may be reported as scan
#     errors; use PowerShell 7 (pwsh) for the full docs/evidence baseline sweep.
#   - This file is intentionally ASCII-only: PS 5.1 reads BOM-less files as ANSI, so a
#     non-ASCII comment byte can silently swallow the following statement.

[CmdletBinding()]
param(
    [string]$Root,
    [switch]$ExcludeEvidence,
    [string]$ReportFile,
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'

# --- resolve scan root ---------------------------------------------------------------
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path

# --- optional self-test: repeatable fixture verification; writes only tmp/doc-links-selftest ---
if ($SelfTest) {
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $fixtureRoot = Join-Path $repoRoot (Join-Path 'tmp' 'doc-links-selftest')
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Force -Path (Join-Path $fixtureRoot 'docs'), (Join-Path $fixtureRoot 'sub') | Out-Null
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'docs/a.md') -Value @('line1', 'line2', 'line3', 'line4')
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'docs/b.md') -Value @('b')
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'docs/pct-a.md') -Value @('pct')
    $fixtureReadme = @'
# fixture

- [a](docs/a.md)
- [anchor](docs/a.md#L2-L3)
- [b](docs/b.md)
- [pct](docs/pct-a%2Emd)
- [outside](https://example.com/never.md)

```
[fake](docs/never-exists-in-fence.md)
```
'@
    [System.IO.File]::WriteAllText((Join-Path $fixtureRoot 'README.md'), $fixtureReadme, $utf8)

    function Invoke-SelfScan([string]$name, [int]$expectCode, [int]$expectMissing, [int]$expectLinks) {
        $out = & $PSCommandPath -Root $fixtureRoot 2>&1
        $code = $LASTEXITCODE
        $text = ($out -join "`n")
        $missing = -1; $found = -1
        if ($text -match 'missing link targets: (\d+)') { $missing = [int]$Matches[1] }
        if ($text -match 'links found: (\d+)') { $found = [int]$Matches[1] }
        $ok = ($code -eq $expectCode -and $missing -eq $expectMissing -and $found -eq $expectLinks)
        # Return a single result object; never Write-Output inside this function or the
        # emitted lines would pollute the function's return value.
        return @{ line = ("{0}: exit={1} missing={2} links={3} -> {4}" -f $name, $code, $missing, $found, $(if ($ok) { 'PASS' } else { 'FAIL' })); ok = $ok }
    }

    $r1 = Invoke-SelfScan 'run1 initial tree (expect 0 / 0 / 5)' 0 0 5
    Write-Output $r1.line

    Move-Item -LiteralPath (Join-Path $fixtureRoot 'docs/a.md') -Destination (Join-Path $fixtureRoot 'sub/a-moved.md')
    Remove-Item -LiteralPath (Join-Path $fixtureRoot 'docs/b.md')
    $r2 = Invoke-SelfScan 'run2 after move docs/a.md and delete docs/b.md (expect 1 / 3 / 5)' 1 3 5
    Write-Output $r2.line

    $fixed = [System.IO.File]::ReadAllText((Join-Path $fixtureRoot 'README.md'), $utf8)
    $fixed = $fixed.Replace('[a](docs/a.md)', '[a](sub/a-moved.md)').Replace('[anchor](docs/a.md#L2-L3)', '[anchor](sub/a-moved.md#L2-L3)')
    [System.IO.File]::WriteAllText((Join-Path $fixtureRoot 'README.md'), $fixed, $utf8)
    $r3 = Invoke-SelfScan 'run3 after updating moved references (expect 1 / 1 / 5)' 1 1 5
    Write-Output $r3.line

    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    $overall = ($r1.ok -and $r2.ok -and $r3.ok)
    Write-Output ("self-test: {0}" -f $(if ($overall) { 'PASS' } else { 'FAIL' }))
    if ($overall) { exit 0 } else { exit 1 }
}

if (-not $Root) { $Root = $repoRoot }
$rootPath = if ([System.IO.Path]::IsPathRooted($Root)) { $Root } else { Join-Path (Get-Location).Path $Root }
if (-not (Test-Path -LiteralPath $rootPath -PathType Container)) {
    Write-Output "validate-doc-links: root not found: $rootPath"
    exit 2
}
$rootPath = (Resolve-Path -LiteralPath $rootPath).Path.TrimEnd('\', '/')
$rootWithSep = $rootPath + [System.IO.Path]::DirectorySeparatorChar

try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch { }

# Directory names never descended into (build output, caches, session temp artifacts).
$excludedDirNames = @('.git', '.scratch', '.shots', '.learnings', '.superpowers',
                      'node_modules', 'target', 'dist', 'tmp', '__pycache__', 'BOOT-INF')
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

# --- collect markdown files (manual recursion so excluded dirs are skipped) ----------
$mdFiles = [System.Collections.Generic.List[string]]::new()
$dirErrors = [System.Collections.Generic.List[string]]::new()
$stack = [System.Collections.Generic.Stack[string]]::new()
$stack.Push($rootPath)
while ($stack.Count -gt 0) {
    $dir = $stack.Pop()
    $entries = $null
    try { $entries = [System.IO.Directory]::GetFileSystemEntries($dir) }
    catch { $dirErrors.Add($dir); continue }
    foreach ($e in $entries) {
        $name = [System.IO.Path]::GetFileName($e)
        if ([System.IO.Directory]::Exists($e)) {
            if ($excludedDirNames -contains $name) { continue }
            if ($ExcludeEvidence) {
                $relParts = $e.Substring($rootWithSep.Length) -split '[\\/]'
                if ($relParts.Count -ge 2 -and $relParts[0] -eq 'docs' -and $relParts[1] -eq 'evidence') { continue }
            }
            $stack.Push($e)
        }
        elseif ($name -like '*.md') {
            if ($ExcludeEvidence) {
                $relParts = $e.Substring($rootWithSep.Length) -split '[\\/]'
                if ($relParts.Count -ge 2 -and $relParts[0] -eq 'docs' -and $relParts[1] -eq 'evidence') { continue }
            }
            $mdFiles.Add($e)
        }
    }
}
$mdFiles.Sort()

# --- counters -----------------------------------------------------------------------
$script:lineCountCache = @{}
$filesScanned = 0
$areaFiles = @{}
$linksFound = 0
$relativeChecked = 0
$externalSkipped = 0
$fragmentSkipped = 0
$missingTargets = 0
$unresolvedRefs = 0
$anchorOutOfRange = 0
$fileErrors = 0
$issues = [System.Collections.Generic.List[string]]::new()
$issuesByArea = @{}

function Add-Issue([string]$sourceFile, [int]$lineNo, [string]$category, [string]$rawUrl, [string]$detail) {
    $rel = $sourceFile.Substring($rootWithSep.Length)
    $area = '(root)'
    if ($rel -match '[\\/]') { $area = ($rel -split '[\\/]')[0] }
    if (-not $issuesByArea.ContainsKey($area)) { $issuesByArea[$area] = 0 }
    $issuesByArea[$area]++
    $issues.Add(('{0}:{1} [{2}] {3} -> {4}' -f $rel, $lineNo, $category, $detail, $rawUrl))
}

function Get-TargetLineCount([string]$path) {
    if ($script:lineCountCache.ContainsKey($path)) { return $script:lineCountCache[$path] }
    $n = -1
    try { $n = [System.IO.File]::ReadAllLines($path, $utf8NoBom).Count } catch { $n = -1 }
    $script:lineCountCache[$path] = $n
    return $n
}

function ConvertTo-DecodedLinkPath([string]$p) {
    if ($p -notmatch '%[0-9A-Fa-f]{2}') { return $p }
    try { return [System.Uri]::UnescapeDataString($p) } catch { return $p }
}

# --- link classification ------------------------------------------------------------
$inlineLinkRegex = [regex]'\[[^\]]*\]\(\s*(?<url><[^>]*>|[^)\s]+)(\s+"[^"]*")?\)'
$inlineCodeRegex = [regex]'`[^`]*`'
$fenceRegex      = [regex]'^\s*(```|~~~)'
$refDefRegex     = [regex]'^\s{0,3}\[(?<label>[^\]]+)\]:\s*(?<url>\S+?)(\s+".*")?\s*$'
$refUseRegex     = [regex]'\[[^\]]*\]\s*\[(?<label>[^\]]+)\]'
$anchorRegex     = [regex]'^L(\d+)(?:-L?(\d+))?$'
$schemeRegex     = [regex]'^[A-Za-z][A-Za-z0-9+.\-]*:'
$driveRegex      = [regex]'^[A-Za-z]:[\\/]'

function Resolve-LinkTarget([string]$sourceFile, [int]$lineNo, [string]$rawUrl, [string]$kind) {
    # $kind: 'inline' or 'ref' - used only for issue reporting.
    $target = $rawUrl.Trim()
    if ($target.StartsWith('<') -and $target.EndsWith('>')) {
        $target = $target.Substring(1, $target.Length - 2).Trim()
    }
    if ($target -eq '') { return }
    $script:linksFound++

    $frag = ''
    $hashIdx = $target.IndexOf('#')
    if ($hashIdx -ge 0) {
        $frag = $target.Substring($hashIdx + 1)
        $target = $target.Substring(0, $hashIdx)
    }
    if ($target -eq '') {
        if ($frag -ne '') { $script:fragmentSkipped++ }
        return
    }

    if ($schemeRegex.IsMatch($target) -and -not $driveRegex.IsMatch($target)) {
        # http(s)/mailto/app/codex etc. - never fetched, never checked as files.
        $script:externalSkipped++
        return
    }

    $target = $target.Split('?')[0]
    if ($target -eq '') { return }
    $script:relativeChecked++

    $full = $null
    try {
        if ($driveRegex.IsMatch($target)) {
            # Absolute Windows path (historical evidence keeps such paths on purpose).
            $full = [System.IO.Path]::GetFullPath(($target -replace '/', '\'))
        }
        elseif ($target.StartsWith('/')) {
            $full = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($repoRoot, $target.TrimStart('/')))
        }
        else {
            $decoded = ConvertTo-DecodedLinkPath $target
            $full = [System.IO.Path]::GetFullPath(
                [System.IO.Path]::Combine([System.IO.Path]::GetDirectoryName($sourceFile), ($decoded -replace '/', '\')))
        }
    }
    catch {
        $script:missingTargets++
        Add-Issue $sourceFile $lineNo 'broken' $rawUrl 'cannot resolve path'
        return
    }

    $exists = $false
    try { $exists = Test-Path -LiteralPath $full } catch { $exists = $false }
    if (-not $exists) {
        $script:missingTargets++
        Add-Issue $sourceFile $lineNo $kind $rawUrl 'target missing'
        return
    }

    if ($frag -ne '') {
        $am = $anchorRegex.Match($frag)
        if ($am.Success) {
            $n = Get-TargetLineCount $full
            if ($n -ge 0) {
                $first = [int]$am.Groups[1].Value
                $second = $first
                if ($am.Groups[2].Success) { $second = [int]$am.Groups[2].Value }
                if ($first -lt 1 -or $first -gt $n -or $second -gt $n) {
                    $script:anchorOutOfRange++
                    Add-Issue $sourceFile $lineNo 'anchor' $rawUrl ("line anchor out of range ({0} > {1} lines)" -f $frag, $n)
                }
            }
        }
    }
}

# --- scan ---------------------------------------------------------------------------
foreach ($path in $mdFiles) {
    $rel = $path.Substring($rootWithSep.Length)
    $area = '(root)'
    if ($rel -match '[\\/]') { $area = ($rel -split '[\\/]')[0] }
    if (-not $areaFiles.ContainsKey($area)) { $areaFiles[$area] = 0 }
    $areaFiles[$area]++

    try { $lines = [System.IO.File]::ReadAllLines($path, $utf8NoBom) }
    catch { $script:fileErrors++; continue }
    $script:filesScanned++

    # Pass 1: reference definitions ([label]: url) local to this file.
    $refDefs = @{}
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $m = $refDefRegex.Match($lines[$i])
        if ($m.Success) {
            $key = $m.Groups['label'].Value.ToLowerInvariant()
            if (-not $refDefs.ContainsKey($key)) { $refDefs[$key] = $m.Groups['url'].Value.Trim() }
        }
    }

    # Pass 2: links outside fenced code blocks and inline code.
    $inFence = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($fenceRegex.IsMatch($line)) { $inFence = -not $inFence; continue }
        if ($inFence) { continue }
        $work = $inlineCodeRegex.Replace($line, '')
        if ($work -notmatch '\]\(' -and $work -notmatch '\]\s*\[') { continue }
        foreach ($m in $inlineLinkRegex.Matches($work)) {
            Resolve-LinkTarget $path ($i + 1) $m.Groups['url'].Value 'inline'
        }
        foreach ($m in $refUseRegex.Matches($work)) {
            $key = $m.Groups['label'].Value.ToLowerInvariant()
            if (-not $refDefs.ContainsKey($key)) {
                $script:linksFound++
                $script:unresolvedRefs++
                Add-Issue $path ($i + 1) 'ref' $m.Value ('unresolved reference label: {0}' -f $key)
                continue
            }
            Resolve-LinkTarget $path ($i + 1) $refDefs[$key] 'ref'
        }
    }
}

# --- output -------------------------------------------------------------------------
$nl = [System.Environment]::NewLine
$sb = [System.Text.StringBuilder]::new()
[void]$sb.Append("validate-doc-links report$nl")
[void]$sb.Append("root: $rootPath$nl")
[void]$sb.Append("excludeEvidence: $ExcludeEvidence$nl")
[void]$sb.Append("files scanned: $filesScanned$nl")
foreach ($a in ($areaFiles.Keys | Sort-Object)) {
    [void]$sb.Append(("  area {0}: {1} files$nl" -f $a, $areaFiles[$a]))
}
[void]$sb.Append("links found: $linksFound$nl")
[void]$sb.Append("  relative targets checked: $relativeChecked$nl")
[void]$sb.Append("  external urls skipped: $externalSkipped$nl")
[void]$sb.Append("  same-file fragments skipped: $fragmentSkipped$nl")
[void]$sb.Append("missing link targets: $missingTargets$nl")
[void]$sb.Append("unresolved reference labels: $unresolvedRefs$nl")
[void]$sb.Append("out-of-range line anchors: $anchorOutOfRange (non-blocking)$nl")
[void]$sb.Append("scan errors: $($fileErrors + $dirErrors.Count) (files: $fileErrors, dirs: $($dirErrors.Count))$nl")
if ($dirErrors.Count -gt 0) {
    foreach ($d in $dirErrors) { [void]$sb.Append("  dir error: $d$nl") }
}
[void]$sb.Append("result: $(if ($missingTargets -gt 0 -or $unresolvedRefs -gt 0) { 'BROKEN' } else { 'CLEAN' })$nl")
[void]$sb.Append("issues by area:$nl")
foreach ($a in ($issuesByArea.Keys | Sort-Object)) {
    [void]$sb.Append(("  {0}: {1}$nl" -f $a, $issuesByArea[$a]))
}
[void]$sb.Append("detail:$nl")
foreach ($line in $issues) { [void]$sb.Append("  $line$nl") }

$reportText = $sb.ToString()
Write-Output $reportText.TrimEnd()

if ($ReportFile) {
    $reportDir = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $ReportFile)))
    if (-not (Test-Path -LiteralPath $reportDir)) { New-Item -ItemType Directory -Force -Path $reportDir | Out-Null }
    [System.IO.File]::WriteAllText((Join-Path (Get-Location).Path $ReportFile), $reportText, $utf8NoBom)
    Write-Output "report file: $ReportFile"
}

if ($missingTargets -gt 0 -or $unresolvedRefs -gt 0) { exit 1 }
exit 0
