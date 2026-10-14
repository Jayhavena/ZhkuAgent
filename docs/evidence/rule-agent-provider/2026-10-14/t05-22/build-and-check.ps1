$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8
[Console]::OutputEncoding=$utf8
$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$secrets=@()
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY','POSTGRES_PASSWORD')){
 foreach($scope in @('Process','User','Machine')){
  try{$value=[Environment]::GetEnvironmentVariable($name,$scope);if($value){$secrets+=$value}}catch{}
 }
 [Environment]::SetEnvironmentVariable($name,$null,'Process')
}
$before=@('knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProtocol.java','knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleCandidateReferences.java','knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineFactoryTest.java')|ForEach-Object {@{path=$_;sha256=(Get-FileHash $_).Hash.ToLowerInvariant()}}
$previous=[Environment]::CurrentDirectory
try{
 [Environment]::CurrentDirectory=$root
 $build=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','knowledge\scripts\start-rule-agent-probe.cmd','-BuildOnly') -TimeoutSeconds 150 -Secrets $secrets
}finally{[Environment]::CurrentDirectory=$previous}
$buildRecord=@{exitCode=$build.exitCode;timedOut=$build.timedOut;resourcesClosed=$build.resourcesClosed;output=$build.output;errorOutput=$build.errorOutput;providerCalls=0;databaseOperations=0}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'build-result.json'),($buildRecord|ConvertTo-Json -Depth 5),$utf8)
if($build.exitCode-ne 0-or $build.timedOut-or !$build.resourcesClosed){throw 'Build failed, do not use previous jar'}
$sha='7bcb597074997edd9fc2ef22e5e85003df093e9eaf92b2cebc6d5a5d14a3f560'
try{
 [Environment]::CurrentDirectory=$root
 $check=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','knowledge\scripts\start-rule-agent-probe.cmd','-CheckPackage','-OnlyP01','-ApprovedPackageSha256',$sha) -TimeoutSeconds 15 -Secrets $secrets
}finally{[Environment]::CurrentDirectory=$previous}
if($check.exitCode-ne 0-or $check.timedOut-or !$check.resourcesClosed){throw 'Zero-call package check failed'}
$data=$check.output|ConvertFrom-Json
if(!$data.passed-or $data.providerCalls-ne 0-or $data.databaseOperations-ne 0){throw 'Package check not zero-call'}
foreach($item in $before){if((Get-FileHash $item.path).Hash.ToLowerInvariant()-ne $item.sha256){throw 'Source changed during build'}}
$record=@{passed=$true;buildResourcesClosed=$true;checkResourcesClosed=$true;sourceDrift=0;source=$before;jarSha256=(Get-FileHash 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar').Hash.ToLowerInvariant();packageSha256=(Get-FileHash 'docs/evidence/rule-agent-provider/2026-10-14/t05-19/request-package.json').Hash.ToLowerInvariant();packageCheck=$data;providerCalls=0;databaseOperations=0;authorizationGranted=$false}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'build-identity.json'),($record|ConvertTo-Json -Depth 7),$utf8)
$record|ConvertTo-Json -Depth 7
