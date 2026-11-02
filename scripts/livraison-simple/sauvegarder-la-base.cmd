@echo off
rem ====================================================================================================================
rem  Sauvegarde la base de GesStock ({{NOM_BASE}}) dans le sous-dossier "sauvegardes", dans un fichier date.
rem  A faire avant chaque mise a jour de GesStock, et regulierement (une fois par semaine au moins).
rem  GesStock peut rester allume pendant la sauvegarde. WampServer doit etre demarre (icone verte).
rem ====================================================================================================================
setlocal
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

rem --- Le compte root de WampServer n'a pas de mot de passe ; sinon on le demande ---
"%DUMP_EXE%" -u root --no-data --databases mysql --tables db >nul 2>&1
if not errorlevel 1 goto sauvegarder
echo.
echo  MySQL ne repond pas avec le compte root sans mot de passe.
echo  Si WampServer vient de demarrer, attendez que son icone soit verte, puis relancez ce fichier.
echo  Si root a un mot de passe, saisissez-le ici ; sinon fermez cette fenetre.
set /p "MYSQL_PWD=Mot de passe root de MySQL : "

:sauvegarder
echo.
echo  Sauvegarde de la base {{NOM_BASE}} en cours...
"%DUMP_EXE%" -u root --single-transaction --routines --default-character-set=utf8mb4 --databases {{NOM_BASE}} --result-file="%FICHIER%"
if errorlevel 1 goto echec
rem mysqldump termine son fichier par "Dump completed" : sans cette ligne, la sauvegarde est incomplete.
findstr /c:"Dump completed" "%FICHIER%" >nul 2>&1
if errorlevel 1 goto echec

echo.
echo  Sauvegarde terminee :
echo    %FICHIER%
echo.
echo  Pour plus de surete, copiez aussi ce fichier sur une cle USB.
echo.
pause
exit /b 0

:outil_absent
echo.
echo  mysqldump.exe est introuvable. WampServer est-il installe, et son icone est-elle verte ?
pause
exit /b 1

:echec
echo.
echo  La sauvegarde a ECHOUE : ne mettez pas GesStock a jour.
echo  Verifiez que WampServer est demarre (icone verte), puis relancez ce fichier.
echo  Si cela recommence, envoyez une photo de cet ecran a votre prestataire.
if exist "%FICHIER%" del "%FICHIER%" >nul 2>&1
pause
exit /b 1
