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

CI GitHub (`.github/workflows/ci.yml`, à chaque envoi sur `main`) : serveur, migrations sur MySQL, interface, Android, sécurité
(Trivy ; gitleaks sur **tout l'historique**, valeurs d'essai connues dans `.gitleaks.toml`, tout autre secret la fait échouer)
et image Docker. Les actions d'autres éditeurs que GitHub sont épinglées sur un commit.

## Carte du code

- Backend : `src/main/java/com/gesstock/gesstock/` — un paquet par domaine : `articles`, `ventes`, `caisse`, `clients`,
  `fournisseurs`, `approvisionnements`, `inventaires`, `stock`, `statistiques`, `entreprise`, `promotions`, `journal`,
  `security`, `config`, `interfaceweb` (sert l'interface embarquée). Migrations Flyway `V1` à `V18` dans
  `src/main/resources/db/migration` ; Hibernate ne fait que valider le schéma (entité modifiée = migration obligatoire).
- Frontend : `frontend/src/pages` (un écran par route, tests à côté), `components`, `services` (appels API), `auth`, `entreprise`.
  Caisse : `CaissePage`, `ChampClient` (recherche client), `QuantiteSaisie` (quantité saisie au clavier, virgule acceptée).
  Quantités : `lib/quantite.ts` (lecture « 2,5 », affichage) et `stock/Quantites.java` côté serveur ; `ChampArticle` (recherche
  d'article par nom, bons d'approvisionnement).
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
- Remises LIBRES (décision du client, novembre 2026, remplace les plafonds par rôle) : le caissier fixe le prix négocié sans
  responsable, quel que soit le taux, par unité ou sur le total de la ligne. Sous le prix d'achat : la vente passe, avec un
  avertissement à la caisse et une ligne « Vente sous le prix d'achat » au journal des opérations.
- Quantités à 3 décimales (stock, ventes, bons, retours, inventaires). Montant de chaque ligne de vente stocké (`montant_ligne`).
- Approvisionnement : article cherché par son nom ; prix d'achat et de revente pré-remplis (derniers prix connus) ; le prix de
  revente devient le prix de vente à la RÉCEPTION ; un bon se corrige aussi après réception (stock, coût moyen, prix et dette
  fournisseur suivent ; refusé si déjà vendu, si déjà payé au-delà du nouveau total, ou s'il y a un retour fournisseur).
- Réception d'un bon : case « payer en espèces de la caisse » cochée par défaut (sortie automatique du total, bon payé) ; décochée, dette
  fournisseur comme avant ; le bon compte comme ACHAT dans les statistiques dans les deux cas. Entrées de fonds en caisse : ADMIN et
  MANAGER seulement, jamais du chiffre d'affaires. Statistiques : chiffre d'affaires tel quel, achats, autres dépenses (sorties de caisse
  MANUELLES seulement), résultat = CA − achats − autres dépenses, entrées de fonds à part.
- Stock disponible (filtre + export PDF/Excel), valeur du stock à la revente au tableau de bord, liste des articles par catégorie
  (avec ou sans stock, sans prix d'achat).
- Coût moyen pondéré. Le prix d'achat tapé dans la fiche d'un article fixe le coût de tout son stock en rayon (le client charge ses
  articles à 0 puis les tarife à la main) ; les bons le pondèrent ensuite ; les ventes passées gardent leur coût mémorisé, sauf celles
  valorisées à 0, corrigées au prix d'achat actuel et marquées « coût estimé » (V18, décision de l'utilisateur, novembre 2026).
  Bon d'approvisionnement imprimable en PDF (sans prix de revente). Ticket imprimé une seule fois : `ZoneImpression`, jamais
  `visibility: hidden` + `position: fixed` (le ticket se répète sur chaque page fabriquée par l'écran caché).
- Marge d'un article = prix de vente − coût, taux **sur le prix de vente** ; « Coût à vérifier » quand le coût est à 0 (jamais de faux 100 %).
- Import d'articles Excel/CSV (novembre 2026) : aperçu puis import **tout ou rien** ; référence existante = modifiée (cellule vide = inchangé), sinon créée ;
  catégories inconnues créées, unités inconnues refusées ; le stock d'un article existant ne change jamais par import (choix par défaut non confirmé).
- Listes à imprimer (PDF/Excel) : fournisseurs (tous, avec dette, sans dette) ; clients (tous, avec dette, sans dette, dette de plus de 30/60/90 jours, du plus gros
  débiteur au plus petit) : filtres proposés par moi à la demande de l'utilisateur (« propose-moi »), ouverts à tous les métiers qui voient les clients.
- Sauvegarde automatique (livraison simple) : chaque jour à 13 h, sans fenêtre, 30 sauvegardes gardées, copie facultative sur une clé USB
  (`choisir-la-copie-des-sauvegardes.cmd`), `restaurer-une-sauvegarde.cmd` avec copie de sécurité d'abord. Installée par `installer-demarrage-automatique.cmd`.
- Compte fournisseur complet (dettes, paiements, avoirs imputés d'office).
- Inventaire sans seuil : MANAGER/ADMIN valident, le validateur n'est pas l'ouvreur ; tout le stock ou une catégorie.
- Vente à crédit possible (pas pour un client de passage). Journal d'un caissier : **ses lignes seulement**
  (ses ventes, ses encaissements, ses sorties) avec « espèces à remettre » ; ni fond de caisse, ni solde, ni écart.
  Le journal complet est réservé aux responsables ; un CAISSIER qui nomme un autre caissier reçoit 403.
- Historique des ventes en trois onglets (toutes les ventes, ventes soldées, règlements de dettes), global ou par vendeur :
  une vente soldée (comptant, ou crédit réglé jusqu'au dernier franc) reste à la **date de la vente** ; un règlement est
  rattaché au compte **qui l'a encaissé** ; un caissier ne voit que les siens (403 s'il en nomme un autre), les
  responsables voient tout ou choisissent le vendeur (`PerimetreVendeur`).
- Journal des opérations (Gestion) : **réservé aux responsables** ; global ou par utilisateur, et par famille. Il enregistre
  aussi les connexions (réussies, refusées, avec le poste), les articles (ancien et nouveau prix), catégories, unités de
  vente, clients et comptes. Toute nouvelle opération sensible doit passer par `JournalActiviteService.enregistrer` et
  recevoir un libellé dans `OperationsConnues`.
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

Image Docker construite par la CI mais jamais démarrée ; pas d'analyse CodeQL (refusée sur un dépôt privé de compte
personnel) ; pas de HTTPS ; pas de conformité « logiciel de caisse certifié » (à
vérifier pays par pays) ; MySQL 8.4 seul testé ; listes non paginées (proformas, dettes, approvisionnements, clients,
retours) ; la sauvegarde automatique (tâche SYSTEM, restauration avec arrêt réel de GesStock) n'a jamais été essayée en administrateur ni chez le client.

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
- `gh` n'est pas installé : lire les résultats de la CI par l'API GitHub avec le jeton que renvoie `git credential fill`
  (script Node écrit avec Write), sans jamais afficher ni enregistrer ce jeton.
