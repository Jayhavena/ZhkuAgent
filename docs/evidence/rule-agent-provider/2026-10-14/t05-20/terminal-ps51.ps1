$ErrorActionPreference='Stop'
$utf8=New-Object Text.UTF8Encoding($false)
[Console]::InputEncoding=$utf8
[Console]::OutputEncoding=$utf8
$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location -LiteralPath $root
$env:PATH=([Environment]::GetEnvironmentVariable('PATH','Machine')+';'+[Environment]::GetEnvironmentVariable('PATH','User'))
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY','POSTGRES_PASSWORD')){
 [Environment]::SetEnvironmentVariable($name,$null,'Process')
}
& (Join-Path $root 'knowledge/scripts/start-rule-agent-probe.cmd') -CheckEnvironment
if($LASTEXITCODE-ne 0){exit $LASTEXITCODE}
& (Join-Path $root 'knowledge/scripts/start-rule-agent-probe.cmd') -CheckPackage -OnlyP01 -ApprovedPackageSha256 7bcb597074997edd9fc2ef22e5e85003df093e9eaf92b2cebc6d5a5d14a3f560
exit $LASTEXITCODE
