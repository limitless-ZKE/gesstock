@echo off
rem ====================================================================================================================
rem  Demarre GesStock (apres arreter.cmd, par exemple). Si l'installation automatique n'a pas ete faite, utiliser lancer.cmd.
rem ====================================================================================================================
net session >nul 2>&1
if errorlevel 1 (
  echo Demande des droits administrateur : acceptez la fenetre Windows qui va s'afficher.
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
schtasks /query /tn "GesStock - Serveur" >nul 2>&1
if errorlevel 1 (
  echo GesStock n'est pas installe en demarrage automatique : lancez installer.cmd, ou lancer.cmd pour un essai.
  echo.
  pause
  exit /b 1
)
schtasks /run /tn "GesStock - Serveur"
echo.
echo GesStock demarre. Comptez 1 a 2 minutes avant que la page s'ouvre.
echo.
pause
