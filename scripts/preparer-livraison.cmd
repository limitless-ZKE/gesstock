@echo off
rem ============================================================================================================
rem  Prepare le KIT DE LIVRAISON a emporter chez un client : livraison\GesStock-AAAA-MM-JJ.zip
rem  (programme + interface, reglages, installation automatique, sauvegarde quotidienne, modes d'emploi).
rem  Option "rapide" : ne reconstruit pas le programme (utilise target\gesstock-0.0.1-SNAPSHOT.jar tel quel).
rem  Prerequis : Java 17 et Node.js (comme pour scripts\construire-pour-le-reseau.cmd).
rem ============================================================================================================
setlocal
cd /d "%~dp0.." || goto erreur

if /i not "%~1"=="rapide" (
  call "%~dp0construire-pour-le-reseau.cmd" || goto erreur
)
if not exist "target\gesstock-0.0.1-SNAPSHOT.jar" (
  echo Le programme target\gesstock-0.0.1-SNAPSHOT.jar n'existe pas : lancez sans l'option "rapide".
  goto erreur
)

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd"') do set JOUR=%%i
set COMMIT=inconnu
for /f %%i in ('git rev-parse --short HEAD 2^>nul') do set COMMIT=%%i
set DOSSIER=livraison\GesStock
set ARCHIVE=livraison\GesStock-%JOUR%.zip

echo.
echo === Assemblage de %DOSSIER% ...
if exist "livraison" rmdir /s /q "livraison"
mkdir "%DOSSIER%" || goto erreur
copy /y "target\gesstock-0.0.1-SNAPSHOT.jar" "%DOSSIER%\gesstock.jar" >nul || goto erreur
copy /y "scripts\kit-livraison\*" "%DOSSIER%\" >nul || goto erreur
(
  echo GesStock - version du %JOUR%
  echo Code source : commit %COMMIT%
) > "%DOSSIER%\VERSION.txt"

echo.
echo === Compression ...
"%SystemRoot%\System32\tar.exe" -a -c -f "%ARCHIVE%" -C "livraison" "GesStock" || goto erreur

echo.
echo TERMINE : %ARCHIVE%
echo Emportez ce fichier chez le client, puis suivez INSTALLATION.txt (qui se trouve a l'interieur).
exit /b 0

:erreur
echo.
echo ECHEC de la preparation du kit (voir le message ci-dessus).
exit /b 1
