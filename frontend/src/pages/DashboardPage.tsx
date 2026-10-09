import { useEffect, useMemo, useRef, useState } from "react";
import { CircleAlert, ShoppingCart } from "lucide-react";
import { Link } from "react-router-dom";
import { api } from "../services/api";
import { apiErrorMessage } from "../services/apiError";
import { money } from "../lib/format";

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

const nombre = new Intl.NumberFormat("fr-FR");
const compact = new Intl.NumberFormat("fr-FR", { notation: "compact", maximumFractionDigits: 1 });
const pluriel = (n: number, un: string, plusieurs: string) => `${nombre.format(n)} ${n > 1 ? plusieurs : un}`;
const jourComplet = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" });

export default function DashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    api.get<DashboardStats>("/statistiques/dashboard")
      .then(({ data }) => { if (active) setStats(data); })
      .catch((reason: unknown) => {
        if (!active) return;
        setError(apiErrorMessage(reason, "Les statistiques ne sont pas accessibles pour ce compte ou le serveur est indisponible."));
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const valeur = (lire: (s: DashboardStats) => string) => (loading ? "…" : stats ? lire(stats) : "—");
  const alertes = stats?.alertesStockCritique ?? 0;

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Tableau de bord</h1>
          <p className="mt-0.5 text-sm text-slate-500 first-letter:uppercase">{new Intl.DateTimeFormat("fr-FR", { dateStyle: "full" }).format(new Date())}</p>
        </div>
        <Link to="/caisse" className="button-primary"><ShoppingCart size={17} /> Nouvelle vente</Link>
      </header>

      {error && (
        <div role="alert" className="flex items-center gap-2 border-l-4 border-rose-600 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-800">
          <CircleAlert size={17} />{error}
          <button onClick={() => window.location.reload()} className="ml-auto font-semibold underline">Réessayer</button>
        </div>
      )}

      {/* Un seul chiffre domine : le chiffre d'affaires du jour. Les trois autres le complètent sur une même ligne. */}
      <section aria-label="Chiffres du jour" className="rounded-2xl border border-slate-200 bg-white">
        <div className="p-5 sm:p-6">
          <p className="text-sm font-medium text-slate-600">Chiffre d’affaires du jour</p>
          <p className="mt-1 text-4xl font-bold text-slate-900 proportional-nums sm:text-5xl">{valeur((s) => money.format(s.chiffreAffairesJour))}</p>
          <p className="mt-1 text-sm text-slate-500">Ventes validées aujourd’hui</p>
        </div>
        <div className="grid divide-y divide-slate-200 border-t border-slate-200 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
          <Chiffre libelle="Ventes réalisées" valeur={valeur((s) => nombre.format(s.nombreVentesJour))} note="Tickets du jour" />
          <Chiffre libelle="Créances clients" valeur={valeur((s) => money.format(s.totalCreances))} note="Reste à recouvrer" />
          <Chiffre
            libelle="Stock à surveiller"
            valeur={valeur((s) => pluriel(s.alertesStockCritique, "article", "articles"))}
            note={alertes > 0 ? "Au seuil d’alerte ou en rupture" : "Aucun article sous le seuil"}
            alerte={alertes > 0}
            lien={alertes > 0 ? { vers: "/articles", texte: "Voir le catalogue" } : undefined}
          />
        </div>
      </section>

      <section className="grid gap-5 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <article className="min-w-0 rounded-2xl border border-slate-200 bg-white p-5 sm:p-6">
          <h2 className="text-base font-bold text-slate-900">Chiffre d’affaires des 7 derniers jours</h2>
          {stats?.ventes7Jours.length ? (
            <VentesSemaine points={stats.ventes7Jours} />
          ) : (
            <p className="grid h-48 place-items-center text-sm text-slate-500">{loading ? "Chargement…" : "Aucune vente sur ces 7 jours. Les ventes validées apparaîtront ici."}</p>
          )}
        </article>

        <article className="min-w-0 rounded-2xl border border-slate-200 bg-white p-5 sm:p-6">
          <h2 className="text-base font-bold text-slate-900">Articles les plus vendus</h2>
          <p className="mt-0.5 text-sm text-slate-500">Cette semaine, en quantité</p>
          {stats?.topArticles.length ? (
            <ArticlesVendus articles={stats.topArticles.slice(0, 6)} />
          ) : (
            <p className="grid h-40 place-items-center text-sm text-slate-500">{loading ? "Chargement…" : "Pas encore de ventes cette semaine."}</p>
          )}
          <Link to="/statistiques" className="mt-4 inline-block text-sm font-semibold text-emerald-700 hover:text-emerald-900 hover:underline">Voir toutes les statistiques</Link>
        </article>
      </section>
    </div>
  );
}

