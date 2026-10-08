@echo off
setlocal
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -File "%~dp0start-nginx-preview.ps1" %*
exit /b %errorlevel%
