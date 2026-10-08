@echo off
setlocal
rem Resolve PowerShell 7 without depending on Codex's injected PATH.
set "ZHKU_VERIFY_PWSH="
for %%I in (pwsh.exe) do set "ZHKU_VERIFY_PWSH=%%~$PATH:I"
if not defined ZHKU_VERIFY_PWSH if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "ZHKU_VERIFY_PWSH=%ProgramFiles%\PowerShell\7\pwsh.exe"
if not defined ZHKU_VERIFY_PWSH if exist "%ProgramFiles(x86)%\PowerShell\7\pwsh.exe" set "ZHKU_VERIFY_PWSH=%ProgramFiles(x86)%\PowerShell\7\pwsh.exe"
if not defined ZHKU_VERIFY_PWSH if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" set "ZHKU_VERIFY_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
if not defined ZHKU_VERIFY_PWSH (
    echo PowerShell 7 executable was not found.
    exit /b 1
)
for /f "tokens=2 delims=:" %%C in ('chcp') do set "ZHKU_VERIFY_OLD_CP=%%C"
chcp 65001 >nul
"%ZHKU_VERIFY_PWSH%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0verify-rule-check-refactor.ps1" %*
set "ZHKU_VERIFY_EXIT=%ERRORLEVEL%"
if defined ZHKU_VERIFY_OLD_CP chcp %ZHKU_VERIFY_OLD_CP% >nul
exit /b %ZHKU_VERIFY_EXIT%
