import { useEffect, useMemo, useState, type FormEvent } from "react";
import { LoaderCircle, Mail, MapPin, Pencil, Phone, Plus, ReceiptText, Search, Trash2, Truck, X } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import CompteFournisseurModal from "../components/CompteFournisseurModal";
import { money } from "../lib/format";
import { api } from "../services/api";
import { apiErrorMessage as messageFor } from "../services/apiError";

type Supplier = { id: number; nom: string; telephone: string | null; email: string | null; adresse: string | null };
type SupplierForm = { nom: string; telephone: string; email: string; adresse: string };
type Balance = { fournisseurId: number; soldeDu: number; creditDisponible: number };
const blank: SupplierForm = { nom: "", telephone: "", email: "", adresse: "" };

export default function FournisseursPage() {
  const { user } = useAuth();
  const isManager = (user?.roles ?? []).some((role) => ["ADMIN", "MANAGER"].includes(role.toUpperCase().replace(/^ROLE_/, "")));
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [balances, setBalances] = useState<Record<number, Balance>>({});
  const [accountOf, setAccountOf] = useState<Supplier | null>(null);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [modal, setModal] = useState<Supplier | "new" | null>(null);
  const [form, setForm] = useState<SupplierForm>(blank);
  const [saving, setSaving] = useState(false);

  async function load() {
    setLoading(true);
    setError("");
    try {
      const [list, accounts] = await Promise.all([
        api.get<Supplier[]>("/fournisseurs"),
        // Les soldes sont un plus : si leur chargement échoue, la liste des fournisseurs reste utilisable.
        api.get<Balance[]>("/fournisseurs/comptes").catch(() => ({ data: [] as Balance[] })),
      ]);
      setSuppliers(list.data);
      setBalances(Object.fromEntries(accounts.data.map((balance) => [balance.fournisseurId, balance])));
    } catch (reason) {
      setError(messageFor(reason, "Impossible de charger les fournisseurs."));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); }, []);

  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("fr");
    return suppliers.filter((supplier) => [supplier.nom, supplier.telephone, supplier.email, supplier.adresse]
      .some((value) => value?.toLocaleLowerCase("fr").includes(query)));
  }, [search, suppliers]);

  function open(target: Supplier | "new") {
    setModal(target);
    setForm(target === "new" ? blank : {
      nom: target.nom, telephone: target.telephone ?? "",
      email: target.email ?? "", adresse: target.adresse ?? "",
    });
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    try {
      if (modal === "new") await api.post("/fournisseurs", form);
      else if (modal) await api.put(`/fournisseurs/${modal.id}`, form);
      setNotice(modal === "new" ? "Fournisseur créé." : "Fournisseur modifié.");
      setModal(null);
      await load();
    } catch (reason) {
      setError(messageFor(reason, "L’enregistrement du fournisseur a échoué."));
    } finally {
      setSaving(false);
    }
  }

  async function remove(supplier: Supplier) {
    if (!window.confirm(`Supprimer le fournisseur « ${supplier.nom} » ?`)) return;
    try {
      await api.delete(`/fournisseurs/${supplier.id}`);
      setNotice("Fournisseur supprimé.");
      await load();
    } catch (reason) {
      setError(messageFor(reason, "Ce fournisseur ne peut pas être supprimé."));
    }
  }

  return <div className="space-y-6">
    <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><h1 className="text-2xl font-bold text-slate-900">Fournisseurs</h1><p className="mt-1.5 text-sm text-slate-500">Gérez les coordonnées de vos partenaires d’approvisionnement.</p></div><button onClick={() => open("new")} className="button-primary"><Plus size={16} />Nouveau fournisseur</button></header>
    {notice && <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-xs font-semibold text-emerald-800">{notice}<button onClick={() => setNotice("")} className="float-right"><X size={14} /></button></div>}
    {error && <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs font-semibold text-rose-800">{error}<button onClick={() => setError("")} className="float-right"><X size={14} /></button></div>}
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-col justify-between gap-3 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:px-5"><div><p className="text-sm font-bold text-slate-900">{suppliers.length} fournisseur(s)</p><p className="mt-1 text-xs text-slate-500">Coordonnées utilisées pour les bons de réception</p></div><label className="relative sm:w-72"><Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" /><input className="input-field pl-9" placeholder="Nom, téléphone, email…" value={search} onChange={(event) => setSearch(event.target.value)} /></label></div>
      <div className="overflow-x-auto"><table className="w-full min-w-[900px] text-left"><thead className="bg-slate-50 text-xs font-semibold text-slate-600"><tr><th className="px-5 py-3">Fournisseur</th><th className="px-4 py-3">Téléphone</th><th className="px-4 py-3">E-mail</th><th className="px-4 py-3">Adresse</th><th className="px-4 py-3 text-right">Compte</th><th className="px-5 py-3 text-right">Actions</th></tr></thead><tbody className="divide-y divide-slate-100">{loading ? <tr><td colSpan={6} className="h-36 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={15} />Chargement…</td></tr> : filtered.length ? filtered.map((supplier) => <tr key={supplier.id} className="hover:bg-slate-50/70"><td className="px-5 py-3.5"><div className="flex items-center gap-2.5"><span className="text-xs font-bold text-slate-800">{supplier.nom}</span></div></td><td className="px-4 py-3.5 text-xs text-slate-600">{supplier.telephone ? <span className="inline-flex items-center gap-1.5"><Phone size={12} className="text-slate-400" />{supplier.telephone}</span> : "—"}</td><td className="px-4 py-3.5 text-xs text-slate-600">{supplier.email ? <span className="inline-flex items-center gap-1.5"><Mail size={12} className="text-slate-400" />{supplier.email}</span> : "—"}</td><td className="px-4 py-3.5 text-xs text-slate-600">{supplier.adresse ? <span className="inline-flex items-center gap-1.5"><MapPin size={12} className="text-slate-400" />{supplier.adresse}</span> : "—"}</td><td className="px-4 py-3.5 text-right"><button onClick={() => setAccountOf(supplier)} title="Voir le compte, payer, saisir un avoir" className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-xs font-bold hover:bg-slate-100"><ReceiptText size={13} className="text-slate-400" />{(balances[supplier.id]?.soldeDu ?? 0) > 0 ? <span className="text-rose-700">Dû {money.format(balances[supplier.id].soldeDu)}</span> : (balances[supplier.id]?.creditDisponible ?? 0) > 0 ? <span className="text-emerald-700">Crédit {money.format(balances[supplier.id].creditDisponible)}</span> : <span className="text-slate-500">Compte soldé</span>}</button></td><td className="px-5 py-3.5"><div className="flex justify-end gap-1"><button onClick={() => open(supplier)} title="Modifier" className="icon-button text-slate-400 hover:text-emerald-700"><Pencil size={14} /></button><button onClick={() => void remove(supplier)} title="Supprimer" className="icon-button text-slate-400 hover:bg-rose-50 hover:text-rose-600"><Trash2 size={14} /></button></div></td></tr>) : <tr><td colSpan={6} className="h-36 text-center text-xs text-slate-400">Aucun fournisseur trouvé.</td></tr>}</tbody></table></div>
    </section>
    {accountOf && <CompteFournisseurModal supplier={accountOf} canCreateCredit={isManager} onClose={() => setAccountOf(null)} onChanged={() => void load()} />}
    {modal && <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setModal(null); }}><section role="dialog" aria-modal="true" aria-label="Fournisseur" className="my-auto w-full max-w-xl rounded-2xl bg-white p-5 shadow-2xl"><div className="mb-5 flex items-center justify-between"><div className="flex items-center gap-2"><Truck size={17} className="text-emerald-700" /><h2 className="text-lg font-bold text-slate-900">{modal === "new" ? "Nouveau fournisseur" : "Modifier le fournisseur"}</h2></div><button onClick={() => setModal(null)} className="icon-button"><X size={16} /></button></div><form onSubmit={(event) => void save(event)} className="space-y-4"><label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Nom / raison sociale</span><input autoFocus required maxLength={120} className="input-field" value={form.nom} onChange={(event) => setForm({ ...form, nom: event.target.value })} /></label><div className="grid gap-3 sm:grid-cols-2"><label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Téléphone</span><input maxLength={30} className="input-field" value={form.telephone} onChange={(event) => setForm({ ...form, telephone: event.target.value })} /></label><label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">E-mail</span><input type="email" maxLength={180} className="input-field" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label></div><label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Adresse</span><textarea rows={2} maxLength={255} className="input-field resize-y" value={form.adresse} onChange={(event) => setForm({ ...form, adresse: event.target.value })} /></label><div className="flex justify-end gap-2 border-t border-slate-100 pt-4"><button type="button" disabled={saving} onClick={() => setModal(null)} className="button-secondary">Annuler</button><button disabled={saving} className="button-primary">{saving ? <LoaderCircle size={14} className="animate-spin" /> : <Plus size={14} />}{saving ? "Enregistrement…" : "Enregistrer"}</button></div></form></section></div>}
  </div>;
}
