@echo off
rem ====================================================================================================================
rem  Active le DEMARRAGE AUTOMATIQUE de GesStock : il se lance tout seul avec Windows, sans aucune fenetre, et le navigateur
rem  s'ouvre sur la page de connexion quand il est pret. A faire UNE SEULE FOIS, apres importer-la-base.cmd.
rem  Demande tout seul les droits administrateur. Peut etre relance sans risque (par exemple apres une mise a jour).
rem ====================================================================================================================
net session >nul 2>&1
if errorlevel 1 (
  echo Demande des droits administrateur : acceptez la fenetre Windows qui va s'afficher.
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0outils\demarrage-automatique.ps1"
echo.
pause
