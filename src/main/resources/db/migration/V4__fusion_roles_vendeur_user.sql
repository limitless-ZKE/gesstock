-- L'application ne reconnaît plus que quatre rôles : ADMIN, MANAGER, GESTIONNAIRE, CAISSIER.
-- Les anciens rôles VENDEUR et USER sont fusionnés dans CAISSIER : sans cela, leurs comptes n'auraient plus aucun droit.
-- Migration sans effet sur une base qui n'a jamais eu ces rôles.

-- 1. Le rôle CAISSIER existe s'il faut y reverser des comptes.
INSERT INTO roles (name)
SELECT 'CAISSIER' FROM DUAL
WHERE EXISTS (SELECT 1 FROM roles WHERE UPPER(name) IN ('VENDEUR', 'USER'))
  AND NOT EXISTS (SELECT 1 FROM roles WHERE UPPER(name) = 'CAISSIER');

-- 2. Chaque compte qui avait VENDEUR ou USER reçoit CAISSIER (une seule fois, même s'il avait les deux).
INSERT INTO utilisateur_roles (role_id, utilisateur_id)
SELECT DISTINCT caissier.id, ancien.utilisateur_id
FROM utilisateur_roles ancien
JOIN roles r ON r.id = ancien.role_id AND UPPER(r.name) IN ('VENDEUR', 'USER')
JOIN roles caissier ON UPPER(caissier.name) = 'CAISSIER'
WHERE NOT EXISTS (
    SELECT 1 FROM utilisateur_roles deja
    WHERE deja.utilisateur_id = ancien.utilisateur_id AND deja.role_id = caissier.id);

-- 3. Les anciens rôles disparaissent, avec leurs attributions.
DELETE FROM utilisateur_roles
WHERE role_id IN (SELECT id FROM roles WHERE UPPER(name) IN ('VENDEUR', 'USER'));
DELETE FROM roles WHERE UPPER(name) IN ('VENDEUR', 'USER');

-- 4. L'ancien champ unique « role » (relu pour les comptes sans rôle attribué) suit le mouvement.
UPDATE utilisateurs SET role = 'CAISSIER'
WHERE UPPER(role) IN ('VENDEUR', 'USER', 'ROLE_VENDEUR', 'ROLE_USER');
