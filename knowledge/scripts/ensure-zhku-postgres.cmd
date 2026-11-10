@echo off
setlocal
rem Use built-in Windows PowerShell; no dependency on an agent-injected PATH.
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0ensure-zhku-postgres.ps1" %*
exit /b %ERRORLEVEL%
