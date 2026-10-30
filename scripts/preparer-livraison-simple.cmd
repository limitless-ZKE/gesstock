@echo off
rem ============================================================================================================
rem  Prepare le dossier de LIVRAISON SIMPLE sur le Bureau : gesstock.jar + base de lancement + 2 fichiers a
rem  double-cliquer + mode d'emploi. Le programme doit etre construit avant (scripts\construire-pour-le-reseau.cmd).
rem  Options : voir scripts\preparer-livraison-simple.ps1 (par exemple  -Destination D:\Livraison  -Admin patron).
rem ============================================================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0preparer-livraison-simple.ps1" %*
rem Pas de bloc ( ) ici : une parenthese dans un message fermerait le bloc et casserait le script (". etait inattendu").
if not errorlevel 1 exit /b 0
echo.
echo ECHEC de la preparation : voir le message ci-dessus.
exit /b 1
