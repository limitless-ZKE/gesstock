-- Approbation tracée des remises exceptionnelles : au-delà du plafond de son rôle, ou sous le coût de revient, un prix
-- négocié exige l'accord d'un responsable. On garde QUI l'a donné, ligne par ligne (NULL = aucune approbation nécessaire).
ALTER TABLE lignes_vente
    ADD COLUMN remise_approuvee_par_id BIGINT NULL,
    ADD CONSTRAINT fk_lv_approbateur FOREIGN KEY (remise_approuvee_par_id) REFERENCES utilisateurs (id);

ALTER TABLE lignes_proforma
    ADD COLUMN remise_approuvee_par_id BIGINT NULL,
    ADD CONSTRAINT fk_lp_approbateur FOREIGN KEY (remise_approuvee_par_id) REFERENCES utilisateurs (id);
