import { Fragment, useCallback, useEffect, useState, type FormEvent } from "react";
import { Ban, Check, ChevronDown, ChevronRight, CircleAlert, LoaderCircle, PackageMinus, Plus, X } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { money } from "../lib/format";
import { api } from "../services/api";
import { apiErrorMessage } from "../services/apiError";

type Status = "BROUILLON" | "VALIDE" | "ANNULE";
type ReturnLine = {
  ligneApprovisionnementId: number;
  articleId: number;
  reference: string;
  designation: string;
  quantite: number;
  coutUnitaire: number;
  montant: number;
};
type SupplierReturn = {
  id: number;
  numero: string;
  statut: Status;
  fournisseur: string;
  numeroBon: string;
  motif: string;
  dateCreation: string;
  creePar: string;
  dateValidation: string | null;
  validePar: string | null;
  montantTotal: number;
  lignes: ReturnLine[];
};
type Receipt = { id: number; numero: string; fournisseur: string; statut: "BROUILLON" | "RECU" | "ANNULE"; dateApprovisionnement: string };
type Returnable = {
  ligneApprovisionnementId: number;
  reference: string;
  designation: string;
  quantiteRecue: number;
  quantiteDejaRetournee: number;
  quantiteRetournable: number;
  coutUnitaire: number;
  stockActuel: number;
};

