@echo off
setlocal
rem ASCII only: cmd.exe parses this file with the OEM/GBK code page, so Chinese
rem comments here would be split mid-byte and executed as commands.
rem Resolve Windows PowerShell 5.1 explicitly; the user's terminal PATH differs from the agent PATH.
set "ZHKU_PERSISTENT_PS="
for %%I in (powershell.exe) do set "ZHKU_PERSISTENT_PS=%%~$PATH:I"
if not defined ZHKU_PERSISTENT_PS if exist "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" set "ZHKU_PERSISTENT_PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not defined ZHKU_PERSISTENT_PS (
    echo Windows PowerShell 5.1 was not found. Install it or run the .ps1 with an available PowerShell.
    exit /b 1
)
rem Run under UTF-8 for Chinese logs, then restore the previous code page.
for /f "tokens=2 delims=:" %%C in ('chcp') do set "ZHKU_PERSISTENT_OLD_CP=%%C"
chcp 65001 >nul
"%ZHKU_PERSISTENT_PS%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-persistent-rule-check.ps1" %*
set "ZHKU_PERSISTENT_EXIT=%ERRORLEVEL%"
if defined ZHKU_PERSISTENT_OLD_CP chcp %ZHKU_PERSISTENT_OLD_CP% >nul
exit /b %ZHKU_PERSISTENT_EXIT%
