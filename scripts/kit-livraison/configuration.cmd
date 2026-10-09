@echo off
rem ====================================================================================================================
rem  CONFIGURATION DE GESSTOCK POUR CE CLIENT
rem  Seul fichier a modifier a l'installation.
rem  Dans les mots de passe, eviter les caracteres  &  %  ^  !  "  \  et les espaces (lettres et chiffres : le plus sur).
rem ====================================================================================================================

rem --- Adresse du PC serveur sur le reseau (celle que les autres postes tapent dans leur navigateur) ---
set ADRESSE_SERVEUR=192.168.1.10
set PORT=8080
rem  Port interne de supervision (jamais ouvert au reseau). A changer seulement si un autre programme l'utilise deja.
set PORT_SUPERVISION=8081

rem --- Base de donnees MySQL (utilisateur dedie, jamais "root") ---
set MYSQL_HOTE=localhost
set MYSQL_PORT=3306
set MYSQL_BASE=gesstock_db
set MYSQL_UTILISATEUR=gesstock
set MYSQL_MOT_DE_PASSE=CHANGEZ-MOI

rem --- Reglages du commerce ---
rem  Fuseau horaire : il decide quand la journee de caisse change.
rem  Exemples : Africa/Dakar, Africa/Abidjan, Africa/Lome (UTC+0) ; Africa/Douala, Africa/Libreville (UTC+1)
set FUSEAU_HORAIRE=Africa/Dakar
rem  TVA en pourcent (0 = aucune TVA). PRIX_TTC=true si les prix du catalogue sont TTC, false s'ils sont HT.
set TAUX_TVA=0
set PRIX_TTC=false

rem --- PREMIERE INSTALLATION SEULEMENT (base vide) : creer le premier administrateur ---
rem  Retirer le "rem" des 2 lignes, mettre un mot de passe de 12 caracteres au moins, lancer GesStock UNE fois,
rem  puis REMETTRE le "rem" (le compte existe alors dans la base et ces lignes ne servent plus).
rem set APP_ADMIN_USERNAME=patron
rem set APP_ADMIN_PASSWORD=CHANGEZ-MOI-12-CARACTERES-AU-MOINS

rem --- Sauvegarde quotidienne de la base ---
rem  Heure de la sauvegarde automatique (format HH:MM).
set HEURE_SAUVEGARDE=22:00
rem  Nombre de jours de sauvegardes conservees.
set JOURS_CONSERVES=30
rem  INDISPENSABLE : un second emplacement, sur un AUTRE disque (cle USB, disque externe, dossier partage d'un autre PC).
rem  Si le disque du serveur tombe en panne, les sauvegardes qui s'y trouvent sont perdues avec lui.
rem  Exemple : set DOSSIER_COPIE_SAUVEGARDE=E:\Sauvegardes-GesStock
set DOSSIER_COPIE_SAUVEGARDE=

rem --- Reglages techniques (normalement a ne pas toucher) ---
rem  Memoire maximale de GesStock. Laisser le reste du PC a MySQL et a Windows.
set MEMOIRE_JAVA=768m
rem  Chemin complet de mysqldump.exe si GesStock ne le trouve pas tout seul. Exemple :
rem  set MYSQLDUMP_EXE=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe
set MYSQLDUMP_EXE=
