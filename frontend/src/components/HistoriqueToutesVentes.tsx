import { useMemo, useState } from "react";
import { Ban, CircleAlert, LoaderCircle, Printer, ReceiptText, Search } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import BoutonsExport from "./BoutonsExport";
import Indicateur from "./Indicateur";
import Pagination from "./Pagination";
import ReceiptModal, { type Sale } from "./ReceiptModal";
import { aujourdhui, versJourIso } from "../lib/dates";
import { moneyPrecise as money } from "../lib/format";
import { PAR_PAGE, libellePaiement, momentDansLaPeriode, nomDeFichier, nombre, useHistorique, type ProprietesHistorique } from "../lib/historique";
import { api } from "../services/api";
import { apiErrorMessage as errorMessage } from "../services/apiError";
import { formatQuantite } from "../lib/quantite";

type LigneHistorique = {
  id: number;
  code: string;
  dateVente: string;
  statut: "VALIDEE" | "ANNULEE";
  client: string | null;
  vendeur: string | null;
  aCredit: boolean;
  modeReglement: string | null;
  nombreArticles: number;
  remises: number;
  montantTotal: number;
};
type Historique = {
  debut: string;
  fin: string;
  resume: { nombreValidees: number; chiffreAffaires: number; remises: number; nombreAnnulees: number; montantAnnule: number };
  ventes: LigneHistorique[];
};
type StatusFilter = "all" | "VALIDEE" | "ANNULEE";

