-- Les quatre rôles que connaît l'application (ADMIN, MANAGER, GESTIONNAIRE, CAISSIER) sont des données de RÉFÉRENCE : les droits de
-- chaque écran en dépendent. Sur une installation neuve (base vide, profil prod), seul ADMIN était créé, au démarrage, pour le
-- premier administrateur : il fallait alors créer les trois autres à la main avant de pouvoir donner un compte à un caissier.
-- Idempotent : une base qui a déjà un rôle ne le voit pas dupliqué (la colonne name est unique).

INSERT INTO roles (name) SELECT 'ADMIN'        FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');
INSERT INTO roles (name) SELECT 'MANAGER'      FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'MANAGER');
INSERT INTO roles (name) SELECT 'GESTIONNAIRE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'GESTIONNAIRE');
INSERT INTO roles (name) SELECT 'CAISSIER'     FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CAISSIER');
