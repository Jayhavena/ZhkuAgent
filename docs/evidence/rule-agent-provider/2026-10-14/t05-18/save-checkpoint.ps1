$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$base='docs/evidence/rule-agent-provider/2026-10-14'
$source=@(
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineFactory.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineProbeHost.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProtocol.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProvider.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationContract.java',
 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineFactoryTest.java',
 'knowledge/scripts/start-rule-agent-probe.ps1'
)
if((git rev-parse HEAD)-ne 'fa4f08594ed8b226854218494fdd6329a5e3730f'){throw 'Base changed'}
$files=@($source)+@('docs/evidence/rule-agent-progress.md')
$exceptions=@()
foreach($phase in @('t05-online-wire-red-01','t05-online-wire-red-02','t05-online-wire-green-03','t05-online-wire-green-05')){
 foreach($name in @('results.json','command.json')){$files+="$base/$phase/$name"}
 $xml="$base/$phase/xml"
 foreach($file in Get-ChildItem -LiteralPath $xml -File){
  $p="$xml/$($file.Name)"
  $files+=$p
  [xml]$doc=Get-Content -LiteralPath $file.FullName -Raw
  if([int]$doc.testsuite.failures-gt 0-or [int]$doc.testsuite.errors-gt 0){$exceptions+=$p}
 }
}
$files+="$base/t05-online-wire-green-05/source-after.json"
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object {
 $_.Extension -in @('.json','.md','.ps1','.mjs') -and $_.Name -notin @('stage-audit.json','commit-receipt.json')
} | ForEach-Object { "$base/t05-18/$($_.Name)" })
$files+="docs/evidence/rule-agent-provider/2026-10-14/t05-probe-04/request-package.json"
$files=$files | Sort-Object -Unique
if(@(git -c core.excludesfile= diff --cached --name-only | Where-Object {$_ -notin $files}).Count){throw 'Index has unrelated files'}
$result=Get-Content -LiteralPath "$base/t05-online-wire-green-05/results.json" -Raw | ConvertFrom-Json
if(!$result.passed-or $result.testCount-ne 112-or $result.sourceDrift-ne 0-or !$result.resourcesVerified){throw 'Final verification incomplete'}
$manifest=Get-Content -LiteralPath "$base/t05-online-wire-green-05/source-after.json" -Raw | ConvertFrom-Json
$sourceIdentity=@()
foreach($p in $source){
 $raw=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()
 $entry=$manifest | Where-Object path -eq $p
 if((!$entry-and !$p.EndsWith('.ps1'))-or ($entry-and $entry.sha256-ne $raw)){throw "Verified source differs: $p"}
 $sourceIdentity+=@{path=$p;sha256=$raw;testManifestMatched=[bool]$entry}
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
git -c core.excludesfile= commit -m 'feat: wire independent review into online factory and probe startup' -m 'Wire the same decision/review port into factory. Bind action revision 02, three decision plus one review budget and fresh P01/P02 package; reject old permission paths. Validate real TaskStore token continuation, idempotency and cancellation with fake HTTP. Fresh 112 backend tests, 84 frontend tests, offline CMD build and zero-call package check passed. Synthetic evidence only; zero supplier/database calls. Real probe remains unapproved and T05 incomplete. Preserve failures; restore scoped files from parent if needed.'
if($LASTEXITCODE-ne 0){throw 'Commit failed'}
$sha=git rev-parse HEAD
foreach($item in $identity){if((git rev-parse ($sha+':'+$item.path))-ne $item.blob){throw 'Committed blob differs'}}
$receipt=[ordered]@{implementationCommit=$sha;parent=(git rev-parse HEAD^);tree=(git rev-parse 'HEAD^{tree}');sourceIdentityVerified=$true;backendTests=112;frontendTests=84;actualSnapshotsChecked=2;actualProviderCalls=0;databaseCalls=0;remainingTask2LocalComplete=$true;t05Complete=$false;startedAt='2026-10-14T10:16:48+08:00';finishedAt=[DateTimeOffset]::Now.ToString('o');budgetMinutes=20;sourceFiles=$source;next='Remaining task 3: specifically authorized real probes and T05 engineering closure'}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'commit-receipt.json'),($receipt|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= add -- "$base/t05-18/stage-audit.json" "$base/t05-18/commit-receipt.json"
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Receipt whitespace failed'}
git -c core.excludesfile= commit -m 'docs: record online factory checkpoint'
if($LASTEXITCODE-ne 0){throw 'Receipt commit failed'}
Write-Output ('IMPLEMENTATION='+$sha)
Write-Output ('RECEIPT='+(git rev-parse HEAD))
