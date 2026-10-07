import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import AppLayout from "./components/AppLayout";
import ProtectedRoute from "./components/ProtectedRoute";
import ApprovisionnementsPage from "./pages/ApprovisionnementsPage";
import AdminUsersPage from "./pages/AdminUsersPage";
import ArticlesPage from "./pages/ArticlesPage";
import CaissePage from "./pages/CaissePage";
import CaisseMouvementsPage from "./pages/CaisseMouvementsPage";
import ClientsDettesPage from "./pages/ClientsDettesPage";
import DashboardPage from "./pages/DashboardPage";
import FournisseursPage from "./pages/FournisseursPage";
import LoginPage from "./pages/LoginPage";
import StatistiquesPage from "./pages/StatistiquesPage";
import VentesHistoriquePage from "./pages/VentesHistoriquePage";

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
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/caisse" element={<CaissePage />} />
            <Route path="/historique-ventes" element={<VentesHistoriquePage />} />
            <Route path="/caisse/mouvements" element={<CaisseMouvementsPage />} />
            <Route path="/articles" element={<ArticlesPage />} />
            <Route path="/approvisionnements" element={<ApprovisionnementsPage />} />
            <Route path="/fournisseurs" element={<FournisseursPage />} />
            <Route path="/admin/utilisateurs" element={<AdminUsersPage />} />
            <Route path="/clients-dettes" element={<ClientsDettesPage />} />
            <Route path="/statistiques" element={<StatistiquesPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </>
  );
}
