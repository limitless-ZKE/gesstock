import { useEffect, useMemo, useState, type FormEvent } from "react";
import axios from "axios";
import {
  Banknote,
  Check,
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  CreditCard,
  HandCoins,
  Minus,
  Package,
  Plus,
  Search,
  ShoppingBag,
  Smartphone,
  Trash2,
  WalletCards,
  X,
} from "lucide-react";
import ChampClient from "../components/ChampClient";
import QuantiteSaisie from "../components/QuantiteSaisie";
import ReceiptModal, { type Sale } from "../components/ReceiptModal";
import { usePanier, type Article, type CartLine } from "../panier/PanierContext";
import { api } from "../services/api";
import { apiErrorMessage as messageFor, parseApiError } from "../services/apiError";
import { money as currency, moneyPrecise as money2 } from "../lib/format";
import { arrondirQuantite, formatQuantite } from "../lib/quantite";

type Category = { id: number; nom: string };
type Client = { id: number; nom: string; telephone: string | null };
type Promotion = {
  id: number;
  nom: string;
  pourcentage: number;
  debut: string;
  fin: string;
  active: boolean;
  articleIds: number[];
};
type CashSession = {
  id: number;
  code: string;
  journee: string;
  statut: "OUVERTE" | "FERMEE";
  journeeDepassee: boolean;
  fondDeCaisse: number;
  montantCloture: number | null;
  encaissementsEspeces: number;
  reglementsClientsEspeces: number;
  sortiesCaisse: number;
  soldeTheorique: number;
  ecartCloture: number | null;
  ouverteLe: string;
};
type SaleResponse = Sale;
type PaymentMode = "ESPECES" | "MOBILE_MONEY" | "CARTE" | "VIREMENT";
/** Le crédit n'est pas un mode de règlement : la vente devient une dette du client, rien n'est encaissé. */
type ChoixReglement = PaymentMode | "CREDIT";

const round2 = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100;
const round4 = (value: number) => Math.round((value + Number.EPSILON) * 10000) / 10000;

/** Prix négocié saisi, arrondi à deux décimales ; null si le champ est vide ou invalide. */
function parseNegotiatedPrice(value: string): number | null {
  if (value.trim() === "") return null;
  const parsed = Number(value.replace(",", "."));
  return Number.isFinite(parsed) && parsed >= 0 ? round2(parsed) : null;
}

/** Message d'erreur du prix négocié d'une ligne, ou null s'il est valide (ou vide). */
function negotiatedPriceError(line: CartLine): string | null {
  if (line.prixNegocie.trim() === "") return null;
  const price = parseNegotiatedPrice(line.prixNegocie);
  if (price === null) return "Montant invalide.";
  if (price > line.article.prixVente) return `Ne peut pas dépasser le prix catalogue (${currency.format(line.article.prixVente)}).`;
  return null;
}

/** Prix de base appliqué à la ligne : le prix négocié s'il est valide, sinon le prix catalogue. */
function basePrice(line: CartLine): number {
  return negotiatedPriceError(line) === null ? parseNegotiatedPrice(line.prixNegocie) ?? line.article.prixVente : line.article.prixVente;
}

/** Total de la ligne au prix catalogue (sans remise ni promotion). */
const catalogueTotal = (line: CartLine) => round2(line.article.prixVente * line.quantite);

/** Message d'erreur du total négocié d'une ligne, ou null s'il est valide (ou vide). */
function negotiatedTotalError(line: CartLine): string | null {
  if (line.totalNegocie.trim() === "") return null;
  const total = parseNegotiatedPrice(line.totalNegocie);
  if (total === null) return "Montant invalide.";
  if (total > catalogueTotal(line)) return `Ne peut pas dépasser le total au prix catalogue (${currency.format(catalogueTotal(line))}).`;
  return null;
}

/** Total négocié de la ligne s'il est saisi et valide ; null sinon. */
function negotiatedLineTotal(line: CartLine): number | null {
  return negotiatedTotalError(line) === null ? parseNegotiatedPrice(line.totalNegocie) : null;
}

function promotionOf(line: CartLine, promotions: Promotion[]) {
  return line.promotionId == null
    ? bestPromotion(line.article.id, promotions)
    : promotions.find((candidate) => candidate.id === line.promotionId);
}

/**
 * Montant de la ligne, calculé comme le serveur : le total négocié s'il y en a un, sinon
 * (prix − promotion arrondie à 4 décimales) × quantité, arrondi à 4 décimales.
 */
function lineAmount(line: CartLine, promotions: Promotion[]): number {
  const total = negotiatedLineTotal(line);
  if (total !== null) return total;
  const rate = promotionOf(line, promotions)?.pourcentage ?? 0;
  const unitDiscount = round4(basePrice(line) * rate / 100);
  return round4((basePrice(line) - unitDiscount) * line.quantite);
}


const paymentModes: { value: PaymentMode; label: string; icon: typeof Banknote }[] = [
  { value: "ESPECES", label: "Espèces", icon: Banknote },
  { value: "MOBILE_MONEY", label: "Mobile Money", icon: Smartphone },
  { value: "CARTE", label: "Carte", icon: CreditCard },
  { value: "VIREMENT", label: "Virement", icon: WalletCards },
];