const statusLabel: Record<Status, { label: string; className: string }> = {
  BROUILLON: { label: "À valider", className: "bg-amber-50 text-amber-700" },
  VALIDE: { label: "Validé", className: "bg-emerald-50 text-emerald-700" },
  ANNULE: { label: "Annulé", className: "bg-slate-100 text-slate-500" },
};
const dayTime = (value: string | null) => (value ? new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—");

export default function RetoursFournisseursPage() {
  const { user } = useAuth();
  const isManager = (user?.roles ?? []).some((role) => ["ADMIN", "MANAGER"].includes(role.toUpperCase().replace(/^ROLE_/, "")));
  const [returns, setReturns] = useState<SupplierReturn[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ text: string; isError: boolean } | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [creating, setCreating] = useState(false);
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [receiptId, setReceiptId] = useState("");
  const [lines, setLines] = useState<Returnable[]>([]);
  const [quantities, setQuantities] = useState<Record<number, string>>({});
  const [reason, setReason] = useState("");

  const load = useCallback(async () => {
    try {
      const { data } = await api.get<SupplierReturn[]>("/retours-fournisseurs");
      setReturns(data);
    } catch (failure) {
      setNotice({ text: apiErrorMessage(failure, "Impossible de charger les retours."), isError: true });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function startCreation() {
    setCreating(true);
    setReceiptId("");
    setLines([]);
    setQuantities({});
    setReason("");
    try {
      const { data } = await api.get<Receipt[]>("/approvisionnements");
      setReceipts(data.filter((receipt) => receipt.statut === "RECU"));
    } catch (failure) {
      setNotice({ text: apiErrorMessage(failure, "Impossible de charger les bons reçus."), isError: true });
    }
  }

  async function chooseReceipt(id: string) {
    setReceiptId(id);
    setLines([]);
    setQuantities({});
    if (!id) return;
    try {
      const { data } = await api.get<Returnable[]>(`/retours-fournisseurs/bons/${id}/lignes`);
      setLines(data);
    } catch (failure) {
      setNotice({ text: apiErrorMessage(failure, "Impossible de charger les lignes du bon."), isError: true });
    }
  }

  const chosen = lines
    .map((line) => ({ line, quantity: Number(quantities[line.ligneApprovisionnementId] ?? 0) }))
    .filter(({ quantity }) => quantity > 0);
  const total = chosen.reduce((sum, { line, quantity }) => sum + line.coutUnitaire * quantity, 0);

  async function submitCreation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (chosen.length === 0) {
      setNotice({ text: "Indiquez la quantité rendue sur au moins une ligne.", isError: true });
      return;
    }
    setBusy(true);
    try {
      const { data } = await api.post<SupplierReturn>("/retours-fournisseurs", {
        approvisionnementId: Number(receiptId),
        motif: reason.trim(),
        lignes: chosen.map(({ line, quantity }) => ({ ligneApprovisionnementId: line.ligneApprovisionnementId, quantite: quantity })),
      });
      setNotice({ text: `Retour ${data.numero} préparé : en attente de validation par un responsable.`, isError: false });
      setCreating(false);
      await load();
    } catch (failure) {
      setNotice({ text: apiErrorMessage(failure, "Le retour n’a pas pu être préparé."), isError: true });
    } finally {
      setBusy(false);
    }
  }

  async function act(item: SupplierReturn, action: "validation" | "annulation") {
    const question = action === "validation"
      ? `Valider le retour ${item.numero} ? Le stock sera réduit et un avoir de ${money.format(item.montantTotal)} sera crédité au fournisseur. Cette opération ne s’annule pas.`
      : `Annuler le retour ${item.numero} ? Le stock et le compte du fournisseur ne seront pas modifiés.`;
    if (!window.confirm(question)) return;
    setBusy(true);
    try {
      await api.post(`/retours-fournisseurs/${item.id}/${action}`);
      setNotice({ text: action === "validation" ? `Retour ${item.numero} validé : stock réduit, avoir crédité.` : `Retour ${item.numero} annulé.`, isError: false });
      await load();
    } catch (failure) {
      setNotice({ text: apiErrorMessage(failure, "L’opération a échoué."), isError: true });
    } finally {
      setBusy(false);
    }
  }

  function toggle(id: number) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Retours fournisseurs</h1>
          <p className="mt-1.5 text-sm text-slate-500">Rendez de la marchandise reçue : à la validation, le stock baisse et le fournisseur vous crédite un avoir.</p>
        </div>
        <button className="button-primary" onClick={() => void startCreation()}><Plus size={16} /> Nouveau retour</button>
      </header>

      {notice && (
        <div role={notice.isError ? "alert" : "status"} className={`flex items-center gap-2 rounded-xl border px-4 py-3 text-xs font-semibold ${notice.isError ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}>
          <CircleAlert size={16} /> {notice.text}
          <button className="ml-auto opacity-60 hover:opacity-100" onClick={() => setNotice(null)} aria-label="Fermer"><X size={15} /></button>
        </div>
      )}

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[860px] text-left">
            <thead className="bg-slate-50/80 text-xs font-semibold text-slate-600">
              <tr><th className="px-5 py-3">Retour</th><th className="px-4 py-3">Fournisseur</th><th className="px-4 py-3">Bon</th><th className="px-4 py-3">Préparé</th><th className="px-4 py-3">Statut</th><th className="px-4 py-3 text-right">Avoir</th><th className="px-5 py-3 text-right"><span className="sr-only">Actions</span></th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr><td colSpan={7} className="h-32 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>
              ) : returns.length === 0 ? (
                <tr><td colSpan={7} className="h-32 text-center text-sm text-slate-400"><PackageMinus className="mr-2 inline" size={16} />Aucun retour pour l’instant.</td></tr>
              ) : returns.map((item) => (
                <Fragment key={item.id}>
                  <tr className="hover:bg-slate-50/70">
                    <td className="px-5 py-3.5">
                      <button className="flex items-center gap-1.5 text-xs font-bold text-slate-800" onClick={() => toggle(item.id)} aria-expanded={expanded.has(item.id)}>
                        {expanded.has(item.id) ? <ChevronDown size={13} /> : <ChevronRight size={13} />}{item.numero}
                      </button>
                    </td>
                    <td className="px-4 py-3.5 text-xs text-slate-700">{item.fournisseur}</td>
                    <td className="px-4 py-3.5 text-xs text-slate-600">{item.numeroBon}</td>
                    <td className="px-4 py-3.5 text-xs text-slate-600">{dayTime(item.dateCreation)} · {item.creePar}</td>
                    <td className="px-4 py-3.5"><span className={`rounded-full px-2.5 py-1 text-xs font-bold ${statusLabel[item.statut].className}`}>{statusLabel[item.statut].label}</span></td>
                    <td className="px-4 py-3.5 text-right text-xs font-bold text-slate-900">{money.format(item.montantTotal)}</td>
                    <td className="px-5 py-3.5">
                      {item.statut === "BROUILLON" && (
                        <div className="flex justify-end gap-2">
                          {isManager && <button disabled={busy} className="button-primary h-8 px-3 text-xs" onClick={() => void act(item, "validation")}><Check size={13} /> Valider</button>}
                          <button disabled={busy} className="inline-flex h-8 items-center gap-1.5 rounded-xl border border-rose-200 px-3 text-xs font-bold text-rose-700 hover:bg-rose-50" onClick={() => void act(item, "annulation")}><Ban size={13} /> Annuler</button>
                        </div>
                      )}
                    </td>
                  </tr>
                  {expanded.has(item.id) && (
                    <tr className="bg-slate-50/60">
                      <td colSpan={7} className="px-5 py-3">
                        <p className="mb-2 text-xs text-slate-600"><span className="font-bold text-slate-800">Motif :</span> {item.motif}{item.validePar && ` · validé ${dayTime(item.dateValidation)} par ${item.validePar}`}</p>
                        <ul className="space-y-1">
                          {item.lignes.map((line) => (
                            <li key={line.ligneApprovisionnementId} className="flex flex-wrap gap-x-4 text-xs text-slate-600">
                              <span className="w-64 font-semibold text-slate-800">{line.designation} <span className="font-normal text-slate-400">({line.reference})</span></span>
                              <span>{line.quantite} × {money.format(line.coutUnitaire)}</span>
                              <span className="font-semibold text-slate-800">{money.format(line.montant)}</span>
                            </li>
                          ))}
                        </ul>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {creating && (
        <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) setCreating(false); }}>
          <section role="dialog" aria-modal="true" aria-label="Nouveau retour fournisseur" className="my-auto w-full max-w-3xl rounded-2xl bg-white p-6 shadow-2xl">
            <div className="mb-5 flex items-center justify-between">
              <div className="flex items-center gap-2"><PackageMinus size={18} className="text-emerald-700" /><h2 className="text-lg font-bold text-slate-900">Nouveau retour fournisseur</h2></div>
              <button onClick={() => setCreating(false)} className="icon-button" aria-label="Fermer"><X size={16} /></button>
            </div>
            <form onSubmit={(event) => void submitCreation(event)} className="space-y-4">
              <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Bon reçu à l’origine du retour</span>
                <select required className="input-field" value={receiptId} onChange={(event) => void chooseReceipt(event.target.value)}>
                  <option value="">Choisir un bon reçu</option>
                  {receipts.map((receipt) => <option key={receipt.id} value={receipt.id}>{receipt.numero} · {receipt.fournisseur} · {receipt.dateApprovisionnement}</option>)}
                </select>
              </label>

              {lines.length > 0 && (
                <div className="overflow-x-auto rounded-xl border border-slate-200">
                  <table className="w-full min-w-[620px] text-left">
                    <thead className="bg-slate-50 text-xs font-semibold text-slate-600">
                      <tr><th className="px-4 py-2.5">Article</th><th className="px-3 py-2.5 text-right">Reçu</th><th className="px-3 py-2.5 text-right">Déjà rendu</th><th className="px-3 py-2.5 text-right">En rayon</th><th className="px-3 py-2.5 text-right">Coût</th><th className="px-4 py-2.5">À rendre</th></tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {lines.map((line) => {
                        const limit = Math.min(line.quantiteRetournable, line.stockActuel);
                        return (
                          <Fragment key={line.ligneApprovisionnementId}>
                            <tr>
                              <td className="px-4 py-2.5"><p className="text-xs font-bold text-slate-800">{line.designation}</p><p className="text-xs text-slate-400">{line.reference}</p></td>
                              <td className="px-3 py-2.5 text-right text-xs text-slate-600">{line.quantiteRecue}</td>
                              <td className="px-3 py-2.5 text-right text-xs text-slate-600">{line.quantiteDejaRetournee}</td>
                              <td className="px-3 py-2.5 text-right text-xs text-slate-600">{line.stockActuel}</td>
                              <td className="px-3 py-2.5 text-right text-xs text-slate-600">{money.format(line.coutUnitaire)}</td>
                              <td className="px-4 py-2.5">
                                <input
                                  type="number" min="0" max={limit} step="1" disabled={limit === 0} aria-label={`Quantité rendue de ${line.designation}`}
                                  className="input-field h-9 w-24" value={quantities[line.ligneApprovisionnementId] ?? ""}
                                  onChange={(event) => setQuantities({ ...quantities, [line.ligneApprovisionnementId]: event.target.value })}
                                />
                              </td>
                            </tr>
                          </Fragment>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}

              <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Motif du retour</span>
                <input required maxLength={300} className="input-field" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Ex. : écrans défectueux à la livraison" />
              </label>

              <div className="flex items-center justify-between border-t border-slate-100 pt-4">
                <p className="text-xs text-slate-600">Avoir attendu : <span className="font-bold text-slate-900">{money.format(total)}</span></p>
                <div className="flex gap-2">
                  <button type="button" disabled={busy} className="button-secondary" onClick={() => setCreating(false)}>Annuler</button>
                  <button disabled={busy || chosen.length === 0} className="button-primary">{busy ? <LoaderCircle size={15} className="animate-spin" /> : <Check size={15} />} Préparer le retour</button>
                </div>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}
