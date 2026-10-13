# T00证据辅助程序：只调用既有入口，不修改业务、全局环境或Git。
param([ValidateSet('environment','runtime','knowledge','frontend-tests','frontend-build','package','dependencies','terminal')][string]$Step)
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$secrets=@()
foreach($name in @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','DASHSCOPE_API_KEY','ZHKU_RULECHECK_STUDENT_PASSWORD')){
    foreach($scope in @('Process','User','Machine')){
        try{$value=[Environment]::GetEnvironmentVariable($name,$scope);if($value){$secrets+=$value}}catch{}
    }
    [Environment]::SetEnvironmentVariable($name,$null,'Process')
}
$working=$root;$executable=(Join-Path $PSHOME 'pwsh.exe');$timeout=150
$common=@('-o',"-Dmaven.repo.local=$env:USERPROFILE/.m2/repository",'-Dfile.encoding=UTF-8')
switch($Step){
    'environment' {$executable=$env:ComSpec;$arguments=@('/d','/c','knowledge\scripts\verify-rule-check-refactor.cmd','-Mode','Unit','-Phase','agent-baseline-20261013-01','-Tests','RuleCheckEvidenceVerifierTest,RuleCheckDecisionServiceTest,RuleCheckControllerTest','-CheckEnvironment');$timeout=30}
    'knowledge' {$executable=$env:ComSpec;$arguments=@('/d','/c','knowledge\scripts\verify-rule-check-refactor.cmd','-Mode','Unit','-Phase','agent-baseline-20261013-01','-Tests','RuleCheckEvidenceVerifierTest,RuleCheckDecisionServiceTest,RuleCheckControllerTest')}
    'runtime' {$executable=$env:ComSpec;$arguments=@('/d','/c','mvnw.cmd')+$common+@('-pl','agent-runtime','-Dtest=SharedRuntimeContractTest,AgentRuntimeLifecycleContractTest','test')}
    'frontend-tests' {$executable=(Get-Command node.exe).Source;$arguments=@('--test','agent/frontend/tests/rule-check-api.test.mjs','agent/frontend/tests/rule-check-workflow.test.mjs','agent/frontend/tests/rule-check-page.test.mjs')}
    'frontend-build' {$working=Join-Path $root 'agent/frontend';$executable=$env:ComSpec;$arguments=@('/d','/c','npm.cmd','run','build')}
    'package' {$arguments=@('-NoProfile','-File',(Join-Path $root 'knowledge/scripts/start-controlled-rule-check.ps1'),'-BuildOnly')}
    'dependencies' {$executable=$env:ComSpec;$arguments=@('/d','/c','mvnw.cmd')+$common+@('-pl','knowledge','-am','dependency:tree','-Dincludes=org.springframework.ai:*,com.zhku:agent-runtime','-Dverbose')}
    'terminal' {$executable=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe';$arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $PSScriptRoot 'terminal-probe.ps1'));$timeout=30}
}
$started=[DateTimeOffset]::Now
$originalDirectory=[Environment]::CurrentDirectory
Push-Location $working
try{
    # PowerShell位置与.NET进程目录不同；后者决定未指定WorkingDirectory的子进程起点。
    [Environment]::CurrentDirectory=$working
    $result=Invoke-ZhkuOwnedProcess -Executable $executable -Arguments $arguments -TimeoutSeconds $timeout -Secrets $secrets
}finally{[Environment]::CurrentDirectory=$originalDirectory;Pop-Location}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot "$Step.log"),$result.output+"`n"+$result.errorOutput,$utf8)
$record=[ordered]@{step=$Step;started=$started.ToString('o');finished=[DateTimeOffset]::Now.ToString('o');workingDirectory=$working;executable=$executable;arguments=$arguments;timeoutSeconds=$timeout;exitCode=$result.exitCode;timedOut=$result.timedOut;resourcesClosed=$result.resourcesClosed;providerAllowed=$false;databaseAllowed=$false;sourceHead=(git -C $root rev-parse HEAD)}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot "$Step.json"),($record|ConvertTo-Json -Depth 8),$utf8)
$record|ConvertTo-Json -Depth 8 -Compress
$lines=($result.output+"`n"+$result.errorOutput)-split "`r?`n"
$lines|Select-Object -Last 14
if($result.exitCode-ne 0-or $result.timedOut-or !$result.resourcesClosed){exit 1}
