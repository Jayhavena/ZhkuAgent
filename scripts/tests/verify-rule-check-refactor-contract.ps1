#requires -Version 7.0
param([string]$ReportPath='')
$ErrorActionPreference='Stop'
$testRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testRunner=Join-Path $testRoot 'knowledge/scripts/verify-rule-check-refactor.ps1'
if (!(Test-Path -LiteralPath $testRunner)) {
    Write-Output 'FAIL 验收入口不存在，无法拒绝不安全选择；productionImplementation=MISSING'
    exit 1
}
$testTokens=$null; $testErrors=$null
$testAst=[Management.Automation.Language.Parser]::ParseFile($testRunner,[ref]$testTokens,[ref]$testErrors)
if ($testErrors.Count) { throw '验收入口存在语法错误，不是目标行为RED' }
# 加载真正的生产函数，替身只代替外部进程；断言预检查的结果与副作用。
foreach ($testFunction in $testAst.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst]},$false)) {
    . ([scriptblock]::Create($testFunction.Extent.Text))
}
$script:testExternalStarts=0
function Invoke-RuleCheckMaven { $script:testExternalStarts++; throw 'UNEXPECTED_EXTERNAL_START' }
function Start-RuleCheckDatabase { $script:testExternalStarts++; throw 'UNEXPECTED_EXTERNAL_START' }
$testPhase='contract-'+[guid]::NewGuid().ToString('N')
$testBase=@{Root=$testRoot;Mode='Unit';Phase=$testPhase;Tests='HybridRetrievalServiceTest';Port=55439;DatabaseUrl=''}
$testCases=@(
    @{name='空测试集合';override=@{Tests=''}},
    @{name='通配符';override=@{Tests='*Test'}},
    @{name='方法选择绕过';override=@{Tests='HybridRetrievalServiceTest#*'}},
    @{name='供应商类';override=@{Tests='DashScopeLiveProviderTest'}},
    @{name='未登记类';override=@{Tests='SharedCorpusEvaluationReportTest'}},
    @{name='重复类';override=@{Tests='HybridRetrievalServiceTest,HybridRetrievalServiceTest'}},
    @{name='空列表项';override=@{Tests='HybridRetrievalServiceTest,'}},
    @{name='Unit选数据库类';override=@{Tests='KnowledgeSchemaTest'}},
    @{name='Database选单元类';override=@{Mode='Database';Tests='HybridRetrievalServiceTest'}},
    @{name='未知模式';override=@{Mode='Unknown'}},
    @{name='危险批次路径';override=@{Phase='../escape'}},
    @{name='原库端口';override=@{Port=54329}},
    @{name='原库URL';override=@{Mode='Database';Tests='KnowledgeSchemaTest';DatabaseUrl='jdbc:postgresql://127.0.0.1:54329/zhkuagent'}},
    @{name='外部host';override=@{Mode='Database';Tests='KnowledgeSchemaTest';DatabaseUrl='jdbc:postgresql://example.org:55439/zhkuagent_rulecheck_e2e'}},
    @{name='相同端口其他库';override=@{Mode='Database';Tests='KnowledgeSchemaTest';DatabaseUrl='jdbc:postgresql://127.0.0.1:55439/zhkuagent'}},
    @{name='URL额外参数';override=@{Mode='Database';Tests='KnowledgeSchemaTest';DatabaseUrl='jdbc:postgresql://127.0.0.1:55439/zhkuagent_rulecheck_e2e?options=x'}},
    @{name='URL端口与Port不一致';override=@{Mode='Database';Tests='KnowledgeSchemaTest';DatabaseUrl='jdbc:postgresql://127.0.0.1:55440/zhkuagent_rulecheck_e2e'}},
    @{name='已存在批次';override=@{Phase='r0-baseline-01'}}
)
$testResults=@()
foreach ($testCase in $testCases) {
    $testArguments=$testBase.Clone()
    foreach ($testKey in $testCase.override.Keys) {$testArguments[$testKey]=$testCase.override[$testKey]}
    $testRejected=$false; $testError=''
    try { Invoke-RuleCheckVerification @testArguments } catch { $testRejected=$true; $testError=$_.Exception.Message }
    $testDirectory=Join-Path $testRoot ('docs/evidence/rule-check-refactor/2026-10-07/'+$testArguments.Phase)
    $testUnchanged=($testArguments.Phase -eq 'r0-baseline-01') -or !(Test-Path -LiteralPath $testDirectory)
    $testPass=$testRejected -and $script:testExternalStarts -eq 0 -and $testUnchanged -and $testError -notlike '*UNEXPECTED_EXTERNAL_START*'
    $testResults+=@{name=$testCase.name;passed=$testPass;rejected=$testRejected;externalStarts=$script:testExternalStarts;directoryUnchanged=$testUnchanged}
    Write-Output "$(if($testPass){'PASS'}else{'FAIL'}) $($testCase.name)"
}
$testGood=Get-RuleCheckVerificationPlan @testBase
if ($testGood.Database -or $testGood.Selected.Count -ne 1) {throw '合法Unit选择结果错误'}
# 新Agent证据不能默默写入旧日期；检查真实生产函数，不创建批次或外部进程。
$newAgentRoot='docs/evidence/rule-agent-contract/2026-10-13'
$newAgentPlan=Get-RuleCheckVerificationPlan -Root $testRoot -Mode Unit -Phase $testPhase -Tests RuleAgentContractTest -Port 55439 -EvidenceRoot $newAgentRoot
$newAgentPass=$newAgentPlan.Directory -eq (Join-Path $testRoot "$newAgentRoot/$testPhase") -and !$newAgentPlan.Database
$testResults+=@{name='新Agent测试登记与显式证据根';passed=$newAgentPass}
Write-Output "$(if($newAgentPass){'PASS'}else{'FAIL'}) 新Agent证据根"
foreach($badRoot in @('../outside','C:/outside','docs/evidence/../outside','knowledge/evidence','docs/evidence/')){
    $rejected=$false
    try{Get-RuleCheckVerificationPlan -Root $testRoot -Mode Unit -Phase $testPhase -Tests RuleAgentContractTest -Port 55439 -EvidenceRoot $badRoot|Out-Null}catch{$rejected=$true}
    $testResults+=@{name="拒绝证据根-$badRoot";passed=$rejected}
}
$testDatabase=Get-RuleCheckVerificationPlan -Root $testRoot -Mode Database -Phase $testPhase -Tests KnowledgeSchemaTest -Port 55439 -DatabaseUrl ''
if ($testDatabase.DatabaseUrl -ne 'jdbc:postgresql://127.0.0.1:55439/zhkuagent_rulecheck_e2e') {throw '合法临时库目标错误'}
$testPackage04Root=Join-Path $testRoot ('.superpowers/sdd/2026-10-07-rule-check-p0-plan/package04-plan-fixture-'+[guid]::NewGuid().ToString('N'))
$testPackage04Java=Join-Path $testPackage04Root 'knowledge/src/test/java/RuleCheck2025ReliabilityReplayTest.java'
New-Item -ItemType Directory -Path (Split-Path $testPackage04Java) -Force|Out-Null
'fixture'|Set-Content -LiteralPath $testPackage04Java
# 正式回放目录完成后必须拒绝覆盖；独立根用于继续核对特殊目录映射，而不触碰历史证据。
$testPackage04=Get-RuleCheckVerificationPlan -Root $testPackage04Root -Mode Database -Phase package04-application-replay -Tests RuleCheck2025ReliabilityReplayTest -Port 55439 -DatabaseUrl ''
$testPackage04Expected=Join-Path $testPackage04Root 'docs/evidence/rule-check-refactor/2026-10-11/package-04/application-replay'
if ($testPackage04.Directory -ne $testPackage04Expected -or $testPackage04.DataProvenance -ne 'REVIEWED_2025_TEXT') {throw '第四包必须写入独立证据目录并标记审核资料'}
$testResults+=@{name='合法Unit和Database计划不启动进程';passed=($script:testExternalStarts -eq 0);externalStarts=$script:testExternalStarts}
foreach($testProvenanceCase in @(
    @{tests='KnowledgeSchemaTest';expected='SYNTHETIC_FIXTURES'},
    @{tests='RuleCheck2025RobustnessTest';expected='MIXED_REVIEWED_2025_AND_SYNTHETIC_FIXTURES'},
    @{tests='RuleCheck2025RobustnessTest,RuleCheck2025TrialTest';expected='MIXED_REVIEWED_2025_AND_SYNTHETIC_FIXTURES'},
    @{tests='RuleCheck2025TrialTest';expected='REVIEWED_2025_TEXT'},
    @{tests='RuleCheck2025ApplicabilityTest';expected='REVIEWED_2025_TEXT'},
    @{tests='RuleCheck2025ApplicabilityTest,JdbcCandidateRetrieverTest';expected='MIXED_REVIEWED_2025_AND_SYNTHETIC_FIXTURES'},
    @{tests='RuleCheck2025ApplicabilityTest,RuleCheckApplicabilityTest';mode='Acceptance';expected='MIXED_REVIEWED_2025_AND_SYNTHETIC_FIXTURES'},
    @{tests='RuleCheck2025TrialTest,RuleCheckApplicationServiceTest';mode='Acceptance';expected='MIXED_REVIEWED_2025_AND_SYNTHETIC_FIXTURES'},
    @{tests='RuleCheck2025DevelopmentCaptureTest';expected='REVIEWED_2025_TEXT'},
    @{tests='RuleCheck2025LexicalCalibrationTest';expected='REVIEWED_2025_TEXT'},
    @{tests='RuleCheck2025LexicalCalibrationTest,JdbcCandidateRetrieverTest';expected='MIXED_REVIEWED_2025_AND_SYNTHETIC_FIXTURES'},
    @{tests='RuleCheck2025TrialTest,RuleCheck2025DevelopmentCaptureTest';expected='REVIEWED_2025_TEXT'},
    @{tests='RuleCheck2025TrialTest,JdbcRuleCheckEvidenceReaderTest';expected='MIXED_REVIEWED_2025_AND_SYNTHETIC_FIXTURES'}
)) {
    $testProvenanceMode=if($testProvenanceCase.mode){$testProvenanceCase.mode}else{'Database'}
    $testProvenance=Get-RuleCheckVerificationPlan -Root $testRoot -Mode $testProvenanceMode -Phase $testPhase -Tests $testProvenanceCase.tests -Port 55439 -DatabaseUrl ''
    $testProvenancePass=$testProvenance.DataProvenance -eq $testProvenanceCase.expected
    $testResults+=@{name="资料来源分类-$($testProvenanceCase.expected)";passed=$testProvenancePass;externalStarts=$script:testExternalStarts}
    Write-Output "$(if($testProvenancePass){'PASS'}else{'FAIL'}) 资料来源分类-$($testProvenanceCase.expected)"
}
$testFixture=Join-Path $testRoot ('.superpowers/sdd/2026-10-07-rule-check-p0-plan/contract-fixture-'+[guid]::NewGuid().ToString('N'))
$testFiles=@('pom.xml','knowledge/pom.xml','agent-runtime/pom.xml','AGENTS.md','mvnw.cmd','.mvn/wrapper/maven-wrapper.properties',
    'scripts/Import-ZhkuCredentials.ps1','knowledge/scripts/verify-rule-check-refactor.ps1','knowledge/scripts/verify-rule-check-refactor.cmd',
    'scripts/tests/verify-rule-check-refactor-contract.ps1','knowledge/src/test/java/HybridRetrievalServiceTest.java','agent-runtime/src/fixture.txt')
