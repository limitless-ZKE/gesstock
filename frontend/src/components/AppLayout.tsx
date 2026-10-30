import { Suspense, useEffect, useState } from "react";
import {
  Boxes,
  Building2,
  ChartNoAxesCombined,
  ClipboardList,
  LayoutDashboard,
  LogOut,
  Menu,
  PackageMinus,
  PackagePlus,
  PanelLeftClose,
  PanelLeftOpen,
  ReceiptText,
  ScrollText,
  ShoppingCart,
  ShieldCheck,
  SlidersHorizontal,
  Store,
  UsersRound,
  X,
  type LucideIcon,
} from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useEntreprise } from "../entreprise/EntrepriseContext";
import { api } from "../services/api";
import Marque from "./Marque";

interface Lien {
  to: string;
  label: string;
  icon: LucideIcon;
}

/** Le menu suit le travail du magasin : on vend, on gère le stock, on pilote. Un titre de groupe disparaît si aucun de ses liens n'est permis. */
const groupes: { titre: string | null; liens: Lien[] }[] = [
  { titre: null, liens: [{ to: "/dashboard", label: "Tableau de bord", icon: LayoutDashboard }] },
  {
    titre: "Vente",
    liens: [
      { to: "/caisse", label: "Caisse & ventes", icon: ShoppingCart },
      { to: "/historique-ventes", label: "Historique des ventes", icon: ReceiptText },
      { to: "/caisse/mouvements", label: "Mouvements de caisse", icon: SlidersHorizontal },
      { to: "/clients-dettes", label: "Clients & dettes", icon: UsersRound },
    ],
  },
  {
    titre: "Stock",
    liens: [
      { to: "/articles", label: "Articles", icon: Boxes },
      { to: "/inventaires", label: "Inventaires", icon: ClipboardList },
      { to: "/approvisionnements", label: "Approvisionnements", icon: PackagePlus },
      { to: "/fournisseurs", label: "Fournisseurs", icon: Building2 },
      { to: "/retours-fournisseurs", label: "Retours fournisseurs", icon: PackageMinus },
    ],
  },
  {
    titre: "Gestion",
    liens: [
      { to: "/statistiques", label: "Statistiques", icon: ChartNoAxesCombined },
      { to: "/journal-operations", label: "Journal des opérations", icon: ScrollText },
      { to: "/entreprise", label: "Entreprise", icon: Store },
      { to: "/admin/utilisateurs", label: "Administration", icon: ShieldCheck },
    ],
  },
];

interface CashSession {
  id: number;
  statut: "OUVERTE" | "FERMEE";
}

