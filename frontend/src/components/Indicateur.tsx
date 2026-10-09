import type { ReactNode } from "react";

/**
 * Un chiffre et son libellé : le même composant partout où un écran résume son contenu (articles, bons, clients, ventes…).
 * Pas d'icône décorative : le libellé dit ce que mesure le chiffre. « accent » marque le chiffre à regarder en premier.
 */
export default function Indicateur({ libelle, valeur, note, accent = false }: { libelle: string; valeur: ReactNode; note?: string; accent?: boolean }) {
  return (
    <div className={`rounded-xl border bg-white px-4 py-3.5 ${accent ? "border-emerald-600" : "border-slate-200"}`}>
      <p className="text-sm font-medium text-slate-600">{libelle}</p>
      <p className="mt-1 text-2xl font-bold text-slate-900">{valeur}</p>
      {note && <p className="mt-0.5 text-sm text-slate-500">{note}</p>}
    </div>
  );
}
