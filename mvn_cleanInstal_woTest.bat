@cls
@echo off
%~d0
cd %~dp0
@call mvn -f pom.xml clean install -Dmaven.test.skip=true
pause rem timeout 5 > NUL

