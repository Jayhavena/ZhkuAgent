@echo off
setlocal
rem Resolve the actual executable: Codex PATH is not the user's terminal PATH.
set "ZHKU_NGINX_PWSH="
for %%I in (pwsh.exe) do set "ZHKU_NGINX_PWSH=%%~$PATH:I"
if not defined ZHKU_NGINX_PWSH if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "ZHKU_NGINX_PWSH=%ProgramFiles%\PowerShell\7\pwsh.exe"
if not defined ZHKU_NGINX_PWSH if exist "%ProgramFiles(x86)%\PowerShell\7\pwsh.exe" set "ZHKU_NGINX_PWSH=%ProgramFiles(x86)%\PowerShell\7\pwsh.exe"
if not defined ZHKU_NGINX_PWSH if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" set "ZHKU_NGINX_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
if not defined ZHKU_NGINX_PWSH (
    echo PowerShell 7 executable was not found. Check the local installation or Codex runtime.
    exit /b 1
)
rem Match the Java UTF-8 console for this run; restore the previous code page.
for /f "tokens=2 delims=:" %%C in ('chcp') do set "ZHKU_NGINX_OLD_CP=%%C"
chcp 65001 >nul
"%ZHKU_NGINX_PWSH%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-nginx-preview.ps1" %*
set "ZHKU_NGINX_EXIT=%ERRORLEVEL%"
if defined ZHKU_NGINX_OLD_CP chcp %ZHKU_NGINX_OLD_CP% >nul
exit /b %ZHKU_NGINX_EXIT%
