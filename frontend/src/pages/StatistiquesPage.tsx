import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  ArrowDownToLine,
  CircleAlert,
  } from "lucide-react";
import { api } from "../services/api";
import { apiErrorMessage as messageFor } from "../services/apiError";
import { money } from "../lib/format";
import BoutonsExport from "../components/BoutonsExport";
import EnTeteDocument from "../components/EnTeteDocument";
import Indicateur from "../components/Indicateur";
import SelecteurPeriode, { type Periode } from "../components/SelecteurPeriode";
import { aujourdhui, debutDuMois } from "../lib/dates";
import { formatQuantite } from "../lib/quantite";

type CategoryMargin = {
  categorieId: number | null;
  categorie: string;
  chiffreAffaires: number;
  coutAchat: number;
  margeBrute: number;
};
type ArticleMargin = {
  articleId: number;
  reference: string;
  designation: string;
  quantiteVendue: number;
  chiffreAffaires: number;
  coutAchat: number;
  margeBrute: number;
};
type CashSession = {
  sessionId: number;
  code: string;
  journee: string;
  caissier: string;
  statut: "OUVERTE" | "FERMEE";
  fondDeCaisse: number;
  chiffreAffaires: number;
  montantEncaisse: number;
  reglementsClientsEspeces: number;
  sortiesCaisse: number;
  montantCloture: number | null;
  nombreVentes: number;
  ouverteLe: string;
  fermeeLe: string | null;
};
type JournalActivity = {
  id: number;
  action: string;
  entite: string;
  entiteId: number | null;
  details: string | null;
  dateActivite: string;
  utilisateur: string | null;
};
type Report = {
  debut: string;
  fin: string;
  chiffreAffaires: number;
  nombreVentes: number;
  margeBrute: number;
  margesParCategorie: CategoryMargin[];
  margesParArticle: ArticleMargin[];
  sessionsCaisse: CashSession[];
  journalActivites: JournalActivity[];
};

function dateLabel(value: string) {
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium" }).format(new Date(`${value.slice(0, 10)}T00:00:00`));
}

