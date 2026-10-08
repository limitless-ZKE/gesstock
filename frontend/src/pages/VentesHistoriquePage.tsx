import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Ban,
  Banknote,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  LoaderCircle,
  Printer,
  ReceiptText,
  Search,
  ShoppingBag,
  Tag,
} from "lucide-react";
import ReceiptModal, { remisesDeLaVente, type Sale } from "../components/ReceiptModal";
import { api } from "../services/api";
import { apiErrorMessage as errorMessage } from "../services/apiError";
import { moneyPrecise as money } from "../lib/format";


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
          <p className="mb-1 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Suivi de la caisse</p>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">Historique des ventes</h1>
          <p className="mt-1.5 text-sm capitalize text-slate-500">{formatLongDay(day)}</p>
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

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi icon={<ShoppingBag size={18} />} label="Ventes validées" value={String(summary.count)} />
        <Kpi icon={<Banknote size={18} />} label="Chiffre d’affaires TTC" value={money.format(summary.revenue)} />
        <Kpi icon={<Tag size={18} />} label="Remises accordées" value={money.format(summary.discounts)} />
        <Kpi icon={<Ban size={18} />} label="Ventes annulées" value={String(summary.cancelled)} />
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
            <thead className="bg-slate-50/80 text-[10px] font-bold uppercase tracking-wider text-slate-400">
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
                      <p className="mt-0.5 text-[10px] text-slate-400">{sale.lignes.reduce((total, line) => total + line.quantite, 0)} article(s)</p>
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-600">{sale.client ?? "Client de passage"}</td>
                    <td className="px-4 py-3 text-xs text-slate-600">{sale.vendeur ?? "—"}</td>
                    <td className="px-4 py-3 text-xs text-slate-600">
                      {sale.aCredit ? "À crédit" : sale.modeReglement ? paymentLabels[sale.modeReglement] ?? sale.modeReglement : "—"}
                    </td>
                    <td className="px-4 py-3 text-right text-xs text-emerald-700">{discounts > 0 ? `−${money.format(discounts)}` : "—"}</td>
                    <td className={`px-4 py-3 text-right text-xs font-bold ${cancelled ? "text-slate-400 line-through" : "text-slate-900"}`}>{money.format(sale.montantTotal)}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded-full px-2 py-1 text-[9px] font-bold ${cancelled ? "bg-rose-50 text-rose-700" : "bg-emerald-50 text-emerald-700"}`}>
                        {cancelled ? "Annulée" : "Validée"}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-right">
                      <button onClick={() => setReceipt(sale)} className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-slate-200 px-3 text-[10px] font-bold text-slate-600 hover:border-emerald-300 hover:text-emerald-700" title="Réimprimer le reçu">
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
        <p className="border-t border-slate-100 px-5 py-3 text-[11px] text-slate-500">
          {filtered.length} vente(s) affichée(s). Le bouton « Reçu » imprime un duplicata du ticket remis au client.
        </p>
      </section>

      {receipt && <ReceiptModal sale={receipt} duplicata onClose={() => setReceipt(null)} />}
    </div>
  );
}

function Kpi({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex items-center justify-between">
        <p className="text-[11px] font-semibold text-slate-500">{label}</p>
        <span className="grid size-8 place-items-center rounded-lg bg-slate-50 text-slate-500">{icon}</span>
      </div>
      <p className="mt-3 text-2xl font-bold tracking-tight text-slate-900">{value}</p>
    </article>
  );
}
