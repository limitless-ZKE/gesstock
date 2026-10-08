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

- Profil `dev` : MySQL local `gesstock_db` (root, sans mot de passe), le schéma suit les entités
  (`ddl-auto=update`), Flyway est désactivé, Swagger UI (`/swagger-ui.html`, réservé aux ADMIN connectés),
  cookies non sécurisés (HTTP local).
- Comptes de démonstration (profil `dev` uniquement) : `admin`, `caissier`, `gestionnaire`.
- Sonde de santé : `http://127.0.0.1:8081/actuator/health` (port de supervision, jamais exposé au réseau).
- Les tests tournent sur H2 en mémoire : `./mvnw test`.

## Authentification et droits

API pure : aucune page HTML n'est servie par le backend. Session par cookie (`JSESSIONID`) + protection CSRF :
le backend écrit le cookie `XSRF-TOKEN`, le frontend le renvoie dans l'en-tête `X-XSRF-TOKEN` pour toute requête
d'écriture.

| Appel | Résultat |
|---|---|
| `POST /api/auth/login` (formulaire `username`, `password`) | `204` ; sinon `401` `IDENTIFIANTS_INVALIDES`, `429` `TROP_DE_TENTATIVES` |
| `GET /api/auth/me` | `200` `{ username, roles }` ou `401` |
| `POST /api/auth/logout` | `204` |

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

Le schéma est géré par Flyway ; Hibernate se contente de le valider. Swagger est désactivé.
Le contrôle de santé de Docker interroge `http://127.0.0.1:8081/actuator/health/readiness` dans le conteneur.

### Migrations (Flyway, `src/main/resources/db/migration`)

- `V1__schema_initial.sql` : schéma complet pour une base vide.
- `V2__montants_en_decimal.sql` : conversion des montants `DOUBLE` en `DECIMAL(19,4)`.
- `V3__prix_catalogue_lignes.sql` : prix catalogue mémorisé sur les lignes de vente et de proforma.
- `V4__fusion_roles_vendeur_user.sql` : fusion des rôles `VENDEUR` et `USER` dans `CAISSIER`.
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
