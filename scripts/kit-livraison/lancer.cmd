@echo off
rem ====================================================================================================================
rem  Lance GesStock. Si GesStock s'arrete (panne, MySQL pas encore demarre au demarrage de Windows...), il est relance
rem  automatiquement au bout de 30 secondes. Les messages de GesStock sont dans le dossier journaux.
rem  Pour l'arreter volontairement : arreter.cmd (ne pas simplement fermer cette fenetre si l'installation est automatique).
rem ====================================================================================================================
setlocal
cd /d "%~dp0"

call "%~dp0configuration.cmd"
if "%MYSQL_MOT_DE_PASSE%"=="CHANGEZ-MOI" (
  echo Modifiez d'abord configuration.cmd : adresse du serveur et mot de passe MySQL.
  pause
  exit /b 1
)

if not exist "%~dp0journaux" mkdir "%~dp0journaux"
if not exist "%~dp0sauvegardes" mkdir "%~dp0sauvegardes"

rem Java : le chemin trouve par l'installateur, sinon "java" du PATH.
if exist "%~dp0java.chemin.txt" set /p JAVA_EXE=<"%~dp0java.chemin.txt"
if not defined JAVA_EXE set JAVA_EXE=java

rem --- Reglages passes a GesStock ---
set SPRING_PROFILES_ACTIVE=prod
set SERVER_PORT=%PORT%
set MANAGEMENT_PORT=%PORT_SUPERVISION%
set DB_URL=jdbc:mysql://%MYSQL_HOTE%:%MYSQL_PORT%/%MYSQL_BASE%?useSSL=false^&serverTimezone=UTC^&allowPublicKeyRetrieval=true
set DB_USERNAME=%MYSQL_UTILISATEUR%
set DB_PASSWORD=%MYSQL_MOT_DE_PASSE%
rem Interface servie par GesStock lui-meme : meme adresse que l'API, aucun CORS a regler.
set CORS_ALLOWED_ORIGINS=http://%ADRESSE_SERVEUR%:%PORT%
rem Reseau local en HTTP : les cookies de connexion ne peuvent pas exiger le HTTPS.
set COOKIES_SECURE=false
set APP_ZONE_HORAIRE=%FUSEAU_HORAIRE%
set APP_TVA_TAUX=%TAUX_TVA%
set APP_TVA_PRIX_TTC=%PRIX_TTC%
rem Journaux dans un fichier, tournants : 10 Mo par fichier, 30 fichiers, 300 Mo au plus.
set LOGGING_FILE_NAME=%~dp0journaux\gesstock.log
set LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE=10MB
set LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY=30
set LOGGING_LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP=300MB

echo GesStock demarre. Adresse pour tous les postes :  http://%ADRESSE_SERVEUR%:%PORT%
echo (le premier demarrage d'une base vide prend 2 a 3 minutes)

:boucle
"%JAVA_EXE%" -Xmx%MEMOIRE_JAVA% -jar "%~dp0gesstock.jar"
echo [%date% %time%] GesStock s'est arrete (code %errorlevel%) : nouvel essai dans 30 secondes. >> "%~dp0journaux\lancer.log"
rem "ping" plutot que "timeout" : timeout echoue quand il n'y a pas de fenetre (tache planifiee).
ping -n 31 127.0.0.1 >nul
goto boucle
