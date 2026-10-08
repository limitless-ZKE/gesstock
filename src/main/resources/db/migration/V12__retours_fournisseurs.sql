-- Retours de marchandise au fournisseur : un retour part d'un bon reçu, sort le stock à sa validation et crée un avoir.

-- Unités déjà rendues, par ligne de bon. Le CHECK est le garde-fou de fond : même deux validations simultanées ne peuvent
-- pas rendre plus que ce qui a été reçu (l'application incrémente par un UPDATE conditionnel).
ALTER TABLE ligne_approvisionnements
    ADD COLUMN quantite_retournee INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_ligne_appro_retournee CHECK (quantite_retournee >= 0 AND quantite_retournee <= quantite);

CREATE TABLE retours_fournisseurs (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    numero                VARCHAR(40)   NOT NULL,
    statut                ENUM('ANNULE','BROUILLON','VALIDE') NOT NULL,
    fournisseur_id        BIGINT        NOT NULL,
    approvisionnement_id  BIGINT        NOT NULL,
    motif                 VARCHAR(300)  NOT NULL,
    date_creation         DATETIME(6)   NOT NULL,
    cree_par_id           BIGINT        NOT NULL,
    date_validation       DATETIME(6)   NULL,
    valide_par_id         BIGINT        NULL,
    montant_total         DECIMAL(19,4) NOT NULL,
    avoir_id              BIGINT        NULL,
    version               BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_retour_fournisseur_numero (numero),
    UNIQUE KEY uk_retour_fournisseur_avoir (avoir_id),
    KEY idx_retour_fournisseur_bon (approvisionnement_id),
    CONSTRAINT ck_retour_fournisseur_montant CHECK (montant_total >= 0),
    -- un retour validé a toujours sa date et son validateur
    CONSTRAINT ck_retour_fournisseur_validation CHECK (statut <> 'VALIDE' OR (date_validation IS NOT NULL AND valide_par_id IS NOT NULL)),
    CONSTRAINT fk_retour_fournisseur_fournisseur FOREIGN KEY (fournisseur_id)       REFERENCES fournisseurs (id),
    CONSTRAINT fk_retour_fournisseur_bon         FOREIGN KEY (approvisionnement_id) REFERENCES approvisionnements (id),
    CONSTRAINT fk_retour_fournisseur_cree_par    FOREIGN KEY (cree_par_id)          REFERENCES utilisateurs (id),
    CONSTRAINT fk_retour_fournisseur_valide_par  FOREIGN KEY (valide_par_id)        REFERENCES utilisateurs (id),
    CONSTRAINT fk_retour_fournisseur_avoir       FOREIGN KEY (avoir_id)             REFERENCES avoirs_fournisseurs (id)
) ENGINE = InnoDB;

CREATE TABLE lignes_retour_fournisseur (
    id                          BIGINT        NOT NULL AUTO_INCREMENT,
    retour_id                   BIGINT        NOT NULL,
    ligne_approvisionnement_id  BIGINT        NOT NULL,
    article_id                  BIGINT        NOT NULL,
    quantite                    INT           NOT NULL,
    cout_unitaire               DECIMAL(19,4) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ligne_retour_ligne_bon (retour_id, ligne_approvisionnement_id),
    CONSTRAINT ck_ligne_retour_quantite CHECK (quantite >= 1),
    CONSTRAINT ck_ligne_retour_cout CHECK (cout_unitaire >= 0),
    CONSTRAINT fk_ligne_retour_retour  FOREIGN KEY (retour_id)                  REFERENCES retours_fournisseurs (id),
    CONSTRAINT fk_ligne_retour_ligne   FOREIGN KEY (ligne_approvisionnement_id) REFERENCES ligne_approvisionnements (id),
    CONSTRAINT fk_ligne_retour_article FOREIGN KEY (article_id)                 REFERENCES article (id)
) ENGINE = InnoDB;

ALTER TABLE mouvements_stock
    ADD COLUMN retour_fournisseur_id BIGINT NULL,
    ADD CONSTRAINT fk_mvt_retour_fournisseur FOREIGN KEY (retour_fournisseur_id) REFERENCES retours_fournisseurs (id);
