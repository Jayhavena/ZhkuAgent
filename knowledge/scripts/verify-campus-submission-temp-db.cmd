@echo off
setlocal
rem Resolve the actual executable: Codex PATH is not the user's terminal PATH.
set "ZHKU_DEMO_PWSH="
for %%I in (pwsh.exe) do set "ZHKU_DEMO_PWSH=%%~$PATH:I"
if not defined ZHKU_DEMO_PWSH if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "ZHKU_DEMO_PWSH=%ProgramFiles%\PowerShell\7\pwsh.exe"
if not defined ZHKU_DEMO_PWSH if exist "%ProgramFiles(x86)%\PowerShell\7\pwsh.exe" set "ZHKU_DEMO_PWSH=%ProgramFiles(x86)%\PowerShell\7\pwsh.exe"
if not defined ZHKU_DEMO_PWSH if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" set "ZHKU_DEMO_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
if not defined ZHKU_DEMO_PWSH (
    echo PowerShell 7 executable was not found. Check the local installation or Codex runtime.
    exit /b 1
)
rem Match the Java UTF-8 console for this run; restore the previous code page.
for /f "tokens=2 delims=:" %%C in ('chcp') do set "ZHKU_DEMO_OLD_CP=%%C"
chcp 65001 >nul
"%ZHKU_DEMO_PWSH%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0verify-campus-submission-temp-db.ps1" %*
set "ZHKU_DEMO_EXIT=%ERRORLEVEL%"
if defined ZHKU_DEMO_OLD_CP chcp %ZHKU_DEMO_OLD_CP% >nul
exit /b %ZHKU_DEMO_EXIT%
