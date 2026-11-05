/**
 * Marge d'un article : prix de vente moins coût (le coût moyen pondéré, celui sur lequel se calculent les bénéfices).
 * Le taux se calcule sur le PRIX DE VENTE : sur 100 F encaissés, combien restent après le coût. Il est vide quand le coût est
 * inconnu (0) : un taux de 100 % serait faux et trompeur.
 */
export function margeUnitaire(prixVente: number, cout: number): number {
  return prixVente - cout;
}

export function tauxDeMarge(montantVente: number, cout: number): number | null {
  if (montantVente <= 0 || cout <= 0) return null;
  return ((montantVente - cout) / montantVente) * 100;
}

/** Un coût à 0 sur un article qui se vend : le prix d'achat n'a jamais été saisi, la marge affichée serait fausse. */
export function coutAVerifier(cout: number): boolean {
  return cout <= 0;
}
