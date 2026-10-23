@echo off
rem Run once as Administrator: starts the helpdesk automatically 1 minute after
rem every Windows boot (the delay lets MySQL start first), even with nobody logged in.
setlocal
set "START=%~dp0start-helpdesk.bat"

schtasks /create /tn "Helpdesk" /tr "\"%START%\"" /sc onstart /delay 0001:00 /ru SYSTEM /rl HIGHEST /f
if errorlevel 1 (
  echo Could not create the task. Right-click this file and choose "Run as administrator".
  exit /b 1
)

rem schtasks stops a task after 72 hours by default; the helpdesk must run forever.
rem Also restart it if it crashes, and never stop it on battery power.
powershell -NoProfile -Command "$s = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries; Set-ScheduledTask -TaskName 'Helpdesk' -Settings $s | Out-Null"
if errorlevel 1 (
  echo Could not remove the 72-hour run limit. Run this file as administrator again.
  exit /b 1
)
echo Auto-start registered (Task Scheduler task "Helpdesk").
echo Start it now without rebooting:  schtasks /run /tn "Helpdesk"
echo Remove it later:                 schtasks /delete /tn "Helpdesk" /f
