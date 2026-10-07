import { useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import { Check, LoaderCircle, Pencil, Plus, Search, ShieldCheck, UserRound, UserRoundX, X } from "lucide-react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";

type Role = { id: number; name: string };
type User = { id: number; username: string; actif: boolean; roles: Role[] };
type UserForm = { username: string; password: string; roleIds: number[]; actif: boolean };

const knownRoles = ["ADMIN", "CAISSIER", "GESTIONNAIRE"];
const accessMatrix: { module: string; roles: string[] }[] = [
  { module: "Tableau de bord", roles: ["ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER", "VENDEUR", "USER"] },
  { module: "Caisse et ventes", roles: ["ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER", "VENDEUR", "USER"] },
  { module: "Articles", roles: ["ADMIN", "MANAGER", "GESTIONNAIRE", "VENDEUR"] },
  { module: "Approvisionnements / fournisseurs", roles: ["ADMIN", "MANAGER", "GESTIONNAIRE"] },
  { module: "Clients, proformas et dettes", roles: ["ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER", "VENDEUR", "USER"] },
  { module: "Statistiques et journal", roles: ["ADMIN", "MANAGER", "GESTIONNAIRE"] },
  { module: "Administration des comptes", roles: ["ADMIN"] },
];

function errorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError<{ detail?: string; message?: string }>(error)) return error.response?.data?.detail ?? error.response?.data?.message ?? fallback;
  return fallback;
}

function normalizeRole(name: string) {
  return name.toUpperCase().replace(/^ROLE_/, "");
}

