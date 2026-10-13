param([ValidateSet('java-red','java-green','frontend-green','runner','database')][string]$Step,[string]$Phase='t03-loop-unit-01')
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$secrets=@()
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY','POSTGRES_PASSWORD')){
    foreach($scope in @('Process','User','Machine')){try{$v=[Environment]::GetEnvironmentVariable($name,$scope);if($v){$secrets+=$v}}catch{}}
    [Environment]::SetEnvironmentVariable($name,$null,'Process')
}
if($Step-eq 'java-red'){
    $executable=$env:ComSpec;$arguments=@('/d','/c','mvnw.cmd','-o',"-Dmaven.repo.local=$env:USERPROFILE/.m2/repository",'-Dfile.encoding=UTF-8','-pl','knowledge','-am','-Dtest=RuleAgentEvidenceToolsTest','-Dsurefire.failIfNoSpecifiedTests=false','test')
}elseif($Step-eq 'java-green'){
    $executable=$env:ComSpec;$arguments=@('/d','/c','knowledge\scripts\verify-rule-check-refactor.cmd','-Mode','Unit','-Phase',$Phase,'-Tests','RuleAgentRunnerTest,RuleAgentEvidenceToolsTest,RuleAgentContractTest,RuleCheckEvidenceVerifierTest','-EvidenceRoot','docs/evidence/rule-agent-loop/2026-10-13')
}elseif($Step-eq 'database'){
    $executable=$env:ComSpec;$arguments=@('/d','/c','knowledge\scripts\verify-rule-check-refactor.cmd','-Mode','Database','-Phase',$Phase,'-Port','55514','-Tests','RuleAgentLoopDatabaseTest','-EvidenceRoot','docs/evidence/rule-agent-loop/2026-10-13')
}elseif($Step-eq 'frontend-green'){
    $executable=(Get-Command node.exe).Source;$arguments=@('--test','agent/frontend/tests/rule-agent-evidence.test.mjs','agent/frontend/tests/rule-check-api.test.mjs','agent/frontend/tests/rule-check-workflow.test.mjs','agent/frontend/tests/rule-check-page.test.mjs')
}else{
    $executable=Join-Path $PSHOME 'pwsh.exe';$arguments=@('-NoProfile','-File',(Join-Path $root 'scripts/tests/verify-rule-check-refactor-contract.ps1'),'-ReportPath',(Join-Path $PSScriptRoot 'runner-contract-result.json'))
}
$start=[DateTimeOffset]::Now;$previous=[Environment]::CurrentDirectory
try{[Environment]::CurrentDirectory=$root;$result=Invoke-ZhkuOwnedProcess -Executable $executable -Arguments $arguments -TimeoutSeconds 150 -Secrets $secrets}finally{[Environment]::CurrentDirectory=$previous}
$stem="$Step-$Phase"
[IO.File]::WriteAllText((Join-Path $PSScriptRoot "$stem.log"),$result.output+"`n"+$result.errorOutput,$utf8)
$record=[ordered]@{step=$Step;started=$start.ToString('o');finished=[DateTimeOffset]::Now.ToString('o');exitCode=$result.exitCode;timedOut=$result.timedOut;resourcesClosed=$result.resourcesClosed;arguments=$arguments;providerAllowed=$false;databaseAllowed=($Step-eq 'database');baseHead=(git -C $root rev-parse HEAD)}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot "$stem.json"),($record|ConvertTo-Json -Depth 6),$utf8)
$record|ConvertTo-Json -Depth 6 -Compress
($result.output+"`n"+$result.errorOutput)-split "`r?`n"|Select-Object -Last 14
if($result.exitCode-ne 0-or !$result.resourcesClosed-or $result.timedOut){exit 1}
