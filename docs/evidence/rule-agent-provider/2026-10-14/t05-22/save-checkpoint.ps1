$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$base='docs/evidence/rule-agent-provider/2026-10-14'
$task="$base/t05-22"
if((git rev-parse HEAD)-ne '1926a2ba9172f3ef9607ead654b45f1e97be8de4'){throw 'Unexpected base'}
if(git -c core.excludesfile= diff --cached --name-only){throw 'Index not empty'}
$tests=Get-Content "$base/t05-count-contract-final-01/results.json" -Raw|ConvertFrom-Json
$build=Get-Content "$task/build-identity.json" -Raw|ConvertFrom-Json
if(!$tests.passed-or $tests.testCount-ne 143-or $tests.sourceDrift-ne 0-or !$tests.resourcesVerified){throw 'Tests not accepted'}
if(!$build.passed-or !$build.buildResourcesClosed-or !$build.checkResourcesClosed-or $build.providerCalls-ne 0-or $build.databaseOperations-ne 0){throw 'Build not accepted'}
$sources=@('knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProtocol.java','knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleCandidateReferences.java','knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineFactoryTest.java')
$snapshot=Get-Content "$base/t05-count-contract-final-01/source-after.json" -Raw|ConvertFrom-Json
foreach($p in $sources){
 $expected=@($snapshot|Where-Object path -eq $p)
 if($expected.Count-ne 1-or (Get-FileHash $p).Hash.ToLowerInvariant()-ne $expected[0].sha256){throw "Final verified source differs: $p"}
}
# 构建后只补充测试输入，不重建未改变的生产代码；产物核对生产文件，测试核对最终143项快照。
foreach($item in @($build.source|Where-Object {$_.path-like '*/src/main/*'})){
 if((Get-FileHash $item.path).Hash.ToLowerInvariant()-ne $item.sha256){throw 'Build runtime source differs'}
}
if((Get-FileHash 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar').Hash.ToLowerInvariant()-ne $build.jarSha256){throw 'Jar changed'}
if((Get-FileHash "$base/t05-19/request-package.json").Hash.ToLowerInvariant()-ne $build.packageSha256){throw 'Frozen packet changed'}
$files=$sources+@('docs/evidence/rule-agent-progress.md')
$files+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File|Where-Object Name -notin @('stage-audit.json','commit-receipt.json')|ForEach-Object {"$task/$($_.Name)"})
foreach($phase in @('t05-count-contract-red-01','t05-count-contract-green-01','t05-count-contract-final-01')){
 $files+=@("$base/$phase/results.json","$base/$phase/command.json","$base/$phase/source-after.json")
}
foreach($phase in @('t05-count-contract-red-01','t05-count-contract-final-01')){
 $files+=@(Get-ChildItem "$base/$phase/xml" -File|ForEach-Object {"$base/$phase/xml/$($_.Name)"})
}
$files=@($files|Sort-Object -Unique)
$secrets=@()
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY','POSTGRES_PASSWORD')){
 foreach($scope in @('Process','User','Machine')){
  try{$value=[Environment]::GetEnvironmentVariable($name,$scope);if($value){$secrets+=$value}}catch{}
 }
}
foreach($p in $files){
 $content=[IO.File]::ReadAllText((Join-Path $root $p))
 foreach($value in $secrets){if($content.Contains($value)){throw "Credential detected: $p"}}
 if($content-match '"(?:originalToken|continuationToken)"\s*:\s*"[0-9a-f-]{36}"'){throw "Access token detected: $p"}
}
git -c core.excludesfile= add -- $files
if($LASTEXITCODE-ne 0){throw 'Stage failed'}
if(((git -c core.excludesfile= diff --cached --name-only|Sort-Object)-join "`n")-ne (($files|Sort-Object)-join "`n")){throw 'Stage scope differs'}
$xml=@($files|Where-Object {$_-like '*/xml/*.xml'})
$excludes=@($xml|ForEach-Object {':(exclude)'+$_})
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- . $excludes
if($LASTEXITCODE-ne 0){throw 'Whitespace failed'}
# 只允许原始JUnit XML的堆栈行末空白，保留原字节，不改变失败和通过记录。
git -c core.excludesfile= -c core.whitespace=-blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- $xml
if($LASTEXITCODE-ne 0){throw 'Raw XML whitespace failed'}
$identity=@()
foreach($p in $files){
 $working=git -c core.excludesfile= hash-object --path=$p $p
 $staged=git -c core.excludesfile= rev-parse (':'+$p)
 if($working-ne $staged){throw "Stage identity differs: $p"}
 $identity+=@{path=$p;blob=$staged}
}
$audit=@{baseHead=(git rev-parse HEAD);sourceIdentity='PASS';buildRuntimeIdentity='PASS';finalTestIdentity='PASS';secrets='PASS';scope='PASS';stageIdentity='PASS';rawXmlWhitespaceException=$xml;files=$identity;providerCalls=0;databaseOperations=0;t05Complete=$false}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'stage-audit.json'),($audit|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= diff --cached --stat
git -c core.excludesfile= commit -m 'fix: distinguish safe READ count diagnostics and verify local P01 bounds' -m 'Local T05 diagnostic checkpoint only. Keep top-level READ_HANDLE_COUNT and five safe detail fields; classify non-array/below-min/above-max without values, UUIDs or exact counts. Share original1..3 count validator across wire and internal reads; leave model prompt/contract/permissions/publication/budget unchanged. RED31 exposes3 diagnostic failures, legal P01 host flow already passed; final143 backend pass, precise4-unique rejection and1/2/3 read+publication with reviewed article4 via production factory/tools and simulated retrieval/provider/reviewer. All10 candidate references retained; not real model/SQL quality proof. Offline build and zero-call frozen packet check pass. Previous real failure subtype remains unknown, no historical score rewrite or new calls, old allowances closed. JSON-object mode vs provider-enforced schema remains an unverified output-design question, not claimed solved. Restore via reviewed inverse change and revalidation; no push or T05 completion.'
if($LASTEXITCODE-ne 0){throw 'Commit failed'}
$sha=git rev-parse HEAD
foreach($item in $identity){if((git rev-parse ($sha+':'+$item.path))-ne $item.blob){throw 'Committed identity differs'}}
$receipt=@{implementationCommit=$sha;baseHead='1926a2ba9172f3ef9607ead654b45f1e97be8de4';startedAt='2026-10-14T13:58:51+08:00';finishedAt=[DateTimeOffset]::Now.ToString('o');maximumMinutes=20;backendTests=143;redDiagnosticFailures=3;providerCalls=0;databaseOperations=0;jarSha256=$build.jarSha256;packageSha256=$build.packageSha256;packetUnchanged=$true;promptUnchanged=$true;resourcesClosed=$true;realP01Passed=$false;previousRealSubtype='UNKNOWN_NOT_RETAINED';t05Complete=$false}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'commit-receipt.json'),($receipt|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
git -c core.excludesfile= add -- "$task/stage-audit.json" "$task/commit-receipt.json"
git -c core.excludesfile= -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
if($LASTEXITCODE-ne 0){throw 'Receipt whitespace failed'}
git -c core.excludesfile= commit -m 'docs: record local READ count diagnostic checkpoint receipt'
if($LASTEXITCODE-ne 0){throw 'Receipt commit failed'}
Write-Output ('IMPLEMENTATION='+$sha)
Write-Output ('RECEIPT='+(git rev-parse HEAD))
