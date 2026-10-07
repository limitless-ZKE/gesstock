-- Prix catalogue mémorisé sur chaque ligne de vente et de proforma, pour afficher la remise négociée
-- (prix catalogue - prix négocié) sur les reçus et les duplicata. NULL sur les lignes antérieures.
ALTER TABLE lignes_vente ADD COLUMN prix_catalogue DECIMAL(19,4) NULL;
ALTER TABLE lignes_proforma ADD COLUMN prix_catalogue DECIMAL(19,4) NULL;
