import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  Boxes,
  Check,
  ChevronLeft,
  ChevronRight,
  CirclePlus,
  Layers3,
  LoaderCircle,
  Pencil,
  Plus,
  Search,
  Settings2,
  SlidersHorizontal,
  Trash2,
  X,
} from "lucide-react";
import { api } from "../services/api";
import { apiErrorMessage as errorMessage } from "../services/apiError";
import { money } from "../lib/format";

type Category = { id: number; nom: string; description: string | null; active: boolean; parentId: number | null };
type Unit = { id: number; code: string; libelle: string };
type Article = {
  id: number;
  reference: string;
  designation: string;
  prixAchat: number;
  coutMoyen: number;
  prixVente: number;
  quantiteStock: number;
  seuilAlerteStock: number;
  categorieId: number | null;
  categorie: string | null;
  uniteMesureId: number | null;
  uniteMesure: string | null;
};
type SaleUnit = {
  id: number;
  articleId: number;
  code: string | null;
  uniteMesureId: number;
  uniteMesureCode: string;
  uniteMesureLibelle: string;
  facteurConversion: number;
  prixVente: number | null;
  active: boolean;
};
type ArticleForm = {
  reference: string;
  designation: string;
  prixAchat: string;
  prixVente: string;
  seuilAlerteStock: string;
  quantiteStock: string;
  categorieId: string;
  uniteMesureId: string;
};

const PAGE_SIZE = 10;
const blankArticle: ArticleForm = {
  reference: "", designation: "", prixAchat: "", prixVente: "", seuilAlerteStock: "0",
  quantiteStock: "0", categorieId: "", uniteMesureId: "",
};

const adjustmentReasons = [
  { value: "CASSE", label: "Casse" },
  { value: "VOL", label: "Vol" },
  { value: "PEREMPTION", label: "Péremption" },
  { value: "ERREUR_DE_SAISIE", label: "Erreur de saisie" },
  { value: "ECART_DE_COMPTAGE", label: "Écart de comptage" },
  { value: "AUTRE", label: "Autre (préciser)" },
];
const blankAdjustment = { nouveauStock: "", motif: "", commentaire: "" };

