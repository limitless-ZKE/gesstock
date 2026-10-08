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
- Test des migrations sur un vrai MySQL (désactivé par défaut ; il crée et supprime ses propres bases jetables, en
  `sql_mode` strict) :

  ```sh
  GESSTOCK_TEST_MYSQL=localhost:3306 GESSTOCK_TEST_MYSQL_USER=root GESSTOCK_TEST_MYSQL_PASSWORD= \
    ./mvnw test -Dtest=MigrationsMysqlIT
  ```

  Il vérifie l'installation neuve (V1 à V9 puis validation Hibernate, y compris les valeurs d'enum MySQL que Hibernate
  ne contrôle pas), la mise à jour depuis V3 avec des données, et la reprise d'une base existante. La CI le lance
  sur MySQL 8.4.

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
- **Coût du stock** : le coût d'un article est son coût moyen pondéré (CMP), recalculé à chaque réception ; chaque ligne de
  vente mémorise le coût du moment, donc la marge d'une vente passée ne bouge plus. `historique_prix_achat` garde l'évolution.
- **TVA** : le taux est imposé par le serveur (`APP_TVA_TAUX`, 0 par défaut : aucune TVA), jamais saisi à la caisse
  (un autre taux envoyé est refusé). Pour l'activer sans migration : `APP_TVA_TAUX=18`, et `APP_TVA_PRIX_TTC=true` si les
  prix du catalogue sont TTC (la taxe en est extraite : le client paie le prix affiché) ou `false` s'ils sont HT (la taxe
  s'ajoute). La caisse lit le régime sur `GET /api/caisse/parametres`. Les proformas n'appliquent pas de TVA : à traiter
  le jour où la TVA est activée.

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