function Chiffre({ libelle, valeur, note, alerte = false, lien }: { libelle: string; valeur: string; note: string; alerte?: boolean; lien?: { vers: string; texte: string } }) {
  return (
    <div className="p-5 sm:p-6">
      <p className="text-sm font-medium text-slate-600">{libelle}</p>
      <p className={`mt-1 text-2xl font-bold ${alerte ? "text-amber-800" : "text-slate-900"}`}>{valeur}</p>
      <p className="mt-1 text-sm text-slate-500">
        {note}
        {lien && <> — <Link to={lien.vers} className="font-semibold text-emerald-700 hover:underline">{lien.texte}</Link></>}
      </p>
    </div>
  );
}

function ArticlesVendus({ articles }: { articles: TopArticle[] }) {
  const maximum = Math.max(1, ...articles.map((a) => a.quantiteVendue));
  return (
    <ol className="mt-3 divide-y divide-slate-200">
      {articles.map((article) => (
        <li key={article.articleId} className="py-3">
          <div className="flex items-baseline justify-between gap-3">
            <span className="min-w-0 truncate text-sm font-semibold text-slate-900">{article.designation}</span>
            <span className="shrink-0 text-sm font-bold text-slate-900">{pluriel(article.quantiteVendue, "unité", "unités")}</span>
          </div>
          <div className="mt-1.5 h-1.5 rounded-full bg-slate-100" aria-hidden="true">
            <div className="h-full rounded-full bg-emerald-600" style={{ width: `${(article.quantiteVendue / maximum) * 100}%` }} />
          </div>
          <p className="mt-1 text-xs text-slate-500">Réf. {article.reference}</p>
        </li>
      ))}
    </ol>
  );
}

/** Plus petit « chiffre rond » (1, 2, 2,5, 5 × 10ⁿ) au moins égal à la valeur : l'axe se lit en milliers entiers. */
function maximumRond(valeur: number): number {
  if (valeur <= 0) return 1000;
  const puissance = 10 ** Math.floor(Math.log10(valeur));
  return [1, 2, 2.5, 5, 10].map((m) => m * puissance).find((m) => m >= valeur) ?? 10 * puissance;
}

/** Colonne arrondie au sommet seulement, posée à plat sur la ligne de base (rayon réduit si la colonne est très basse). */
function colonne(x: number, y: number, largeur: number, hauteur: number): string {
  const r = Math.min(4, hauteur);
  return `M${x} ${y + hauteur}V${y + r}Q${x} ${y} ${x + r} ${y}H${x + largeur - r}Q${x + largeur} ${y} ${x + largeur} ${y + r}V${y + hauteur}Z`;
}

/**
 * Chiffre d'affaires par jour : une colonne par jour, un seul ton (aujourd'hui en vert plein, les jours passés en vert clair),
 * valeur écrite seulement sur la journée en cours et sur le meilleur jour. Chaque colonne répond au survol et au clavier ;
 * le tableau sous le graphique donne les mêmes chiffres sans dépendre de la vue ni de la couleur.
 */
