# 使用同一安全验收入口，只增加编译异常栈，不改变测试选择与凭证隔离。
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
$runnerSource=[IO.File]::ReadAllText((Join-Path $taskRoot 'knowledge/scripts/verify-rule-check-refactor.ps1'))
$functionsOnly=$runnerSource.Substring($runnerSource.IndexOf('function Get-RuleCheckVerificationPlan'),$runnerSource.IndexOf('$runnerRoot=')-$runnerSource.IndexOf('function Get-RuleCheckVerificationPlan'))
. ([scriptblock]::Create($functionsOnly))
function Invoke-RuleCheckMaven {
    param($Plan,[string[]]$Arguments,[string[]]$Secrets)
    Push-Location $Plan.Root
    try {
        & $Plan.Maven @Arguments -e 2>&1 | ForEach-Object {Protect-RuleCheckText $_.ToString() $Secrets} | Tee-Object -FilePath (Join-Path $Plan.Directory 'maven.log') | Out-Null
        return $LASTEXITCODE
    } finally {Pop-Location}
}
$result=Invoke-RuleCheckVerification -Root $taskRoot -Mode Unit -Phase r5-decision-red-diagnostic-20261008-01 -Tests RuleCheckDecisionServiceTest -Port 55439
$result|ConvertTo-Json -Depth 6
if (!$result.passed) {exit 1}
