# GesStock

Gestion des stocks, ventes, caisse, approvisionnements et dettes clients.
Backend Spring Boot 4 / Java 17 / MySQL, frontend React (dossier `frontend/`).

## Développement

```sh
./mvnw spring-boot:run          # MySQL local gesstock_db, données de démonstration (DataSeeder)
cd frontend && npm install && npm run dev
```

- Le schéma suit les entités (`ddl-auto=update`), Flyway est désactivé.
- Comptes de démonstration : `admin`, `caissier`, `gestionnaire` (profil `!prod` uniquement).
- Les tests tournent sur H2 en mémoire : `./mvnw test`.

## Authentification

Session par cookie (`JSESSIONID`) + protection CSRF : le backend écrit le cookie `XSRF-TOKEN`,
le frontend le renvoie dans l'en-tête `X-XSRF-TOKEN` pour toute requête d'écriture.

## Production (profil `prod`)

```sh
export MYSQL_PASSWORD=... MYSQL_ROOT_PASSWORD=...
export APP_ADMIN_USERNAME=admin APP_ADMIN_PASSWORD='12 caractères minimum'
export CORS_ALLOWED_ORIGINS=https://gesstock.exemple.com
docker compose up -d --build
```

| Variable | Rôle |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Connexion MySQL |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` | Premier administrateur, créé seulement si aucun utilisateur n'existe |
| `CORS_ALLOWED_ORIGINS` | Origines du frontend, séparées par des virgules |
| `JPA_DDL_AUTO` | `validate` par défaut ; le schéma est géré par Flyway |

### Migrations (Flyway, `src/main/resources/db/migration`)

- `V1__schema_initial.sql` : schéma complet pour une base vide.
- `V2__montants_en_decimal.sql` : conversion des montants `DOUBLE` en `DECIMAL(19,4)`.
- Une base existante créée avec `ddl-auto=update` est reprise automatiquement : Flyway la marque en
  version 1 puis applique V2. **Sauvegardez la base avant la première mise en production.**
- Toute évolution du schéma doit désormais passer par un nouveau fichier `V<n>__description.sql`.