function VentesSemaine({ points }: { points: DailyPoint[] }) {
  const [actif, setActif] = useState<number | null>(null);
  // Le dessin suit la largeur réelle de la zone : les textes gardent leur taille (12 px), ils ne rétrécissent pas avec l'écran.
  const conteneur = useRef<HTMLDivElement>(null);
  const [largeur, setLargeur] = useState(640);
  useEffect(() => {
    const zone = conteneur.current;
    if (!zone) return;
    const mesurer = () => setLargeur(Math.max(280, Math.round(zone.getBoundingClientRect().width)));
    mesurer();
    if (typeof ResizeObserver === "undefined") return;
    const observateur = new ResizeObserver(mesurer);
    observateur.observe(zone);
    return () => observateur.disconnect();
  }, []);
  const hauteur = 230;
  const gauche = 44;
  const droite = 8;
  const haut = 26;
  const bas = 40;
  const zoneHauteur = hauteur - haut - bas;
  const zoneLargeur = largeur - gauche - droite;
  const maxVente = useMemo(() => Math.max(0, ...points.map((p) => p.chiffreAffaires)), [points]);
  const echelle = maximumRond(maxVente);
  const bande = zoneLargeur / points.length;
  const epaisseur = Math.min(24, bande * 0.5);
  const meilleur = maxVente > 0 ? points.findIndex((p) => p.chiffreAffaires === maxVente) : -1;
  const dernier = points.length - 1;
  const graduations = [0, echelle / 2, echelle];
  const yDe = (valeur: number) => haut + zoneHauteur - (valeur / echelle) * zoneHauteur;
  const pointActif = actif === null ? null : points[actif];

  return (
    <div className="mt-3">
      <div ref={conteneur} className="relative w-full overflow-hidden">
        <svg width={largeur} height={hauteur} viewBox={`0 0 ${largeur} ${hauteur}`} className="block" role="group" aria-label="Colonnes du chiffre d’affaires des sept derniers jours ; le tableau ci-dessous donne les mêmes valeurs.">
          {graduations.map((g) => (
            <g key={g}>
              <line x1={gauche} x2={largeur - droite} y1={yDe(g)} y2={yDe(g)} stroke="var(--color-slate-200)" strokeWidth="1" />
              <text x={gauche - 8} y={yDe(g) + 4} textAnchor="end" fontSize="12" fill="var(--color-slate-500)">{compact.format(g)}</text>
            </g>
          ))}
          {points.map((point, i) => {
            const x = gauche + bande * i + (bande - epaisseur) / 2;
            const h = (point.chiffreAffaires / echelle) * zoneHauteur;
            const centre = x + epaisseur / 2;
            const date = new Date(`${point.journee}T00:00:00`);
            const etiquette = i === dernier || i === meilleur;
            // une valeur longue sur la dernière colonne ne doit pas sortir du dessin : elle s'aligne alors sur le bord droit
            const butee = centre + (nombre.format(point.chiffreAffaires).length * 4.2) > largeur - 2;
            return (
              <g key={point.journee}>
                {point.chiffreAffaires > 0 ? (
                  <path d={colonne(x, yDe(point.chiffreAffaires), epaisseur, h)} fill={i === dernier ? "var(--color-emerald-600)" : "var(--color-emerald-400)"} opacity={actif !== null && actif !== i ? 0.55 : 1} />
                ) : (
                  <rect x={x} y={yDe(0) - 2} width={epaisseur} height="2" fill="var(--color-slate-300)" />
                )}
                {etiquette && point.chiffreAffaires > 0 && (
                  <text x={butee ? largeur - 2 : centre} y={yDe(point.chiffreAffaires) - 7} textAnchor={butee ? "end" : "middle"} fontSize="13" fontWeight="700" fill="var(--color-slate-900)">{nombre.format(point.chiffreAffaires)}</text>
                )}
                <text x={centre} y={hauteur - 20} textAnchor="middle" fontSize="12" fill={i === dernier ? "var(--color-slate-900)" : "var(--color-slate-500)"} fontWeight={i === dernier ? 700 : 400}>{date.toLocaleDateString("fr-FR", { weekday: "short" })}</text>
                <text x={centre} y={hauteur - 5} textAnchor="middle" fontSize="12" fill={i === dernier ? "var(--color-slate-900)" : "var(--color-slate-500)"} fontWeight={i === dernier ? 700 : 400}>{date.getDate()}</text>
                {/* Zone de survol : toute la bande du jour, bien plus large que la colonne. */}
                <rect
                  x={gauche + bande * i}
                  y={haut}
                  width={bande}
                  height={zoneHauteur + bas - 6}
                  fill="transparent"
                  tabIndex={0}
                  role="img"
                  aria-label={`${jourComplet(point.journee)} : ${money.format(point.chiffreAffaires)}, ${pluriel(point.nombreVentes, "vente", "ventes")}`}
                  onPointerEnter={() => setActif(i)}
                  onPointerLeave={() => setActif(null)}
                  onFocus={() => setActif(i)}
                  onBlur={() => setActif(null)}
                  className="outline-none focus-visible:stroke-emerald-600 focus-visible:stroke-2"
                />
              </g>
            );
          })}
        </svg>
        {pointActif && actif !== null && (
          <div
            className="pointer-events-none absolute top-0 z-10 -translate-x-1/2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm shadow-lg"
            style={{ left: Math.min(Math.max(gauche + bande * actif + bande / 2, 90), largeur - 90) }}
          >
            <p className="font-bold text-slate-900">{money.format(pointActif.chiffreAffaires)}</p>
            <p className="text-slate-600">{pluriel(pointActif.nombreVentes, "vente", "ventes")}</p>
            <p className="text-xs text-slate-500 first-letter:uppercase">{jourComplet(pointActif.journee)}</p>
          </div>
        )}
      </div>
      <details className="mt-2 text-sm">
        <summary className="cursor-pointer font-semibold text-emerald-700 hover:underline">Afficher les chiffres en tableau</summary>
        <table className="mt-2 w-full text-left">
          <thead>
            <tr className="border-b border-slate-200 text-slate-600">
              <th className="py-1.5 font-semibold">Jour</th>
              <th className="py-1.5 text-right font-semibold">Ventes</th>
              <th className="py-1.5 text-right font-semibold">Chiffre d’affaires</th>
            </tr>
          </thead>
          <tbody>
            {points.map((point) => (
              <tr key={point.journee} className="border-b border-slate-100">
                <td className="py-1.5 first-letter:uppercase">{jourComplet(point.journee)}</td>
                <td className="py-1.5 text-right">{nombre.format(point.nombreVentes)}</td>
                <td className="py-1.5 text-right font-semibold">{money.format(point.chiffreAffaires)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
