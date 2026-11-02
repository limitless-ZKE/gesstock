-- Quantités à virgule (demande du client, novembre 2026) : le stock et les quantités vendues, reçues, rendues ou comptées
-- passent d'entiers à DECIMAL(15,3) (3 chiffres après la virgule : une caisse entamée, un liquide au litre).
-- Les montants des lignes de vente sont désormais calculés une fois par le serveur et stockés (montant_ligne) : avec des
-- quantités décimales et les remises faites sur le total d'une ligne, ils ne se recalculent plus exactement à la volée.
--
-- Les anciennes contraintes CHECK sur ces colonnes (« quantite >= 1 » interdirait 0,5) sont retirées puis reposées avec un
-- nom. Celles de V1 ont été créées sans nom (article_chk_1…) : on les retrouve par leur texte dans information_schema.

-- article.quantite (stock) -------------------------------------------------------------------------------------------------
SET @sql = (SELECT IFNULL(CONCAT('ALTER TABLE article ', GROUP_CONCAT(CONCAT('DROP CHECK `', tc.CONSTRAINT_NAME, '`') SEPARATOR ', ')), 'DO 0')
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'article' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%`quantite`%');
PREPARE instruction FROM @sql;
EXECUTE instruction;
DEALLOCATE PREPARE instruction;
ALTER TABLE article
    MODIFY quantite DECIMAL(15,3) NOT NULL,
    ADD CONSTRAINT ck_article_quantite CHECK (quantite >= 0);

-- ligne_approvisionnements.quantite et quantite_retournee ------------------------------------------------------------------
SET @sql = (SELECT IFNULL(CONCAT('ALTER TABLE ligne_approvisionnements ', GROUP_CONCAT(CONCAT('DROP CHECK `', tc.CONSTRAINT_NAME, '`') SEPARATOR ', ')), 'DO 0')
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'ligne_approvisionnements' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND (cc.CHECK_CLAUSE LIKE '%`quantite`%' OR cc.CHECK_CLAUSE LIKE '%`quantite_retournee`%'));
PREPARE instruction FROM @sql;
EXECUTE instruction;
DEALLOCATE PREPARE instruction;
ALTER TABLE ligne_approvisionnements
    MODIFY quantite DECIMAL(15,3) NOT NULL,
    MODIFY quantite_retournee DECIMAL(15,3) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_ligne_appro_quantite CHECK (quantite > 0),
    ADD CONSTRAINT ck_ligne_appro_retournee CHECK (quantite_retournee >= 0 AND quantite_retournee <= quantite);

-- lignes_proforma.quantite -------------------------------------------------------------------------------------------------
SET @sql = (SELECT IFNULL(CONCAT('ALTER TABLE lignes_proforma ', GROUP_CONCAT(CONCAT('DROP CHECK `', tc.CONSTRAINT_NAME, '`') SEPARATOR ', ')), 'DO 0')
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'lignes_proforma' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%`quantite`%');
PREPARE instruction FROM @sql;
EXECUTE instruction;
DEALLOCATE PREPARE instruction;
ALTER TABLE lignes_proforma
    MODIFY quantite DECIMAL(15,3) NOT NULL,
    ADD CONSTRAINT ck_ligne_proforma_quantite CHECK (quantite > 0);

-- lignes_vente.quantite, et le montant net de chaque ligne -----------------------------------------------------------------
SET @sql = (SELECT IFNULL(CONCAT('ALTER TABLE lignes_vente ', GROUP_CONCAT(CONCAT('DROP CHECK `', tc.CONSTRAINT_NAME, '`') SEPARATOR ', ')), 'DO 0')
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'lignes_vente' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%`quantite`%');
PREPARE instruction FROM @sql;
EXECUTE instruction;
DEALLOCATE PREPARE instruction;
ALTER TABLE lignes_vente
    MODIFY quantite DECIMAL(15,3) NOT NULL,
    ADD COLUMN montant_ligne DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_ligne_vente_quantite CHECK (quantite > 0);
-- Les lignes existantes gardent exactement le montant qu'elles avaient : (prix appliqué - promotion) × quantité.
UPDATE lignes_vente
SET montant_ligne = ROUND((COALESCE(prix_unitaire, 0) - COALESCE(remise_unitaire, 0)) * quantite, 4);

-- mouvements_stock : quantité, stock avant et après ------------------------------------------------------------------------
SET @sql = (SELECT IFNULL(CONCAT('ALTER TABLE mouvements_stock ', GROUP_CONCAT(CONCAT('DROP CHECK `', tc.CONSTRAINT_NAME, '`') SEPARATOR ', ')), 'DO 0')
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'mouvements_stock' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND (cc.CHECK_CLAUSE LIKE '%`quantite`%' OR cc.CHECK_CLAUSE LIKE '%`stock_avant`%' OR cc.CHECK_CLAUSE LIKE '%`stock_apres`%'));
PREPARE instruction FROM @sql;
EXECUTE instruction;
DEALLOCATE PREPARE instruction;
ALTER TABLE mouvements_stock
    MODIFY quantite DECIMAL(15,3) NOT NULL,
    MODIFY stock_avant DECIMAL(15,3) NOT NULL,
    MODIFY stock_apres DECIMAL(15,3) NOT NULL,
    ADD CONSTRAINT ck_mouvement_quantite CHECK (quantite > 0),
    ADD CONSTRAINT ck_mouvement_stock_avant CHECK (stock_avant >= 0),
    ADD CONSTRAINT ck_mouvement_stock_apres CHECK (stock_apres >= 0);

-- lignes_inventaire : stock théorique, compté, écart appliqué --------------------------------------------------------------
ALTER TABLE lignes_inventaire DROP CHECK ck_ligne_inventaire_compte;
ALTER TABLE lignes_inventaire
    MODIFY stock_theorique DECIMAL(15,3) NOT NULL,
    MODIFY stock_compte DECIMAL(15,3) NULL,
    MODIFY ecart_applique DECIMAL(15,3) NULL,
    ADD CONSTRAINT ck_ligne_inventaire_compte CHECK (stock_compte IS NULL OR stock_compte >= 0);

-- lignes_retour_fournisseur.quantite ---------------------------------------------------------------------------------------
ALTER TABLE lignes_retour_fournisseur DROP CHECK ck_ligne_retour_quantite;
ALTER TABLE lignes_retour_fournisseur
    MODIFY quantite DECIMAL(15,3) NOT NULL,
    ADD CONSTRAINT ck_ligne_retour_quantite CHECK (quantite > 0);

-- historique_prix_achat : stock avant la réception et quantité reçue -------------------------------------------------------
ALTER TABLE historique_prix_achat
    MODIFY stock_avant DECIMAL(15,3) NOT NULL,
    MODIFY quantite_recue DECIMAL(15,3) NOT NULL;
