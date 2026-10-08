import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import {
  ArrowLeft,
  Ban,
  Check,
  CircleAlert,
  ClipboardList,
  Download,
  LoaderCircle,
  Plus,
  Send,
  Undo2,
  X,
} from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { money } from "../lib/format";
import { api } from "../services/api";
import { apiErrorMessage as errorMessage } from "../services/apiError";

type Status = "EN_SAISIE" | "SOUMIS" | "VALIDE" | "ANNULE";
type Reason = "CASSE" | "VOL" | "PEREMPTION" | "ERREUR_DE_SAISIE" | "ECART_DE_COMPTAGE" | "AUTRE";
type Inventory = {
  id: number;
  code: string;
  statut: Status;
  perimetre: "TOUT" | "CATEGORIE";
  categorie: string | null;
  commentaire: string | null;
  ouvertLe: string;
  ouvertPar: string;
  soumisLe: string | null;
  valideLe: string | null;
  validePar: string | null;
  nombreLignes: number;
  nombreComptees: number;
  nombreEcarts: number;
  valeurEcartTotal: number;
};
type Line = {
  articleId: number;
  reference: string;
  designation: string;
  categorie: string | null;
  unite: string | null;
  stockTheorique: number;
  stockCompte: number | null;
  ecart: number | null;
  coutUnitaire: number;
  valeurEcart: number | null;
  motif: Reason | null;
  commentaire: string | null;
  ecartApplique: number | null;
};
type Category = { id: number; nom: string };

const reasons: { value: Reason; label: string }[] = [
  { value: "CASSE", label: "Casse" },
  { value: "VOL", label: "Vol" },
  { value: "PEREMPTION", label: "Péremption" },
  { value: "ERREUR_DE_SAISIE", label: "Erreur de saisie" },
  { value: "ECART_DE_COMPTAGE", label: "Écart de comptage" },
  { value: "AUTRE", label: "Autre (préciser)" },
];
const statusLabels: Record<Status, { label: string; className: string }> = {
  EN_SAISIE: { label: "En saisie", className: "bg-sky-50 text-sky-700" },
  SOUMIS: { label: "À valider", className: "bg-amber-50 text-amber-700" },
  VALIDE: { label: "Validé", className: "bg-emerald-50 text-emerald-700" },
  ANNULE: { label: "Annulé", className: "bg-slate-100 text-slate-500" },
};
const PAGE = 500;

function dateTime(value: string | null) {
  return value ? new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—";
}

function signed(value: number) {
  return `${value > 0 ? "+" : ""}${value}`;
}

