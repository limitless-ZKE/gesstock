-- Articles chargés avec un prix d'achat à 0 puis tarifés à la main dans leur fiche : le coût moyen (cmp), qui sert aux marges et à
-- la valeur du stock, était resté à 0 et le bénéfice par article égalait le chiffre d'affaires.
-- Désormais la fiche aligne le coût sur le prix d'achat saisi ; cette migration rattrape ce qui existe déjà.

-- 1) Le stock en rayon prend le prix d'achat enregistré sur l'article. Les articles dont le coût a déjà évolué (bons d'approvisionnement)
--    ne sont pas touchés : leur coût n'est pas à 0.
UPDATE article SET cmp = prix_achat WHERE cmp = 0 AND prix_achat > 0;

-- 2) Les ventes passées valorisées à 0 prennent le prix d'achat de l'article et sont marquées « coût estimé » (le vrai coût du jour
--    n'est pas connu, comme pour les lignes antérieures à V8). Une ligne qui porte un vrai coût n'est jamais modifiée.
UPDATE lignes_vente l
    JOIN article a ON a.id = l.article_id
    LEFT JOIN unites_vente u ON u.id = l.unite_vente_id
SET l.cout_unitaire = a.prix_achat * COALESCE(u.facteur_conversion, 1),
    l.cout_estime   = 1
WHERE COALESCE(l.cout_unitaire, 0) = 0 AND a.prix_achat > 0;
