@echo off
rem ============================================================================================================
rem  Construit GesStock avec son interface : un seul programme (target\gesstock-0.0.1-SNAPSHOT.jar) que tous les
rem  postes du reseau ouvrent dans leur navigateur. A refaire apres chaque mise a jour du code.
rem  Prerequis : Java 17 et Node.js installes (deja le cas sur ce poste).
rem ============================================================================================================
setlocal
cd /d "%~dp0..\frontend" || goto erreur

echo.
echo === 1/2  Construction de l'interface ...
call npm install --no-audit --no-fund || goto erreur
call npm run build || goto erreur

echo.
echo === 2/2  Construction du programme (avec l'interface) ...
cd /d "%~dp0.." || goto erreur
call "%~dp0..\mvnw.cmd" -B -DskipTests -Pinterface clean package || goto erreur

echo.
echo TERMINE : target\gesstock-0.0.1-SNAPSHOT.jar contient maintenant l'interface.
echo Pour le lancer pour tout le reseau : scripts\lancer-gesstock-reseau.cmd
exit /b 0

:erreur
echo.
echo ECHEC de la construction (voir le message ci-dessus).
exit /b 1
