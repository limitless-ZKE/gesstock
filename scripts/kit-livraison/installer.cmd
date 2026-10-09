@echo off
rem ====================================================================================================================
rem  Installe GesStock : demarrage automatique avec Windows, sauvegarde quotidienne, pare-feu.
rem  Demande tout seul les droits administrateur. Avant : renseigner configuration.cmd.
rem ====================================================================================================================
net session >nul 2>&1
if errorlevel 1 (
  echo Demande des droits administrateur : acceptez la fenetre Windows qui va s'afficher.
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installer.ps1"
echo.
pause
