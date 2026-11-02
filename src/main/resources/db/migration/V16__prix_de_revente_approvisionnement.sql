-- Prix de revente saisi sur une ligne de bon d'approvisionnement (demande du client, novembre 2026) : il devient le prix de
-- vente de l'article à la réception du bon. NULL = le prix de vente de l'article ne change pas.
ALTER TABLE ligne_approvisionnements
    ADD COLUMN prix_vente DECIMAL(19,4) NULL,
    ADD CONSTRAINT ck_ligne_appro_prix_vente CHECK (prix_vente IS NULL OR prix_vente >= 0);
