import { useState, type FormEvent } from "react";
import axios from "axios";
import { ArrowRight, Boxes, Eye, EyeOff, LockKeyhole } from "lucide-react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

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
  if (user) return <Navigate to="/dashboard" replace />;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await login(username, password);
      const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
      navigate(from ?? "/dashboard", { replace: true });
    } catch (loginError) {
      if (axios.isAxiosError(loginError) && loginError.response?.status === 401) {
        setError("Identifiant ou mot de passe incorrect.");
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
    <main className="grid min-h-screen bg-white lg:grid-cols-[1.05fr_0.95fr]">
      <section className="relative hidden overflow-hidden bg-[#102b26] p-12 text-white lg:flex lg:flex-col lg:justify-between xl:p-16">
        <div className="absolute -right-32 -top-24 size-[520px] rounded-full border border-white/10" />
        <div className="absolute -right-8 -top-10 size-[390px] rounded-full border border-white/10" />
        <div className="absolute -bottom-52 -left-32 size-[520px] rounded-full bg-emerald-500/10 blur-2xl" />
        <div className="relative flex items-center gap-3">
          <div className="grid size-12 place-items-center rounded-2xl bg-emerald-500 text-white">
            <Boxes size={25} />
          </div>
          <div>
            <p className="text-lg font-extrabold tracking-tight">GesStock</p>
            <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-emerald-300">Gestion 3.0</p>
          </div>
        </div>
        <div className="relative max-w-lg">
          <p className="mb-5 text-xs font-bold uppercase tracking-[0.2em] text-emerald-300">Pilotez votre activité</p>
          <h1 className="text-5xl font-semibold leading-[1.12] tracking-tight xl:text-6xl">
            Votre stock,
            <br />
            <span className="text-emerald-300">en toute clarté.</span>
          </h1>
          <p className="mt-6 max-w-md text-sm leading-7 text-emerald-50/70">
            Gérez les ventes, les approvisionnements et les performances de votre entreprise depuis un espace unique.
          </p>
        </div>
        <p className="relative text-xs text-emerald-50/40">© 2026 GesStock · Gestion simplifiée</p>
      </section>

      <section className="flex min-h-screen items-center justify-center px-6 py-12">
        <div className="w-full max-w-[410px]">
          <div className="mb-10 flex items-center gap-3 lg:hidden">
            <div className="grid size-11 place-items-center rounded-xl bg-emerald-600 text-white"><Boxes size={23} /></div>
            <div>
              <p className="font-extrabold text-slate-900">GesStock</p>
              <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-emerald-600">Gestion 3.0</p>
            </div>
          </div>
          <div className="mb-9">
            <div className="mb-5 grid size-12 place-items-center rounded-2xl bg-emerald-50 text-emerald-700">
              <LockKeyhole size={21} />
            </div>
            <h2 className="text-3xl font-bold tracking-tight text-slate-900">Bon retour</h2>
            <p className="mt-2 text-sm text-slate-500">Connectez-vous à votre espace de gestion.</p>
          </div>

          <form className="space-y-5" onSubmit={handleSubmit}>
            <label className="block">
              <span className="mb-2 block text-xs font-bold text-slate-700">Nom d’utilisateur</span>
              <input
                autoComplete="username"
                required
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                className="input-field"
                placeholder="Votre identifiant"
              />
            </label>
            <label className="block">
              <span className="mb-2 block text-xs font-bold text-slate-700">Mot de passe</span>
              <span className="relative block">
                <input
                  autoComplete="current-password"
                  required
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="input-field pr-12"
                  placeholder="Votre mot de passe"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  className="absolute inset-y-0 right-0 grid w-12 place-items-center text-slate-400 hover:text-slate-700"
                  aria-label={showPassword ? "Masquer le mot de passe" : "Afficher le mot de passe"}
                >
                  {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </span>
            </label>
            {error && <p role="alert" className="rounded-xl bg-rose-50 px-4 py-3 text-xs font-semibold text-rose-700">{error}</p>}
            <button disabled={submitting} className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 text-sm font-bold text-white shadow-lg shadow-emerald-200 transition hover:bg-emerald-700 disabled:cursor-wait disabled:opacity-60">
              {submitting ? "Connexion…" : "Se connecter"}
              {!submitting && <ArrowRight size={17} />}
            </button>
          </form>
          <p className="mt-8 text-center text-[11px] text-slate-400">Accès sécurisé · GesStock 3.0</p>
        </div>
      </section>
    </main>
  );
}
