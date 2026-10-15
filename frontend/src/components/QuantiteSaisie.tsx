import { useState } from "react";

type Props = {
  valeur: number;
  /** Plus grande quantité acceptée (le stock disponible). */
  max: number;
  libelle: string;
  onChange: (quantite: number) => void;
};

/** Quantité d'une ligne du panier : on tape le nombre voulu (les boutons − et + restent à côté). Jamais au-delà du stock. */
export default function QuantiteSaisie({ valeur, max, libelle, onChange }: Props) {
  // Pendant la frappe le champ peut être vide ou à zéro sans toucher au panier ; en le quittant il retrouve la quantité du panier.
  const [saisie, setSaisie] = useState<string | null>(null);

  function modifier(texte: string) {
    const chiffres = texte.replace(/\D/g, "").replace(/^0+(?=\d)/, "");
    const nombre = chiffres === "" ? 0 : Number(chiffres);
    if (nombre < 1) {
      setSaisie(chiffres);
      return;
    }
    const retenu = Math.min(nombre, max);
    setSaisie(String(retenu));
    onChange(retenu);
  }

  return (
    <input
      type="text"
      inputMode="numeric"
      autoComplete="off"
      aria-label={libelle}
      value={saisie ?? String(valeur)}
      onChange={(event) => modifier(event.target.value)}
      onFocus={(event) => event.target.select()}
      onBlur={() => setSaisie(null)}
      className="h-8 w-12 border-x border-slate-200 bg-transparent text-center text-xs font-bold text-slate-900 outline-none focus:bg-emerald-50"
    />
  );
}
