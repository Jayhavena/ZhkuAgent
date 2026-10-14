$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$base='docs/evidence/rule-agent-provider/2026-10-14/t05-21'
$live='docs/evidence/rule-agent-provider/2026-10-14/t05-live-1c7d94d4c9e94870bb299b35b38b5c53'
if((git rev-parse HEAD)-ne '53616cc1636301b314fb1247c7d2a899ba4025b6'){throw 'Unexpected source HEAD'}
if(git -c core.excludesfile= diff --name-only -- knowledge/src knowledge/scripts agent-runtime/src scripts){throw 'Runtime source changed'}
if(git -c core.excludesfile= diff --cached --name-only){throw 'Index not empty'}
$report=Get-Content "$base/failure-assessment.json" -Raw|ConvertFrom-Json
if($report.probePassed-or !$report.safetyChecksPassed-or $report.actualProviderCalls-ne 2-or !$report.resourcesClosed-or !$report.unusedAllowanceClosed){throw 'Failure evidence not accepted'}
if((Get-FileHash "$live/provider-traces.jsonl").Hash.ToLowerInvariant()-ne $report.hashes.trace){throw 'Trace identity changed'}
$files=@('docs/evidence/rule-agent-progress.md',"$live/provider-traces.jsonl","$live/probe-result.json")
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File|Where-Object {$_.Extension-in @('.md','.json','.mjs','.ps1')-and $_.Name-notin @('failure-stage-audit.json','failure-commit-receipt.json')}|ForEach-Object {"$base/$($_.Name)"})
$files=@($files|Sort-Object -Unique)
$secrets=@()
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY','POSTGRES_PASSWORD')){
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
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Whitespace failed'}
$identity=@()
foreach($p in $files){
 $working=git -c core.excludesfile= hash-object --path=$p $p
 $staged=git -c core.excludesfile= rev-parse (':'+$p)
 if($working-ne $staged){throw "Stage identity differs: $p"}
 $identity+=@{path=$p;blob=$staged}
}
$audit=@{baseHead=(git rev-parse HEAD);evidenceOnly=$true;sourceUnchanged=$true;secrets='PASS';scope='PASS';stageIdentity='PASS';files=$identity;probePassed=$false;t05Complete=$false}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'failure-stage-audit.json'),($audit|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= diff --cached --stat
git -c core.excludesfile= commit -m 'docs: record stopped READ03 P01 probe with invalid selection count' -m 'Failure evidence only, not completion: explicit new packet authorization max4, actually2 DECISION HTTP200 calls, no retries/P02. Real temporary-DB search returned10 candidates including article4; second action rejected at ACTION_PARAMETERS/READ_HANDLE_COUNT, safe diagnostics show ARRAY only. Empty vs too-many and set/reference validity remain unknown because count guard runs first; do not infer old UUID root cause or claim real mapping success. No READ, independent review or claims; actual failed snapshot accepted by frontend contract. Usage2148 input/136 output tokens, currency unknown. Host and temporary container exited; remaining2 closed, old10 not reused. Runtime/prompts/budget and old scores unchanged. Raw outcome/access handles/host log not staged; restore via evidence checkpoint, never replay this authorization.'
if($LASTEXITCODE-ne 0){throw 'Evidence commit failed'}
$sha=git rev-parse HEAD
foreach($item in $identity){if((git rev-parse ($sha+':'+$item.path))-ne $item.blob){throw 'Committed identity differs'}}
$receipt=@{evidenceCommit=$sha;runtimeSourceCommit='85a533c50aaf28ef05568c38ac2d73e18ffc0b25';sourceHead='53616cc1636301b314fb1247c7d2a899ba4025b6';evidenceOnly=$true;probePassed=$false;t05Complete=$false;authorizedMaximum=4;actualProviderCalls=2;closedUnusedCalls=2;old10Reusable=$false;historicalProviderCalls=20;retryPerformed=$false;resourcesClosed=$true;failure='ACTION_PARAMETERS/READ_HANDLE_COUNT';emptyOrTooMany='UNKNOWN_NOT_RETAINED';candidateReferenceLegality='NOT_REACHED';startedAt='2026-10-14T12:26:28+08:00';finishedAt=[DateTimeOffset]::Now.ToString('o');maximumMinutes=20}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'failure-commit-receipt.json'),($receipt|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= add -- "$base/failure-stage-audit.json" "$base/failure-commit-receipt.json"
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Receipt whitespace failed'}
git -c core.excludesfile= commit -m 'docs: record failed READ03 probe evidence receipt'
if($LASTEXITCODE-ne 0){throw 'Receipt commit failed'}
Write-Output ('EVIDENCE='+$sha)
Write-Output ('RECEIPT='+(git rev-parse HEAD))