function dateTimeLabel(value: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

export default function StatistiquesPage() {
  const [periode, setPeriode] = useState<Periode>(() => ({ debut: debutDuMois(), fin: aujourdhui() }));
  const [report, setReport] = useState<Report | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [exportError, setExportError] = useState<string | null>(null);
  // Une réponse tardive d'une ancienne période ne doit pas écraser celle qu'on vient de choisir.
  const derniereDemande = useRef(0);

  const loadReport = useCallback(async (choisie: Periode) => {
    const demande = ++derniereDemande.current;
    setLoading(true);
    setError("");
    setExportError(null);
    try {
      const { data } = await api.get<Report>("/statistiques/rapport", { params: { debut: choisie.debut, fin: choisie.fin } });
      if (demande === derniereDemande.current) setReport(data);
    } catch (reason) {
      if (demande === derniereDemande.current) setError(messageFor(reason, "Le rapport financier n’a pas pu être chargé."));
    } finally {
      if (demande === derniereDemande.current) setLoading(false);
    }
  }, []);

  useEffect(() => { void loadReport(periode); }, [periode, loadReport]);

  const maxCategoryMargin = useMemo(() => Math.max(0, ...(report?.margesParCategorie.map((item) => Math.max(0, item.margeBrute)) ?? [])), [report]);
  const maxArticleMargin = useMemo(() => Math.max(0, ...(report?.margesParArticle.map((item) => Math.max(0, item.margeBrute)) ?? [])), [report]);

  return <div className="space-y-6">
    <div className="hidden print:block"><EnTeteDocument variante="a4" /></div>
    <header className="flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
      <div><h1 className="text-2xl font-bold text-slate-900">Rapports & statistiques</h1><p className="mt-1.5 text-sm text-slate-500">Analysez le chiffre d’affaires, les marges et les sessions de caisse.</p><p className="mt-1 text-xs text-slate-400">Le chiffre d’affaires est TTC ; les marges produit sont calculées hors TVA, après remises.</p></div>
      <BoutonsExport chemin="/statistiques/rapport" periode={periode} nomDuFichier="gesstock-rapport" desactive={loading || !report} onErreur={setExportError} />
    </header>

    <SelecteurPeriode periode={periode} onChange={setPeriode} chargement={loading} titre="Période d’analyse" libelleBouton="Analyser" />

    {error && <div role="alert" className="flex items-center gap-2 border-l-4 border-rose-600 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-800"><CircleAlert size={16} />{error}<button onClick={() => void loadReport(periode)} className="ml-auto font-semibold underline">Réessayer</button></div>}
    {exportError && <div role="alert" className="flex items-center gap-2 border-l-4 border-amber-600 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900"><CircleAlert size={16} />{exportError}<button onClick={() => setExportError(null)} className="ml-auto font-semibold underline">Fermer</button></div>}

    <section className="grid gap-3 sm:grid-cols-3">
      <Kpi title="Chiffre d’affaires" value={report ? money.format(report.chiffreAffaires) : "—"} loading={loading} />
      <Kpi title="Ventes validées" value={report ? String(report.nombreVentes) : "—"} loading={loading} />
      <Kpi title="Marge brute estimée" value={report ? money.format(report.margeBrute) : "—"} loading={loading} />
    </section>

    {report && <>
      <section className="grid gap-5 xl:grid-cols-2">
        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"><div className="mb-5"><h2 className="font-bold text-slate-900">Rentabilité par catégorie</h2><p className="mt-1 text-xs text-slate-500">Marge brute estimée · {dateLabel(report.debut)} – {dateLabel(report.fin)}</p></div>
          {report.margesParCategorie.length ? <div className="space-y-4">{report.margesParCategorie.map((item) => <div key={item.categorieId ?? "uncategorized"}><div className="mb-1.5 flex justify-between gap-3 text-xs"><span className="truncate font-semibold text-slate-700">{item.categorie}</span><span className={`shrink-0 font-bold ${item.margeBrute < 0 ? "text-rose-600" : "text-emerald-700"}`}>{money.format(item.margeBrute)}</span></div><div className="h-2 overflow-hidden rounded-full bg-slate-100"><div className={`h-full rounded-full ${item.margeBrute < 0 ? "bg-rose-400" : "bg-emerald-500"}`} style={{ width: `${Math.min(100, Math.max(0, maxCategoryMargin > 0 ? item.margeBrute / maxCategoryMargin * 100 : 0))}%` }} /></div><p className="mt-1 text-xs text-slate-400">CA {money.format(item.chiffreAffaires)} · coût {money.format(item.coutAchat)}</p></div>)}</div> : <Empty>No sales in this range.</Empty>}
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"><div className="mb-5"><h2 className="font-bold text-slate-900">Meilleurs produits par marge</h2><p className="mt-1 text-xs text-slate-500">Les 5 articles au meilleur résultat brut</p></div>
          {report.margesParArticle.length ? <div className="space-y-3">{[...report.margesParArticle].sort((a, b) => b.margeBrute - a.margeBrute).slice(0, 5).map((item, index) => <div key={item.articleId} className="flex items-center gap-3"><span className="grid size-7 shrink-0 place-items-center rounded-lg bg-slate-100 text-xs font-extrabold text-slate-500">{index + 1}</span><div className="min-w-0 flex-1"><p className="truncate text-xs font-bold text-slate-800">{item.designation}</p><p className="mt-0.5 text-xs text-slate-400">{item.reference} · {formatQuantite(item.quantiteVendue)} vendu(s)</p><div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-100"><div className={`h-full rounded-full ${item.margeBrute < 0 ? "bg-rose-400" : "bg-emerald-600"}`} style={{ width: `${Math.max(0, Math.min(100, maxArticleMargin > 0 ? item.margeBrute / maxArticleMargin * 100 : 0))}%` }} /></div></div><span className={`text-xs font-extrabold ${item.margeBrute < 0 ? "text-rose-600" : "text-slate-800"}`}>{money.format(item.margeBrute)}</span></div>)}</div> : <Empty>No sales in this range.</Empty>}
        </article>
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-100 p-5"><h2 className="font-bold text-slate-900">Rentabilité détaillée par produit</h2><p className="mt-1 text-xs text-slate-500">Chiffre d’affaires net HT, coût d’achat et marge estimée sur l’ensemble des articles vendus.</p></div>
        <div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left"><thead className="bg-slate-50 text-xs font-semibold text-slate-600"><tr><th className="px-5 py-3">Produit</th><th className="px-4 py-3">Quantité</th><th className="px-4 py-3">Chiffre d’affaires HT</th><th className="px-4 py-3">Coût d’achat</th><th className="px-5 py-3 text-right">Marge brute</th></tr></thead><tbody className="divide-y divide-slate-100">{[...report.margesParArticle].sort((a, b) => b.chiffreAffaires - a.chiffreAffaires).map((item) => <tr key={item.articleId} className="hover:bg-slate-50/70"><td className="px-5 py-3"><p className="text-xs font-bold text-slate-800">{item.designation}</p><p className="mt-1 text-xs text-slate-400">{item.reference}</p></td><td className="px-4 py-3 text-xs text-slate-600">{formatQuantite(item.quantiteVendue)}</td><td className="px-4 py-3 text-xs text-slate-600">{money.format(item.chiffreAffaires)}</td><td className="px-4 py-3 text-xs text-slate-600">{money.format(item.coutAchat)}</td><td className={`px-5 py-3 text-right text-xs font-bold ${item.margeBrute < 0 ? "text-rose-600" : "text-emerald-700"}`}>{money.format(item.margeBrute)}</td></tr>)}{report.margesParArticle.length === 0 && <tr><td colSpan={5} className="h-28 text-center text-xs text-slate-400">Aucun produit vendu sur la période sélectionnée.</td></tr>}</tbody></table></div>
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-slate-100 p-5"><div><h2 className="font-bold text-slate-900">Journal de caisse par caissier</h2><p className="mt-1 text-xs text-slate-500">Fonds d’ouverture, entrées (ventes et règlements clients), sorties de caisse et comptage à la clôture.</p></div><ArrowDownToLine size={17} className="text-slate-400" /></div>
        <div className="overflow-x-auto"><table className="w-full min-w-[1180px] text-left"><thead className="bg-slate-50 text-xs font-semibold text-slate-600"><tr><th className="px-5 py-3">Journée / session</th><th className="px-4 py-3">Caissier</th><th className="px-4 py-3">Statut</th><th className="px-4 py-3">Fond initial</th><th className="px-4 py-3">Ventes TTC</th><th className="px-4 py-3">Encaissé sur ventes</th><th className="px-4 py-3">Règl. clients (espèces)</th><th className="px-4 py-3">Sorties de caisse</th><th className="px-4 py-3">Comptage final</th><th className="px-4 py-3">Ouverture / clôture</th></tr></thead>
          <tbody className="divide-y divide-slate-100">{report.sessionsCaisse.length ? report.sessionsCaisse.map((session) => <tr key={session.sessionId} className="hover:bg-slate-50/70"><td className="px-5 py-3"><p className="text-xs font-bold text-slate-800">{dateLabel(session.journee)}</p><p className="mt-1 text-xs text-slate-400">{session.code}</p></td><td className="px-4 py-3 text-xs font-semibold text-slate-700">{session.caissier}</td><td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-xs font-bold ${session.statut === "OUVERTE" ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>{session.statut === "OUVERTE" ? "Ouverte" : "Clôturée"}</span></td><td className="px-4 py-3 text-xs text-slate-600">{money.format(session.fondDeCaisse)}</td><td className="px-4 py-3"><p className="text-xs font-bold text-slate-800">{money.format(session.chiffreAffaires)}</p><p className="mt-1 text-xs text-slate-400">{session.nombreVentes} vente(s)</p></td><td className="px-4 py-3 text-xs font-semibold text-emerald-700">{money.format(session.montantEncaisse)}</td><td className="px-4 py-3 text-xs font-semibold text-emerald-700">{money.format(session.reglementsClientsEspeces)}</td><td className="px-4 py-3 text-xs font-semibold text-rose-700">{session.sortiesCaisse > 0 ? `− ${money.format(session.sortiesCaisse)}` : money.format(0)}</td><td className="px-4 py-3 text-xs text-slate-600">{session.montantCloture == null ? "—" : money.format(session.montantCloture)}</td><td className="px-4 py-3"><p className="text-xs text-slate-600">{dateTimeLabel(session.ouverteLe)}</p><p className="mt-1 text-xs text-slate-400">{dateTimeLabel(session.fermeeLe)}</p></td></tr>) : <tr><td colSpan={10} className="h-32 text-center text-sm text-slate-400">Aucune session de caisse sur cette période.</td></tr>}</tbody>
        </table></div>
        <p className="border-t border-slate-100 px-5 py-3 text-xs text-slate-400">Les entrées regroupent les règlements de ventes et les règlements clients en espèces rattachés à la session ; les sorties sont les retraits et dépenses saisis en caisse.</p>
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-100 p-5"><h2 className="font-bold text-slate-900">Journal des opérations</h2><p className="mt-1 text-xs text-slate-500">Traçabilité des actions utilisateurs sur la période.</p></div>
        <div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left"><thead className="bg-slate-50 text-xs font-semibold text-slate-600"><tr><th className="px-5 py-3">Date et heure</th><th className="px-4 py-3">Opération</th><th className="px-4 py-3">Élément</th><th className="px-4 py-3">Utilisateur</th><th className="px-5 py-3">Détails</th></tr></thead><tbody className="divide-y divide-slate-100">{report.journalActivites.length ? [...report.journalActivites].reverse().map((activity) => <tr key={activity.id} className="hover:bg-slate-50/70"><td className="whitespace-nowrap px-5 py-3 text-xs text-slate-600">{dateTimeLabel(activity.dateActivite)}</td><td className="px-4 py-3"><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold text-slate-600">{activity.action}</span></td><td className="px-4 py-3 text-xs font-semibold text-slate-700">{activity.entite}{activity.entiteId == null ? "" : ` #${activity.entiteId}`}</td><td className="px-4 py-3 text-xs text-slate-600">{activity.utilisateur || "Système"}</td><td className="max-w-sm px-5 py-3 text-xs text-slate-500">{activity.details || "—"}</td></tr>) : <tr><td colSpan={5} className="h-28 text-center text-xs text-slate-400">Aucune opération journalisée sur cette période.</td></tr>}</tbody></table></div>
      </section>
    </>}
    <div className="hidden print:block"><p className="text-xs text-slate-500">Rapport généré le {new Date().toLocaleString("fr-FR")}</p></div>
  </div>;
}

function Kpi({ title, value, loading }: { title: string; value: string; loading: boolean }) {
  return <Indicateur libelle={title} valeur={loading ? "…" : value} />;
}

function Empty({ children }: { children: ReactNode }) {
  return <div className="rounded-xl border border-dashed border-slate-200 px-4 py-9 text-center text-xs text-slate-400">{children}</div>;
}
