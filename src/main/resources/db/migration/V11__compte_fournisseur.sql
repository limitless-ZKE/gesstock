-- Compte fournisseur : une dette par bon reçu, des règlements (paiement ou avoir imputé), des avoirs.
--
-- Les bons reçus AVANT cette migration ne génèrent aucune dette : on ignore ce qui a déjà été payé, et inventer des dettes
-- sur l'historique donnerait des soldes faux. Le compte démarre à zéro, avec le premier bon reçu après la migration.

CREATE TABLE dettes_fournisseurs (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    date_creation         DATE          NOT NULL,
    montant_initial       DECIMAL(19,4) NOT NULL,
    montant_regle         DECIMAL(19,4) NOT NULL,
    statut                ENUM('ANNULEE','EN_COURS','PARTIELLEMENT_REGLEE','REGLEE') NOT NULL,
    fournisseur_id        BIGINT        NOT NULL,
    approvisionnement_id  BIGINT        NOT NULL,
    version               BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dette_fournisseur_bon (approvisionnement_id),
    KEY idx_dette_fournisseur_compte (fournisseur_id, statut, date_creation),
    CONSTRAINT ck_dette_fournisseur_montants CHECK (montant_initial > 0 AND montant_regle >= 0 AND montant_regle <= montant_initial),
    CONSTRAINT fk_dette_fournisseur_fournisseur FOREIGN KEY (fournisseur_id)       REFERENCES fournisseurs (id),
    CONSTRAINT fk_dette_fournisseur_bon         FOREIGN KEY (approvisionnement_id) REFERENCES approvisionnements (id)
) ENGINE = InnoDB;

CREATE TABLE avoirs_fournisseurs (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    fournisseur_id  BIGINT        NOT NULL,
    montant         DECIMAL(19,4) NOT NULL,
    montant_utilise DECIMAL(19,4) NOT NULL,
    date_creation   DATETIME(6)   NOT NULL,
    motif           VARCHAR(300)  NOT NULL,
    cree_par_id     BIGINT        NOT NULL,
    version         BIGINT        NOT NULL,
    PRIMARY KEY (id),
    KEY idx_avoir_fournisseur_compte (fournisseur_id, date_creation),
    CONSTRAINT ck_avoir_fournisseur_montants CHECK (montant > 0 AND montant_utilise >= 0 AND montant_utilise <= montant),
    CONSTRAINT fk_avoir_fournisseur_fournisseur FOREIGN KEY (fournisseur_id) REFERENCES fournisseurs (id),
    CONSTRAINT fk_avoir_fournisseur_cree_par    FOREIGN KEY (cree_par_id)    REFERENCES utilisateurs (id)
) ENGINE = InnoDB;

CREATE TABLE reglements_fournisseurs (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    dette_id            BIGINT        NOT NULL,
    fournisseur_id      BIGINT        NOT NULL,
    montant             DECIMAL(19,4) NOT NULL,
    source              ENUM('AVOIR','PAIEMENT') NOT NULL,
    mode_reglement      ENUM('AUTRE','CARTE','CHEQUE','ESPECES','MOBILE_MONEY','VIREMENT') NULL,
    reference           VARCHAR(80)   NULL,
    date_reglement      DATETIME(6)   NOT NULL,
    avoir_id            BIGINT        NULL,
    mouvement_caisse_id BIGINT        NULL,
    effectue_par_id     BIGINT        NULL,
    PRIMARY KEY (id),
    KEY idx_reglement_fournisseur_compte (fournisseur_id, date_reglement),
    KEY idx_reglement_fournisseur_dette (dette_id),
    CONSTRAINT ck_reglement_fournisseur_montant CHECK (montant > 0),
    -- Un avoir imputé n'a ni mode de règlement ni sortie de caisse ; un paiement a un mode et pas d'avoir.
    CONSTRAINT ck_reglement_fournisseur_source CHECK (
        (source = 'AVOIR'    AND avoir_id IS NOT NULL AND mode_reglement IS NULL AND mouvement_caisse_id IS NULL)
        OR (source = 'PAIEMENT' AND avoir_id IS NULL AND mode_reglement IS NOT NULL)),
    CONSTRAINT fk_reglement_fournisseur_dette        FOREIGN KEY (dette_id)            REFERENCES dettes_fournisseurs (id),
    CONSTRAINT fk_reglement_fournisseur_fournisseur  FOREIGN KEY (fournisseur_id)      REFERENCES fournisseurs (id),
    CONSTRAINT fk_reglement_fournisseur_avoir        FOREIGN KEY (avoir_id)            REFERENCES avoirs_fournisseurs (id),
    CONSTRAINT fk_reglement_fournisseur_mouvement    FOREIGN KEY (mouvement_caisse_id) REFERENCES mouvements_caisse (id),
    CONSTRAINT fk_reglement_fournisseur_effectue_par FOREIGN KEY (effectue_par_id)     REFERENCES utilisateurs (id)
) ENGINE = InnoDB;
