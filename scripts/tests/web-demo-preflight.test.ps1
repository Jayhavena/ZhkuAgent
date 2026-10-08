#requires -Version 7.0
$ErrorActionPreference='Stop'
$testRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testTokens=$null; $testErrors=$null
$testAst=[Management.Automation.Language.Parser]::ParseFile((Join-Path $testRoot 'knowledge/scripts/start-controlled-web-demo.ps1'),[ref]$testTokens,[ref]$testErrors)
if($testErrors.Count){throw '启动器语法错误，不能算行为测试失败'}
# 执行真正的准入检查分支，只替换进程清单；不启动脚本后面的库、构建或模型。
$testGuard=$testAst.FindAll({param($node) $node -is [Management.Automation.Language.IfStatementAst] -and $node.Extent.Text.Contains('Get-CimInstance') -and $node.Extent.Text.Contains('已有构建/演示进程')},$true) | Sort-Object {$_.Extent.Text.Length} | Select-Object -First 1
if(!$testGuard){throw '未找到生产准入分支'}
function Get-CimInstance { param($ClassName,$Filter) $script:testProcessList }
$root='D:\alldesk\Projects\ZhkuAgent'
$testMain='org.codehaus.plexus.classworlds.launcher.Launcher'
$testCases=@(
    @{name='同仓Maven必须阻止';line="java -Dmaven.multiModuleProjectDirectory=D:\alldesk\Projects\ZhkuAgent $testMain test";blocked=$true},
    @{name='其他仓库Maven不占本仓target';line="java -Dmaven.multiModuleProjectDirectory=D:\other\backend $testMain package";blocked=$false},
    @{name='无法识别Maven工作目录仍阻止';line="java $testMain test";blocked=$true},
    @{name='类路径jar不等于CLI构建';line='java -cp D:\alldesk\Projects\ZhkuAgent\plexus-classworlds.jar org.jetbrains.idea.maven.server.RemoteMavenServer';blocked=$false},
    @{name='仓库名前缀相似不能误认';line="java -Dmaven.multiModuleProjectDirectory=D:\alldesk\Projects\ZhkuAgent-other $testMain test";blocked=$false},
    @{name='正斜杠与大小写仍属同仓';line="java -Dmaven.multiModuleProjectDirectory=d:/alldesk/projects/zhkuagent $testMain test";blocked=$true},
    @{name='本仓演示仍阻止';line='java -Dloader.main=com.zhku.knowledge.controlled.demo.ControlledWebDemoHost -cp D:\alldesk\Projects\ZhkuAgent\knowledge\target\app.jar';blocked=$true}
)
$testFailed=0
foreach($testCase in $testCases){
    $script:testProcessList=@([pscustomobject]@{CommandLine=$testCase.line;ProcessId=1})
    $blocked=$false
    try {& ([scriptblock]::Create($testGuard.Extent.Text))} catch {if($_.Exception.Message -notlike '*已有构建/演示进程*'){throw};$blocked=$true}
    if($blocked -ne $testCase.blocked){$testFailed++;Write-Output "FAIL $($testCase.name): blocked=$blocked expected=$($testCase.blocked)"}else{Write-Output "PASS $($testCase.name)"}
}
Write-Output "PREFLIGHT_TESTS total=$($testCases.Count) failed=$testFailed databaseOperations=0 providerCalls=0"
if($testFailed){exit 1}