/** Date d'une journée de caisse (AAAA-MM-JJ) au format local, sans décalage de fuseau. */
function formatDay(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "long" }).format(new Date(year, month - 1, day));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("fr-FR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

export default function CaissePage() {
  const [articles, setArticles] = useState<Article[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [session, setSession] = useState<CashSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [catalogError, setCatalogError] = useState("");
  const [search, setSearch] = useState("");
  const [categoryId, setCategoryId] = useState("all");
  // Le panier et le client choisi vivent au-dessus de l'écran (PanierProvider) : quitter la caisse ne les perd pas.
  const { cart, setCart, clientId, setClientId, rapprocher } = usePanier();
  // Régime de TVA imposé par le serveur : la caisse l'affiche, elle ne le décide pas (0 % tant que la TVA n'est pas activée).
  const [fiscal, setFiscal] = useState({ tauxTva: 0, prixTtc: false });
  const [openingAmount, setOpeningAmount] = useState("");
  const [closingAmount, setClosingAmount] = useState("");
  const [sessionBusy, setSessionBusy] = useState(false);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [paymentMode, setPaymentMode] = useState<ChoixReglement>("ESPECES");
  const [amountReceived, setAmountReceived] = useState("");
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  // Lignes vendues sous le prix d'achat lors de la dernière vente : la vente est passée, le vendeur est prévenu.
  const [avertissements, setAvertissements] = useState<string[]>([]);
  const [pendingDraft, setPendingDraft] = useState<SaleResponse | null>(null);
  // Contenu envoyé avec ce brouillon : le serveur le garde tel quel, un panier modifié depuis exige donc un nouveau brouillon.
  const [draftSignature, setDraftSignature] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<SaleResponse | null>(null);
  const [notice, setNotice] = useState("");
  const [noticeIsError, setNoticeIsError] = useState(false);

  const filteredArticles = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("fr");
    return articles.filter((article) => {
      const matchesCategory = categoryId === "all" || String(article.categorieId ?? "") === categoryId;
      const matchesSearch = !query
        || article.designation.toLocaleLowerCase("fr").includes(query)
        || article.reference.toLocaleLowerCase("fr").includes(query);
      return matchesCategory && matchesSearch;
    });
  }, [articles, categoryId, search]);

  const cartTotals = useMemo(() => {
    // Même calcul que le serveur, ligne par ligne : le total affiché est exactement le total facturé.
    const subtotal = cart.reduce((total, line) => total + lineAmount(line, promotions), 0);
    // Mêmes formules que le serveur (CalculTva) : le total affiché est exactement le total facturé.
    const net = round4(subtotal);
    if (fiscal.tauxTva === 0) return { subtotal: net, tax: 0, total: net };
    if (fiscal.prixTtc) {
      const hors = round4(net / (1 + fiscal.tauxTva / 100));
      return { subtotal: hors, tax: round4(net - hors), total: net };
    }
    const tax = round4(net * fiscal.tauxTva / 100);
    return { subtotal: net, tax, total: round4(net + tax) };
  }, [cart, promotions, fiscal]);

  useEffect(() => {
    api.get<{ tauxTva: number; prixTtc: boolean }>("/caisse/parametres")
      .then(({ data }) => setFiscal(data))
      .catch(() => undefined); // à défaut, 0 % : le serveur refusera un taux différent avec un message explicite
  }, []);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      const [articleResult, categoryResult, clientResult, promotionResult, sessionResult] =
        await Promise.allSettled([
          api.get<Article[]>("/articles"),
          api.get<Category[]>("/articles/categories"),
          api.get<Client[]>("/clients"),
          api.get<Promotion[]>("/promotions"),
          api.get<CashSession>("/referentiels/sessions-caisse/ouverte"),
        ]);
      if (!active) return;

      let changementsDuPanier: string[] = [];
      if (articleResult.status === "fulfilled") {
        setArticles(articleResult.value.data);
        // Un panier gardé depuis un passage précédent est corrigé d'après le stock et les prix du moment.
        changementsDuPanier = rapprocher(articleResult.value.data);
      }
      else setCatalogError(messageFor(articleResult.reason, "Impossible de charger le catalogue."));
      if (categoryResult.status === "fulfilled") setCategories(categoryResult.value.data);
      if (clientResult.status === "fulfilled") setClients(clientResult.value.data);
      if (promotionResult.status === "fulfilled") setPromotions(promotionResult.value.data);
      if (sessionResult.status === "fulfilled") {
        setSession(sessionResult.value.data);
        setClosingAmount(String(sessionResult.value.data.soldeTheorique));
      }
      else if (!axios.isAxiosError(sessionResult.reason) || sessionResult.reason.response?.status !== 404) {
        setNotice(messageFor(sessionResult.reason, "État de caisse indisponible."));
        setNoticeIsError(true);
      }
      if (changementsDuPanier.length > 0) {
        setNotice(`Panier mis à jour d'après le stock actuel : ${changementsDuPanier.join(" ")}`);
        setNoticeIsError(true);
      }
      setLoading(false);
    }
    void load();
    return () => { active = false; };
  }, [rapprocher]);

  function showNotice(message: string, isError = false) {
    setNotice(message);
    setNoticeIsError(isError);
  }

  function addToCart(article: Article) {
    if (article.quantiteStock <= 0) return;
    setCart((current) => {
      const existing = current.find((line) => line.article.id === article.id);
      if (existing) {
        return current.map((line) => line.article.id === article.id
          ? { ...line, quantite: arrondirQuantite(Math.min(line.quantite + 1, article.quantiteStock)) }
          : line);
      }
      // Un reste de stock inférieur à 1 (0,5 caisse) se vend tel quel.
      return [...current, { article, quantite: Math.min(1, article.quantiteStock), promotionId: null, prixNegocie: "", totalNegocie: "" }];
    });
  }

  function changeQuantity(articleId: number, quantity: number) {
    const article = articles.find((item) => item.id === articleId);
    if (!article) return;
    if (quantity <= 0) {
      setCart((current) => current.filter((line) => line.article.id !== articleId));
      return;
    }
    setCart((current) => current.map((line) => line.article.id === articleId
      ? { ...line, quantite: arrondirQuantite(Math.min(quantity, article.quantiteStock)) }
      : line));
  }

  async function refreshSession() {
    try {
      const { data } = await api.get<CashSession>("/referentiels/sessions-caisse/ouverte");
      setSession(data);
      setClosingAmount(String(data.soldeTheorique));
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) setSession(null);
      else throw error;
    }
  }

  async function openSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSessionBusy(true);
    try {
      const { data } = await api.post<CashSession>("/referentiels/sessions-caisse/ouverture", {
        fondDeCaisse: Number(openingAmount),
      });
      setSession(data);
      setOpeningAmount("");
      setClosingAmount(String(data.soldeTheorique));
      showNotice("La session de caisse est ouverte.");
    } catch (error) {
      showNotice(messageFor(error, "L’ouverture de caisse a échoué."), true);
    } finally {
      setSessionBusy(false);
    }
  }

  async function closeSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    setSessionBusy(true);
    try {
      const { data: closedSession } = await api.post<CashSession>(`/referentiels/sessions-caisse/${session.id}/cloture`, {
        montantCloture: Number(closingAmount),
      });
      setSession(null);
      setClosingAmount("");
      const variance = closedSession.ecartCloture ?? 0;
      showNotice(variance === 0
        ? "La session de caisse est clôturée, le comptage correspond au solde théorique."
        : `Session clôturée. Écart de caisse : ${currency.format(variance)} (${variance > 0 ? "excédent" : "manquant"}).`);
    } catch (error) {
      showNotice(messageFor(error, "La clôture de caisse a échoué."), true);
    } finally {
      setSessionBusy(false);
    }
  }

  function beginCheckout() {
    if (!session) {
      showNotice("Ouvrez une session de caisse avant d’encaisser une vente.", true);
      return;
    }
    setPaymentMode("ESPECES");
    setAmountReceived(String(cartTotals.total));
    setCheckoutOpen(true);
  }

  async function checkout(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await envoyerVente();
  }

  /** Enregistre puis valide la vente. Le prix négocié est libre (aucun accord de responsable n'est demandé). */
  async function envoyerVente() {
    if (!session || cart.length === 0) return;
    const invalidLine = cart.find((line) => negotiatedPriceError(line) !== null || negotiatedTotalError(line) !== null);
    if (invalidLine) {
      showNotice(`Prix négocié invalide pour « ${invalidLine.article.designation} ». ${negotiatedPriceError(invalidLine) ?? negotiatedTotalError(invalidLine)}`, true);
      return;
    }
    // À crédit : ni mode de règlement ni montant reçu, la vente est inscrite en dette au nom du client.
    const modeReglement = paymentMode === "CREDIT" ? null : paymentMode;
    const aCredit = modeReglement === null;
    if (aCredit && !clientId) {
      showNotice("Une vente à crédit doit être rattachée à un client : choisissez-le dans le panier.", true);
      return;
    }
    const received = Number(amountReceived);
    if (!aCredit && (!Number.isFinite(received) || received < cartTotals.total)) {
      showNotice("Le montant reçu doit être supérieur ou égal au total à payer.", true);
      return;
    }
    if (!aCredit && modeReglement !== "ESPECES" && received !== cartTotals.total) {
      showNotice("Pour un règlement sans espèces, saisissez exactement le total à payer.", true);
      return;
    }
    setCheckoutBusy(true);
    let draftAlreadyExists = pendingDraft !== null;
    try {
      const request = {
        clientId: clientId ? Number(clientId) : null,
        aCredit,
        tauxTva: fiscal.tauxTva,
        modeReglement,
        montantRecu: aCredit ? null : received,
        lignes: cart.map((line) => {
          // total négocié pour la ligne entière : il remplace le prix unitaire et la promotion (envoyé s'il diffère du catalogue)
          const total = negotiatedLineTotal(line);
          if (total !== null && total !== catalogueTotal(line)) {
            return { articleId: line.article.id, quantite: line.quantite, promotionId: null, prixUnitaire: null, montantNegocie: total };
          }
          const negotiated = parseNegotiatedPrice(line.prixNegocie);
          return {
            articleId: line.article.id,
            quantite: line.quantite,
            promotionId: line.promotionId,
            // prix négocié envoyé seulement s'il diffère du prix catalogue
            prixUnitaire: negotiated !== null && negotiated !== line.article.prixVente ? negotiated : null,
          };
        }),
      };
      const signature = JSON.stringify(request);
      let draft = pendingDraft !== null && draftSignature === signature ? pendingDraft : null;
      draftAlreadyExists = draft !== null;
      if (!draft) {
        const { data } = await api.post<SaleResponse>("/ventes", request);
        draft = data;
        draftAlreadyExists = true;
        setPendingDraft(draft);
        setDraftSignature(signature);
      }
      const { data: validated } = await api.post<SaleResponse>(`/ventes/${draft.id}/validation`);
      setReceipt(validated);
      setAvertissements(validated.avertissements ?? []);
      setPendingDraft(null);
      setDraftSignature(null);
      setCart([]);
      setClientId("");
      setCheckoutOpen(false);
      setArticles((current) => current.map((article) => {
        const sold = cart.find((line) => line.article.id === article.id);
        return sold ? { ...article, quantiteStock: arrondirQuantite(article.quantiteStock - sold.quantite) } : article;
      }));
      showNotice("Vente enregistrée et stock mis à jour.");
      void refreshSession().catch(() => setSession(null));
    } catch (error) {
      if (parseApiError(error).code === "PRIX_SOUS_LE_COUT") {
        // Le serveur refuse de valider tant que le prix négocié est sous le prix d'achat : on referme l'encaissement pour que le
        // caissier relève le prix dans le panier ; l'ancien brouillon est abandonné, un nouveau sera créé avec le prix corrigé.
        setPendingDraft(null);
        setDraftSignature(null);
        setCheckoutOpen(false);
        showNotice(`${messageFor(error, "Un prix négocié est sous le prix d’achat.")} Le panier est conservé : corrigez le prix, puis encaissez à nouveau.`, true);
        return;
      }
      showNotice(
        draftAlreadyExists
          ? `Le brouillon reste enregistré. ${messageFor(error, "La validation a échoué.")} Corrigez le problème puis réessayez.`
          : messageFor(error, "La vente n’a pas pu être validée."),
        true,
      );
    } finally {
      setCheckoutBusy(false);
    }
  }

  const amountTendered = Number(amountReceived) || 0;
  const change = Math.max(0, amountTendered - cartTotals.total);
  const paymentAmountValid = paymentMode === "CREDIT"
    ? clientId !== ""
    : paymentMode === "ESPECES"
      ? amountTendered >= cartTotals.total
      : amountTendered === cartTotals.total;
  const clientNom = clients.find((client) => String(client.id) === clientId)?.nom ?? null;

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-slate-900">Caisse & ventes</h1>
      </header>

      {notice && (
        <div role={noticeIsError ? "alert" : "status"} className={`flex items-start gap-2 rounded-xl border px-4 py-3 text-sm font-medium ${
          noticeIsError ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"
        }`}>
          {noticeIsError ? <CircleAlert size={17} className="mt-0.5 shrink-0" /> : <CheckCircle2 size={17} className="mt-0.5 shrink-0" />}
          <span>{notice}</span>
          <button className="ml-auto opacity-60 hover:opacity-100" onClick={() => setNotice("")} aria-label="Fermer"><X size={16} /></button>
        </div>
      )}

      {avertissements.length > 0 && (
        <div role="alert" className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900">
          <CircleAlert size={17} className="mt-0.5 shrink-0" />
          <div>
            <p className="font-bold">Vente enregistrée sous le prix d’achat</p>
            <ul className="mt-1 list-disc pl-4">
              {avertissements.map((avertissement) => <li key={avertissement}>{avertissement.replace(/^Vendu sous le prix d'achat — /, "")}</li>)}
            </ul>
            <p className="mt-1 text-xs">Elle est notée dans le journal des opérations.</p>
          </div>
          <button className="ml-auto opacity-60 hover:opacity-100" onClick={() => setAvertissements([])} aria-label="Fermer l’avertissement"><X size={16} /></button>
        </div>
      )}

      <CashSessionPanel
        session={session}
        openingAmount={openingAmount}
        closingAmount={closingAmount}
        busy={sessionBusy}
        onOpeningAmount={setOpeningAmount}
        onClosingAmount={setClosingAmount}
        onOpen={openSession}
        onClose={closeSession}
      />

      <div className="grid items-start gap-5 2xl:grid-cols-[minmax(0,1.65fr)_minmax(390px,0.85fr)]">
        <section className="min-w-0 rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-col gap-4 border-b border-slate-100 p-5 md:p-6">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="font-bold text-slate-900">Catalogue</h2>
                <p className="mt-1 text-xs text-slate-500">{articles.length} articles au catalogue</p>
              </div>
              
            </div>
            <div className="flex flex-col gap-3 sm:flex-row">
              <label className="relative min-w-0 flex-1">
                <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" size={17} />
                <input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  onKeyDown={(event) => {
                    // Un lecteur de codes-barres « tape » la référence puis Entrée : on ajoute l'article au panier.
                    if (event.key !== "Enter") return;
                    const code = search.trim().toLocaleLowerCase("fr");
                    const scanned = code && articles.find((article) => article.reference.toLocaleLowerCase("fr") === code);
                    if (scanned) {
                      addToCart(scanned);
                      setSearch("");
                    }
                  }}
                  className="input-field pl-10"
                  placeholder="Rechercher un article ou scanner son code…"
                  aria-label="Rechercher par nom ou référence"
                />
              </label>
              <label className="relative sm:w-52">
                <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} className="input-field appearance-none pr-9">
                  <option value="all">Toutes les catégories</option>
                  {categories.map((category) => <option key={category.id} value={category.id}>{category.nom}</option>)}
                </select>
                <ChevronDown className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
              </label>
            </div>
          </div>

          <div className="max-h-[620px] min-h-[300px] overflow-y-auto p-5 md:p-6">
            {catalogError && <p role="alert" className="mb-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{catalogError}</p>}
            {loading ? (
              <div className="grid min-h-60 place-items-center text-sm text-slate-400">Chargement du catalogue…</div>
            ) : filteredArticles.length === 0 ? (
              <div className="grid min-h-60 place-items-center text-center">
                <div><Package className="mx-auto text-slate-300" size={28} /><p className="mt-3 text-sm font-semibold text-slate-600">Aucun article trouvé</p><p className="mt-1 text-xs text-slate-400">Modifiez votre recherche ou votre filtre.</p></div>
              </div>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {filteredArticles.map((article) => {
                  const inCart = cart.find((line) => line.article.id === article.id)?.quantite ?? 0;
                  const outOfStock = article.quantiteStock <= 0;
                  const lowStock = article.quantiteStock > 0 && article.quantiteStock <= article.seuilAlerteStock;
                  return (
                    <button
                      key={article.id}
                      disabled={outOfStock}
                      onClick={() => addToCart(article)}
                      className="group relative flex min-h-[160px] flex-col rounded-xl border border-slate-200 p-4 text-left transition hover:border-emerald-300 hover:shadow-md disabled:cursor-not-allowed disabled:opacity-55"
                    >
                      {inCart > 0 && <span className="absolute right-3 top-3 grid h-6 min-w-6 place-items-center rounded-full bg-emerald-600 px-1.5 text-xs font-bold text-white">{formatQuantite(inCart)}</span>}
                      
                      <p className="line-clamp-2 pr-7 text-base font-semibold text-slate-900">{article.designation}</p>
                      <p className="mt-1 text-xs font-medium text-slate-400">{article.reference}{article.categorie ? ` · ${article.categorie}` : ""}</p>
                      <div className="mt-auto flex items-end justify-between gap-2 pt-4">
                        <div>
                          <p className="text-sm font-extrabold text-slate-900">{currency.format(article.prixVente)}</p>
                          <p className={`mt-1 text-xs font-semibold ${outOfStock || lowStock ? "text-amber-700" : "text-slate-400"}`}>
                            Stock : {formatQuantite(article.quantiteStock)} {article.uniteMesure ?? ""}
                            {outOfStock ? " · Rupture" : lowStock ? " · Alerte" : ""}
                          </p>
                        </div>
                        <span className="grid size-8 place-items-center rounded-lg bg-slate-100 text-slate-500 group-hover:bg-emerald-600 group-hover:text-white"><Plus size={16} /></span>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </section>

        <section className="min-w-0 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-100 p-5 md:p-6">
            <div className="flex items-center gap-3">
              
              <div><h2 className="font-bold text-slate-900">Panier de vente</h2><p className="mt-1 text-xs text-slate-500">{formatQuantite(cart.reduce((count, line) => count + line.quantite, 0))} article(s)</p></div>
            </div>
            {cart.length > 0 && <button onClick={() => setCart([])} className="text-xs font-semibold text-slate-400 hover:text-rose-600">Vider</button>}
          </div>

          <div className="space-y-4 p-5 md:p-6">
            <ChampClient clients={clients} valeur={clientId} onChange={setClientId} />

            <div className="max-h-[335px] min-h-[150px] space-y-3 overflow-y-auto pr-1">
              {cart.length === 0 ? (
                <div className="grid min-h-[150px] place-items-center rounded-xl border border-dashed border-slate-200 text-center">
                  <div><ShoppingBag className="mx-auto text-slate-300" size={24} /><p className="mt-2 text-xs font-semibold text-slate-500">Votre panier est vide</p><p className="mt-1 text-xs text-slate-400">Ajoutez des articles depuis le catalogue.</p></div>
                </div>
              ) : cart.map((line) => {
                const availablePromotions = promotionsForArticle(line.article.id, promotions);
                const negotiationError = negotiatedPriceError(line);
                const totalError = negotiatedTotalError(line);
                const lineTotal = negotiatedLineTotal(line);
                const negotiatedDiscount = round2(line.article.prixVente - basePrice(line));
                const amount = lineAmount(line, promotions);
                const unitPrice = amount / line.quantite;
                const lineDiscount = round2(catalogueTotal(line) - (lineTotal ?? 0));
                return (
                  <article key={line.article.id} className="rounded-xl border border-slate-100 p-3.5">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate text-xs font-bold text-slate-800">{line.article.designation}</p>
                        <p className="mt-1 text-xs text-slate-400">{money2.format(unitPrice)} / {line.article.uniteMesure || "unité"}</p>
                      </div>
                      <button onClick={() => changeQuantity(line.article.id, 0)} className="rounded-md p-1 text-slate-300 hover:bg-rose-50 hover:text-rose-600" aria-label={`Retirer ${line.article.designation}`}><Trash2 size={15} /></button>
                    </div>
                    <div className="mt-3 flex items-center justify-between gap-2">
                      <div className="flex items-center rounded-lg border border-slate-200">
                        <button onClick={() => changeQuantity(line.article.id, line.quantite - 1)} className="grid size-8 place-items-center text-slate-500 hover:bg-slate-50" aria-label="Diminuer quantité"><Minus size={13} /></button>
                        <QuantiteSaisie
                          valeur={line.quantite}
                          max={line.article.quantiteStock}
                          libelle={`Quantité de ${line.article.designation}`}
                          onChange={(quantite) => changeQuantity(line.article.id, quantite)}
                        />
                        <button onClick={() => changeQuantity(line.article.id, line.quantite + 1)} disabled={line.quantite >= line.article.quantiteStock} className="grid size-8 place-items-center text-slate-500 hover:bg-slate-50 disabled:opacity-30" aria-label="Augmenter quantité"><Plus size={13} /></button>
                      </div>
                      <p className="text-xs font-extrabold text-slate-900">{money2.format(amount)}</p>
                    </div>
                    <label className="mt-3 flex items-center gap-2 text-xs font-semibold text-slate-600">
                      <span className="shrink-0">Prix négocié</span>
                      <input
                        type="number"
                        inputMode="decimal"
                        min="0"
                        max={line.article.prixVente}
                        step="0.01"
                        value={line.prixNegocie}
                        placeholder={`Catalogue : ${line.article.prixVente}`}
                        aria-label={`Prix négocié pour ${line.article.designation}`}
                        onChange={(event) => setCart((current) => current.map((entry) => entry.article.id === line.article.id
                          ? { ...entry, prixNegocie: event.target.value, totalNegocie: "" }
                          : entry))}
                        className={`input-field h-8 min-w-0 flex-1 text-right text-xs ${negotiationError ? "border-rose-300" : ""}`}
                      />
                    </label>
                    <label className="mt-2 flex items-center gap-2 text-xs font-semibold text-slate-600">
                      <span className="shrink-0">Total négocié</span>
                      <input
                        type="number"
                        inputMode="decimal"
                        min="0"
                        step="0.01"
                        value={line.totalNegocie}
                        placeholder={`Catalogue : ${catalogueTotal(line)}`}
                        aria-label={`Total négocié pour ${line.article.designation}`}
                        onChange={(event) => setCart((current) => current.map((entry) => entry.article.id === line.article.id
                          ? { ...entry, totalNegocie: event.target.value, prixNegocie: "" }
                          : entry))}
                        className={`input-field h-8 min-w-0 flex-1 text-right text-xs ${totalError ? "border-rose-300" : ""}`}
                      />
                    </label>
                    {negotiationError || totalError
                      ? <p role="alert" className="mt-1 text-xs font-semibold text-rose-600">{negotiationError ?? totalError}</p>
                      : lineTotal !== null && lineDiscount > 0 ? (
                        <p className="mt-1 text-xs font-semibold text-emerald-700">
                          Remise sur la ligne : −{money2.format(lineDiscount)}
                          {" "}(−{round2(lineDiscount / catalogueTotal(line) * 100)} %)
                        </p>
                      ) : negotiatedDiscount > 0 && (
                        <p className="mt-1 text-xs font-semibold text-emerald-700">
                          Remise négociée : −{money2.format(negotiatedDiscount)} / unité
                          {" "}(−{round2(negotiatedDiscount / line.article.prixVente * 100)} %)
                        </p>
                      )}
                    {availablePromotions.length > 0 && lineTotal === null && (
                      <label className="mt-3 flex items-center gap-2 text-xs font-semibold text-emerald-700">
                        <span className="shrink-0">Remise</span>
                        <select
                          value={line.promotionId ?? ""}
                          onChange={(event) => setCart((current) => current.map((entry) => entry.article.id === line.article.id
                            ? { ...entry, promotionId: event.target.value ? Number(event.target.value) : null }
                            : entry))}
                          className="min-w-0 flex-1 rounded-lg border border-emerald-100 bg-emerald-50 px-2 py-1.5 text-xs font-semibold outline-none"
                        >
                          <option value="">Automatique · meilleure promo</option>
                          {availablePromotions.map((promotion) => (
                            <option key={promotion.id} value={promotion.id}>{promotion.nom} · -{promotion.pourcentage}%</option>
                          ))}
                        </select>
                      </label>
                    )}
                  </article>
                );
              })}
            </div>

            <div className="rounded-xl bg-slate-50 p-4">
              <div className="flex items-center justify-between text-xs text-slate-500">
                <span>Sous-total après promotions</span><span className="font-semibold text-slate-700">{currency.format(cartTotals.subtotal)}</span>
              </div>
              {fiscal.tauxTva > 0 && (
                <div className="mt-2 flex items-center justify-between text-xs text-slate-500">
                  <span>TVA ({fiscal.tauxTva} %{fiscal.prixTtc ? ", incluse" : ""})</span><span className="font-semibold text-slate-700">{currency.format(cartTotals.tax)}</span>
                </div>
              )}
              <div className="my-3 border-t border-dashed border-slate-200" />
              <div className="flex items-center justify-between">
                <span className="text-sm font-bold text-slate-800">{fiscal.tauxTva > 0 ? "Total TTC" : "Total à payer"}</span>
                <span className="text-xl font-extrabold tracking-tight text-slate-900">{currency.format(cartTotals.total)}</span>
              </div>
            </div>
            <button
              onClick={beginCheckout}
              disabled={!cart.length || !session}
              className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 text-sm font-bold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-400 disabled:shadow-none"
            >
              <Banknote size={17} /> Encaisser {currency.format(cartTotals.total)}
            </button>
            {!session && <p className="-mt-2 text-center text-xs font-medium text-amber-700">Ouvrez la caisse pour pouvoir valider une vente.</p>}
          </div>
        </section>
      </div>

      {checkoutOpen && (
        <PaymentModal
          amount={cartTotals.total}
          paymentMode={paymentMode}
          amountReceived={amountReceived}
          change={change}
          paymentAmountValid={paymentAmountValid}
          clientNom={clientNom}
          busy={checkoutBusy}
          draftCreated={pendingDraft !== null}
          onPaymentMode={setPaymentMode}
          onAmountReceived={setAmountReceived}
          onClose={() => setCheckoutOpen(false)}
          onSubmit={checkout}
        />
      )}

      {receipt && <ReceiptModal sale={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}

function CashSessionPanel({
  session,
  openingAmount,
  closingAmount,
  busy,
  onOpeningAmount,
  onClosingAmount,
  onOpen,
  onClose,
}: {
  session: CashSession | null;
  openingAmount: string;
  closingAmount: string;
  busy: boolean;
  onOpeningAmount: (value: string) => void;
  onClosingAmount: (value: string) => void;
  onOpen: (event: FormEvent<HTMLFormElement>) => void;
  onClose: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm md:px-5">
      <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-center">
        <div className="flex items-center gap-3">
          
          <div>
            <p className="text-base font-semibold text-slate-900">{session ? "Session de caisse active" : "Aucune session ouverte"}</p>
            <p className="mt-1 text-xs text-slate-500">
              {session ? `Journée du ${formatDay(session.journee)} · ${session.code} · Fond initial ${currency.format(session.fondDeCaisse)} · Ouverte à ${formatDateTime(session.ouverteLe)}` : "Ouvrez la journée de caisse : sa date est celle du serveur, elle ne se saisit pas."}
            </p>
          </div>
        </div>
        {session ? (
          <form onSubmit={onClose} className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <div className="border-l-4 border-emerald-600 pl-3 text-sm">
              <p className="font-semibold text-slate-900">Solde théorique : {currency.format(session.soldeTheorique)}</p>
              <p className="text-slate-500">Espèces des ventes + règlements + entrées de fonds − sorties</p>
            </div>
            <label className="block">
              <span className="mb-1.5 block text-sm font-semibold text-slate-700">Espèces comptées (XOF)</span>
              <input className="input-field sm:w-48" min="0" step="1" type="number" required value={closingAmount} onChange={(event) => onClosingAmount(event.target.value)} placeholder="Montant compté" />
            </label>
            <button disabled={busy} className="button-secondary border-rose-300 text-rose-800 hover:bg-rose-50">{busy ? "Clôture…" : "Clôturer la caisse"}</button>
          </form>
        ) : (
          <form onSubmit={onOpen} className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <label className="block">
              <span className="mb-1.5 block text-sm font-semibold text-slate-700">Fond initial (XOF)</span>
              <input className="input-field sm:w-48" min="0" step="1" type="number" required value={openingAmount} onChange={(event) => onOpeningAmount(event.target.value)} placeholder="Ex. 50 000" />
            </label>
            <button disabled={busy} className="button-primary">{busy ? "Ouverture…" : "Ouvrir la caisse"}</button>
          </form>
        )}
      {session?.journeeDepassee && (
        <p role="alert" className="mt-3 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-800">
          <CircleAlert size={14} className="mt-0.5 shrink-0" />
          La journée du {formatDay(session.journee)} n’a pas été clôturée. Comptez les espèces et clôturez-la : les ventes et l’ouverture de la journée d’aujourd’hui sont bloquées tant qu’elle reste ouverte.
        </p>
      )}
      </div>
    </section>
  );
}

function PaymentModal({
  amount,
  paymentMode,
  amountReceived,
  change,
  paymentAmountValid,
  clientNom,
  busy,
  draftCreated,
  onPaymentMode,
  onAmountReceived,
  onClose,
  onSubmit,
}: {
  amount: number;
  paymentMode: ChoixReglement;
  amountReceived: string;
  change: number;
  paymentAmountValid: boolean;
  /** Nom du client choisi dans le panier ; null pour un client de passage (pas de vente à crédit possible). */
  clientNom: string | null;
  busy: boolean;
  draftCreated: boolean;
  onPaymentMode: (mode: ChoixReglement) => void;
  onAmountReceived: (amount: string) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/50 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose(); }}>
      <form role="dialog" aria-modal="true" aria-label="Règlement" onSubmit={onSubmit} className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between">
          <div><h2 className="mt-1 text-xl font-bold text-slate-900">Règlement</h2></div>
          <button type="button" onClick={onClose} disabled={busy} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" aria-label="Fermer"><X size={18} /></button>
        </div>
        <div className="mt-5 rounded-xl bg-slate-50 p-4">
          <p className="text-xs font-medium text-slate-500">Montant à régler · TTC</p>
          <p className="mt-1 text-2xl font-extrabold text-slate-900">{currency.format(amount)}</p>
        </div>
        <fieldset className="mt-5">
          <legend className="mb-2 text-xs font-bold text-slate-700">Mode de paiement</legend>
          <div className="grid grid-cols-2 gap-2">
            {paymentModes.map(({ value, label, icon: Icon }) => (
              <button
                key={value}
                type="button"
                onClick={() => onPaymentMode(value)}
                disabled={draftCreated}
                className={`flex items-center gap-2 rounded-xl border px-3 py-3 text-xs font-bold transition ${
                  paymentMode === value ? "border-emerald-500 bg-emerald-50 text-emerald-800 ring-1 ring-emerald-500" : "border-slate-200 text-slate-600 hover:bg-slate-50"
                }`}
              >
                <Icon size={16} />{label}
                {paymentMode === value && <Check size={14} className="ml-auto" />}
              </button>
            ))}
            <button
              type="button"
              onClick={() => onPaymentMode("CREDIT")}
              disabled={draftCreated || clientNom === null}
              className={`col-span-2 flex items-center gap-2 rounded-xl border px-3 py-3 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50 ${
                paymentMode === "CREDIT" ? "border-emerald-500 bg-emerald-50 text-emerald-800 ring-1 ring-emerald-500" : "border-slate-200 text-slate-600 hover:bg-slate-50"
              }`}
            >
              <HandCoins size={16} />À crédit
              {paymentMode === "CREDIT" && <Check size={14} className="ml-auto" />}
            </button>
          </div>
          {clientNom === null && <p className="mt-2 text-xs text-slate-500">Choisissez d’abord un client dans le panier pour vendre à crédit.</p>}
        </fieldset>
        {draftCreated && <p className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-xs font-medium text-amber-800">Le brouillon est déjà enregistré. Les informations de règlement sont verrouillées pendant la nouvelle tentative de validation.</p>}
        {paymentMode === "CREDIT" ? (
          <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs text-amber-900">
            <p className="font-bold">Rien n’est encaissé maintenant.</p>
            <p className="mt-1">
              {currency.format(amount)} seront inscrits en dette au nom de <strong>{clientNom}</strong>. Le règlement se fera plus tard,
              depuis « Clients & dettes ».
            </p>
          </div>
        ) : (
          <>
            <label className="mt-5 block">
              <span className="mb-2 block text-xs font-bold text-slate-700">{paymentMode === "ESPECES" ? "Montant reçu (XOF)" : "Montant encaissé (XOF)"}</span>
              <input autoFocus disabled={draftCreated} className="input-field h-12 text-base font-bold" min={amount} step="any" type="number" required value={amountReceived} onChange={(event) => onAmountReceived(event.target.value)} />
            </label>
            <div className={`mt-3 flex items-center justify-between rounded-xl px-4 py-3 ${paymentMode === "ESPECES" ? "bg-emerald-50" : "bg-slate-50"}`}>
              <span className="text-xs font-semibold text-slate-600">{paymentMode === "ESPECES" ? "Monnaie à rendre" : "Rendu de monnaie"}</span>
              <span className="text-sm font-extrabold text-slate-900">{paymentMode === "ESPECES" ? currency.format(change) : currency.format(0)}</span>
            </div>
          </>
        )}
        <div className="mt-6 flex gap-3">
          <button type="button" disabled={busy} onClick={onClose} className="h-11 flex-1 rounded-xl border border-slate-200 text-sm font-bold text-slate-600 hover:bg-slate-50">Annuler</button>
          <button disabled={busy || !paymentAmountValid} className="h-11 flex-[1.4] rounded-xl bg-emerald-600 text-sm font-bold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50">{busy ? "Validation…" : paymentMode === "CREDIT" ? "Valider à crédit" : "Valider et encaisser"}</button>
        </div>
      </form>
    </div>
  );
}

function promotionsForArticle(articleId: number, promotions: Promotion[]) {
  const now = Date.now();
  return promotions.filter((promotion) =>
    promotion.active
    && promotion.articleIds.includes(articleId)
    && new Date(promotion.debut).getTime() <= now
    && new Date(promotion.fin).getTime() >= now
  );
}

function bestPromotion(articleId: number, promotions: Promotion[]) {
  return promotionsForArticle(articleId, promotions)
    .reduce<Promotion | undefined>((best, promotion) =>
      !best || promotion.pourcentage > best.pourcentage ? promotion : best, undefined);
}
