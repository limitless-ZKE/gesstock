import { CheckCircle2, Printer, X } from "lucide-react";
import { useEntreprise } from "../entreprise/EntrepriseContext";
import { imprimer } from "../lib/impression";
import { formatQuantite } from "../lib/quantite";
import EnTeteDocument from "./EnTeteDocument";
import ZoneImpression from "./ZoneImpression";

export type ReceiptPaymentMode = "ESPECES" | "MOBILE_MONEY" | "CARTE" | "VIREMENT" | "CHEQUE" | "AUTRE";

export type ReceiptLine = {
  articleId: number;
  reference: string;
  designation: string;
  quantite: number;
  prixUnitaire: number;
  montantLigne: number;
  promotionId: number | null;
  remiseUnitaire: number | null;
  /** Prix catalogue au moment de la vente ; l'écart avec le prix unitaire est la remise négociée. */
  prixCatalogue: number | null;
};

export type Sale = {
  id: number;
  code: string;
  dateVente: string;
  statut: string;
  client: string | null;
  vendeur: string | null;
  aCredit: boolean;
  montantTotal: number;
  montantHT: number;
  montantTva: number;
  tauxTva: number;
  modeReglement: ReceiptPaymentMode | null;
  montantRecu: number | null;
  lignes: ReceiptLine[];
  /** Avertissements de la validation (vente sous le prix d'achat) ; absents ailleurs. */
  avertissements?: string[];
};

const paymentLabels: Record<ReceiptPaymentMode, string> = {
  ESPECES: "Espèces",
  MOBILE_MONEY: "Mobile Money",
  CARTE: "Carte",
  VIREMENT: "Virement",
  CHEQUE: "Chèque",
  AUTRE: "Autre",
};

// Montants du ticket : « 13 500 F », comme sur les factures papier de l'entreprise (jusqu'à deux décimales pour un prix négocié).
const nombre = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 2 });
const francs = (value: number) => `${nombre.format(value)} F`;

/** 1 -> « 1,0 », 0.5 -> « 0,5 », 2.25 -> « 2,25 » : au moins une décimale dans la colonne des quantités. */
const quantiteDuTicket = (value: number) => (Number.isInteger(value) ? value.toFixed(1).replace(".", ",") : formatQuantite(value));

/** « 2026-11-17T14:50:13 » -> « 2026-11-17 14:50:13 » : l'heure du serveur telle quelle, sans conversion de fuseau. */
function formatDateTicket(value: string) {
  const lue = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(?::(\d{2}))?/.exec(value);
  return lue ? `${lue[1]} ${lue[2]}:${lue[3] ?? "00"}` : value;
}

/** Arrondi à deux décimales, pour éviter les écarts de virgule flottante dans les montants affichés. */
const round2 = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100;

/**
 * Lignes et totaux du ticket. Le tableau est au prix catalogue (prix avant remise) : sa somme est le « Total brut », la remise
 * (négociée avec le client + promotions) s'en retranche, et ce qui reste est le « TOTAL NET » à payer. Tout s'additionne donc à
 * la main, quelle que soit la façon dont les remises ont été accordées.
 */
export function totauxDuTicket(sale: Sale) {
  const lignes = sale.lignes.map((line) => {
    const prix = line.prixCatalogue ?? line.prixUnitaire;
    const montant = round2(prix * line.quantite);
    const remisePromo = line.remiseUnitaire != null && line.remiseUnitaire > 0 ? round2(line.remiseUnitaire * line.quantite) : 0;
    const remiseEcart = round2(Math.max(0, montant - line.montantLigne));
    const remiseLigne = round2(Math.min(montant, Math.max(remiseEcart, remisePromo)));
    return { ...line, prix, montant, remiseLigne };
  });
  const brut = round2(lignes.reduce((total, line) => total + line.montant, 0));
  const remise = round2(Math.max(0, brut - sale.montantTotal));
  const pourcentage = brut > 0 ? round2((remise / brut) * 100) : 0;
  return { lignes, brut, remise, pourcentage };
}

type ReceiptModalProps = {
  sale: Sale;
  onClose: () => void;
  /** Réimpression d'un reçu déjà remis : le ticket est marqué « DUPLICATA ». */
  duplicata?: boolean;
};

