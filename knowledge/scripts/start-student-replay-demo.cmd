@echo off
setlocal
rem 一条命令的学生回放演示入口；凭据仍只在后端窗口或进程环境中提供，绝不进入本命令参数。
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-student-replay-demo.ps1" %*
exit /b %ERRORLEVEL%
