-- Informations de l'entreprise et logo, modifiables depuis l'application (plus rien d'écrit en dur dans les documents imprimés).
--
-- Une seule ligne par table (CHECK id = 1) : l'entreprise est unique, et la base le garantit.
-- Le logo est dans une table à part : lire le nom ou l'adresse ne charge jamais l'image. Il est stocké en base (donc inclus
-- dans les sauvegardes, sans dossier ni volume à configurer) ; son empreinte SHA-256 sert d'identifiant de version.

CREATE TABLE parametres_entreprise (
    id                 INT           NOT NULL,
    nom                VARCHAR(150)  NOT NULL,
    adresse            VARCHAR(300)  NULL,
    telephone          VARCHAR(40)   NULL,
    email              VARCHAR(180)  NULL,
    identifiant_legal  VARCHAR(80)   NULL,    -- RCCM, NINEA, SIRET… selon le pays
    site_web           VARCHAR(180)  NULL,
    pied_de_page       VARCHAR(200)  NULL,    -- message en bas des tickets
    logo_sur_tickets   BIT           NOT NULL DEFAULT b'1',
    logo_empreinte     VARCHAR(64)   NULL,    -- NULL = pas de logo
    logo_largeur       INT           NULL,
    logo_hauteur       INT           NULL,
    mis_a_jour_le      DATETIME(6)   NOT NULL,
    mis_a_jour_par_id  BIGINT        NULL,
    version            BIGINT        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_parametres_entreprise_unique CHECK (id = 1),
    CONSTRAINT ck_parametres_entreprise_nom CHECK (CHAR_LENGTH(TRIM(nom)) > 0),
    -- l'empreinte et les dimensions du logo vont ensemble : tous renseignés ou tous absents
    CONSTRAINT ck_parametres_entreprise_logo CHECK (
        (logo_empreinte IS NULL AND logo_largeur IS NULL AND logo_hauteur IS NULL)
        OR (logo_empreinte IS NOT NULL AND logo_largeur > 0 AND logo_hauteur > 0)),
    CONSTRAINT fk_parametres_entreprise_utilisateur FOREIGN KEY (mis_a_jour_par_id) REFERENCES utilisateurs (id)
) ENGINE = InnoDB;

CREATE TABLE logo_entreprise (
    id             INT          NOT NULL,
    contenu        LONGBLOB     NOT NULL,
    type_mime      VARCHAR(40)  NOT NULL,
    largeur        INT          NOT NULL,
    hauteur        INT          NOT NULL,
    empreinte      VARCHAR(64)  NOT NULL,
    mis_a_jour_le  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_logo_entreprise_unique CHECK (id = 1),
    CONSTRAINT ck_logo_entreprise_dimensions CHECK (largeur > 0 AND hauteur > 0)
) ENGINE = InnoDB;

-- Valeur de départ : à remplacer depuis l'écran « Entreprise ». Les tickets existants ne changent pas de ton.
INSERT INTO parametres_entreprise (id, nom, pied_de_page, mis_a_jour_le, version)
VALUES (1, 'Mon entreprise', 'Merci de votre visite !', NOW(6), 0);
