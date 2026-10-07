import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import {
  ArrowDownRight,
  ArrowUpRight,
  Banknote,
  Boxes,
  CircleAlert,
  PackageCheck,
  ShoppingBag,
  TrendingUp,
  UsersRound,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";

type DailyPoint = { journee: string; nombreVentes: number; chiffreAffaires: number };
type TopArticle = { articleId: number; reference: string; designation: string; quantiteVendue: number };
type DashboardStats = {
  journee: string;
  chiffreAffairesJour: number;
  nombreVentesJour: number;
  totalCreances: number;
  alertesStockCritique: number;
  ventes7Jours: DailyPoint[];
  topArticles: TopArticle[];
};

const money = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "XOF", maximumFractionDigits: 0 });

export default function DashboardPage() {
  const { user } = useAuth();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    api.get<DashboardStats>("/statistiques/dashboard")
      .then(({ data }) => { if (active) setStats(data); })
      .catch((reason: unknown) => {
        if (!active) return;
        setError(axios.isAxiosError<{ message?: string }>(reason)
          ? reason.response?.data?.message ?? "Les statistiques ne sont pas accessibles pour ce compte ou le serveur est indisponible."
          : "Impossible de charger le tableau de bord.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const cards = [
    { label: "Chiffre d’affaires du jour", value: stats ? money.format(stats.chiffreAffairesJour) : "—", note: "Ventes validées aujourd’hui", icon: Banknote, tone: "emerald" },
    { label: "Ventes réalisées", value: stats ? String(stats.nombreVentesJour) : "—", note: "Transactions du jour", icon: ShoppingBag, tone: "blue" },
    { label: "Créances clients", value: stats ? money.format(stats.totalCreances) : "—", note: "Solde restant à recouvrer", icon: UsersRound, tone: "violet" },
    { label: "Alertes de stock", value: stats ? String(stats.alertesStockCritique) : "—", note: "Articles au seuil ou en rupture", icon: Boxes, tone: "amber" },
  ] as const;

  const weekTotal = useMemo(() => stats?.ventes7Jours.reduce((total, point) => total + point.chiffreAffaires, 0) ?? 0, [stats]);
  const bestDay = useMemo(() => Math.max(0, ...(stats?.ventes7Jours.map((point) => point.chiffreAffaires) ?? [])), [stats]);

  return (
    <div className="space-y-6">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div><p className="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Vue exécutive</p><h1 className="text-3xl font-bold tracking-tight text-slate-900">Bonjour, {user?.username} <span aria-hidden="true">👋</span></h1><p className="mt-2 text-sm text-slate-500">Synthèse des ventes, créances et stocks à surveiller.</p></div>
        <div className="text-xs font-medium capitalize text-slate-400">{new Intl.DateTimeFormat("fr-FR", { dateStyle: "full" }).format(new Date())}</div>
      </header>

      {error && <div role="alert" className="flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs font-semibold text-rose-800"><CircleAlert size={16} />{error}<button onClick={() => window.location.reload()} className="ml-auto underline">Réessayer</button></div>}

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map(({ label, value, note, icon: Icon, tone }) => <article key={label} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5"><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold text-slate-500">{label}</p><p className="mt-3 text-2xl font-extrabold tracking-tight text-slate-900">{loading ? "…" : value}</p></div><span className={`grid size-10 shrink-0 place-items-center rounded-xl ${tone === "emerald" ? "bg-emerald-50 text-emerald-700" : tone === "blue" ? "bg-blue-50 text-blue-700" : tone === "violet" ? "bg-violet-50 text-violet-700" : "bg-amber-50 text-amber-700"}`}><Icon size={18} /></span></div><p className="mt-2 text-[10px] text-slate-400">{note}</p></article>)}
      </section>

      <section className="grid gap-5 xl:grid-cols-[1.5fr_0.8fr]">
        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
          <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-bold text-slate-900">Tendance des ventes</h2><p className="mt-1 text-xs text-slate-500">Chiffre d’affaires quotidien sur les 7 derniers jours</p></div><div className="rounded-xl bg-emerald-50 px-3 py-2 text-right"><p className="text-[9px] font-bold uppercase tracking-wide text-emerald-700">Total période</p><p className="mt-0.5 text-sm font-extrabold text-emerald-900">{money.format(weekTotal)}</p></div></div>
          {stats?.ventes7Jours.length ? <SalesChart points={stats.ventes7Jours} bestDay={bestDay} /> : <div className="grid h-56 place-items-center text-sm text-slate-400">{loading ? "Chargement…" : "Aucune vente sur cette période."}</div>}
        </article>

        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
          <div className="flex items-start justify-between"><div><h2 className="font-bold text-slate-900">Top performers</h2><p className="mt-1 text-xs text-slate-500">Articles les plus vendus cette semaine</p></div><TrendingUp size={18} className="text-emerald-600" /></div>
          {stats?.topArticles.length ? <ol className="mt-5 space-y-3">{stats.topArticles.slice(0, 5).map((article, index) => <li key={article.articleId} className="flex items-center gap-3"><span className={`grid size-8 shrink-0 place-items-center rounded-lg text-xs font-extrabold ${index === 0 ? "bg-emerald-100 text-emerald-800" : "bg-slate-100 text-slate-500"}`}>{index + 1}</span><span className="min-w-0 flex-1"><span className="block truncate text-xs font-bold text-slate-800">{article.designation}</span><span className="mt-0.5 block text-[10px] text-slate-400">{article.reference}</span></span><span className="whitespace-nowrap text-xs font-extrabold text-slate-800">{article.quantiteVendue}<span className="ml-1 font-medium text-slate-400">unités</span></span></li>)}</ol> : <div className="grid h-48 place-items-center text-sm text-slate-400">{loading ? "Chargement…" : "Pas encore de ventes cette semaine."}</div>}
          <Link to="/statistiques" className="mt-5 inline-flex items-center gap-1.5 text-xs font-bold text-emerald-700 hover:text-emerald-900">Explorer les rapports <ArrowUpRight size={14} /></Link>
        </article>
      </section>

      <section className="grid gap-5 lg:grid-cols-[1fr_1fr]">
        <article className="rounded-2xl bg-[#12332c] p-5 text-white shadow-sm sm:p-6"><div className="flex items-center gap-2 text-emerald-300"><PackageCheck size={17} /><p className="text-xs font-bold uppercase tracking-[0.14em]">Suivi de la semaine</p></div><p className="mt-4 text-2xl font-extrabold">{money.format(weekTotal)}</p><p className="mt-1 text-xs text-emerald-50/70">de chiffre d’affaires sur la période glissante</p><div className="mt-5 flex items-center gap-2 text-[11px] text-emerald-100/70"><ArrowUpRight size={14} />Basé uniquement sur les ventes validées</div></article>
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"><h2 className="font-bold text-slate-900">Accès rapides</h2><p className="mt-1 text-xs text-slate-500">Retrouvez les opérations courantes.</p><div className="mt-4 grid gap-2 sm:grid-cols-2"><QuickLink to="/caisse" title="Nouvelle vente" description="Ouvrir le point de vente" /><QuickLink to="/articles" title="Articles en alerte" description="Vérifier le catalogue" /><QuickLink to="/approvisionnements" title="Réapprovisionner" description="Créer un bon de réception" /><QuickLink to="/clients-dettes" title="Créances clients" description="Suivre les règlements" /></div></div>
      </section>
    </div>
  );
}

function SalesChart({ points, bestDay }: { points: DailyPoint[]; bestDay: number }) {
  const width = 720;
  const height = 210;
  const left = 52;
  const right = 15;
  const top = 18;
  const bottom = 36;
  const graphHeight = height - top - bottom;
  const graphWidth = width - left - right;
  const max = bestDay || 1;
  const coordinates = points.map((point, index) => ({
    x: left + index * graphWidth / Math.max(1, points.length - 1),
    y: top + graphHeight - point.chiffreAffaires / max * graphHeight,
    point,
  }));
  const line = coordinates.map((item, index) => `${index ? "L" : "M"} ${item.x} ${item.y}`).join(" ");
  const area = `${line} L ${left + graphWidth} ${height - bottom} L ${left} ${height - bottom} Z`;
  const guides = [0, 0.5, 1];
  return <div className="mt-5">
    <div className="mb-3 flex items-center gap-1.5 text-[10px] font-semibold text-emerald-700"><span className="size-2 rounded-full bg-emerald-500" />Chiffre d’affaires (XOF)</div>
    <div className="w-full overflow-hidden"><svg viewBox={`0 0 ${width} ${height}`} className="h-auto w-full" role="img" aria-label="Graphique du chiffre d’affaires des sept derniers jours">
      <defs><linearGradient id="sales-area-fill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="#10b981" stopOpacity=".22" /><stop offset="100%" stopColor="#10b981" stopOpacity=".015" /></linearGradient></defs>
      {guides.map((fraction) => { const y = top + graphHeight * fraction; return <g key={fraction}><line x1={left} x2={width - right} y1={y} y2={y} stroke="#e2e8f0" strokeDasharray="3 4" /><text x={left - 8} y={y + 3} textAnchor="end" fill="#94a3b8" fontSize="9">{money.format(max * (1 - fraction)).replace(/\s?FCFA$/, "")}</text></g>; })}
      <path d={area} fill="url(#sales-area-fill)" /><path d={line} fill="none" stroke="#059669" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
      {coordinates.map(({ x, y, point }) => <g key={point.journee}><circle cx={x} cy={y} r="4" fill="#fff" stroke="#059669" strokeWidth="2.5"><title>{`${new Date(`${point.journee}T00:00:00`).toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "short" })} : ${money.format(point.chiffreAffaires)} · ${point.nombreVentes} vente(s)`}</title></circle><text x={x} y={height - 12} textAnchor="middle" fill="#64748b" fontSize="9">{new Date(`${point.journee}T00:00:00`).toLocaleDateString("fr-FR", { weekday: "short", day: "numeric" })}</text></g>)}
    </svg></div>
  </div>;
}

function QuickLink({ to, title, description }: { to: string; title: string; description: string }) {
  return <Link to={to} className="group flex items-center justify-between rounded-xl border border-slate-100 p-3 transition hover:border-emerald-200 hover:bg-emerald-50/50"><div><p className="text-xs font-bold text-slate-800">{title}</p><p className="mt-1 text-[10px] text-slate-500">{description}</p></div><ArrowDownRight size={15} className="rotate-[-45deg] text-slate-300 transition group-hover:text-emerald-600" /></Link>;
}
