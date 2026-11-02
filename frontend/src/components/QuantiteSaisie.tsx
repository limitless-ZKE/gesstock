import { useState } from "react";
import { formatQuantite, lireQuantite, nettoyerSaisieQuantite } from "../lib/quantite";

type Props = {
  valeur: number;
  /** Plus grande quantité acceptée (le stock disponible). */
  max: number;
  libelle: string;
  onChange: (quantite: number) => void;
};

/**
 * Quantité d'une ligne du panier : on tape le nombre voulu, avec une virgule si besoin (3 décimales au plus : « 2,5 »).
 * Les boutons − et + restent à côté. Jamais au-delà du stock.
 */
export default function QuantiteSaisie({ valeur, max, libelle, onChange }: Props) {
  // Pendant la frappe le champ peut être vide, à zéro ou finir par une virgule sans toucher au panier ; en le quittant il
  // retrouve la quantité du panier.
  const [saisie, setSaisie] = useState<string | null>(null);

  function modifier(texte: string) {
    const propre = nettoyerSaisieQuantite(texte);
    setSaisie(propre);
    const nombre = lireQuantite(propre);
    if (nombre === null || nombre <= 0) return;
    const retenu = Math.min(nombre, max);
    if (retenu !== nombre) setSaisie(formatQuantite(retenu));
    onChange(retenu);
  }

  return (
    <input
      type="text"
      inputMode="decimal"
      autoComplete="off"
      aria-label={libelle}
      value={saisie ?? formatQuantite(valeur)}
      onChange={(event) => modifier(event.target.value)}
      onFocus={(event) => event.target.select()}
      onBlur={() => setSaisie(null)}
      className="h-8 w-16 border-x border-slate-200 bg-transparent text-center text-xs font-bold text-slate-900 outline-none focus:bg-emerald-50"
    />
  );
}