export default function ArticlesPage() {
  const [articles, setArticles] = useState<Article[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("all");
  const [stockFilter, setStockFilter] = useState<"all" | "low" | "out">("all");
  const [sortKey, setSortKey] = useState<keyof Article>("designation");
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");
  const [page, setPage] = useState(1);
  const [articleModal, setArticleModal] = useState<Article | "new" | null>(null);
  const [articleForm, setArticleForm] = useState<ArticleForm>(blankArticle);
  const [savingArticle, setSavingArticle] = useState(false);
  const [adjustArticle, setAdjustArticle] = useState<Article | null>(null);
  const [adjustForm, setAdjustForm] = useState(blankAdjustment);
  const [savingAdjustment, setSavingAdjustment] = useState(false);
  const [categoryModal, setCategoryModal] = useState(false);
  const [categoryForm, setCategoryForm] = useState({ nom: "", description: "", parentId: "" });
  const [savingCategory, setSavingCategory] = useState(false);
  const [unitForm, setUnitForm] = useState({ code: "", libelle: "" });
  const [savingUnit, setSavingUnit] = useState(false);
  const [editingUnitId, setEditingUnitId] = useState<number | null>(null);
  const [unitsArticle, setUnitsArticle] = useState<Article | null>(null);
  const [saleUnits, setSaleUnits] = useState<SaleUnit[]>([]);
  const [saleUnitForm, setSaleUnitForm] = useState({ code: "", uniteMesureId: "", facteurConversion: "1", prixVente: "" });
  const [editingSaleUnitId, setEditingSaleUnitId] = useState<number | null>(null);
  const [savingSaleUnit, setSavingSaleUnit] = useState(false);
  const [notice, setNotice] = useState("");
  const [noticeError, setNoticeError] = useState(false);

  async function loadData() {
    setLoading(true);
    setError("");
    try {
      const [articleResponse, categoryResponse, unitResponse] = await Promise.all([
        api.get<Article[]>("/articles"),
        api.get<Category[]>("/articles/categories"),
        api.get<Unit[]>("/referentiels/unites-mesure"),
      ]);
      setArticles(articleResponse.data);
      setCategories(categoryResponse.data);
      setUnits(unitResponse.data);
    } catch (loadError) {
      setError(errorMessage(loadError, "Impossible de charger le catalogue. Vérifiez vos droits et la connexion au serveur."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadData(); }, []);

  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("fr");
    return articles
      .filter((article) => categoryFilter === "all" || String(article.categorieId ?? "") === categoryFilter)
      .filter((article) => stockFilter === "all"
        || (stockFilter === "low" && article.quantiteStock > 0 && article.quantiteStock <= article.seuilAlerteStock)
        || (stockFilter === "out" && article.quantiteStock <= 0))
      .filter((article) => !query
        || article.designation.toLocaleLowerCase("fr").includes(query)
        || article.reference.toLocaleLowerCase("fr").includes(query))
      .sort((left, right) => {
        const a = left[sortKey];
        const b = right[sortKey];
        const comparison = typeof a === "number" && typeof b === "number"
          ? a - b
          : String(a ?? "").localeCompare(String(b ?? ""), "fr", { numeric: true, sensitivity: "base" });
        return sortDirection === "asc" ? comparison : -comparison;
      });
  }, [articles, categoryFilter, search, sortDirection, sortKey, stockFilter]);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const visibleArticles = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  useEffect(() => { setPage(1); }, [search, categoryFilter, stockFilter]);
  useEffect(() => { if (page > pageCount) setPage(pageCount); }, [page, pageCount]);

  function notify(message: string, isError = false) {
    setNotice(message);
    setNoticeError(isError);
  }

  function startArticleEdit(article: Article | "new") {
    setArticleForm(article === "new" ? blankArticle : {
      reference: article.reference,
      designation: article.designation,
      prixAchat: String(article.prixAchat),
      prixVente: String(article.prixVente),
      seuilAlerteStock: String(article.seuilAlerteStock),
      quantiteStock: String(article.quantiteStock),
      categorieId: article.categorieId == null ? "" : String(article.categorieId),
      uniteMesureId: article.uniteMesureId == null ? "" : String(article.uniteMesureId),
    });
    setArticleModal(article);
  }

  async function saveArticle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingArticle(true);
    const payload = {
      reference: articleForm.reference.trim(),
      designation: articleForm.designation.trim(),
      prixAchat: Number(articleForm.prixAchat),
      prixVente: Number(articleForm.prixVente),
      seuilAlerteStock: Number(articleForm.seuilAlerteStock),
      // Le stock initial ne se fixe qu'à la création ; ensuite seul « Ajuster le stock » le modifie (avec trace).
      quantiteStock: articleModal === "new" ? Number(articleForm.quantiteStock) : undefined,
      categorieId: articleForm.categorieId ? Number(articleForm.categorieId) : null,
      uniteMesureId: articleForm.uniteMesureId ? Number(articleForm.uniteMesureId) : null,
    };
    try {
      if (articleModal === "new") await api.post("/articles", payload);
      else if (articleModal) await api.put(`/articles/${articleModal.id}`, payload);
      setArticleModal(null);
      notify(articleModal === "new" ? "Article créé." : "Article modifié.");
      await loadData();
    } catch (saveError) {
      notify(errorMessage(saveError, "Enregistrement de l’article impossible."), true);
    } finally {
      setSavingArticle(false);
    }
  }

  function startAdjustment(article: Article) {
    setAdjustForm({ ...blankAdjustment, nouveauStock: String(article.quantiteStock) });
    setAdjustArticle(article);
  }

  async function saveAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!adjustArticle) return;
    setSavingAdjustment(true);
    try {
      await api.post(`/articles/${adjustArticle.id}/ajustement-stock`, {
        stockAffiche: adjustArticle.quantiteStock,
        nouveauStock: Number(adjustForm.nouveauStock),
        motif: adjustForm.motif,
        commentaire: adjustForm.commentaire.trim() || null,
      });
      setAdjustArticle(null);
      notify(`Stock de « ${adjustArticle.designation} » ajusté : ${adjustArticle.quantiteStock} → ${Number(adjustForm.nouveauStock)}.`);
      await loadData();
    } catch (adjustError) {
      notify(errorMessage(adjustError, "Ajustement du stock impossible."), true);
      await loadData();
    } finally {
      setSavingAdjustment(false);
    }
  }

  async function deleteArticle(article: Article) {
    if (!window.confirm(`Supprimer l’article « ${article.designation} » ?`)) return;
    try {
      await api.delete(`/articles/${article.id}`);
      notify("Article supprimé.");
      await loadData();
    } catch (deleteError) {
      notify(errorMessage(deleteError, "Suppression impossible : cet article est peut-être déjà utilisé."), true);
    }
  }

  async function createCategory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingCategory(true);
    try {
      await api.post("/articles/categories", {
        nom: categoryForm.nom.trim(),
        description: categoryForm.description.trim() || null,
        parentId: categoryForm.parentId ? Number(categoryForm.parentId) : null,
      });
      setCategoryForm({ nom: "", description: "", parentId: "" });
      notify("Catégorie créée.");
      await loadData();
    } catch (categoryError) {
      notify(errorMessage(categoryError, "Création de catégorie impossible."), true);
    } finally {
      setSavingCategory(false);
    }
  }

  async function deleteCategory(category: Category) {
    if (!window.confirm(`Supprimer la catégorie « ${category.nom} » ?`)) return;
    try {
      await api.delete(`/articles/categories/${category.id}`);
      notify("Catégorie supprimée.");
      await loadData();
    } catch (deleteError) {
      notify(errorMessage(deleteError, "Cette catégorie est probablement encore utilisée."), true);
    }
  }

  async function saveUnit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingUnit(true);
    try {
      if (editingUnitId != null) await api.put(`/referentiels/unites-mesure/${editingUnitId}`, unitForm);
      else await api.post("/referentiels/unites-mesure", unitForm);
      notify(editingUnitId != null ? "Unité de mesure modifiée." : "Unité de mesure créée.");
      cancelUnitEdit();
      await loadData();
    } catch (unitError) {
      notify(errorMessage(unitError, editingUnitId != null ? "Modification d’unité impossible." : "Création d’unité impossible."), true);
    } finally {
      setSavingUnit(false);
    }
  }

  function startUnitEdit(unit: Unit) {
    setEditingUnitId(unit.id);
    setUnitForm({ code: unit.code, libelle: unit.libelle });
  }

  function cancelUnitEdit() {
    setEditingUnitId(null);
    setUnitForm({ code: "", libelle: "" });
  }

  async function deleteUnit(unit: Unit) {
    if (!window.confirm(`Supprimer l’unité « ${unit.libelle} » ?`)) return;
    try {
      await api.delete(`/referentiels/unites-mesure/${unit.id}`);
      notify("Unité supprimée.");
      await loadData();
    } catch (deleteError) {
      notify(errorMessage(deleteError, "Cette unité est probablement encore utilisée."), true);
    }
  }

  async function openSaleUnits(article: Article) {
    setUnitsArticle(article);
    setSaleUnitForm({ code: "", uniteMesureId: article.uniteMesureId ? String(article.uniteMesureId) : "", facteurConversion: "1", prixVente: "" });
    setEditingSaleUnitId(null);
    try {
      const { data } = await api.get<SaleUnit[]>(`/articles/${article.id}/unites-vente`);
      setSaleUnits(data);
    } catch (unitsError) {
      setUnitsArticle(null);
      notify(errorMessage(unitsError, "Impossible de charger les unités de vente."), true);
    }
  }

  async function saveSaleUnit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!unitsArticle) return;
    setSavingSaleUnit(true);
    const payload = {
      code: saleUnitForm.code.trim() || null,
      uniteMesureId: Number(saleUnitForm.uniteMesureId),
      facteurConversion: Number(saleUnitForm.facteurConversion),
      prixVente: saleUnitForm.prixVente ? Number(saleUnitForm.prixVente) : null,
      active: true,
    };
    try {
      if (editingSaleUnitId != null) await api.put(`/articles/${unitsArticle.id}/unites-vente/${editingSaleUnitId}`, payload);
      else await api.post(`/articles/${unitsArticle.id}/unites-vente`, payload);
      const { data } = await api.get<SaleUnit[]>(`/articles/${unitsArticle.id}/unites-vente`);
      setSaleUnits(data);
      setSaleUnitForm({ code: "", uniteMesureId: unitsArticle.uniteMesureId ? String(unitsArticle.uniteMesureId) : "", facteurConversion: "1", prixVente: "" });
      setEditingSaleUnitId(null);
      notify("Unité de vente enregistrée.");
    } catch (saveError) {
      notify(errorMessage(saveError, "Enregistrement de l’unité de vente impossible."), true);
    } finally {
      setSavingSaleUnit(false);
    }
  }

  async function deleteSaleUnit(unit: SaleUnit) {
    if (!unitsArticle || !window.confirm(`Supprimer l’unité ${unit.code ?? unit.uniteMesureLibelle} ?`)) return;
    try {
      await api.delete(`/articles/${unitsArticle.id}/unites-vente/${unit.id}`);
      setSaleUnits((current) => current.filter((item) => item.id !== unit.id));
      notify("Unité de vente supprimée.");
    } catch (deleteError) {
      notify(errorMessage(deleteError, "Suppression impossible."), true);
    }
  }

  function editSaleUnit(unit: SaleUnit) {
    setEditingSaleUnitId(unit.id);
    setSaleUnitForm({
      code: unit.code ?? "",
      uniteMesureId: String(unit.uniteMesureId),
      facteurConversion: String(unit.facteurConversion),
      prixVente: unit.prixVente == null ? "" : String(unit.prixVente),
    });
  }

  function changeSort(key: keyof Article) {
    if (sortKey === key) setSortDirection((current) => current === "asc" ? "desc" : "asc");
    else { setSortKey(key); setSortDirection("asc"); }
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Gestion des stocks</p>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">Catalogue articles</h1>
          <p className="mt-1.5 text-sm text-slate-500">Gérez les références, les catégories, les unités et les niveaux de stock.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button onClick={() => setCategoryModal(true)} className="button-secondary"><Layers3 size={16} /> Catégories & unités</button>
          <button onClick={() => startArticleEdit("new")} className="button-primary"><CirclePlus size={17} /> Nouvel article</button>
        </div>
      </header>

      {notice && <Notice error={noticeError} onClose={() => setNotice("")}>{notice}</Notice>}

      <section className="grid gap-3 sm:grid-cols-3">
        <SummaryCard label="Références" value={articles.length} icon={<Boxes size={18} />} tone="emerald" />
        <SummaryCard label="Stock bas" value={articles.filter((item) => item.quantiteStock > 0 && item.quantiteStock <= item.seuilAlerteStock).length} icon={<AlertTriangle size={18} />} tone="orange" />
        <SummaryCard label="En rupture" value={articles.filter((item) => item.quantiteStock <= 0).length} icon={<AlertTriangle size={18} />} tone="red" />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 md:flex-row md:items-center md:p-5">
          <label className="relative min-w-0 flex-1">
            <Search size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input className="input-field pl-10" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Rechercher par nom ou référence…" />
          </label>
          <select className="input-field md:w-52" value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}>
            <option value="all">Toutes les catégories</option>
            {categories.map((category) => <option key={category.id} value={category.id}>{category.nom}</option>)}
          </select>
          <select className="input-field md:w-48" value={stockFilter} onChange={(event) => setStockFilter(event.target.value as typeof stockFilter)}>
            <option value="all">Tous les niveaux</option>
            <option value="low">Stock bas</option>
            <option value="out">Rupture</option>
          </select>
        </div>
        {error && <div className="m-4"><Notice error>{error}<button className="ml-2 underline" onClick={() => void loadData()}>Réessayer</button></Notice></div>}
        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] text-left">
            <thead className="bg-slate-50/80 text-[10px] font-bold uppercase tracking-wider text-slate-400">
              <tr>
                <SortHeader label="Article" active={sortKey === "designation"} direction={sortDirection} onClick={() => changeSort("designation")} />
                <SortHeader label="Catégorie" active={sortKey === "categorie"} direction={sortDirection} onClick={() => changeSort("categorie")} />
                <SortHeader label="Dernier achat" active={sortKey === "prixAchat"} direction={sortDirection} onClick={() => changeSort("prixAchat")} />
                <SortHeader label="Prix de vente" active={sortKey === "prixVente"} direction={sortDirection} onClick={() => changeSort("prixVente")} />
                <SortHeader label="Stock" active={sortKey === "quantiteStock"} direction={sortDirection} onClick={() => changeSort("quantiteStock")} />
                <th className="px-4 py-3">État</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? <tr><td colSpan={7} className="h-48 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={17} />Chargement du catalogue…</td></tr>
                : visibleArticles.length === 0 ? <tr><td colSpan={7} className="h-48 text-center text-sm text-slate-400">Aucun article ne correspond à ces filtres.</td></tr>
                  : visibleArticles.map((article) => {
                    const status = stockStatus(article);
                    return (
                      <tr key={article.id} className="transition hover:bg-slate-50/70">
                        <td className="px-4 py-3.5">
                          <div className="flex items-center gap-3">
                            <div className="grid size-9 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-700"><Boxes size={17} /></div>
                            <div><p className="text-xs font-bold text-slate-800">{article.designation}</p><p className="mt-1 text-[10px] font-medium tracking-wide text-slate-400">{article.reference}</p></div>
                          </div>
                        </td>
                        <td className="px-4 py-3.5 text-xs text-slate-600">{article.categorie ?? "—"}</td>
                        <td className="px-4 py-3.5 text-xs text-slate-600"><p>{money.format(article.prixAchat)}</p><p title="Coût moyen pondéré : valeur du stock divisée par sa quantité, base des marges" className="mt-1 text-[10px] text-slate-400">CMP {money.format(article.coutMoyen)}</p></td>
                        <td className="px-4 py-3.5 text-xs font-bold text-slate-800">{money.format(article.prixVente)}</td>
                        <td className="px-4 py-3.5"><p className="text-xs font-bold text-slate-800">{article.quantiteStock} <span className="font-normal text-slate-400">{article.uniteMesure ?? ""}</span></p><p className="mt-1 text-[10px] text-slate-400">Seuil : {article.seuilAlerteStock}</p></td>
                        <td className="px-4 py-3.5"><span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-bold ${status.className}`}><span className={`size-1.5 rounded-full ${status.dot}`} />{status.label}</span></td>
                        <td className="px-4 py-3.5">
                          <div className="flex justify-end gap-1">
                            <button title="Unités de vente" onClick={() => void openSaleUnits(article)} className="icon-button"><Settings2 size={15} /></button>
                            <button title="Ajuster le stock" onClick={() => startAdjustment(article)} className="icon-button"><SlidersHorizontal size={15} /></button>
                            <button title="Modifier" onClick={() => startArticleEdit(article)} className="icon-button"><Pencil size={15} /></button>
                            <button title="Supprimer" onClick={() => void deleteArticle(article)} className="icon-button text-slate-400 hover:bg-rose-50 hover:text-rose-600"><Trash2 size={15} /></button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col justify-between gap-3 border-t border-slate-100 px-4 py-3 sm:flex-row sm:items-center md:px-5">
          <p className="text-[11px] text-slate-500">{filtered.length ? `Affichage ${(page - 1) * PAGE_SIZE + 1}–${Math.min(page * PAGE_SIZE, filtered.length)} sur ${filtered.length} articles` : "0 article"}</p>
          <div className="flex items-center gap-2">
            <button className="icon-button" aria-label="Page précédente" disabled={page <= 1} onClick={() => setPage((current) => Math.max(1, current - 1))}><ChevronLeft size={16} /></button>
            <span className="text-xs font-semibold text-slate-600">Page {page} / {pageCount}</span>
            <button className="icon-button" aria-label="Page suivante" disabled={page >= pageCount} onClick={() => setPage((current) => Math.min(pageCount, current + 1))}><ChevronRight size={16} /></button>
          </div>
        </div>
      </section>

      {articleModal && (
        <Modal title={articleModal === "new" ? "Créer un article" : "Modifier l’article"} subtitle="Renseignez les informations du catalogue." onClose={() => setArticleModal(null)}>
          <form onSubmit={saveArticle} className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Désignation"><input required maxLength={150} className="input-field" value={articleForm.designation} onChange={(event) => setArticleForm({ ...articleForm, designation: event.target.value })} /></Field>
              <Field label="Référence"><input required maxLength={60} className="input-field" value={articleForm.reference} onChange={(event) => setArticleForm({ ...articleForm, reference: event.target.value })} /></Field>
              <Field label={articleModal === "new" ? "Prix d’achat (XOF)" : "Dernier prix d’achat (XOF)"}><input required min="0" step="any" type="number" className="input-field" value={articleForm.prixAchat} onChange={(event) => setArticleForm({ ...articleForm, prixAchat: event.target.value })} /></Field>
              <Field label="Prix de vente (XOF)"><input required min="0" step="any" type="number" className="input-field" value={articleForm.prixVente} onChange={(event) => setArticleForm({ ...articleForm, prixVente: event.target.value })} /></Field>
              <Field label="Seuil d’alerte"><input required min="0" step="1" type="number" className="input-field" value={articleForm.seuilAlerteStock} onChange={(event) => setArticleForm({ ...articleForm, seuilAlerteStock: event.target.value })} /></Field>
              <Field label={articleModal === "new" ? "Stock initial" : "Stock actuel (non modifiable ici)"}><input required min="0" step="1" type="number" readOnly={articleModal !== "new"} className="input-field read-only:bg-slate-50 read-only:text-slate-500" value={articleForm.quantiteStock} onChange={(event) => setArticleForm({ ...articleForm, quantiteStock: event.target.value })} /><span className="mt-1 block text-[10px] text-slate-400">{articleModal === "new" ? "Les variations de stock sont ensuite enregistrées par réception ou vente." : "Pour corriger le stock, utilisez « Ajuster le stock » dans la liste (motif obligatoire)."}</span></Field>
              <Field label="Catégorie"><select className="input-field" value={articleForm.categorieId} onChange={(event) => setArticleForm({ ...articleForm, categorieId: event.target.value })}><option value="">Sans catégorie</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.nom}</option>)}</select></Field>
              <Field label="Unité de mesure principale"><select className="input-field" value={articleForm.uniteMesureId} onChange={(event) => setArticleForm({ ...articleForm, uniteMesureId: event.target.value })}><option value="">Sans unité</option>{units.map((unit) => <option key={unit.id} value={unit.id}>{unit.libelle} ({unit.code})</option>)}</select></Field>
            </div>
            <ModalActions busy={savingArticle} onCancel={() => setArticleModal(null)} submitLabel="Enregistrer l’article" />
          </form>
        </Modal>
      )}

      {adjustArticle && (
        <Modal title="Ajuster le stock" subtitle={`${adjustArticle.designation} (${adjustArticle.reference}) : stock enregistré ${adjustArticle.quantiteStock}`} onClose={() => setAdjustArticle(null)}>
          <form onSubmit={saveAdjustment} className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Stock réel constaté"><input required autoFocus min="0" step="1" type="number" className="input-field" value={adjustForm.nouveauStock} onChange={(event) => setAdjustForm({ ...adjustForm, nouveauStock: event.target.value })} /></Field>
              <Field label="Motif"><select required className="input-field" value={adjustForm.motif} onChange={(event) => setAdjustForm({ ...adjustForm, motif: event.target.value })}><option value="">Choisir un motif</option>{adjustmentReasons.map((reason) => <option key={reason.value} value={reason.value}>{reason.label}</option>)}</select></Field>
            </div>
            <Field label={adjustForm.motif === "AUTRE" ? "Commentaire (obligatoire)" : "Commentaire (facultatif)"}><input required={adjustForm.motif === "AUTRE"} maxLength={300} className="input-field" value={adjustForm.commentaire} onChange={(event) => setAdjustForm({ ...adjustForm, commentaire: event.target.value })} placeholder="Ex. : carton tombé pendant le déchargement" /></Field>
            {Number(adjustForm.nouveauStock) !== adjustArticle.quantiteStock && adjustForm.nouveauStock !== "" && (
              <p className="rounded-xl bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-800">
                Le stock passera de {adjustArticle.quantiteStock} à {Number(adjustForm.nouveauStock)} ({Number(adjustForm.nouveauStock) - adjustArticle.quantiteStock > 0 ? "+" : ""}{Number(adjustForm.nouveauStock) - adjustArticle.quantiteStock}). Un mouvement de stock et une ligne de journal seront enregistrés à votre nom.
              </p>
            )}
            <ModalActions busy={savingAdjustment} onCancel={() => setAdjustArticle(null)} submitLabel="Enregistrer l’ajustement" />
          </form>
        </Modal>
      )}

      {categoryModal && (
        <Modal title="Catégories & unités de mesure" subtitle="Gérez les référentiels utilisés par les articles." onClose={() => setCategoryModal(false)} wide>
          <div className="grid gap-7 lg:grid-cols-2">
            <div>
              <h3 className="mb-3 text-sm font-bold text-slate-800">Catégories</h3>
              <form onSubmit={createCategory} className="space-y-3 rounded-xl bg-slate-50 p-4">
                <input required maxLength={100} className="input-field" placeholder="Nom de la catégorie" value={categoryForm.nom} onChange={(event) => setCategoryForm({ ...categoryForm, nom: event.target.value })} />
                <input maxLength={500} className="input-field" placeholder="Description (facultatif)" value={categoryForm.description} onChange={(event) => setCategoryForm({ ...categoryForm, description: event.target.value })} />
                <select className="input-field" value={categoryForm.parentId} onChange={(event) => setCategoryForm({ ...categoryForm, parentId: event.target.value })}><option value="">Catégorie principale</option>{categories.map((item) => <option key={item.id} value={item.id}>{item.nom}</option>)}</select>
                <button disabled={savingCategory} className="button-primary w-full"><Plus size={15} /> Ajouter une catégorie</button>
              </form>
              <div className="mt-3 max-h-52 divide-y divide-slate-100 overflow-y-auto rounded-xl border border-slate-100">
                {categories.map((category) => <div key={category.id} className="flex items-center justify-between px-3 py-2.5"><div><p className="text-xs font-semibold text-slate-700">{category.nom}</p>{category.description && <p className="mt-0.5 text-[10px] text-slate-400">{category.description}</p>}</div><button onClick={() => void deleteCategory(category)} className="icon-button" aria-label={`Supprimer ${category.nom}`}><Trash2 size={14} /></button></div>)}
                {categories.length === 0 && <p className="p-4 text-xs text-slate-400">Aucune catégorie.</p>}
              </div>
            </div>
            <div>
              <h3 className="mb-3 text-sm font-bold text-slate-800">Unités de mesure</h3>
              <form onSubmit={saveUnit} className="flex flex-col gap-2 rounded-xl bg-slate-50 p-4 sm:flex-row">
                <input required maxLength={20} className="input-field sm:w-28" placeholder="Code" value={unitForm.code} onChange={(event) => setUnitForm({ ...unitForm, code: event.target.value })} />
                <input required maxLength={80} className="input-field min-w-0 flex-1" placeholder="Libellé" value={unitForm.libelle} onChange={(event) => setUnitForm({ ...unitForm, libelle: event.target.value })} />
                <button disabled={savingUnit} className="button-primary sm:px-3">{editingUnitId != null ? <Check size={15} /> : <Plus size={15} />} {editingUnitId != null ? "Enregistrer" : "Ajouter"}</button>
                {editingUnitId != null && <button type="button" onClick={cancelUnitEdit} className="button-secondary sm:px-3" aria-label="Annuler la modification"><X size={15} /></button>}
              </form>
              <div className="mt-3 max-h-52 divide-y divide-slate-100 overflow-y-auto rounded-xl border border-slate-100">
                {units.map((unit) => <div key={unit.id} className="flex items-center justify-between px-3 py-2.5"><div><p className="text-xs font-semibold text-slate-700">{unit.libelle}</p><p className="mt-0.5 text-[10px] text-slate-400">{unit.code}</p></div><div className="flex shrink-0"><button onClick={() => startUnitEdit(unit)} className="icon-button" aria-label={`Modifier ${unit.libelle}`}><Pencil size={14} /></button><button onClick={() => void deleteUnit(unit)} className="icon-button" aria-label={`Supprimer ${unit.libelle}`}><Trash2 size={14} /></button></div></div>)}
                {units.length === 0 && <p className="p-4 text-xs text-slate-400">Aucune unité.</p>}
              </div>
            </div>
          </div>
          <div className="mt-6 flex justify-end"><button onClick={() => setCategoryModal(false)} className="button-secondary">Fermer</button></div>
        </Modal>
      )}

      {unitsArticle && (
        <Modal title={`Unités de vente · ${unitsArticle.designation}`} subtitle="Définissez les conditionnements et leurs facteurs de conversion." onClose={() => setUnitsArticle(null)} wide>
          <div className="grid gap-6 lg:grid-cols-[1fr_1.15fr]">
            <form onSubmit={saveSaleUnit} className="space-y-3 rounded-xl bg-slate-50 p-4">
              <h3 className="text-sm font-bold text-slate-800">{editingSaleUnitId ? "Modifier l’unité" : "Nouvelle unité de vente"}</h3>
              <Field label="Unité de mesure"><select required className="input-field" value={saleUnitForm.uniteMesureId} onChange={(event) => setSaleUnitForm({ ...saleUnitForm, uniteMesureId: event.target.value })}><option value="">Sélectionner</option>{units.map((unit) => <option key={unit.id} value={unit.id}>{unit.libelle} ({unit.code})</option>)}</select></Field>
              <Field label="Code / conditionnement"><input maxLength={30} className="input-field" placeholder="Ex. CARTON-12" value={saleUnitForm.code} onChange={(event) => setSaleUnitForm({ ...saleUnitForm, code: event.target.value })} /></Field>
              <Field label="Facteur de conversion vers le stock"><input required min="0.000001" step="any" type="number" className="input-field" value={saleUnitForm.facteurConversion} onChange={(event) => setSaleUnitForm({ ...saleUnitForm, facteurConversion: event.target.value })} /></Field>
              <Field label="Prix de vente du conditionnement"><input min="0" step="any" type="number" className="input-field" value={saleUnitForm.prixVente} onChange={(event) => setSaleUnitForm({ ...saleUnitForm, prixVente: event.target.value })} /></Field>
              <div className="flex gap-2"><button disabled={savingSaleUnit} className="button-primary flex-1">{editingSaleUnitId ? <Check size={15} /> : <Plus size={15} />}{editingSaleUnitId ? "Enregistrer" : "Ajouter"}</button>{editingSaleUnitId && <button type="button" onClick={() => { setEditingSaleUnitId(null); setSaleUnitForm({ code: "", uniteMesureId: "", facteurConversion: "1", prixVente: "" }); }} className="button-secondary">Annuler</button>}</div>
            </form>
            <div>
              <h3 className="mb-3 text-sm font-bold text-slate-800">Unités associées</h3>
              <div className="divide-y divide-slate-100 rounded-xl border border-slate-100">
                {saleUnits.map((unit) => <div key={unit.id} className="flex items-center justify-between gap-3 px-3 py-3"><div className="min-w-0"><p className="truncate text-xs font-bold text-slate-700">{unit.code || unit.uniteMesureLibelle}</p><p className="mt-1 text-[10px] text-slate-400">1 unité = {unit.facteurConversion} {unit.uniteMesureLibelle} · {unit.prixVente == null ? "Prix article" : money.format(unit.prixVente)}</p></div><div className="flex shrink-0"><button onClick={() => editSaleUnit(unit)} className="icon-button" aria-label="Modifier"><Pencil size={14} /></button><button onClick={() => void deleteSaleUnit(unit)} className="icon-button" aria-label="Supprimer"><Trash2 size={14} /></button></div></div>)}
                {saleUnits.length === 0 && <p className="p-5 text-center text-xs text-slate-400">Aucune unité secondaire n’est définie.</p>}
              </div>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

function stockStatus(article: Article) {
  if (article.quantiteStock <= 0) return { label: "Rupture", className: "bg-rose-50 text-rose-700", dot: "bg-rose-500" };
  if (article.quantiteStock <= article.seuilAlerteStock) return { label: "Stock bas", className: "bg-orange-50 text-orange-700", dot: "bg-orange-500" };
  return { label: "En stock", className: "bg-emerald-50 text-emerald-700", dot: "bg-emerald-500" };
}

function SummaryCard({ label, value, icon, tone }: { label: string; value: number; icon: ReactNode; tone: "emerald" | "orange" | "red" }) {
  const styles = tone === "emerald" ? "bg-emerald-50 text-emerald-700" : tone === "orange" ? "bg-orange-50 text-orange-700" : "bg-rose-50 text-rose-700";
  return <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><div><p className="text-xs font-semibold text-slate-500">{label}</p><p className="mt-1 text-2xl font-extrabold text-slate-900">{value}</p></div><span className={`grid size-10 place-items-center rounded-xl ${styles}`}>{icon}</span></div>;
}

function SortHeader({ label, active, direction, onClick }: { label: string; active: boolean; direction: "asc" | "desc"; onClick: () => void }) {
  return <th className="px-4 py-3"><button onClick={onClick} className={`inline-flex items-center gap-1.5 ${active ? "text-emerald-700" : "text-slate-400"} hover:text-slate-700`}>{label}{active && (direction === "asc" ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}</button></th>;
}

function Notice({ children, error, onClose }: { children: ReactNode; error?: boolean; onClose?: () => void }) {
  return <div role={error ? "alert" : "status"} className={`flex items-start gap-2 rounded-xl border px-4 py-3 text-xs font-semibold ${error ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}>{error ? <AlertTriangle size={15} className="mt-0.5 shrink-0" /> : <Check size={15} className="mt-0.5 shrink-0" />}<span>{children}</span>{onClose && <button className="ml-auto" onClick={onClose} aria-label="Fermer"><X size={15} /></button>}</div>;
}

function Modal({ title, subtitle, children, onClose, wide = false }: { title: string; subtitle: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  return <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section role="dialog" aria-modal="true" aria-label={title} className={`my-auto w-full rounded-2xl bg-white p-5 shadow-2xl md:p-6 ${wide ? "max-w-4xl" : "max-w-2xl"}`}><div className="mb-5 flex items-start justify-between gap-4"><div><h2 className="text-lg font-bold text-slate-900">{title}</h2><p className="mt-1 text-xs text-slate-500">{subtitle}</p></div><button className="icon-button" onClick={onClose} aria-label="Fermer"><X size={17} /></button></div>{children}</section></div>;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="block"><span className="mb-1.5 block text-[11px] font-bold text-slate-600">{label}</span>{children}</label>;
}

function ModalActions({ busy, onCancel, submitLabel }: { busy: boolean; onCancel: () => void; submitLabel: string }) {
  return <div className="flex justify-end gap-2 border-t border-slate-100 pt-4"><button type="button" onClick={onCancel} className="button-secondary">Annuler</button><button disabled={busy} className="button-primary min-w-40">{busy && <LoaderCircle size={15} className="animate-spin" />}{busy ? "Enregistrement…" : submitLabel}</button></div>;
}
