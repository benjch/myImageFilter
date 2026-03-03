%~d0
cd %~dp0

set mydate=%date:~6,4%%date:~3,2%%date:~0,2%-%time:~0,2%%time:~3,2%%time:~6,2%
D:\benjch\program\7-Zip\7z.exe -mx0 a D:\benjch\gitBenjch\%mydate%_${ProjectNameLowerCamelCase}.7z .
REM pause
ECHO D:\benjch\gitBenjch\%mydate%_${ProjectNameLowerCamelCase}.7z
timeout 5 > NUL