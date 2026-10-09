@echo off
rem ============================================================================================================
rem  Lance GesStock pour TOUT le reseau. A lancer sur le PC qui sert de serveur (celui ou tourne WampServer).
rem  Les autres postes ouvrent  http://ADRESSE:8080  dans leur navigateur.
rem
rem  A MODIFIER avant le premier lancement (les 3 lignes ci-dessous) :
rem ============================================================================================================
set ADRESSE=192.168.1.75
set MYSQL_UTILISATEUR=gesstock
set MYSQL_MOT_DE_PASSE=CHANGEZ-MOI

rem --- PREMIERE INSTALLATION SEULEMENT (base vide) : creer le premier administrateur. Retirer le "rem" des 2 lignes, mettre un mot de
rem     passe de 12 caracteres au moins, lancer UNE fois, puis remettre le "rem" (le compte existe alors dans la base).
rem set APP_ADMIN_USERNAME=patron
rem set APP_ADMIN_PASSWORD=CHANGEZ-MOI-12-CARACTERES-AU-MOINS

rem --- Reglages (normalement a ne pas toucher) ---
set SPRING_PROFILES_ACTIVE=prod
set DB_URL=jdbc:mysql://localhost:3306/gesstock_db?useSSL=false^&serverTimezone=UTC^&allowPublicKeyRetrieval=true
set DB_USERNAME=%MYSQL_UTILISATEUR%
set DB_PASSWORD=%MYSQL_MOT_DE_PASSE%
rem Adresse(s) autorisee(s) a appeler l'API depuis un autre site : ici, le serveur lui-meme.
set CORS_ALLOWED_ORIGINS=http://%ADRESSE%:8080
rem Reseau local en HTTP (sans chiffrement) : les cookies de connexion ne peuvent pas exiger le HTTPS.
set COOKIES_SECURE=false

if "%MYSQL_MOT_DE_PASSE%"=="CHANGEZ-MOI" (
  echo Modifiez d'abord ce fichier : ADRESSE et mot de passe MySQL.
  pause
  exit /b 1
)
if not exist "%~dp0..\target\gesstock-0.0.1-SNAPSHOT.jar" (
  echo Le programme n'est pas construit : lancez d'abord scripts\construire-pour-le-reseau.cmd
  pause
  exit /b 1
)

echo GesStock demarre. Adresse pour les autres postes :  http://%ADRESSE%:8080
echo Laissez cette fenetre ouverte : la fermer arrete GesStock pour tout le monde.
java -jar "%~dp0..\target\gesstock-0.0.1-SNAPSHOT.jar"
pause
