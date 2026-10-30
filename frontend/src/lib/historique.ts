import { useCallback, useEffect, useRef, useState } from "react";
import type { Periode } from "../components/SelecteurPeriode";
import { api } from "../services/api";
import { apiErrorMessage } from "../services/apiError";

/** Ce que reçoit chaque onglet de l'historique : la période et le vendeur choisis en haut de l'écran. */
export interface ProprietesHistorique {
  periode: Periode;
  /** Le vendeur choisi par un responsable ; vide : tous. Un caissier n'en envoie jamais : le serveur impose le sien. */
  vendeur: string;
  /** Pour le nom des fichiers imprimés : le vendeur choisi, ou le caissier connecté (vide : tous les vendeurs). */
  nomVendeur: string;
  /** Prévient l'écran qu'un chargement est en cours (le bouton « Afficher » de la période attend). */
  onChargement: (enCours: boolean) => void;
}

/** Lignes affichées par page : au-delà, la liste devient longue à parcourir (l'impression, elle, prend tout). */
export const PAR_PAGE = 50;

const libellesPaiement: Record<string, string> = {
  ESPECES: "Espèces",
  MOBILE_MONEY: "Mobile Money",
  CARTE: "Carte",
  VIREMENT: "Virement",
  CHEQUE: "Chèque",
  AUTRE: "Autre",
};

export const libellePaiement = (mode: string | null): string => (mode ? libellesPaiement[mode] ?? mode : "—");

/** Montants des tableaux : sans l'unité, dite une fois sous le tableau (sinon la ligne dépasse l'écran). */
export const nombre = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 2 });

const heure = new Intl.DateTimeFormat("fr-FR", { timeStyle: "short" });
const jourHeure = new Intl.DateTimeFormat("fr-FR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
const jour = new Intl.DateTimeFormat("fr-FR", { day: "2-digit", month: "2-digit", year: "numeric" });

/** L'heure seule pour une journée, le jour et l'heure pour une période de plusieurs jours. */
export const momentDansLaPeriode = (iso: string, plusieursJours: boolean): string => (plusieursJours ? jourHeure : heure).format(new Date(iso));

export const jourSeul = (iso: string): string => jour.format(new Date(iso));

/** « gesstock-ventes-soldees » ou, pour un seul vendeur, « gesstock-ventes-soldees-marie ». */
export const nomDeFichier = (debut: string, nomVendeur: string): string => (nomVendeur ? `${debut}-${nomVendeur}` : debut);

/**
 * Charge un historique de la période (et du vendeur choisi) depuis le serveur. Une réponse tardive d'une demande précédente ne
 * remplace jamais la dernière.
 */
export function useHistorique<T>(chemin: string, { periode, vendeur, onChargement }: ProprietesHistorique, messageErreur: string) {
  const [donnees, setDonnees] = useState<T | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState("");
  const derniereDemande = useRef(0);

  const recharger = useCallback(async () => {
    const demande = ++derniereDemande.current;
    setChargement(true);
    setErreur("");
    try {
      const { data } = await api.get<T>(chemin, { params: { debut: periode.debut, fin: periode.fin, ...(vendeur ? { vendeur } : {}) } });
      if (demande !== derniereDemande.current) return;
      setDonnees(data);
    } catch (raison) {
      if (demande !== derniereDemande.current) return;
      setDonnees(null);
      setErreur(apiErrorMessage(raison, messageErreur));
    } finally {
      if (demande === derniereDemande.current) setChargement(false);
    }
  }, [chemin, periode, vendeur, messageErreur]);

  useEffect(() => { void recharger(); }, [recharger]);
  useEffect(() => { onChargement(chargement); }, [chargement, onChargement]);

  return { donnees, chargement, erreur, recharger };
}
