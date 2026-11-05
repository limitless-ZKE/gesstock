@echo off
rem ====================================================================================================================
rem  Remet la base de GesStock ({{NOM_BASE}}) dans l'etat d'une sauvegarde du dossier "sauvegardes" : apres une panne, une
rem  erreur de manipulation ou un disque remplace. Tout ce qui a ete fait APRES la sauvegarde choisie est perdu : une copie de
rem  l'etat actuel est donc faite d'abord (sauvegardes\avant-restauration-...sql). Demande les droits administrateur.
rem ====================================================================================================================
net session >nul 2>&1
if errorlevel 1 (
  echo Demande des droits administrateur : acceptez la fenetre Windows qui va s'afficher.
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0outils\restaurer.ps1" -Base "{{NOM_BASE}}"
echo.
pause
