@echo off
rem ====================================================================================================================
rem  Retire le demarrage automatique de GesStock (et arrete GesStock). Ne touche NI a la base de donnees NI aux fichiers.
rem  Pour le remettre : installer-demarrage-automatique.cmd (dans le dossier au-dessus).
rem ====================================================================================================================
net session >nul 2>&1
if errorlevel 1 (
  echo Demande des droits administrateur : acceptez la fenetre Windows qui va s'afficher.
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0demarrage-automatique.ps1" -Desinstaller
echo.
pause
