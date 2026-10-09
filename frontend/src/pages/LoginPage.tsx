import { useState, type FormEvent } from "react";
import axios from "axios";
import { Eye, EyeOff } from "lucide-react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import Marque from "../components/Marque";
import { pageDAccueil } from "../lib/routes";

/** Bord dentelé du bas d'un ticket : une dent sur deux est creusée de 7 px. */
const DENTS = 18;
const BORD_DENTELE = `polygon(0 0, 100% 0, ${Array.from({ length: DENTS * 2 + 1 }, (_, i) => `${(100 - (i * 100) / (DENTS * 2)).toFixed(2)}% ${i % 2 === 0 ? "100%" : "calc(100% - 7px)"}`).join(", ")})`;

export default function LoginPage() {
  const { user, login, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  if (loading) return <div className="grid min-h-screen place-items-center">Chargement…</div>;
  if (user) return <Navigate to={pageDAccueil(user.roles)} replace />;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await login(username, password);
      const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
      navigate(from ?? "/", { replace: true });
    } catch (loginError) {
      if (axios.isAxiosError(loginError) && loginError.response?.status === 401) {
        setError("Identifiant ou mot de passe incorrect.");
      } else if (axios.isAxiosError<{ detail?: string }>(loginError) && loginError.response?.status === 429) {
        setError(loginError.response.data?.detail ?? "Trop de tentatives de connexion. Réessayez dans quelques minutes.");
      } else if (axios.isAxiosError(loginError) && !loginError.response) {
        setError("Le serveur GesStock est injoignable. Vérifiez qu’il est démarré puis réessayez.");
      } else {
        setError("Connexion impossible. Vérifiez vos identifiants puis réessayez.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="grid min-h-screen bg-fond lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <section className="flex min-h-screen flex-col justify-center px-6 py-12 sm:px-12 lg:px-20 xl:px-28">
        <div className="w-full max-w-[380px]">
          <div className="mb-10 flex items-center gap-3">
            <Marque taille={36} className="text-emerald-600" />
            <p className="text-lg font-bold text-slate-900">GesStock</p>
          </div>
          <h1 className="text-2xl font-bold text-slate-900">Connexion</h1>
          <p className="mt-2 text-sm text-slate-600">Entrez l’identifiant et le mot de passe remis par votre responsable.</p>

          <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
            <label className="block">
              <span className="mb-1.5 block text-sm font-semibold text-slate-800">Identifiant</span>
              <input
                autoComplete="username"
                required
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                className="input-field"
              />
            </label>
            <label className="block">
              <span className="mb-1.5 block text-sm font-semibold text-slate-800">Mot de passe</span>
              <span className="relative block">
                <input
                  autoComplete="current-password"
                  required
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="input-field pr-12"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  className="absolute inset-y-0 right-0 grid w-12 place-items-center text-slate-500 hover:text-slate-900"
                  aria-label={showPassword ? "Masquer le mot de passe" : "Afficher le mot de passe"}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </span>
            </label>
            {error && <p role="alert" className="border-l-4 border-rose-600 bg-rose-50 px-3 py-2.5 text-sm font-medium text-rose-800">{error}</p>}
            <button disabled={submitting} className="button-primary w-full disabled:cursor-wait">
              {submitting ? "Connexion…" : "Se connecter"}
            </button>
          </form>
        </div>
      </section>

      {/* Le ticket de caisse est ce que GesStock produit de plus concret : il sert d'illustration (exemple fictif, sans donnée réelle). */}
      <section aria-hidden="true" className="hidden items-center justify-center border-l border-slate-200 bg-slate-100 p-12 lg:flex">
        <div className="w-[300px] bg-white px-6 pb-10 pt-7 font-mono text-[13px] leading-6 text-slate-800" style={{ clipPath: BORD_DENTELE }}>
          <p className="text-center text-sm font-bold">BOUTIQUE DU MARCHÉ</p>
          <p className="text-center text-xs text-slate-500">Ticket de vente n° 1284</p>
          <div className="my-3 border-t border-dashed border-slate-400" />
          <p>Riz parfumé 5 kg</p>
          <p className="flex justify-between text-slate-600"><span>2 × 3 500</span><span>7 000</span></p>
          <p>Huile végétale 1 L</p>
          <p className="flex justify-between text-slate-600"><span>3 × 1 200</span><span>3 600</span></p>
          <p>Sucre 1 kg</p>
          <p className="flex justify-between text-slate-600"><span>1 × 900</span><span>900</span></p>
          <div className="my-3 border-t border-dashed border-slate-400" />
          <p className="flex justify-between text-base font-bold"><span>TOTAL</span><span>11 500 F</span></p>
          <p className="flex justify-between text-slate-600"><span>Espèces</span><span>12 000</span></p>
          <p className="flex justify-between text-slate-600"><span>Rendu</span><span>500</span></p>
          <p className="mt-4 text-center text-xs text-slate-500">Merci de votre visite</p>
        </div>
      </section>
    </main>
  );
}
