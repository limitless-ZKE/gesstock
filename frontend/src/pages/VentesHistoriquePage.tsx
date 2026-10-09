import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Ban,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  LoaderCircle,
  Printer,
  ReceiptText,
  Search,
  } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import ReceiptModal, { remisesDeLaVente, type Sale } from "../components/ReceiptModal";
import { api } from "../services/api";
import { apiErrorMessage as errorMessage } from "../services/apiError";
import { moneyPrecise as money } from "../lib/format";
import Indicateur from "../components/Indicateur";


const paymentLabels: Record<string, string> = {
  ESPECES: "Espèces",
  MOBILE_MONEY: "Mobile Money",
  CARTE: "Carte",
  VIREMENT: "Virement",
  CHEQUE: "Chèque",
  AUTRE: "Autre",
};

/** Date locale au format AAAA-MM-JJ attendu par l'API et par <input type="date">. */
function toInputDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function shiftDay(value: string, days: number) {
  const [year, month, day] = value.split("-").map(Number);
  return toInputDate(new Date(year, month - 1, day + days));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("fr-FR", { timeStyle: "short" }).format(new Date(value));
}

function formatLongDay(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "full" }).format(new Date(year, month - 1, day));
}

type StatusFilter = "all" | "VALIDEE" | "ANNULEE";

