@echo off
setlocal
rem One-command student replay demo. Credentials stay in the backend window or
rem process environment (ZHKU_RULECHECK_STUDENT_PASSWORD); never as arguments here.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-student-replay-demo.ps1" %*
exit /b %ERRORLEVEL%
