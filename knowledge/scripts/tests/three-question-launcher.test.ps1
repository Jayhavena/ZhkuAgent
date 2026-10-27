#requires -Version 7.0
$ErrorActionPreference='Stop'
$testLauncher=Join-Path $PSScriptRoot '../start-rule-agent-three-question-test.ps1'
$testTokens=$null;$testErrors=$null
$testAst=[Management.Automation.Language.Parser]::ParseFile($testLauncher,[ref]$testTokens,[ref]$testErrors)
if($testErrors.Count){throw '启动器语法错误，不能算行为测试失败'}
# 使用真实参数绑定和入口校验；首个资源解析边界强制终止，绝不进入凭证、目录创建或进程执行。
$testEntryStatements=@(foreach($statement in $testAst.EndBlock.Statements){
 if($statement -is [Management.Automation.Language.FunctionDefinitionAst]){break}
 $statement.Extent.Text
})
$testScriptRoot=(Split-Path -Parent $testLauncher).Replace("'","''")
$testEntry=[scriptblock]::Create($testAst.ParamBlock.Extent.Text+"`n"+"`$PSScriptRoot='$testScriptRoot'`n"+($testEntryStatements -join "`n"))
function Resolve-Path {
 param($Path,$LiteralPath)
 $script:testResourceBoundaryReached=$true
 throw 'TEST_RESOURCE_BOUNDARY_REACHED'
}
function Assert-Entry {
 param([hashtable]$Parameters,[string]$ExpectedRejection)
 $script:testResourceBoundaryReached=$false
 $message=$null
 try{& $testEntry @Parameters}catch{$message=$_.Exception.Message}
 if($ExpectedRejection){
  if($script:testResourceBoundaryReached -or !$message -or !$message.Contains($ExpectedRejection)){
   throw "应在资源边界前拒绝：$ExpectedRejection；实际拒绝=$message；资源边界=$script:testResourceBoundaryReached"
  }
 }elseif(!$script:testResourceBoundaryReached -or $message -ne 'TEST_RESOURCE_BOUNDARY_REACHED'){
  throw "有效参数应通过纯参数校验；实际拒绝=$message"
 }
}
function Assert-EvidenceDirectory {
 param([hashtable]$Parameters,[string]$ExpectedRejection)
 # 路径选择走真实入口；资源查询均替换，在首次存在性查询处停止，绝不创建证据目录。
 function Resolve-Path {
  param($Path,$LiteralPath)
  [pscustomobject]@{Path=if($LiteralPath){'D:\offline fixture\assets'}else{'D:\offline fixture\repo'}}
 }
 function Get-NetTCPConnection {param($State,$LocalPort,$ErrorAction) return $null}
 function Test-Path {
  param($LiteralPath,$PathType)
  $script:testEvidenceBoundaryReached=$true
  throw 'TEST_EVIDENCE_BOUNDARY_REACHED'
 }
 $script:testEvidenceBoundaryReached=$false
 $message=$null
 try{& $testEntry @Parameters}catch{$message=$_.Exception.Message}
 if($ExpectedRejection){
  if($script:testEvidenceBoundaryReached -or !$message -or !$message.Contains($ExpectedRejection)){throw "证据路径应被拒绝：$ExpectedRejection；实际=$message"}
 }elseif(!$script:testEvidenceBoundaryReached -or $message -ne 'TEST_EVIDENCE_BOUNDARY_REACHED'){
  throw "证据路径应通过纯路径校验；实际=$message"
 }
}
$testMavenFunctions=@($testAst.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-Maven'},$true))
if($testMavenFunctions.Count -ne 1){throw '须恰有一个真实 Invoke-Maven 供参数边界测试'}
function Get-MavenInvocation {
 param([string]$Profile,[string]$Operation,[int]$PageProxyPort=0,[switch]$WithoutPackage,[string]$HandbookPdf)
 $root="D:\offline fixture\repo's root"
 $assets="D:\offline fixture\assets's root"
 $batch="D:\offline fixture\batch's path"
 $PackageSha256=if($WithoutPackage){$null}else{'a'*64}
 $ApprovedSha256='a'*64
 $ApprovedBudgetRmb=if($Profile -eq 'E02'){'4.02'}else{'16.10'}
 $AuthorizationId='explicit-approval-001'
 $pwsh='TEST_ONLY_POWERSHELL_BOUNDARY'
 $mvn='Invoke-TestMavenBoundary'
 # 只载入真实函数；Invoke-Child 是进程边界替身，原编码命令在内存 runspace 内调用 argv 捕获器。
 . ([scriptblock]::Create($testMavenFunctions[0].Extent.Text))
 function Invoke-Child([string]$exe,[string[]]$argv,[int]$seconds){
  if($exe -ne 'TEST_ONLY_POWERSHELL_BOUNDARY' -or $argv.Count -ne 3 -or $argv[0] -ne '-NoProfile' -or $argv[1] -ne '-EncodedCommand'){
   throw '仅允许内存编码命令捕获，禁止真实进程'
  }
  $command=[Text.Encoding]::Unicode.GetString([Convert]::FromBase64String($argv[2]))
  $shell=[powershell]::Create()
  try{
   $adapter='function Invoke-TestMavenBoundary { param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArguments) $global:LASTEXITCODE=0; [pscustomobject]@{MavenArguments=$MavenArguments} }; '
   [void]$shell.AddScript($adapter+$command)
   $results=@($shell.Invoke())
   if($shell.HadErrors -or $results.Count -ne 1 -or !$results[0].PSObject.Properties['MavenArguments']){throw '真实编码命令未到达唯一的 Maven argv 捕获器'}
   [pscustomobject]@{mavenArguments=@($results[0].MavenArguments);timeoutSeconds=$seconds}
  }finally{$shell.Dispose()}
 }
 Invoke-Maven $Operation 37
}
function Assert-ContainsOnce {
 param([string[]]$Arguments,[string]$Expected)
 if(@($Arguments | Where-Object {$_ -ceq $Expected}).Count -ne 1){throw "Maven argv 须恰好含一个参数：$Expected"}
}
$testTotal=0;$testFailed=0
function Test-Case {
 param([string]$Name,[scriptblock]$Body)
 $script:testTotal++
 try{& $Body;Write-Output "PASS $Name"}catch{$script:testFailed++;Write-Output "FAIL $Name : $($_.Exception.Message)"}
}
Test-Case 'E02拒绝三题硬帽且不触及资源边界' {
 Assert-Entry @{Operation='PageFixture';Profile='E02';HardBudgetRmb='16.10'} '档位与硬预算不匹配'
}
Test-Case 'Live授权SHA不符在资源边界前拒绝' {
 Assert-Entry @{Operation='Live';Profile='E02';PackageSha256=('a'*64);ApprovedSha256=('b'*64);ApprovedBudgetRmb='4.02';AuthorizationId='e02-explicit-approval'} 'Live须先获本包SHA'
}
Test-Case 'Live即使双方SHA相同也拒绝不完整SHA且不触及资源边界' {
 Assert-Entry @{Operation='Live';Profile='E02';PackageSha256='old-short-sha';ApprovedSha256='old-short-sha';ApprovedBudgetRmb='4.02';AuthorizationId='e02-explicit-approval'} 'Live须先获本包SHA'
}
Test-Case 'Live拒绝带尾随换行的授权编号' {
 Assert-Entry @{Operation='Live';Profile='E02';PackageSha256=('a'*64);ApprovedSha256=('a'*64);ApprovedBudgetRmb='4.02';AuthorizationId="e02-explicit-approval`n"} 'Live须先获本包SHA'
}
Test-Case 'Live拒绝双方一致但带尾随换行的SHA' {
 Assert-Entry @{Operation='Live';Profile='E02';PackageSha256=(('a'*64)+"`n");ApprovedSha256=(('a'*64)+"`n");ApprovedBudgetRmb='4.02';AuthorizationId='e02-explicit-approval'} 'Live须先获本包SHA'
}
Test-Case 'E02实际Maven argv显式传档位且保持离线及带引号路径' {
 $invocation=Get-MavenInvocation -Profile 'E02' -Operation 'Prepare'
 Assert-ContainsOnce $invocation.mavenArguments '-Dzhku.three.profile=E02'
 Assert-ContainsOnce $invocation.mavenArguments '-o'
 Assert-ContainsOnce $invocation.mavenArguments "-Dzhku.three.codeRoot=D:\offline fixture\repo's root"
 Assert-ContainsOnce $invocation.mavenArguments "-Dzhku.t06.root=D:\offline fixture\assets's root"
 Assert-ContainsOnce $invocation.mavenArguments "-Dzhku.three.batch=D:\offline fixture\batch's path"
}
$testProfiles=@(
 @{name='E02';budget='4.02';wrongBudget='16.10';profileArgument='-Dzhku.three.profile=E02'},
 @{name='Three';budget='16.10';wrongBudget='4.02';profileArgument=$null}
)
Test-Case '缺省档位和缺省硬帽可通过纯参数校验' {Assert-Entry @{} $null}
foreach($testProfile in $testProfiles){
 foreach($operation in @('Prepare','Verify','Assembly','Live','PageFixture')){
  Test-Case "$($testProfile.name) $operation 错配硬帽在资源边界前拒绝" {
   Assert-Entry @{Operation=$operation;Profile=$testProfile.name;HardBudgetRmb=$testProfile.wrongBudget} '档位与硬预算不匹配'
  }
 }
 Test-Case "$($testProfile.name) 缺省硬帽通过纯参数校验" {Assert-Entry @{Profile=$testProfile.name} $null}
 Test-Case "$($testProfile.name) 显式匹配硬帽通过纯参数校验" {Assert-Entry @{Profile=$testProfile.name;HardBudgetRmb=$testProfile.budget} $null}
 Test-Case "$($testProfile.name) 完整匹配Live授权通过纯参数校验" {
  Assert-Entry @{Operation='Live';Profile=$testProfile.name;PackageSha256=('a'*64);ApprovedSha256=('a'*64);ApprovedBudgetRmb=$testProfile.budget;AuthorizationId='explicit-approval-001'} $null
 }
 foreach($testOperation in @(@{name='Prepare';argument='prepare'},@{name='Verify';argument='verify'},@{name='Assembly';argument='assembly'},@{name='Live';argument='live'})){
  Test-Case "$($testProfile.name) $($testOperation.name) 实际Maven argv档位及授权隔离" {
   $invocation=Get-MavenInvocation -Profile $testProfile.name -Operation $testOperation.name
   $arguments=$invocation.mavenArguments
   Assert-ContainsOnce $arguments '-o'
   Assert-ContainsOnce $arguments '-Dtest=RuleAgentThreeQuestionHostTest'
   Assert-ContainsOnce $arguments ('-Dzhku.three.operation='+$testOperation.argument)
   Assert-ContainsOnce $arguments ('-Dzhku.three.sha256='+('a'*64))
   Assert-ContainsOnce $arguments 'test'
   if(@($arguments | Where-Object {$_ -like '-Dzhku.t06.handbookPdf=*'}).Count){throw '未显式指定手册时不得合成或沿用旧PDF路径'}
   if($invocation.timeoutSeconds -ne 37){throw '未传递本次调用的超时边界'}
   $profileArguments=@($arguments | Where-Object {$_ -like '-Dzhku.three.profile=*'})
   if($testProfile.profileArgument){
    if($profileArguments.Count -ne 1 -or $profileArguments[0] -cne $testProfile.profileArgument){throw 'E02必须显式且唯一传递E02档位'}
   }elseif($profileArguments.Count){throw 'Three不得传入E02或其他显式档位'}
   $authorizationArguments=@($arguments | Where-Object {$_ -like '-Dzhku.three.approved*' -or $_ -like '-Dzhku.three.authorizationId=*'})
   if($testOperation.name -eq 'Live'){
    if($authorizationArguments.Count -ne 3){throw 'Live必须且仅转发三项人工授权参数'}
    Assert-ContainsOnce $arguments ('-Dzhku.three.approvedSha256='+('a'*64))
    Assert-ContainsOnce $arguments ('-Dzhku.three.approvedBudgetRmb='+$testProfile.budget)
    Assert-ContainsOnce $arguments '-Dzhku.three.authorizationId=explicit-approval-001'
   }elseif($authorizationArguments.Count){throw '非Live不得转发人工授权参数'}
  }
 }
}
$testInvalidAuthorizations=@(
 @{name='缺少包SHA和批准SHA';changes=@{PackageSha256=$null;ApprovedSha256=$null}},
 @{name='缺少批准SHA';changes=@{ApprovedSha256=$null}},
 @{name='批准预算不符';changes=@{ApprovedBudgetRmb='16.10'}},
 @{name='缺少批准预算';changes=@{ApprovedBudgetRmb=$null}},
 @{name='非规范批准预算';changes=@{ApprovedBudgetRmb='4.020'}},
 @{name='缺少授权编号';changes=@{AuthorizationId=$null}},
 @{name='授权编号含空格';changes=@{AuthorizationId='not an authorization'}},
 @{name='授权编号超长';changes=@{AuthorizationId=('a'*129)}},
 @{name='本地测试编号';changes=@{AuthorizationId='LOCAL_TEST_ONLY'}},
 @{name='非十六进制SHA';changes=@{PackageSha256=('g'*64);ApprovedSha256=('g'*64)}}
)
foreach($testInvalid in $testInvalidAuthorizations){
 Test-Case "Live拒绝$($testInvalid.name)且不触及资源边界" {
  $parameters=@{Operation='Live';Profile='E02';PackageSha256=('a'*64);ApprovedSha256=('a'*64);ApprovedBudgetRmb='4.02';AuthorizationId='explicit-approval-001'}
  foreach($key in $testInvalid.changes.Keys){$parameters[$key]=$testInvalid.changes[$key]}
  Assert-Entry $parameters 'Live须先获本包SHA'
 }
}
Test-Case 'Maven未给包SHA时不合成或沿用旧SHA' {
 $invocation=Get-MavenInvocation -Profile 'E02' -Operation 'Prepare' -WithoutPackage
 if(@($invocation.mavenArguments | Where-Object {$_ -like '-Dzhku.three.sha256=*'}).Count){throw '无包SHA时不得生成SHA参数'}
}
foreach($operation in @('Assembly','Prepare','Live')){
 Test-Case "$operation 实际Maven argv仅Assembly转发页面代理端口" {
  $invocation=Get-MavenInvocation -Profile 'E02' -Operation $operation -PageProxyPort 18088
  $ports=@($invocation.mavenArguments | Where-Object {$_ -like '-Dzhku.three.pageProxyPort=*'})
  if($operation -eq 'Assembly'){
   if($ports.Count -ne 1 -or $ports[0] -cne '-Dzhku.three.pageProxyPort=18088'){throw 'Assembly须唯一转发页面代理端口'}
  }elseif($ports.Count){throw '非Assembly不得转发页面代理端口'}
 }
}
Test-Case '启动器公开参数原样接收HandbookPdf路径而不读取文件' {
 $bind=[scriptblock]::Create($testAst.ParamBlock.Extent.Text+"`n"+'[pscustomobject]@{bound=$PSBoundParameters}')
 $binding=& $bind -Profile 'E02' -HandbookPdf "D:\offline fixture\handbook's copy.pdf"
 if(!$binding.bound.ContainsKey('HandbookPdf') -or $binding.bound['HandbookPdf'] -cne "D:\offline fixture\handbook's copy.pdf"){
  throw '公开HandbookPdf参数必须保留调用者提供的原始路径'
 }
}
Test-Case '实际Maven argv原样转发带空格和单引号的显式HandbookPdf路径' {
 $invocation=Get-MavenInvocation -Profile 'E02' -Operation 'Prepare' -HandbookPdf "D:\offline fixture\handbook's copy.pdf"
 Assert-ContainsOnce $invocation.mavenArguments "-Dzhku.t06.handbookPdf=D:\offline fixture\handbook's copy.pdf"
}
Test-Case '实际Maven argv不附加空值HandbookPdf参数' {
 foreach($value in @($null,'')){
  $invocation=Get-MavenInvocation -Profile 'E02' -Operation 'Prepare' -HandbookPdf $value
  if(@($invocation.mavenArguments | Where-Object {$_ -like '-Dzhku.t06.handbookPdf=*'}).Count){throw '空手册路径不得生成PDF参数'}
 }
}
Test-Case 'E02接受10月28日独立证据根内的新批次路径' {
 Assert-EvidenceDirectory @{Operation='Prepare';Profile='E02';AssetsRoot='D:\offline fixture\assets';BatchDirectory='D:\offline fixture\repo\docs\evidence\rule-check-refactor\2026-10-28\e02-single-page-live\new-batch'} $null
}
Test-Case 'E02拒绝旧10月27日证据根批次路径' {
 Assert-EvidenceDirectory @{Operation='Prepare';Profile='E02';AssetsRoot='D:\offline fixture\assets';BatchDirectory='D:\offline fixture\repo\docs\evidence\rule-check-refactor\2026-10-27\e02-single-page-live\old-batch'} '本批只允许独立证据目录内的子目录'
}
Test-Case 'Three仍接受原10月26日证据根批次路径' {
 Assert-EvidenceDirectory @{Operation='Prepare';Profile='Three';AssetsRoot='D:\offline fixture\assets';BatchDirectory='D:\offline fixture\repo\docs\evidence\rule-check-refactor\2026-10-26\three-question-online-prepare\new-batch'} $null
}
Test-Case 'PageFixture仍接受原10月27日证据根' {
 Assert-EvidenceDirectory @{Operation='PageFixture';PageEvidenceDirectory='D:\offline fixture\repo\docs\evidence\rule-check-refactor\2026-10-27\fixture-child'} $null
}
Test-Case 'PageFixture不随E02移动到10月28日证据根' {
 Assert-EvidenceDirectory @{Operation='PageFixture';PageEvidenceDirectory='D:\offline fixture\repo\docs\evidence\rule-check-refactor\2026-10-28\fixture-child'} '页面证据须位于本轮独立子目录'
}
Write-Output "LAUNCHER_TESTS total=$testTotal failed=$testFailed actualMavenRuns=0 databaseOperations=0 credentialReads=0 providerCalls=0"
if($testFailed){exit 1}
