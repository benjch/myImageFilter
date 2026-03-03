rem @echo off
cd /d "%~dp0"
git fetch origin --prune
for /f "delims=" %%B in ('git rev-parse --abbrev-ref HEAD') do git reset --hard origin/%%B
git clean -fd
git submodule update --init --recursive
pause