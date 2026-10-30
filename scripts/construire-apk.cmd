@echo off
rem ============================================================================================================
rem  Construit l'application Android de GesStock (GesStock.apk + mode d'emploi) dans le dossier GesStock-Android
rem  du Bureau. La premiere fois sur un PC :  scripts\construire-apk.cmd -InstallerOutils
rem  Options : voir scripts\construire-apk.ps1.
rem ============================================================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0construire-apk.ps1" %*
rem Pas de bloc ( ) ici : une parenthese dans un message fermerait le bloc et casserait le script.
if not errorlevel 1 exit /b 0
echo.
echo ECHEC de la construction de l'application Android : voir le message ci-dessus.
exit /b 1
