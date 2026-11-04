import { useEffect, useMemo, useState, type FormEvent } from "react";
import axios from "axios";
import { ArrowDownRight, ArrowUpRight, CircleAlert, LoaderCircle, Plus, X } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";
import { apiErrorMessage as messageFor } from "../services/apiError";
import { money } from "../lib/format";
import Indicateur from "../components/Indicateur";
import JournalCaisse from "../components/JournalCaisse";

type Session = {
  id: number;
  code: string;
  statut: "OUVERTE" | "FERMEE";
  fondDeCaisse: number;
  soldeTheorique: number;
  encaissementsEspeces: number;
  reglementsClientsEspeces: number;
  entreesDeFonds: number;
  sortiesCaisse: number;
};
type CashMovement = {
  id: number;
  sessionId: number;
  type: "SORTIE" | "ENTREE";
  /** MANUEL, REGLEMENT_FOURNISSEUR (bon payé en espèces) ou REMBOURSEMENT_CLIENT (vente annulée). */
  origine: "MANUEL" | "REGLEMENT_FOURNISSEUR" | "REMBOURSEMENT_CLIENT";
  motif: string;
  beneficiaire: string;
  montant: number;
  dateMouvement: string;
  effectuePar: string;
};
type Sens = "sortie" | "entree";

const libelleOrigine: Record<CashMovement["origine"], string> = {
  MANUEL: "",
  REGLEMENT_FOURNISSEUR: "Paiement fournisseur",
  REMBOURSEMENT_CLIENT: "Remboursement client",
};

