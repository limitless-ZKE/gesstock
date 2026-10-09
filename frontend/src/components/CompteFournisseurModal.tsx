import { Fragment, useCallback, useEffect, useState, type FormEvent } from "react";
import { Banknote, ChevronDown, ChevronRight, CircleAlert, LoaderCircle, Plus, ReceiptText, X } from "lucide-react";
import { money } from "../lib/format";
import { api } from "../services/api";
import { apiErrorMessage } from "../services/apiError";

type Payment = {
  id: number;
  detteId: number;
  montant: number;
  source: "PAIEMENT" | "AVOIR";
  modeReglement: string | null;
  reference: string | null;
  dateReglement: string;
  effectuePar: string | null;
  avoirId: number | null;
};
type Debt = {
  id: number;
  dateCreation: string;
  numeroBon: string;
  montantInitial: number;
  montantRegle: number;
  solde: number;
  statut: "EN_COURS" | "PARTIELLEMENT_REGLEE" | "REGLEE" | "ANNULEE";
  reglements: Payment[];
};
type Credit = {
  id: number;
  dateCreation: string;
  montant: number;
  montantUtilise: number;
  restant: number;
  motif: string;
  creePar: string;
};
type Account = {
  fournisseurId: number;
  nom: string;
  soldeDu: number;
  creditDisponible: number;
  dettes: Debt[];
  avoirs: Credit[];
};

const modes: { value: string; label: string }[] = [
  { value: "ESPECES", label: "Espèces (sortie de caisse)" },
  { value: "VIREMENT", label: "Virement" },
  { value: "CHEQUE", label: "Chèque" },
  { value: "MOBILE_MONEY", label: "Mobile money" },
  { value: "CARTE", label: "Carte" },
  { value: "AUTRE", label: "Autre" },
];
const modeLabel = (value: string | null) => modes.find((mode) => mode.value === value)?.label.replace(" (sortie de caisse)", "") ?? "—";
const statusLabel: Record<Debt["statut"], { label: string; className: string }> = {
  EN_COURS: { label: "À payer", className: "bg-rose-50 text-rose-700" },
  PARTIELLEMENT_REGLEE: { label: "Partielle", className: "bg-amber-50 text-amber-700" },
  REGLEE: { label: "Réglée", className: "bg-emerald-50 text-emerald-700" },
  ANNULEE: { label: "Annulée", className: "bg-slate-100 text-slate-500" },
};
const day = (value: string) => new Intl.DateTimeFormat("fr-FR", { dateStyle: "short" }).format(new Date(value));
const dayTime = (value: string) => new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

type Props = {
  supplier: { id: number; nom: string };
  /** Un avoir saisi à la main efface une dette sans qu'aucun argent ne bouge : réservé aux responsables. */
  canCreateCredit: boolean;
  onClose: () => void;
  /** Appelé après toute écriture, pour rafraîchir les soldes de la liste. */
  onChanged: () => void;
};

