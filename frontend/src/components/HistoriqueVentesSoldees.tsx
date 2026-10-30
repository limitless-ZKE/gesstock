import { useMemo, useState } from "react";
import { CircleAlert, LoaderCircle, ReceiptText, Search } from "lucide-react";
import BoutonsExport from "./BoutonsExport";
import Indicateur from "./Indicateur";
import Pagination from "./Pagination";
import { moneyPrecise as money } from "../lib/format";
import { PAR_PAGE, jourSeul, libellePaiement, momentDansLaPeriode, nomDeFichier, nombre, useHistorique, type ProprietesHistorique } from "../lib/historique";

type LigneSoldee = {
  id: number;
  code: string;
  dateVente: string;
  client: string | null;
  vendeur: string | null;
  aCredit: boolean;
  modeReglement: string | null;
  montantTotal: number;
  /** Au comptant : la vente elle-même ; à crédit : le dernier règlement, celui qui a soldé la dette. */
  soldeeLe: string | null;
};
type VentesSoldees = {
  debut: string;
  fin: string;
  resume: { nombre: number; montant: number; nombreComptant: number; montantComptant: number; nombreCreditsSoldes: number; montantCreditsSoldes: number };
  ventes: LigneSoldee[];
};

const pluriel = (n: number, mot: string) => `${n} ${mot}${n > 1 ? "s" : ""}`;

/**
 * Les ventes de la période entièrement payées : au comptant, ou à crédit dont la dette est réglée jusqu'au dernier franc. Une vente
 * à crédit y figure à la date où elle a été faite, dès que sa dette est soldée ; les crédits encore dus et les ventes annulées non.
 */
export default function HistoriqueVentesSoldees(proprietes: ProprietesHistorique) {
  const { periode, vendeur, nomVendeur } = proprietes;
  const { donnees, chargement, erreur } = useHistorique<VentesSoldees>("/ventes/historique/soldees", proprietes, "Impossible de charger les ventes soldées.");
  const [erreurExport, setErreurExport] = useState<string | null>(null);
  const [recherche, setRecherche] = useState("");
  const [page, setPage] = useState(0);

  const ventes = useMemo(() => donnees?.ventes ?? [], [donnees]);
  const filtrees = useMemo(() => {
    const texte = recherche.trim().toLocaleLowerCase("fr");
    return texte ? ventes.filter((vente) => [vente.code, vente.client, vente.vendeur].some((valeur) => valeur?.toLocaleLowerCase("fr").includes(texte))) : ventes;
  }, [ventes, recherche]);
  const pages = Math.max(1, Math.ceil(filtrees.length / PAR_PAGE));
  const pageCourante = Math.min(page, pages - 1);
  const affichees = filtrees.slice(pageCourante * PAR_PAGE, (pageCourante + 1) * PAR_PAGE);
  const plusieursJours = periode.debut !== periode.fin;
  const resume = donnees?.resume;

  return (
    <>
      <div className="flex justify-end">
        <BoutonsExport chemin="/ventes/historique/soldees" parametres={vendeur ? { vendeur } : undefined} periode={periode}
          nomDuFichier={nomDeFichier("gesstock-ventes-soldees", nomVendeur)} desactive={chargement || !donnees} onErreur={setErreurExport} />
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

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Indicateur libelle="Ventes soldées" valeur={resume ? String(resume.nombre) : "—"} accent />
        <Indicateur libelle="Montant encaissé TTC" valeur={resume ? money.format(resume.montant) : "—"} />
        <Indicateur libelle="Au comptant" valeur={resume ? money.format(resume.montantComptant) : "—"} note={resume ? pluriel(resume.nombreComptant, "vente") : undefined} />
        <Indicateur libelle="Crédits soldés" valeur={resume ? money.format(resume.montantCreditsSoldes) : "—"} note={resume ? pluriel(resume.nombreCreditsSoldes, "vente") : undefined} />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="border-b border-slate-100 p-4">
          <label className="relative block">
            <Search size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input value={recherche} onChange={(evenement) => { setRecherche(evenement.target.value); setPage(0); }} className="input-field pl-10"
              placeholder="N° de vente, client ou caissier…" aria-label="Rechercher une vente soldée" />
          </label>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left">
            <thead className="bg-slate-50 text-sm font-semibold text-slate-600">
              <tr>
                <th className="px-5 py-3">{plusieursJours ? "Date" : "Heure"}</th>
                <th className="px-3 py-3">Vente</th>
                <th className="px-3 py-3">Client</th>
                <th className="px-3 py-3">Caissier</th>
                <th className="px-3 py-3">Paiement</th>
                <th className="px-4 py-3 text-right">Total TTC</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {chargement ? (
                <tr><td colSpan={6} className="h-32 text-center text-sm text-slate-500"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
              ) : affichees.length ? affichees.map((vente) => (
                <tr key={vente.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-5 py-3 text-sm text-slate-600">{momentDansLaPeriode(vente.dateVente, plusieursJours)}</td>
                  <td className="whitespace-nowrap px-3 py-3 text-sm font-semibold text-slate-900">{vente.code}</td>
                  <td className="px-3 py-3 text-sm text-slate-700">{vente.client ?? "Client de passage"}</td>
                  <td className="px-3 py-3 text-sm text-slate-700">{vente.vendeur ?? "—"}</td>
                  <td className="px-3 py-3 text-sm text-slate-700">
                    {vente.aCredit
                      ? <span className="font-medium text-emerald-800">Crédit soldé{vente.soldeeLe ? ` le ${jourSeul(vente.soldeeLe)}` : ""}</span>
                      : libellePaiement(vente.modeReglement)}
                  </td>
                  <td className="px-4 py-3 text-right text-sm font-bold text-slate-900">{nombre.format(vente.montantTotal)}</td>
                </tr>
              )) : (
                <tr>
                  <td colSpan={6} className="h-36 text-center text-sm text-slate-500">
                    <ReceiptText className="mx-auto mb-2" size={22} />
                    {ventes.length ? "Aucune vente ne correspond à votre recherche." : "Aucune vente soldée sur cette période."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col gap-2 border-t border-slate-100 px-5 py-3 text-sm text-slate-600 sm:flex-row sm:items-center sm:justify-between">
          <p>{filtrees.length} vente(s) soldée(s), montants en francs CFA. Une vente à crédit apparaît ici, à sa date, dès que sa dette est entièrement réglée.</p>
          <Pagination page={pageCourante} pages={pages} onPage={setPage} libelle="Pages des ventes soldées" />
        </div>
      </section>
    </>
  );
}
