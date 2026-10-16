@echo off
rem Builds the React UI and packages it inside the backend jar:
rem   Backend_BE\target\helpdesk.jar  (UI + API on one port)
setlocal
set "ROOT=%~dp0.."

echo [1/2] Building frontend...
pushd "%ROOT%\Frontend_FE" || exit /b 1
call npm ci || goto :fail
call npm run build || goto :fail
popd

echo [2/2] Building backend jar with the UI bundled (also runs the tests)...
pushd "%ROOT%\Backend_BE" || exit /b 1
call "%ROOT%\Backend_BE\mvnw.cmd" -q -Pbundle-frontend clean package || goto :fail
popd

echo.
echo Build OK: Backend_BE\target\helpdesk.jar
echo Next: deploy\start-helpdesk.bat
exit /b 0

:fail
popd
echo.
echo BUILD FAILED - see the messages above.
exit /b 1