export default function AppLayout() {
  const { user, logout } = useAuth();
  const { entreprise, logoUrl } = useEntreprise();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [cashOpen, setCashOpen] = useState<boolean | null>(null);
  const [logoEnErreur, setLogoEnErreur] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    api
      .get<CashSession>("/referentiels/sessions-caisse/ouverte")
      .then(({ data }) => {
        if (active) setCashOpen(data.statut === "OUVERTE");
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (typeof error === "object" && error !== null && "response" in error) {
          const status = (error as { response?: { status?: number } }).response?.status;
          if (status === 404) {
            setCashOpen(false);
            return;
          }
        }
        setCashOpen(null);
      });
    return () => {
      active = false;
    };
  }, []);

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  const roleLabel = user?.roles[0]?.replace(/^ROLE_/, "").replaceAll("_", " ").toLowerCase() ?? "utilisateur";
  const normalizedRoles = user?.roles.map((role) => role.toUpperCase().replace(/^ROLE_/, "")) ?? [];
  const aUnDesRoles = (...roles: string[]) => normalizedRoles.some((role) => roles.includes(role));
  const permis = (to: string): boolean => {
    if (to === "/admin/utilisateurs") return aUnDesRoles("ADMIN");
    if (to === "/entreprise") return aUnDesRoles("ADMIN", "MANAGER");
    if (["/approvisionnements", "/fournisseurs", "/retours-fournisseurs", "/inventaires", "/statistiques", "/journal-operations"].includes(to)) {
      return aUnDesRoles("ADMIN", "MANAGER", "GESTIONNAIRE");
    }
    if (to === "/caisse/mouvements") return aUnDesRoles("ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER");
    // Le tableau de bord lit des statistiques, et le catalogue se modifie : ni l'un ni l'autre ne concerne le caissier.
    if (["/dashboard", "/articles"].includes(to)) return aUnDesRoles("ADMIN", "MANAGER", "GESTIONNAIRE");
    return true;
  };
  const groupesVisibles = groupes
    .map((groupe) => ({ ...groupe, liens: groupe.liens.filter(({ to }) => permis(to)) }))
    .filter((groupe) => groupe.liens.length > 0);

  const nomAffiche = entreprise?.nom ?? "GesStock";
  const urlLogo = logoUrl("miniature");
  const logo = urlLogo !== null && urlLogo !== logoEnErreur ? urlLogo : null;

  return (
    <div className="min-h-screen bg-fond">
      {mobileOpen && (
        <button
          aria-label="Fermer le menu"
          className="fixed inset-0 z-30 bg-slate-950/40 lg:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex flex-col border-r border-slate-200 bg-white transition-[width,transform] duration-200 ${
          collapsed ? "w-[68px]" : "w-[248px]"
        } ${mobileOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"}`}
      >
        {/* L'application porte le nom du magasin : c'est lui que le personnel reconnaît, pas la version du logiciel. */}
        <div className={`flex h-16 shrink-0 items-center justify-between border-b border-slate-200 ${collapsed ? "px-3" : "px-4"}`}>
          <div className="flex min-w-0 items-center gap-3">
            {logo ? (
              <img
                src={logo}
                alt=""
                onError={() => setLogoEnErreur(logo)}
                className="size-9 shrink-0 rounded-lg border border-slate-200 bg-white object-contain p-0.5"
              />
            ) : (
              <Marque taille={36} className="text-emerald-600" />
            )}
            {!collapsed && (
              <div className="min-w-0 leading-tight">
                <p className="truncate text-[15px] font-bold text-slate-900">{nomAffiche}</p>
                <p className="truncate text-xs text-slate-500">{entreprise ? "GesStock" : "Gestion de commerce"}</p>
              </div>
            )}
          </div>
          <button
            className="icon-button lg:hidden"
            onClick={() => setMobileOpen(false)}
            aria-label="Fermer la navigation"
          >
            <X size={18} />
          </button>
        </div>

        {/* Seule cette zone défile : sans min-h-0 + overflow-y-auto, les liens qui dépassent la hauteur de l'écran sont coupés et inaccessibles. */}
        <div className={`min-h-0 flex-1 overflow-y-auto overscroll-contain pb-4 pt-3 [scrollbar-width:thin] ${collapsed ? "px-2" : "px-3"}`}>
          <nav aria-label="Navigation principale">
            {groupesVisibles.map((groupe, index) => (
              <div key={groupe.titre ?? "accueil"} className={index === 0 ? "" : collapsed ? "mt-3 border-t border-slate-200 pt-3" : "mt-4"}>
                {groupe.titre && !collapsed && <p className="mb-1 px-3 text-xs font-semibold text-slate-500">{groupe.titre}</p>}
                <div className="space-y-0.5">
                  {groupe.liens.map(({ to, label, icon: Icon }) => (
                    <NavLink
                      key={to}
                      to={to}
                      title={collapsed ? label : undefined}
                      onClick={() => setMobileOpen(false)}
                      className={({ isActive }) =>
                        `flex h-9 items-center gap-3 rounded-lg border-l-[3px] px-2.5 text-sm transition-colors ${
                          isActive
                            ? "border-emerald-600 bg-emerald-50 font-semibold text-emerald-800"
                            : "border-transparent font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                        } ${collapsed ? "justify-center px-0" : ""}`
                      }
                    >
                      <Icon size={18} strokeWidth={1.9} className="shrink-0" />
                      {!collapsed && <span className="truncate">{label}</span>}
                    </NavLink>
                  ))}
                </div>
              </div>
            ))}
          </nav>
        </div>

        <div className="hidden shrink-0 border-t border-slate-200 p-2 lg:block">
          <button
            onClick={() => setCollapsed((value) => !value)}
            className={`flex h-9 w-full items-center gap-2 rounded-lg px-2.5 text-sm font-medium text-slate-500 hover:bg-slate-100 hover:text-slate-900 ${collapsed ? "justify-center px-0" : ""}`}
            aria-label={collapsed ? "Déployer la barre latérale" : "Rétracter la barre latérale"}
          >
            {collapsed ? <PanelLeftOpen size={17} /> : <><PanelLeftClose size={17} /> Réduire le menu</>}
          </button>
        </div>
      </aside>

      <div className={`min-h-screen transition-[padding] duration-200 ${collapsed ? "lg:pl-[68px]" : "lg:pl-[248px]"}`}>
        <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b border-slate-200 bg-white px-4 md:px-6">
          <div className="flex items-center gap-3">
            <button
              className="icon-button lg:hidden"
              onClick={() => setMobileOpen(true)}
              aria-label="Ouvrir la navigation"
            >
              <Menu size={20} />
            </button>
            <p
              className={`flex items-center gap-2 text-sm font-semibold ${
                cashOpen === true ? "text-emerald-800" : cashOpen === false ? "text-amber-800" : "text-slate-500"
              }`}
            >
              <span className={`size-2 rounded-full ${cashOpen === true ? "bg-emerald-600" : cashOpen === false ? "bg-amber-600" : "bg-slate-400"}`} />
              Caisse {cashOpen === true ? "ouverte" : cashOpen === false ? "fermée" : "indisponible"}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <div className="hidden text-right leading-tight sm:block">
              <p className="text-sm font-semibold text-slate-900">{user?.username}</p>
              <p className="text-xs capitalize text-slate-500">{roleLabel}</p>
            </div>
            <button onClick={handleLogout} className="icon-button" aria-label="Déconnexion" title="Déconnexion">
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-[1440px] p-4 md:p-6">
          {/* Le menu reste affiché pendant le chargement d'un écran. */}
          <Suspense fallback={<p role="status" className="py-24 text-center text-sm font-medium text-slate-500">Chargement…</p>}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  );
}
