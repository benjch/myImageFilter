@cls
@echo off
%~d0
cd %~dp0
@call mvn -f pom.xml clean install -Dmaven.test.skip=false
pause rem timeout 5 > NUL

