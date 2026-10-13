# 只准备明确清单和内容自审；实际暂存/提交另行执行，避免混入其他任务的文件。
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
$utf8=[Text.UTF8Encoding]::new($false)
$scope=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($manifest in @('docs/evidence/rule-agent-baseline/2026-10-13/t00-01/candidate-manifest.json','docs/evidence/rule-agent-contract/2026-10-13/t01-01/change-manifest.json','docs/evidence/rule-agent-evidence/2026-10-13/t02-02/change-manifest.json','docs/evidence/rule-agent-loop/2026-10-13/t03-01/change-manifest.json')){
    $record=Get-Content -LiteralPath (Join-Path $root $manifest) -Raw -Encoding utf8|ConvertFrom-Json
    foreach($file in $record.files){$scope[$file.path]=$file.sha256}
}
foreach($pair in $scope.GetEnumerator()){
    $path=Join-Path $root $pair.Key
    if(!(Test-Path -LiteralPath $path)|| (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()-ne $pair.Value){throw "验收清单与当前内容不符：$($pair.Key)"}
}
$additional=@('AGENTS.md','.gitattributes','docs/git-commit-workflow.md')
foreach($directory in @('docs/evidence/rule-agent-baseline/2026-10-13','docs/evidence/rule-agent-contract/2026-10-13','docs/evidence/rule-agent-evidence/2026-10-13','docs/evidence/rule-agent-loop/2026-10-13')){
    $additional+=Get-ChildItem -LiteralPath (Join-Path $root $directory) -Recurse -File|Where-Object {$_.Extension -in @('.md','.json','.log','.xml','.ps1','.mjs','.py','.patch')}|ForEach-Object {[IO.Path]::GetRelativePath($root,$_.FullName).Replace('\','/')}
}
foreach($path in $additional){$scope[$path]=(Get-FileHash -LiteralPath (Join-Path $root $path)).Hash.ToLowerInvariant()}
$excluded=@($scope.Keys|Where-Object {$_ -match '(^|/)__pycache__/|\.pyc$'})
foreach($path in $excluded){[void]$scope.Remove($path)}
$paths=@($scope.Keys|Sort-Object)
if(@($paths|Where-Object {$_ -ne '.mvn/wrapper/maven-wrapper.jar' -and ($_ -match '(^|/)(\.git|target|node_modules|dist|__pycache__|data/raw|infra/\.env)(/|$)' -or $_ -match '\.(pdf|zip|class|jar|pyc|png|jpg)$')}).Count){throw '清单包含不应提交的资产'}
$configured=@()
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY')){
    $found=$false
    foreach($environmentScope in @('Process','User','Machine')){try{$value=[Environment]::GetEnvironmentVariable($name,$environmentScope);if($value){$configured+=@{name=$name;value=$value};$found=$true}}catch{}}
    if(!$found){throw "秘密检查访问边界：当前无法读取$name，不能报告配置值检查通过"}
}
$fixtureContexts=@{
 'knowledge/scripts/test_build_rule_check_package05_freeze.py'='GENERATED_SYNTHETIC_TOKEN_REJECTION_FIXTURE'
 'knowledge/src/test/java/com/zhku/knowledge/campusagent/submission/CampusRequestFingerprintTest.java'='NUMERIC_BOUNDARY_FIXTURE'
 'knowledge/src/test/java/com/zhku/knowledge/retrieval/RuleCheckDatabaseTestTargetTest.java'='INVALID_PORT_FIXTURE'
 'knowledge/src/test/java/com/zhku/knowledge/serviceguide/HumanHandoffRouteTest.java'='SYNTHETIC_PHONE_FIXTURE'
 'knowledge/src/test/java/com/zhku/knowledge/serviceguide/JdbcServiceGuideRepositoryTest.java'='SYNTHETIC_PHONE_FIXTURE'
 'knowledge/src/test/java/com/zhku/knowledge/serviceguide/ServiceGuideAcceptanceTest.java'='SYNTHETIC_PHONE_FIXTURE'
 'knowledge/src/test/java/com/zhku/knowledge/serviceguide/ServiceGuideCardTest.java'='SYNTHETIC_PHONE_FIXTURE'
 'knowledge/src/test/java/com/zhku/knowledge/serviceguide/ServiceGuideModelTest.java'='SYNTHETIC_PHONE_FIXTURE'
 'knowledge/src/test/java/com/zhku/knowledge/web/streaming/ConversationStreamProcessorTest.java'='SYNTHETIC_CITATION_IDENTIFIER'
 'knowledge/src/test/java/com/zhku/knowledge/ZhkuDatabaseTestTargetTest.java'='INVALID_PORT_FIXTURE'
}
$patternFixtures=@('knowledge/src/test/java/com/zhku/knowledge/rag/ProviderProfileContractTest.java','knowledge/src/test/java/com/zhku/knowledge/rag/SpringAiAdapterContractTest.java','knowledge/src/test/java/com/zhku/knowledge/tools/modelcall/CourseCreditModelBudgetProtocolTest.java','knowledge/src/test/java/com/zhku/knowledge/tools/modelcall/CourseCreditSpringAiProtocolContractTest.java')
$findings=@();$blocked=@();$patternFindings=@()
foreach($path in $paths){
    $text=[IO.File]::ReadAllText((Join-Path $root $path))
    foreach($item in $configured){if($text.Contains($item.value)){
        $classification=$fixtureContexts[$path]
        # 仅为本次自审建立的精确文件+内容身份例外；文件变化后必须重新审查，不能沿用宽泛忽略。
        if(!$classification){$blocked+=$path;$classification='UNREVIEWED_CONFIGURED_VALUE_MATCH'}
        $findings+=@{path=$path;variable=$item.name;classification=$classification;sha256=$scope[$path]}
    }}
    if($text -match '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----' -or $text -match 'sk-[A-Za-z0-9_-]{16,}'){
        $reviewed=$path -eq 'knowledge/scripts/test_build_rule_check_package05_freeze.py'
        if($path -in $patternFixtures){$reviewed=(git -C $root hash-object "--path=$path" $path)-eq (git -C $root rev-parse "HEAD:$path")}
        $patternFindings+=@{path=$path;reviewedSyntheticLocalFixture=$reviewed;sha256=$scope[$path]}
        if(!$reviewed){$blocked+=$path}
    }
}
$records=@($paths|ForEach-Object {@{path=$_;sha256=$scope[$_]}})
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'scope-manifest.json'),(@{files=$records;fileCount=$paths.Count;excludedGeneratedCache=$excluded;baseline='T00 + T01 + T02 + T03';scope='CURRENT_ENGINEERING_CHECKPOINT_NOT_PRODUCT_RELEASE'}|ConvertTo-Json -Depth 6),$utf8)
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'secret-review.json'),(@{freshConfiguredValueScan=$true;findings=$findings;patternFindings=$patternFindings;blockedPaths=@($blocked|Sort-Object -Unique);passed=($blocked.Count-eq 0);valuesNeverPrinted=$true;publicationApproved=$false}|ConvertTo-Json -Depth 6),$utf8)
if($blocked.Count){throw ('未核对的敏感匹配：'+(($blocked|Sort-Object -Unique)-join ', '))}
[IO.File]::WriteAllBytes((Join-Path $PSScriptRoot 'pathspec.bin'),$utf8.GetBytes(($paths -join "`0")+"`0"))
@{files=$paths.Count;reviewedFixtureMatches=$findings.Count;secretReviewPassed=$true}|ConvertTo-Json -Compress
