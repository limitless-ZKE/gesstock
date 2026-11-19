import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type Dispatch,
  type ReactNode,
  type SetStateAction,
} from "react";
import { useAuth } from "../auth/AuthContext";
import { arrondirQuantite, formatQuantite } from "../lib/quantite";

export type Article = {
  id: number;
  reference: string;
  designation: string;
  prixVente: number;
  quantiteStock: number;
  seuilAlerteStock: number;
  categorieId: number | null;
  categorie: string | null;
  uniteMesure: string | null;
};

// prixNegocie : prix convenu avec le client par unité (texte saisi) ; vide = prix catalogue.
// totalNegocie : prix convenu pour la LIGNE entière (remise sur le total de l'article) ; il remplace le prix unitaire et la
// promotion. Les deux s'excluent : saisir l'un vide l'autre.
export type CartLine = { article: Article; quantite: number; promotionId: number | null; prixNegocie: string; totalNegocie: string };

type Contenu = { lignes: CartLine[]; clientId: string };

const VIDE: Contenu = { lignes: [], clientId: "" };

/** Un panier oublié depuis plus longtemps n'est plus rouvert : ses prix et ses stocks ne sont plus fiables. */
export const DUREE_DE_VIE_MS = 12 * 60 * 60 * 1000;

const cleDeStockage = (utilisateur: string) => `gesstock:panier:${utilisateur}`;

const estLigneValide = (ligne: unknown): ligne is CartLine => {
  if (typeof ligne !== "object" || ligne === null) return false;
  const { article, quantite, promotionId, prixNegocie, totalNegocie } = ligne as Partial<CartLine>;
  return typeof article === "object" && article !== null
    && typeof article.id === "number"
    && typeof article.designation === "string"
    && typeof article.prixVente === "number"
    && typeof article.quantiteStock === "number"
    && typeof quantite === "number" && Number.isFinite(quantite) && quantite > 0
    && (promotionId === null || typeof promotionId === "number")
    && typeof prixNegocie === "string"
    && typeof totalNegocie === "string";
};

/** Panier enregistré pour cette personne ; vide s'il n'y en a pas, s'il est trop ancien ou s'il est illisible. */
export function lirePanier(utilisateur: string, maintenant = Date.now()): Contenu {
  try {
    const brut = window.localStorage.getItem(cleDeStockage(utilisateur));
    if (!brut) return VIDE;
    const donnees = JSON.parse(brut) as { enregistreLe?: unknown; lignes?: unknown; clientId?: unknown };
    if (typeof donnees.enregistreLe !== "number" || maintenant - donnees.enregistreLe > DUREE_DE_VIE_MS) return VIDE;
    if (!Array.isArray(donnees.lignes)) return VIDE;
    return {
      lignes: donnees.lignes.filter(estLigneValide),
      clientId: typeof donnees.clientId === "string" ? donnees.clientId : "",
    };
  } catch {
    return VIDE; // stockage indisponible ou contenu abîmé : on repart d'un panier vide, jamais d'une erreur
  }
}

function ecrirePanier(utilisateur: string, contenu: Contenu) {
  try {
    if (contenu.lignes.length === 0 && contenu.clientId === "") {
      window.localStorage.removeItem(cleDeStockage(utilisateur));
    } else {
      window.localStorage.setItem(cleDeStockage(utilisateur), JSON.stringify({ ...contenu, enregistreLe: Date.now() }));
    }
  } catch {
    // Stockage plein ou interdit : le panier reste en mémoire (il survit encore au changement d'écran), sans plus.
  }
}

/**
 * Remet le panier en phase avec le catalogue actuel : un panier rouvert peut dater de quelques heures (autre caissier,
 * réception de marchandise, changement de prix). Retire ce qui n'existe plus ou est en rupture, ramène les quantités au
 * stock disponible, et signale chaque changement pour que le caissier ne le découvre pas à l'encaissement.
 */
