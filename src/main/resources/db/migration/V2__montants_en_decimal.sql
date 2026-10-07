-- Les montants étaient stockés en DOUBLE (erreurs d'arrondi). On les convertit en DECIMAL(19,4).
-- Sur une base neuve, V1 crée déjà ces colonnes en DECIMAL : ces instructions n'ont alors aucun effet.
ALTER TABLE article MODIFY prix DECIMAL(19,4) NOT NULL;
ALTER TABLE ligne_approvisionnements MODIFY prix_unitaire DECIMAL(19,4) NOT NULL;
ALTER TABLE lignes_vente MODIFY prix_unitaire DECIMAL(19,4) NOT NULL;
ALTER TABLE vente MODIFY prix_unit DECIMAL(19,4) NOT NULL;
ALTER TABLE vente MODIFY montant_total DECIMAL(19,4) NULL;
ALTER TABLE vente MODIFY prix_total DECIMAL(19,4) NULL;
