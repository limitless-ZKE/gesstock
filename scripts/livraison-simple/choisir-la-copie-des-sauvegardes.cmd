@echo off
rem ====================================================================================================================
rem  Choisit OU copier chaque sauvegarde de la base : une cle USB ou un autre disque. Une sauvegarde restee sur le meme PC
rem  est perdue avec lui (panne, vol, incendie) : c'est la copie qui sauve les donnees. A faire une seule fois.
rem  Le choix est ecrit dans copie-des-sauvegardes.txt ; la cle doit rester branchee (sinon la sauvegarde reste sur le PC
rem  et le journal des sauvegardes le dit).
rem ====================================================================================================================
setlocal
title Choisir la copie des sauvegardes de GesStock
cd /d "%~dp0"
echo.
echo  Chaque sauvegarde de GesStock peut etre copiee automatiquement vers une cle USB ou un autre disque.
echo.
if exist "%~dp0copie-des-sauvegardes.txt" (
  echo  Dossier de copie actuel :
  type "%~dp0copie-des-sauvegardes.txt"
  echo.
)
echo  Exemples :   E:\GesStock-Sauvegardes     ou     D:\Sauvegardes
echo  La lettre (E: ou D:) est celle de votre cle USB : regardez dans "Ce PC". Laissez la cle branchee sur le PC.
echo  Pour ne plus faire de copie, tapez SUPPRIMER.
echo.
set "CHOIX="
set /p "CHOIX=Dossier de copie (Entree pour ne rien changer) : "
if not defined CHOIX goto rien
if /i "%CHOIX%"=="SUPPRIMER" goto supprimer

rem La valeur passe par une variable d'environnement : un nom de dossier avec un & ou un % ne casse pas le script.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$d = $env:CHOIX.Trim().Trim([char]34); try { New-Item -ItemType Directory -Path $d -Force -ErrorAction Stop | Out-Null; $t = Join-Path $d '.essai-gesstock.txt'; Set-Content -LiteralPath $t -Value 'essai'; Remove-Item -LiteralPath $t -Force; Set-Content -LiteralPath '%~dp0copie-des-sauvegardes.txt' -Value $d; exit 0 } catch { Write-Host $_.Exception.Message; exit 1 }"
if errorlevel 1 goto impossible
echo.
echo  C'est note. Chaque sauvegarde sera copiee dans ce dossier, des la prochaine (chaque jour a 13h, ou a la main).
echo  Pour essayer tout de suite : double-cliquez sur sauvegarder-la-base.cmd.
echo.
pause
exit /b 0

:supprimer
if exist "%~dp0copie-des-sauvegardes.txt" del "%~dp0copie-des-sauvegardes.txt"
echo.
echo  Fait : les sauvegardes ne sont plus copiees nulle part ailleurs que sur ce PC.
echo.
pause
exit /b 0

:rien
echo.
echo  Rien n'a ete change.
echo.
pause
exit /b 0

:impossible
echo.
echo  Impossible d'ecrire dans ce dossier : la cle USB est-elle branchee, et la lettre est-elle la bonne ?
echo  Rien n'a ete change. Relancez ce fichier pour reessayer.
echo.
pause
exit /b 1
