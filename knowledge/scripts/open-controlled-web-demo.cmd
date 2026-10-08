@echo off
setlocal
rem A separate persistent window keeps preflight failures visible, even from Explorer/ISE.
rem The child runs the existing launcher; credentials are still entered there, never here.
start "ZhkuAgent campus web demo" /wait "%ComSpec%" /d /k ""%~dp0start-controlled-web-demo.cmd" %*"
exit /b %ERRORLEVEL%
