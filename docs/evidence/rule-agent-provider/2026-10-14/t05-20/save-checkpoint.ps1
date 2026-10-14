$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$base='docs/evidence/rule-agent-provider/2026-10-14'
$task="$base/t05-20"
if((git rev-parse HEAD)-ne 'f0eed907b1c16d7db7fb4475bd1126cbea8ee628'){throw 'Unexpected base'}
if(git -c core.excludesfile= diff --cached --name-only){throw 'Index not empty'}
$report=Get-Content "$task/preflight-result.json" -Raw|ConvertFrom-Json
$tests=Get-Content "$base/t05-p01-preflight-green-01/results.json" -Raw|ConvertFrom-Json
$terminal=Get-Content "$task/terminal-ps51.json" -Raw|ConvertFrom-Json
if(!$report.passed-or $report.caseCount-ne 6-or !$report.resourcesClosed-or $report.providerCalls-ne 0-or $report.databaseOperations-ne 0){throw 'Preflight not accepted'}
if(!$tests.passed-or $tests.testCount-ne 136-or $tests.sourceDrift-ne 0-or !$tests.resourcesVerified){throw 'Backend verification not accepted'}
if($terminal.exitCode-ne 0-or !$terminal.resourcesClosed-or !$terminal.chineseCorrect-or !$terminal.packageCheckPassed){throw 'PS5.1 check not accepted'}
foreach($item in $report.source){if((Get-FileHash -LiteralPath $item.path).Hash.ToLowerInvariant()-ne $item.sha256){throw "Source differs: $($item.path)"}}
if((Get-FileHash 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar').Hash.ToLowerInvariant()-ne $report.jarSha256){throw 'Build differs'}
if((Get-FileHash "$base/t05-19/request-package.json").Hash.ToLowerInvariant()-ne $report.packageSha256){throw 'Frozen package changed'}
$files=@($report.source|ForEach-Object path)
$files+=@('docs/evidence/rule-agent-progress.md')
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File|Where-Object Name -notin @('stage-audit.json','commit-receipt.json')|ForEach-Object {"$task/$($_.Name)"})
foreach($phase in @('t05-p01-preflight-red-01','t05-p01-preflight-green-01')){
 $files+=@("$base/$phase/results.json","$base/$phase/command.json","$base/$phase/source-before.json","$base/$phase/source-after.json")
 $files+=@(Get-ChildItem "$base/$phase/xml" -File|ForEach-Object {"$base/$phase/xml/$($_.Name)"})
}
$files=@($files|Sort-Object -Unique)
$secrets=@()
foreach($name in @('DASHSCOPE_API_KEY','ZHKU_DB_PASSWORD','POSTGRES_PASSWORD')){
 foreach($scope in @('Process','User','Machine')){
  try{$value=[Environment]::GetEnvironmentVariable($name,$scope);if($value){$secrets+=$value}}catch{}
 }
}
foreach($p in $files){
 $content=[IO.File]::ReadAllText((Join-Path $root $p))
 foreach($value in $secrets){if($content.Contains($value)){throw "Credential in $p"}}
 if($content-match '"(?:originalToken|continuationToken)"\s*:\s*"[0-9a-f-]{36}"'){throw "Access token in $p"}
}
git -c core.excludesfile= add -- $files
if($LASTEXITCODE-ne 0){throw 'Stage failed'}
if(((git -c core.excludesfile= diff --cached --name-only|Sort-Object)-join "`n")-ne (($files|Sort-Object)-join "`n")){throw 'Stage scope differs'}
$xml=@($files|Where-Object {$_-like '*/xml/*.xml'})
$excludes=@($xml|ForEach-Object {':(exclude)'+$_})
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- . $excludes
if($LASTEXITCODE-ne 0){throw 'Whitespace failed'}
# 仅明确原始JUnit XML保留堆栈的行末空白，禁止修改真实测试记录凑门禁。
git -c core.excludesfile= -c core.whitespace=-blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- $xml
if($LASTEXITCODE-ne 0){throw 'Raw XML whitespace failed'}
$identity=@()
foreach($p in $files){
 $working=git -c core.excludesfile= hash-object --path=$p $p
 $staged=git -c core.excludesfile= rev-parse (':'+$p)
 if($working-ne $staged){throw "Stage identity differs: $p"}
 $identity+=@{path=$p;blob=$staged}
}
$audit=@{baseHead=(git rev-parse HEAD);sourceIdentity='PASS';buildIdentity='PASS';secrets='PASS';scope='PASS';stageIdentity='PASS';rawXmlWhitespaceException=$xml;files=$identity;providerCalls=0;databaseOperations=0;t05Complete=$false}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'stage-audit.json'),($audit|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= diff --cached --stat
git -c core.excludesfile= commit -m 'fix: isolate P01 probe scope and verify zero-call startup' -m 'T05 preflight checkpoint: bind existing immutable READ03 P01 request packet, one run capped at4 calls, require OnlyP01 and reject combined/P02 scope before credentials or Docker operations. Remove current probe P02 execution branch; preserve domain clarification/READ/publication implementation and original run/call budgets. RED25 cases expose5 old-scope defects; related backend136 pass, offline build,6 actual CMD preflight checks under persistent user/machine PATH and Windows PowerShell5.1 Chinese/package check pass, resources closed. No database/model operations, no retries, no reuse of old10-call remainder; no T05 completion or product quality claim. New explicit P01 supplier authorization remains required. Restore via reviewed inverse change and fresh verification; do not reset unrelated files or revive old authorization.'
if($LASTEXITCODE-ne 0){throw 'Commit failed'}
$sha=git rev-parse HEAD
foreach($item in $identity){if((git rev-parse ($sha+':'+$item.path))-ne $item.blob){throw 'Committed identity differs'}}
$receipt=@{implementationCommit=$sha;baseHead='f0eed907b1c16d7db7fb4475bd1126cbea8ee628';startedAt='2026-10-14T12:04:35+08:00';finishedAt=[DateTimeOffset]::Now.ToString('o');maximumMinutes=20;backendTests=136;cmdPreflightCases=6;windowsPowerShell51Passed=$true;providerCalls=0;databaseOperations=0;packageUnchanged=$true;packageSha256=$report.packageSha256;jarSha256=$report.jarSha256;authorizationGranted=$false;old10Reusable=$false;resourcesClosed=$true;t05Complete=$false}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'commit-receipt.json'),($receipt|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= add -- "$task/stage-audit.json" "$task/commit-receipt.json"
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Receipt whitespace failed'}
git -c core.excludesfile= commit -m 'docs: record P01 zero-call preflight checkpoint receipt'
if($LASTEXITCODE-ne 0){throw 'Receipt commit failed'}
Write-Output ('IMPLEMENTATION='+$sha)
Write-Output ('RECEIPT='+(git rev-parse HEAD))
