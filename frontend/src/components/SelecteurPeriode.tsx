import { useState, type FormEvent } from "react";
import { CalendarDays, LoaderCircle } from "lucide-react";
import { aujourdhui, debutDuMois, hier } from "../lib/dates";

export interface Periode {
  /** Premier jour inclus, AAAA-MM-JJ. */
  debut: string;
  /** Dernier jour inclus, AAAA-MM-JJ. */
  fin: string;
}

type Raccourci = "aujourdhui" | "hier" | "mois" | "perso";

function raccourciDe(periode: Periode): Raccourci {
  if (periode.debut === aujourdhui() && periode.fin === aujourdhui()) return "aujourdhui";
  if (periode.debut === hier() && periode.fin === hier()) return "hier";
  if (periode.debut === debutDuMois() && periode.fin === aujourdhui()) return "mois";
  return "perso";
}

interface SelecteurPeriodeProps {
  /** Période actuellement affichée par l'écran (point de départ du sélecteur). */
  periode: Periode;
  /** Appelée quand l'utilisateur choisit un raccourci ou valide des dates précises. */
  onChange: (periode: Periode) => void;
  chargement?: boolean;
  titre?: string;
  libelleBouton?: string;
}

/**
 * Choix d'une période, le même sur tous les écrans qui en ont besoin (statistiques, historique des ventes, journal de caisse) :
 * des raccourcis (aujourd'hui, hier, ce mois) qui s'appliquent tout de suite, ou « Personnalisée » avec un jour de début et un
 * jour de fin. Pour un seul jour, mettre la même date des deux côtés. Aucune date future : il n'y a rien à y lire.
 */
export default function SelecteurPeriode({ periode, onChange, chargement = false, titre = "Période", libelleBouton = "Afficher" }: SelecteurPeriodeProps) {
  const [raccourci, setRaccourci] = useState<Raccourci>(() => raccourciDe(periode));
  const [debut, setDebut] = useState(periode.debut);
  const [fin, setFin] = useState(periode.fin);
  const [erreur, setErreur] = useState("");

  function appliquer(prochaine: Periode, nouveauRaccourci: Raccourci) {
    setRaccourci(nouveauRaccourci);
    setDebut(prochaine.debut);
    setFin(prochaine.fin);
    setErreur("");
    onChange(prochaine);
  }

  function valider(evenement: FormEvent<HTMLFormElement>) {
    evenement.preventDefault();
    if (!debut || !fin) {
      setErreur("Indiquez le premier et le dernier jour.");
      return;
    }
    if (fin < debut) {
      setErreur("La date de fin doit être égale ou postérieure à la date de début.");
      return;
    }
    if (fin > aujourdhui()) {
      setErreur("Le dernier jour ne peut pas être dans le futur.");
      return;
    }
    setErreur("");
    setRaccourci(raccourciDe({ debut, fin }));
    onChange({ debut, fin });
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 md:p-5">
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="mb-2 text-sm font-semibold text-slate-700" id="titre-periode">{titre}</p>
          <div role="group" aria-labelledby="titre-periode" className="flex flex-wrap gap-1 rounded-xl bg-slate-100 p-1">
            <BoutonRaccourci actif={raccourci === "aujourdhui"} onClick={() => appliquer({ debut: aujourdhui(), fin: aujourdhui() }, "aujourdhui")}>Aujourd’hui</BoutonRaccourci>
            <BoutonRaccourci actif={raccourci === "hier"} onClick={() => appliquer({ debut: hier(), fin: hier() }, "hier")}>Hier</BoutonRaccourci>
            <BoutonRaccourci actif={raccourci === "mois"} onClick={() => appliquer({ debut: debutDuMois(), fin: aujourdhui() }, "mois")}>Ce mois</BoutonRaccourci>
            <BoutonRaccourci actif={raccourci === "perso"} onClick={() => setRaccourci("perso")}>Personnalisée</BoutonRaccourci>
          </div>
        </div>
        <form onSubmit={valider} noValidate className="flex flex-col gap-2 sm:flex-row sm:items-end">
          <ChampDate libelle="Du" valeur={debut} max={fin || aujourdhui()} onChange={(valeur) => { setDebut(valeur); setRaccourci("perso"); }} />
          <ChampDate libelle="Au" valeur={fin} min={debut} max={aujourdhui()} onChange={(valeur) => { setFin(valeur); setRaccourci("perso"); }} />
          <button disabled={chargement} className="button-primary min-w-32">
            {chargement && <LoaderCircle size={15} className="animate-spin" />}
            {libelleBouton}
          </button>
        </form>
      </div>
      {erreur && <p role="alert" className="mt-3 border-l-4 border-amber-600 bg-amber-50 px-3 py-2 text-sm font-medium text-amber-900">{erreur}</p>}
    </section>
  );
}

function BoutonRaccourci({ actif, onClick, children }: { actif: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={actif}
      className={`rounded-lg px-3 py-2 text-sm font-semibold transition ${actif ? "bg-white text-emerald-800 shadow-sm" : "text-slate-600 hover:text-slate-900"}`}
    >
      {children}
    </button>
  );
}

function ChampDate({ libelle, valeur, min, max, onChange }: { libelle: string; valeur: string; min?: string; max: string; onChange: (valeur: string) => void }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-semibold text-slate-700">{libelle}</span>
      <span className="relative block">
        <CalendarDays size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
        <input type="date" required value={valeur} min={min} max={max} onChange={(evenement) => onChange(evenement.target.value)} className="input-field pl-9" />
      </span>
    </label>
  );
}