export default function AdminUsersPage() {
  const { user } = useAuth();
  const isAdmin = user?.roles.some((role) => normalizeRole(role) === "ADMIN") ?? false;
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [search, setSearch] = useState("");
  const [formUser, setFormUser] = useState<User | "new" | null>(null);
  const [form, setForm] = useState<UserForm>({ username: "", password: "", roleIds: [], actif: true });
  const [saving, setSaving] = useState(false);

  async function loadData() {
    setLoading(true);
    setError("");
    try {
      const [usersResponse, rolesResponse] = await Promise.all([
        api.get<User[]>("/referentiels/utilisateurs"),
        api.get<Role[]>("/referentiels/roles"),
      ]);
      setUsers(usersResponse.data);
      setRoles(rolesResponse.data);
    } catch (reason) {
      setError(errorMessage(reason, "Impossible de charger les utilisateurs et rôles."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { if (isAdmin) void loadData(); }, [isAdmin]);
  if (!isAdmin) return <Navigate to="/dashboard" replace />;

  function openForm(target: User | "new") {
    setFormUser(target);
    setForm(target === "new" ? { username: "", password: "", roleIds: [], actif: true } : {
      username: target.username,
      password: "",
      roleIds: target.roles.map((role) => role.id),
      actif: target.actif,
    });
  }

  async function saveUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    try {
      if (formUser === "new") {
        await api.post("/referentiels/utilisateurs", form);
      } else if (formUser) {
        await api.put(`/referentiels/utilisateurs/${formUser.id}`, {
          ...form,
          password: form.password.trim() || null,
        });
      }
      setNotice(formUser === "new" ? "Le compte utilisateur a été créé." : "Le compte utilisateur a été modifié.");
      setFormUser(null);
      await loadData();
    } catch (reason) {
      setError(errorMessage(reason, "L’enregistrement du compte a échoué."));
    } finally {
      setSaving(false);
    }
  }

  async function toggleUser(target: User) {
    const actif = !target.actif;
    if (!window.confirm(`${actif ? "Réactiver" : "Désactiver"} le compte ${target.username} ?`)) return;
    try {
      await api.put(`/referentiels/utilisateurs/${target.id}`, {
        username: target.username,
        password: null,
        actif,
        roleIds: target.roles.map((role) => role.id),
      });
      setNotice(`Le compte ${target.username} est ${actif ? "actif" : "désactivé"}.`);
      await loadData();
    } catch (reason) {
      setError(errorMessage(reason, "La modification du statut a échoué."));
    }
  }

  const visibleUsers = users.filter((item) => item.username.toLocaleLowerCase("fr").includes(search.trim().toLocaleLowerCase("fr")));

  return <div className="space-y-6">
    <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><p className="mb-1 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Administration sécurisée</p><h1 className="text-3xl font-bold tracking-tight text-slate-900">Utilisateurs & rôles</h1><p className="mt-1.5 text-sm text-slate-500">Créez les comptes, attribuez leurs rôles et désactivez les accès inutilisés.</p></div><button onClick={() => openForm("new")} className="button-primary"><Plus size={16} />Nouvel utilisateur</button></header>
    {notice && <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-xs font-semibold text-emerald-800">{notice}<button onClick={() => setNotice("")} className="float-right"><X size={14} /></button></div>}
    {error && <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs font-semibold text-rose-800">{error}<button onClick={() => setError("")} className="float-right"><X size={14} /></button></div>}

    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-col justify-between gap-3 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:px-5"><div><h2 className="text-sm font-bold text-slate-900">Comptes utilisateurs</h2><p className="mt-1 text-[10px] text-slate-500">{users.length} compte(s) · les mots de passe sont hachés côté serveur</p></div><label className="relative block sm:w-64"><Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" /><input className="input-field pl-9" placeholder="Rechercher…" value={search} onChange={(event) => setSearch(event.target.value)} /></label></div>
      <div className="overflow-x-auto"><table className="w-full min-w-[720px] text-left"><thead className="bg-slate-50 text-[9px] font-bold uppercase tracking-wider text-slate-400"><tr><th className="px-5 py-3">Utilisateur</th><th className="px-4 py-3">Rôles attribués</th><th className="px-4 py-3">Accès</th><th className="px-4 py-3">Statut</th><th className="px-5 py-3 text-right">Actions</th></tr></thead><tbody className="divide-y divide-slate-100">{loading ? <tr><td colSpan={5} className="h-32 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={15} />Chargement…</td></tr> : visibleUsers.length ? visibleUsers.map((target) => <tr key={target.id} className="hover:bg-slate-50/70"><td className="px-5 py-3.5"><div className="flex items-center gap-2.5"><span className="grid size-8 place-items-center rounded-full bg-slate-100 text-slate-500"><UserRound size={15} /></span><span className="text-xs font-bold text-slate-800">{target.username}</span></div></td><td className="px-4 py-3.5"><div className="flex flex-wrap gap-1">{target.roles.map((role) => <span key={role.id} className="rounded-full bg-violet-50 px-2 py-1 text-[9px] font-bold text-violet-700">{normalizeRole(role.name)}</span>)}</div></td><td className="px-4 py-3.5 text-[10px] text-slate-500">{accessSummary(target.roles)}</td><td className="px-4 py-3.5"><span className={`rounded-full px-2.5 py-1 text-[9px] font-bold ${target.actif ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-500"}`}>{target.actif ? "Actif" : "Désactivé"}</span></td><td className="px-5 py-3.5"><div className="flex justify-end gap-1"><button title="Modifier" onClick={() => openForm(target)} className="icon-button text-slate-400 hover:text-emerald-700"><Pencil size={14} /></button><button title={target.actif ? "Désactiver" : "Réactiver"} onClick={() => void toggleUser(target)} className="icon-button text-slate-400 hover:text-rose-600">{target.actif ? <UserRoundX size={14} /> : <Check size={14} />}</button></div></td></tr>) : <tr><td colSpan={5} className="h-32 text-center text-xs text-slate-400">Aucun utilisateur.</td></tr>}</tbody></table></div>
    </section>

    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"><div className="border-b border-slate-100 p-5"><div className="flex items-center gap-2"><ShieldCheck size={17} className="text-emerald-700" /><h2 className="text-sm font-bold text-slate-900">Accès aux modules par rôle</h2></div><p className="mt-1 text-[10px] text-slate-500">Matrice appliquée par les règles d’autorisation du backend. Elle est informative ici ; seules les rôles déjà reconnus par la configuration accordent des permissions.</p></div><div className="overflow-x-auto"><table className="w-full min-w-[650px] text-left"><thead className="bg-slate-50 text-[9px] font-bold uppercase tracking-wider text-slate-400"><tr><th className="px-5 py-3">Module</th>{knownRoles.map((role) => <th key={role} className="px-4 py-3 text-center">{role}</th>)}</tr></thead><tbody className="divide-y divide-slate-100">{accessMatrix.map((item) => <tr key={item.module}><td className="px-5 py-3 text-xs font-semibold text-slate-700">{item.module}</td>{knownRoles.map((role) => <td key={role} className="px-4 py-3 text-center">{item.roles.includes(role) ? <Check size={15} className="mx-auto text-emerald-600" /> : <span className="text-slate-200">—</span>}</td>)}</tr>)}</tbody></table></div><div className="border-t border-slate-100 px-5 py-3 text-[10px] text-slate-400">Rôles disponibles depuis le serveur : {roles.map((role) => normalizeRole(role.name)).join(", ") || "—"}. Demandez une mise à jour de la configuration backend pour modifier les permissions d’un rôle.</div></section>

    {formUser && <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setFormUser(null); }}><section role="dialog" aria-modal="true" aria-label={formUser === "new" ? "Créer un utilisateur" : "Modifier un utilisateur"} className="my-auto w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl"><div className="mb-5 flex items-center justify-between"><h2 className="text-lg font-bold text-slate-900">{formUser === "new" ? "Créer un utilisateur" : "Modifier l’utilisateur"}</h2><button onClick={() => setFormUser(null)} className="icon-button"><X size={16} /></button></div><form onSubmit={(event) => void saveUser(event)} className="space-y-4"><label className="block"><span className="mb-1.5 block text-[10px] font-bold text-slate-600">Identifiant</span><input required maxLength={80} className="input-field" value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} /></label><label className="block"><span className="mb-1.5 block text-[10px] font-bold text-slate-600">Mot de passe {formUser !== "new" && "(laisser vide pour conserver l’actuel)"}</span><input required={formUser === "new"} minLength={8} maxLength={72} type="password" autoComplete="new-password" className="input-field" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label><fieldset><legend className="mb-2 text-[10px] font-bold text-slate-600">Rôles</legend><div className="grid gap-2 sm:grid-cols-2">{roles.map((role) => <label key={role.id} className="flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2.5 text-xs font-semibold text-slate-700"><input type="checkbox" checked={form.roleIds.includes(role.id)} onChange={(event) => setForm({ ...form, roleIds: event.target.checked ? [...form.roleIds, role.id] : form.roleIds.filter((id) => id !== role.id) })} />{normalizeRole(role.name)}</label>)}</div>{!form.roleIds.length && <p className="mt-1 text-[10px] text-rose-600">Attribuez au moins un rôle.</p>}</fieldset>{formUser !== "new" && <label className="flex items-center gap-2 text-xs font-semibold text-slate-700"><input type="checkbox" checked={form.actif} onChange={(event) => setForm({ ...form, actif: event.target.checked })} />Compte actif</label>}<div className="flex justify-end gap-2 border-t border-slate-100 pt-4"><button type="button" disabled={saving} onClick={() => setFormUser(null)} className="button-secondary">Annuler</button><button disabled={saving || !form.roleIds.length} className="button-primary">{saving ? <LoaderCircle size={14} className="animate-spin" /> : <Check size={14} />}{saving ? "Enregistrement…" : "Enregistrer"}</button></div></form></section></div>}
  </div>;
}

function accessSummary(roles: Role[]) {
  const assigned = roles.map((role) => normalizeRole(role.name));
  if (assigned.includes("ADMIN")) return "Tous les modules";
  if (assigned.some((role) => ["MANAGER", "GESTIONNAIRE"].includes(role))) return "Gestion, caisse et rapports";
  if (assigned.some((role) => ["CAISSIER", "VENDEUR", "USER"].includes(role))) return "Caisse et clients";
  return "Accès limités";
}
