$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$base='docs/evidence/rule-agent-provider/2026-10-14'
$source=@(
 'agent/frontend/src/api/rule-agent-contract.js',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentContract.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentRunner.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleResultPublisher.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleSemanticReviewPort.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/RuleAgentLoopDatabaseTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/RulePublicationGateTest.java'
)
$files=@($source)+@('docs/evidence/rule-agent-progress.md')
foreach($phase in @('t05-finish-wire-red-01','t05-finish-wire-db-green-02','t05-finish-wire-db-green-04','t05-finish-wire-unit-green-03','t05-finish-wire-unit-green-05')){
 foreach($name in @('results.json','command.json','source-after.json','database.json','environment-failure.json')){
  $p="$base/$phase/$name"
  if(Test-Path -LiteralPath $p){$files+=$p}
 }
 $xml="$base/$phase/xml"
 if(Test-Path -LiteralPath $xml){$files+=@(Get-ChildItem -LiteralPath $xml -File | ForEach-Object { "$xml/$($_.Name)" })}
}
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object {
 $_.Extension -in @('.json','.md','.mjs','.ps1') -and $_.Name -notin @('stage-audit.json','commit-receipt.json')
} | ForEach-Object { "$base/t05-15/$($_.Name)" })
$files=$files | Sort-Object -Unique
$before=(git -c core.excludesfile= diff --cached --name-only)
if($before){throw 'Index was not empty'}
$sourceIdentity=@()
$manifest=Get-Content -LiteralPath "$base/t05-finish-wire-unit-green-05/source-after.json" -Raw | ConvertFrom-Json
foreach($p in $source){
 $raw=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()
 $entry=$manifest | Where-Object path -eq $p
 if($entry -and $entry.sha256-ne $raw){throw "Test source changed: $p"}
 $sourceIdentity+=@{path=$p;sha256=$raw;testManifestMatched=[bool]$entry}
}
$secretValues=@()
foreach($name in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','DASHSCOPE_API_KEY')){
 foreach($scope in @('Process','User','Machine')){
  try{$value=[Environment]::GetEnvironmentVariable($name,$scope);if($value){$secretValues+=$value}}catch{}
 }
}
foreach($p in $files){
 $content=[IO.File]::ReadAllText((Join-Path $root $p))
 foreach($value in $secretValues){if($content.Contains($value)){throw "Credential detected in $p"}}
 if($content -match '"originalToken"\s*:'){throw "Original access token in $p"}
 if($content -match '(?i)Bearer\s+sk-[a-z0-9]{12,}|"(?:password|api_key)"\s*:\s*"[^"\s]{4,}"'){throw "Sensitive value in $p"}
}
git -c core.excludesfile= add -- $files
if($LASTEXITCODE-ne 0){throw 'Stage failed'}
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Whitespace check failed'}
$identity=@()
foreach($p in $files){
 $working=git -c core.excludesfile= hash-object --path=$p $p
 $staged=git -c core.excludesfile= rev-parse (':'+$p)
 if($working-ne $staged){throw "Stage content differs: $p"}
 $identity+=@{path=$p;blob=$staged}
}
$expected=($files|Sort-Object)-join "`n"
$actual=(git -c core.excludesfile= diff --cached --name-only | Sort-Object)-join "`n"
if($expected-ne $actual){throw 'Stage scope differs'}
$audit=[ordered]@{baseHead=(git rev-parse HEAD);files=$identity;sourceIdentity=$sourceIdentity;secretCheck='PASS; environment values checked without output';whitespace='PASS';stageIdentity='PASS';actualProviderCalls=0;scope='Local FINISH wiring; T05 incomplete';verifiedAt=[DateTimeOffset]::Now.ToString('o')}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'stage-audit.json'),($audit|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= diff --cached --stat
git -c core.excludesfile= commit -m 'feat: wire local FINISH publication review' -m 'Align question-fact coverage with independent review while retaining evidence, condition and scope guards. Reserve one of four calls; partial omitted requirements remain processing failures. Verified 73 backend tests, 84 frontend tests, 2 temporary-database flow tests including six cases and actual snapshots accepted by frontend. Decision and review mocked; zero provider calls. ASK, online adapter/factory and T05 completion remain pending. Restore scoped changes from parent if needed; no demo database touched.'
if($LASTEXITCODE-ne 0){throw 'Commit failed'}
$sha=git rev-parse HEAD
foreach($item in $identity){if((git rev-parse ($sha+':'+$item.path))-ne $item.blob){throw 'Committed blob differs'}}
$receipt=[ordered]@{implementationCommit=$sha;parent=(git rev-parse HEAD^);tree=(git rev-parse 'HEAD^{tree}');verifiedSourceIdentity=$true;backendTests=73;frontendTests=84;databaseTests=2;databaseCases=6;actualProviderCalls=0;t05Complete=$false;startedAt='2026-10-14T00:02:09+08:00';finishedAt=[DateTimeOffset]::Now.ToString('o');budgetMinutes=20;sourceFiles=$source;next='ASK local review and online adapter design/wiring; no provider call permission'}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'commit-receipt.json'),($receipt|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= add -- "$base/t05-15/stage-audit.json" "$base/t05-15/commit-receipt.json"
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Receipt whitespace failed'}
git -c core.excludesfile= commit -m 'docs: record FINISH wiring checkpoint receipt'
if($LASTEXITCODE-ne 0){throw 'Receipt commit failed'}
Write-Output ('IMPLEMENTATION='+$sha)
Write-Output ('RECEIPT='+(git rev-parse HEAD))
