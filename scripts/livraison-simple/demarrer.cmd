@echo off
rem ====================================================================================================================
rem  Lance GesStock. Gardez cette fenetre ouverte : la fermer arrete GesStock.
rem  Reglages possibles : les lignes "set" entre les deux traits ci-dessous (ouvrir ce fichier avec le Bloc-notes).
rem ====================================================================================================================
setlocal
title GesStock
cd /d "%~dp0"

rem ------------------------------------------------ reglages a adapter au client
set PORT={{PORT}}
set FUSEAU_HORAIRE=Africa/Dakar
set TAUX_TVA=0
set PRIX_TTC=false
set MEMOIRE_JAVA=768m
rem ------------------------------------------------

if not exist "%~dp0gesstock.jar" (
  echo gesstock.jar est introuvable dans ce dossier.
  pause
  exit /b 1
)

rem --- Java 17 ou plus : celui du PATH, sinon les emplacements habituels ---
set "JAVA_EXE="
for /f "delims=" %%i in ('where java 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%i"
if not defined JAVA_EXE for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-*" "%ProgramFiles%\Eclipse Adoptium\jre-*" "%ProgramFiles%\Java\jdk-*" "%ProgramFiles%\Microsoft\jdk-*" "%ProgramFiles%\Zulu\zulu*" "%ProgramFiles%\Amazon Corretto\jdk*") do if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
if not defined JAVA_EXE goto java_absent

rem Version de Java : premiere ligne de "java -version", par exemple  openjdk version "17.0.20" 2026-07-21
"%JAVA_EXE%" -version 2>&1 | findstr /i "version" > "%TEMP%\gesstock-java.txt"
set "LIGNE="
set /p LIGNE=<"%TEMP%\gesstock-java.txt"
del "%TEMP%\gesstock-java.txt" >nul 2>&1
set "JAVA_VERSION="
set "JAVA_MAJEUR="
if not defined LIGNE goto java_ok
set "LIGNE=%LIGNE:"=%"
for /f "tokens=3" %%v in ("%LIGNE%") do set "JAVA_VERSION=%%v"
for /f "delims=." %%m in ("%JAVA_VERSION%") do set "JAVA_MAJEUR=%%m"
if not defined JAVA_MAJEUR goto java_ok
if %JAVA_MAJEUR% LSS 17 goto java_ancien
:java_ok

if not exist "%~dp0journaux" mkdir "%~dp0journaux"

rem --- Reglages passes a GesStock ---
set SPRING_PROFILES_ACTIVE=prod
set SERVER_PORT=%PORT%
set MANAGEMENT_PORT={{PORT_SUPERVISION}}
set "DB_URL=jdbc:mysql://localhost:3306/{{NOM_BASE}}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
set DB_USERNAME={{UTILISATEUR}}
set DB_PASSWORD={{MOT_DE_PASSE_BASE}}
rem L'interface est servie par GesStock lui-meme (meme adresse que l'API) : aucun reglage d'origine a faire.
set "CORS_ALLOWED_ORIGINS=http://localhost:%PORT%"
rem Reseau local en HTTP : les cookies de connexion ne peuvent pas exiger le HTTPS.
set COOKIES_SECURE=false
set APP_ZONE_HORAIRE=%FUSEAU_HORAIRE%
set APP_TVA_TAUX=%TAUX_TVA%
set APP_TVA_PRIX_TTC=%PRIX_TTC%
rem Journaux dans un fichier tournant : 10 Mo par fichier, 30 fichiers, 300 Mo au plus.
set "LOGGING_FILE_NAME=%~dp0journaux\gesstock.log"
set LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE=10MB
set LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY=30
set LOGGING_LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP=300MB

echo.
echo  GesStock demarre. Sur ce PC, ouvrez :  http://localhost:%PORT%
echo  Depuis les autres postes du reseau, ouvrez l'une de ces adresses :
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do for /f "tokens=*" %%b in ("%%a") do echo       http://%%b:%PORT%
echo.
echo  Le premier demarrage prend environ une minute. Gardez cette fenetre ouverte.
echo.

rem Si GesStock s'arrete (MySQL pas encore pret, par exemple), il est relance au bout de 30 secondes.
:boucle
"%JAVA_EXE%" -Xmx%MEMOIRE_JAVA% -jar "%~dp0gesstock.jar" >nul 2>>"%~dp0journaux\erreurs.log"
echo [%date% %time%] GesStock s'est arrete (code %errorlevel%) : nouvel essai dans 30 secondes. >> "%~dp0journaux\lancer.log"
echo  GesStock s'est arrete : nouvel essai dans 30 secondes. Voir le dossier journaux si cela se repete.
rem "ping" plutot que "timeout" : timeout echoue quand la fenetre n'est pas interactive.
ping -n 31 127.0.0.1 >nul
goto boucle

:java_absent
echo.
echo  Java 17 est introuvable sur ce PC. Installez-le, puis relancez demarrer.cmd.
pause
exit /b 1

:java_ancien
echo.
echo  La version de Java trouvee est la %JAVA_VERSION% : GesStock demande Java 17 ou plus recent.
echo  Installez Java 17, puis relancez demarrer.cmd.
pause
exit /b 1