export default function CompteFournisseurModal({ supplier, canCreateCredit, onClose, onChanged }: Props) {
  const [account, setAccount] = useState<Account | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ text: string; isError: boolean } | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [paying, setPaying] = useState<Debt | null>(null);
  const [payment, setPayment] = useState({ montant: "", mode: "VIREMENT", reference: "" });
  const [creditOpen, setCreditOpen] = useState(false);
  const [credit, setCredit] = useState({ montant: "", motif: "" });

  const load = useCallback(async () => {
    try {
      const { data } = await api.get<Account>(`/fournisseurs/${supplier.id}/compte`);
      setAccount(data);
    } catch (reason) {
      setNotice({ text: apiErrorMessage(reason, "Impossible de charger le compte."), isError: true });
    } finally {
      setLoading(false);
    }
  }, [supplier.id]);

  useEffect(() => { void load(); }, [load]);

  function toggle(id: number) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  function startPayment(debt: Debt) {
    setPaying(debt);
    setCreditOpen(false);
    setPayment({ montant: String(debt.solde), mode: "VIREMENT", reference: "" });
  }

  async function submitPayment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!paying) return;
    setBusy(true);
    try {
      await api.post(`/fournisseurs/dettes/${paying.id}/reglements`, {
        montant: Number(payment.montant),
        modeReglement: payment.mode,
        reference: payment.reference.trim() || null,
      });
      setNotice({ text: `Paiement de ${money.format(Number(payment.montant))} enregistré pour le bon ${paying.numeroBon}.`, isError: false });
      setPaying(null);
      await load();
      onChanged();
    } catch (reason) {
      setNotice({ text: apiErrorMessage(reason, "Le paiement a échoué."), isError: true });
    } finally {
      setBusy(false);
    }
  }

  async function submitCredit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    try {
      await api.post(`/fournisseurs/${supplier.id}/avoirs`, { montant: Number(credit.montant), motif: credit.motif.trim() });
      setNotice({ text: "Avoir enregistré et imputé sur les dettes les plus anciennes.", isError: false });
      setCreditOpen(false);
      setCredit({ montant: "", motif: "" });
      await load();
      onChanged();
    } catch (reason) {
      setNotice({ text: apiErrorMessage(reason, "L’avoir n’a pas pu être enregistré."), isError: true });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4"
      onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose(); }}
    >
      <section role="dialog" aria-modal="true" aria-label={`Compte de ${supplier.nom}`} className="my-auto w-full max-w-4xl rounded-2xl bg-white p-6 shadow-2xl">
        <div className="mb-5 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ReceiptText size={18} className="text-emerald-700" />
            <h2 className="text-lg font-bold text-slate-900">Compte de {supplier.nom}</h2>
          </div>
          <button onClick={onClose} className="icon-button" aria-label="Fermer"><X size={16} /></button>
        </div>

        {notice && (
          <div role={notice.isError ? "alert" : "status"} className={`mb-4 flex items-center gap-2 rounded-xl border px-4 py-3 text-xs font-semibold ${notice.isError ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}>
            <CircleAlert size={15} /> {notice.text}
          </div>
        )}

        {loading || !account ? (
          <p className="py-16 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={15} />Chargement…</p>
        ) : (
          <div className="space-y-6">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-xl border border-slate-200 p-4">
                <p className="text-xs font-semibold text-slate-600">Nous lui devons</p>
                <p className={`mt-1 text-2xl font-bold ${account.soldeDu > 0 ? "text-rose-700" : "text-slate-900"}`}>{money.format(account.soldeDu)}</p>
              </div>
              <div className="rounded-xl border border-slate-200 p-4">
                <p className="text-xs font-semibold text-slate-600">Crédit chez lui (avoirs)</p>
                <p className={`mt-1 text-2xl font-bold ${account.creditDisponible > 0 ? "text-emerald-700" : "text-slate-900"}`}>{money.format(account.creditDisponible)}</p>
                {account.creditDisponible > 0 && <p className="mt-1 text-xs text-slate-500">Il sera déduit automatiquement de ses prochains bons.</p>}
              </div>
            </div>

            <div>
              <h3 className="mb-2 text-sm font-bold text-slate-900">Dettes (un bon reçu = une dette)</h3>
              <div className="overflow-x-auto rounded-xl border border-slate-200">
                <table className="w-full min-w-[640px] text-left">
                  <thead className="bg-slate-50 text-xs font-semibold text-slate-600">
                    <tr><th className="px-4 py-2.5">Bon</th><th className="px-3 py-2.5">Reçu le</th><th className="px-3 py-2.5 text-right">Montant</th><th className="px-3 py-2.5 text-right">Réglé</th><th className="px-3 py-2.5 text-right">Reste</th><th className="px-3 py-2.5">Statut</th><th className="px-4 py-2.5 text-right"><span className="sr-only">Actions</span></th></tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {account.dettes.length === 0 && (
                      <tr><td colSpan={7} className="h-20 text-center text-xs text-slate-400">Aucune dette. Elle apparaît à la réception d’un bon d’approvisionnement.</td></tr>
                    )}
                    {account.dettes.map((debt) => (
                      <Fragment key={debt.id}>
                        <tr className="hover:bg-slate-50/70">
                          <td className="px-4 py-2.5">
                            <button className="flex items-center gap-1.5 text-xs font-bold text-slate-800" onClick={() => toggle(debt.id)} aria-expanded={expanded.has(debt.id)}>
                              {expanded.has(debt.id) ? <ChevronDown size={13} /> : <ChevronRight size={13} />}{debt.numeroBon}
                            </button>
                          </td>
                          <td className="px-3 py-2.5 text-xs text-slate-600">{day(debt.dateCreation)}</td>
                          <td className="px-3 py-2.5 text-right text-xs text-slate-600">{money.format(debt.montantInitial)}</td>
                          <td className="px-3 py-2.5 text-right text-xs text-slate-600">{money.format(debt.montantRegle)}</td>
                          <td className="px-3 py-2.5 text-right text-xs font-bold text-slate-900">{money.format(debt.solde)}</td>
                          <td className="px-3 py-2.5"><span className={`rounded-full px-2.5 py-1 text-xs font-bold ${statusLabel[debt.statut].className}`}>{statusLabel[debt.statut].label}</span></td>
                          <td className="px-4 py-2.5 text-right">
                            {(debt.statut === "EN_COURS" || debt.statut === "PARTIELLEMENT_REGLEE") && (
                              <button className="button-secondary h-8 px-3 text-xs" onClick={() => startPayment(debt)}><Banknote size={13} /> Payer</button>
                            )}
                          </td>
                        </tr>
                        {expanded.has(debt.id) && (
                          <tr className="bg-slate-50/60">
                            <td colSpan={7} className="px-4 py-3">
                              {debt.reglements.length === 0 ? (
                                <p className="text-xs text-slate-400">Aucun règlement pour ce bon.</p>
                              ) : (
                                <ul className="space-y-1">
                                  {debt.reglements.map((item) => (
                                    <li key={item.id} className="flex flex-wrap items-center gap-x-3 text-xs text-slate-600">
                                      <span className="w-28 text-slate-400">{dayTime(item.dateReglement)}</span>
                                      <span className="w-24 text-right font-semibold text-slate-800">{money.format(item.montant)}</span>
                                      <span>{item.source === "AVOIR" ? "Avoir imputé" : modeLabel(item.modeReglement)}</span>
                                      {item.reference && <span className="text-slate-400">réf. {item.reference}</span>}
                                      {item.effectuePar && <span className="text-slate-400">par {item.effectuePar}</span>}
                                    </li>
                                  ))}
                                </ul>
                              )}
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {paying && (
              <form onSubmit={(event) => void submitPayment(event)} className="space-y-3 rounded-xl border border-emerald-200 bg-emerald-50/40 p-4">
                <p className="text-xs font-bold text-slate-900">Payer le bon {paying.numeroBon} <span className="font-normal text-slate-500">· reste {money.format(paying.solde)}</span></p>
                <div className="grid gap-3 sm:grid-cols-3">
                  <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Montant (XOF)</span>
                    <input required autoFocus type="number" min="0.0001" max={paying.solde} step="any" className="input-field" value={payment.montant} onChange={(event) => setPayment({ ...payment, montant: event.target.value })} />
                  </label>
                  <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Mode de règlement</span>
                    <select className="input-field" value={payment.mode} onChange={(event) => setPayment({ ...payment, mode: event.target.value })}>
                      {modes.map((mode) => <option key={mode.value} value={mode.value}>{mode.label}</option>)}
                    </select>
                  </label>
                  <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Référence (facultatif)</span>
                    <input maxLength={80} className="input-field" value={payment.reference} onChange={(event) => setPayment({ ...payment, reference: event.target.value })} placeholder="N° de chèque, de virement…" />
                  </label>
                </div>
                {payment.mode === "ESPECES" && (
                  <p className="text-xs text-slate-600">L’argent sort de la caisse : une session de caisse ouverte avec assez d’espèces est nécessaire.</p>
                )}
                <div className="flex justify-end gap-2">
                  <button type="button" disabled={busy} className="button-secondary" onClick={() => setPaying(null)}>Annuler</button>
                  <button disabled={busy} className="button-primary">{busy ? <LoaderCircle size={14} className="animate-spin" /> : <Banknote size={14} />} Enregistrer le paiement</button>
                </div>
              </form>
            )}

            <div>
              <div className="mb-2 flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-900">Avoirs du fournisseur</h3>
                {canCreateCredit && !creditOpen && (
                  <button className="button-secondary h-8 px-3 text-xs" onClick={() => { setCreditOpen(true); setPaying(null); }}><Plus size={13} /> Saisir un avoir</button>
                )}
              </div>
              {creditOpen && (
                <form onSubmit={(event) => void submitCredit(event)} className="mb-3 space-y-3 rounded-xl border border-slate-200 p-4">
                  <div className="grid gap-3 sm:grid-cols-3">
                    <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Montant (XOF)</span>
                      <input required autoFocus type="number" min="0.0001" step="any" className="input-field" value={credit.montant} onChange={(event) => setCredit({ ...credit, montant: event.target.value })} />
                    </label>
                    <label className="block sm:col-span-2"><span className="mb-1.5 block text-xs font-bold text-slate-600">Motif</span>
                      <input required maxLength={300} className="input-field" value={credit.motif} onChange={(event) => setCredit({ ...credit, motif: event.target.value })} placeholder="Ex. : ristourne de fin d’année" />
                    </label>
                  </div>
                  <p className="text-xs text-slate-500">L’avoir efface aussitôt les dettes les plus anciennes ; ce qui dépasse reste en crédit pour les prochains bons.</p>
                  <div className="flex justify-end gap-2">
                    <button type="button" disabled={busy} className="button-secondary" onClick={() => setCreditOpen(false)}>Annuler</button>
                    <button disabled={busy} className="button-primary">{busy ? <LoaderCircle size={14} className="animate-spin" /> : <Plus size={14} />} Enregistrer l’avoir</button>
                  </div>
                </form>
              )}
              <div className="overflow-x-auto rounded-xl border border-slate-200">
                <table className="w-full min-w-[560px] text-left">
                  <thead className="bg-slate-50 text-xs font-semibold text-slate-600">
                    <tr><th className="px-4 py-2.5">Date</th><th className="px-3 py-2.5">Motif</th><th className="px-3 py-2.5 text-right">Montant</th><th className="px-3 py-2.5 text-right">Utilisé</th><th className="px-4 py-2.5 text-right">Disponible</th></tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {account.avoirs.length === 0 && <tr><td colSpan={5} className="h-16 text-center text-xs text-slate-400">Aucun avoir.</td></tr>}
                    {account.avoirs.map((item) => (
                      <tr key={item.id}>
                        <td className="px-4 py-2.5 text-xs text-slate-600">{dayTime(item.dateCreation)}</td>
                        <td className="px-3 py-2.5 text-xs text-slate-700">{item.motif}<span className="ml-2 text-xs text-slate-400">par {item.creePar}</span></td>
                        <td className="px-3 py-2.5 text-right text-xs text-slate-600">{money.format(item.montant)}</td>
                        <td className="px-3 py-2.5 text-right text-xs text-slate-600">{money.format(item.montantUtilise)}</td>
                        <td className="px-4 py-2.5 text-right text-xs font-bold text-slate-900">{money.format(item.restant)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
