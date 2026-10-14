$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$base='docs/evidence/rule-agent-provider/2026-10-14'
$source=@(
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProvider.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProtocol.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentJson.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleSemanticReviewPort.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationProtocol.java',
 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentProviderTest.java'
)
if((git rev-parse HEAD)-ne '7570500693db94cff7a69f49c40625c474ad3557'){throw 'Base changed'}
$files=@($source)+@('docs/evidence/rule-agent-progress.md')
$exceptions=@()
foreach($phase in @('t05-review-adapter-red-01','t05-review-adapter-green-02','t05-review-adapter-green-03','t05-review-adapter-green-04')){
 foreach($name in @('results.json','command.json')){$files+="$base/$phase/$name"}
 $xml="$base/$phase/xml"
 foreach($file in Get-ChildItem -LiteralPath $xml -File){
  $p="$xml/$($file.Name)"
  $files+=$p
  [xml]$doc=Get-Content -LiteralPath $file.FullName -Raw
  if([int]$doc.testsuite.failures-gt 0-or [int]$doc.testsuite.errors-gt 0){$exceptions+=$p}
 }
}
$files+="$base/t05-review-adapter-green-04/source-after.json"
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object {
 $_.Extension -in @('.json','.md','.ps1') -and $_.Name -notin @('stage-audit.json','commit-receipt.json')
} | ForEach-Object { "$base/t05-17/$($_.Name)" })
$files=$files | Sort-Object -Unique
if(@(git -c core.excludesfile= diff --cached --name-only | Where-Object {$_ -notin $files}).Count){throw 'Index has unrelated files'}
$result=Get-Content -LiteralPath "$base/t05-review-adapter-green-04/results.json" -Raw | ConvertFrom-Json
if(!$result.passed-or $result.testCount-ne 109-or $result.sourceDrift-ne 0-or !$result.resourcesVerified){throw 'Final verification incomplete'}
$manifest=Get-Content -LiteralPath "$base/t05-review-adapter-green-04/source-after.json" -Raw | ConvertFrom-Json
$sourceIdentity=@()
foreach($p in $source){
 $raw=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()
 $entry=$manifest | Where-Object path -eq $p
 if(!$entry-or $entry.sha256-ne $raw){throw "Verified source differs: $p"}
 $sourceIdentity+=@{path=$p;sha256=$raw;testManifestMatched=$true}
}
$secretValues=@()
foreach($name in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','DASHSCOPE_API_KEY')){
 foreach($scope in @('Process','User','Machine')){try{$v=[Environment]::GetEnvironmentVariable($name,$scope);if($v){$secretValues+=$v}}catch{}}
}
foreach($p in $files){
 $content=[IO.File]::ReadAllText((Join-Path $root $p))
 foreach($v in $secretValues){if($content.Contains($v)){throw "Credential detected: $p"}}
 if($content -match '"(?:originalToken|continuationToken)"\s*:\s*"[0-9a-f-]{36}"'){throw "Access token detected: $p"}
 if($content -match '(?i)Bearer\s+sk-[a-z0-9]{12,}|"(?:password|api_key)"\s*:\s*"[^"\s]{4,}"'){throw "Sensitive value detected: $p"}
}
git -c core.excludesfile= add -- $files
if($LASTEXITCODE-ne 0){throw 'Stage failed'}
# 原始失败XML的CDATA空白保持原样，精确豁免这些证据的行尾检查，不改写历史结果。
$exclude=@($exceptions|ForEach-Object {':(exclude)'+$_})
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- . @exclude
if($LASTEXITCODE-ne 0){throw 'Whitespace check failed'}
foreach($p in $exceptions){
 git -c core.excludesfile= -c core.whitespace=-blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- $p
 if($LASTEXITCODE-ne 0){throw 'Raw evidence check failed'}
}
$identity=@()
foreach($p in $files){
 $working=git -c core.excludesfile= hash-object --path=$p $p
 $staged=git -c core.excludesfile= rev-parse (':'+$p)
 if($working-ne $staged){throw "Stage identity differs: $p"}
 $identity+=@{path=$p;blob=$staged}
}
if((($files|Sort-Object)-join "`n")-ne ((git -c core.excludesfile= diff --cached --name-only | Sort-Object)-join "`n")){throw 'Stage scope differs'}
$audit=[ordered]@{baseHead=(git rev-parse HEAD);files=$identity;sourceIdentity=$sourceIdentity;secretCheck='PASS; environment values checked without output';whitespace='PASS; original failed XML CDATA preserved';trailingWhitespaceExceptions=$exceptions;stageIdentity='PASS';actualProviderCalls=0;databaseCalls=0;t05Complete=$false;verifiedAt=[DateTimeOffset]::Now.ToString('o')}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'stage-audit.json'),($audit|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= diff --cached --stat
git -c core.excludesfile= commit -m 'feat: share provider transport with independent semantic review' -m 'Implement review port on the existing provider with isolated review payload and strict contract-derived parsing. Share client, allowance, attempt sequence, cancellation and usage-before-validation auditing; record purpose. Fix locally reproduced close cancellation wrapped as ExecutionException. Fresh 109 tests passed including 44 provider tests and legacy factory/task regressions. Only loopback fake HTTP, zero real supplier/database calls. New factory wiring and real probes remain pending; T05 incomplete. Restore these scoped files from parent if needed; old failure records preserved.'
if($LASTEXITCODE-ne 0){throw 'Commit failed'}
$sha=git rev-parse HEAD
foreach($item in $identity){if((git rev-parse ($sha+':'+$item.path))-ne $item.blob){throw 'Committed blob differs'}}
$receipt=[ordered]@{implementationCommit=$sha;parent=(git rev-parse HEAD^);tree=(git rev-parse 'HEAD^{tree}');sourceIdentityVerified=$true;backendTests=109;newProviderTests=12;actualProviderCalls=0;databaseCalls=0;remainingTask1LocalComplete=$true;t05Complete=$false;startedAt='2026-10-14T10:00:52+08:00';finishedAt=[DateTimeOffset]::Now.ToString('o');budgetMinutes=20;sourceFiles=$source;next='Remaining task 2: online factory/start configuration and local end-to-end validation; no real probe permission'}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'commit-receipt.json'),($receipt|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= add -- "$base/t05-17/stage-audit.json" "$base/t05-17/commit-receipt.json"
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Receipt whitespace failed'}
git -c core.excludesfile= commit -m 'docs: record semantic review adapter checkpoint'
if($LASTEXITCODE-ne 0){throw 'Receipt commit failed'}
Write-Output ('IMPLEMENTATION='+$sha)
Write-Output ('RECEIPT='+(git rev-parse HEAD))
