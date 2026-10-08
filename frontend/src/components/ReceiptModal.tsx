import { CheckCircle2, Printer, X } from "lucide-react";
import { formatPercent, moneyPrecise as money } from "../lib/format";

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
};

const paymentLabels: Record<ReceiptPaymentMode, string> = {
  ESPECES: "Espèces",
  MOBILE_MONEY: "Mobile Money",
  CARTE: "Carte",
  VIREMENT: "Virement",
  CHEQUE: "Chèque",
  AUTRE: "Autre",
};

// Jusqu'à deux décimales : un prix négocié peut comporter des centimes.

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

/** Arrondi à deux décimales, pour éviter les écarts de virgule flottante dans les remises affichées. */
const round2 = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100;

/** Remise négociée par unité (prix catalogue - prix appliqué), jamais négative. */
export const remiseNegocieeUnitaire = (line: ReceiptLine) =>
  round2(Math.max(0, (line.prixCatalogue ?? line.prixUnitaire) - line.prixUnitaire));

/** Totaux des remises d'une vente : négociée avec le client, et issue des promotions. */
export function remisesDeLaVente(sale: Sale) {
  const negociee = round2(sale.lignes.reduce((total, line) => total + remiseNegocieeUnitaire(line) * line.quantite, 0));
  const promotions = round2(sale.lignes.reduce((total, line) => total + (line.remiseUnitaire ?? 0) * line.quantite, 0));
  return { negociee, promotions, total: round2(negociee + promotions) };
}

type ReceiptModalProps = {
  sale: Sale;
  onClose: () => void;
  /** Réimpression d'un reçu déjà remis : le ticket est marqué « DUPLICATA ». */
  duplicata?: boolean;
};

export default function ReceiptModal({ sale, onClose, duplicata = false }: ReceiptModalProps) {
  const received = sale.montantRecu ?? sale.montantTotal;
  const paymentLabel = sale.modeReglement ? paymentLabels[sale.modeReglement] : "Paiement";
  const remises = remisesDeLaVente(sale);
  const annulee = sale.statut === "ANNULEE";
  return (
    <div className="fixed inset-0 z-[60] grid place-items-center overflow-y-auto bg-slate-950/50 p-4">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
        <div className="mb-5 flex items-center justify-between print:hidden">
          <div className="flex items-center gap-2 text-emerald-700">
            <CheckCircle2 size={20} />
            <span className="text-sm font-bold">{duplicata ? "Duplicata du reçu" : "Vente validée"}</span>
          </div>
          <button onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100" aria-label="Fermer"><X size={18} /></button>
        </div>
        <div id="sales-receipt" className="receipt-content mx-auto max-w-xs font-mono text-[11px] text-slate-800">
          <div className="text-center">
            <p className="text-base font-extrabold tracking-tight">GESSTOCK</p>
            <p className="mt-1 text-[9px] uppercase tracking-[0.2em]">Ticket de vente</p>
            {duplicata && <p className="mt-2 border border-slate-800 py-1 text-xs font-extrabold tracking-[0.3em]">DUPLICATA</p>}
            {annulee && <p className="mt-2 border border-rose-600 py-1 text-xs font-extrabold tracking-[0.2em] text-rose-700">VENTE ANNULÉE</p>}
            <p className="mt-3 border-y border-dashed border-slate-300 py-2">{sale.code}</p>
            <p className="mt-1">{formatDateTime(sale.dateVente)}</p>
            {sale.vendeur && <p className="mt-1">Caissier : {sale.vendeur}</p>}
          </div>
          <div className="my-4 border-t border-dashed border-slate-300" />
          <div className="space-y-3">
            {sale.lignes.map((line, index) => {
              const negociee = remiseNegocieeUnitaire(line);
              const catalogue = line.prixCatalogue ?? line.prixUnitaire;
              return (
                <div key={`${line.articleId}-${index}`}>
                  <div className="flex justify-between gap-3">
                    <span className="min-w-0">{line.designation}</span>
                    <span className="shrink-0">{money.format(line.montantLigne)}</span>
                  </div>
                  <p className="text-[9px] text-slate-500">
                    {line.quantite} × {money.format(line.prixUnitaire)}
                  </p>
                  {negociee > 0 && (
                    <p className="text-[9px] text-slate-500">
                      Prix catalogue {money.format(catalogue)} · remise négociée −{money.format(negociee)}/u
                      {" "}(−{formatPercent(round2(negociee / catalogue * 100))} %)
                    </p>
                  )}
                  {(line.remiseUnitaire ?? 0) > 0 && (
                    <p className="text-[9px] text-slate-500">Promotion −{money.format(line.remiseUnitaire ?? 0)}/u</p>
                  )}
                </div>
              );
            })}
          </div>
          <div className="my-4 border-t border-dashed border-slate-300" />
          <div className="space-y-1.5">
            {remises.negociee > 0 && <ReceiptRow label="Remise négociée" value={`−${money.format(remises.negociee)}`} />}
            {remises.promotions > 0 && <ReceiptRow label="Remise promotions" value={`−${money.format(remises.promotions)}`} />}
            {remises.total > 0 && <ReceiptRow label="Total des remises" value={`−${money.format(remises.total)}`} />}
            <ReceiptRow label="Sous-total HT" value={money.format(sale.montantHT)} />
            <ReceiptRow label={`TVA (${sale.tauxTva}%)`} value={money.format(sale.montantTva)} />
            <ReceiptRow label="TOTAL TTC" value={money.format(sale.montantTotal)} strong />
            {sale.aCredit ? (
              <ReceiptRow label="Vente à crédit" value="à régler" />
            ) : (
              <>
                <ReceiptRow label={paymentLabel} value={money.format(received)} />
                {sale.modeReglement === "ESPECES" && (
                  <ReceiptRow label="Monnaie rendue" value={money.format(Math.max(0, received - sale.montantTotal))} />
                )}
              </>
            )}
          </div>
          {sale.client && <p className="mt-4">Client : {sale.client}</p>}
          <p className="mt-5 text-center text-[9px] text-slate-500">Merci de votre visite !</p>
        </div>
        <div className="mt-6 flex gap-3 print:hidden">
          <button onClick={onClose} className="h-11 flex-1 rounded-xl border border-slate-200 text-sm font-bold text-slate-600 hover:bg-slate-50">
            {duplicata ? "Fermer" : "Terminer"}
          </button>
          <button onClick={() => window.print()} className="flex h-11 flex-1 items-center justify-center gap-2 rounded-xl bg-emerald-600 text-sm font-bold text-white hover:bg-emerald-700"><Printer size={16} /> Imprimer</button>
        </div>
      </div>
      <style>{`@media print { body * { visibility: hidden !important; } #sales-receipt, #sales-receipt * { visibility: visible !important; } #sales-receipt { position: fixed; inset: 0 auto auto 0; width: 72mm; max-width: 72mm; padding: 4mm; color: #000 !important; background: #fff !important; } @page { size: 80mm auto; margin: 3mm; } }`}</style>
    </div>
  );
}

function ReceiptRow({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`flex justify-between gap-3 ${strong ? "border-t border-slate-300 pt-2 text-sm font-extrabold" : ""}`}>
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );
}
