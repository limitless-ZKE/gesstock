# GesStock

Gestion des stocks, ventes, caisse, approvisionnements et dettes clients.
Backend Spring Boot 4 / Java 17 / MySQL (API REST uniquement), frontend React (dossier `frontend/`).

## Développement

Le profil `dev` doit être activé explicitement : sans profil, l'application démarre en configuration de
production (elle exige `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS` et ne crée aucun compte).

```sh
# Linux / macOS / Git Bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# PowerShell
$env:SPRING_PROFILES_ACTIVE = "dev"; ./mvnw spring-boot:run

cd frontend && npm install && npm run dev       # http://localhost:5173
```

Depuis VS Code (Spring Boot Dashboard, « Run »), ajouter `SPRING_PROFILES_ACTIVE=dev` aux variables d'environnement.

- Profil `dev` : MySQL local `gesstock_db` (root, sans mot de passe, créée au besoin), schéma créé par **Flyway**
  comme en production (Hibernate ne fait que le valider : une entité modifiée sans migration fait échouer le
  démarrage), Swagger UI (`/swagger-ui.html`, réservé aux ADMIN connectés), cookies non sécurisés (HTTP local).
- Repartir d'une base neuve : sauvegarder (`mysqldump --databases gesstock_db > sauvegarde.sql`), puis
  `DROP DATABASE gesstock_db;` et relancer : Flyway recrée le schéma et `DataSeeder` les données de démonstration.
- Comptes de démonstration (profil `dev` uniquement) : `admin`, `caissier`, `gestionnaire`.
- Sonde de santé : `http://127.0.0.1:8081/actuator/health` (port de supervision, jamais exposé au réseau).
- Les tests tournent sur H2 en mémoire : `./mvnw test`.
- Front : `npm run lint`, `npm test` (Vitest + Testing Library, API simulée) et `npm run build` (types + production), depuis
  `frontend/` ; la CI les lance. Détails et choix volontairement différés dans `frontend/README.md`. Les correctifs de
  dépendances arrivent par Dependabot (versions majeures ignorées, à décider à part).
- Test des migrations sur un vrai MySQL (désactivé par défaut ; il crée et supprime ses propres bases jetables, en
  `sql_mode` strict) :

  ```sh
  GESSTOCK_TEST_MYSQL=localhost:3306 GESSTOCK_TEST_MYSQL_USER=root GESSTOCK_TEST_MYSQL_PASSWORD= \
    ./mvnw test -Dtest=MigrationsMysqlIT
  ```

  Il vérifie l'installation neuve (V1 à V12 puis validation Hibernate, y compris les valeurs d'enum MySQL que Hibernate
  ne contrôle pas, et les contraintes CHECK / index uniques qui portent les règles de fond), la mise à jour depuis V3 avec
  des données, et la reprise d'une base existante. La CI le lance sur MySQL 8.4.

## Authentification et droits

API pure : aucune page HTML n'est servie par le backend. Session par cookie (`JSESSIONID`) + protection CSRF :
le backend écrit le cookie `XSRF-TOKEN`, le frontend le renvoie dans l'en-tête `X-XSRF-TOKEN` pour toute requête
d'écriture.

| Appel | Résultat |
|---|---|
| `POST /api/auth/login` (formulaire `username`, `password`) | `204` ; sinon `401` `IDENTIFIANTS_INVALIDES`, `429` `TROP_DE_TENTATIVES` |
| `GET /api/auth/me` | `200` `{ username, roles }` ou `401` |
| `POST /api/auth/logout` | `204` |

- À la connexion, le jeton CSRF est renouvelé et la réponse `204` porte déjà le nouveau cookie `XSRF-TOKEN` : un client
  autre que le front peut écrire immédiatement après, sans appel intermédiaire.
- Les erreurs de sécurité sont en `application/problem+json` avec un champ `code` stable
  (`NON_AUTHENTIFIE`, `ACCES_REFUSE`, `CSRF_INVALIDE`…).
- Après 5 mauvais mots de passe en 15 minutes, le couple identifiant + adresse IP est bloqué 15 minutes
  (état en mémoire : une seule instance du serveur).
- Tout ce qui n'est pas explicitement autorisé est refusé (`anyRequest().denyAll()`), voir `SecurityConfig`.

