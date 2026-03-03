@echo off
cd /d %~dp0
setlocal enabledelayedexpansion
where git >nul 2>&1 || (echo Git introuvable.& exit /b 1)
git rev-parse --is-inside-work-tree >nul 2>&1 || (echo Pas dans un depot Git.& exit /b 1)
for /f "usebackq delims=" %%a in (`powershell -NoProfile -Command "(Get-Date).ToString('yyyy-MM-dd_HH-mm-ss')"`) do set dt=%%a
set msg=int !dt!
git add -A
git diff --cached --quiet && git diff --quiet && (echo Rien a commiter.& exit /b 0)
git commit -m "!msg!"
if errorlevel 1 exit /b %errorlevel%
git push
pause