# GesStock 3.0 — Frontend

React 19, TypeScript, Vite, Tailwind CSS et lucide-react ; client de l'API Spring Boot de GesStock.

## Développement

Démarrer le backend sur `http://localhost:8080` (profil `dev`, voir le README à la racine), puis, depuis ce dossier :

```sh
npm install
npm run dev
```

Ouvrir `http://localhost:5173`. Le backend doit autoriser cette origine (CORS `http://localhost:5173` en développement).
L'authentification utilise le cookie de session Spring Security et le jeton CSRF (`XSRF-TOKEN`, renvoyé par axios dans
`X-XSRF-TOKEN`). `VITE_API_BASE_URL` change l'adresse de l'API (défaut `http://localhost:8080/api`) ; copier `.env.example`
vers `.env.local` pour la fixer.

La TVA n'est pas réglée dans le front : le serveur l'impose (`APP_TVA_TAUX`, 0 par défaut) et la caisse la lit sur
`GET /api/caisse/parametres`.

## Qualité

| Commande | Rôle |
|---|---|
| `npm run lint` | ESLint (TypeScript + règles des hooks React) |
| `npm test` | Vitest + Testing Library, API simulée (`npm run test:watch` pour le mode continu) |
| `npm run build` | Vérification des types (`tsc -b`, tests compris) puis build de production |

La CI exécute les trois. Les tests portent sur ce qui coûte cher quand ça casse : formats de montants, contrat d'erreur de l'API
et intercepteur (401, 403, 5xx), session et routes protégées, menu selon les rôles, fenêtre d'accord d'un responsable, et les
écrans de la Phase 4 (inventaires, compte fournisseur, retours) en vérifiant ce qui part vraiment vers le serveur. Ils ne
remplacent pas les tests du serveur (droits, règles métier), qui font foi : le front n'est qu'un confort.

Chaque écran est un morceau chargé à la première visite (`React.lazy`) ; React, le routeur et axios sont regroupés dans un
morceau « socle » stable, qui reste en cache d'une version à l'autre.

## Écrans

`/login`, `/dashboard`, `/caisse`, `/historique-ventes`, `/caisse/mouvements`, `/articles`, `/inventaires`,
`/approvisionnements`, `/fournisseurs` (avec le compte de chaque fournisseur), `/retours-fournisseurs`, `/clients-dettes`,
`/statistiques` et `/admin/utilisateurs`. Le menu n'affiche que ce que le rôle de la personne connectée peut utiliser ; le serveur
refuse de toute façon le reste.

## Choix volontairement différés

Pas de bibliothèque de cache de données (TanStack Query) : chaque page charge ses données avec `useEffect` + axios. Pas de saut
de version majeure des dépendances (Vite, TypeScript, plugin React, lucide) tant qu'aucune raison ne le demande.
