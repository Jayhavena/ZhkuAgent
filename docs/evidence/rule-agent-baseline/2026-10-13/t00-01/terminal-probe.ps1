# 从注册的用户/系统PATH重建子进程环境，避免用Codex注入PATH冒充用户终端。
$ErrorActionPreference='Stop'
$utf8=New-Object Text.UTF8Encoding($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
$machinePath=[Environment]::GetEnvironmentVariable('Path','Machine')
$userPath=[Environment]::GetEnvironmentVariable('Path','User')
if([string]::IsNullOrWhiteSpace($machinePath)){throw '无法取得用户终端系统PATH，不能宣称已验证普通终端'}
$env:Path=[Environment]::ExpandEnvironmentVariables($machinePath+';'+$userPath)
$record=[ordered]@{shellVersion=$PSVersionTable.PSVersion.ToString();pathSource='WINDOWS_MACHINE_PLUS_USER';encoding=[Console]::OutputEncoding.CodePage;javaAvailable=($null-ne(Get-Command java.exe -ErrorAction SilentlyContinue));nodeAvailable=($null-ne(Get-Command node.exe -ErrorAction SilentlyContinue));npmAvailable=($null-ne(Get-Command npm.cmd -ErrorAction SilentlyContinue));dockerAvailable=($null-ne(Get-Command docker.exe -ErrorAction SilentlyContinue));pwshOnUserPath=($null-ne(Get-Command pwsh.exe -ErrorAction SilentlyContinue));encodingSample='中文验收：工程基线、仅回放'}
$record|ConvertTo-Json -Compress
Push-Location $root
try{& '.\knowledge\scripts\start-controlled-rule-check.cmd' -TaskPlanReplay -CheckEnvironment;$result=$LASTEXITCODE}finally{Pop-Location}
exit $result
