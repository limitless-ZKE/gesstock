-- Inventaire physique : photographie du stock théorique, saisie des comptages, validation par un responsable.

-- Ajouter une valeur d'enum à mouvements_stock EST une migration : Hibernate « validate » ne la détecte pas.
-- STOCK_INITIAL trace le stock de départ saisi à la création d'un article.
ALTER TABLE mouvements_stock
    MODIFY COLUMN type ENUM('AJUSTEMENT_NEGATIF','AJUSTEMENT_POSITIF','ENTREE_APPROVISIONNEMENT','RETOUR_CLIENT',
                            'RETOUR_FOURNISSEUR','SORTIE_VENTE','STOCK_INITIAL') NOT NULL;

CREATE TABLE inventaires (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    code           VARCHAR(40)  NOT NULL,
    statut         ENUM('ANNULE','EN_SAISIE','SOUMIS','VALIDE') NOT NULL,
    perimetre      ENUM('CATEGORIE','TOUT') NOT NULL,
    categorie_id   BIGINT       NULL,
    commentaire    VARCHAR(500) NULL,
    ouvert_le      DATETIME(6)  NOT NULL,
    ouvert_par_id  BIGINT       NOT NULL,
    soumis_le      DATETIME(6)  NULL,
    valide_le      DATETIME(6)  NULL,
    valide_par_id  BIGINT       NULL,
    version        BIGINT       NOT NULL,
    -- 1 tant que l'inventaire est en cours, NULL sinon : l'index unique ci-dessous n'autorise donc qu'UN inventaire
    -- en cours à la fois, garanti par la base et non par un simple contrôle applicatif.
    en_cours       TINYINT GENERATED ALWAYS AS (IF(statut IN ('EN_SAISIE','SOUMIS'), 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventaires_code (code),
    UNIQUE KEY uk_inventaire_en_cours (en_cours),
    CONSTRAINT fk_inv_categorie  FOREIGN KEY (categorie_id)  REFERENCES categories_articles (id),
    CONSTRAINT fk_inv_ouvert_par FOREIGN KEY (ouvert_par_id) REFERENCES utilisateurs (id),
    CONSTRAINT fk_inv_valide_par FOREIGN KEY (valide_par_id) REFERENCES utilisateurs (id)
) ENGINE = InnoDB;

CREATE TABLE lignes_inventaire (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    inventaire_id    BIGINT        NOT NULL,
    article_id       BIGINT        NOT NULL,
    stock_theorique  INT           NOT NULL,
    stock_compte     INT           NULL,
    cout_unitaire    DECIMAL(19,4) NOT NULL,
    motif            ENUM('CASSE','ECART_DE_COMPTAGE','ERREUR_DE_SAISIE','PEREMPTION','VOL','AUTRE') NULL,
    commentaire      VARCHAR(500)  NULL,
    ecart_applique   INT           NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ligne_inventaire_article (inventaire_id, article_id),
    CONSTRAINT ck_ligne_inventaire_compte CHECK (stock_compte IS NULL OR stock_compte >= 0),
    CONSTRAINT fk_li_inventaire FOREIGN KEY (inventaire_id) REFERENCES inventaires (id),
    CONSTRAINT fk_li_article    FOREIGN KEY (article_id)    REFERENCES article (id)
) ENGINE = InnoDB;

ALTER TABLE mouvements_stock
    ADD COLUMN inventaire_id BIGINT NULL,
    ADD CONSTRAINT fk_mvt_inventaire FOREIGN KEY (inventaire_id) REFERENCES inventaires (id);
