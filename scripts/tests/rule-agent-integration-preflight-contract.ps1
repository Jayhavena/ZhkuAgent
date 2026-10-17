#requires -Version 7.0
param([string]$ReportPath='')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$tokens=$null;$parseErrors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $root 'knowledge/scripts/verify-rule-check-refactor.ps1'),[ref]$tokens,[ref]$parseErrors)
if($parseErrors.Count){throw 'Verification entry has parse errors'}
foreach($function in $ast.FindAll({param($node)$node -is [Management.Automation.Language.FunctionDefinitionAst]},$false)){
    . ([scriptblock]::Create($function.Extent.Text))
}
$cases=@()
foreach($testClass in @('RuleAgentControlledLaunchTest','RuleAgentLaunchCompatibilityTest')){
    $passed=$false;$reason=''
    try{
        # 装配检查没有数据库或供应商副作用；拒绝登记会令同版本验收根本未执行。
        $plan=Get-RuleCheckVerificationPlan -Root $root -Mode Unit -Phase ('integration-preflight-'+[guid]::NewGuid().ToString('N')) -Tests $testClass -Port 55439 -EvidenceRoot 'docs/evidence/rule-agent-integration/2026-10-18'
        $passed=(!$plan.Database -and $plan.Selected.Count -eq 1 -and $plan.Selected[0] -eq $testClass)
    }catch{$reason=$_.Exception.Message}
    $cases+=@{testClass=$testClass;passed=$passed;reason=$reason}
    Write-Output ("{0} {1}" -f $(if($passed){'PASS'}else{'FAIL'}),$testClass)
}
$result=@{passed=(@($cases|Where-Object {!$_.passed}).Count -eq 0);cases=$cases;externalStarts=0;databaseOperations=0;providerCalls=0}
if($ReportPath){[IO.File]::WriteAllText($ReportPath,(($result|ConvertTo-Json -Depth 4) -replace "`r`n","`n"),[Text.UTF8Encoding]::new($false))}
if(!$result.passed){exit 1}