@echo off
rem ====================================================================================================================
rem  Sauvegarde la base de GesStock ({{NOM_BASE}}) dans le sous-dossier "sauvegardes", dans un fichier date.
rem  - A la main (double-clic) : a faire avant chaque mise a jour de GesStock.
rem  - Automatiquement : la tache planifiee "GesStock - Sauvegarde" (installee par installer-demarrage-automatique.cmd) la
rem    lance chaque jour avec le mot "silencieux" : sans fenetre, sans question, en attendant que MySQL soit pret.
rem  Les 30 sauvegardes les plus recentes sont gardees. Chacune peut aussi etre copiee vers une cle USB ou un autre disque :
rem  voir choisir-la-copie-des-sauvegardes.cmd. Le resultat de chaque sauvegarde est note dans sauvegardes\journal-sauvegardes.txt.
rem  GesStock peut rester allume pendant la sauvegarde. WampServer doit etre demarre (icone verte).
rem ====================================================================================================================
setlocal
set "SILENCIEUX="
if /i "%~1"=="silencieux" set "SILENCIEUX=1"
title Sauvegarder la base de GesStock
cd /d "%~dp0"

rem --- Trouver mysqldump.exe : celui du PATH, sinon WampServer, MySQL ou XAMPP ---
set "DUMP_EXE="
for /f "delims=" %%i in ('where mysqldump 2^>nul') do if not defined DUMP_EXE set "DUMP_EXE=%%i"
if not defined DUMP_EXE for %%l in (C D E F) do for /d %%d in ("%%l:\wamp64\bin\mysql\mysql*" "%%l:\wamp\bin\mysql\mysql*") do if exist "%%d\bin\mysqldump.exe" set "DUMP_EXE=%%d\bin\mysqldump.exe"
if not defined DUMP_EXE for /d %%d in ("%ProgramFiles%\MySQL\MySQL Server *") do if exist "%%d\bin\mysqldump.exe" set "DUMP_EXE=%%d\bin\mysqldump.exe"
if not defined DUMP_EXE if exist "C:\xampp\mysql\bin\mysqldump.exe" set "DUMP_EXE=C:\xampp\mysql\bin\mysqldump.exe"
if not defined DUMP_EXE goto outil_absent

rem --- Nom du fichier : base-AAAA-MM-JJ_HH-MM.sql (date lue par PowerShell : independante des reglages regionaux) ---
set "HORODATAGE="
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH-mm"') do set "HORODATAGE=%%t"
if not defined HORODATAGE set "HORODATAGE=sans-date"
if not exist "%~dp0sauvegardes" mkdir "%~dp0sauvegardes"
set "FICHIER=%~dp0sauvegardes\{{NOM_BASE}}-%HORODATAGE%.sql"

rem --- Le compte root de WampServer n'a pas de mot de passe ; sinon on le demande (jamais en mode silencieux) ---
"%DUMP_EXE%" -u root --no-data --databases mysql --tables db >nul 2>&1
if not errorlevel 1 goto sauvegarder
if defined SILENCIEUX goto attendre_mysql
echo.
echo  MySQL ne repond pas avec le compte root sans mot de passe.
echo  Si WampServer vient de demarrer, attendez que son icone soit verte, puis relancez ce fichier.
echo  Si root a un mot de passe, saisissez-le ici ; sinon fermez cette fenetre.
set /p "MYSQL_PWD=Mot de passe root de MySQL : "
goto sauvegarder

:attendre_mysql
rem Au demarrage de Windows, MySQL peut mettre plusieurs minutes a etre pret : 20 essais, 15 secondes d'ecart.
set "ESSAIS=0"
:boucle_mysql
set /a ESSAIS+=1
if %ESSAIS% gtr 20 goto echec
ping -n 16 127.0.0.1 >nul
"%DUMP_EXE%" -u root --no-data --databases mysql --tables db >nul 2>&1
if errorlevel 1 goto boucle_mysql

:sauvegarder
echo.
echo  Sauvegarde de la base {{NOM_BASE}} en cours...
"%DUMP_EXE%" -u root --single-transaction --routines --default-character-set=utf8mb4 --databases {{NOM_BASE}} --result-file="%FICHIER%"
if errorlevel 1 goto echec
rem mysqldump termine son fichier par "Dump completed" : sans cette ligne, la sauvegarde est incomplete.
findstr /c:"Dump completed" "%FICHIER%" >nul 2>&1
if errorlevel 1 goto echec

rem Garder les 30 plus recentes, copier vers la cle USB choisie, noter le resultat (une erreur ici ne rend pas la sauvegarde invalide).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0outils\sauvegarde-suite.ps1" -Fichier "%FICHIER%" -Prefixe "{{NOM_BASE}}-" -Garder 30
if defined SILENCIEUX exit /b 0

echo.
echo  Sauvegarde terminee :
echo    %FICHIER%
echo.
echo  Pour plus de surete, faites copier chaque sauvegarde sur une cle USB : choisir-la-copie-des-sauvegardes.cmd
echo.
pause
exit /b 0

:outil_absent
echo.
echo  mysqldump.exe est introuvable. WampServer est-il installe, et son icone est-elle verte ?
if defined SILENCIEUX exit /b 1
pause
exit /b 1

:echec
echo.
echo  La sauvegarde a ECHOUE : ne mettez pas GesStock a jour.
echo  Verifiez que WampServer est demarre (icone verte), puis relancez ce fichier.
echo  Si cela recommence, envoyez une photo de cet ecran a votre prestataire.
if exist "%FICHIER%" del "%FICHIER%" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0outils\sauvegarde-suite.ps1" -Echec
if defined SILENCIEUX exit /b 1
pause
exit /b 1
