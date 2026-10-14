$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$base='docs/evidence/rule-agent-provider/2026-10-14'
$source=@(
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentRunner.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationContract.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationGate.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleResultPublisher.java',
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/RuleAgentLoopDatabaseTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/RulePublicationGateTest.java'
)
if((git rev-parse HEAD)-ne '04192dbe67587acd0a816d7db5c7e532402c78f2'){throw 'Base changed'}
$files=@($source)+@('docs/evidence/rule-agent-progress.md')
foreach($phase in @('t05-ask-wire-red-01','t05-ask-wire-unit-green-02','t05-ask-wire-db-green-03','t05-ask-wire-db-green-05','t05-ask-wire-unit-green-07')){
 foreach($name in @('results.json','command.json','database.json','database-attempt.json')){
  $p="$base/$phase/$name"
  if(Test-Path -LiteralPath $p){$files+=$p}
 }
 $xml="$base/$phase/xml"
 if(Test-Path -LiteralPath $xml){$files+=@(Get-ChildItem -LiteralPath $xml -File | ForEach-Object { "$xml/$($_.Name)" })}
}
$files+="$base/t05-ask-wire-unit-green-07/source-after.json"
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object {
 $_.Extension -in @('.json','.md','.mjs','.ps1') -and $_.Name -notin @('stage-audit.json','commit-receipt.json')
} | ForEach-Object { "$base/t05-16/$($_.Name)" })
$files=$files | Sort-Object -Unique
$existing=@(git -c core.excludesfile= diff --cached --name-only)
if($existing | Where-Object { $_ -notin $files }){throw 'Index contains unrelated files'}
$sourceIdentity=@()
$manifests=@(
 (Get-Content -LiteralPath "$base/t05-ask-wire-unit-green-07/source-after.json" -Raw | ConvertFrom-Json),
 (Get-Content -LiteralPath "$base/t05-ask-wire-db-green-05/source-after.json" -Raw | ConvertFrom-Json)
)
foreach($p in $source){
 $raw=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()
 foreach($manifest in $manifests){$entry=$manifest | Where-Object path -eq $p; if(!$entry-or $entry.sha256-ne $raw){throw "Test source differs: $p"}}
 $sourceIdentity+=@{path=$p;sha256=$raw;unitAndDatabaseMatched=$true}
}
foreach($phase in @('t05-ask-wire-unit-green-07','t05-ask-wire-db-green-05')){
 $r=Get-Content -LiteralPath "$base/$phase/results.json" -Raw | ConvertFrom-Json
 if(!$r.passed-or $r.sourceDrift-ne 0-or !$r.resourcesVerified){throw 'Final verification incomplete'}
}
$secretValues=@()
foreach($name in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','DASHSCOPE_API_KEY')){
 foreach($scope in @('Process','User','Machine')){try{$v=[Environment]::GetEnvironmentVariable($name,$scope);if($v){$secretValues+=$v}}catch{}}
}
foreach($p in $files){
 $content=[IO.File]::ReadAllText((Join-Path $root $p))
 foreach($v in $secretValues){if($content.Contains($v)){throw "Credential detected: $p"}}
 if($content -match '"(?:originalToken|continuationToken)"\s*:'){throw "Access token detected: $p"}
 if($content -match '(?i)Bearer\s+sk-[a-z0-9]{12,}|"(?:password|api_key)"\s*:\s*"[^"\s]{4,}"'){throw "Sensitive value detected: $p"}
}
git -c core.excludesfile= add -- $files
if($LASTEXITCODE-ne 0){throw 'Stage failed'}
$rawFailure="$base/t05-ask-wire-red-01/xml/TEST-com.zhku.knowledge.ruleagent.RulePublicationGateTest.xml"
# 失败XML的CDATA带原始行尾空白，保留原始失败证据；仅此文件豁免行尾检查。
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- . (':(exclude)'+$rawFailure)
if($LASTEXITCODE-ne 0){throw 'Whitespace check failed'}
git -c core.excludesfile= -c core.whitespace=-blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- $rawFailure
if($LASTEXITCODE-ne 0){throw 'Raw failure artifact check failed'}
$identity=@()
foreach($p in $files){
 $working=git -c core.excludesfile= hash-object --path=$p $p
 $staged=git -c core.excludesfile= rev-parse (':'+$p)
 if($working-ne $staged){throw "Stage identity differs: $p"}
 $identity+=@{path=$p;blob=$staged}
}
if((($files|Sort-Object)-join "`n")-ne ((git -c core.excludesfile= diff --cached --name-only | Sort-Object)-join "`n")){throw 'Stage scope differs'}
$audit=[ordered]@{baseHead=(git rev-parse HEAD);files=$identity;sourceIdentity=$sourceIdentity;secretCheck='PASS; environment values checked without printing';whitespace='PASS; original red XML CDATA trailing whitespace preserved, sole exception';stageIdentity='PASS';actualProviderCalls=0;t05Complete=$false;verifiedAt=[DateTimeOffset]::Now.ToString('o')}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'stage-audit.json'),($audit|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= diff --cached --stat
git -c core.excludesfile= commit -m 'feat: validate ASK through independent publication review' -m 'Require missing audited conditions to be independently necessary; preserve only relevant verified independent facts. Share existing four-call/45-second budget with FINISH. Fresh checks: 75 backend tests, 84 frontend tests, 3 temporary-database flow tests, 9 actual snapshots accepted by frontend. Decision and review mocked; zero real supplier calls. Preserve prior interrupted run. TaskStore continuation-token consumption, online reviewer/usage/factory and T05 completion remain unverified. Restore scoped changes from parent if needed; no demo database writes.'
if($LASTEXITCODE-ne 0){throw 'Commit failed'}
$sha=git rev-parse HEAD
foreach($item in $identity){if((git rev-parse ($sha+':'+$item.path))-ne $item.blob){throw 'Committed blob differs'}}
$receipt=[ordered]@{implementationCommit=$sha;parent=(git rev-parse HEAD^);tree=(git rev-parse 'HEAD^{tree}');sourceIdentityVerified=$true;backendTests=75;frontendTests=84;databaseTests=3;actualSnapshotsChecked=9;actualProviderCalls=0;t05Complete=$false;startedAt='2026-10-14T09:47:46+08:00';finishedAt=[DateTimeOffset]::Now.ToString('o');budgetMinutes=20;sourceFiles=$source;next='Independent online review adapter/shared usage-budget/factory wiring; local validation before any new authorized real probe'}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'commit-receipt.json'),($receipt|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= add -- "$base/t05-16/stage-audit.json" "$base/t05-16/commit-receipt.json"
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Receipt whitespace failed'}
git -c core.excludesfile= commit -m 'docs: record ASK publication review checkpoint'
if($LASTEXITCODE-ne 0){throw 'Receipt commit failed'}
Write-Output ('IMPLEMENTATION='+$sha)
Write-Output ('RECEIPT='+(git rev-parse HEAD))
