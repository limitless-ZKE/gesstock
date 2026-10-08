-- Les droits ne viennent plus que de utilisateur_roles : l'ancienne colonne utilisateurs.role disparaît.

-- 1) Un compte qui n'avait QUE l'ancienne colonne reçoit le rôle correspondant avant sa suppression.
--    Le rôle est créé s'il manque et s'il fait partie des quatre rôles connus de l'application.
INSERT INTO roles (name)
SELECT DISTINCT UPPER(REPLACE(u.role, 'ROLE_', ''))
FROM utilisateurs u
WHERE NOT EXISTS (SELECT 1 FROM utilisateur_roles ur WHERE ur.utilisateur_id = u.id)
  AND UPPER(REPLACE(u.role, 'ROLE_', '')) IN ('ADMIN', 'MANAGER', 'GESTIONNAIRE', 'CAISSIER')
  AND NOT EXISTS (SELECT 1 FROM roles r WHERE UPPER(REPLACE(r.name, 'ROLE_', '')) = UPPER(REPLACE(u.role, 'ROLE_', '')));

INSERT INTO utilisateur_roles (utilisateur_id, role_id)
SELECT u.id, r.id
FROM utilisateurs u
JOIN roles r ON UPPER(REPLACE(r.name, 'ROLE_', '')) = UPPER(REPLACE(u.role, 'ROLE_', ''))
WHERE NOT EXISTS (SELECT 1 FROM utilisateur_roles ur WHERE ur.utilisateur_id = u.id);

-- 2) Un compte dont l'ancien rôle n'était pas l'un des quatre n'a plus aucun droit (il n'en avait déjà aucun :
--    tout ce qui n'est pas déclaré est refusé) et ne peut plus se connecter tant qu'un rôle ne lui est pas attribué.
ALTER TABLE utilisateurs DROP COLUMN role;
