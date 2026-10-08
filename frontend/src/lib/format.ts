/** Montants en francs CFA (XOF), sans décimale : l'affichage courant (listes, tableaux de bord, rapports). */
export const money = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "XOF", maximumFractionDigits: 0 });

/** Jusqu'à deux décimales : un prix négocié ou une remise peut comporter des centimes (caisse, reçus, historique). */
export const moneyPrecise = new Intl.NumberFormat("fr-FR", {
  style: "currency",
  currency: "XOF",
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const pourcentage = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 2 });

/** 10.2 -> « 10,2 » : même séparateur décimal que les montants affichés à côté. */
export const formatPercent = (value: number): string => pourcentage.format(value);
