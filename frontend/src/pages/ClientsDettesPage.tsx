import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import {
  Banknote,
  Check,
  CheckCircle2,
  ChevronDown,
  CircleDollarSign,
  CirclePlus,
  CreditCard,
  FileText,
  History,
  LoaderCircle,
  Mail,
  MapPin,
  Pencil,
  Plus,
  Printer,
  Search,
  Smartphone,
  Trash2,
  UserRound,
  UsersRound,
  WalletCards,
  X,
} from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";
import { apiErrorMessage as errorMessage, parseApiError } from "../services/apiError";
import ApprobationModal, { type Approbation } from "../components/ApprobationModal";
import EnTeteDocument from "../components/EnTeteDocument";
import { imprimer } from "../lib/impression";
import { money as currency } from "../lib/format";

type Client = { id: number; nom: string; email: string | null; telephone: string | null; adresse: string | null; nombreAchats: number };
type Article = { id: number; reference: string; designation: string; prixVente: number; quantiteStock: number };
type SaleLine = {
  articleId: number;
  reference: string;
  designation: string;
  quantite: number;
  prixUnitaire: number;
  montantLigne: number;
  remiseUnitaire: number | null;
};
type Sale = {
  id: number;
  code: string;
  dateVente: string;
  statut: string;
  clientId: number | null;
  client: string | null;
  aCredit: boolean;
  montantTotal: number;
  lignes: SaleLine[];
};
type ProformaLine = {
  articleId: number;
  reference: string;
  designation: string;
  quantite: number;
  prixUnitaire: number;
  montantLigne: number;
  remiseUnitaire: number | null;
};
type Proforma = {
  id: number;
  numero: string;
  dateEmission: string;
  dateExpiration: string | null;
  statut: "BROUILLON" | "ENVOYEE" | "ACCEPTEE" | "REFUSEE" | "EXPIREE" | "CONVERTIE";
  clientId: number | null;
  client: string | null;
  montantTotal: number;
  lignes: ProformaLine[];
  venteId: number | null;
};
type Debt = {
  id: number;
  clientId: number;
  client: string;
  dateCreation: string;
  venteId: number;
  numeroVente: string;
  montantInitial: number;
  montantRegle: number;
  solde: number;
  statut: "EN_COURS" | "PARTIELLEMENT_REGLEE" | "REGLEE" | "ANNULEE";
};
type PaymentMode = "ESPECES" | "MOBILE_MONEY" | "CARTE" | "VIREMENT" | "CHEQUE" | "AUTRE";
type Payment = {
  id: number;
  detteId: number;
  clientId: number;
  montant: number;
  modeReglement: PaymentMode;
  reference: string | null;
  dateReglement: string;
  recuPar: string | null;
};
type DraftLine = { articleId: string; quantite: string; prixUnitaire: string; remise: string };
type ClientForm = { nom: string; email: string; telephone: string; adresse: string };

const today = new Date().toISOString().slice(0, 10);
const blankClient: ClientForm = { nom: "", email: "", telephone: "", adresse: "" };
/** Arrondi à deux décimales (remises en %, prix négociés). */
const round2 = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100;
const blankLine = (): DraftLine => ({ articleId: "", quantite: "1", prixUnitaire: "", remise: "0" });
const paymentModes: { value: PaymentMode; label: string; icon: typeof Banknote }[] = [
  { value: "ESPECES", label: "Espèces", icon: Banknote },
  { value: "MOBILE_MONEY", label: "Mobile Money", icon: Smartphone },
  { value: "CARTE", label: "Carte", icon: CreditCard },
  { value: "VIREMENT", label: "Virement", icon: WalletCards },
  { value: "CHEQUE", label: "Chèque", icon: FileText },
  { value: "AUTRE", label: "Autre", icon: CircleDollarSign },
];

function dateLabel(value: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium" }).format(new Date(`${value.slice(0, 10)}T00:00:00`));
}

