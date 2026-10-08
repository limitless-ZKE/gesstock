import { useState } from "react";
import { logoUrlDe, useEntreprise, type Entreprise } from "../entreprise/EntrepriseContext";

interface EnTeteDocumentProps {
  /**
   * « ticket » : colonne centrée pour le papier thermique 80 mm. « a4 » : logo à gauche, coordonnées à droite, pour les
   * rapports, factures et bons. Chaque variante demande au serveur la taille de logo qui lui convient.
   */
  variante: "ticket" | "a4";
  /** Pour un aperçu : valeurs à afficher à la place de celles de l'entreprise enregistrée. */
  entreprise?: Entreprise | null;
}

/** Plafonds d'affichage du logo, en millimètres (unités d'impression). Le serveur fournit déjà une image de taille voisine. */
const LOGO_MAX = {
  ticket: { largeur: "42mm", hauteur: "18mm" },
  a4: { largeur: "60mm", hauteur: "28mm" },
} as const;

/**
 * En-tête des documents imprimables : logo, nom et coordonnées de l'entreprise, lus au moment de l'impression. Un logo
 * introuvable est simplement omis (pas d'icône d'image cassée sur un ticket).
 */
export default function EnTeteDocument({ variante, entreprise: apercu }: EnTeteDocumentProps) {
  const contexte = useEntreprise();
  const entreprise = apercu === undefined ? contexte.entreprise : apercu;
  const [logoEnErreur, setLogoEnErreur] = useState<string | null>(null);

  if (!entreprise) return null;
  const taille = variante === "ticket" ? "ticket" : "entete";
  const url = logoUrlDe(entreprise, taille);
  const afficherLogo = url !== null && url !== logoEnErreur && (variante === "a4" || entreprise.logoSurTickets);
  const limites = LOGO_MAX[variante];
  const logo = afficherLogo && (
    <img
      src={url}
      alt={`Logo ${entreprise.nom}`}
      onError={() => setLogoEnErreur(url)}
      style={{ maxWidth: limites.largeur, maxHeight: limites.hauteur, objectFit: "contain" }}
      className={variante === "ticket" ? "mx-auto mb-2 block" : "block"}
    />
  );
  const contact = [entreprise.telephone && `Tél. ${entreprise.telephone}`, entreprise.email].filter(Boolean).join(" · ");

  if (variante === "ticket") {
    return (
      <div className="text-center">
        {logo}
        <p className="text-base font-extrabold uppercase tracking-tight">{entreprise.nom}</p>
        {entreprise.adresse && <p className="mt-1 text-[9px]">{entreprise.adresse}</p>}
        {contact && <p className="text-[9px]">{contact}</p>}
        {entreprise.identifiantLegal && <p className="text-[9px]">{entreprise.identifiantLegal}</p>}
        {entreprise.siteWeb && <p className="text-[9px]">{entreprise.siteWeb}</p>}
      </div>
    );
  }
  return (
    <div className="flex items-center gap-6 border-b border-slate-300 pb-4">
      {logo}
      <div className="min-w-0 text-sm text-slate-700">
        <p className="text-lg font-extrabold text-slate-900">{entreprise.nom}</p>
        {entreprise.adresse && <p>{entreprise.adresse}</p>}
        {contact && <p>{contact}</p>}
        {entreprise.identifiantLegal && <p>{entreprise.identifiantLegal}</p>}
        {entreprise.siteWeb && <p>{entreprise.siteWeb}</p>}
      </div>
    </div>
  );
}
