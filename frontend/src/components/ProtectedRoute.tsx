import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function ProtectedRoute() {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="grid min-h-screen place-items-center text-sm font-medium text-slate-500">
        Chargement de votre session…
      </div>
    );
  }

  return user ? (
    <Outlet />
  ) : (
    <Navigate to="/login" replace state={{ from: location }} />
  );
}
