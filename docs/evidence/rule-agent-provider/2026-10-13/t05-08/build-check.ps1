$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$secrets=@()
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY','POSTGRES_PASSWORD')){
 foreach($scope in @('Process','User','Machine')){try{$v=[Environment]::GetEnvironmentVariable($name,$scope);if($v){$secrets+=$v}}catch{}}
 Remove-Item -LiteralPath "Env:\$name" -ErrorAction SilentlyContinue
}
$env:PATH=[Environment]::GetEnvironmentVariable('PATH','Machine')+';'+[Environment]::GetEnvironmentVariable('PATH','User')
$started=[DateTimeOffset]::Now
$previous=[Environment]::CurrentDirectory
try{[Environment]::CurrentDirectory=$root;$result=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','knowledge\scripts\start-rule-agent-probe.cmd','-PrepareProbe') -TimeoutSeconds 150 -Secrets $secrets}finally{[Environment]::CurrentDirectory=$previous}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'build.log'),$result.output+"`n"+$result.errorOutput,$utf8)
$record=[ordered]@{started=$started.ToString('o');finished=[DateTimeOffset]::Now.ToString('o');exitCode=$result.exitCode;timedOut=$result.timedOut;resourcesClosed=$result.resourcesClosed;ordinaryUserPath=$true;providerCalls=0;databaseCalls=0;compiledJar=$false}
if($result.exitCode-eq 0-and !$result.timedOut-and $result.resourcesClosed){$record.compiledJar=$true;$record.jarSha256=(Get-FileHash -LiteralPath (Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar')).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'build-result.json'),($record|ConvertTo-Json),$utf8)
$record|ConvertTo-Json -Compress
if(!$record.compiledJar){exit 1}