| Rôle | Accès |
|---|---|
| `ADMIN` | Tout, dont les comptes utilisateurs |
| `MANAGER` | Tout sauf les comptes ; **seul avec ADMIN à pouvoir annuler une vente** |
| `GESTIONNAIRE` | Catalogue, fournisseurs, approvisionnements, caisse, ventes, clients, statistiques, journal |
| `CAISSIER` | Caisse, ventes, proformas, clients et règlements ; consultation du catalogue |

Seuls ces quatre rôles existent. Les anciens rôles `VENDEUR` et `USER` sont fusionnés dans `CAISSIER`
(migration `V4`). Pour une base de développement qui n'est pas encore sous Flyway, réattribuer ces comptes
à `CAISSIER` depuis l'écran d'administration, ou exécuter le contenu de `V4__fusion_roles_vendeur_user.sql`.

## Erreurs et règles de caisse

- Toute réponse d'erreur de l'API est en `application/problem+json` : `status`, `title`, `detail` (en français),
  `code` stable (`STOCK_INSUFFISANT`, `CSRF_INVALIDE`…), `requestId` et, pour une validation, `erreurs` par champ.
  Le `requestId` est le même que l'en-tête `X-Request-Id` et figure dans les logs : c'est la référence à
  communiquer pour retrouver une erreur. Une erreur imprévue donne un 500 générique, la cause reste dans les logs.
- Le stock ne se modifie que par réception, vente ou `POST /api/articles/{id}/ajustement-stock` (motif obligatoire,
  mouvement de stock et journal) ; la fiche article refuse tout changement de stock.
- Une vente au comptant, y compris issue d'un proforma, exige un mode de règlement et un montant reçu : c'est ce qui
  alimente le solde théorique de la caisse. Le « montant encaissé » est le total de la vente, pas la somme remise.

- **Remises** : un prix négocié plus bas que le prix catalogue est plafonné selon le rôle du vendeur (CAISSIER 5 %,
  GESTIONNAIRE 10 %, MANAGER 25 %, ADMIN 100 %, réglables par `APP_REMISES_PLAFOND_<RÔLE>`). Au-delà, ou sous le coût de
  revient (`APP_REMISES_MARGE_PLANCHER`, 0 % par défaut), le serveur répond `409 APPROBATION_REQUISE` : un AUTRE responsable
  (MANAGER ou ADMIN) saisit son identifiant et son mot de passe au comptoir, la même demande est renvoyée avec
  `approbation: { username, motDePasse }`. Le serveur vérifie le mot de passe sans ouvrir de session (mêmes limites d'essais
  que la connexion), garde le nom du responsable sur la ligne de vente ou de proforma et écrit une ligne au journal. Les
  promotions ne comptent pas dans le plafond.
- **Annulation d'une vente** (ADMIN et MANAGER seulement, `POST /api/ventes/{id}/annulation`) : à crédit, la dette est
  annulée et les versements éventuels remboursés. Au comptant, seulement le jour même et tant que la journée de caisse de la
  vente est ouverte, avec les articles rapportés en l'état (`articlesRetournesEnEtat`) : le stock rentre au coût de la vente et
  le client est remboursé (en espèces, à condition que la caisse en contienne assez ; sinon par le même moyen de paiement). Le
  remboursement en espèces n'est pas une sortie de caisse séparée : la vente annulée quitte les encaissements, ce qui fait
  baisser le solde théorique du même montant. Après la clôture, une journée ne se rouvre pas.
- **Inventaire physique** (`/api/inventaires`) : ouvrir (tout le catalogue ou une catégorie avec ses sous-catégories), compter
  article par article, soumettre, puis valider. Ouvrir, compter et soumettre : ADMIN, MANAGER, GESTIONNAIRE ; valider, reprendre
  en saisie ou annuler : ADMIN et MANAGER, et la validation exige une AUTRE personne que celle qui a ouvert. Un seul inventaire
  est en cours à la fois. L'ouverture photographie le stock théorique et le coût moyen ; comme les ventes continuent pendant le
  comptage, la validation applique l'ÉCART (compté − théorique) au stock du moment, jamais la valeur comptée, et refuse tout
  si un écart ferait passer un stock sous zéro (article à recompter). Tout écart exige un motif à la soumission. Les articles non
  comptés ne sont pas touchés. `GET /api/inventaires/{id}/export.xlsx` donne la feuille de comptage. Un stock de départ saisi
  à la création d'un article laisse un mouvement `STOCK_INITIAL`.
