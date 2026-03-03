@echo off
cd /d %~dp0
git ls-remote --heads origin >nul
git fetch origin --prune
git branch -r
pause