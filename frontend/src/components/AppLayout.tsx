import { Suspense, useEffect, useState } from "react";
import {
  Boxes,
  Building2,
  ChartNoAxesCombined,
  ChevronRight,
  ClipboardList,
  LayoutDashboard,
  LogOut,
  Menu,
  PackageMinus,
  PackagePlus,
  PanelLeftClose,
  PanelLeftOpen,
  ReceiptText,
  ShoppingCart,
  ShieldCheck,
  SlidersHorizontal,
  Store,
  UsersRound,
  X,
} from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";

const navigation = [
  { to: "/dashboard", label: "Tableau de bord", icon: LayoutDashboard },
  { to: "/caisse", label: "Caisse & ventes", icon: ShoppingCart },
  { to: "/historique-ventes", label: "Historique des ventes", icon: ReceiptText },
  { to: "/caisse/mouvements", label: "Mouvements de caisse", icon: SlidersHorizontal },
  { to: "/articles", label: "Articles", icon: Boxes },
  { to: "/inventaires", label: "Inventaires", icon: ClipboardList },
  { to: "/approvisionnements", label: "Approvisionnements", icon: PackagePlus },
  { to: "/fournisseurs", label: "Fournisseurs", icon: Building2 },
  { to: "/retours-fournisseurs", label: "Retours fournisseurs", icon: PackageMinus },
  { to: "/clients-dettes", label: "Clients & dettes", icon: UsersRound },
  { to: "/statistiques", label: "Statistiques", icon: ChartNoAxesCombined },
  { to: "/entreprise", label: "Entreprise", icon: Store },
  { to: "/admin/utilisateurs", label: "Administration", icon: ShieldCheck },
];