/** Toutes les ventes de la période (validées et annulées), avec le duplicata du ticket et l'annulation du jour pour un responsable. */
export default function HistoriqueToutesVentes(proprietes: ProprietesHistorique) {
  const { periode, vendeur, nomVendeur } = proprietes;
  const { donnees: historique, chargement: loading, erreur: error, recharger } =
    useHistorique<Historique>("/ventes/historique", proprietes, "Impossible de charger l’historique des ventes.");
  const [exportError, setExportError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<StatusFilter>("all");
  const [page, setPage] = useState(0);
  const [receipt, setReceipt] = useState<Sale | null>(null);
  const [receiptLoadingId, setReceiptLoadingId] = useState<number | null>(null);
  const { user } = useAuth();
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [notice, setNotice] = useState<{ text: string; isError: boolean } | null>(null);
  // Annuler une vente (stock remis, client remboursé) est réservé aux responsables : voir SecurityConfig.
  const canCancel = (user?.roles ?? []).some((role) => ["ADMIN", "MANAGER"].includes(role.toUpperCase().replace(/^ROLE_/, "")));

  /** Le ticket complet (avec ses lignes) se relit à la demande : la liste, elle, reste légère. */
  async function showReceipt(ligne: LigneHistorique) {
    setReceiptLoadingId(ligne.id);
    try {
      const { data } = await api.get<Sale>(`/ventes/${ligne.id}`);
      setReceipt(data);
    } catch (reason) {
      setNotice({ text: errorMessage(reason, "Impossible de charger le reçu de cette vente."), isError: true });
    } finally {
      setReceiptLoadingId(null);
    }
  }

  /** Annulation d'une vente au comptant, le jour même : le client rapporte les articles et est remboursé. */
  async function cancelCashSale(sale: LigneHistorique) {
    const motif = window.prompt(
      `Annuler la vente au comptant ${sale.code} (${money.format(sale.montantTotal)}) ?\n`
        + "Le stock sera remis en rayon et le client remboursé.\n\nMotif de l’annulation :",
    )?.trim();
    if (!motif) return;
    const especes = sale.modeReglement === "ESPECES";
    if (!window.confirm(
      `Le client rapporte-t-il les articles de la vente ${sale.code} en l’état ?\n\n`
        + `OK : vente annulée et ${money.format(sale.montantTotal)} remboursés${especes ? " (sortis de la caisse du jour)" : " (par le même moyen de paiement)"}.\n`
        + "Annuler : rien n’est modifié.",
    )) return;
    setCancellingId(sale.id);
    try {
      await api.post(`/ventes/${sale.id}/annulation`, { motif, articlesRetournesEnEtat: true });
      setNotice({ text: `La vente ${sale.code} est annulée : stock remis en rayon, ${money.format(sale.montantTotal)} remboursés.`, isError: false });
      await recharger();
    } catch (reason) {
      setNotice({ text: errorMessage(reason, "L’annulation de la vente a échoué."), isError: true });
    } finally {
      setCancellingId(null);
    }
  }

  const sales = useMemo(() => historique?.ventes ?? [], [historique]);
  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("fr");
    return sales.filter((sale) =>
      (status === "all" || sale.statut === status)
      && (!query || [sale.code, sale.client, sale.vendeur].some((value) => value?.toLocaleLowerCase("fr").includes(query))));
  }, [sales, search, status]);
  const pages = Math.max(1, Math.ceil(filtered.length / PAR_PAGE));
  const pageCourante = Math.min(page, pages - 1);
  const affichees = filtered.slice(pageCourante * PAR_PAGE, (pageCourante + 1) * PAR_PAGE);
  const plusieursJours = periode.debut !== periode.fin;
  const resume = historique?.resume;

  return (
    <>
      <div className="flex justify-end">
        <BoutonsExport chemin="/ventes/historique" parametres={vendeur ? { vendeur } : undefined} periode={periode}
          nomDuFichier={nomDeFichier("gesstock-historique-ventes", nomVendeur)} desactive={loading || !historique} onErreur={setExportError} />
      </div>

      {error && (
        <div role="alert" className="flex items-center gap-2 border-l-4 border-rose-600 bg-rose-50 px-3 py-3 text-sm font-medium text-rose-800">
          <CircleAlert size={16} /> {error}
        </div>
      )}
      {exportError && (
        <div role="alert" className="flex items-center gap-2 border-l-4 border-amber-600 bg-amber-50 px-3 py-3 text-sm font-medium text-amber-900">
          <CircleAlert size={16} /> {exportError}
          <button className="ml-auto font-semibold underline" onClick={() => setExportError(null)}>Fermer</button>
        </div>
      )}
      {notice && (
        <div role={notice.isError ? "alert" : "status"} className={`flex items-center gap-2 border-l-4 px-3 py-3 text-sm font-medium ${notice.isError ? "border-rose-600 bg-rose-50 text-rose-800" : "border-emerald-600 bg-emerald-50 text-emerald-900"}`}>
          <CircleAlert size={16} /> {notice.text}
          <button className="ml-auto opacity-60 hover:opacity-100" onClick={() => setNotice(null)} aria-label="Fermer">×</button>
        </div>
      )}

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Indicateur libelle="Ventes validées" valeur={resume ? String(resume.nombreValidees) : "—"} />
        <Indicateur libelle="Chiffre d’affaires TTC" valeur={resume ? money.format(resume.chiffreAffaires) : "—"} />
        <Indicateur libelle="Remises accordées" valeur={resume ? money.format(resume.remises) : "—"} />
        <Indicateur libelle="Ventes annulées" valeur={resume ? String(resume.nombreAnnulees) : "—"} />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 md:flex-row md:items-center">
          <label className="relative min-w-0 flex-1">
            <Search size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
            <input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} className="input-field pl-10" placeholder="N° de vente, client ou caissier…" aria-label="Rechercher une vente" />
          </label>
          <select value={status} onChange={(event) => { setStatus(event.target.value as StatusFilter); setPage(0); }} className="input-field md:w-48" aria-label="Filtrer par statut">
            <option value="all">Tous les statuts</option>
            <option value="VALIDEE">Validées</option>
            <option value="ANNULEE">Annulées</option>
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[860px] text-left">
            <thead className="bg-slate-50 text-sm font-semibold text-slate-600">
              <tr>
                <th className="px-5 py-3">{plusieursJours ? "Date" : "Heure"}</th>
                <th className="px-3 py-3">Vente</th>
                <th className="px-3 py-3">Client</th>
                <th className="px-3 py-3">Caissier</th>
                <th className="px-3 py-3">Paiement</th>
                <th className="px-3 py-3 text-right">Remises</th>
                <th className="px-3 py-3 text-right">Total TTC</th>
                <th className="px-3 py-3">Statut</th>
                <th className="px-4 py-3 text-right">Reçu</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr><td colSpan={9} className="h-32 text-center text-sm text-slate-500"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
              ) : affichees.length ? affichees.map((sale) => {
                const cancelled = sale.statut === "ANNULEE";
                const duJour = versJourIso(new Date(sale.dateVente)) === aujourdhui();
                return (
                  <tr key={sale.id} className="hover:bg-slate-50">
                    <td className="whitespace-nowrap px-5 py-3 text-sm text-slate-600">{momentDansLaPeriode(sale.dateVente, plusieursJours)}</td>
                    <td className="px-3 py-3">
                      <p className="whitespace-nowrap text-sm font-semibold text-slate-900">{sale.code}</p>
                      <p className="mt-0.5 text-xs text-slate-500">{formatQuantite(sale.nombreArticles)} article(s)</p>
                    </td>
                    <td className="px-3 py-3 text-sm text-slate-700">{sale.client ?? "Client de passage"}</td>
                    <td className="px-3 py-3 text-sm text-slate-700">{sale.vendeur ?? "—"}</td>
                    <td className="px-3 py-3 text-sm text-slate-700">{sale.aCredit ? "À crédit" : libellePaiement(sale.modeReglement)}</td>
                    <td className="px-3 py-3 text-right text-sm text-emerald-800">{sale.remises > 0 ? `−${nombre.format(sale.remises)}` : "—"}</td>
                    <td className={`px-3 py-3 text-right text-sm font-bold ${cancelled ? "text-slate-500 line-through" : "text-slate-900"}`}>{nombre.format(sale.montantTotal)}</td>
                    <td className="px-3 py-3">
                      <span className={`rounded-full px-2 py-1 text-xs font-semibold ${cancelled ? "bg-rose-50 text-rose-800" : "bg-emerald-50 text-emerald-800"}`}>
                        {cancelled ? "Annulée" : "Validée"}
                      </span>
                    </td>
                    <td className="space-x-2 whitespace-nowrap px-4 py-3 text-right">
                      {canCancel && !cancelled && !sale.aCredit && duJour && (
                        <button disabled={cancellingId === sale.id} onClick={() => void cancelCashSale(sale)} className="button-secondary h-9 border-rose-300 px-3 text-rose-800 hover:bg-rose-50" title="Annuler cette vente : le client rapporte les articles et est remboursé">
                          {cancellingId === sale.id ? <LoaderCircle size={14} className="animate-spin" /> : <Ban size={14} />} Annuler
                        </button>
                      )}
                      <button disabled={receiptLoadingId === sale.id} onClick={() => void showReceipt(sale)} className="button-secondary h-9 px-3" title="Réimprimer le reçu">
                        {receiptLoadingId === sale.id ? <LoaderCircle size={14} className="animate-spin" /> : <Printer size={14} />} Reçu
                      </button>
                    </td>
                  </tr>
                );
              }) : (
                <tr>
                  <td colSpan={9} className="h-36 text-center text-sm text-slate-500">
                    <ReceiptText className="mx-auto mb-2" size={22} />
                    {sales.length ? "Aucune vente ne correspond à votre recherche." : plusieursJours ? "Aucune vente enregistrée sur cette période." : "Aucune vente enregistrée pour cette journée."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col gap-2 border-t border-slate-100 px-5 py-3 text-sm text-slate-600 sm:flex-row sm:items-center sm:justify-between">
          <p>{filtered.length} vente(s), montants en francs CFA. Le bouton « Reçu » imprime un duplicata du ticket remis au client.</p>
          <Pagination page={pageCourante} pages={pages} onPage={setPage} libelle="Pages de l’historique" />
        </div>
      </section>

      {receipt && <ReceiptModal sale={receipt} duplicata onClose={() => setReceipt(null)} />}
    </>
  );
}