export default function CaisseMouvementsPage() {
  const { user } = useAuth();
  // Un apport de fonds augmente les espèces attendues en caisse : seuls les responsables peuvent l'enregistrer.
  const estResponsable = (user?.roles ?? []).some((role) => ["ADMIN", "MANAGER"].includes(role.toUpperCase().replace(/^ROLE_/, "")));
  const [session, setSession] = useState<Session | null>(null);
  const [movements, setMovements] = useState<CashMovement[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [sens, setSens] = useState<Sens | null>(null);
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
        setError("Aucune session de caisse ouverte aujourd’hui. Ouvrez la caisse pour enregistrer des mouvements.");
      } else {
        setError(messageFor(reason, "Impossible de charger les mouvements de caisse."));
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadData(); }, []);
  const totalOut = useMemo(() => movements.filter((movement) => movement.type === "SORTIE").reduce((sum, movement) => sum + movement.montant, 0), [movements]);
  const totalIn = useMemo(() => movements.filter((movement) => movement.type === "ENTREE").reduce((sum, movement) => sum + movement.montant, 0), [movements]);

  function ouvrir(nouveauSens: Sens) {
    setError("");
    setSens(nouveauSens);
  }

  async function registerMovement(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !sens) return;
    if (!motif.trim() || !beneficiaire.trim() || !(Number(montant) > 0)) {
      setError(sens === "sortie"
        ? "Indiquez le motif, le bénéficiaire et un montant supérieur à zéro."
        : "Indiquez le motif, la provenance des fonds et un montant supérieur à zéro.");
      return;
    }
    if (sens === "sortie" && Number(montant) > session.soldeTheorique) {
      setError(`Montant supérieur au solde disponible (${money.format(session.soldeTheorique)}).`);
      return;
    }
    setSaving(true);
    setError("");
    try {
      await api.post(sens === "sortie" ? "/caisse/mouvements" : "/caisse/mouvements/entrees", {
        motif: motif.trim(),
        beneficiaire: beneficiaire.trim(),
        montant: Number(montant),
      });
      setNotice(sens === "sortie"
        ? "Sortie de caisse enregistrée. Le solde théorique a été recalculé."
        : "Entrée de fonds enregistrée. Le solde théorique a été recalculé.");
      setSens(null);
      setMotif("");
      setBeneficiaire("");
      setMontant("");
      await loadData();
    } catch (reason) {
      setError(messageFor(reason, sens === "sortie" ? "L’enregistrement de la sortie a échoué." : "L’enregistrement de l’entrée de fonds a échoué."));
    } finally {
      setSaving(false);
    }
  }

  return <div className="space-y-6">
    <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Mouvements de caisse</h1>
        <p className="mt-1.5 text-sm text-slate-500">{onglet === "session" ? "Suivez les entrées et les sorties d’argent pendant la session ouverte." : "Relisez et imprimez le journal de caisse d’une journée ou d’une période."}</p>
      </div>
      {onglet === "session" && (
        <div className="flex flex-wrap gap-2">
          {estResponsable && <button disabled={!session} onClick={() => ouvrir("entree")} className="button-secondary"><ArrowUpRight size={16} />Enregistrer une entrée de fonds</button>}
          <button disabled={!session} onClick={() => ouvrir("sortie")} className="button-primary"><Plus size={16} />Enregistrer une sortie</button>
        </div>
      )}
    </header>
    <div role="tablist" aria-label="Affichage" className="flex w-fit gap-1 rounded-xl bg-slate-100 p-1">
      <Onglet actif={onglet === "session"} onClick={() => setOnglet("session")}>Session en cours</Onglet>
      <Onglet actif={onglet === "journal"} onClick={() => setOnglet("journal")}>Journal de caisse</Onglet>
    </div>
    {onglet === "journal" ? <JournalCaisse /> : <>
      {notice && <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-xs font-semibold text-emerald-800">{notice}<button onClick={() => setNotice("")} className="float-right"><X size={14} /></button></div>}
      {error && !sens && <div role="alert" className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-900"><CircleAlert size={15} className="mt-0.5 shrink-0" /><span>{error}</span><button onClick={() => void loadData()} className="ml-auto underline">Actualiser</button></div>}
      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <Kpi label="Fond d’ouverture" value={session ? money.format(session.fondDeCaisse) : "—"} />
        <Kpi label="Ventes en espèces" value={session ? money.format(session.encaissementsEspeces) : "—"} />
        <Kpi label="Règlements clients en espèces" value={session ? money.format(session.reglementsClientsEspeces) : "—"} />
        <Kpi label="Entrées de fonds" value={session ? money.format(session.entreesDeFonds ?? 0) : "—"} />
        <Kpi label="Sorties de caisse" value={session ? money.format(session.sortiesCaisse ?? 0) : "—"} />
        <Kpi label="Solde théorique disponible" value={session ? money.format(session.soldeTheorique) : "—"} emphasized />
      </section>
      {session && <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-xs text-slate-600"><span className="font-bold text-slate-800">{session.code}</span><span className="mx-2 text-slate-300">·</span>{movements.length} mouvement(s)<span className="mx-2 text-slate-300">·</span>Total entré : <strong className="text-emerald-700">{money.format(totalIn)}</strong><span className="mx-2 text-slate-300">·</span>Total décaissé : <strong className="text-rose-700">{money.format(totalOut)}</strong></div>}
      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-100 p-5">
          <h2 className="text-sm font-bold text-slate-900">Historique des mouvements</h2>
          <p className="mt-1 text-xs text-slate-500">Les entrées de fonds s’ajoutent au solde théorique, les sorties s’en retranchent. Les entrées ne sont jamais du chiffre d’affaires ; les sorties saisies à la main sont des dépenses dans les statistiques.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left">
            <thead className="bg-slate-50 text-xs font-semibold text-slate-600"><tr><th className="px-5 py-3">Date / heure</th><th className="px-4 py-3">Mouvement</th><th className="px-4 py-3">Motif</th><th className="px-4 py-3">Bénéficiaire / provenance</th><th className="px-4 py-3">Enregistré par</th><th className="px-5 py-3 text-right">Montant</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? <tr><td colSpan={6} className="h-36 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={15} />Chargement…</td></tr>
                : movements.length ? movements.map((movement) => (
                  <tr key={movement.id} className="hover:bg-slate-50/70">
                    <td className="whitespace-nowrap px-5 py-3.5 text-xs text-slate-600">{new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeStyle: "short" }).format(new Date(movement.dateMouvement))}</td>
                    <td className="px-4 py-3.5 text-xs"><span className={`inline-flex rounded-full px-2.5 py-1 font-bold ${movement.type === "ENTREE" ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700"}`}>{movement.type === "ENTREE" ? "Entrée de fonds" : "Sortie"}</span>{libelleOrigine[movement.origine] && <span className="ml-1.5 text-slate-400">{libelleOrigine[movement.origine]}</span>}</td>
                    <td className="px-4 py-3.5 text-xs font-semibold text-slate-700">{movement.motif}</td>
                    <td className="px-4 py-3.5 text-xs text-slate-600">{movement.beneficiaire}</td>
                    <td className="px-4 py-3.5 text-xs text-slate-500">{movement.effectuePar}</td>
                    <td className={`px-5 py-3.5 text-right text-xs font-extrabold ${movement.type === "ENTREE" ? "text-emerald-700" : "text-rose-700"}`}>{movement.type === "ENTREE" ? "+" : "−"}{money.format(movement.montant)}</td>
                  </tr>
                )) : <tr><td colSpan={6} className="h-36 text-center text-xs text-slate-400">Aucun mouvement enregistré sur la session.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
      {sens && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setSens(null); }}>
          <section role="dialog" aria-modal="true" aria-label={sens === "sortie" ? "Nouvelle sortie de caisse" : "Nouvelle entrée de fonds"} className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl">
            <div className="mb-5 flex items-center justify-between">
              <div>
                <p className={`text-xs font-semibold ${sens === "sortie" ? "text-rose-600" : "text-emerald-700"}`}>{sens === "sortie" ? "Décaissement" : "Apport d’argent"}</p>
                <h2 className="mt-1 text-lg font-bold text-slate-900">{sens === "sortie" ? "Nouvelle sortie de caisse" : "Nouvelle entrée de fonds"}</h2>
              </div>
              <button onClick={() => setSens(null)} className="icon-button" aria-label="Fermer"><X size={16} /></button>
            </div>
            <p className="mb-4 rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-500">
              {sens === "sortie"
                ? <>Disponible actuellement : <strong className="text-slate-800">{money.format(session?.soldeTheorique ?? 0)}</strong></>
                : <>L’argent ajouté augmente les espèces attendues en caisse. Ce n’est pas du chiffre d’affaires. Solde actuel : <strong className="text-slate-800">{money.format(session?.soldeTheorique ?? 0)}</strong></>}
            </p>
            {error && <div role="alert" className="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-800">{error}</div>}
            <form onSubmit={(event) => void registerMovement(event)} className="space-y-4">
              <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">{sens === "sortie" ? "Motif de la dépense" : "Motif de l’entrée"}</span><input required maxLength={500} autoFocus className="input-field" value={motif} onChange={(event) => setMotif(event.target.value)} placeholder={sens === "sortie" ? "Ex. achat de fournitures, transport…" : "Ex. apport du patron pour payer un fournisseur…"} /></label>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">{sens === "sortie" ? "Bénéficiaire" : "Provenance (de la part de)"}</span><input required maxLength={120} className="input-field" value={beneficiaire} onChange={(event) => setBeneficiaire(event.target.value)} /></label>
                <label className="block"><span className="mb-1.5 block text-xs font-bold text-slate-600">Montant (XOF)</span><input required min="0.0001" max={sens === "sortie" ? session?.soldeTheorique ?? 0 : undefined} step="any" type="number" className="input-field" value={montant} onChange={(event) => setMontant(event.target.value)} /></label>
              </div>
              <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
                <button type="button" disabled={saving} onClick={() => setSens(null)} className="button-secondary">Annuler</button>
                <button disabled={saving} className={`button-primary ${sens === "sortie" ? "bg-rose-600 hover:bg-rose-700" : ""}`}>
                  {saving ? <LoaderCircle size={14} className="animate-spin" /> : sens === "sortie" ? <ArrowDownRight size={14} /> : <ArrowUpRight size={14} />}
                  {saving ? "Enregistrement…" : sens === "sortie" ? "Confirmer la sortie" : "Confirmer l’entrée"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </>}
  </div>;
}

function Onglet({ actif, onClick, children }: { actif: boolean; onClick: () => void; children: string }) {
  return <button role="tab" aria-selected={actif} onClick={onClick} className={`rounded-lg px-3.5 py-2 text-sm font-semibold transition ${actif ? "bg-white text-emerald-800 shadow-sm" : "text-slate-600 hover:text-slate-900"}`}>{children}</button>;
}

function Kpi({ label, value, emphasized = false }: { label: string; value: string; emphasized?: boolean }) {
  return <Indicateur libelle={label} valeur={value} accent={emphasized} />;
}
