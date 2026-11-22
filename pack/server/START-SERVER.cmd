@echo off
rem Random Item Skyblock server. Double-click to start. Needs nothing else installed.
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
pause
