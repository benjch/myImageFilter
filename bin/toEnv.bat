@echo off
REM -- Se placer dans le dossier du script
cd /d "%~dp0"

REM -- En-tête + vérif de l'argument ENV
echo ==========================================
echo               ENV : %1
echo ==========================================
if "%~1"=="" exit /b 1

@echo on
REM -- Copie récursive cfg_by_env\<ENV>\ -> resources\
xcopy "src\main\build\cfg_by_env\%~1\*" "src\main\resources\" /E /I /Y >nul
@echo off

REM -- Message de fin selon le résultat
set "rc=%ERRORLEVEL%"
if "%rc%"=="0" (
  echo [OK] Copie terminee.
) else if "%rc%"=="1" (
  echo [INFO] Aucun fichier trouve a copier.
) else if "%rc%"=="2" (
  echo [ANNULE] Operation interrompue par l'utilisateur.
) else if "%rc%"=="4" (
  echo [ERREUR] Erreur d'initialisation XCOPY.
) else if "%rc%"=="5" (
  echo [ERREUR] Erreur d'ecriture disque.
) else (
  echo [ERREUR] XCOPY a retourne le code %rc%.
)

echo La fenetre va se fermer dans 5 secondes...
timeout /t 5 >nul