export default function InventairesPage() {
  const { user } = useAuth();
  const isManager = (user?.roles ?? []).some((role) => ["ADMIN", "MANAGER"].includes(role.toUpperCase().replace(/^ROLE_/, "")));
  const [inventories, setInventories] = useState<Inventory[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [lines, setLines] = useState<Line[]>([]);
  const [loading, setLoading] = useState(true);
  const [linesLoading, setLinesLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ text: string; isError: boolean } | null>(null);
  const [openModal, setOpenModal] = useState(false);
  const [scope, setScope] = useState<"TOUT" | "CATEGORIE">("TOUT");
  const [categoryId, setCategoryId] = useState("");
  const [comment, setComment] = useState("");
  const [onlyGaps, setOnlyGaps] = useState(false);
  const [search, setSearch] = useState("");

  const selected = inventories.find((inventory) => inventory.id === selectedId) ?? null;
  const editable = selected?.statut === "EN_SAISIE";

  const loadInventories = useCallback(async () => {
    try {
      const { data } = await api.get<Inventory[]>("/inventaires");
      setInventories(data);
    } catch (reason) {
      setNotice({ text: errorMessage(reason, "Impossible de charger les inventaires."), isError: true });
    } finally {
      setLoading(false);
    }
  }, []);

  const loadLines = useCallback(async (id: number) => {
    setLinesLoading(true);
    try {
      const all: Line[] = [];
      for (let page = 0; ; page += 1) {
        const response = await api.get<Line[]>(`/inventaires/${id}/lignes`, { params: { page, size: PAGE } });
        all.push(...response.data);
        const total = Number(response.headers["x-total-count"] ?? all.length);
        if (all.length >= total || response.data.length === 0) break;
      }
      setLines(all);
    } catch (reason) {
      setNotice({ text: errorMessage(reason, "Impossible de charger les lignes de l’inventaire."), isError: true });
    } finally {
      setLinesLoading(false);
    }
  }, []);

  useEffect(() => { void loadInventories(); }, [loadInventories]);
  useEffect(() => {
    api.get<Category[]>("/articles/categories").then(({ data }) => setCategories(data)).catch(() => undefined);
  }, []);
  useEffect(() => {
    if (selectedId !== null) void loadLines(selectedId);
    else setLines([]);
  }, [selectedId, loadLines]);

  const visibleLines = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("fr");
    return lines.filter((line) => (!onlyGaps || (line.ecart !== null && line.ecart !== 0))
      && (!query || line.reference.toLocaleLowerCase("fr").includes(query) || line.designation.toLocaleLowerCase("fr").includes(query)));
  }, [lines, onlyGaps, search]);

  async function run(action: () => Promise<string>) {
    setBusy(true);
    try {
      setNotice({ text: await action(), isError: false });
      await loadInventories();
      if (selectedId !== null) await loadLines(selectedId);
    } catch (reason) {
      setNotice({ text: errorMessage(reason, "L’opération a échoué."), isError: true });
    } finally {
      setBusy(false);
    }
  }

  async function openInventory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    try {
      const { data } = await api.post<Inventory>("/inventaires", {
        perimetre: scope,
        categorieId: scope === "CATEGORIE" ? Number(categoryId) : null,
        commentaire: comment.trim() || null,
      });
      setOpenModal(false);
      setComment("");
      await loadInventories();
      setSelectedId(data.id);
      setNotice({ text: `Inventaire ${data.code} ouvert : ${data.nombreLignes} article(s) à compter.`, isError: false });
    } catch (reason) {
      setNotice({ text: errorMessage(reason, "Ouverture de l’inventaire impossible."), isError: true });
    } finally {
      setBusy(false);
    }
  }

  /** Enregistre le comptage d'une ligne dès que la personne quitte la case (ou choisit un motif). */
  async function saveLine(line: Line, patch: { stockCompte?: number; motif?: Reason | null; commentaire?: string }) {
    const stockCompte = patch.stockCompte ?? line.stockCompte;
    if (stockCompte === null || stockCompte === undefined) return;
    try {
      const { data } = await api.put<Line>(`/inventaires/${selectedId}/lignes/${line.articleId}`, {
        stockCompte,
        motif: patch.motif === undefined ? line.motif : patch.motif,
        commentaire: patch.commentaire === undefined ? line.commentaire : patch.commentaire || null,
      });
      setLines((current) => current.map((candidate) => (candidate.articleId === data.articleId ? data : candidate)));
      void loadInventories();
    } catch (reason) {
      setNotice({ text: errorMessage(reason, "Comptage non enregistré."), isError: true });
    }
  }

  async function exportSheet() {
    if (!selected) return;
    try {
      const { data } = await api.get<Blob>(`/inventaires/${selected.id}/export.xlsx`, { responseType: "blob" });
      const url = URL.createObjectURL(data);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${selected.code}.xlsx`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (reason) {
      setNotice({ text: errorMessage(reason, "L’export a échoué."), isError: true });
    }
  }

  const post = (path: string, success: string) => run(async () => {
    await api.post(`/inventaires/${selected?.id}/${path}`);
    return success;
  });

  return (
    <div className="space-y-6">
      <header className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <p className="mb-1 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Stock</p>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">Inventaires physiques</h1>
          <p className="mt-1.5 text-sm text-slate-500">Comptez le stock réel, justifiez les écarts, puis faites valider par un responsable.</p>
        </div>
        {!selected && (
          <button className="button-primary" onClick={() => setOpenModal(true)}><Plus size={16} /> Ouvrir un inventaire</button>
        )}
        {selected && (
          <button className="button-secondary" onClick={() => setSelectedId(null)}><ArrowLeft size={16} /> Tous les inventaires</button>
        )}
      </header>

      {notice && (
        <div role={notice.isError ? "alert" : "status"} className={`flex items-center gap-2 rounded-xl border px-4 py-3 text-xs font-semibold ${notice.isError ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}>
          <CircleAlert size={16} /> {notice.text}
          <button className="ml-auto opacity-60 hover:opacity-100" onClick={() => setNotice(null)} aria-label="Fermer"><X size={15} /></button>
        </div>
      )}

      {!selected && (
        <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left">
              <thead className="bg-slate-50/80 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                <tr><th className="px-5 py-3">Inventaire</th><th className="px-4 py-3">Périmètre</th><th className="px-4 py-3">Ouvert</th><th className="px-4 py-3">Statut</th><th className="px-4 py-3 text-right">Comptés</th><th className="px-4 py-3 text-right">Écarts</th><th className="px-5 py-3 text-right">Valeur de l’écart</th></tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr><td colSpan={7} className="h-32 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
                ) : inventories.length ? inventories.map((inventory) => (
                  <tr key={inventory.id} className="cursor-pointer hover:bg-slate-50/70" onClick={() => setSelectedId(inventory.id)}>
                    <td className="px-5 py-3.5"><p className="text-xs font-bold text-slate-800">{inventory.code}</p>{inventory.commentaire && <p className="mt-0.5 text-[10px] text-slate-400">{inventory.commentaire}</p>}</td>
                    <td className="px-4 py-3.5 text-xs text-slate-600">{inventory.categorie ?? "Tout le catalogue"}</td>
                    <td className="px-4 py-3.5 text-xs text-slate-600">{dateTime(inventory.ouvertLe)} · {inventory.ouvertPar}</td>
                    <td className="px-4 py-3.5"><span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${statusLabels[inventory.statut].className}`}>{statusLabels[inventory.statut].label}</span></td>
                    <td className="px-4 py-3.5 text-right text-xs text-slate-600">{inventory.nombreComptees} / {inventory.nombreLignes}</td>
                    <td className="px-4 py-3.5 text-right text-xs text-slate-600">{inventory.nombreEcarts}</td>
                    <td className={`px-5 py-3.5 text-right text-xs font-bold ${inventory.valeurEcartTotal < 0 ? "text-rose-700" : "text-slate-800"}`}>{money.format(inventory.valeurEcartTotal)}</td>
                  </tr>
                )) : (
                  <tr><td colSpan={7} className="h-32 text-center text-sm text-slate-400"><ClipboardList className="mr-2 inline" size={16} />Aucun inventaire pour l’instant.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {selected && (
        <>
          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-3">
                  <h2 className="text-lg font-bold text-slate-900">{selected.code}</h2>
                  <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${statusLabels[selected.statut].className}`}>{statusLabels[selected.statut].label}</span>
                </div>
                <p className="mt-1 text-xs text-slate-500">
                  {selected.categorie ?? "Tout le catalogue"} · ouvert {dateTime(selected.ouvertLe)} par {selected.ouvertPar}
                  {selected.validePar ? ` · validé ${dateTime(selected.valideLe)} par ${selected.validePar}` : ""}
                </p>
                <p className="mt-2 text-xs font-semibold text-slate-700">
                  {selected.nombreComptees} / {selected.nombreLignes} article(s) comptés · {selected.nombreEcarts} écart(s) · valeur de l’écart{" "}
                  <span className={selected.valeurEcartTotal < 0 ? "text-rose-700" : "text-slate-900"}>{money.format(selected.valeurEcartTotal)}</span>
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <button className="button-secondary" onClick={() => void exportSheet()}><Download size={15} /> Feuille Excel</button>
                {selected.statut === "EN_SAISIE" && (
                  <button disabled={busy} className="button-primary" onClick={() => void post("soumission", "Inventaire soumis à validation.")}><Send size={15} /> Soumettre</button>
                )}
                {selected.statut === "SOUMIS" && isManager && (
                  <>
                    <button disabled={busy} className="button-secondary" onClick={() => void post("reprise", "Inventaire repris en saisie.")}><Undo2 size={15} /> Reprendre la saisie</button>
                    <button
                      disabled={busy}
                      className="button-primary"
                      onClick={() => { if (window.confirm(`Valider l’inventaire ${selected.code} ? Les écarts seront appliqués au stock et ne pourront plus être modifiés.`)) void post("validation", "Inventaire validé : les écarts sont appliqués au stock."); }}
                    ><Check size={15} /> Valider</button>
                  </>
                )}
                {(selected.statut === "EN_SAISIE" || selected.statut === "SOUMIS") && isManager && (
                  <button
                    disabled={busy}
                    className="inline-flex h-10 items-center gap-2 rounded-xl border border-rose-200 px-3 text-xs font-bold text-rose-700 hover:bg-rose-50"
                    onClick={() => { if (window.confirm(`Annuler l’inventaire ${selected.code} ? Le stock ne sera pas modifié.`)) void post("annulation", "Inventaire annulé, le stock n’a pas été modifié."); }}
                  ><Ban size={15} /> Annuler</button>
                )}
              </div>
            </div>
            {selected.statut === "SOUMIS" && !isManager && (
              <p className="mt-3 rounded-xl bg-amber-50 px-4 py-2.5 text-xs font-semibold text-amber-900">En attente de validation par un responsable (MANAGER ou ADMIN), autre que la personne qui a ouvert l’inventaire.</p>
            )}
          </section>

          <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-4">
              <input className="input-field max-w-xs" placeholder="Rechercher un article…" value={search} onChange={(event) => setSearch(event.target.value)} />
              <label className="flex items-center gap-2 text-xs font-semibold text-slate-600">
                <input type="checkbox" checked={onlyGaps} onChange={(event) => setOnlyGaps(event.target.checked)} /> Écarts seulement
              </label>
              <span className="ml-auto text-[11px] text-slate-500">{visibleLines.length} ligne(s)</span>
            </div>
            <div className="max-h-[65vh] overflow-auto">
              <table className="w-full min-w-[920px] text-left">
                <thead className="sticky top-0 bg-slate-50 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  <tr><th className="px-5 py-3">Article</th><th className="px-3 py-3 text-right">Théorique</th><th className="px-3 py-3">Compté</th><th className="px-3 py-3 text-right">Écart</th><th className="px-3 py-3 text-right">Valeur</th><th className="px-3 py-3">Motif</th><th className="px-4 py-3">Commentaire</th></tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {linesLoading ? (
                    <tr><td colSpan={7} className="h-32 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
                  ) : visibleLines.map((line) => {
                    const gap = line.ecart !== null && line.ecart !== 0;
                    return (
                      <tr key={line.articleId} className={gap ? "bg-amber-50/40" : ""}>
                        <td className="px-5 py-2.5"><p className="text-xs font-bold text-slate-800">{line.designation}</p><p className="text-[10px] text-slate-400">{line.reference}{line.unite ? ` · ${line.unite}` : ""}</p></td>
                        <td className="px-3 py-2.5 text-right text-xs text-slate-600">{line.stockTheorique}</td>
                        <td className="px-3 py-2.5">
                          {editable ? (
                            <input
                              type="number" min="0" step="1" aria-label={`Quantité comptée de ${line.designation}`}
                              className="input-field h-9 w-24" defaultValue={line.stockCompte ?? ""}
                              onBlur={(event) => { const value = event.target.value; if (value !== "" && Number(value) !== line.stockCompte) void saveLine(line, { stockCompte: Number(value) }); }}
                            />
                          ) : <span className="text-xs text-slate-700">{line.stockCompte ?? "—"}</span>}
                        </td>
                        <td className={`px-3 py-2.5 text-right text-xs font-bold ${gap ? (line.ecart! < 0 ? "text-rose-700" : "text-emerald-700") : "text-slate-400"}`}>{line.ecart === null ? "—" : signed(line.ecart)}</td>
                        <td className="px-3 py-2.5 text-right text-xs text-slate-600">{line.valeurEcart === null || !gap ? "—" : money.format(line.valeurEcart)}</td>
                        <td className="px-3 py-2.5">
                          {gap && editable ? (
                            <select className="input-field h-9" aria-label={`Motif de l’écart de ${line.designation}`} value={line.motif ?? ""} onChange={(event) => void saveLine(line, { motif: (event.target.value || null) as Reason | null })}>
                              <option value="">Motif requis</option>
                              {reasons.map((reason) => <option key={reason.value} value={reason.value}>{reason.label}</option>)}
                            </select>
                          ) : <span className="text-xs text-slate-600">{reasons.find((reason) => reason.value === line.motif)?.label ?? "—"}</span>}
                        </td>
                        <td className="px-4 py-2.5">
                          {gap && editable ? (
                            <input className="input-field h-9" maxLength={300} defaultValue={line.commentaire ?? ""} aria-label={`Commentaire de ${line.designation}`}
                              onBlur={(event) => { if (event.target.value !== (line.commentaire ?? "")) void saveLine(line, { commentaire: event.target.value }); }} />
                          ) : <span className="text-xs text-slate-500">{line.commentaire ?? ""}</span>}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}

      {openModal && (
        <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4 print:hidden" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) setOpenModal(false); }}>
          <section role="dialog" aria-modal="true" aria-label="Ouvrir un inventaire" className="my-auto w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <h2 className="mb-1 text-base font-bold text-slate-900">Ouvrir un inventaire</h2>
            <p className="mb-5 text-[11px] text-slate-500">Le stock théorique est figé à l’ouverture. Un seul inventaire peut être en cours à la fois.</p>
            <form onSubmit={openInventory} className="space-y-4">
              <label className="block"><span className="mb-1.5 block text-[10px] font-bold text-slate-600">Périmètre</span>
                <select className="input-field" value={scope} onChange={(event) => setScope(event.target.value as "TOUT" | "CATEGORIE")}>
                  <option value="TOUT">Tout le catalogue</option>
                  <option value="CATEGORIE">Une catégorie (rayon)</option>
                </select>
              </label>
              {scope === "CATEGORIE" && (
                <label className="block"><span className="mb-1.5 block text-[10px] font-bold text-slate-600">Catégorie</span>
                  <select required className="input-field" value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
                    <option value="">Choisir une catégorie</option>
                    {categories.map((category) => <option key={category.id} value={category.id}>{category.nom}</option>)}
                  </select>
                </label>
              )}
              <label className="block"><span className="mb-1.5 block text-[10px] font-bold text-slate-600">Commentaire (facultatif)</span>
                <input className="input-field" maxLength={500} value={comment} onChange={(event) => setComment(event.target.value)} placeholder="Ex. : inventaire de fin de mois" />
              </label>
              <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
                <button type="button" disabled={busy} className="button-secondary" onClick={() => setOpenModal(false)}>Annuler</button>
                <button disabled={busy} className="button-primary min-w-40">{busy ? <LoaderCircle size={15} className="animate-spin" /> : <Check size={15} />} Ouvrir</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}
