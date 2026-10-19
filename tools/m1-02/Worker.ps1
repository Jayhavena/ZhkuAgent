#requires -Version 7.0
param([Parameter(Mandatory)][string]$Request,[Parameter(Mandatory)][string]$Gate)
$ErrorActionPreference='Stop'
[Console]::InputEncoding=[Console]::OutputEncoding=$OutputEncoding=[Text.UTF8Encoding]::new($false)
# 父进程只有确认 Job 所有权成功才创建 gate。父进程提前退出时绝不运行任何目标命令。
$deadline=[DateTime]::UtcNow.AddSeconds(15)
while(!(Test-Path -LiteralPath $Gate)) {if([DateTime]::UtcNow -gt $deadline){exit 125};Start-Sleep -Milliseconds 20}
try {
    $r=Get-Content -LiteralPath $Request -Raw|ConvertFrom-Json
    Set-Location -LiteralPath $r.cwd
    & $r.executable @($r.arguments) 2>&1 | ForEach-Object { [Console]::WriteLine([string]$_) }
    if($null -eq $LASTEXITCODE){exit 0};exit $LASTEXITCODE
} catch {[Console]::Error.WriteLine($_.ToString());exit 1}
