@echo off
rem ====================================================================================================================
rem  Controle de l'installation de GesStock : MySQL, programme, demarrage automatique, pare-feu, sauvegardes, veille.
rem  A lancer a la fin de l'installation, puis apres chaque redemarrage de test. Demande les droits administrateur.
rem  Le rapport est aussi enregistre dans journaux\verification.txt.
rem ====================================================================================================================
net session >nul 2>&1
if errorlevel 1 (
  echo Demande des droits administrateur : acceptez la fenetre Windows qui va s'afficher.
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0verifier-installation.ps1"
echo.
pause
