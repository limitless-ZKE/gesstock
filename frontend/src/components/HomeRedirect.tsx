import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { pageDAccueil } from "../lib/routes";

/** Page d'accueil « / » : envoie chaque utilisateur sur l'écran qui correspond à ses droits. */
export default function HomeRedirect() {
  const { user } = useAuth();
  return <Navigate to={pageDAccueil(user?.roles ?? [])} replace />;
}