function dateTimeLabel(value: string) {
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function makeProformaNumber() {
  return `PRO-${new Date().toISOString().replace(/[-:T.Z]/g, "").slice(0, 14)}`;
}

export default function ClientsDettesPage() {
  const { user } = useAuth();
  // Annuler une vente (stock, dette, remboursement) est réservé aux responsables : voir SecurityConfig.
  const canCancelSales = (user?.roles ?? []).some((role) => ["ADMIN", "MANAGER"].includes(role.toUpperCase().replace(/^ROLE_/, "")));
  const [cancellingSaleId, setCancellingSaleId] = useState<number | null>(null);
  const [clients, setClients] = useState<Client[]>([]);
  const [articles, setArticles] = useState<Article[]>([]);
  // Historique d'achats du client dont la fiche est ouverte : les 20 dernières ventes, chargées à l'ouverture
  const [clientSales, setClientSales] = useState<Sale[]>([]);
  const [proformas, setProformas] = useState<Proforma[]>([]);
  const [debts, setDebts] = useState<Debt[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [notice, setNotice] = useState("");
  const [noticeError, setNoticeError] = useState(false);
  const [tab, setTab] = useState<"clients" | "proformas" | "debts">("clients");
  const [search, setSearch] = useState("");
  const [clientModal, setClientModal] = useState<Client | "new" | null>(null);
  const [clientForm, setClientForm] = useState<ClientForm>(blankClient);
  const [clientSaving, setClientSaving] = useState(false);
  const [clientDetail, setClientDetail] = useState<Client | null>(null);
  const [proformaModal, setProformaModal] = useState<Proforma | "new" | null>(null);
  const [proformaNumber, setProformaNumber] = useState(makeProformaNumber());
  const [proformaClientId, setProformaClientId] = useState("");
  const [emissionDate, setEmissionDate] = useState(today);
  const [expirationDate, setExpirationDate] = useState("");
  const [draftLines, setDraftLines] = useState<DraftLine[]>([blankLine()]);
  const [proformaSaving, setProformaSaving] = useState(false);
  const [approbationDemandee, setApprobationDemandee] = useState<{ message: string; erreur: string } | null>(null);
  const [busyProformaId, setBusyProformaId] = useState<number | null>(null);
  const [convertCreditId, setConvertCreditId] = useState<number | null>(null);
  const [cashProforma, setCashProforma] = useState<Proforma | null>(null);
  const [cashMode, setCashMode] = useState<PaymentMode>("ESPECES");
  const [cashReceived, setCashReceived] = useState("");
  const [paymentDebt, setPaymentDebt] = useState<Debt | null>(null);
  const [paymentAmount, setPaymentAmount] = useState("");
  const [paymentMode, setPaymentMode] = useState<PaymentMode>("ESPECES");
  const [paymentReference, setPaymentReference] = useState("");
  const [paymentSaving, setPaymentSaving] = useState(false);
  const [paymentHistory, setPaymentHistory] = useState<Payment[]>([]);
  const [paymentHistoryLoading, setPaymentHistoryLoading] = useState(false);
  const [receipt, setReceipt] = useState<{ payment: Payment; debt: Debt } | null>(null);

  async function loadData() {
    setLoading(true);
    setLoadError("");
    try {
      const [clientResult, articleResult, proformaResult, debtResult] = await Promise.all([
        api.get<Client[]>("/clients"),
        api.get<Article[]>("/articles"),
        api.get<Proforma[]>("/proformas"),
        api.get<Debt[]>("/dettes-clients"),
      ]);
      setClients(clientResult.data);
      setArticles(articleResult.data);
      setProformas(proformaResult.data);
      setDebts(debtResult.data);
    } catch (error) {
      setLoadError(errorMessage(error, "Impossible de charger les données clients et financières."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadData(); }, []);

  async function loadClientSales(clientId: number) {
    try {
      const { data } = await api.get<Sale[]>("/ventes", { params: { clientId, size: 20 } });
      setClientSales(data.filter((sale) => sale.statut === "VALIDEE"));
    } catch (error) {
      showNotice(errorMessage(error, "L’historique d’achats du client est indisponible."), true);
    }
  }

  function openClientDetail(client: Client) {
    setClientDetail(client);
    setClientSales([]);
    void loadClientSales(client.id);
  }

  const totalDue = useMemo(() => debts.reduce((sum, debt) => sum + Math.max(0, debt.solde), 0), [debts]);
  const unpaidDebtCount = debts.filter((debt) => debt.solde > 0 && debt.statut !== "ANNULEE").length;
  const filteredClients = clients.filter((client) => {
    const query = search.trim().toLocaleLowerCase("fr");
    return !query || [client.nom, client.email, client.telephone, client.adresse]
      .some((value) => value?.toLocaleLowerCase("fr").includes(query));
  });
  const filteredProformas = proformas.filter((proforma) => {
    const query = search.trim().toLocaleLowerCase("fr");
    return !query || proforma.numero.toLocaleLowerCase("fr").includes(query)
      || proforma.client?.toLocaleLowerCase("fr").includes(query);
  });
  const filteredDebts = debts.filter((debt) => {
    const query = search.trim().toLocaleLowerCase("fr");
    return debt.solde > 0 && debt.statut !== "ANNULEE"
      && (!query || debt.client.toLocaleLowerCase("fr").includes(query)
        || debt.numeroVente.toLocaleLowerCase("fr").includes(query));
  });
  const proformaTotal = useMemo(() => draftLines.reduce((sum, line) =>
    sum + (Number(line.quantite) || 0) * (Number(line.prixUnitaire) || 0), 0), [draftLines]);

  function showNotice(message: string, isError = false) {
    setNotice(message);
    setNoticeError(isError);
  }

  function openClientForm(client: Client | "new") {
    setClientForm(client === "new" ? blankClient : {
      nom: client.nom,
      email: client.email ?? "",
      telephone: client.telephone ?? "",
      adresse: client.adresse ?? "",
    });
    setClientModal(client);
  }

  async function saveClient(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setClientSaving(true);
    try {
      if (clientModal === "new") await api.post("/clients", clientForm);
      else if (clientModal) await api.put(`/clients/${clientModal.id}`, clientForm);
      setClientModal(null);
      showNotice(clientModal === "new" ? "Le client a été créé." : "Les informations du client ont été modifiées.");
      await loadData();
    } catch (error) {
      showNotice(errorMessage(error, "L’enregistrement du client a échoué."), true);
    } finally {
      setClientSaving(false);
    }
  }

  async function deleteClient(client: Client) {
    if (!window.confirm(`Supprimer le client « ${client.nom} » ?`)) return;
    try {
      await api.delete(`/clients/${client.id}`);
      showNotice("Le client a été supprimé.");
      await loadData();
    } catch (error) {
      showNotice(errorMessage(error, "Ce client ne peut pas être supprimé."), true);
    }
  }

  function openProformaForm(proforma: Proforma | "new") {
    setProformaNumber(proforma === "new" ? makeProformaNumber() : proforma.numero);
    setProformaClientId(proforma === "new" ? "" : String(proforma.clientId ?? ""));
    setEmissionDate(proforma === "new" ? today : proforma.dateEmission);
    setExpirationDate(proforma === "new" ? "" : proforma.dateExpiration ?? "");
    setDraftLines(proforma === "new" ? [blankLine()] : proforma.lignes.map((line) => {
      const article = articles.find((item) => item.id === line.articleId);
      const listPrice = article?.prixVente ?? line.prixUnitaire;
      return {
        articleId: String(line.articleId),
        quantite: String(line.quantite),
        prixUnitaire: String(round2(line.prixUnitaire)),
        remise: listPrice > 0 ? String(round2(Math.max(0, (1 - line.prixUnitaire / listPrice) * 100))) : "0",
      };
    }));
    setProformaModal(proforma);
  }

  function updateLine(index: number, patch: Partial<DraftLine>) {
    setDraftLines((current) => current.map((line, lineIndex) => {
      if (lineIndex !== index) return line;
      const updated = { ...line, ...patch };
      const article = articles.find((item) => String(item.id) === updated.articleId);
      if (patch.articleId && article) {
        updated.prixUnitaire = String(round2(article.prixVente));
        updated.remise = "0";
      } else if (patch.remise !== undefined && article) {
        // La remise et le prix n'ont jamais plus de deux décimales : au-delà, le champ (pas de 0,01) bloquerait l'envoi.
        const discount = round2(Math.max(0, Math.min(100, Number(updated.remise) || 0)));
        updated.remise = String(discount);
        updated.prixUnitaire = String(round2(article.prixVente * (1 - discount / 100)));
      } else if (patch.prixUnitaire !== undefined && article && article.prixVente > 0) {
        updated.remise = String(round2(Math.max(0, (1 - (Number(updated.prixUnitaire) || 0) / article.prixVente) * 100)));
      }
      return updated;
    }));
  }

  async function saveProforma(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await envoyerProforma();
  }

  /** Enregistre le proforma, avec l'accord d'un responsable quand le serveur l'a demandé (remise au-delà du plafond, prix sous le coût). */
  async function envoyerProforma(approbation?: Approbation) {
    const validLines = draftLines.filter((line) => line.articleId && Number(line.quantite) > 0
      && Number(line.prixUnitaire) >= 0 && Number(line.prixUnitaire) <= (articles.find((article) => String(article.id) === line.articleId)?.prixVente ?? Infinity));
    if (!validLines.length) {
      showNotice("Ajoutez au moins un article avec une quantité et un prix valides.", true);
      return;
    }
    setProformaSaving(true);
    try {
      const request = {
        numero: proformaNumber.trim(),
        dateEmission: emissionDate,
        dateExpiration: expirationDate || null,
        clientId: proformaClientId ? Number(proformaClientId) : null,
        lignes: validLines.map((line) => ({
          articleId: Number(line.articleId),
          quantite: Number(line.quantite),
          prixUnitaire: round2(Number(line.prixUnitaire)),
        })),
        approbation,
      };
      if (proformaModal === "new") await api.post("/proformas", request);
      else if (proformaModal) await api.put(`/proformas/${proformaModal.id}`, request);
      setProformaModal(null);
      showNotice(proformaModal === "new" ? "La facture proforma a été créée en brouillon." : "Le brouillon proforma a été modifié.");
      await loadData();
    } catch (error) {
      const probleme = parseApiError(error);
      if (probleme.code === "APPROBATION_REQUISE" || probleme.code === "APPROBATION_REFUSEE") {
        setApprobationDemandee({ message: probleme.message, erreur: probleme.code === "APPROBATION_REFUSEE" ? probleme.message : "" });
        return;
      }
      showNotice(errorMessage(error, "L’enregistrement du proforma a échoué."), true);
    } finally {
      setProformaSaving(false);
    }
  }

  async function updateProformaStatus(proforma: Proforma, status: "ENVOYEE" | "ACCEPTEE" | "REFUSEE") {
    const label = status === "ENVOYEE" ? "marquer comme envoyée" : status === "ACCEPTEE" ? "accepter" : "refuser";
    if (!window.confirm(`Confirmer : ${label} le proforma ${proforma.numero} ?`)) return;
    setBusyProformaId(proforma.id);
    try {
      await api.patch(`/proformas/${proforma.id}/statut`, { statut: status });
      showNotice(`Le proforma ${proforma.numero} est maintenant ${statusLabel(status).toLocaleLowerCase("fr")}.`);
      await loadData();
    } catch (error) {
      showNotice(errorMessage(error, "Le changement de statut a échoué."), true);
    } finally {
      setBusyProformaId(null);
    }
  }

  async function deleteProforma(proforma: Proforma) {
    if (!window.confirm(`Supprimer le proforma ${proforma.numero} ?`)) return;
    setBusyProformaId(proforma.id);
    try {
      await api.delete(`/proformas/${proforma.id}`);
      showNotice("Le proforma a été supprimé.");
      await loadData();
    } catch (error) {
      showNotice(errorMessage(error, "La suppression du proforma a échoué."), true);
    } finally {
      setBusyProformaId(null);
    }
  }

  async function convertProforma(proforma: Proforma, aCredit: boolean) {
    if (!aCredit) {
      // Au comptant, le règlement est obligatoire : sans lui l'argent encaissé serait invisible dans la caisse.
      setCashMode("ESPECES");
      setCashReceived(String(proforma.montantTotal));
      setCashProforma(proforma);
      return;
    }
    if (!window.confirm(`Convertir ${proforma.numero} en vente à crédit ? Le stock sera déduit, la créance client enregistrée, et une journée de caisse ouverte est requise.`)) return;
    await sendConversion(proforma, { aCredit: true });
  }

  async function confirmCashConversion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!cashProforma) return;
    const received = Number(cashReceived);
    if (!Number.isFinite(received) || received < cashProforma.montantTotal) {
      showNotice("Le montant reçu doit couvrir le total du proforma.", true);
      return;
    }
    if (cashMode !== "ESPECES" && received !== cashProforma.montantTotal) {
      showNotice("Un règlement sans espèces doit correspondre exactement au total.", true);
      return;
    }
    const converted = await sendConversion(cashProforma, { aCredit: false, modeReglement: cashMode, montantRecu: received });
    if (converted) setCashProforma(null);
  }

  async function sendConversion(
    proforma: Proforma,
    body: { aCredit: boolean; modeReglement?: PaymentMode; montantRecu?: number },
  ): Promise<boolean> {
    setBusyProformaId(proforma.id);
    try {
      const { data: sale } = await api.post<Sale>(`/proformas/${proforma.id}/conversion`, body);
      showNotice(`Vente ${sale.code} validée. Le stock a été déduit${sale.aCredit ? " et la créance client enregistrée" : ", le règlement est enregistré en caisse"}.`);
      setConvertCreditId(null);
      await loadData();
      return true;
    } catch (error) {
      showNotice(errorMessage(error, "La conversion du proforma a échoué."), true);
      return false;
    } finally {
      setBusyProformaId(null);
    }
  }

  async function cancelCreditSale(sale: Sale) {
    const paid = debts.find((debt) => debt.venteId === sale.id)?.montantRegle ?? 0;
    const reason = window.prompt(
      `Annuler la vente à crédit ${sale.code} ?\nLe stock sera remis en rayon et la dette du client annulée.`
        + (paid > 0 ? `\nLe client a déjà versé ${currency.format(paid)} : il sera remboursé.` : "")
        + "\n\nMotif de l’annulation :",
    )?.trim();
    if (!reason) return;
    // Un client qui a déjà payé n'est remboursé que s'il rapporte les articles en l'état.
    if (paid > 0 && !window.confirm(
      `Le client rapporte-t-il les articles de la vente ${sale.code} en l’état ?\n\n`
        + `OK : vente annulée et ${currency.format(paid)} remboursés (la part en espèces sort de la caisse du jour).\n`
        + "Annuler : rien n’est modifié.",
    )) return;
    setCancellingSaleId(sale.id);
    try {
      const { data } = await api.post<{ montantRembourse: number; remboursementEspeces: number; remboursementAutresModes: number }>(
        `/ventes/${sale.id}/annulation`,
        { motif: reason, articlesRetournesEnEtat: paid > 0 },
      );
      showNotice(
        `La vente ${sale.code} est annulée : stock remis en rayon et dette annulée.`
          + (data.montantRembourse > 0
            ? ` Remboursement de ${currency.format(data.montantRembourse)}`
              + (data.remboursementEspeces > 0 ? ` dont ${currency.format(data.remboursementEspeces)} en espèces sortis de la caisse.` : ".")
            : ""),
      );
      await loadData();
      if (clientDetail) await loadClientSales(clientDetail.id);
    } catch (error) {
      showNotice(errorMessage(error, "L’annulation de la vente a échoué."), true);
    } finally {
      setCancellingSaleId(null);
    }
  }

  async function openPaymentForm(debt: Debt) {
    setPaymentDebt(debt);
    setPaymentAmount(String(debt.solde));
    setPaymentMode("ESPECES");
    setPaymentReference("");
    setPaymentHistory([]);
    setPaymentHistoryLoading(true);
    try {
      const { data } = await api.get<Payment[]>(`/dettes-clients/${debt.id}/reglements`);
      setPaymentHistory(data);
    } catch (error) {
      showNotice(errorMessage(error, "L’historique des règlements n’a pas pu être chargé."), true);
    } finally {
      setPaymentHistoryLoading(false);
    }
  }

  async function savePayment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!paymentDebt) return;
    const amount = Number(paymentAmount);
    if (!Number.isFinite(amount) || amount <= 0 || amount > paymentDebt.solde) {
      showNotice("Le montant doit être positif et ne peut pas dépasser le solde restant.", true);
      return;
    }
    setPaymentSaving(true);
    try {
      const { data } = await api.post<Payment>(`/dettes-clients/${paymentDebt.id}/reglements`, {
        montant: amount,
        modeReglement: paymentMode,
        reference: paymentReference.trim() || null,
      });
      setReceipt({ payment: data, debt: paymentDebt });
      setPaymentDebt(null);
      showNotice("Le règlement client a été enregistré.");
      await loadData();
    } catch (error) {
      showNotice(errorMessage(error, "L’enregistrement du règlement a échoué."), true);
    } finally {
      setPaymentSaving(false);
    }
  }

  const clientDebts = clientDetail ? debts.filter((debt) => debt.clientId === clientDetail.id) : [];

  return <>
    <div className="space-y-6 print:hidden">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div><p className="mb-1 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Espace clients</p><h1 className="text-3xl font-bold tracking-tight text-slate-900">Clients, proformas & dettes</h1><p className="mt-1.5 text-sm text-slate-500">Gérez les comptes clients, les offres commerciales et les règlements.</p></div>
        <button onClick={() => tab === "clients" ? openClientForm("new") : openProformaForm("new")} className="button-primary">{tab === "clients" ? <UserRound size={16} /> : <CirclePlus size={16} />}{tab === "clients" ? "Nouveau client" : "Nouvelle proforma"}</button>
      </header>

      {notice && <div role={noticeError ? "alert" : "status"} className={`flex items-start gap-2 rounded-xl border px-4 py-3 text-xs font-semibold ${noticeError ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}><CheckCircle2 size={15} className="mt-0.5 shrink-0" /><span>{notice}</span><button className="ml-auto" onClick={() => setNotice("")} aria-label="Fermer"><X size={15} /></button></div>}

      <section className="grid gap-3 sm:grid-cols-3">
        <Metric title="Clients enregistrés" count={clients.length} icon={<UsersRound size={18} />} />
        <Metric title="Proformas en cours" count={proformas.filter((item) => item.statut === "BROUILLON" || item.statut === "ENVOYEE" || item.statut === "ACCEPTEE").length} icon={<FileText size={18} />} />
        <div className="flex items-center justify-between rounded-2xl border border-orange-200 bg-orange-50 p-4 shadow-sm"><div><p className="text-xs font-semibold text-orange-700">Total des créances</p><p className="mt-1 text-xl font-extrabold text-orange-900">{currency.format(totalDue)}</p><p className="mt-1 text-[10px] font-medium text-orange-700">{unpaidDebtCount} dette(s) non soldée(s)</p></div><span className="grid size-10 place-items-center rounded-xl bg-white text-orange-600"><CircleDollarSign size={18} /></span></div>
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 md:flex-row md:items-center md:justify-between md:px-5">
          <div className="flex w-fit gap-1 rounded-xl bg-slate-100 p-1">
            <TabButton active={tab === "clients"} onClick={() => { setTab("clients"); setSearch(""); }} icon={<UsersRound size={14} />}>Clients</TabButton>
            <TabButton active={tab === "proformas"} onClick={() => { setTab("proformas"); setSearch(""); }} icon={<FileText size={14} />}>Proformas</TabButton>
            <TabButton active={tab === "debts"} onClick={() => { setTab("debts"); setSearch(""); }} icon={<CircleDollarSign size={14} />}>Dettes & règlements</TabButton>
          </div>
          <label className="relative block w-full md:max-w-xs"><Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" /><input className="input-field pl-9" value={search} onChange={(event) => setSearch(event.target.value)} placeholder={tab === "clients" ? "Rechercher un client…" : tab === "proformas" ? "Rechercher un proforma…" : "Client ou numéro de vente…"} /></label>
        </div>
        {loadError && <div role="alert" className="m-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs font-semibold text-rose-800">{loadError}<button onClick={() => void loadData()} className="ml-2 underline">Réessayer</button></div>}
        <div className="overflow-x-auto">
          {tab === "clients" && <ClientsTable clients={filteredClients} loading={loading} onDetail={openClientDetail} onEdit={openClientForm} onDelete={(client) => void deleteClient(client)} />}
          {tab === "proformas" && <ProformasTable proformas={filteredProformas} loading={loading} busyId={busyProformaId} convertCreditId={convertCreditId} onConvertCredit={setConvertCreditId} onEdit={openProformaForm} onStatus={(proforma, status) => void updateProformaStatus(proforma, status)} onConvert={(proforma) => void convertProforma(proforma, convertCreditId === proforma.id)} onDelete={(proforma) => void deleteProforma(proforma)} />}
          {tab === "debts" && <DebtsTable debts={filteredDebts} loading={loading} onPay={(debt) => void openPaymentForm(debt)} />}
        </div>
        <div className="border-t border-slate-100 px-5 py-3 text-[11px] text-slate-500">{tab === "clients" ? `${filteredClients.length} client(s) · L’historique d’achats est accessible depuis chaque fiche.` : tab === "proformas" ? `${filteredProformas.length} proforma(s)` : `${filteredDebts.length} dette(s) en cours`}</div>
      </section>
    </div>

    {clientModal && <Modal title={clientModal === "new" ? "Nouveau client" : "Modifier le client"} onClose={() => !clientSaving && setClientModal(null)}>
      <form onSubmit={(event) => void saveClient(event)} className="space-y-4">
        <Field label="Nom complet"><input autoFocus required maxLength={120} className="input-field" value={clientForm.nom} onChange={(event) => setClientForm({ ...clientForm, nom: event.target.value })} placeholder="Nom du client ou de l’entreprise" /></Field>
        <div className="grid gap-3 sm:grid-cols-2"><Field label="Téléphone"><input maxLength={30} className="input-field" value={clientForm.telephone} onChange={(event) => setClientForm({ ...clientForm, telephone: event.target.value })} placeholder="+221 …" /></Field><Field label="Adresse e-mail"><input type="email" maxLength={180} className="input-field" value={clientForm.email} onChange={(event) => setClientForm({ ...clientForm, email: event.target.value })} placeholder="client@exemple.com" /></Field></div>
        <Field label="Adresse"><textarea maxLength={255} rows={2} className="input-field resize-y" value={clientForm.adresse} onChange={(event) => setClientForm({ ...clientForm, adresse: event.target.value })} placeholder="Adresse du client" /></Field>
        <ModalActions busy={clientSaving} submitLabel="Enregistrer le client" onCancel={() => setClientModal(null)} />
      </form>
    </Modal>}

    {proformaModal && <Modal wide title={proformaModal === "new" ? "Nouvelle facture proforma" : `Modifier ${proformaModal.numero}`} onClose={() => !proformaSaving && setProformaModal(null)}>
      <form onSubmit={(event) => void saveProforma(event)} className="space-y-5">
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Numéro"><input required maxLength={50} className="input-field" value={proformaNumber} onChange={(event) => setProformaNumber(event.target.value)} /></Field>
          <Field label="Client (facultatif)"><span className="relative block"><select className="input-field appearance-none pr-9" value={proformaClientId} onChange={(event) => setProformaClientId(event.target.value)}><option value="">Client comptoir</option>{clients.map((client) => <option key={client.id} value={client.id}>{client.nom}</option>)}</select><ChevronDown className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" size={15} /></span></Field>
          <Field label="Date d’émission"><input required type="date" className="input-field" value={emissionDate} onChange={(event) => setEmissionDate(event.target.value)} /></Field>
          <Field label="Date d’expiration"><input type="date" min={emissionDate} className="input-field" value={expirationDate} onChange={(event) => setExpirationDate(event.target.value)} /></Field>
        </div>
        <div className="overflow-hidden rounded-xl border border-slate-200">
          <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-4 py-3"><div><p className="text-xs font-bold text-slate-700">Articles et prix négociés</p><p className="mt-0.5 text-[10px] text-slate-400">Saisissez une remise ou modifiez directement le prix unitaire.</p></div><button type="button" onClick={() => setDraftLines((current) => [...current, blankLine()])} className="button-secondary h-9"><Plus size={14} /> Ajouter</button></div>
          <div className="divide-y divide-slate-100">
            {draftLines.map((line, index) => <div key={index} className="grid gap-3 p-3 sm:grid-cols-[minmax(0,1fr)_85px_100px_130px_115px_36px] sm:items-end">
              <Field label="Article"><select required className="input-field h-10" value={line.articleId} onChange={(event) => updateLine(index, { articleId: event.target.value })}><option value="">Sélectionner</option>{articles.map((article) => <option key={article.id} value={article.id}>{article.reference} · {article.designation} ({currency.format(article.prixVente)})</option>)}</select></Field>
              <Field label="Qté"><input required type="number" min="1" step="1" className="input-field h-10" value={line.quantite} onChange={(event) => updateLine(index, { quantite: event.target.value })} /></Field>
              <Field label="Remise %"><input type="number" min="0" max="100" step="0.01" className="input-field h-10" value={line.remise} onChange={(event) => updateLine(index, { remise: event.target.value })} /></Field>
              <Field label="Prix négocié"><input required type="number" min="0" step="any" className="input-field h-10" value={line.prixUnitaire} onChange={(event) => updateLine(index, { prixUnitaire: event.target.value })} /></Field>
              <div><span className="mb-1 block text-[10px] font-bold text-slate-500">Total ligne</span><p className="flex h-10 items-center text-xs font-bold text-slate-800">{currency.format((Number(line.quantite) || 0) * (Number(line.prixUnitaire) || 0))}</p></div>
              <button type="button" title="Supprimer cette ligne" disabled={draftLines.length === 1} onClick={() => setDraftLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} className="icon-button mb-0.5 disabled:opacity-30"><Trash2 size={15} /></button>
            </div>)}
          </div>
          <div className="flex justify-between border-t border-slate-100 bg-slate-50 px-4 py-3"><span className="text-xs font-bold text-slate-600">Total proforma</span><span className="text-sm font-extrabold text-slate-900">{currency.format(proformaTotal)}</span></div>
        </div>
        <ModalActions busy={proformaSaving} submitLabel={proformaModal === "new" ? "Créer le brouillon" : "Enregistrer les modifications"} onCancel={() => setProformaModal(null)} />
      </form>
    </Modal>}

    {clientDetail && <Modal wide title={`Fiche client · ${clientDetail.nom}`} onClose={() => setClientDetail(null)}>
      <div className="mb-5 grid gap-3 rounded-xl border border-slate-100 bg-slate-50 p-4 sm:grid-cols-3">
        <div className="flex gap-2 text-xs text-slate-600"><UserRound size={15} className="shrink-0 text-slate-400" />{clientDetail.nom}</div>
        <div className="flex gap-2 text-xs text-slate-600"><Mail size={15} className="shrink-0 text-slate-400" />{clientDetail.email || clientDetail.telephone || "Aucun contact"}</div>
        <div className="flex gap-2 text-xs text-slate-600"><MapPin size={15} className="shrink-0 text-slate-400" />{clientDetail.adresse || "Adresse non renseignée"}</div>
      </div>
      <div className="mb-5 flex items-center justify-between"><h3 className="text-sm font-bold text-slate-800">Historique d’achats</h3><span className="text-[11px] text-slate-500">{(clients.find((client) => client.id === clientDetail.id) ?? clientDetail).nombreAchats} vente(s) validée(s){clientSales.length < (clients.find((client) => client.id === clientDetail.id) ?? clientDetail).nombreAchats ? ` · ${clientSales.length} dernière(s) affichée(s)` : ""}</span></div>
      {clientSales.length ? <div className="max-h-56 overflow-auto rounded-xl border border-slate-200"><table className="w-full text-left"><thead className="sticky top-0 bg-slate-50 text-[9px] font-bold uppercase text-slate-400"><tr><th className="px-3 py-2">Vente</th><th className="px-3 py-2">Date</th><th className="px-3 py-2">Articles</th><th className="px-3 py-2 text-right">Total</th>{canCancelSales && <th className="px-3 py-2 text-right">Action</th>}</tr></thead><tbody className="divide-y divide-slate-100">{clientSales.map((sale) => <tr key={sale.id}><td className="px-3 py-2.5 text-xs font-bold text-slate-700">{sale.code}</td><td className="px-3 py-2.5 text-xs text-slate-500">{dateTimeLabel(sale.dateVente)}</td><td className="px-3 py-2.5 text-xs text-slate-500">{sale.lignes.length}</td><td className="px-3 py-2.5 text-right text-xs font-bold text-slate-800">{currency.format(sale.montantTotal)}</td>{canCancelSales && <td className="px-3 py-2.5 text-right">{sale.aCredit ? <button disabled={cancellingSaleId === sale.id} onClick={() => void cancelCreditSale(sale)} className="text-[10px] font-bold text-rose-600 hover:text-rose-800 disabled:opacity-50">{cancellingSaleId === sale.id ? "Annulation…" : "Annuler"}</button> : <span className="text-[10px] text-slate-300">—</span>}</td>}</tr>)}</tbody></table></div> : <Empty>Pas encore d’achat validé pour ce client.</Empty>}
      <div className="mb-3 mt-6 flex items-center justify-between"><h3 className="text-sm font-bold text-slate-800">Situation du compte</h3><span className="text-xs font-bold text-orange-700">{currency.format(clientDebts.reduce((sum, debt) => sum + Math.max(0, debt.solde), 0))} dû</span></div>
      {clientDebts.length ? <div className="max-h-40 overflow-auto rounded-xl border border-slate-200"><table className="w-full text-left"><tbody className="divide-y divide-slate-100">{clientDebts.map((debt) => <tr key={debt.id}><td className="px-3 py-2.5 text-xs font-semibold text-slate-700">{debt.numeroVente}</td><td className="px-3 py-2.5 text-xs text-slate-500">{dateLabel(debt.dateCreation)}</td><td className="px-3 py-2.5"><DebtBadge status={debt.statut} /></td><td className="px-3 py-2.5 text-right text-xs font-bold text-slate-800">{currency.format(debt.solde)}</td></tr>)}</tbody></table></div> : <Empty>Aucune dette client.</Empty>}
      <div className="mt-5 flex justify-end"><button onClick={() => { setClientDetail(null); openClientForm(clientDetail); }} className="button-secondary"><Pencil size={14} /> Modifier la fiche</button></div>
    </Modal>}

    {approbationDemandee && <ApprobationModal message={approbationDemandee.message} erreur={approbationDemandee.erreur} occupe={proformaSaving} libelleAction="Enregistrer le proforma" onAnnuler={() => setApprobationDemandee(null)} onSoumettre={(approbation) => { setApprobationDemandee(null); void envoyerProforma(approbation); }} />}
    {cashProforma && <Modal title={`Règlement · ${cashProforma.numero}`} onClose={() => busyProformaId === null && setCashProforma(null)}>
      <div className="mb-5 rounded-xl border border-emerald-200 bg-emerald-50 p-4"><p className="text-[10px] font-bold uppercase tracking-wider text-emerald-700">Total à encaisser</p><p className="mt-1 text-xl font-extrabold text-emerald-900">{currency.format(cashProforma.montantTotal)}</p></div>
      <form onSubmit={(event) => void confirmCashConversion(event)} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Mode de règlement"><select className="input-field" value={cashMode} onChange={(event) => { const mode = event.target.value as PaymentMode; setCashMode(mode); if (mode !== "ESPECES") setCashReceived(String(cashProforma.montantTotal)); }}>{paymentModes.map((mode) => <option key={mode.value} value={mode.value}>{mode.label}</option>)}</select></Field>
          <Field label="Montant reçu"><input required autoFocus type="number" min={cashProforma.montantTotal} step="any" className="input-field" value={cashReceived} onChange={(event) => setCashReceived(event.target.value)} /></Field>
        </div>
        {cashMode === "ESPECES" && Number(cashReceived) > cashProforma.montantTotal && <p className="rounded-xl bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-800">Monnaie à rendre : {currency.format(Number(cashReceived) - cashProforma.montantTotal)}</p>}
        <p className="text-[10px] text-slate-500">La vente sera validée, le stock déduit et le montant ajouté à la caisse de la journée ouverte.</p>
        <ModalActions busy={busyProformaId !== null} submitLabel="Encaisser et convertir" onCancel={() => setCashProforma(null)} />
      </form>
    </Modal>}
    {paymentDebt && <Modal wide title={`Règlement · ${paymentDebt.client}`} onClose={() => !paymentSaving && setPaymentDebt(null)}>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-orange-200 bg-orange-50 p-4"><div><p className="text-[10px] font-bold uppercase tracking-wider text-orange-700">Solde restant · vente {paymentDebt.numeroVente}</p><p className="mt-1 text-xl font-extrabold text-orange-900">{currency.format(paymentDebt.solde)}</p></div><DebtBadge status={paymentDebt.statut} /></div>
      <form onSubmit={(event) => void savePayment(event)} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Montant versé"><input required autoFocus type="number" min="0.0001" max={paymentDebt.solde} step="any" className="input-field" value={paymentAmount} onChange={(event) => setPaymentAmount(event.target.value)} /></Field>
          <Field label="Mode de règlement"><select className="input-field" value={paymentMode} onChange={(event) => setPaymentMode(event.target.value as PaymentMode)}>{paymentModes.map((mode) => <option key={mode.value} value={mode.value}>{mode.label}</option>)}</select></Field>
          <Field label="Référence (facultatif)"><input maxLength={80} className="input-field" value={paymentReference} onChange={(event) => setPaymentReference(event.target.value)} placeholder="N° de transaction ou reçu" /></Field>
        </div>
        <div className="rounded-xl border border-slate-200">
          <div className="flex items-center gap-2 border-b border-slate-100 px-4 py-3 text-xs font-bold text-slate-700"><History size={15} className="text-slate-400" />Historique des règlements</div>
          {paymentHistoryLoading ? <div className="p-4 text-xs text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={14} />Chargement…</div>
            : paymentHistory.length ? <div className="max-h-32 divide-y divide-slate-100 overflow-auto">{paymentHistory.map((payment) => <div key={payment.id} className="flex justify-between gap-3 px-4 py-2.5 text-xs"><span className="text-slate-500">{dateTimeLabel(payment.dateReglement)} · {modeLabel(payment.modeReglement)}</span><strong className="text-slate-800">{currency.format(payment.montant)}</strong></div>)}</div>
              : <p className="p-4 text-xs text-slate-400">Aucun règlement enregistré.</p>}
        </div>
        <ModalActions busy={paymentSaving} submitLabel="Enregistrer et générer le reçu" onCancel={() => setPaymentDebt(null)} />
      </form>
    </Modal>}

    {receipt && <div className="fixed inset-0 z-[70] grid place-items-center overflow-y-auto bg-slate-950/50 p-4 print:static print:block print:bg-white print:p-0">
      <section id="client-payment-receipt" className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl print:max-w-none print:rounded-none print:p-0 print:shadow-none">
        <div className="mb-5 flex items-center justify-between print:hidden"><div><p className="text-xs font-bold uppercase tracking-wider text-emerald-600">Versement enregistré</p><h2 className="mt-1 text-lg font-bold text-slate-900">Reçu de règlement</h2></div><button onClick={() => setReceipt(null)} className="icon-button" aria-label="Fermer"><X size={16} /></button></div>
        <div className="border-y border-dashed border-slate-300 py-5 text-center"><EnTeteDocument variante="ticket" /><p className="mt-2 text-[10px] text-slate-500">REÇU DE VERSEMENT CLIENT</p><p className="mt-4 text-2xl font-extrabold text-slate-900">{currency.format(receipt.payment.montant)}</p></div>
        <div className="space-y-3 py-5 text-xs"><ReceiptRow label="Client" value={receipt.debt.client} /><ReceiptRow label="Vente" value={receipt.debt.numeroVente} /><ReceiptRow label="Date" value={dateTimeLabel(receipt.payment.dateReglement)} /><ReceiptRow label="Mode" value={modeLabel(receipt.payment.modeReglement)} />{receipt.payment.reference && <ReceiptRow label="Référence" value={receipt.payment.reference} />}<ReceiptRow label="Reçu par" value={receipt.payment.recuPar || "Utilisateur"} /><ReceiptRow label="Solde restant" value={currency.format(Math.max(0, receipt.debt.solde - receipt.payment.montant))} /></div>
        <p className="border-t border-dashed border-slate-300 pt-4 text-center text-[10px] text-slate-500">Merci. Conservez ce reçu comme justificatif de paiement.</p>
        <div className="mt-5 flex gap-2 print:hidden"><button onClick={() => void imprimer(document.getElementById("client-payment-receipt"))} className="button-primary flex-1"><Printer size={15} /> Imprimer le reçu</button><button onClick={() => setReceipt(null)} className="button-secondary">Terminer</button></div>
      </section>
      <style>{`@media print { body * { visibility: hidden !important; } #client-payment-receipt, #client-payment-receipt * { visibility: visible !important; } #client-payment-receipt { position: fixed; inset: 0 auto auto 0; width: 80mm; max-width: 80mm; padding: 4mm; color: #000 !important; background: #fff !important; } @page { size: 80mm auto; margin: 3mm; } }`}</style>
    </div>}
  </>;
}

function statusLabel(status: Proforma["statut"]) {
  return ({ BROUILLON: "Brouillon", ENVOYEE: "Envoyée", ACCEPTEE: "Acceptée", REFUSEE: "Refusée", EXPIREE: "Expirée", CONVERTIE: "Convertie" })[status];
}

function modeLabel(mode: PaymentMode) {
  return paymentModes.find((item) => item.value === mode)?.label ?? mode;
}

function Metric({ title, count, icon }: { title: string; count: number | string; icon: ReactNode }) {
  return <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><div><p className="text-xs font-semibold text-slate-500">{title}</p><p className="mt-1 text-2xl font-extrabold text-slate-900">{count}</p></div><span className="grid size-10 place-items-center rounded-xl bg-emerald-50 text-emerald-700">{icon}</span></div>;
}

function TabButton({ active, onClick, icon, children }: { active: boolean; onClick: () => void; icon: ReactNode; children: ReactNode }) {
  return <button onClick={onClick} className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-[11px] font-bold transition ${active ? "bg-white text-emerald-700 shadow-sm" : "text-slate-500 hover:text-slate-800"}`}>{icon}{children}</button>;
}

function ClientsTable({ clients, loading, onDetail, onEdit, onDelete }: { clients: Client[]; loading: boolean; onDetail: (client: Client) => void; onEdit: (client: Client) => void; onDelete: (client: Client) => void }) {
  return <table className="w-full min-w-[760px] text-left">
    <thead className="bg-slate-50/80 text-[10px] font-bold uppercase tracking-wider text-slate-400"><tr><th className="px-5 py-3">Client</th><th className="px-4 py-3">Téléphone</th><th className="px-4 py-3">Adresse</th><th className="px-4 py-3">Achats validés</th><th className="px-5 py-3 text-right">Actions</th></tr></thead>
    <tbody className="divide-y divide-slate-100">{loading ? <LoadingRow cols={5} /> : clients.length ? clients.map((client) => <tr key={client.id} className="hover:bg-slate-50/70"><td className="px-5 py-3.5"><button onClick={() => onDetail(client)} className="flex items-center gap-2.5 text-left"><span className="grid size-9 place-items-center rounded-xl bg-emerald-50 text-emerald-700"><UserRound size={16} /></span><span><span className="block text-xs font-bold text-slate-800">{client.nom}</span><span className="mt-1 block text-[10px] text-slate-400">{client.email || "E-mail non renseigné"}</span></span></button></td><td className="px-4 py-3.5 text-xs text-slate-600">{client.telephone || "—"}</td><td className="max-w-56 truncate px-4 py-3.5 text-xs text-slate-600">{client.adresse || "—"}</td><td className="px-4 py-3.5 text-xs font-semibold text-slate-700">{client.nombreAchats}</td><td className="px-5 py-3.5"><div className="flex justify-end gap-1"><button onClick={() => onDetail(client)} title="Historique et fiche client" className="icon-button text-slate-400 hover:text-emerald-700"><History size={15} /></button><button onClick={() => onEdit(client)} title="Modifier" className="icon-button text-slate-400 hover:text-emerald-700"><Pencil size={15} /></button><button onClick={() => onDelete(client)} title="Supprimer" className="icon-button text-slate-400 hover:bg-rose-50 hover:text-rose-600"><Trash2 size={15} /></button></div></td></tr>) : <EmptyRow cols={5} message="Aucun client trouvé." />}</tbody>
  </table>;
}

function ProformasTable({ proformas, loading, busyId, convertCreditId, onConvertCredit, onEdit, onStatus, onConvert, onDelete }: { proformas: Proforma[]; loading: boolean; busyId: number | null; convertCreditId: number | null; onConvertCredit: (value: number | null) => void; onEdit: (proforma: Proforma) => void; onStatus: (proforma: Proforma, status: "ENVOYEE" | "ACCEPTEE" | "REFUSEE") => void; onConvert: (proforma: Proforma) => void; onDelete: (proforma: Proforma) => void }) {
  return <table className="w-full min-w-[1020px] text-left">
    <thead className="bg-slate-50/80 text-[10px] font-bold uppercase tracking-wider text-slate-400"><tr><th className="px-5 py-3">Proforma</th><th className="px-4 py-3">Client</th><th className="px-4 py-3">Émise le</th><th className="px-4 py-3">Validité</th><th className="px-4 py-3">Articles</th><th className="px-4 py-3">Montant</th><th className="px-4 py-3">Statut</th><th className="px-5 py-3 text-right">Actions</th></tr></thead>
    <tbody className="divide-y divide-slate-100">{loading ? <LoadingRow cols={8} /> : proformas.length ? proformas.map((proforma) => {
      const busy = busyId === proforma.id;
      return <tr key={proforma.id} className="hover:bg-slate-50/70"><td className="px-5 py-3.5"><p className="text-xs font-bold text-slate-800">{proforma.numero}</p><p className="mt-1 text-[10px] text-slate-400">{proforma.lignes.length} ligne(s)</p></td><td className="px-4 py-3.5 text-xs text-slate-600">{proforma.client || "Client comptoir"}</td><td className="px-4 py-3.5 text-xs text-slate-600">{dateLabel(proforma.dateEmission)}</td><td className="px-4 py-3.5 text-xs text-slate-600">{dateLabel(proforma.dateExpiration)}</td><td className="px-4 py-3.5 text-xs text-slate-600">{proforma.lignes.reduce((sum, line) => sum + line.quantite, 0)} unité(s)</td><td className="px-4 py-3.5 text-xs font-bold text-slate-800">{currency.format(proforma.montantTotal)}</td><td className="px-4 py-3.5"><ProformaBadge status={proforma.statut} /></td><td className="px-5 py-3.5"><div className="flex flex-wrap items-center justify-end gap-1">
        {proforma.statut === "BROUILLON" && <><button disabled={busy} onClick={() => onEdit(proforma)} title="Modifier le brouillon" className="icon-button text-slate-400 hover:text-emerald-700"><Pencil size={14} /></button><button disabled={busy} onClick={() => onDelete(proforma)} title="Supprimer" className="icon-button text-slate-400 hover:text-rose-600"><Trash2 size={14} /></button><button disabled={busy} onClick={() => onStatus(proforma, "ENVOYEE")} className="button-secondary h-8 px-2.5 text-[10px]">Marquer envoyée</button></>}
        {proforma.statut === "ENVOYEE" && <><button disabled={busy} onClick={() => onStatus(proforma, "ACCEPTEE")} className="inline-flex h-8 items-center gap-1 rounded-lg bg-emerald-600 px-2.5 text-[10px] font-bold text-white disabled:opacity-50">{busy ? <LoaderCircle size={12} className="animate-spin" /> : <Check size={12} />}Accepter</button><button disabled={busy} onClick={() => onStatus(proforma, "REFUSEE")} className="button-secondary h-8 px-2.5 text-[10px]">Refuser</button></>}
        {proforma.statut === "ACCEPTEE" && <><label className="flex items-center gap-1 text-[9px] font-semibold text-slate-500"><input type="checkbox" checked={convertCreditId === proforma.id} onChange={(event) => onConvertCredit(event.target.checked ? proforma.id : null)} />À crédit</label><button disabled={busy} onClick={() => onConvert(proforma)} className="inline-flex h-8 items-center gap-1 rounded-lg bg-emerald-600 px-2.5 text-[10px] font-bold text-white disabled:opacity-50">{busy ? <LoaderCircle size={12} className="animate-spin" /> : <Check size={12} />}Convertir en vente</button></>}
        {proforma.statut === "CONVERTIE" && proforma.venteId && <span className="text-[10px] font-semibold text-slate-500">Vente #{proforma.venteId}</span>}
      </div></td></tr>;
    }) : <EmptyRow cols={8} message="Aucune facture proforma trouvée." />}</tbody>
  </table>;
}

function DebtsTable({ debts, loading, onPay }: { debts: Debt[]; loading: boolean; onPay: (debt: Debt) => void }) {
  return <table className="w-full min-w-[820px] text-left">
    <thead className="bg-slate-50/80 text-[10px] font-bold uppercase tracking-wider text-slate-400"><tr><th className="px-5 py-3">Client</th><th className="px-4 py-3">Vente / date</th><th className="px-4 py-3">Montant initial</th><th className="px-4 py-3">Déjà réglé</th><th className="px-4 py-3">Reste à payer</th><th className="px-4 py-3">Statut</th><th className="px-5 py-3 text-right">Action</th></tr></thead>
    <tbody className="divide-y divide-slate-100">{loading ? <LoadingRow cols={7} /> : debts.length ? debts.map((debt) => <tr key={debt.id} className="hover:bg-slate-50/70"><td className="px-5 py-3.5 text-xs font-bold text-slate-800">{debt.client}</td><td className="px-4 py-3.5"><p className="text-xs font-semibold text-slate-700">{debt.numeroVente}</p><p className="mt-1 text-[10px] text-slate-400">{dateLabel(debt.dateCreation)}</p></td><td className="px-4 py-3.5 text-xs text-slate-600">{currency.format(debt.montantInitial)}</td><td className="px-4 py-3.5 text-xs text-emerald-700">{currency.format(debt.montantRegle)}</td><td className="px-4 py-3.5 text-xs font-extrabold text-orange-700">{currency.format(debt.solde)}</td><td className="px-4 py-3.5"><DebtBadge status={debt.statut} /></td><td className="px-5 py-3.5 text-right"><button onClick={() => onPay(debt)} className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-emerald-600 px-3 text-[10px] font-bold text-white hover:bg-emerald-700"><Banknote size={13} />Enregistrer un règlement</button></td></tr>) : <EmptyRow cols={7} message="Aucune créance non soldée." />}</tbody>
  </table>;
}

function ProformaBadge({ status }: { status: Proforma["statut"] }) {
  const colors: Record<Proforma["statut"], string> = {
    BROUILLON: "bg-slate-100 text-slate-600",
    ENVOYEE: "bg-blue-50 text-blue-700",
    ACCEPTEE: "bg-emerald-50 text-emerald-700",
    REFUSEE: "bg-rose-50 text-rose-700",
    EXPIREE: "bg-orange-50 text-orange-700",
    CONVERTIE: "bg-violet-50 text-violet-700",
  };
  return <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${colors[status]}`}>{statusLabel(status)}</span>;
}

function DebtBadge({ status }: { status: Debt["statut"] }) {
  const config = status === "REGLEE" ? ["bg-emerald-50 text-emerald-700", "Soldé"]
    : status === "PARTIELLEMENT_REGLEE" ? ["bg-orange-50 text-orange-700", "Partiellement réglé"]
      : status === "ANNULEE" ? ["bg-slate-100 text-slate-500", "Annulé"]
        : ["bg-rose-50 text-rose-700", "Non réglé"];
  return <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[10px] font-bold ${config[0]}`}>{config[1]}</span>;
}

function Modal({ title, children, onClose, wide = false }: { title: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  return <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4 print:hidden" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section role="dialog" aria-modal="true" aria-label={title} className={`my-auto w-full rounded-2xl bg-white p-5 shadow-2xl md:p-6 ${wide ? "max-w-4xl" : "max-w-xl"}`}><div className="mb-5 flex items-start justify-between gap-4"><h2 className="text-lg font-bold text-slate-900">{title}</h2><button onClick={onClose} className="icon-button" aria-label="Fermer"><X size={16} /></button></div>{children}</section></div>;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="block min-w-0"><span className="mb-1.5 block text-[10px] font-bold text-slate-600">{label}</span>{children}</label>;
}

function ModalActions({ busy, submitLabel, onCancel }: { busy: boolean; submitLabel: string; onCancel: () => void }) {
  return <div className="flex justify-end gap-2 border-t border-slate-100 pt-4"><button type="button" disabled={busy} onClick={onCancel} className="button-secondary">Annuler</button><button disabled={busy} className="button-primary min-w-44">{busy ? <LoaderCircle size={15} className="animate-spin" /> : <Check size={15} />}{busy ? "Enregistrement…" : submitLabel}</button></div>;
}

function LoadingRow({ cols }: { cols: number }) {
  return <tr><td colSpan={cols} className="h-40 text-center text-sm text-slate-400"><LoaderCircle className="mr-2 inline animate-spin" size={16} />Chargement…</td></tr>;
}

function EmptyRow({ cols, message }: { cols: number; message: string }) {
  return <tr><td colSpan={cols} className="h-40 text-center text-sm text-slate-400">{message}</td></tr>;
}

function Empty({ children }: { children: ReactNode }) {
  return <div className="rounded-xl border border-dashed border-slate-200 px-4 py-5 text-center text-xs text-slate-400">{children}</div>;
}

function ReceiptRow({ label, value }: { label: string; value: string }) {
  return <div className="flex justify-between gap-4"><span className="text-slate-500">{label}</span><span className="text-right font-semibold text-slate-800">{value}</span></div>;
}