interface CashSession {
  id: number;
  statut: "OUVERTE" | "FERMEE";
}

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [cashOpen, setCashOpen] = useState<boolean | null>(null);

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

  const roleLabel = user?.roles[0]?.replaceAll("_", " ") ?? "Utilisateur";
  const normalizedRoles = user?.roles.map((role) => role.toUpperCase().replace(/^ROLE_/, "")) ?? [];
  const visibleNavigation = navigation.filter(({ to }) => {
    if (to === "/admin/utilisateurs") return normalizedRoles.includes("ADMIN");
    if (to === "/entreprise") return normalizedRoles.some((role) => ["ADMIN", "MANAGER"].includes(role));
    if (["/approvisionnements", "/fournisseurs", "/retours-fournisseurs", "/inventaires"].includes(to)) {
      return normalizedRoles.some((role) => ["ADMIN", "MANAGER", "GESTIONNAIRE"].includes(role));
    }
    if (to === "/statistiques") return normalizedRoles.some((role) => ["ADMIN", "MANAGER", "GESTIONNAIRE"].includes(role));
    if (to === "/caisse/mouvements") return normalizedRoles.some((role) => ["ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER"].includes(role));
    // Le tableau de bord lit des statistiques, et le catalogue se modifie : ni l'un ni l'autre ne concerne le caissier.
    if (["/dashboard", "/articles"].includes(to)) return normalizedRoles.some((role) => ["ADMIN", "MANAGER", "GESTIONNAIRE"].includes(role));
    return true;
  });

  return (
    <div className="min-h-screen bg-[#f5f7fb]">
      {mobileOpen && (
        <button
          aria-label="Fermer le menu"
          className="fixed inset-0 z-30 bg-slate-950/40 lg:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex flex-col border-r border-slate-200 bg-white transition-all duration-200 ${
          collapsed ? "w-[84px]" : "w-[264px]"
        } ${mobileOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"}`}
      >
        <div className="flex h-[78px] items-center justify-between border-b border-slate-100 px-5">
          <div className="flex items-center gap-3 overflow-hidden">
            <div className="grid size-10 shrink-0 place-items-center rounded-xl bg-emerald-600 text-white shadow-sm shadow-emerald-200">
              <Boxes size={21} strokeWidth={2.4} />
            </div>
            {!collapsed && (
              <div className="leading-tight">
                <p className="font-extrabold tracking-tight text-slate-900">GesStock</p>
                <p className="mt-1 text-[10px] font-bold uppercase tracking-[0.16em] text-emerald-600">
                  Gestion 3.0
                </p>
              </div>
            )}
          </div>
          <button
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 lg:hidden"
            onClick={() => setMobileOpen(false)}
            aria-label="Fermer la navigation"
          >
            <X size={18} />
          </button>
        </div>

        <div className={`px-4 pt-7 ${collapsed ? "px-3" : ""}`}>
          {!collapsed && (
            <p className="mb-3 px-3 text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">
              Espace de travail
            </p>
          )}
          <nav className="space-y-1.5">
            {visibleNavigation.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                title={collapsed ? label : undefined}
                onClick={() => setMobileOpen(false)}
                className={({ isActive }) =>
                  `group flex h-11 items-center gap-3 rounded-xl px-3 text-[13px] font-semibold transition ${
                    isActive
                      ? "bg-emerald-50 text-emerald-700"
                      : "text-slate-500 hover:bg-slate-50 hover:text-slate-900"
                  } ${collapsed ? "justify-center px-0" : ""}`
                }
              >
                <Icon size={18} strokeWidth={2} />
                {!collapsed && <span>{label}</span>}
                {!collapsed && to === "/caisse" && (
                  <ChevronRight className="ml-auto opacity-50" size={15} />
                )}
              </NavLink>
            ))}
          </nav>
        </div>

        <div className="mt-auto p-4">
          {!collapsed && (
            <div className="rounded-2xl bg-slate-50 p-3.5">
              <div className="flex items-center gap-3">
                <div className="grid size-9 shrink-0 place-items-center rounded-full bg-slate-200 text-xs font-bold text-slate-700">
                  {user?.username.slice(0, 2).toUpperCase()}
                </div>
                <div className="min-w-0">
                  <p className="truncate text-xs font-bold text-slate-800">{user?.username}</p>
                  <p className="mt-0.5 truncate text-[10px] font-medium text-slate-500">{roleLabel}</p>
                </div>
              </div>
              <button
                onClick={handleLogout}
                className="mt-3 flex w-full items-center gap-2 rounded-lg px-2 py-2 text-xs font-semibold text-slate-500 hover:bg-white hover:text-rose-600"
              >
                <LogOut size={15} />
                Déconnexion
              </button>
            </div>
          )}
          <button
            onClick={() => setCollapsed((value) => !value)}
            className="mt-3 hidden w-full items-center justify-center rounded-lg p-2 text-slate-400 hover:bg-slate-100 lg:flex"
            aria-label={collapsed ? "Déployer la barre latérale" : "Rétracter la barre latérale"}
          >
            {collapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}
          </button>
        </div>
      </aside>

      <div className={`min-h-screen transition-[padding] duration-200 ${collapsed ? "lg:pl-[84px]" : "lg:pl-[264px]"}`}>
        <header className="sticky top-0 z-20 flex h-[78px] items-center justify-between border-b border-slate-200/80 bg-white/90 px-4 backdrop-blur md:px-8">
          <div className="flex items-center gap-3">
            <button
              className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 lg:hidden"
              onClick={() => setMobileOpen(true)}
              aria-label="Ouvrir la navigation"
            >
              <Menu size={20} />
            </button>
            <div className="hidden items-center gap-2 text-xs font-medium text-slate-400 sm:flex">
              <span>GesStock</span>
              <ChevronRight size={14} />
              <span className="text-slate-700">Gestion des opérations</span>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div
              className={`flex items-center gap-2 rounded-full px-3 py-2 text-[11px] font-bold ${
                cashOpen === true
                  ? "bg-emerald-50 text-emerald-700"
                  : cashOpen === false
                    ? "bg-amber-50 text-amber-700"
                    : "bg-slate-100 text-slate-500"
              }`}
            >
              <span className={`size-1.5 rounded-full ${cashOpen === true ? "bg-emerald-500" : cashOpen === false ? "bg-amber-500" : "bg-slate-400"}`} />
              Caisse {cashOpen === true ? "ouverte" : cashOpen === false ? "fermée" : "indisponible"}
            </div>
            <button
              onClick={handleLogout}
              className="rounded-xl border border-slate-200 p-2.5 text-slate-500 hover:border-rose-200 hover:text-rose-600"
              aria-label="Déconnexion"
              title="Déconnexion"
            >
              <LogOut size={17} />
            </button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-[1440px] p-4 md:p-8">
          {/* Le menu reste affiché pendant le chargement d'un écran. */}
          <Suspense fallback={<p role="status" className="py-24 text-center text-sm font-medium text-slate-400">Chargement…</p>}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  );
}
