$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$base='docs/evidence/rule-agent-provider/2026-10-14/t05-probe-04'
$live='docs/evidence/rule-agent-provider/2026-10-14/t05-live-48509ea06b12415daee4f1e842fe774c'
if((git rev-parse HEAD)-ne '2fc4a359a360997ed03403c83f204982868a5d40'){throw 'Unexpected base'}
if((git -c core.excludesfile= diff --name-only -- knowledge/src knowledge/scripts)){throw 'Runtime source changed'}
$report=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'failure-assessment.json') -Raw | ConvertFrom-Json
if($report.probePassed-or !$report.safetyChecksPassed-or $report.actualProviderCalls-ne 2-or !$report.resourcesClosed){throw 'Failure evidence inconsistent'}
$files=@('docs/evidence/rule-agent-progress.md',"$live/provider-traces.jsonl","$live/probe-result.json")
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object {
 $_.Extension -in @('.json','.md','.mjs','.ps1') -and $_.Name -notin @('failure-stage-audit.json','failure-commit-receipt.json')
} | ForEach-Object { "$base/$($_.Name)" })
$files=$files|Sort-Object -Unique
if(@(git -c core.excludesfile= diff --cached --name-only|Where-Object {$_ -notin $files}).Count){throw 'Index contains unrelated files'}
$values=@()
foreach($name in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','DASHSCOPE_API_KEY')){
 foreach($scope in @('Process','User','Machine')){try{$v=[Environment]::GetEnvironmentVariable($name,$scope);if($v){$values+=$v}}catch{}}
}
foreach($p in $files){
 $content=[IO.File]::ReadAllText((Join-Path $root $p))
 foreach($v in $values){if($content.Contains($v)){throw "Credential in $p"}}
 if($content -match '"(?:originalToken|continuationToken)"\s*:\s*"[0-9a-f-]{36}"'){throw "Access token in $p"}
}
git -c core.excludesfile= add -- $files
if($LASTEXITCODE-ne 0){throw 'Stage failed'}
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Whitespace check failed'}
$identity=@()
foreach($p in $files){
 $working=git -c core.excludesfile= hash-object --path=$p $p
 $staged=git -c core.excludesfile= rev-parse (':'+$p)
 if($working-ne $staged){throw "Stage identity differs: $p"}
 $identity+=@{path=$p;blob=$staged}
}
if(((git -c core.excludesfile= diff HEAD --name-only -- $files|Sort-Object)-join "`n")-ne ((git -c core.excludesfile= diff --cached --name-only|Sort-Object)-join "`n")){throw 'Stage scope differs'}
$audit=[ordered]@{baseHead=(git rev-parse HEAD);evidenceOnly=$true;runtimeSourceChanged=$false;probePassed=$false;t05Complete=$false;stageIdentity='PASS';secretCheck='PASS';files=$identity}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'failure-stage-audit.json'),($audit|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= commit -m 'docs: record stopped T05 probe with invalid READ handle' -m 'Evidence only: authorized maximum 12, actually 2 decision calls, no retries. Retrieval supplied 10 canonical UUID candidates including article 4. Second HTTP200 response rejected at ACTION_PARAMETERS/READ_HANDLE_UUID; no READ or claim published, independent review and P02 not reached. Exact invalid value not retained; do not infer root cause. Usage 2327 input/150 output tokens, cost unknown, owned resources exited. Preserve two pre-dispatch helper errors; no runtime source or old score edits. T05 remains incomplete; this is not a completion commit.'
if($LASTEXITCODE-ne 0){throw 'Evidence commit failed'}
$sha=git rev-parse HEAD
foreach($item in $identity){if((git rev-parse ($sha+':'+$item.path))-ne $item.blob){throw 'Committed blob differs'}}
$receipt=[ordered]@{evidenceCommit=$sha;runtimeSourceCommit='c43dc8cf3b819e998791b0c59e814e1cf31a4aa6';evidenceOnly=$true;probePassed=$false;t05Complete=$false;maximumAuthorizedCalls=12;actualCalls=2;unspentCallsNotUsed=10;retryPerformed=$false;resourcesClosed=$true;startedAt='2026-10-14T10:40:58+08:00';finishedAt=[DateTimeOffset]::Now.ToString('o');budgetMinutes=20;failure='ACTION_PARAMETERS/READ_HANDLE_UUID';exactInvalidValue='UNKNOWN_NOT_RETAINED'}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'failure-commit-receipt.json'),($receipt|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= add -- "$base/failure-stage-audit.json" "$base/failure-commit-receipt.json"
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Receipt whitespace failed'}
git -c core.excludesfile= commit -m 'docs: record failed real probe evidence receipt'
if($LASTEXITCODE-ne 0){throw 'Receipt commit failed'}
Write-Output ('EVIDENCE='+$sha)
Write-Output ('RECEIPT='+(git rev-parse HEAD))
