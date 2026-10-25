#requires -Version 5.1
# 一条命令的学生规则查证回放演示：受控回放后端（独立窗口）+ 回放前端 + 浏览器。
# 真实模型链路仍走 start-rule-agent-http-check 的审包流程；本入口只用于零模型成本的日常演示。
param(
    [ValidateRange(1024,65535)][int]$BackendPort=58126,
    [ValidateRange(1024,65535)][int]$WebPort=58080,
    [ValidateRange(1,15)][int]$MaxMinutes=15
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backend=Join-Path $root 'knowledge\scripts\start-controlled-rule-check.cmd'
$occupied=Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue
if($occupied){throw "端口 $BackendPort 已被占用；先正常退出占用者，或用 -BackendPort 换端口"}
if($env:ZHKU_RULECHECK_STUDENT_PASSWORD){
    Write-Host '检测到 ZHKU_RULECHECK_STUDENT_PASSWORD：后端将直接使用该环境密码，不再提示输入。'
}else{
    Write-Host '后端将在新窗口启动，并在该窗口内要求你设置本次学生账号密码（16～64位，请记住）。'
}
Start-Process -FilePath $env:ComSpec -ArgumentList @('/d','/k',"`"$backend`" -BackendPort $BackendPort -DatabasePort 55500 -MaxMinutes $MaxMinutes") -WorkingDirectory $root
$deadline=(Get-Date).AddMinutes(4);$ready=$false
while((Get-Date) -lt $deadline){
    if(Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue){$ready=$true;break}
    Start-Sleep -Milliseconds 500
}
if(!$ready){throw "后端4分钟内未在端口 $BackendPort 监听；请查看后端窗口中的错误，处理后重试"}
& (Join-Path $root 'agent\frontend\start-nginx-preview.cmd') -BackendPort $BackendPort -WebPort $WebPort -EntryMode replay
Start-Process "http://127.0.0.1:$WebPort/rules"
Write-Host ''
Write-Host "页面地址：http://127.0.0.1:$WebPort/rules  登录账号：controlled-2025-student"
if($env:ZHKU_RULECHECK_STUDENT_PASSWORD){Write-Host '密码：你通过 ZHKU_RULECHECK_STUDENT_PASSWORD 提供的值'}else{Write-Host '密码：你在后端窗口设置的值'}
Write-Host "页面会标注“离线回放演示”；提问使用页面预置问题，答案来自冻结回放，不代表在线能力。"
Write-Host "结束：后端窗口按提示退出（最长 $MaxMinutes 分钟自动结束）；前端执行 agent\frontend\start-nginx-preview.cmd -Action Stop"
