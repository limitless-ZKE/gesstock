@echo off
rem ====================================================================================================================
rem  Retire le demarrage automatique, la sauvegarde quotidienne et la regle du pare-feu.
rem  Ne touche NI a la base de donnees NI aux sauvegardes.
rem ====================================================================================================================
net session >nul 2>&1
if errorlevel 1 (
  echo Demande des droits administrateur : acceptez la fenetre Windows qui va s'afficher.
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installer.ps1" -Desinstaller
echo.
pause