export default function VentesHistoriquePage() {
  const today = toInputDate(new Date());
  const [day, setDay] = useState(today);
  const [sales, setSales] = useState<Sale[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<StatusFilter>("all");
  const [receipt, setReceipt] = useState<Sale | null>(null);
  const { user } = useAuth();
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [notice, setNotice] = useState<{ text: string; isError: boolean } | null>(null);
  // Annuler une vente (stock remis, client remboursé) est réservé aux responsables : voir SecurityConfig.
  const canCancel = (user?.roles ?? []).some((role) => ["ADMIN", "MANAGER"].includes(role.toUpperCase().replace(/^ROLE_/, "")));

  const load = useCallback(async (selectedDay: string) => {
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get<Sale[]>("/ventes", { params: { date: selectedDay } });
      setSales(data);
    } catch (reason) {
      setSales([]);
      setError(errorMessage(reason, "Impossible de charger l’historique des ventes."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(day); }, [day, load]);

  /** Annulation d'une vente au comptant, le jour même : le client rapporte les articles et est remboursé. */
  async function cancelCashSale(sale: Sale) {
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
      await load(day);
    } catch (reason) {
      setNotice({ text: errorMessage(reason, "L’annulation de la vente a échoué."), isError: true });
    } finally {
      setCancellingId(null);
    }
  }

  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("fr");
    return sales.filter((sale) =>
      (status === "all" || sale.statut === status)
      && (!query || [sale.code, sale.client, sale.vendeur].some((value) => value?.toLocaleLowerCase("fr").includes(query))));
  }, [sales, search, status]);

  const summary = useMemo(() => {
    const valid = sales.filter((sale) => sale.statut === "VALIDEE");
    return {
      count: valid.length,
      revenue: valid.reduce((total, sale) => total + sale.montantTotal, 0),
      cancelled: sales.filter((sale) => sale.statut === "ANNULEE").length,
      discounts: valid.reduce((total, sale) => total + remisesDeLaVente(sale).total, 0),
    };
  }, [sales]);

  return (
    <div className="space-y-6">
      <header className="flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Historique des ventes</h1>
          <p className="mt-1.5 text-sm text-slate-500 first-letter:uppercase">{formatLongDay(day)}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button onClick={() => setDay(shiftDay(day, -1))} className="icon-button" aria-label="Jour précédent"><ChevronLeft size={17} /></button>
          <label className="relative block">
            <CalendarDays size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="date"
              value={day}
              max={today}
              onChange={(event) => event.target.value && setDay(event.target.value)}
              className="input-field h-10 pl-9"
              aria-label="Journée à consulter"
            />
          </label>
          <button onClick={() => setDay(shiftDay(day, 1))} disabled={day >= today} className="icon-button disabled:opacity-30" aria-label="Jour suivant"><ChevronRight size={17} /></button>
          <button onClick={() => setDay(today)} disabled={day === today} className="button-secondary h-10 disabled:opacity-40">Aujourd’hui</button>
        </div>
      </header>

      {error && (
        <div role="alert" className="flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs font-semibold text-rose-800">
          <CircleAlert size={16} /> {error}
        </div>
      )}
      {notice && (
        <div role={notice.isError ? "alert" : "status"} className={`flex items-center gap-2 rounded-xl border px-4 py-3 text-xs font-semibold ${notice.isError ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}>
          <CircleAlert size={16} /> {notice.text}
          <button className="ml-auto opacity-60 hover:opacity-100" onClick={() => setNotice(null)} aria-label="Fermer">×</button>
        </div>
      )}

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi label="Ventes validées" value={String(summary.count)} />
        <Kpi label="Chiffre d’affaires TTC" value={money.format(summary.revenue)} />
        <Kpi label="Remises accordées" value={money.format(summary.discounts)} />
        <Kpi label="Ventes annulées" value={String(summary.cancelled)} />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 md:flex-row md:items-center">
          <label className="relative min-w-0 flex-1">
            <Search size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input value={search} onChange={(event) => setSearch(event.target.value)} className="input-field pl-10" placeholder="N° de vente, client ou caissier…" />
          </label>
          <select value={status} onChange={(event) => setStatus(event.target.value as StatusFilter)} className="input-field md:w-48" aria-label="Filtrer par statut">
            <option value="all">Tous les statuts</option>
            <option value="VALIDEE">Validées</option>
            <option value="ANNULEE">Annulées</option>
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[860px] text-left">
            <thead className="bg-slate-50/80 text-xs font-semibold text-slate-600">
              <tr>
                <th className="px-5 py-3">Heure</th>
                <th className="px-4 py-3">Vente</th>
                <th className="px-4 py-3">Client</th>
                <th className="px-4 py-3">Caissier</th>
                <th className="px-4 py-3">Paiement</th>
                <th className="px-4 py-3 text-right">Remises</th>
                <th className="px-4 py-3 text-right">Total TTC</th>
                <th className="px-4 py-3">Statut</th>
                <th className="px-5 py-3 text-right">Reçu</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr><td colSpan={9} className="h-32 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
              ) : filtered.length ? filtered.map((sale) => {
                const discounts = remisesDeLaVente(sale).total;
                const cancelled = sale.statut === "ANNULEE";
                return (
                  <tr key={sale.id} className="hover:bg-slate-50/70">
                    <td className="px-5 py-3 text-xs text-slate-500">{formatTime(sale.dateVente)}</td>
                    <td className="px-4 py-3">
                      <p className="text-xs font-bold text-slate-800">{sale.code}</p>
                      <p className="mt-0.5 text-xs text-slate-400">{sale.lignes.reduce((total, line) => total + line.quantite, 0)} article(s)</p>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-600">{sale.client ?? "Client de passage"}</td>
                    <td className="px-4 py-3 text-xs text-slate-600">{sale.vendeur ?? "—"}</td>
                    <td className="px-4 py-3 text-xs text-slate-600">
                      {sale.aCredit ? "À crédit" : sale.modeReglement ? paymentLabels[sale.modeReglement] ?? sale.modeReglement : "—"}
                    </td>
                    <td className="px-4 py-3 text-right text-xs text-emerald-700">{discounts > 0 ? `−${money.format(discounts)}` : "—"}</td>
                    <td className={`px-4 py-3 text-right text-xs font-bold ${cancelled ? "text-slate-400 line-through" : "text-slate-900"}`}>{money.format(sale.montantTotal)}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded-full px-2 py-1 text-xs font-bold ${cancelled ? "bg-rose-50 text-rose-700" : "bg-emerald-50 text-emerald-700"}`}>
                        {cancelled ? "Annulée" : "Validée"}
                      </span>
                    </td>
                    <td className="space-x-2 px-5 py-3 text-right">
                      {canCancel && !cancelled && !sale.aCredit && day === today && (
                        <button disabled={cancellingId === sale.id} onClick={() => void cancelCashSale(sale)} className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-rose-200 px-3 text-xs font-bold text-rose-700 hover:bg-rose-50 disabled:opacity-50" title="Annuler cette vente : le client rapporte les articles et est remboursé">
                          {cancellingId === sale.id ? <LoaderCircle size={13} className="animate-spin" /> : <Ban size={13} />} Annuler
                        </button>
                      )}
                      <button onClick={() => setReceipt(sale)} className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-slate-200 px-3 text-xs font-bold text-slate-600 hover:border-emerald-300 hover:text-emerald-700" title="Réimprimer le reçu">
                        <Printer size={13} /> Reçu
                      </button>
                    </td>
                  </tr>
                );
              }) : (
                <tr>
                  <td colSpan={9} className="h-36 text-center text-sm text-slate-400">
                    <ReceiptText className="mx-auto mb-2" size={22} />
                    {sales.length ? "Aucune vente ne correspond à votre recherche." : "Aucune vente enregistrée pour cette journée."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <p className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">
          {filtered.length} vente(s) affichée(s). Le bouton « Reçu » imprime un duplicata du ticket remis au client.
        </p>
      </section>

      {receipt && <ReceiptModal sale={receipt} duplicata onClose={() => setReceipt(null)} />}
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: string }) {
  return <Indicateur libelle={label} valeur={value} />;
}
