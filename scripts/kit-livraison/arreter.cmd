@echo off
rem ====================================================================================================================
rem  Arrete GesStock (a faire avant une mise a jour, une restauration ou l'extinction volontaire du serveur).
rem  Le demarrage automatique reste en place : GesStock repartira au prochain demarrage de Windows, ou avec demarrer.cmd.
rem ====================================================================================================================
net session >nul 2>&1
if errorlevel 1 (
  echo Demande des droits administrateur : acceptez la fenetre Windows qui va s'afficher.
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0arreter.ps1"
echo.
pause
