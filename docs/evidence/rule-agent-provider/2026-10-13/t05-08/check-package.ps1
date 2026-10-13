$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8
[Console]::OutputEncoding=$utf8
$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$env:PATH=[Environment]::GetEnvironmentVariable('PATH','Machine')+';'+[Environment]::GetEnvironmentVariable('PATH','User')
foreach($name in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','ZHKU_DB_URL','DASHSCOPE_API_KEY')) {
    Remove-Item -LiteralPath "Env:\$name" -ErrorAction SilentlyContinue
}
$packet=Join-Path $root 'docs/evidence/rule-agent-provider/2026-10-13/t05-probe-03/request-package.json'
$sha=(Get-FileHash -LiteralPath $packet).Hash.ToLowerInvariant()
$previous=[Environment]::CurrentDirectory
try {
    [Environment]::CurrentDirectory=$root
    $checked=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','knowledge\scripts\start-rule-agent-probe.cmd','-CheckPackage','-ApprovedPackageSha256',$sha) -TimeoutSeconds 25
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'package-check.log'),$checked.output+"`n"+$checked.errorOutput,$utf8)
    if($checked.exitCode-ne 0-or $checked.timedOut-or !$checked.resourcesClosed) { throw 'New package check failed' }
    $actual=$checked.output|ConvertFrom-Json
    if(!$actual.passed-or $actual.providerCalls-ne 0-or $actual.databaseOperations-ne 0-or $actual.protocolRevision-ne 'RULE_AGENT_ACTION_CONTRACT_01') { throw 'Unexpected package check state' }
    $oldSha=(Get-FileHash -LiteralPath (Join-Path $root 'docs/evidence/rule-agent-provider/2026-10-13/t05-probe-02/request-package.json')).Hash.ToLowerInvariant()
    $old=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','knowledge\scripts\start-rule-agent-probe.cmd','-CheckPackage','-ApprovedPackageSha256',$oldSha) -TimeoutSeconds 25
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'old-package-check.log'),$old.output+"`n"+$old.errorOutput,$utf8)
    if($old.exitCode-eq 0-or $old.timedOut-or !$old.resourcesClosed-or ($old.output+$old.errorOutput)-notmatch '指纹不同') { throw 'Old package not rejected' }
    $record=[ordered]@{checkedAt=[DateTimeOffset]::Now.ToString('o');passed=$true;ordinaryUserPath=$true;credentialsLoaded=$false;providerCalls=0;databaseCalls=0;resourcesClosed=$true;newPackageSha256=$sha;oldPackageSha256=$oldSha;oldPackageRejected=$true;packageCheck=$actual;jarSha256=(Get-FileHash -LiteralPath (Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar')).Hash.ToLowerInvariant()}
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'package-check.json'),($record|ConvertTo-Json -Depth 5),$utf8)
    $record|ConvertTo-Json -Depth 5 -Compress
} finally {
    [Environment]::CurrentDirectory=$previous
}
