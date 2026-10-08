-- Retire les restes de l'ancien modèle « une vente = un article » et une table jamais utilisée.

-- 1) Conserver l'historique : une ancienne vente à un seul article devient une ligne de vente.
INSERT INTO lignes_vente (vente_id, article_id, quantite, prix_unitaire, remise_unitaire)
SELECT v.id, v.article_id, v.quantite, v.prix_unit, 0
FROM vente v
WHERE v.article_id IS NOT NULL
  AND v.quantite > 0
  AND NOT EXISTS (SELECT 1 FROM lignes_vente l WHERE l.vente_id = v.id);

-- 2) Supprimer les colonnes de l'ancien modèle (les clés étrangères d'abord).
ALTER TABLE vente DROP FOREIGN KEY FKbfsd94pqg2nn8ob777eoyvdtf; -- article_id
ALTER TABLE vente DROP FOREIGN KEY FKqycqr18dqnk7lag7g5w9yjy60; -- promotion_id
ALTER TABLE vente
    DROP COLUMN article_id,
    DROP COLUMN promotion_id,
    DROP COLUMN quantite,
    DROP COLUMN prix_unit,
    DROP COLUMN prix_total;

-- 3) Table sans aucune entité ni requête depuis la refonte.
DROP TABLE IF EXISTS statistiques_journalieres;