- **Compte fournisseur** (`/api/fournisseurs/comptes`, `/{id}/compte`) : recevoir un bon crée une dette égale à son total (quantité
  × prix) ; un bon de valeur nulle n'en crée pas, et les bons reçus avant `V11` n'en ont pas non plus (on ignore ce qui a déjà été
  payé). `POST /api/fournisseurs/dettes/{id}/reglements` enregistre un paiement : jamais au-delà du solde ; en espèces il exige une
  session de caisse ouverte avec assez de liquide et laisse une SORTIE de caisse au nom du fournisseur. Un avoir (créé à la main
  par ADMIN ou MANAGER, ou par un retour de marchandise) s'impute de lui-même sur les dettes les plus anciennes ; ce qui reste
  en crédit s'impute sur les prochains bons dès leur réception. Les écritures d'un même fournisseur sont sérialisées par un verrou
  sur sa ligne. Un paiement enregistré ne s'annule pas (pas d'écran de correction pour l'instant).
- **Retour au fournisseur** (`/api/retours-fournisseurs`) : on part d'un bon REÇU et de ses lignes (`GET /bons/{id}/lignes` donne ce qui
  reste retournable). Préparer ou annuler un brouillon : ADMIN, MANAGER, GESTIONNAIRE ; valider : ADMIN et MANAGER. La validation
  compte les unités rendues sur la ligne du bon par un UPDATE conditionnel (jamais plus que reçu, même avec deux validations
  simultanées ; un CHECK en base le garantit aussi), refuse ce qui n'est plus en rayon, sort le stock au coût du bon (mouvement
  `RETOUR_FOURNISSEUR`), recalcule le coût moyen et crédite le fournisseur d'un avoir égal à quantité × coût du bon, qui s'impute
  aussitôt sur ses dettes. Un retour validé ne s'annule pas.