export default function ReceiptModal({ sale, onClose, duplicata = false }: ReceiptModalProps) {
  const received = sale.montantRecu ?? sale.montantTotal;
  const libellePaiement = sale.aCredit ? "Crédit" : sale.modeReglement ? paymentLabels[sale.modeReglement] : "Comptant";
  const { lignes, brut, remise, pourcentage } = totauxDuTicket(sale);
  const annulee = sale.statut === "ANNULEE";
  // Une vente à crédit n'a pas d'acompte tant que la caisse n'en enregistre pas : le reste à payer est alors le total.
  const acompte = sale.aCredit ? Math.min(sale.montantRecu ?? 0, sale.montantTotal) : 0;
  const { entreprise } = useEntreprise();
  // Message de fin de ticket choisi par l'entreprise ; avant le chargement de ses informations, le message d'usage.
  const piedDePage = entreprise ? entreprise.piedDePage : "Merci de votre visite !";
  return (
    <ZoneImpression cible="sales-receipt">
      <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl print:max-w-none print:rounded-none print:p-0 print:shadow-none">
        <div className="mb-5 flex items-center justify-between print:hidden">
          <div className="flex items-center gap-2 text-emerald-700">
            <CheckCircle2 size={20} />
            <span className="text-sm font-bold">{duplicata ? "Duplicata du reçu" : "Vente validée"}</span>
          </div>
          <button onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" aria-label="Fermer"><X size={18} /></button>
        </div>
        <div id="sales-receipt" className="receipt-content mx-auto max-w-sm font-mono text-[11px] text-slate-800">
          <div className="text-center">
            <EnTeteDocument variante="ticket" />
            {duplicata && <p className="mt-2 border border-slate-800 py-1 text-xs font-extrabold tracking-[0.3em]">DUPLICATA</p>}
            {annulee && <p className="mt-2 border border-rose-600 py-1 text-xs font-extrabold tracking-[0.2em] text-rose-700">VENTE ANNULÉE</p>}
          </div>
          <div className="mt-3 space-y-0.5 border-y border-dashed border-slate-400 py-2">
            <p className="text-center text-xs font-extrabold">FACTURE N° {sale.code}</p>
            <p>Date : {formatDateTicket(sale.dateVente)}</p>
            <p>Client : {sale.client ?? "Client de passage"}</p>
            <p>Paiement : {libellePaiement}{sale.vendeur ? ` - Vendeur : ${sale.vendeur}` : ""}</p>
          </div>
          <table className="mt-3 w-full border-collapse border border-slate-800 text-[9.5px] leading-tight">
            <thead>
              {/* Fond noir imprimé tel quel : sans « exact », le navigateur retire les fonds à l'impression. */}
              <tr className="bg-black text-white [-webkit-print-color-adjust:exact] [print-color-adjust:exact]">
                <th className="border border-slate-800 px-1 py-1 text-left font-bold">N°</th>
                <th className="border border-slate-800 px-1 py-1 text-left font-bold">Produit</th>
                <th className="border border-slate-800 px-1 py-1 text-right font-bold">Qté</th>
                <th className="border border-slate-800 px-1 py-1 text-right font-bold">Prix</th>
                <th className="border border-slate-800 px-1 py-1 text-right font-bold">Remise</th>
                <th className="border border-slate-800 px-1 py-1 text-right font-bold">Montant</th>
              </tr>
            </thead>
            <tbody>
              {lignes.map((line, index) => (
                <tr key={`${line.articleId}-${index}`} className="border border-slate-800 align-top">
                  <td className="border border-slate-800 px-1 py-1">{index + 1}</td>
                  <td className="border border-slate-800 px-1 py-1 break-words">{line.designation}</td>
                  <td className="border border-slate-800 px-1 py-1 text-right whitespace-nowrap">{quantiteDuTicket(line.quantite)}</td>
                  <td className="border border-slate-800 px-1 py-1 text-right whitespace-nowrap">{francs(line.prix)}</td>
                  <td className="border border-slate-800 px-1 py-1 text-right whitespace-nowrap">{francs(line.remiseLigne)}</td>
                  <td className="border border-slate-800 px-1 py-1 text-right font-bold whitespace-nowrap">{francs(line.montant)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="mt-3 space-y-1">
            <ReceiptRow label="Total brut" value={francs(brut)} />
            <ReceiptRow label={`Remise (${pourcentage.toFixed(1).replace(".", ",")} %)`} value={`−${francs(remise)}`} />
            <ReceiptRow label="TOTAL NET" value={francs(sale.montantTotal)} strong />
            {sale.tauxTva > 0 && <ReceiptRow label="dont HT" value={francs(sale.montantHT)} />}
            {sale.tauxTva > 0 && <ReceiptRow label={`dont TVA (${sale.tauxTva} %)`} value={francs(sale.montantTva)} />}
            {sale.aCredit ? (
              <>
                <ReceiptRow label="Acompte payé" value={francs(acompte)} />
                <ReceiptRow label="Reste à payer" value={francs(Math.max(0, sale.montantTotal - acompte))} bold />
              </>
            ) : (
              <>
                <ReceiptRow label={`Reçu (${libellePaiement})`} value={francs(received)} />
                {sale.modeReglement === "ESPECES" && (
                  <ReceiptRow label="Monnaie rendue" value={francs(Math.max(0, received - sale.montantTotal))} />
                )}
              </>
            )}
          </div>
          {piedDePage && <p className="mt-5 text-center text-[9px] text-slate-500">{piedDePage}</p>}
        </div>
        <div className="mt-6 flex gap-3 print:hidden">
          <button onClick={onClose} className="h-11 flex-1 rounded-xl border border-slate-200 text-sm font-bold text-slate-600 hover:bg-slate-50">
            {duplicata ? "Fermer" : "Terminer"}
          </button>
          <button onClick={() => void imprimer(document.getElementById("sales-receipt"))} className="flex h-11 flex-1 items-center justify-center gap-2 rounded-xl bg-emerald-600 text-sm font-bold text-white hover:bg-emerald-700"><Printer size={16} /> Imprimer</button>
        </div>
      </div>
    </ZoneImpression>
  );
}

function ReceiptRow({ label, value, strong = false, bold = false }: { label: string; value: string; strong?: boolean; bold?: boolean }) {
  const style = strong ? "border-y-2 border-black py-1 text-xs font-extrabold" : bold ? "font-extrabold" : "";
  return (
    <div className={`flex justify-between gap-3 ${style}`}>
      <span>{label}</span>
      <span className="whitespace-nowrap">{value}</span>
    </div>
  );
}
