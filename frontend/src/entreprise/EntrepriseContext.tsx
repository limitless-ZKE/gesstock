import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useAuth } from "../auth/AuthContext";
import { API_BASE_URL, api } from "../services/api";

export interface Logo {
  /** Change à chaque remplacement : sert à contourner un ancien cache d'image. */
  empreinte: string;
  largeur: number;
  hauteur: number;
}

export interface Entreprise {
  nom: string;
  adresse: string | null;
  telephone: string | null;
  email: string | null;
  identifiantLegal: string | null;
  siteWeb: string | null;
  piedDePage: string | null;
  logoSurTickets: boolean;
  logo: Logo | null;
  misAJourLe: string;
  misAJourPar: string | null;
}

/** Formats du logo : le serveur adapte la taille de l'image à l'endroit où elle s'imprime. */
export type TailleLogo = "miniature" | "ticket" | "entete" | "original";

interface EntrepriseContextValue {
  /** Null tant que le chargement n'est pas terminé, ou si le serveur n'a pas répondu. */
  entreprise: Entreprise | null;
  rafraichir: () => Promise<void>;
  /** Adresse de l'image du logo au format demandé, ou null s'il n'y a pas de logo. */
  logoUrl: (taille: TailleLogo) => string | null;
}

const EntrepriseContext = createContext<EntrepriseContextValue | null>(null);

export function logoUrlDe(entreprise: Entreprise | null, taille: TailleLogo): string | null {
  if (!entreprise?.logo) return null;
  return `${API_BASE_URL}/entreprise/logo?taille=${taille}&v=${encodeURIComponent(entreprise.logo.empreinte)}`;
}

/**
 * Nom, coordonnées et logo de l'entreprise, lus une fois après la connexion et rechargés à la demande (écran Entreprise).
 * Les documents imprimables les lisent ici : rien n'est écrit en dur dans les écrans.
 */
export function EntrepriseProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const connecte = user !== null;
  const [entreprise, setEntreprise] = useState<Entreprise | null>(null);

  const rafraichir = useCallback(async () => {
    try {
      const { data } = await api.get<Entreprise>("/entreprise");
      setEntreprise(data);
    } catch {
      // Un échec ne doit jamais empêcher de vendre ni d'imprimer : les documents s'impriment alors sans en-tête personnalisé.
      setEntreprise(null);
    }
  }, []);

  useEffect(() => {
    if (connecte) void rafraichir();
    else setEntreprise(null);
  }, [connecte, rafraichir]);

  const value = useMemo(
    () => ({ entreprise, rafraichir, logoUrl: (taille: TailleLogo) => logoUrlDe(entreprise, taille) }),
    [entreprise, rafraichir],
  );
  return <EntrepriseContext.Provider value={value}>{children}</EntrepriseContext.Provider>;
}

export function useEntreprise() {
  const context = useContext(EntrepriseContext);
  if (!context) throw new Error("useEntreprise doit être utilisé dans EntrepriseProvider.");
  return context;
}
