import { useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";

type ZoneImpressionProps = {
  /** Identifiant de l'élément à imprimer (le ticket) à l'intérieur de la fenêtre. */
  cible: string;
  /** Largeur du papier du ticket, en millimètres. */
  largeurMm: number;
  /** Classes de la fenêtre à l'écran (au-dessus des autres fenêtres : z-[60], z-[70]…). */
  className?: string;
  children: ReactNode;
};

/**
 * Fenêtre d'affichage d'un ticket, prête à être imprimée sur une imprimante de caisse.
 *
 * Le ticket est placé tout en bas de la page (hors de l'application) et, à l'impression, tout le reste de la page disparaît
 * (`display: none`, pas `visibility: hidden`). Avec `visibility: hidden`, l'écran caché occupait encore sa place : il fabriquait
 * des pages en plus et le ticket, posé en `position: fixed`, se répétait sur chacune : le reçu sortait deux fois.
 */
export default function ZoneImpression({ cible, largeurMm, className = "z-[60]", children }: ZoneImpressionProps) {
  useEffect(() => {
    const racine = document.documentElement;
    racine.classList.add("impression-ticket");
    return () => racine.classList.remove("impression-ticket");
  }, []);
  return createPortal(
    <div className={`zone-impression fixed inset-0 grid place-items-center overflow-y-auto bg-slate-950/50 p-4 print:static print:block print:overflow-visible print:bg-white print:p-0 ${className}`}>
      {children}
      <style>{`@media print {
        html.impression-ticket body > *:not(.zone-impression) { display: none !important; }
        html.impression-ticket body { min-height: 0 !important; }
        html.impression-ticket #${cible} { width: ${largeurMm}mm; max-width: ${largeurMm}mm; margin: 0; padding: 4mm; color: #000 !important; background: #fff !important; box-shadow: none; }
        @page { size: 80mm auto; margin: 3mm; }
      }`}</style>
    </div>,
    document.body,
  );
}
