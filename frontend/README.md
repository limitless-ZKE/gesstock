# GesStock 3.0 — Frontend

React 19, TypeScript, Vite, Tailwind CSS et lucide-react ; client de l'API Spring Boot de GesStock.

## Développement

Démarrer le backend sur `http://localhost:8080` (profil `dev`, voir le README à la racine), puis, depuis ce dossier :

```sh
npm install
npm run dev
```

Ouvrir `http://localhost:5173`. (Ce mode « développement » est réservé au développeur : pour plusieurs postes, voir « Plusieurs postes sur un réseau local »
dans le README à la racine : le programme embarque alors l'interface et tout le monde ouvre la même adresse.) Le backend doit autoriser cette origine (CORS `http://localhost:5173` en développement).
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
`/statistiques`, `/entreprise` et `/admin/utilisateurs`. Le menu n'affiche que ce que le rôle de la personne connectée peut utiliser ; le serveur
refuse de toute façon le reste.

## Charte graphique

Tout part de `src/index.css` : les échelles Tailwind `slate` (neutres) et `emerald` (le vert sapin de la marque) y sont **redéfinies**, de même
que les angles (`--radius-*`) et les ombres (`--shadow-*`). Les écrans gardent leurs classes (`bg-emerald-600`, `text-slate-500`…) : changer une
teinte se fait à un seul endroit. Principes : une seule couleur de marque (l'ambre et le rouge ne servent qu'à signaler), peu d'ombres, pas de
pastille d'icône décorative, pas d'étiquette en majuscules au-dessus des titres, textes de 12 px au minimum, contrastes AA (calculés, voir le
commentaire en tête de `index.css`), chiffres de largeur fixe pour aligner les colonnes. Les composants réutilisables sont `Indicateur` (un
chiffre et son libellé) et `Marque` (le logo : trois barres de stock décroissantes). Le menu groupe les écrans (Vente, Stock, Gestion) et porte
le nom de l'entreprise renseigné dans l'écran Entreprise. La police, **Atkinson Hyperlegible Next** (conçue pour ne pas confondre 1/7 ou 0/O ; son
zéro est barré), est embarquée par `@fontsource-variable` : aucun appel à Internet. Les documents imprimés (tickets, reçus) gardent leur mise
en page d'origine, réglée pour le papier. Le graphique du tableau de bord applique les règles de lisibilité des graphiques : colonnes fines, un seul ton, valeur sur le jour en
cours et le meilleur jour seulement, info-bulle au survol et au clavier, tableau de secours.

## États par période : historique des ventes et journal de caisse

`SelecteurPeriode` (aujourd'hui, hier, ce mois, deux dates ; refuse une fin avant le début ou dans le futur) et `BoutonsExport` (Excel et PDF préparés par
le serveur, enregistrés par `telecharger()`) sont partagés par `/statistiques`, `/historique-ventes` et l'onglet « Journal de caisse » de
`/caisse/mouvements`. Cet onglet montre `JournalCaisse`, qui choisit selon le rôle : un caissier voit `JournalCaissierPeriode` (son propre journal, sans
fond de caisse ni solde du tiroir ; le serveur lui impose son nom) ; `ADMIN`, `MANAGER` et `GESTIONNAIRE` voient `JournalCaissePeriode` (journal complet) avec une liste
« Caissier » qui bascule sur le journal d'un caissier en gardant la période choisie. Dans la caisse, `ChampClient` remplace la liste déroulante des clients par une recherche
(nom ou téléphone, sans accents ni majuscules ; clavier compris) et `QuantiteSaisie` permet de taper la quantité d'une ligne du panier. Les dates sont celles du poste (`lib/dates.ts`), jamais `toISOString()`, qui donne « hier » juste
après minuit dans un fuseau en avance sur UTC. Un téléchargement qui échoue renvoie son erreur dans un blob : `lireErreurDeFichier()` le relit
pour afficher le message du serveur. L'historique charge des lignes légères (le ticket complet se relit au clic sur « Reçu »), pagine par 50 et
refuse de répondre à une réponse tardive d'une ancienne période.

## Documents imprimables : en-tête de l'entreprise

Nom, coordonnées et logo viennent de l'écran **Entreprise** (ADMIN et MANAGER), jamais du code. `EntrepriseProvider` les charge après
la connexion ; `EnTeteDocument` les affiche : variante `ticket` (colonne centrée, papier thermique 80 mm) ou `a4` (logo à gauche,
coordonnées à droite). Chaque variante demande au serveur le logo à sa taille (`taille=ticket` ou `taille=entete`) et le borne en
millimètres à l'impression. `imprimer()` (`lib/impression.ts`) attend le chargement du logo avant d'ouvrir la boîte d'impression, sans
jamais bloquer plus de 3 secondes. Pour un nouveau document (facture, bon de livraison) : poser `<EnTeteDocument variante="a4" />` en tête
et imprimer avec `imprimer(zone)`.

## Choix volontairement différés

Pas de bibliothèque de cache de données (TanStack Query) : chaque page charge ses données avec `useEffect` + axios. Pas de saut
de version majeure des dépendances (Vite, TypeScript, plugin React, lucide) tant qu'aucune raison ne le demande.
