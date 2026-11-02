/**
 * Quantités de stock et de vente : 3 chiffres après la virgule au plus (une caisse entamée, un liquide au litre), comme le
 * serveur. On les tape avec une virgule ou un point ; on les affiche à la française (« 2,5 », « 0,125 », « 12 »).
 */
export const DECIMALES_QUANTITE = 3;

const affichage = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: DECIMALES_QUANTITE });

/** 2.5 -> « 2,5 » ; 12 -> « 12 » (sans zéros inutiles). */
export const formatQuantite = (valeur: number): string => affichage.format(valeur);

/** Arrondi à 3 décimales : évite les restes du calcul en virgule flottante (0,1 + 0,2 = 0,30000000000000004). */
export const arrondirQuantite = (valeur: number): number => Math.round((valeur + Number.EPSILON) * 1000) / 1000;

/** Texte saisi -> quantité ; null si le texte n'est pas un nombre positif ou nul à 3 décimales au plus. */
export function lireQuantite(texte: string): number | null {
  const propre = texte.trim().replace(/\s/g, "").replace(",", ".");
  if (!/^\d+(\.\d{0,3})?$/.test(propre)) return null;
  return Number(propre);
}

/** Pour un champ de saisie : ne garde que les chiffres et UNE virgule, et pas plus de 3 décimales. */
export function nettoyerSaisieQuantite(texte: string): string {
  const unifie = texte.replace(/\./g, ",").replace(/[^\d,]/g, "");
  const [entier, ...reste] = unifie.split(",");
  const entierPropre = entier.replace(/^0+(?=\d)/, "");
  if (reste.length === 0) return entierPropre;
  return `${entierPropre === "" ? "0" : entierPropre},${reste.join("").slice(0, DECIMALES_QUANTITE)}`;
}
