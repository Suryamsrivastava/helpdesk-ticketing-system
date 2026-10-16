@echo off
rem Stops whatever is listening on the helpdesk port (SERVER_PORT in helpdesk.env, default 8080).
setlocal
set "SERVER_PORT=8080"
if exist "%~dp0helpdesk.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%~dp0helpdesk.env") do if /i "%%A"=="SERVER_PORT" set "SERVER_PORT=%%B"
)
set "FOUND="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%SERVER_PORT% .*LISTENING"') do (
  taskkill /pid %%P /f >nul && set "FOUND=1"
)
if defined FOUND (echo Helpdesk stopped.) else (echo Nothing is running on port %SERVER_PORT%.)
