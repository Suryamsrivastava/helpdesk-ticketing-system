@echo off
rem Starts the helpdesk (UI + API) with the settings in deploy\helpdesk.env.
rem Keeps running in this window; output goes to deploy\logs\helpdesk.log.
setlocal
set "HERE=%~dp0"
set "ENV_FILE=%HERE%helpdesk.env"
set "JAR=%HERE%..\Backend_BE\target\helpdesk.jar"

if not exist "%ENV_FILE%" (
  echo Missing deploy\helpdesk.env - copy helpdesk.env.example to helpdesk.env and fill it in.
  exit /b 1
)
if not exist "%JAR%" (
  echo Missing Backend_BE\target\helpdesk.jar - run deploy\build.bat first.
  exit /b 1
)

rem load KEY=value lines, skipping comments and blank lines
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do set "%%A=%%B"

set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java.exe"
if not exist "%HERE%logs" mkdir "%HERE%logs"

echo Starting helpdesk on http://localhost:%SERVER_PORT%  (log: deploy\logs\helpdesk.log)
"%JAVA%" -jar "%JAR%" >> "%HERE%logs\helpdesk.log" 2>&1
