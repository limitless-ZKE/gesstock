import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import axios from "axios";
import {
  AlertTriangle,
  ArrowDownToLine,
  Building2,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronRight,
  CirclePlus,
  FileText,
  LoaderCircle,
  PackagePlus,
  Plus,
  Search,
  Trash2,
  Truck,
  X,
} from "lucide-react";
import { api } from "../services/api";

type Article = {
  id: number;
  reference: string;
  designation: string;
  prixAchat: number;
  prixVente: number;
  quantiteStock: number;
  uniteMesure: string | null;
};
type Supplier = { id: number; nom: string; telephone: string | null; email: string | null; adresse: string | null };
type PurchaseLine = {
  id: number | null;
  articleId: number;
  referenceArticle: string;
  designationArticle: string;
  quantite: number;
  prixUnitaire: number;
  montantLigne: number;
};
type Supply = {
  id: number;
  numero: string;
  dateApprovisionnement: string;
  statut: "BROUILLON" | "RECU" | "ANNULE";
  fournisseurId: number;
  fournisseur: string;
  montantTotal: number;
  lignes: PurchaseLine[];
};
type DraftLine = { articleId: string; quantite: string; prixUnitaire: string };

const money = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "XOF", maximumFractionDigits: 0 });
const today = new Date().toISOString().slice(0, 10);
const blankLine = (): DraftLine => ({ articleId: "", quantite: "1", prixUnitaire: "" });

function errorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError<{ message?: string }>(error)) return error.response?.data?.message ?? fallback;
  return fallback;
}

function dateLabel(date: string) {
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium" }).format(new Date(`${date}T00:00:00`));
}