foreach($testFile in $testFiles){
    $testFilePath=Join-Path $testFixture $testFile
    New-Item -ItemType Directory -Path (Split-Path $testFilePath) -Force|Out-Null
    'fixture'|Set-Content -LiteralPath $testFilePath
}
New-Item -ItemType Directory -Path (Join-Path $testFixture '.superpowers') -Force|Out-Null
# 外部Git/Maven替身仅用于隔离进程与制造报告；读取XML、计数、漂移及放行均走真实入口。
function git {'fixture-head'}
function Invoke-RuleCheckMaven {
    param($Plan,$Arguments,$Secrets)
    $script:testExternalStarts++
    $reportRoot=Join-Path $Plan.Root 'knowledge/target/surefire-reports'
    New-Item -ItemType Directory -Path $reportRoot -Force|Out-Null
    $xmlPath=Join-Path $reportRoot 'TEST-fixture.HybridRetrievalServiceTest.xml'
    if($script:testReportKind -ne 'missing') {
        $counts=@{tests=1;failures=0;errors=0;skipped=0}
        if($script:testReportKind -eq 'zero'){$counts.tests=0}
        if($script:testReportKind -eq 'skipped'){$counts.skipped=1}
        if($script:testReportKind -eq 'failure'){$counts.failures=1}
        if($script:testReportKind -eq 'error'){$counts.errors=1}
        "<testsuite name='fixture.HybridRetrievalServiceTest' tests='$($counts.tests)' failures='$($counts.failures)' errors='$($counts.errors)' skipped='$($counts.skipped)'><properties><property name='private' value='MUST_NOT_BE_COPIED'/></properties><testcase name='fixture'/><system-out>MUST_NOT_BE_COPIED</system-out></testsuite>"|Set-Content -LiteralPath $xmlPath
        if($script:testReportKind -eq 'stale'){(Get-Item -LiteralPath $xmlPath).LastWriteTimeUtc=[DateTime]::UtcNow.AddMinutes(-10)}
        if($script:testReportKind -eq 'drift'){'changed'|Set-Content -LiteralPath (Join-Path $Plan.Root 'knowledge/src/test/java/HybridRetrievalServiceTest.java')}
    }
    if($script:testReportKind -eq 'nonzero'){return 7}
    return 0
}
foreach($testKind in @('good','missing','stale','zero','skipped','failure','error','drift','nonzero')){
    $script:testReportKind=$testKind
    $testResult=Invoke-RuleCheckVerification -Root $testFixture -Mode Unit -Phase $testKind -Tests HybridRetrievalServiceTest -Port 55439
    $testExpected=$testKind -eq 'good'
    $testPass=$testResult.passed -eq $testExpected
    if($testKind -eq 'good') {
        $testXml=Get-Content -Raw -LiteralPath (Join-Path $testFixture "docs/evidence/rule-check-refactor/2026-10-07/good/xml/TEST-fixture.HybridRetrievalServiceTest.xml")
        $testPass=$testPass -and $testXml -notmatch 'MUST_NOT_BE_COPIED'
    }
    $testResults+=@{name="报告-$testKind";passed=$testPass;actual=$testResult.passed;expected=$testExpected}
    Write-Output "$(if($testPass){'PASS'}else{'FAIL'}) 报告-$testKind"
}
$testLockPath=Join-Path $testFixture '.superpowers/rule-check-verification.lock'
$testLock=[IO.File]::Open($testLockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$testStartsBeforeLock=$script:testExternalStarts; $testLockRejected=$false
try {Invoke-RuleCheckVerification -Root $testFixture -Mode Unit -Phase locked -Tests HybridRetrievalServiceTest -Port 55439|Out-Null} catch {$testLockRejected=$true} finally {$testLock.Dispose()}
$testLockPass=$testLockRejected -and $script:testExternalStarts -eq $testStartsBeforeLock -and !(Test-Path -LiteralPath (Join-Path $testFixture 'docs/evidence/rule-check-refactor/2026-10-07/locked'))
$testResults+=@{name='同仓验收锁占用时不启动Maven或写批次';passed=$testLockPass}
Write-Output "$(if($testLockPass){'PASS'}else{'FAIL'}) 同仓验收锁"
$testSummary=@{passed=(@($testResults|Where-Object {!$_.passed}).Count -eq 0);checks=$testResults.Count;results=$testResults;databaseOperations=0;providerCalls=0;scope='生产预检查与调用顺序；外部执行器为计数替身，实际Maven/容器另验收'}
if ($ReportPath) {$testSummary|ConvertTo-Json -Depth 8|Set-Content -Encoding utf8 -LiteralPath $ReportPath}
Write-Output "CONTRACT checks=$($testResults.Count) passed=$($testSummary.passed) externalStarts=$script:testExternalStarts"
if (!$testSummary.passed) {exit 1}
