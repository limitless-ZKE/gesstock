import { lazy, useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import AppLayout from "./components/AppLayout";
import ProtectedRoute from "./components/ProtectedRoute";
import HomeRedirect from "./components/HomeRedirect";
import LoginPage from "./pages/LoginPage";

// Chaque écran est un morceau à part, chargé à la première visite : la connexion n'embarque pas la caisse, l'inventaire, etc.
const ApprovisionnementsPage = lazy(() => import("./pages/ApprovisionnementsPage"));
const AdminUsersPage = lazy(() => import("./pages/AdminUsersPage"));
const ArticlesPage = lazy(() => import("./pages/ArticlesPage"));
const CaissePage = lazy(() => import("./pages/CaissePage"));
const CaisseMouvementsPage = lazy(() => import("./pages/CaisseMouvementsPage"));
const ClientsDettesPage = lazy(() => import("./pages/ClientsDettesPage"));
const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const EntreprisePage = lazy(() => import("./pages/EntreprisePage"));
const FournisseursPage = lazy(() => import("./pages/FournisseursPage"));
const InventairesPage = lazy(() => import("./pages/InventairesPage"));
const JournalOperationsPage = lazy(() => import("./pages/JournalOperationsPage"));
const RetoursFournisseursPage = lazy(() => import("./pages/RetoursFournisseursPage"));
const StatistiquesPage = lazy(() => import("./pages/StatistiquesPage"));
const VentesHistoriquePage = lazy(() => import("./pages/VentesHistoriquePage"));

export default function App() {
  const [notification, setNotification] = useState("");

  useEffect(() => {
    const showError = (event: Event) => {
      const message = (event as CustomEvent<string>).detail;
      setNotification(message || "Une erreur est survenue.");
      window.setTimeout(() => setNotification(""), 5000);
    };
    window.addEventListener("gesstock:api-error", showError);
    return () => window.removeEventListener("gesstock:api-error", showError);
  }, []);

  return (
    <>
      {notification && (
        <div role="alert" className="fixed right-5 top-5 z-[100] max-w-sm rounded-xl border border-rose-200 bg-white px-4 py-3 text-sm font-semibold text-rose-700 shadow-xl">
          {notification}
        </div>
      )}
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<HomeRedirect />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/caisse" element={<CaissePage />} />
            <Route path="/historique-ventes" element={<VentesHistoriquePage />} />
            <Route path="/caisse/mouvements" element={<CaisseMouvementsPage />} />
            <Route path="/articles" element={<ArticlesPage />} />
            <Route path="/approvisionnements" element={<ApprovisionnementsPage />} />
            <Route path="/fournisseurs" element={<FournisseursPage />} />
            <Route path="/retours-fournisseurs" element={<RetoursFournisseursPage />} />
            <Route path="/inventaires" element={<InventairesPage />} />
            <Route path="/entreprise" element={<EntreprisePage />} />
            <Route path="/admin/utilisateurs" element={<AdminUsersPage />} />
            <Route path="/clients-dettes" element={<ClientsDettesPage />} />
            <Route path="/statistiques" element={<StatistiquesPage />} />
            <Route path="/journal-operations" element={<JournalOperationsPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
