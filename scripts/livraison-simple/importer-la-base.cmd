@echo off
rem ====================================================================================================================
rem  Importe base-de-lancement.sql dans MySQL (WampServer) : cree la base, ses tables, le compte MySQL de GesStock et
rem  l'administrateur. A faire UNE SEULE FOIS, sur une base neuve (WampServer demarre, icone verte).
rem ====================================================================================================================
setlocal
title Importer la base de GesStock
cd /d "%~dp0"

if not exist "%~dp0base-de-lancement.sql" (
  echo base-de-lancement.sql est introuvable dans ce dossier.
  pause
  exit /b 1
)

rem --- Trouver mysql.exe : celui du PATH, sinon WampServer, MySQL ou XAMPP ---
set "MYSQL_EXE="
for /f "delims=" %%i in ('where mysql 2^>nul') do if not defined MYSQL_EXE set "MYSQL_EXE=%%i"
if not defined MYSQL_EXE for %%l in (C D E F) do for /d %%d in ("%%l:\wamp64\bin\mysql\mysql*" "%%l:\wamp\bin\mysql\mysql*") do if exist "%%d\bin\mysql.exe" set "MYSQL_EXE=%%d\bin\mysql.exe"
if not defined MYSQL_EXE for /d %%d in ("%ProgramFiles%\MySQL\MySQL Server *") do if exist "%%d\bin\mysql.exe" set "MYSQL_EXE=%%d\bin\mysql.exe"
if not defined MYSQL_EXE if exist "C:\xampp\mysql\bin\mysql.exe" set "MYSQL_EXE=C:\xampp\mysql\bin\mysql.exe"
if not defined MYSQL_EXE goto mysql_absent

rem --- MySQL repond-il ? Le compte root de WampServer n'a pas de mot de passe ; sinon on le demande ---
"%MYSQL_EXE%" -u root -e "SELECT 1" >nul 2>&1
if not errorlevel 1 goto mysql_ok
echo.
echo  MySQL ne repond pas avec le compte root sans mot de passe.
echo  Si WampServer vient de demarrer, attendez que son icone soit verte, puis relancez ce fichier.
echo  Si root a un mot de passe, saisissez-le ici ; sinon fermez cette fenetre.
set /p "MYSQL_PWD=Mot de passe root de MySQL : "
"%MYSQL_EXE%" -u root -e "SELECT 1" >nul 2>&1
if errorlevel 1 goto mysql_refuse
:mysql_ok

rem --- Protection : ne jamais effacer une base qui contient deja des donnees sans le demander ---
set "NB=0"
"%MYSQL_EXE%" -u root -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{{NOM_BASE}}'" > "%TEMP%\gesstock-nb.txt" 2>nul
set /p NB=<"%TEMP%\gesstock-nb.txt"
del "%TEMP%\gesstock-nb.txt" >nul 2>&1
if "%NB%"=="0" goto importer
echo.
echo  ATTENTION : la base "{{NOM_BASE}}" existe deja et contient %NB% tables.
echo  L'importer EFFACERAIT toutes les donnees actuelles : articles, ventes, clients, caisse.
echo  Si ce n'est PAS ce que vous voulez, fermez cette fenetre : rien n'aura ete modifie.
set "REPONSE="
set /p "REPONSE=Pour tout effacer et repartir de zero, tapez EFFACER puis Entree : "
if /i not "%REPONSE%"=="EFFACER" goto annule

:importer
echo.
echo  Importation en cours...
"%MYSQL_EXE%" -u root --default-character-set=utf8mb4 < "%~dp0base-de-lancement.sql"
if errorlevel 1 goto echec

"%MYSQL_EXE%" -u root -N -B -e "SELECT COUNT(*) FROM {{NOM_BASE}}.utilisateurs WHERE username='{{ADMIN}}'" > "%TEMP%\gesstock-admin.txt" 2>nul
set "ADMIN_OK=0"
set /p ADMIN_OK=<"%TEMP%\gesstock-admin.txt"
del "%TEMP%\gesstock-admin.txt" >nul 2>&1
if not "%ADMIN_OK%"=="1" goto echec

echo.
echo  Base importee. Vous pouvez maintenant double-cliquer sur demarrer.cmd.
echo.
pause
exit /b 0

:mysql_absent
echo.
echo  mysql.exe est introuvable. WampServer est-il installe, et son icone est-elle verte ?
pause
exit /b 1

:mysql_refuse
echo.
echo  Impossible de se connecter a MySQL. Rien n'a ete modifie.
pause
exit /b 1

:annule
echo.
echo  Annule : rien n'a ete modifie.
pause
exit /b 1

:echec
echo.
echo  L'importation a echoue. Envoyez une photo de cet ecran a votre prestataire.
pause
exit /b 1
