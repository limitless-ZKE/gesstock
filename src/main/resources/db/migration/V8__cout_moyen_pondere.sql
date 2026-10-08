-- Coût moyen pondéré (CMP) : le coût d'un article n'est plus « le dernier prix saisi ».
-- article.prix_achat reste le DERNIER prix d'achat (information) ; article.cmp sert aux marges et à la valeur du stock.
ALTER TABLE article ADD COLUMN cmp DECIMAL(19,4) NOT NULL DEFAULT 0;
UPDATE article SET cmp = prix_achat;                       -- meilleur point de départ connu

-- Chaque mouvement de stock mémorise le coût unitaire auquel il valorise le stock.
ALTER TABLE mouvements_stock ADD COLUMN cout_unitaire DECIMAL(19,4) NULL;
UPDATE mouvements_stock m JOIN article a ON a.id = m.article_id SET m.cout_unitaire = a.prix_achat;

-- Chaque ligne de vente mémorise le coût du moment : la marge d'une vente passée ne bouge plus jamais.
-- cout_estime = 1 pour les lignes antérieures à cette migration, dont le vrai coût historique n'est pas connu
-- (on retient le prix d'achat courant, comme le faisaient les rapports).
ALTER TABLE lignes_vente
    ADD COLUMN cout_unitaire DECIMAL(19,4) NULL,
    ADD COLUMN cout_estime   BIT NOT NULL DEFAULT 0;
UPDATE lignes_vente l
    JOIN article a ON a.id = l.article_id
    LEFT JOIN unites_vente u ON u.id = l.unite_vente_id
SET l.cout_unitaire = a.prix_achat * COALESCE(u.facteur_conversion, 1),
    l.cout_estime   = 1;

-- Journal immuable des changements de prix d'achat et de CMP (une ligne par ligne de réception).
CREATE TABLE historique_prix_achat (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    article_id            BIGINT         NOT NULL,
    date_effet            DATETIME(6)    NOT NULL,
    ancien_prix           DECIMAL(19,4)  NOT NULL,
    nouveau_prix          DECIMAL(19,4)  NOT NULL,
    ancien_cmp            DECIMAL(19,4)  NOT NULL,
    nouveau_cmp           DECIMAL(19,4)  NOT NULL,
    stock_avant           INT            NOT NULL,
    quantite_recue        INT            NOT NULL,
    approvisionnement_id  BIGINT         NULL,
    utilisateur_id        BIGINT         NULL,
    PRIMARY KEY (id),
    KEY idx_hpa_article_date (article_id, date_effet),
    CONSTRAINT fk_hpa_article FOREIGN KEY (article_id)           REFERENCES article (id),
    CONSTRAINT fk_hpa_appro   FOREIGN KEY (approvisionnement_id) REFERENCES approvisionnements (id),
    CONSTRAINT fk_hpa_user    FOREIGN KEY (utilisateur_id)       REFERENCES utilisateurs (id)
) ENGINE = InnoDB;
