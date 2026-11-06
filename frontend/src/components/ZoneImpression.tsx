import { useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { ECHELLE_TICKET, MARGE_INTERIEURE_MM } from "../lib/ticket";

type ZoneImpressionProps = {
  /** Identifiant de l'élément à imprimer (le ticket) à l'intérieur de la fenêtre. */
  cible: string;
  /** Agrandir aussi le ticket à l'écran (le ticket de vente) ; faux quand l'élément visé est une fenêtre entière (le reçu de règlement). */
  agrandirALEcran?: boolean;
  /** Classes de la fenêtre à l'écran (au-dessus des autres fenêtres : z-[60], z-[70]…). */
  className?: string;
  children: ReactNode;
};

/**
 * Fenêtre d'affichage d'un ticket, prête à être imprimée sur une imprimante de caisse (papier de 80 mm).
 *
 * Le ticket est placé tout en bas de la page (hors de l'application) et, à l'impression, tout le reste de la page disparaît
 * (`display: none`, pas `visibility: hidden`). Avec `visibility: hidden`, l'écran caché occupait encore sa place : il fabriquait
 * des pages en plus et le ticket, posé en `position: fixed`, se répétait sur chacune : le reçu sortait deux fois.
 *
 * Au papier, le ticket prend toute la largeur disponible de la page (`width: 100 %`, marge de page nulle) : il suit le papier choisi dans
 * l'imprimante et ne déborde jamais, contrairement à une largeur fixe en millimètres. Le zoom agrandit les écritures sans toucher à la
 * largeur (un pourcentage n'est pas multiplié par le zoom : mesuré dans Edge, 79,9 mm sur 80 mm).
 */
export default function ZoneImpression({ cible, agrandirALEcran = true, className = "z-[60]", children }: ZoneImpressionProps) {
  useEffect(() => {
    const racine = document.documentElement;
    racine.classList.add("impression-ticket");
    return () => racine.classList.remove("impression-ticket");
  }, []);
  return createPortal(
    <div className={`zone-impression fixed inset-0 grid place-items-center overflow-y-auto bg-slate-950/50 p-4 print:static print:block print:overflow-visible print:bg-white print:p-0 ${className}`}>
      {children}
      <style>{`${agrandirALEcran ? `.zone-impression #${cible} { zoom: ${ECHELLE_TICKET}; }\n      ` : ""}@media print {
        html.impression-ticket body > *:not(.zone-impression) { display: none !important; }
        html.impression-ticket body { min-height: 0 !important; }
        html.impression-ticket #${cible} { zoom: ${ECHELLE_TICKET}; width: 100%; max-width: none; margin: 0; padding: ${MARGE_INTERIEURE_MM}mm; color: #000 !important; background: #fff !important; box-shadow: none; }
        @page { size: 80mm auto; margin: 0; }
      }`}</style>
    </div>,
    document.body,
  );
}
