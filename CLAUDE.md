# GesStock 3.0 — contexte pour Claude

Gestion de stock, ventes, caisse, approvisionnements et dettes clients pour de petits commerces (premier client :
un dépôt de boissons). Spring Boot 4 / Java 17 / MySQL 8.4 (API REST) + React 19 / TypeScript / Vite / Tailwind 4
(`frontend/`). Le détail technique est dans `README.md` et `frontend/README.md` : ne pas le recopier ici.

## L'utilisateur et la façon de travailler

- Francophone, non technique : répondre en **français simple**, guider **pas à pas**, expliquer ce qu'il doit cliquer ou taper.
- Messages de commit en français, terminés par la ligne `Co-Authored-By` demandée par l'environnement.
- **Ne jamais demander l'autorisation d'éditer des fichiers** (accès complet donné). Trancher seul les détails
  d'implémentation et signaler les choix par défaut dans le compte rendu.
- Poser une question **seulement pour une vraie décision métier** (rôles, seuils, TVA, périmètres), au fil de l'eau,
  option recommandée en premier. Pour un changement borné : un court plan dans la conversation, puis on y va.
- **Fusion dans `main` : seulement quand il écrit « fusionne »** (commit de fusion, annulable par `git revert -m 1 <fusion>`).
  Les travaux se font sur des branches `fonctionnalite/…` ou `correctif/…`.
- **`git push` : seulement sur demande explicite, à chaque fois** (« fais le git push »). Dépôt privé
  `https://github.com/limitless-ZKE/gesstock.git`.
- Un document destiné à un tiers commence par « Écrit pour : … ».
- **Aucun mot de passe dans le dépôt, dans un export ou dans ce fichier.** Les mots de passe d'un dossier livré sont
  donnés à l'utilisateur dans la conversation seulement.

## Commandes

```sh
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run     # API (profil dev obligatoire : sinon config de production)
cd frontend && npm install && npm run dev             # http://localhost:5173
./mvnw -B clean test                                  # suite serveur complète (~4 min), H2
cd frontend && npm run lint && npm test && npm run build
scripts\construire-apk.cmd                            # APK signée + mode d'emploi sur le Bureau (1re fois : -InstallerOutils)
cd android && gradlew.bat test assembleDebug          # tests et APK de l'application Android (ANDROID_HOME requis)
```

Migrations : seule `MigrationsMysqlIT` (voir README) valide le SQL de Flyway sur un vrai MySQL ; H2 ne la remplace pas.
Avant de toucher à une base réelle, rappeler la sauvegarde et tester sur une **copie jetable**.

## Carte du code

