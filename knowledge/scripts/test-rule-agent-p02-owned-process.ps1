#requires -Version 7.0
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$pwshPath=Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/native/powershell/pwsh.exe'
if(!(Test-Path -LiteralPath $pwshPath)){throw '测试需要实际PowerShell7文件'}
$accepted=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','exit','0') -TimeoutSeconds 235
if($accepted.exitCode-ne 0-or $accepted.timedOut-or !$accepted.resourcesClosed){throw 'P02总进程预算未接受'}
# 缩短后的挂起反例验证真实kill/回收；不等待235秒，不触碰用户进程。
$hung=Invoke-ZhkuOwnedProcess -Executable $pwshPath -Arguments @('-NoLogo','-NoProfile','-Command','Start-Sleep -Seconds 15') -TimeoutSeconds 1
if(!$hung.timedOut-or !$hung.resourcesClosed){throw '挂起自有进程未按时回收'}
$rejected=$false
try{ $null=Invoke-ZhkuOwnedProcess -Executable $env:ComSpec -Arguments @('/d','/c','exit','0') -TimeoutSeconds 251 }
catch{if($_.Exception.GetType().FullName-ne 'System.Management.Automation.ParameterBindingValidationException'){throw};$rejected=$true}
if(!$rejected){throw '超出250秒未拒绝'}
@{acceptedP02ProcessSeconds=235;timedOutChildReaped=$hung.resourcesClosed;timeoutObserved=$hung.timedOut;rejectedAbove250=$rejected;realProviderCalls=0;databaseOperations=0}|ConvertTo-Json
