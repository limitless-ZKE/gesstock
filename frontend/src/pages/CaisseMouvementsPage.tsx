import { useEffect, useMemo, useState, type FormEvent } from "react";
import axios from "axios";
import { ArrowDownRight, CircleAlert, LoaderCircle, Plus, X } from "lucide-react";
import { api } from "../services/api";
import { apiErrorMessage as messageFor } from "../services/apiError";
import { money } from "../lib/format";
import Indicateur from "../components/Indicateur";
import JournalCaissePeriode from "../components/JournalCaissePeriode";

type Session = {
  id: number;
  code: string;
  statut: "OUVERTE" | "FERMEE";
  fondDeCaisse: number;
  soldeTheorique: number;
  encaissementsEspeces: number;
  reglementsClientsEspeces: number;
  sortiesCaisse: number;
};
type CashMovement = {
  id: number;
  sessionId: number;
  type: "SORTIE";
  motif: string;
  beneficiaire: string;
  montant: number;
  dateMouvement: string;
  effectuePar: string;
};


export default function CaisseMouvementsPage() {
  const [session, setSession] = useState<Session | null>(null);
  const [movements, setMovements] = useState<CashMovement[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [modalOpen, setModalOpen] = useState(false);
  const [motif, setMotif] = useState("");
  const [beneficiaire, setBeneficiaire] = useState("");
  const [montant, setMontant] = useState("");
  const [onglet, setOnglet] = useState<"session" | "journal">("session");

  async function loadData() {
    setLoading(true);
    setError("");
    try {
      const { data: opened } = await api.get<Session>("/referentiels/sessions-caisse/ouverte");
      setSession(opened);
      const { data } = await api.get<CashMovement[]>("/caisse/mouvements", { params: { sessionId: opened.id } });
      setMovements(data);
    } catch (reason) {
      if (axios.isAxiosError(reason) && reason.response?.status === 404) {
        setSession(null);
        setMovements([]);
        setError("Aucune session de caisse ouverte aujourd’hui. Ouvrez la caisse pour enregistrer des sorties.");
      } else {
        setError(messageFor(reason, "Impossible de charger les mouvements de caisse."));
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadData(); }, []);
  const totalOut = useMemo(() => movements.reduce((sum, movement) => sum + movement.montant, 0), [movements]);

  async function registerMovement(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    if (!motif.trim() || !beneficiaire.trim() || !(Number(montant) > 0)) {
      setError("Indiquez le motif, le bénéficiaire et un montant supérieur à zéro.");
      return;
    }
    if (Number(montant) > session.soldeTheorique) {
      setError(`Montant supérieur au solde disponible (${money.format(session.soldeTheorique)}).`);
      return;
    }
    setSaving(true);
    setError("");
    try {
      await api.post("/caisse/mouvements", {
        motif: motif.trim(),
        beneficiaire: beneficiaire.trim(),
        montant: Number(montant),
      });
      setNotice("Sortie de caisse enregistrée. Le solde théorique a été recalculé.");
      setModalOpen(false);
      setMotif("");
      setBeneficiaire("");
      setMontant("");
      await loadData();
    } catch (reason) {
      setError(messageFor(reason, "L’enregistrement de la sortie a échoué."));
    } finally {
      setSaving(false);
    }
  }

  return <div className="space-y-6">
    <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><h1 className="text-2xl font-bold text-slate-900">Mouvements de caisse</h1><p className="mt-1.5 text-sm text-slate-500">{onglet === "session" ? "Suivez les dépenses et sorties pendant la session ouverte." : "Relisez et imprimez le journal de caisse d’une journée ou d’une période."}</p></div>{onglet === "session" && <button disabled={!session} onClick={() => setModalOpen(true)} className="button-primary"><Plus size={16} />Enregistrer une sortie</button>}</header>
    <div role="tablist" aria-label="Affichage" className="flex w-fit gap-1 rounded-xl bg-slate-100 p-1">
      <Onglet actif={onglet === "session"} onClick={() => setOnglet("session")}>Session en cours</Onglet>
      <Onglet actif={onglet === "journal"} onClick={() => setOnglet("journal")}>Journal de caisse</Onglet>
    </div>
    {onglet === "journal" ? <JournalCaissePeriode /> : <>
    {notice && <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-xs font-semibold text-emerald-800">{notice}<button onClick={() => setNotice("")} className="float-right"><X size={14} /></button></div>}
    {error && <div role="alert" className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-900"><CircleAlert size={15} className="mt-0.5 shrink-0" /><span>{error}</span><button onClick={() => void loadData()} className="ml-auto underline">Actualiser</button></div>}
    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <Kpi label="Fond d’ouverture" value={session ? money.format(session.fondDeCaisse) : "—"} />
      <Kpi label="Ventes en espèces" value={session ? money.format(session.encaissementsEspeces) : "—"} />
      <Kpi label="Règlements clients en espèces" value={session ? money.format(session.reglementsClientsEspeces) : "—"} />
      <Kpi label="Solde théorique disponible" value={session ? money.format(session.soldeTheorique) : "—"} emphasized />
    </section>
    {session && <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs text-slate-600"><span className="font-bold text-slate-800">{session.code}</span><span className="mx-2 text-slate-300">·</span>{movements.length} sortie(s)<span className="mx-2 text-slate-300">·</span>Total décaissé : <strong className="text-rose-700">{money.format(totalOut)}</strong></div>}
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"><div className="border-b border-slate-100 p-5"><h2 className="text-sm font-bold text-slate-900">Historique des sorties</h2><p className="mt-1 text-xs text-slate-500">Les sorties validées sont automatiquement retranchées du solde théorique de clôture.</p></div><div className="overflow-x-auto"><table className="w-full min-w-[700px] text-left"><thead className="bg-slate-50 text-xs font-semibold text-slate-600"><tr><th className="px-5 py-3">Date / heure</th><th className="px-4 py-3">Motif</th><th className="px-4 py-3">Bénéficiaire</th><th className="px-4 py-3">Enregistré par</th><th className="px-5 py-3 text-right">Montant</th></tr></thead><tbody className="divide-y divide-slate-100">{loading ? <tr><td colSpan={5} className="h-36 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={15} />Chargement…</td></tr> : movements.length ? movements.map((movement) => <tr key={movement.id} className="hover:bg-slate-50/70"><td className="whitespace-nowrap px-5 py-3.5 text-xs text-slate-600">{new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeStyle: "short" }).format(new Date(movement.dateMouvement))}</td><td className="px-4 py-3.5 text-xs font-semibold text-slate-700">{movement.motif}</td><td className="px-4 py-3.5 text-xs text-slate-600">{movement.beneficiaire}</td><td className="px-4 py-3.5 text-xs text-slate-500">{movement.effectuePar}</td><td className="px-5 py-3.5 text-right text-xs font-extrabold text-rose-700">−{money.format(movement.montant)}</td></tr>) : <tr><td colSpan={5} className="h-36 text-center text-xs text-slate-400">Aucune sortie enregistrée sur la session.</td></tr>}</tbody></table></div></section>
    {modalOpen && <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setModalOpen(false); }}><section role="dialog" aria-modal="true" aria-label="Nouvelle sortie de caisse" className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl"><div className="mb-5 flex items-center justify-between"><div><p className="text-xs font-semibold text-rose-600">Décaissement</p><h2 className="mt-1 text-lg font-bold text-slate-900">Nouvelle sortie de caisse</h2></div><button onClick={() => setModalOpen(false)} className="icon-button"><X size={16} /></button></div><p className="mb-4 rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-500">Disponible actuellement : <strong className="text-slate-800">{money.format(session?.soldeTheorique ?? 0)}</strong></p><form onSubmit={(event) => void registerMovement(event)} className="space-y-4"><label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Motif de la dépense</span><input required maxLength={500} autoFocus className="input-field" value={motif} onChange={(event) => setMotif(event.target.value)} placeholder="Ex. achat de fournitures, transport…" /></label><div className="grid gap-3 sm:grid-cols-2"><label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Bénéficiaire</span><input required maxLength={120} className="input-field" value={beneficiaire} onChange={(event) => setBeneficiaire(event.target.value)} /></label><label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Montant (XOF)</span><input required min="0.0001" max={session?.soldeTheorique ?? 0} step="any" type="number" className="input-field" value={montant} onChange={(event) => setMontant(event.target.value)} /></label></div><div className="flex justify-end gap-2 border-t border-slate-100 pt-4"><button type="button" disabled={saving} onClick={() => setModalOpen(false)} className="button-secondary">Annuler</button><button disabled={saving} className="button-primary bg-rose-600 hover:bg-rose-700">{saving ? <LoaderCircle size={14} className="animate-spin" /> : <ArrowDownRight size={14} />}{saving ? "Enregistrement…" : "Confirmer la sortie"}</button></div></form></section></div>}
  </>}
  </div>;
}

function Onglet({ actif, onClick, children }: { actif: boolean; onClick: () => void; children: string }) {
  return <button role="tab" aria-selected={actif} onClick={onClick} className={`rounded-lg px-3.5 py-2 text-sm font-semibold transition ${actif ? "bg-white text-emerald-800 shadow-sm" : "text-slate-600 hover:text-slate-900"}`}>{children}</button>;
}

function Kpi({ label, value, emphasized = false }: { label: string; value: string; emphasized?: boolean }) {
  return <Indicateur libelle={label} valeur={value} accent={emphasized} />;
}