- Backend : `src/main/java/com/gesstock/gesstock/` — un paquet par domaine : `articles`, `ventes`, `caisse`, `clients`,
  `fournisseurs`, `approvisionnements`, `inventaires`, `stock`, `statistiques`, `entreprise`, `promotions`, `journal`,
  `security`, `config`, `interfaceweb` (sert l'interface embarquée). Migrations Flyway `V1` à `V14` dans
  `src/main/resources/db/migration` ; Hibernate ne fait que valider le schéma (entité modifiée = migration obligatoire).
- Frontend : `frontend/src/pages` (un écran par route, tests à côté), `components`, `services` (appels API), `auth`, `entreprise`.
  Caisse : `CaissePage`, `ChampClient` (recherche client), `QuantiteSaisie` (quantité saisie au clavier).
  Journaux : `JournalCaisse` choisit selon le rôle entre le journal complet et celui d'un caissier.
- Scripts : `scripts/` — `kit-livraison/` (kit complet), `livraison-simple/` + `preparer-livraison-simple.cmd/.ps1`
  (dossier simple), `construire-pour-le-reseau.cmd`, `lancer-gesstock-reseau.cmd`, `construire-apk.cmd/.ps1` + `android/` (APK).
- Application Android : `android/` (Java sans dépendance, Gradle 8.10, plugin Android 8.7, minSdk 26, targetSdk 35). Une vue web
  sur l'adresse du serveur réglée au premier lancement ; aucune page embarquée. Le pont `window.GesStockAndroid`
  (`frontend/src/lib/android.ts`, `PontAndroid.java`) fait passer l'impression (`imprimer`) et les fichiers (`telecharger`)
  par le téléphone : tout nouvel export ou impression doit utiliser ces deux fonctions, jamais `window.print()` ni un lien
  `download` directement. Clé de signature hors du dépôt : `%USERPROFILE%\.gesstock\android` (à ne jamais commiter ni perdre).

## Décisions métier de l'utilisateur (ne pas remettre en cause)

- 4 rôles : ADMIN, MANAGER, GESTIONNAIRE, CAISSIER. Annulation de vente : ADMIN/MANAGER seulement ; au comptant, le jour même.
- Fuseau UTC+0 (Africa/Dakar). Aucune TVA pour l'instant (décidée par le serveur).
- Remises plafonnées par rôle (5 / 10 / 25 / 100 %) ; au-delà, mot de passe d'un responsable au comptoir.
- Coût moyen pondéré. Compte fournisseur complet (dettes, paiements, avoirs imputés d'office).
- Inventaire sans seuil : MANAGER/ADMIN valident, le validateur n'est pas l'ouvreur ; tout le stock ou une catégorie.
- Vente à crédit possible (pas pour un client de passage). Journal d'un caissier : **ses lignes seulement**
  (ses ventes, ses encaissements, ses sorties) avec « espèces à remettre » ; ni fond de caisse, ni solde, ni écart.
  Le journal complet est réservé aux responsables ; un CAISSIER qui nomme un autre caissier reçoit 403.
- Page de connexion : l'ancienne « Bon retour » (panneau vert « Votre stock, en toute clarté. »), sans ticket de caisse.
- Choix par défaut non confirmés : valider un retour fournisseur = ADMIN/MANAGER ; un paiement fournisseur ou un retour
  validé ne s'annule pas ; CI sur Node 24.

## Livrer chez un client

Le kit complet (`preparer-livraison.cmd`) a été jugé **trop compliqué** par l'utilisateur. Il préfère la **livraison simple** :
un dossier avec `gesstock.jar`, une base de lancement toute prête (tables + utilisateur `admin` au mot de passe aléatoire),
`demarrer.cmd`, `importer-la-base.cmd` et `LISEZ-MOI.txt`.

- `scripts\preparer-livraison-simple.cmd` fabrique le dossier (par défaut sur le Bureau). La base est créée par GesStock
  lui-même dans une base jetable (Flyway + création de l'admin) puis exportée. `-MettreAJour` met à jour un dossier déjà
  livré sans toucher à sa base.
- Démarrage automatique au lancement de Windows, sans fenêtre : `installer-demarrage-automatique.cmd` (en administrateur)
  — tâche planifiée SYSTEM « GesStock - Serveur » (priorité normale, attend MySQL), raccourci dans le dossier Démarrage
  qui ouvre le navigateur sur la connexion quand le serveur répond, règle de pare-feu réseau local.
- Le PC de l'utilisateur est lent à froid (i5-2410M, disque dur 5400 tr/min, 8 Go) : GesStock peut y mettre plusieurs
  minutes à démarrer. La priorité « inférieure à la normale » du Planificateur était la cause principale (corrigée).
  Le résultat du 2e redémarrage réel n'est pas encore connu.
- Le client a déjà installé Java 17 et WampServer (MySQL 8.4.7, `root` sans mot de passe en local).
- Le catalogue de boissons du client (15 catégories, 144 articles) est fourni en scripts SQL idempotents hors dépôt
  (données propres au client). Le stock initial se fixe à la création, par « Ajuster le stock » ou par un approvisionnement.

## Limites connues

La CI n'a jamais été vue sur GitHub ; Docker non testé ; pas d'import d'articles CSV/Excel ; pas de HTTPS ; pas de
conformité « logiciel de caisse certifié » (à vérifier pays par pays) ; MySQL 8.4 seul testé ; listes non paginées
(proformas, dettes, approvisionnements, clients, retours) ; pas de sauvegarde planifiée dans la livraison simple.

## Pièges d'outillage (Windows, PowerShell / Git Bash)

- Ne jamais éditer du Java pendant un `./mvnw` en cours. Après un « Unresolved compilation problem » : `./mvnw clean package`.
- Le Bash de l'outil mange les antislashs et décode `\uXXXX` : écrire scripts, regex et chemins avec Write/Edit, jamais
  par heredoc ou `node -e`. Pas de python ni de jq (utiliser Node). Recherche : `git grep` plutôt que Grep sur tout le dépôt.
- Scripts livrés au client : `.cmd`/`.ps1` en ASCII pur, fins de ligne CRLF ; `.txt` d'usage en UTF-8 avec BOM.
  Exécuter un `.cmd` par `.\nom.cmd`. `Stop-Process` est asynchrone (`Wait-Process`). `Start-Process -Wait` pour `wscript`.
- Session sans droits administrateur : impossible d'essayer tâche SYSTEM, pare-feu ou redémarrage ; ne pas lancer les `.cmd`
  qui s'auto-élèvent (fenêtre UAC).
- Vérifier le front dans Edge headless piloté par DevTools (WebSocket natif de Node 24), ports distincts par script ;
  ne fermer que ses propres processus (PID confirmé par `netstat`) : l'Edge de l'utilisateur tourne en parallèle.
- Vitest : une fonction renvoyée par `beforeEach` est exécutée comme nettoyage (mettre des accolades).
- Mémoire 8 Go : si les tests refusent de démarrer (« insufficient memory »), faire fermer VS Code ou lancer les classes
  une par une avec `-DargLine="-Xmx480m"` et `vitest --no-file-parallelism`.
- Ne pas lancer l'application de dev (port 8080) en même temps qu'une installation de test sur le même port.
