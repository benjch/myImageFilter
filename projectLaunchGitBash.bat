@echo off
setlocal
set "HERE=%~dp0"
set "BASH=%ProgramFiles%\Git\bin\bash.exe"

if not exist "%BASH%" set "BASH=%ProgramFiles(x86)%\Git\bin\bash.exe"
if not exist "%BASH%" set "BASH=%LocalAppData%\Programs\Git\bin\bash.exe"

if not exist "%BASH%" (
  echo bash.exe introuvable.
  pause
  exit /b 1
)

start "" "%BASH%" --login -i -c "cd \"$(cygpath -u '%HERE%')\"; exec bash -i"