import type { ReactNode } from "react";

/**
 * Un chiffre et son libellé : le même composant partout où un écran résume son contenu (articles, bons, clients, ventes…).
 * Pas d'icône décorative : le libellé dit ce que mesure le chiffre. « accent » marque le chiffre à regarder en premier ;
 * « alerte » signale un chiffre qui demande une action (écart de caisse, stock bas) : il passe à l'ambre, jamais au vert.
 */
export default function Indicateur({
  libelle,
  valeur,
  note,
  accent = false,
  alerte = false,
}: {
  libelle: string;
  valeur: ReactNode;
  note?: string;
  accent?: boolean;
  alerte?: boolean;
}) {
  const cadre = alerte ? "border-amber-600" : accent ? "border-emerald-600" : "border-slate-200";
  return (
    <div className={`rounded-xl border bg-white px-4 py-3.5 ${cadre}`}>
      <p className="text-sm font-medium text-slate-600">{libelle}</p>
      <p className={`mt-1 text-2xl font-bold ${alerte ? "text-amber-800" : "text-slate-900"}`}>{valeur}</p>
      {note && <p className="mt-0.5 text-sm text-slate-500">{note}</p>}
    </div>
  );
}