export function rapprocherDuCatalogue(lignes: CartLine[], catalogue: Article[]): { lignes: CartLine[]; messages: string[] } {
  const parId = new Map(catalogue.map((article) => [article.id, article]));
  const messages: string[] = [];
  const resultat: CartLine[] = [];
  for (const ligne of lignes) {
    const actuel = parId.get(ligne.article.id);
    if (!actuel || actuel.quantiteStock <= 0) {
      messages.push(`« ${ligne.article.designation} » n’est plus disponible : retiré du panier.`);
      continue;
    }
    let quantite = ligne.quantite;
    if (quantite > actuel.quantiteStock) {
      quantite = arrondirQuantite(actuel.quantiteStock);
      messages.push(`Quantité de « ${actuel.designation} » ramenée à ${formatQuantite(quantite)} (stock disponible).`);
    }
    if (actuel.prixVente !== ligne.article.prixVente) {
      messages.push(`Le prix de « ${actuel.designation} » a changé : il est maintenant de ${formatQuantite(actuel.prixVente)}.`);
    }
    resultat.push({ ...ligne, article: actuel, quantite });
  }
  return { lignes: resultat, messages };
}

interface PanierContextValue {
  /** Lignes du panier de vente en cours. */
  cart: CartLine[];
  setCart: Dispatch<SetStateAction<CartLine[]>>;
  /** Client choisi pour la vente en cours (« » = client de passage). */
  clientId: string;
  setClientId: (clientId: string) => void;
  /** À appeler une fois le catalogue rechargé : corrige le panier d'après le stock et les prix du moment, renvoie ce qui a changé. */
  rapprocher: (catalogue: Article[]) => string[];
  /** Nombre d'articles en attente (somme des quantités), pour le compteur du menu. */
  nombreArticles: number;
}

const PanierContext = createContext<PanierContextValue | null>(null);

/**
 * Le panier vit ici, au-dessus des écrans : quitter la caisse pour l'historique, les clients ou les articles ne le perd plus.
 * Il est aussi gardé dans le navigateur (par identifiant de connexion) pour survivre à un rechargement de la page ou à une
 * session expirée ; la vente validée, « Vider » ou 12 heures d'oubli le font disparaître. Rien n'est envoyé au serveur
 * avant l'encaissement : le stock n'est donc réservé à personne tant que la vente n'est pas validée.
 */
export function PanierProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const utilisateur = user?.username ?? null;

  // Changement de personne connectée : on recharge SON panier (ajustement d'état pendant le rendu, plutôt que dans un effet).
  const [proprietaire, setProprietaire] = useState<string | null>(utilisateur);
  const [contenu, setContenu] = useState<Contenu>(() => (utilisateur ? lirePanier(utilisateur) : VIDE));
  if (proprietaire !== utilisateur) {
    setProprietaire(utilisateur);
    setContenu(utilisateur ? lirePanier(utilisateur) : VIDE);
  }

  useEffect(() => {
    if (utilisateur && proprietaire === utilisateur) ecrirePanier(utilisateur, contenu);
  }, [utilisateur, proprietaire, contenu]);

  const dernier = useRef(contenu);
  useEffect(() => {
    dernier.current = contenu;
  }, [contenu]);

  const setCart = useCallback<Dispatch<SetStateAction<CartLine[]>>>((valeur) => {
    setContenu((actuel) => ({ ...actuel, lignes: typeof valeur === "function" ? valeur(actuel.lignes) : valeur }));
  }, []);

  const setClientId = useCallback((clientId: string) => {
    setContenu((actuel) => ({ ...actuel, clientId }));
  }, []);

  const rapprocher = useCallback((catalogue: Article[]) => {
    const { lignes, messages } = rapprocherDuCatalogue(dernier.current.lignes, catalogue);
    if (messages.length > 0 || lignes.some((ligne, index) => ligne.article !== dernier.current.lignes[index]?.article)) {
      setContenu((actuel) => ({ ...actuel, lignes: rapprocherDuCatalogue(actuel.lignes, catalogue).lignes }));
    }
    return messages;
  }, []);

  const nombreArticles = useMemo(
    () => arrondirQuantite(contenu.lignes.reduce((total, ligne) => total + ligne.quantite, 0)),
    [contenu.lignes],
  );

  const value = useMemo(
    () => ({ cart: contenu.lignes, setCart, clientId: contenu.clientId, setClientId, rapprocher, nombreArticles }),
    [contenu.lignes, contenu.clientId, setCart, setClientId, rapprocher, nombreArticles],
  );

  return <PanierContext.Provider value={value}>{children}</PanierContext.Provider>;
}

export function usePanier() {
  const context = useContext(PanierContext);
  if (!context) throw new Error("usePanier doit être utilisé dans PanierProvider.");
  return context;
}