export default function ApprovisionnementsPage() {
  const [supplies, setSupplies] = useState<Supply[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [articles, setArticles] = useState<Article[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [actionError, setActionError] = useState("");
  const [search, setSearch] = useState("");
  const [supplierFilter, setSupplierFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("all");
  const [createOpen, setCreateOpen] = useState(false);
  const [supplierId, setSupplierId] = useState("");
  const [supplierModalOpen, setSupplierModalOpen] = useState(false);
  const [supplierForm, setSupplierForm] = useState({ nom: "", telephone: "", email: "", adresse: "" });
  const [savingSupplier, setSavingSupplier] = useState(false);
  const [reference, setReference] = useState(makeOrderReference());
  const [orderDate, setOrderDate] = useState(today);
  const [draftLines, setDraftLines] = useState<DraftLine[]>([blankLine()]);
  const [saving, setSaving] = useState(false);
  const [busySupplyId, setBusySupplyId] = useState<number | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [notice, setNotice] = useState("");
  const [noticeError, setNoticeError] = useState(false);

  async function loadData() {
    setLoading(true);
    setLoadError("");
    try {
      const [supplyResponse, supplierResponse, articleResponse] = await Promise.all([
        api.get<Supply[]>("/approvisionnements"),
        api.get<Supplier[]>("/fournisseurs"),
        api.get<Article[]>("/articles"),
      ]);
      setSupplies(supplyResponse.data);
      setSuppliers(supplierResponse.data);
      setArticles(articleResponse.data);
    } catch (error) {
      setLoadError(errorMessage(error, "Impossible de charger les approvisionnements et les référentiels."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadData(); }, []);

  const visible = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("fr");
    return supplies.filter((supply) =>
      (supplierFilter === "all" || String(supply.fournisseurId) === supplierFilter)
      && (statusFilter === "all" || supply.statut === statusFilter)
      && (!query || supply.numero.toLocaleLowerCase("fr").includes(query)
        || supply.fournisseur.toLocaleLowerCase("fr").includes(query)
        || supply.lignes.some((line) => line.designationArticle.toLocaleLowerCase("fr").includes(query)
          || line.referenceArticle.toLocaleLowerCase("fr").includes(query)))
    );
  }, [search, supplierFilter, statusFilter, supplies]);

  const orderTotal = useMemo(() => draftLines.reduce((sum, line) => {
    const quantity = Number(line.quantite) || 0;
    const price = Number(line.prixUnitaire) || 0;
    return sum + quantity * price;
  }, 0), [draftLines]);

  function addDraftLine() {
    setDraftLines((current) => [...current, blankLine()]);
  }

  function updateDraftLine(index: number, patch: Partial<DraftLine>) {
    setDraftLines((current) => current.map((line, lineIndex) => {
      if (lineIndex !== index) return line;
      const updated = { ...line, ...patch };
      if (patch.articleId) {
        const article = articles.find((item) => String(item.id) === patch.articleId);
        if (article) updated.prixUnitaire = String(article.prixAchat);
      }
      return updated;
    }));
  }

  async function createSupply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validLines = draftLines.filter((line) => line.articleId && Number(line.quantite) > 0 && Number(line.prixUnitaire) >= 0);
    if (!supplierId || !validLines.length) {
      setActionError("Sélectionnez un fournisseur et au moins une ligne complète.");
      return;
    }
    setSaving(true);
    setActionError("");
    try {
      await api.post("/approvisionnements", {
        numero: reference.trim(),
        dateApprovisionnement: orderDate,
        fournisseurId: Number(supplierId),
        lignes: validLines.map((line) => ({
          articleId: Number(line.articleId),
          quantite: Number(line.quantite),
          prixUnitaire: Number(line.prixUnitaire),
        })),
      });
      setCreateOpen(false);
      setDraftLines([blankLine()]);
      setSupplierId("");
      setReference(makeOrderReference());
      setOrderDate(today);
      setNotice("Bon d’approvisionnement enregistré en brouillon. Le stock sera mis à jour uniquement après réception.");
      setNoticeError(false);
      await loadData();
    } catch (error) {
      setActionError(errorMessage(error, "La création du bon a échoué."));
    } finally {
      setSaving(false);
    }
  }

  async function createSupplier(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingSupplier(true);
    setActionError("");
    try {
      const { data } = await api.post<Supplier>("/fournisseurs", supplierForm);
      setSuppliers((current) => [...current, data]);
      setSupplierId(String(data.id));
      setSupplierModalOpen(false);
      setSupplierForm({ nom: "", telephone: "", email: "", adresse: "" });
    } catch (error) {
      setActionError(errorMessage(error, "La création rapide du fournisseur a échoué."));
    } finally {
      setSavingSupplier(false);
    }
  }

  async function receiveSupply(supply: Supply) {
    if (!window.confirm(`Confirmer la réception du bon ${supply.numero} ? Cette opération mettra les quantités en stock à jour.`)) return;
    setBusySupplyId(supply.id);
    try {
      await api.post(`/approvisionnements/${supply.id}/validation`);
      setNotice(`Le bon ${supply.numero} a été réceptionné. Les stocks ont été mis à jour.`);
      setNoticeError(false);
      await loadData();
    } catch (error) {
      setNotice(errorMessage(error, `La réception du bon ${supply.numero} a échoué.`));
      setNoticeError(true);
    } finally {
      setBusySupplyId(null);
    }
  }

  async function deleteDraft(supply: Supply) {
    if (!window.confirm(`Supprimer le brouillon ${supply.numero} ?`)) return;
    setBusySupplyId(supply.id);
    try {
      await api.delete(`/approvisionnements/${supply.id}`);
      setNotice(`Le brouillon ${supply.numero} a été supprimé.`);
      setNoticeError(false);
      await loadData();
    } catch (error) {
      setNotice(errorMessage(error, "Suppression impossible."));
      setNoticeError(true);
    } finally {
      setBusySupplyId(null);
    }
  }

  function openCreate() {
    setActionError("");
    setReference(makeOrderReference());
    setOrderDate(today);
    setSupplierId("");
    setDraftLines([blankLine()]);
    setCreateOpen(true);
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Entrées de stock</p>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">Approvisionnements</h1>
          <p className="mt-1.5 text-sm text-slate-500">Suivez les bons fournisseurs et réceptionnez les marchandises dans le stock.</p>
        </div>
        <button onClick={openCreate} className="button-primary"><CirclePlus size={17} /> Nouveau bon</button>
      </header>

      {notice && <div role={noticeError ? "alert" : "status"} className={`flex items-start gap-2 rounded-xl border px-4 py-3 text-xs font-semibold ${noticeError ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}><Check size={15} className="mt-0.5 shrink-0" /><span>{notice}</span><button className="ml-auto" onClick={() => setNotice("")} aria-label="Fermer"><X size={15} /></button></div>}

      <section className="grid gap-3 sm:grid-cols-3">
        <Metric title="Tous les bons" count={supplies.length} icon={<FileText size={18} />} />
        <Metric title="À réceptionner" count={supplies.filter((item) => item.statut === "BROUILLON").length} icon={<PackagePlus size={18} />} />
        <Metric title="Réceptionnés" count={supplies.filter((item) => item.statut === "RECU").length} icon={<ArrowDownToLine size={18} />} />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 md:flex-row md:items-center md:p-5">
          <label className="relative min-w-0 flex-1"><Search size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" /><input value={search} onChange={(event) => setSearch(event.target.value)} className="input-field pl-10" placeholder="N° de bon, fournisseur ou article…" /></label>
          <select value={supplierFilter} onChange={(event) => setSupplierFilter(event.target.value)} className="input-field md:w-56"><option value="all">Tous les fournisseurs</option>{suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.nom}</option>)}</select>
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} className="input-field md:w-48"><option value="all">Tous les statuts</option><option value="BROUILLON">À réceptionner</option><option value="RECU">Réceptionnés</option><option value="ANNULE">Annulés</option></select>
        </div>
        {loadError && <div className="m-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800">{loadError}<button onClick={() => void loadData()} className="ml-2 underline">Réessayer</button></div>}
        <div className="overflow-x-auto">
          <table className="w-full min-w-[850px] text-left">
            <thead className="bg-slate-50/80 text-[10px] font-bold uppercase tracking-wider text-slate-400"><tr><th className="px-5 py-3">Bon d’approvisionnement</th><th className="px-4 py-3">Fournisseur</th><th className="px-4 py-3">Date</th><th className="px-4 py-3">Articles</th><th className="px-4 py-3">Montant total</th><th className="px-4 py-3">Statut</th><th className="px-5 py-3 text-right">Actions</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? <tr><td colSpan={7} className="h-44 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={17} />Chargement des bons…</td></tr>
                : visible.length === 0 ? <tr><td colSpan={7} className="h-44 text-center text-sm text-slate-400">Aucun bon ne correspond à ces filtres.</td></tr>
                  : visible.map((supply) => (
                    <SupplyRows
                      key={supply.id}
                      supply={supply}
                      expanded={expandedId === supply.id}
                      busy={busySupplyId === supply.id}
                      onToggle={() => setExpandedId((current) => current === supply.id ? null : supply.id)}
                      onReceive={() => void receiveSupply(supply)}
                      onDelete={() => void deleteDraft(supply)}
                    />
                  ))}
            </tbody>
          </table>
        </div>
        <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3"><p className="text-[11px] text-slate-500">{visible.length} bon(s)</p><p className="text-[10px] text-slate-400">Le stock change uniquement à la validation de réception.</p></div>
      </section>

      {createOpen && (
        <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setCreateOpen(false); }}>
          <section role="dialog" aria-modal="true" aria-label="Nouveau bon d’approvisionnement" className="my-auto w-full max-w-4xl rounded-2xl bg-white p-5 shadow-2xl md:p-6">
            <div className="mb-5 flex items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-wider text-emerald-600">Entrée de stock</p><h2 className="mt-1 text-xl font-bold text-slate-900">Nouveau bon d’approvisionnement</h2><p className="mt-1 text-xs text-slate-500">Le stock ne sera modifié qu’après validation de la réception.</p></div><button disabled={saving} onClick={() => setCreateOpen(false)} className="icon-button" aria-label="Fermer"><X size={17} /></button></div>
            {actionError && <div role="alert" className="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs font-semibold text-rose-800">{actionError}</div>}
            {suppliers.length === 0 && <div className="mb-4 flex gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800"><AlertTriangle size={15} className="shrink-0" />Aucun fournisseur n’est enregistré. Ajoutez-en un avant de créer le bon.</div>}
            <form onSubmit={createSupply} className="space-y-5">
              <div className="grid gap-3 sm:grid-cols-3">
                <label className="block"><span className="mb-1.5 block text-[11px] font-bold text-slate-600">Numéro du bon</span><input required maxLength={60} className="input-field" value={reference} onChange={(event) => setReference(event.target.value)} /></label>
                <label className="block"><span className="mb-1.5 flex items-center justify-between text-[11px] font-bold text-slate-600"><span>Fournisseur</span><button type="button" onClick={() => { setActionError(""); setSupplierModalOpen(true); }} className="inline-flex items-center gap-1 text-[10px] font-bold text-emerald-700 hover:text-emerald-900"><Plus size={12} />Créer rapidement</button></span><span className="relative block"><select required className="input-field appearance-none pr-9" value={supplierId} onChange={(event) => setSupplierId(event.target.value)}><option value="">Sélectionner un fournisseur</option>{suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.nom}</option>)}</select><ChevronDown className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" size={15} /></span></label>
                <label className="block"><span className="mb-1.5 block text-[11px] font-bold text-slate-600">Date</span><span className="relative block"><CalendarDays className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={15} /><input required type="date" className="input-field pl-9" value={orderDate} onChange={(event) => setOrderDate(event.target.value)} /></span></label>
              </div>

              <div className="overflow-hidden rounded-xl border border-slate-200">
                <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-4 py-3"><div><p className="text-xs font-bold text-slate-700">Articles à recevoir</p><p className="mt-0.5 text-[10px] text-slate-400">Les quantités sont exprimées dans l’unité principale de l’article.</p></div><button type="button" onClick={addDraftLine} className="button-secondary h-9"><Plus size={14} /> Ajouter une ligne</button></div>
                <div className="divide-y divide-slate-100">
                  {draftLines.map((line, index) => {
                    const article = articles.find((item) => String(item.id) === line.articleId);
                    const rowTotal = (Number(line.quantite) || 0) * (Number(line.prixUnitaire) || 0);
                    return <div key={index} className="grid gap-3 p-3 sm:grid-cols-[minmax(0,1fr)_105px_150px_120px_38px] sm:items-end">
                      <label className="block min-w-0"><span className="mb-1 block text-[10px] font-bold text-slate-500">Article</span><select required className="input-field h-10" value={line.articleId} onChange={(event) => updateDraftLine(index, { articleId: event.target.value })}><option value="">Sélectionner un article</option>{articles.map((item) => <option key={item.id} value={item.id}>{item.reference} · {item.designation} (stock {item.quantiteStock})</option>)}</select>{article && <span className="mt-1 block text-[10px] text-slate-400">Stock actuel : {article.quantiteStock} {article.uniteMesure ?? ""}</span>}</label>
                      <label className="block"><span className="mb-1 block text-[10px] font-bold text-slate-500">Quantité</span><input required min="1" step="1" type="number" className="input-field h-10" value={line.quantite} onChange={(event) => updateDraftLine(index, { quantite: event.target.value })} /></label>
                      <label className="block"><span className="mb-1 block text-[10px] font-bold text-slate-500">Prix unitaire</span><input required min="0" step="any" type="number" className="input-field h-10" value={line.prixUnitaire} onChange={(event) => updateDraftLine(index, { prixUnitaire: event.target.value })} /></label>
                      <div><span className="mb-1 block text-[10px] font-bold text-slate-500">Montant</span><p className="flex h-10 items-center text-xs font-bold text-slate-800">{money.format(rowTotal)}</p></div>
                      <button type="button" title="Retirer la ligne" disabled={draftLines.length === 1} onClick={() => setDraftLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} className="icon-button mb-0.5 disabled:opacity-30"><Trash2 size={15} /></button>
                    </div>;
                  })}
                </div>
                <div className="flex justify-between border-t border-slate-100 bg-slate-50 px-4 py-3"><span className="text-xs font-bold text-slate-600">Total estimé</span><span className="text-sm font-extrabold text-slate-900">{money.format(orderTotal)}</span></div>
              </div>
              <div className="flex justify-end gap-2 border-t border-slate-100 pt-4"><button type="button" disabled={saving} onClick={() => setCreateOpen(false)} className="button-secondary">Annuler</button><button disabled={saving || suppliers.length === 0 || articles.length === 0} className="button-primary min-w-52">{saving ? <LoaderCircle size={15} className="animate-spin" /> : <PackagePlus size={16} />}{saving ? "Enregistrement…" : "Créer le brouillon"}</button></div>
            </form>
          </section>
        </div>
      )}
      {supplierModalOpen && <div className="fixed inset-0 z-[60] grid place-items-center bg-slate-950/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !savingSupplier) setSupplierModalOpen(false); }}><section role="dialog" aria-modal="true" aria-label="Créer rapidement un fournisseur" className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl"><div className="mb-5 flex items-center justify-between"><div className="flex items-center gap-2"><Building2 size={17} className="text-emerald-700" /><h2 className="text-lg font-bold text-slate-900">Nouveau fournisseur</h2></div><button disabled={savingSupplier} onClick={() => setSupplierModalOpen(false)} className="icon-button"><X size={16} /></button></div><form onSubmit={(event) => void createSupplier(event)} className="space-y-3"><label className="block"><span className="mb-1 block text-[10px] font-bold text-slate-600">Nom / raison sociale</span><input required maxLength={120} autoFocus className="input-field" value={supplierForm.nom} onChange={(event) => setSupplierForm({ ...supplierForm, nom: event.target.value })} /></label><div className="grid gap-3 sm:grid-cols-2"><label className="block"><span className="mb-1 block text-[10px] font-bold text-slate-600">Téléphone</span><input maxLength={30} className="input-field" value={supplierForm.telephone} onChange={(event) => setSupplierForm({ ...supplierForm, telephone: event.target.value })} /></label><label className="block"><span className="mb-1 block text-[10px] font-bold text-slate-600">E-mail</span><input type="email" maxLength={180} className="input-field" value={supplierForm.email} onChange={(event) => setSupplierForm({ ...supplierForm, email: event.target.value })} /></label></div><label className="block"><span className="mb-1 block text-[10px] font-bold text-slate-600">Adresse</span><input maxLength={255} className="input-field" value={supplierForm.adresse} onChange={(event) => setSupplierForm({ ...supplierForm, adresse: event.target.value })} /></label>{actionError && <p role="alert" className="text-xs font-semibold text-rose-700">{actionError}</p>}<div className="flex justify-end gap-2 border-t border-slate-100 pt-4"><button type="button" disabled={savingSupplier} onClick={() => setSupplierModalOpen(false)} className="button-secondary">Annuler</button><button disabled={savingSupplier} className="button-primary">{savingSupplier ? <LoaderCircle size={14} className="animate-spin" /> : <Plus size={14} />}{savingSupplier ? "Création…" : "Créer et sélectionner"}</button></div></form></section></div>}
    </div>
  );
}

function makeOrderReference() {
  const now = new Date();
  const compactDate = now.toISOString().replace(/[-:T.Z]/g, "").slice(0, 14);
  return `APP-${compactDate}`;
}

function Metric({ title, count, icon }: { title: string; count: number; icon: ReactNode }) {
  return <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><div><p className="text-xs font-semibold text-slate-500">{title}</p><p className="mt-1 text-2xl font-extrabold text-slate-900">{count}</p></div><span className="grid size-10 place-items-center rounded-xl bg-emerald-50 text-emerald-700">{icon}</span></div>;
}

function SupplyRows({ supply, expanded, busy, onToggle, onReceive, onDelete }: { supply: Supply; expanded: boolean; busy: boolean; onToggle: () => void; onReceive: () => void; onDelete: () => void }) {
  const status = supply.statut === "RECU"
    ? { text: "Réceptionné", style: "bg-emerald-50 text-emerald-700", dot: "bg-emerald-500" }
    : supply.statut === "ANNULE"
      ? { text: "Annulé", style: "bg-slate-100 text-slate-500", dot: "bg-slate-400" }
      : { text: "À réceptionner", style: "bg-orange-50 text-orange-700", dot: "bg-orange-500" };
  return <>
    <tr className="hover:bg-slate-50/70">
      <td className="px-5 py-3.5"><button onClick={onToggle} className="flex items-center gap-2 text-left"><span className="grid size-8 place-items-center rounded-lg bg-slate-50 text-slate-500"><FileText size={15} /></span><span><span className="block text-xs font-bold text-slate-800">{supply.numero}</span><span className="mt-1 block text-[10px] text-slate-400">{supply.lignes.length} ligne(s)</span></span><ChevronRight className={`ml-1 text-slate-400 transition ${expanded ? "rotate-90" : ""}`} size={15} /></button></td>
      <td className="px-4 py-3.5"><div className="flex items-center gap-2 text-xs font-semibold text-slate-700"><Truck size={14} className="text-slate-400" />{supply.fournisseur}</div></td>
      <td className="px-4 py-3.5 text-xs text-slate-600">{dateLabel(supply.dateApprovisionnement)}</td>
      <td className="px-4 py-3.5 text-xs text-slate-600">{supply.lignes.reduce((sum, line) => sum + line.quantite, 0)} unité(s)</td>
      <td className="px-4 py-3.5 text-xs font-bold text-slate-800">{money.format(supply.montantTotal)}</td>
      <td className="px-4 py-3.5"><span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-bold ${status.style}`}><span className={`size-1.5 rounded-full ${status.dot}`} />{status.text}</span></td>
      <td className="px-5 py-3.5"><div className="flex justify-end gap-2">{supply.statut === "BROUILLON" && <><button disabled={busy} onClick={onDelete} title="Supprimer le brouillon" className="icon-button text-slate-400 hover:bg-rose-50 hover:text-rose-600"><Trash2 size={15} /></button><button disabled={busy} onClick={onReceive} className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-emerald-600 px-3 text-[10px] font-bold text-white hover:bg-emerald-700 disabled:opacity-50">{busy ? <LoaderCircle size={13} className="animate-spin" /> : <ArrowDownToLine size={13} />}{busy ? "Réception…" : "Valider réception"}</button></>}</div></td>
    </tr>
    {expanded && <tr className="bg-slate-50/60"><td colSpan={7} className="px-5 py-4"><div className="overflow-hidden rounded-xl border border-slate-200 bg-white"><table className="w-full text-left"><thead className="bg-slate-50 text-[9px] font-bold uppercase tracking-wider text-slate-400"><tr><th className="px-3 py-2">Article</th><th className="px-3 py-2">Quantité</th><th className="px-3 py-2">Prix unitaire</th><th className="px-3 py-2 text-right">Total ligne</th></tr></thead><tbody className="divide-y divide-slate-100">{supply.lignes.map((line) => <tr key={line.id ?? `${supply.id}-${line.articleId}`}><td className="px-3 py-2.5"><p className="text-xs font-semibold text-slate-700">{line.designationArticle}</p><p className="mt-0.5 text-[10px] text-slate-400">{line.referenceArticle}</p></td><td className="px-3 py-2.5 text-xs text-slate-600">{line.quantite}</td><td className="px-3 py-2.5 text-xs text-slate-600">{money.format(line.prixUnitaire)}</td><td className="px-3 py-2.5 text-right text-xs font-bold text-slate-800">{money.format(line.montantLigne)}</td></tr>)}</tbody></table></div></td></tr>}
  </>;
}
