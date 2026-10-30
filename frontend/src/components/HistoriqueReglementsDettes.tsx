import { useMemo, useState } from "react";
import { CircleAlert, HandCoins, LoaderCircle, Search } from "lucide-react";
import BoutonsExport from "./BoutonsExport";
import Indicateur from "./Indicateur";
import Pagination from "./Pagination";
import { moneyPrecise as money } from "../lib/format";
import { PAR_PAGE, libellePaiement, momentDansLaPeriode, nomDeFichier, nombre, useHistorique, type ProprietesHistorique } from "../lib/historique";

type LigneReglement = {
  id: number;
  dateReglement: string;
  client: string;
  vente: string | null;
  montant: number;
  modeReglement: string;
  reference: string | null;
  recuPar: string | null;
  /** Ce qui restait dû sur la dette juste après ce paiement ; nul si la vente à crédit a été annulée depuis. */
  resteApres: number | null;
  detteAnnulee: boolean;
};
type ReglementsDettes = {
  debut: string;
  fin: string;
  resume: { nombre: number; total: number; dettesSoldees: number; parMode: { mode: string; nombre: number; montant: number }[] };
  reglements: LigneReglement[];
};

/**
 * Les paiements reçus sur les dettes des clients pendant la période : qui a encaissé, par quel moyen, et ce qui restait dû juste
 * après. Pour un vendeur, ce sont les paiements qu'IL a encaissés (comme dans son journal de caisse).
 */
export default function HistoriqueReglementsDettes(proprietes: ProprietesHistorique) {
  const { periode, vendeur, nomVendeur } = proprietes;
  const { donnees, chargement, erreur } = useHistorique<ReglementsDettes>("/ventes/historique/reglements", proprietes, "Impossible de charger les règlements de dettes.");
  const [erreurExport, setErreurExport] = useState<string | null>(null);
  const [recherche, setRecherche] = useState("");
  const [page, setPage] = useState(0);

  const reglements = useMemo(() => donnees?.reglements ?? [], [donnees]);
  const filtres = useMemo(() => {
    const texte = recherche.trim().toLocaleLowerCase("fr");
    return texte
      ? reglements.filter((reglement) => [reglement.client, reglement.vente, reglement.reference, reglement.recuPar]
        .some((valeur) => valeur?.toLocaleLowerCase("fr").includes(texte)))
      : reglements;
  }, [reglements, recherche]);
  const pages = Math.max(1, Math.ceil(filtres.length / PAR_PAGE));
  const pageCourante = Math.min(page, pages - 1);
  const affiches = filtres.slice(pageCourante * PAR_PAGE, (pageCourante + 1) * PAR_PAGE);
  const plusieursJours = periode.debut !== periode.fin;
  const resume = donnees?.resume;

  return (
    <>
      <div className="flex justify-end">
        <BoutonsExport chemin="/ventes/historique/reglements" parametres={vendeur ? { vendeur } : undefined} periode={periode}
          nomDuFichier={nomDeFichier("gesstock-reglements-dettes", nomVendeur)} desactive={chargement || !donnees} onErreur={setErreurExport} />
      </div>

      {erreur && (
        <div role="alert" className="flex items-center gap-2 border-l-4 border-rose-600 bg-rose-50 px-3 py-3 text-sm font-medium text-rose-800">
          <CircleAlert size={16} /> {erreur}
        </div>
      )}
      {erreurExport && (
        <div role="alert" className="flex items-center gap-2 border-l-4 border-amber-600 bg-amber-50 px-3 py-3 text-sm font-medium text-amber-900">
          <CircleAlert size={16} /> {erreurExport}
          <button className="ml-auto font-semibold underline" onClick={() => setErreurExport(null)}>Fermer</button>
        </div>
      )}

      <section className="grid gap-3 sm:grid-cols-3">
        <Indicateur libelle="Règlements reçus" valeur={resume ? String(resume.nombre) : "—"} />
        <Indicateur libelle="Total encaissé" valeur={resume ? money.format(resume.total) : "—"} accent />
        <Indicateur libelle="Dettes soldées" valeur={resume ? String(resume.dettesSoldees) : "—"} note="réglées jusqu’au dernier franc" />
      </section>
      {resume && resume.parMode.length > 0 && (
        <p className="text-sm text-slate-700">
          <span className="font-semibold">Par moyen de paiement : </span>
          {resume.parMode.map((mode) => `${libellePaiement(mode.mode)} ${money.format(mode.montant)} (${mode.nombre})`).join(" · ")}
        </p>
      )}

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="border-b border-slate-100 p-4">
          <label className="relative block">
            <Search size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input value={recherche} onChange={(evenement) => { setRecherche(evenement.target.value); setPage(0); }} className="input-field pl-10"
              placeholder="Client, n° de vente, référence ou caissier…" aria-label="Rechercher un règlement" />
          </label>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] text-left">
            <thead className="bg-slate-50 text-sm font-semibold text-slate-600">
              <tr>
                <th className="px-5 py-3">{plusieursJours ? "Date" : "Heure"}</th>
                <th className="px-3 py-3">Client</th>
                <th className="px-3 py-3">Vente</th>
                <th className="px-3 py-3">Paiement</th>
                <th className="px-3 py-3">Référence</th>
                <th className="px-3 py-3">Encaissé par</th>
                <th className="px-3 py-3 text-right">Montant</th>
                <th className="px-4 py-3 text-right">Reste dû après</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {chargement ? (
                <tr><td colSpan={8} className="h-32 text-center text-sm text-slate-500"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
              ) : affiches.length ? affiches.map((reglement) => (
                <tr key={reglement.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-5 py-3 text-sm text-slate-600">{momentDansLaPeriode(reglement.dateReglement, plusieursJours)}</td>
                  <td className="px-3 py-3 text-sm font-semibold text-slate-900">{reglement.client}</td>
                  <td className="whitespace-nowrap px-3 py-3 text-sm text-slate-700">{reglement.vente ?? "—"}</td>
                  <td className="px-3 py-3 text-sm text-slate-700">{libellePaiement(reglement.modeReglement)}</td>
                  <td className="px-3 py-3 text-sm text-slate-700">{reglement.reference ?? "—"}</td>
                  <td className="px-3 py-3 text-sm text-slate-700">{reglement.recuPar ?? "—"}</td>
                  <td className="px-3 py-3 text-right text-sm font-bold text-slate-900">{nombre.format(reglement.montant)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right text-sm">
                    {reglement.detteAnnulee
                      ? <span className="text-rose-800">Dette annulée</span>
                      : reglement.resteApres === 0
                        ? <span className="rounded-full bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-800">Soldée</span>
                        : <span className="text-slate-700">{nombre.format(reglement.resteApres ?? 0)}</span>}
                  </td>
                </tr>
              )) : (
                <tr>
                  <td colSpan={8} className="h-36 text-center text-sm text-slate-500">
                    <HandCoins className="mx-auto mb-2" size={22} />
                    {reglements.length ? "Aucun règlement ne correspond à votre recherche." : "Aucun règlement de dette sur cette période."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col gap-2 border-t border-slate-100 px-5 py-3 text-sm text-slate-600 sm:flex-row sm:items-center sm:justify-between">
          <p>{filtres.length} règlement(s), montants en francs CFA. « Reste dû après » : ce qu’il restait à payer sur la dette juste après ce règlement.</p>
          <Pagination page={pageCourante} pages={pages} onPage={setPage} libelle="Pages des règlements" />
        </div>
      </section>
    </>
  );
}
