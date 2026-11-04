-- Demande du client (novembre 2026) : la caisse a aussi des ENTREES de fonds, et les statistiques séparent les dépenses.
--
-- 1. mouvements_caisse : type SORTIE ou ENTREE, et origine du mouvement. Seules les sorties MANUELLES sont des dépenses :
--    le paiement d'un fournisseur est déjà compté dans les achats (le bon réceptionné), et le remboursement d'un client dont la
--    vente est annulée est déjà sorti du chiffre d'affaires.
ALTER TABLE mouvements_caisse
    MODIFY type ENUM('SORTIE','ENTREE') NOT NULL,
    ADD COLUMN origine ENUM('MANUEL','REGLEMENT_FOURNISSEUR','REMBOURSEMENT_CLIENT') NOT NULL DEFAULT 'MANUEL';

-- Les mouvements existants sont classés d'après ce qu'on sait d'eux : lien vers un règlement fournisseur, ou motif écrit par
-- l'annulation d'une vente ; tout le reste est une sortie saisie à la main.
UPDATE mouvements_caisse m SET origine = 'REGLEMENT_FOURNISSEUR'
WHERE EXISTS (SELECT 1 FROM reglements_fournisseurs r WHERE r.mouvement_caisse_id = m.id);
UPDATE mouvements_caisse SET origine = 'REMBOURSEMENT_CLIENT'
WHERE origine = 'MANUEL' AND motif LIKE 'Remboursement client - annulation de la vente%';

-- 2. approvisionnements : date et heure de la réception, pour compter l'achat le jour où le bon est réceptionné (la date du
--    bon peut être saisie à la main et antérieure). Les bons déjà réceptionnés prennent la date de leur bon.
ALTER TABLE approvisionnements ADD COLUMN recu_le DATETIME(6) NULL;
UPDATE approvisionnements SET recu_le = COALESCE(CAST(date_approvisionnement AS DATETIME(6)), NOW(6)) WHERE statut = 'RECU';