- **Entreprise et logo** (`/api/entreprise`, écran « Entreprise » du menu) : nom, adresse, téléphone, e-mail, RCCM / NINEA / SIRET,
  site web et message de fin de ticket sont enregistrés en base (une seule ligne) et lus à CHAQUE impression : plus rien n'est écrit
  en dur dans les documents. Les lire : tous les rôles connectés (un caissier imprime des tickets) ; les modifier : ADMIN et MANAGER.
  Le logo est stocké en base (donc dans les sauvegardes, sans dossier ni volume à configurer). Un fichier envoyé n'est jamais
  conservé tel quel : PNG ou JPEG seulement (reconnus à leur signature ; SVG, GIF et WebP refusés), 2 Mo et 4000 px de côté au
  plus (`APP_LOGO_OCTETS_MAX`), dimensions lues avant décodage, puis ré-encodé en PNG par le serveur (métadonnées et contenu caché
  supprimés) et réduit à 1000 px au plus. Chaque emplacement demande ensuite son format, calculé à la demande et mis en cache :
  `GET /api/entreprise/logo?taille=miniature|ticket|entete|original` (160×80, 320×160, 720×300 px au plus ; proportions conservées,
  jamais d'agrandissement). L'ETag est l'empreinte du logo : remplacer l'image la renouvelle partout dès l'impression suivante.
  Documents concernés : ticket de vente, reçu de versement client, rapport de statistiques (écran, PDF, Excel) et feuille
  d'inventaire Excel. Il n'existe pas encore de facture A4 ni de bon de livraison ; le composant `EnTeteDocument` (variante A4)
  est prêt pour eux.
- **Coût du stock** : le coût d'un article est son coût moyen pondéré (CMP), recalculé à chaque réception ; chaque ligne de
  vente mémorise le coût du moment, donc la marge d'une vente passée ne bouge plus. `historique_prix_achat` garde l'évolution.
- **TVA** : le taux est imposé par le serveur (`APP_TVA_TAUX`, 0 par défaut : aucune TVA), jamais saisi à la caisse
  (un autre taux envoyé est refusé). Pour l'activer sans migration : `APP_TVA_TAUX=18`, et `APP_TVA_PRIX_TTC=true` si les
  prix du catalogue sont TTC (la taxe en est extraite : le client paie le prix affiché) ou `false` s'ils sont HT (la taxe
  s'ajoute). La caisse lit le régime sur `GET /api/caisse/parametres`. Les proformas n'appliquent pas de TVA : à traiter
  le jour où la TVA est activée.

## États à imprimer par période (historique des ventes, journal de caisse)

Les deux écrans partagent le sélecteur de période de l'écran Statistiques (aujourd'hui, hier, ce mois, ou deux dates) et deux boutons :
**Excel** (pour retravailler les chiffres) et **PDF** (A4 paysage, avec le nom et le logo de l'entreprise, numéro de page, à imprimer).
Tous les rôles lisent l'historique des ventes ; le **journal complet** de la journée (lignes de tous les caissiers) est réservé à `ADMIN`,
`MANAGER` et `GESTIONNAIRE` (voir `SecurityConfig`) : un caissier lit et imprime **son propre journal** (voir plus bas). Une période va de 1 à 366 jours
(`400 REQUETE_INVALIDE` si la fin précède le début ou si la période est trop longue) ; l'écran, lui, n'accepte aucune date future.

| Écran | API (même contenu en JSON, `/excel` et `/pdf`) | Contenu |
|---|---|---|
| Historique des ventes | `GET /api/ventes/historique?debut=&fin=` | une ligne par vente (validées et annulées), résumé (nombre, chiffre d'affaires TTC, remises, annulées), sous-total par jour sur plusieurs jours ; Excel : feuille « Articles vendus » en plus |
| Mouvements de caisse > onglet « Journal de caisse » | `GET /api/caisse/journal?debut=&fin=` | par journée de caisse : fond, ventes en espèces, règlements de dettes en espèces, sorties, solde théorique, espèces comptées, écart, et chaque écriture avec son solde ; PDF à signer (visas) |

**Journal de caisse par caissier.** `GET /api/caisse/journal/caissier?debut=&fin=[&caissier=]` (et `/excel`, `/pdf`) ne garde que ce qu'un compte a fait lui-même :
ses ventes (le vendeur de la vente), les règlements de dettes qu'il a encaissés, ses sorties, journée par journée (les journées où il n'a rien fait
n'y figurent pas). Le fond de caisse, le solde théorique et l'écart de clôture sont ceux du **tiroir de la journée**, partagé par tous (une seule session
par jour) : ils n'y figurent pas ; à leur place, ses **espèces à remettre** = ses ventes en espèces + ses règlements en espèces − ses sorties, et en
résumé ses encaissements hors espèces, ses ventes à crédit et ses ventes annulées. Un **caissier** reçoit toujours le sien : le serveur impose son nom,
en nommer un autre est refusé (`403`). Un **responsable** (`ADMIN`, `MANAGER`, `GESTIONNAIRE`) doit nommer le caissier (`400` sinon ; `404` si le compte
n'existe pas) ; `GET /api/caisse/journal/caissiers` liste les comptes (désactivés compris) pour la liste « Caissier » de l'écran, qui garde la période
choisie quand on change de caissier. Tests : `JournalCaissierServiceTests`, `JournalCaissierHttpTests`, `DocumentsCaissierTests`.

Règles du journal (les mêmes que le solde théorique de la clôture, `SessionCaisseService`) : une vente annulée n'y figure plus et son
remboursement n'est pas une sortie de plus ; une vente à crédit n'a rien rapporté ; Mobile Money, carte ou virement ne passent pas par le
tiroir-caisse et sont résumés à part ; un règlement de dette hors espèces n'est jamais rattaché à la session. Le dernier solde de chaque
journée est donc toujours celui que la caisse affiche à la clôture (test `JournalCaisseServiceTests`). L'écran affiche au plus 5 000 ventes,
les documents 50 000 : au-delà, le serveur demande une période plus courte au lieu de ralentir.

## Plusieurs postes sur un réseau local (une seule adresse)

Un PC fait office de **serveur** (celui où tourne MySQL) ; les caisses et le bureau ouvrent simplement `http://ADRESSE-DU-SERVEUR:8080` dans
leur navigateur. Le programme embarque son interface (profil Maven `interface`) : pas de second programme à lancer, pas de réglage
à faire sur les autres postes. Chaque poste imprime ses tickets sur sa propre imprimante.

1. **Construire** (sur le serveur, à refaire après chaque mise à jour du code) : `scripts\construire-pour-le-reseau.cmd`. Il construit
   l'interface puis le programme `target\gesstock-0.0.1-SNAPSHOT.jar`. Avec Docker, `docker compose up -d --build` fait la même chose.
2. **Créer un utilisateur MySQL dédié** (ne pas faire tourner un serveur de réseau avec `root`) :
   ```sql
   CREATE USER 'gesstock'@'localhost' IDENTIFIED BY 'un mot de passe solide';
   GRANT ALL PRIVILEGES ON gesstock_db.* TO 'gesstock'@'localhost';
   ```
3. **Lancer** : modifier les 3 premières lignes de `scripts\lancer-gesstock-reseau.cmd` (adresse IP du serveur, utilisateur et mot de passe
   MySQL), puis le lancer et laisser sa fenêtre ouverte. Il active le profil `prod` et `COOKIES_SECURE=false` (voir ci-dessous).
4. **Autoriser le port 8080** dans le pare-feu Windows du serveur (PowerShell en administrateur ; réseau « privé » seulement) :
   `New-NetFirewallRule -DisplayName "GesStock 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private`
5. **Fixer l'adresse du serveur** (réservation DHCP dans la box ou adresse IP fixe Windows) : si elle change, les postes ne trouvent plus GesStock.
6. **Ouvrir** `http://ADRESSE:8080` depuis un autre poste ; connexion avec les comptes habituels.

**Première installation chez un client (base vide)** : créer la base vide (`CREATE DATABASE gesstock_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`),
puis au PREMIER lancement seulement, définir `APP_ADMIN_USERNAME` et `APP_ADMIN_PASSWORD` (12 caractères au moins) : ils créent le premier
administrateur, jamais un mot de passe par défaut. Le premier démarrage applique les 14 migrations et prend **2 à 3 minutes** (les suivants,
une demi-minute). Les rôles ADMIN, MANAGER, GESTIONNAIRE et CAISSIER existent d'emblée (migration V14) : l'administrateur crée ensuite
les comptes de l'équipe dans l'écran Administration, puis renseigne l'écran Entreprise, les catégories, unités et articles.

À savoir :
- L'interface construite appelle l'API en `/api`, sur le serveur qui la sert (aucun CORS, aucune adresse à régler). Seul `npm run dev`
  utilise l'adresse `http://localhost:8080/api` ; `VITE_API_BASE_URL` impose une autre adresse dans les deux cas.
- **HTTP sans chiffrement** : sur un réseau local, les cookies de connexion ne peuvent pas exiger le HTTPS (`COOKIES_SECURE=false`), donc
  les mots de passe circulent en clair sur le réseau. Acceptable sur un réseau filaire privé ; sur un Wi-Fi partagé ou non fiable, placer
  un serveur web avec certificat (Caddy, nginx) devant GesStock. **Ne jamais exposer le port à Internet** sans HTTPS.
- **Changer les mots de passe de démonstration** (`admin123`…) depuis l'écran Administration avant d'ouvrir l'accès au réseau.
- Le serveur doit rester allumé et GesStock lancé pendant les heures d'ouverture. Ce lancement « à la main » ne redémarre rien tout seul :
  pour un client, utiliser le **kit de livraison** (section suivante), qui installe le démarrage automatique et la sauvegarde quotidienne.
- Seul le serveur parle à MySQL ; la supervision (`/actuator/health`) reste sur le port local 8081, jamais sur le réseau.
- Testé : profil `prod` sur une copie de la base, ouvert par l'adresse réseau du PC dans un vrai navigateur (connexion, tous les écrans,
  rechargement d'une page profonde, logo, tickets). Pas encore essayé avec un second PC physique ni avec le pare-feu activé.

## Livrer chez un client (kit de livraison)

`scripts\preparer-livraison.cmd` construit l'interface et le programme puis assemble `livraison\GesStock-AAAA-MM-JJ.zip` (option `rapide` : sans
reconstruire). Le dossier `livraison\` n'est pas versionné. Le zip contient `gesstock.jar` et les fichiers de `scripts\kit-livraison\` :

| Fichier | Rôle |
|---|---|
| `INSTALLATION.txt` | guide complet chez le client (Java 17, WampServer/MySQL, base, adresse fixe, réglages, installation, tests dont le **redémarrage sans ouvrir de session**, liste de sortie « avant de partir », dépannage) |
| `EXPLOITATION.txt` | vie quotidienne, restauration d'une sauvegarde, mise à jour, retour arrière, que transmettre au support |
| `configuration.cmd` | **seul fichier à modifier** : adresse, base, mot de passe MySQL, fuseau, TVA, premier administrateur, sauvegardes |
| `installer.cmd` / `installer.ps1` | demande les droits administrateur ; crée la tâche « GesStock - Serveur » (démarrage avec Windows, compte SYSTEM, relance), la tâche « GesStock - Sauvegarde » (quotidienne) et la règle de pare-feu (réseau local seulement) ; `-Simulation` n'écrit rien |
| `lancer.cmd` | profil `prod`, journaux dans `journaux\gesstock.log` (10 Mo × 30 fichiers, 300 Mo au plus), relance 30 s après tout arrêt |
| `sauvegarder-base.cmd` | `mysqldump` → `.zip` vérifié (« Dump completed »), 30 jours conservés, copie sur un second disque si `DOSSIER_COPIE_SAUVEGARDE` est renseigné, mot de passe jamais sur la ligne de commande ; code de retour 1 en cas d'échec |
| `verifier-installation.cmd` / `.ps1` | contrôle en un clic (administrateur) : réglages, Java, service MySQL en démarrage automatique et fermé au réseau, accès à la base, GesStock sur ce PC et sur l'adresse du réseau, adresse fixe, tâches planifiées, pare-feu, **GesStock lancé après le dernier démarrage de Windows**, sauvegardes (dont la copie), mise en veille ; `[OK]`/`[ATTENTION]`/`[ERREUR]` et rapport dans `journauxerification.txt` |
| `arreter.cmd`, `demarrer.cmd`, `desinstaller.cmd` | arrêt (serveur **et** boucle de relance, ce dossier seulement), démarrage, retrait |

Mise à jour chez un client : sauvegarde, `arreter.cmd`, remplacer `gesstock.jar` (sans toucher à `configuration.cmd`), `demarrer.cmd`. Les migrations
Flyway ne sont pas réversibles : le retour arrière passe par la restauration de la sauvegarde faite juste avant.

Testé pour de vrai (base MySQL jetable, utilisateur dédié sans droits globaux, dossier avec une espace dans son chemin) : première installation depuis le zip
(14 migrations en 1 min 48, premier administrateur, connexion, interface et pages profondes servies), journal dans un fichier, sauvegarde (35 tables, zip
lisible, copie sur le second emplacement, conservation à 30 jours sur les deux dossiers sans toucher aux fichiers étrangers), échecs de sauvegarde (mauvais
mot de passe, second disque absent, `mysqldump` ou base introuvable : code 1, journal, aucun `.sql` ni fichier de mot de passe résiduel), relance
automatique après arrêt brutal du programme, `arreter.ps1` (n'arrête que ce dossier), restauration d'une sauvegarde puis nouvelle connexion, `installer.ps1 -Simulation`.
Installation réelle réussie ensuite sur le PC d'essai de l'utilisateur (Wamp + Java 17, tâches et pare-feu enregistrés). `verifier-installation.ps1` a été exécuté sur ce PC
sans droits administrateur (les contrôles des tâches, du pare-feu et des processus sous SYSTEM n'y sont pas lisibles). **Non testé** : le démarrage automatique après un
redémarrage réel de Windows, un second PC physique, `bind-address = 127.0.0.1` dans le `my.ini` de Wamp. `INSTALLATION.txt` (parties 11 et 12) fait donc faire ces
vérifications sur place, avec une liste de sortie à cocher avant de quitter le client.

### Livraison simple (sans installateur)

`scripts\preparer-livraison-simple.cmd` (après `scripts\construire-pour-le-reseau.cmd`) produit sur le Bureau le dossier `GesStock-Livraison-Simple` :
`gesstock.jar`, `base-de-lancement.sql`, `importer-la-base.cmd`, `demarrer.cmd`, `LISEZ-MOI.txt` (modèles dans `scripts\livraison-simple\`).
Le serveur a seulement besoin de Java 17 et de WampServer : on copie le dossier, on double-clique `importer-la-base.cmd` (une fois), puis `demarrer.cmd`.

- **La base est fabriquée par GesStock lui-même** : une base jetable de ce PC est migrée par Flyway et reçoit le premier administrateur (mot de passe
  **aléatoire à chaque préparation**, affiché une seule fois ; le SQL ne contient que son empreinte BCrypt), puis elle est exportée et supprimée. À refaire pour
  chaque client et après chaque nouvelle migration. Options : `-Destination`, `-Admin`, `-MotDePasseAdmin`, `-NomBase`, `-Utilisateur`, `-Port`, `-MotDePasseRoot`.
- `importer-la-base.cmd` refuse d'effacer une base déjà remplie sans que l'on tape `EFFACER` ; `demarrer.cmd` refuse un Java inférieur à 17 et relance GesStock
  s'il s'arrête.
- **Démarrage automatique, sans fenêtre** : `installer-demarrage-automatique.cmd` (une fois, administrateur, relançable après une mise à jour) enregistre la
  tâche « GesStock - Serveur » (au démarrage de Windows + 1 minute, compte `SYSTEM` donc aucune fenêtre ni session nécessaire, relance si arrêt, lance
  `demarrer.cmd silencieux` qui ne fait jamais de pause), un raccourci du dossier Démarrage de tous les utilisateurs qui lance `outils\ouvrir-navigateur.vbs`
  (invisible : il attend que GesStock réponde sur le port lu dans `demarrer.cmd`, puis ouvre le navigateur par défaut sur la page de connexion ; au bout de 7 minutes
  sans réponse il l'ouvre quand même), et la règle de pare-feu du réseau local (indispensable : lancé par Windows, GesStock n'affiche pas la question « autoriser
  l'accès ? »). `outils\arreter.cmd` arrête GesStock avant une mise à jour ; `outils\desactiver-demarrage-automatique.cmd` retire tout. Pas de sauvegarde planifiée
  (c'est le rôle du kit complet).
- **Mettre à jour un dossier déjà livré** sans toucher à sa base : `preparer-livraison-simple.cmd -MettreAJour -Destination <dossier>` copie le nouveau `gesstock.jar`
  et les scripts, et relit dans son `demarrer.cmd` le port, la base, le compte, le mot de passe MySQL, le fuseau et la TVA (aucun Java ni WampServer requis).
- Vérifié de bout en bout avec d'autres noms de base, de compte et de port : import, refus de réimporter, démarrage en 56 s, connexion de l'administrateur dans
  Edge par l'adresse du réseau, quantité saisie, vente à crédit, dette créée. Le vrai dossier livré a été démarré sur son propre SQL (importé sous d'autres noms) : connexion
  de l'administrateur acceptée, mauvais mot de passe refusé. Démarrage automatique : testés sans droits administrateur seulement la simulation de l'installation et du
  retrait, le code de la tâche (enregistrée puis supprimée pour l'utilisateur courant) et du raccourci, `demarrer.cmd silencieux` lancé sans fenêtre, et
  `ouvrir-navigateur.vbs` (attend un serveur absent puis le voit prêt 30 s après son lancement). **Non testés** (il faut un administrateur et un vrai redémarrage) :
  l'enregistrement de la tâche sous `SYSTEM`, la règle de pare-feu, le raccourci du dossier Démarrage et l'ouverture réelle du navigateur à la connexion.
  **Premier vrai redémarrage** (PC de 2011, i5-2410M, disque dur 5400 tr/min) : tâche, raccourci et navigateur ont fonctionné, mais GesStock n'a été prêt que plus de vingt minutes
  après le démarrage de Windows (le navigateur s'était ouvert avant). Cause mesurée : la tâche tournait en priorité « inférieure à la normale » (défaut du Planificateur,
  `Win32_Process.Priority` = 6 contre 8), et Java a mis dix minutes à arriver au premier message pendant que Windows et l'ouverture de session saturaient le disque. Corrections :
  priorité normale, `demarrer.cmd` qui attend MySQL, options Java (`-XX:TieredStopAtLevel=1 -XX:+UseSerialGC`, démarrage 33 s → 24 s à chaud), navigateur qui attend 20 minutes.

## Production (profil `prod`)

```sh
export MYSQL_PASSWORD=... MYSQL_ROOT_PASSWORD=...
export APP_ADMIN_USERNAME=admin APP_ADMIN_PASSWORD='12 caractères minimum'
export CORS_ALLOWED_ORIGINS=https://gesstock.exemple.com
docker compose up -d --build
```

| Variable | Rôle |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Connexion MySQL (obligatoires) |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` | Premier administrateur, créé seulement si aucun utilisateur n'existe |
| `CORS_ALLOWED_ORIGINS` | Origines du frontend, séparées par des virgules (obligatoire) |
| `COOKIES_SECURE` | `true` par défaut : cookies réservés au HTTPS. `false` seulement pour un déploiement volontairement en HTTP, sinon la connexion échoue |
| `MANAGEMENT_PORT` | Port de supervision (8081 par défaut), lié à `127.0.0.1` |
| `APP_ZONE_HORAIRE` | Fuseau du commerce (`Africa/Dakar` par défaut) : il décide quand la journée de caisse change |
| `APP_TVA_TAUX`, `APP_TVA_PRIX_TTC` | Taux de TVA en % (0 = aucune TVA) et prix catalogue TTC ou HT |

Le schéma est géré par Flyway ; Hibernate se contente de le valider. Swagger est désactivé.
Le contrôle de santé de Docker interroge `http://127.0.0.1:8081/actuator/health/readiness` dans le conteneur.

### Migrations (Flyway, `src/main/resources/db/migration`)

- `V1__schema_initial.sql` : schéma complet pour une base vide.
- `V2__montants_en_decimal.sql` : conversion des montants `DOUBLE` en `DECIMAL(19,4)`.
- `V3__prix_catalogue_lignes.sql` : prix catalogue mémorisé sur les lignes de vente et de proforma.
- `V4__fusion_roles_vendeur_user.sql` : fusion des rôles `VENDEUR` et `USER` dans `CAISSIER`.
- `V5__index_performance.sql` : index des rapports et de l'historique (ventes par statut et date, journal, mouvements).
- `V6__nettoyage_ancien_modele_vente.sql` : une ancienne vente « un article » devient une ligne de vente, puis les
  colonnes héritées de `vente` (article, promotion, quantité, prix) et la table `statistiques_journalieres` disparaissent.
- `V7__roles_source_unique.sql` : les droits ne viennent plus que de `utilisateur_roles` ; la colonne `utilisateurs.role`
  disparaît après avoir été reportée dans `utilisateur_roles` (rôle créé au besoin s'il fait partie des quatre connus).
- `V8__cout_moyen_pondere.sql` : coût moyen pondéré des articles, coût mémorisé sur les lignes de vente (estimé pour les
  ventes déjà enregistrées) et sur les mouvements de stock, historique des prix d'achat.
- `V9__approbation_remises.sql` : responsable ayant approuvé une remise, sur les lignes de vente et de proforma.
- `V10__inventaire_physique.sql` : inventaires et leurs lignes (un seul inventaire en cours à la fois, garanti par un index
  unique sur une colonne générée), type de mouvement `STOCK_INITIAL`, lien mouvement de stock → inventaire.
- `V11__compte_fournisseur.sql` : dettes fournisseurs (une par bon reçu), règlements (paiement ou avoir imputé) et avoirs, avec
  les contraintes de montants tenues par la base.
- `V12__retours_fournisseurs.sql` : retours au fournisseur et leurs lignes, compteur `quantite_retournee` (avec CHECK) sur les
  lignes de bon, lien mouvement de stock → retour.
- `V14__roles_de_reference.sql` : crée les quatre rôles connus (ADMIN, MANAGER, GESTIONNAIRE, CAISSIER) s'ils manquent. Sur une installation
  neuve, seul ADMIN existait, et il fallait créer les trois autres à la main avant de donner un compte à un caissier.
- `V13__parametres_entreprise.sql` : informations de l'entreprise et logo (une seule ligne chacune, garantie par la base ; le
  logo dans une table à part pour ne pas être chargé avec les textes).
- Une migration déjà appliquée ne se modifie jamais : toute évolution du schéma est un nouveau fichier
  `V<n>__description.sql`.
- Une base créée à la main par `ddl-auto=update` n'est **pas** reprise automatiquement (`V3` échouerait : colonne
  déjà présente). **Sauvegardez la base**, puis choisissez la version de départ d'après son contenu :

  ```sql
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = 'gesstock_db' AND table_name = 'lignes_vente' AND column_name = 'prix_catalogue';
  -- 1 => version 3      0 => version 2
  ```

  Premier démarrage seulement : `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` et `SPRING_FLYWAY_BASELINE_VERSION=<3 ou 2>`,
  à retirer ensuite.
