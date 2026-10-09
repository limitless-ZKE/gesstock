@echo off
rem ====================================================================================================================
rem  Sauvegarde la base de donnees de GesStock dans un fichier .zip du dossier "sauvegardes", garde les derniers jours,
rem  et en copie un exemplaire dans DOSSIER_COPIE_SAUVEGARDE s'il est renseigne (configuration.cmd).
rem  Lance chaque soir par la tache planifiee ; peut aussi se lancer a la main (double-clic) a tout moment.
rem  Code de retour 0 = sauvegarde reussie et verifiee ; 1 = echec (voir journaux\sauvegarde.log).
rem ====================================================================================================================
setlocal
cd /d "%~dp0"
call "%~dp0configuration.cmd"

if not exist "%~dp0journaux" mkdir "%~dp0journaux"
if not exist "%~dp0sauvegardes" mkdir "%~dp0sauvegardes"
set JOURNAL=%~dp0journaux\sauvegarde.log

rem --- Trouver mysqldump ---
set DUMP=%MYSQLDUMP_EXE%
if not defined DUMP for /f "delims=" %%i in ('where mysqldump 2^>nul') do if not defined DUMP set DUMP=%%i
if not defined DUMP for /d %%d in ("C:\Program Files\MySQL\MySQL Server *") do if exist "%%d\bin\mysqldump.exe" set DUMP=%%d\bin\mysqldump.exe
if not defined DUMP for /d %%d in ("C:\wamp64\bin\mysql\mysql*") do if exist "%%d\bin\mysqldump.exe" set DUMP=%%d\bin\mysqldump.exe
if not defined DUMP for /d %%d in ("C:\xampp\mysql") do if exist "%%d\bin\mysqldump.exe" set DUMP=%%d\bin\mysqldump.exe
if not defined DUMP (
  call :echec "mysqldump.exe introuvable : renseignez MYSQLDUMP_EXE dans configuration.cmd"
  exit /b 1
)

rem --- Horodatage AAAA-MM-JJ_HHMM (independant de la langue de Windows) ---
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmm"') do set HORODATAGE=%%i
set SQL=%~dp0sauvegardes\%MYSQL_BASE%-%HORODATAGE%.sql
set ZIP=%~dp0sauvegardes\%MYSQL_BASE%-%HORODATAGE%.zip

rem --- Identifiants dans un fichier temporaire (jamais sur la ligne de commande, visible de tous) ---
set OPTIONS=%TEMP%\gesstock-sauvegarde-%RANDOM%.cnf
(
  echo [client]
  echo host=%MYSQL_HOTE%
  echo port=%MYSQL_PORT%
  echo user=%MYSQL_UTILISATEUR%
  echo password="%MYSQL_MOT_DE_PASSE%"
) > "%OPTIONS%"

"%DUMP%" --defaults-extra-file="%OPTIONS%" --single-transaction --no-tablespaces --default-character-set=utf8mb4 --databases %MYSQL_BASE% --result-file="%SQL%" 2>>"%JOURNAL%"
set CODE=%errorlevel%
del "%OPTIONS%" >nul 2>&1
if not "%CODE%"=="0" (
  del "%SQL%" >nul 2>&1
  call :echec "mysqldump a echoue (code %CODE%) : verifiez MySQL et le mot de passe dans configuration.cmd"
  exit /b 1
)

rem --- Verifier que l'export est complet (mysqldump ecrit cette ligne en tout dernier) ---
findstr /c:"-- Dump completed" "%SQL%" >nul
if errorlevel 1 (
  del "%SQL%" >nul 2>&1
  call :echec "export incomplet : la sauvegarde est ecartee"
  exit /b 1
)

rem --- Compresser (le fichier .sql est ensuite supprime) ---
"%SystemRoot%\System32\tar.exe" -a -c -f "%ZIP%" -C "%~dp0sauvegardes" "%MYSQL_BASE%-%HORODATAGE%.sql"
if errorlevel 1 (
  call :echec "compression impossible"
  exit /b 1
)
del "%SQL%" >nul 2>&1

rem --- Supprimer les sauvegardes plus anciennes que JOURS_CONSERVES ---
forfiles /P "%~dp0sauvegardes" /M "%MYSQL_BASE%-*.zip" /D -%JOURS_CONSERVES% /C "cmd /c del @path" >nul 2>&1

rem --- Copie sur un autre disque (pas de bloc entre parentheses : un chemin comme "Program Files (x86)" les casserait) ---
set COPIE_OK=
if not defined DOSSIER_COPIE_SAUVEGARDE goto fin
if not exist "%DOSSIER_COPIE_SAUVEGARDE%" mkdir "%DOSSIER_COPIE_SAUVEGARDE%" >nul 2>&1
copy /y "%ZIP%" "%DOSSIER_COPIE_SAUVEGARDE%\" >nul 2>&1
if errorlevel 1 goto copie_impossible
forfiles /P "%DOSSIER_COPIE_SAUVEGARDE%" /M "%MYSQL_BASE%-*.zip" /D -%JOURS_CONSERVES% /C "cmd /c del @path" >nul 2>&1
set COPIE_OK=oui
goto fin

:copie_impossible
call :echec "copie vers le second emplacement impossible (disque absent ?) : la sauvegarde reste uniquement sur ce PC"
exit /b 1

:fin
echo [%date% %time%] Sauvegarde reussie : %MYSQL_BASE%-%HORODATAGE%.zip>> "%JOURNAL%"
echo Sauvegarde reussie : sauvegardes\%MYSQL_BASE%-%HORODATAGE%.zip
if not defined DOSSIER_COPIE_SAUVEGARDE echo ATTENTION : aucun second emplacement (DOSSIER_COPIE_SAUVEGARDE) n'est configure.
exit /b 0

:echec
echo [%date% %time%] ECHEC : %~1 >> "%JOURNAL%"
echo ECHEC de la sauvegarde : %~1
exit /b 1
