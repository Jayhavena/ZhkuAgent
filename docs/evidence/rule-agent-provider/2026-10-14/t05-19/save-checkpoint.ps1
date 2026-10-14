$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$base='docs/evidence/rule-agent-provider/2026-10-14'
$task="$base/t05-19"
if((git rev-parse HEAD)-ne 'df4fdb5050c336f9d1984a5993090fbac12363d1'){throw 'Unexpected base HEAD'}
$source=@(
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProtocol.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProvider.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleCandidateReferences.java',
 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineFactoryTest.java',
 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentProviderTest.java'
)
$snapshot=Get-Content "$base/t05-read-ref-final-02/source-after.json" -Raw|ConvertFrom-Json
foreach($p in $source){
 $expected=@($snapshot|Where-Object path -eq $p)
 if($expected.Count-ne 1-or (Get-FileHash -LiteralPath $p).Hash.ToLowerInvariant()-ne $expected[0].sha256){throw "Verified source differs: $p"}
}
$result=Get-Content "$base/t05-read-ref-final-02/results.json" -Raw|ConvertFrom-Json
if(!$result.passed-or $result.testCount-ne 134-or !$result.resourcesVerified-or $result.sourceDrift-ne 0){throw 'Final tests not accepted'}
$front=Get-Content "$base/t05-18/frontend-green-t05-read-ref-02.json" -Raw|ConvertFrom-Json
if($front.exitCode-ne 0-or !$front.resourcesClosed-or $front.timedOut){throw 'Frontend not accepted'}
$files=$source+@('docs/evidence/rule-agent-progress.md')
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File|Where-Object Name -notin @('stage-audit.json','commit-receipt.json')|ForEach-Object {"$task/$($_.Name)"})
foreach($phase in @('t05-read-ref-red-01','t05-read-ref-red-02','t05-read-ref-green-01','t05-read-ref-final-01','t05-read-ref-final-02')){
 $files+=@("$base/$phase/results.json","$base/$phase/command.json","$base/$phase/source-before.json","$base/$phase/source-after.json")
}
$files+=@("$base/t05-read-ref-red-02/xml/TEST-com.zhku.knowledge.ruleagent.RuleAgentOnlineFactoryTest.xml")
$files+=@(Get-ChildItem "$base/t05-read-ref-final-02/xml" -File|ForEach-Object {"$base/t05-read-ref-final-02/xml/$($_.Name)"})
foreach($phase in @('01','02')){
 $files+=@("$base/t05-18/frontend-green-t05-read-ref-$phase.json","$base/t05-18/frontend-green-t05-read-ref-$phase.log")
}
$files=@($files|Sort-Object -Unique)
if(@(git -c core.excludesfile= diff --cached --name-only|Where-Object {$_-notin $files}).Count){throw 'Index contains unrelated files'}
$values=@()
foreach($name in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','DASHSCOPE_API_KEY')){
 foreach($scope in @('Process','User','Machine')){
  try{$value=[Environment]::GetEnvironmentVariable($name,$scope);if($value){$values+=$value}}catch{}
 }
}
foreach($p in $files){
 $content=[IO.File]::ReadAllText((Join-Path $root $p))
 foreach($value in $values){if($content.Contains($value)){throw "Credential detected: $p"}}
 if($content-match '"(?:originalToken|continuationToken)"\s*:\s*"[0-9a-f-]{36}"'){throw "Access token detected: $p"}
}
git -c core.excludesfile= add -- $files
if($LASTEXITCODE-ne 0){throw 'Stage failed'}
$staged=@(git -c core.excludesfile= diff --cached --name-only|Sort-Object)
if(($staged-join "`n")-ne (($files|Sort-Object)-join "`n")){throw 'Unexpected staged scope'}
$xml=@($files|Where-Object {$_-like '*/xml/*.xml'})
$logs=@($files|Where-Object {$_-like '*/frontend-green-t05-read-ref-*.log'})
$excludes=@(($xml+$logs)|ForEach-Object {':(exclude)'+$_})
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- . $excludes
if($LASTEXITCODE-ne 0){throw 'Whitespace check failed'}
# 原始测试XML包含JUnit堆栈空白，保留字节；仅这些明确文件豁免行末空白。
git -c core.excludesfile= -c core.whitespace=-blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- $xml
if($LASTEXITCODE-ne 0){throw 'Raw XML whitespace check failed'}
git -c core.excludesfile= -c core.whitespace=blank-at-eol,-blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- $logs
if($LASTEXITCODE-ne 0){throw 'Raw terminal log whitespace check failed'}
$identity=@()
foreach($p in $files){
 $working=git -c core.excludesfile= hash-object --path=$p $p
 $stagedBlob=git -c core.excludesfile= rev-parse (':'+$p)
 if($working-ne $stagedBlob){throw "Stage identity differs: $p"}
 $identity+=@{path=$p;blob=$stagedBlob}
}
$audit=@{baseHead=(git rev-parse HEAD);verifiedSource='PASS';scope='PASS';secrets='PASS';stageIdentity='PASS';rawXmlWhitespaceException=$xml;rawLogBlankEofException=$logs;files=$identity;t05Complete=$false}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'stage-audit.json'),($audit|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= diff --cached --stat
git -c core.excludesfile= commit -m 'fix: harden model READ references with host candidate mapping' -m 'T05 local checkpoint only: model selects host c references with a current task/run candidate-set nonce; host maps to original UUID actions and preserves existing preflight, tools, evidence and publication guards. Record bounded element/type/length/category/current-membership diagnostics, retain known usage, invalidate stale/cross-task/end-run references, expose all current candidates. Same 121 RED cases: 13 failures to PASS; final 134 backend and 84 frontend pass, no source drift, resources closed. No database or real model calls; old malformed value remains unknown. Prepare P01-only unauthorized packet; existing old-batch launcher must reject it. No reuse of old 10-call remainder, no P02/T06, no product success claim. Restore via reviewed inverse change and revalidation, not resetting unrelated work.'
if($LASTEXITCODE-ne 0){throw 'Commit failed'}
$sha=git rev-parse HEAD
foreach($item in $identity){if((git rev-parse ($sha+':'+$item.path))-ne $item.blob){throw 'Committed identity differs'}}
$receipt=@{implementationCommit=$sha;baseHead='df4fdb5050c336f9d1984a5993090fbac12363d1';localReadCheckpoint=$true;t05Complete=$false;backendTests=134;frontendTests=84;realProviderCalls=0;databaseOperations=0;startedAt='2026-10-14T11:08:26+08:00';finishedAt=[DateTimeOffset]::Now.ToString('o');maximumMinutes=20;packetSha256='7bcb597074997edd9fc2ef22e5e85003df093e9eaf92b2cebc6d5a5d14a3f560';packetAuthorized=$false;oldRemaining10Reusable=$false;launcherAdapted=$false;resourcesClosed=$true}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'commit-receipt.json'),($receipt|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= add -- "$task/stage-audit.json" "$task/commit-receipt.json"
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Receipt check failed'}
git -c core.excludesfile= commit -m 'docs: record local READ protocol checkpoint receipt'
if($LASTEXITCODE-ne 0){throw 'Receipt commit failed'}
Write-Output ('IMPLEMENTATION='+$sha)
Write-Output ('RECEIPT='+(git rev-parse HEAD))
