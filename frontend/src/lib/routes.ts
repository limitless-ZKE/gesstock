const ROLES_PILOTAGE = ["ADMIN", "MANAGER", "GESTIONNAIRE"];

/** Normalise un rôle venu du serveur (« ROLE_ADMIN » ou « admin ») en « ADMIN ». */
export const normaliserRole = (role: string): string => role.trim().toUpperCase().replace(/^ROLE_/, "");

/**
 * Écran d'accueil selon les droits. Le tableau de bord appelle des statistiques interdites au caissier : l'y envoyer
 * à la connexion lui afficherait immédiatement une erreur « droits insuffisants » avant même d'avoir travaillé.
 */
export function pageDAccueil(roles: string[]): string {
  const normalises = roles.map(normaliserRole);
  if (normalises.some((role) => ROLES_PILOTAGE.includes(role))) return "/dashboard";
  if (normalises.includes("CAISSIER")) return "/caisse";
  return "/dashboard";
}
